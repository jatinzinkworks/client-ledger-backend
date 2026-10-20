package com.psc.cl.servicecatalog.service;

import com.psc.cl.exception.SpreadsheetValidationException;
import com.psc.cl.service.SpreadsheetImport;
import com.psc.cl.service.SpreadsheetImport.CellErrors;
import com.psc.cl.service.SpreadsheetImport.Choices;
import com.psc.cl.service.SpreadsheetImport.Column;
import com.psc.cl.service.SpreadsheetImport.Row;
import com.psc.cl.service.SpreadsheetImport.SheetLayout;
import com.psc.cl.service.XlsxReader;
import com.psc.cl.service.XlsxWorkbook;
import com.psc.cl.service.XlsxWorkbook.Cell;
import com.psc.cl.service.XlsxWorkbook.Sheet;
import com.psc.cl.servicecatalog.dto.CatalogServiceRequest;
import com.psc.cl.servicecatalog.dto.CatalogServiceResponse;
import com.psc.cl.servicecatalog.dto.InvoiceSchedule;
import com.psc.cl.servicecatalog.dto.ServiceCatalogImportResponse;
import com.psc.cl.servicecatalog.model.BillingFrequency;
import com.psc.cl.servicecatalog.model.CatalogService;
import com.psc.cl.servicecatalog.model.InvoiceMonth;
import com.psc.cl.servicecatalog.model.QuarterInvoiceMonth;
import com.psc.cl.servicecatalog.model.ServiceCategory;
import com.psc.cl.servicecatalog.repository.CatalogServiceRepository;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Default {@link ServiceCatalogImportService}.
 *
 * <p>Every row is read and checked before anything is created: values that cannot be read, such as
 * an unknown category, are reported against their cell, as is a name already in the catalog or
 * repeated on the sheet; each service is then checked against the same rules as
 * {@code POST /services}, with each rule broken also reported against its cell. Only a workbook
 * without a single error is imported, in one transaction.
 */
@Service
@RequiredArgsConstructor
public class ServiceCatalogImportServiceImpl implements ServiceCatalogImportService {

    private static final Logger log = LoggerFactory.getLogger(ServiceCatalogImportServiceImpl.class);

    static final String SERVICES_SHEET = "Services";

    static final String INSTRUCTIONS_SHEET = "Instructions";

    /** Most services one workbook may import. */
    static final int MAX_SERVICES = 500;

    /** What the template is called in messages. */
    private static final String TEMPLATE = "service catalog import template";

    private static final int NAME = 0;

    private static final int BILLING_FREQUENCY = 3;

    private static final int INVOICE_DAY = 6;

    private static final int MONTH_OF_QUARTER = 7;

    private static final int INVOICE_MONTH = 8;

    /** GST rates the template's drop-down offers: the slabs services are usually charged at. */
    private static final String[] GST_RATES = {"0", "5", "12", "18", "28", "40"};

    /** The Services sheet, column A onwards; each field is the {@link CatalogServiceRequest} component it fills. */
    static final SheetLayout SERVICES = new SheetLayout(SERVICES_SHEET, List.of(
            new Column("Service Name", "serviceName", 32, true, "Name of the service, up to 150 characters. "
                    + "Unique across the catalog and this sheet, ignoring case", "GST Monthly Return Filing"),
            new Column("Description", "description", 45, false, "What the service covers, up to 1000 characters",
                    "Preparation and filing of GSTR-1 and GSTR-3B each month"),
            new Column("Category", "category", 16, true, "One of " + SpreadsheetImport.choices(ServiceCategory.class)
                    + ", picked from the drop-down", "COMPLIANCE", Choices.of(ServiceCategory.class)),
            new Column("Billing Frequency", "billingFrequency", 18, true, "One of "
                    + SpreadsheetImport.choices(BillingFrequency.class) + ", picked from the drop-down", "MONTHLY",
                    Choices.of(BillingFrequency.class)),
            new Column("Standard Fee", "standardFee", 14, true, "Fee charged for the service, excluding GST; not "
                    + "negative, at most 2 decimals", "5000"),
            new Column("GST %", "gstRatePercent", 8, false, "GST rate, 0 to 100 with at most 2 decimals. The "
                    + "drop-down offers the usual rates, but any other may be typed, as 18 or 18%. Blank for 18", "18",
                    Choices.suggested(GST_RATES)),
            new Column("Invoice Day", "invoiceSchedule.dayOfMonth", 12, false, "Day of the month the invoice is "
                    + "raised, 1 to 31; in shorter months, their last day. Required unless ONE_OFF, which must "
                    + "leave it blank", "7", new Choices(IntStream.rangeClosed(1, 31).mapToObj(String::valueOf)
                    .toList(), true)),
            new Column("Invoice Month of Quarter", "invoiceSchedule.monthOfQuarter", 24, false, "QUARTERLY only, "
                    + "and required for it: which month after the quarter closes the invoice is raised in, "
                    + SpreadsheetImport.choices(QuarterInvoiceMonth.class) + ", or 1, 2 or 3. For the quarter "
                    + "ending March, FIRST_MONTH is April", "FIRST_MONTH",
                    // Not strict: 1, 2 and 3 are accepted too.
                    new Choices(Choices.of(QuarterInvoiceMonth.class).values(), false)),
            new Column("Invoice Month", "invoiceSchedule.month", 16, false, "ANNUAL only, and required for it: the "
                    + "month the invoice is raised in, picked from the drop-down", "APRIL",
                    Choices.of(InvoiceMonth.class))), 0);

    /**
     * What a broken rule about the invoice schedule says on the sheet, against the column it
     * concerns: the request's own messages name its JSON fields, which the sheet does not have.
     */
    private static final Map<String, CellRule> SCHEDULE_RULES = Map.of(
            "scheduleAbsentForOneOff", new CellRule(BILLING_FREQUENCY, "is ONE_OFF, so leave Invoice Day, "
                    + "Invoice Month of Quarter and Invoice Month blank"),
            "dayOfMonthPresentWhenRecurring", new CellRule(INVOICE_DAY, "must be given for MONTHLY, QUARTERLY "
                    + "and ANNUAL billing"),
            "monthOfQuarterMatchingBilling", new CellRule(MONTH_OF_QUARTER, "must be given for QUARTERLY "
                    + "billing, and left blank otherwise"),
            "monthMatchingBilling", new CellRule(INVOICE_MONTH, "must be given for ANNUAL billing, and left "
                    + "blank otherwise"));

    /** Months of a quarter by number, as people may write them. */
    private static final Map<String, QuarterInvoiceMonth> QUARTER_MONTHS_BY_NUMBER = Map.of(
            "1", QuarterInvoiceMonth.FIRST_MONTH,
            "2", QuarterInvoiceMonth.SECOND_MONTH,
            "3", QuarterInvoiceMonth.THIRD_MONTH);

    private final ServiceCatalogService serviceCatalogService;

    private final CatalogServiceRepository catalogServiceRepository;

    private final Validator validator;

    /**
     * Where a broken rule is reported, and what is said.
     *
     * @param column  the column of the cell it concerns
     * @param message the message, after the column's heading
     */
    private record CellRule(int column, String message) {
    }

    @Override
    public byte[] template() {
        XlsxWorkbook workbook = new XlsxWorkbook();
        SpreadsheetImport.addSheet(workbook, SERVICES);

        Sheet instructions = workbook.addSheet(INSTRUCTIONS_SHEET, 14, 24, 10, 100, 40);
        instructions.addRow(Cell.bold("Importing services into the catalog"))
                .addBlankRow()
                .addRow(Cell.text("1. On the " + SERVICES_SHEET + " sheet, add one row per service. Columns with a "
                        + "set list of values offer it in a drop-down."))
                .addRow(Cell.text("2. Fill in the invoice schedule to match the billing frequency: ONE_OFF leaves "
                        + "it blank; MONTHLY needs an Invoice Day; QUARTERLY an Invoice Day and an Invoice Month of "
                        + "Quarter; ANNUAL an Invoice Day and an Invoice Month."))
                .addRow(Cell.text("3. Keep the headings in row 1 as they are. Blank rows are skipped. Up to "
                        + MAX_SERVICES + " services per file."))
                .addRow(Cell.text("4. The file is imported whole or not at all: if any cell is wrong, or a name is "
                        + "already in the catalog, nothing is imported, and every cell at fault is listed so it can "
                        + "be corrected."))
                .addBlankRow()
                .addRow(Cell.bold("Sheet"), Cell.bold("Column"), Cell.bold("Required"), Cell.bold("What to enter"),
                        Cell.bold("Example"));
        SpreadsheetImport.describe(instructions, SERVICES);
        return workbook.toBytes();
    }

    @Override
    @Transactional
    public ServiceCatalogImportResponse importServices(byte[] content) {
        XlsxReader.Workbook workbook = XlsxReader.read(content);
        XlsxReader.Sheet sheet = SpreadsheetImport.requireSheet(workbook, SERVICES, TEMPLATE);
        Map<Integer, Map<Integer, String>> rows = SpreadsheetImport.dataRows(sheet);
        if (rows.size() > MAX_SERVICES) {
            throw new SpreadsheetValidationException("The " + SERVICES_SHEET + " sheet has " + rows.size()
                    + " services; import at most " + MAX_SERVICES + " per file");
        }

        CellErrors errors = new CellErrors();
        Map<Integer, CatalogServiceRequest> services = readServices(rows, errors);
        services.forEach((row, request) -> validate(row, request, errors));
        if (!errors.isEmpty()) {
            throw errors.toException();
        }
        if (services.isEmpty()) {
            throw new SpreadsheetValidationException("The " + SERVICES_SHEET + " sheet has no services below its "
                    + "headings");
        }

        List<CatalogServiceResponse> created = new ArrayList<>();
        services.values().forEach(request -> created.add(serviceCatalogService.createService(request)));
        log.info("Imported {} catalog service(s) from a workbook", created.size());

        return ServiceCatalogImportResponse.builder()
                .serviceCount(created.size())
                .services(created)
                .build();
    }

    /** Each service read, by row; a row whose name is missing, taken or repeated is reported and left out. */
    private Map<Integer, CatalogServiceRequest> readServices(Map<Integer, Map<Integer, String>> rows,
                                                             CellErrors errors) {
        // Names are unique ignoring case, as the catalog's unique index holds them.
        Map<String, String> catalogNames = catalogServiceRepository.findAll().stream()
                .map(CatalogService::getServiceName)
                .collect(Collectors.toMap(SpreadsheetImport::key, name -> name, (first, same) -> first));
        Map<String, Integer> namesOnSheet = new HashMap<>();
        Map<Integer, CatalogServiceRequest> services = new LinkedHashMap<>();
        rows.forEach((row, values) -> {
            Row cells = new Row(SERVICES, row, values, errors);
            CatalogServiceRequest request = request(cells);
            String name = cells.text(NAME);
            if (name == null) {
                // Left to validation, which reports the missing name with the rest of the row.
                services.put(row, request);
                return;
            }
            String key = SpreadsheetImport.key(name);
            if (catalogNames.containsKey(key)) {
                cells.error(NAME, "the catalog already has a service named " + catalogNames.get(key));
            } else if (namesOnSheet.containsKey(key)) {
                cells.error(NAME, "repeats the Service Name of row " + namesOnSheet.get(key)
                        + "; give each service its own");
            } else {
                namesOnSheet.put(key, row);
            }
            services.put(row, request);
        });
        return services;
    }

    private static CatalogServiceRequest request(Row cells) {
        Integer day = cells.wholeNumber(INVOICE_DAY);
        QuarterInvoiceMonth monthOfQuarter = monthOfQuarter(cells);
        InvoiceMonth month = cells.choice(INVOICE_MONTH, InvoiceMonth.class);
        boolean scheduled = cells.text(INVOICE_DAY) != null || cells.text(MONTH_OF_QUARTER) != null
                || cells.text(INVOICE_MONTH) != null;
        return CatalogServiceRequest.builder()
                .serviceName(cells.text(NAME))
                .description(cells.text(1))
                .category(cells.choice(2, ServiceCategory.class))
                .billingFrequency(cells.choice(BILLING_FREQUENCY, BillingFrequency.class))
                .standardFee(cells.amount(4))
                .gstRatePercent(cells.percent(5))
                // A row with nothing in the schedule columns has no schedule, as a ONE_OFF service needs.
                .invoiceSchedule(scheduled ? new InvoiceSchedule(day, monthOfQuarter, month) : null)
                .build();
    }

    /** A month of a quarter by name, such as First Month, or by number, 1 to 3. */
    private static QuarterInvoiceMonth monthOfQuarter(Row cells) {
        String value = cells.text(MONTH_OF_QUARTER);
        QuarterInvoiceMonth byNumber = value == null ? null : QUARTER_MONTHS_BY_NUMBER.get(value);
        return byNumber != null ? byNumber : cells.choice(MONTH_OF_QUARTER, QuarterInvoiceMonth.class);
    }

    /**
     * Checks a service against the rules of {@code POST /services}, reporting each rule broken
     * against the cell it concerns. A cell already reported keeps its first error.
     */
    private void validate(int row, CatalogServiceRequest request, CellErrors errors) {
        for (ConstraintViolation<CatalogServiceRequest> violation : validator.validate(request)) {
            String path = violation.getPropertyPath().toString();
            CellRule rule = SCHEDULE_RULES.get(path);
            if (rule != null) {
                errors.add(SERVICES, row, rule.column(), rule.message());
            } else {
                int column = SERVICES.columnOf(path);
                errors.add(SERVICES, row, column < 0 ? NAME : column, violation.getMessage());
            }
        }
    }
}
