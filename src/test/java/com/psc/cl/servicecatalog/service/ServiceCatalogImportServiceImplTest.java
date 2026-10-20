package com.psc.cl.servicecatalog.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.psc.cl.dto.ErrorResponse;
import com.psc.cl.exception.SpreadsheetValidationException;
import com.psc.cl.service.XlsxReader;
import com.psc.cl.service.XlsxWorkbook;
import com.psc.cl.service.XlsxWorkbook.Cell;
import com.psc.cl.service.XlsxWorkbookTest;
import com.psc.cl.servicecatalog.dto.CatalogServiceRequest;
import com.psc.cl.servicecatalog.dto.CatalogServiceResponse;
import com.psc.cl.servicecatalog.dto.ServiceCatalogImportResponse;
import com.psc.cl.servicecatalog.model.BillingFrequency;
import com.psc.cl.servicecatalog.model.CatalogService;
import com.psc.cl.servicecatalog.model.InvoiceMonth;
import com.psc.cl.servicecatalog.model.QuarterInvoiceMonth;
import com.psc.cl.servicecatalog.model.ServiceCategory;
import com.psc.cl.servicecatalog.repository.CatalogServiceRepository;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

@ExtendWith(MockitoExtension.class)
class ServiceCatalogImportServiceImplTest {

    private static final ValidatorFactory VALIDATION = Validation.buildDefaultValidatorFactory();

    private static final Cell[] HEADINGS = headings("Service Name", "Description", "Category", "Billing Frequency",
            "Standard Fee", "GST %", "Invoice Day", "Invoice Month of Quarter", "Invoice Month");

    @Mock
    private ServiceCatalogService serviceCatalogService;

    @Mock
    private CatalogServiceRepository catalogServiceRepository;

    private ServiceCatalogImportServiceImpl importService;

    @BeforeEach
    void setUp() {
        importService = new ServiceCatalogImportServiceImpl(serviceCatalogService, catalogServiceRepository,
                VALIDATION.getValidator());
    }

    @AfterAll
    static void closeValidation() {
        VALIDATION.close();
    }

    @Test
    @DisplayName("writes a template with the Services headings and an Instructions sheet")
    void writesTemplate() {
        XlsxReader.Workbook template = XlsxReader.read(importService.template());

        assertThat(template.sheets()).extracting(XlsxReader.Sheet::name).containsExactly("Services", "Instructions");
        XlsxReader.Sheet services = template.sheet("Services").orElseThrow();
        assertThat(IntStream.range(0, 9).mapToObj(column -> services.cell(1, column)))
                .containsExactly("Service Name", "Description", "Category", "Billing Frequency", "Standard Fee",
                        "GST %", "Invoice Day", "Invoice Month of Quarter", "Invoice Month");
        assertThat(services.rows()).containsOnlyKeys(1);
        assertThat(template.sheet("Instructions").orElseThrow().rows().values())
                .anySatisfy(row -> assertThat(row.get(3)).contains("COMPLIANCE"))
                .anySatisfy(row -> assertThat(row.get(3)).contains("FIRST_MONTH"));
    }

    @Test
    @DisplayName("the template offers drop-downs for the columns with a set list of values")
    void offersDropDowns() {
        String services = XlsxWorkbookTest.parts(importService.template()).get("xl/worksheets/sheet1.xml");

        assertThat(services)
                .contains("<dataValidations count=\"6\">")
                .contains("sqref=\"C2:C1048576\"><formula1>&quot;COMPLIANCE,TAX,AUDIT,ACCOUNTING,ADVISORY,"
                        + "CERTIFICATION&quot;</formula1>")
                .contains("sqref=\"D2:D1048576\"><formula1>&quot;ONE_OFF,MONTHLY,QUARTERLY,ANNUAL&quot;</formula1>")
                .contains("sqref=\"F2:F1048576\"><formula1>&quot;0,5,12,18,28,40&quot;</formula1>")
                .contains("sqref=\"G2:G1048576\"><formula1>&quot;1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,"
                        + "20,21,22,23,24,25,26,27,28,29,30,31&quot;</formula1>")
                .contains("sqref=\"H2:H1048576\"><formula1>&quot;FIRST_MONTH,SECOND_MONTH,THIRD_MONTH&quot;")
                .contains("sqref=\"I2:I1048576\"><formula1>&quot;JANUARY,FEBRUARY,MARCH,APRIL,MAY,JUNE,JULY,"
                        + "AUGUST,SEPTEMBER,OCTOBER,NOVEMBER,DECEMBER&quot;</formula1>")
                // Only GST and the month of a quarter accept values beyond their lists, so only they just warn.
                .containsSubsequence("errorStyle=\"warning\"", "sqref=\"F2:F1048576\"",
                        "errorStyle=\"warning\"", "sqref=\"H2:H1048576\"");
        assertThat(services.split("errorStyle=\"warning\"", -1)).hasSize(3);
    }

    @Test
    @DisplayName("an empty template is refused: there is nothing to import")
    void refusesEmptyTemplate() {
        assertThatThrownBy(() -> importService.importServices(importService.template()))
                .isInstanceOf(SpreadsheetValidationException.class)
                .hasMessageContaining("no services");

        verify(serviceCatalogService, never()).createService(any());
    }

    @Test
    @DisplayName("creates each service with the schedule its billing frequency needs, reading values as typed")
    void importsServices() {
        when(catalogServiceRepository.findAll()).thenReturn(List.of(stored("Statutory Audit")));
        when(serviceCatalogService.createService(any())).thenAnswer(invocation -> created(invocation.getArgument(0)));
        byte[] workbook = workbook(
                row("GST Monthly Return Filing", "GSTR-1 and GSTR-3B", "compliance", "Monthly", "5,000", "18%",
                        "7", null, null),
                row("TDS Quarterly Return", null, "TAX", "QUARTERLY", "7500", null, "10", "2", null),
                row("Annual ROC Filing", null, "COMPLIANCE", "ANNUAL", "₹12000.50", "12", "15", null, "april"),
                row("Company Incorporation", null, "ADVISORY", "One Off", "25000", null, null, null, null));

        ServiceCatalogImportResponse response = importService.importServices(workbook);

        ArgumentCaptor<CatalogServiceRequest> captor = ArgumentCaptor.forClass(CatalogServiceRequest.class);
        verify(serviceCatalogService, times(4)).createService(captor.capture());
        List<CatalogServiceRequest> requests = captor.getAllValues();

        CatalogServiceRequest monthly = requests.get(0);
        assertThat(monthly.serviceName()).isEqualTo("GST Monthly Return Filing");
        assertThat(monthly.description()).isEqualTo("GSTR-1 and GSTR-3B");
        assertThat(monthly.category()).isEqualTo(ServiceCategory.COMPLIANCE);
        assertThat(monthly.billingFrequency()).isEqualTo(BillingFrequency.MONTHLY);
        assertThat(monthly.standardFee()).isEqualByComparingTo("5000");
        assertThat(monthly.gstRatePercent()).isEqualByComparingTo("18");
        assertThat(monthly.scheduledDayOfMonth()).isEqualTo(7);

        // A month of quarter by number.
        assertThat(requests.get(1).scheduledMonthOfQuarter()).isEqualTo(QuarterInvoiceMonth.SECOND_MONTH);
        // A GST rate left blank takes the default when created.
        assertThat(requests.get(1).gstRatePercent()).isNull();
        assertThat(requests.get(2).scheduledMonth()).isEqualTo(InvoiceMonth.APRIL);
        assertThat(requests.get(2).standardFee()).isEqualByComparingTo("12000.50");
        // Nothing in the schedule columns: no schedule, as ONE_OFF needs.
        assertThat(requests.get(3).billingFrequency()).isEqualTo(BillingFrequency.ONE_OFF);
        assertThat(requests.get(3).invoiceSchedule()).isNull();

        assertThat(response.getServiceCount()).isEqualTo(4);
        assertThat(response.getServices()).extracting(CatalogServiceResponse::getServiceName)
                .containsExactly("GST Monthly Return Filing", "TDS Quarterly Return", "Annual ROC Filing",
                        "Company Incorporation");
    }

    @Test
    @DisplayName("creates nothing when any cell is wrong, listing every cell at fault in row order")
    void reportsEveryInvalidCell() {
        when(catalogServiceRepository.findAll()).thenReturn(List.of(stored("Statutory Audit")));
        byte[] workbook = workbook(
                row("statutory audit", null, "AUDIT", "ANNUAL", "50000", null, "15", null, "APRIL"),
                row("GST Monthly Return Filing", null, "COMPLIANCE", "MONTHLY", "5000", null, "7", null, null),
                row("gst monthly return filing", null, "COMPLIANCE", "MONTHLY", "5000", null, "7", null, null),
                row("Payroll", null, "PAYROLL", "WEEKLY", "-10", "ten", "40", null, null),
                row("TDS Quarterly Return", null, "TAX", "QUARTERLY", "7500", null, null, null, null),
                row("Company Incorporation", null, "ADVISORY", "ONE_OFF", "25000", null, "1", null, null),
                row(null, "No name", "TAX", "MONTHLY", "100", null, "1", null, null));

        assertThatThrownBy(() -> importService.importServices(workbook))
                .isInstanceOfSatisfying(SpreadsheetValidationException.class, e ->
                        assertThat(e.getErrors()).extracting(ErrorResponse.FieldError::getField,
                                        ErrorResponse.FieldError::getMessage)
                                .containsExactly(
                                        tuple("Services!A2", "Service Name: the catalog already has a service "
                                                + "named Statutory Audit"),
                                        tuple("Services!A4", "Service Name: repeats the Service Name of row 3; "
                                                + "give each service its own"),
                                        tuple("Services!C5", "Category: must be one of COMPLIANCE, TAX, AUDIT, "
                                                + "ACCOUNTING, ADVISORY, CERTIFICATION"),
                                        tuple("Services!D5", "Billing Frequency: must be one of ONE_OFF, MONTHLY, "
                                                + "QUARTERLY, ANNUAL"),
                                        tuple("Services!E5", "Standard Fee: must not be negative"),
                                        tuple("Services!F5", "GST %: must be a number, such as 10, 12.5 or 10%"),
                                        tuple("Services!G5", "Invoice Day: must be between 1 and 31"),
                                        tuple("Services!G6", "Invoice Day: must be given for MONTHLY, QUARTERLY "
                                                + "and ANNUAL billing"),
                                        tuple("Services!H6", "Invoice Month of Quarter: must be given for QUARTERLY "
                                                + "billing, and left blank otherwise"),
                                        tuple("Services!D7", "Billing Frequency: is ONE_OFF, so leave Invoice Day, "
                                                + "Invoice Month of Quarter and Invoice Month blank"),
                                        tuple("Services!A8", "Service Name: must not be blank")));

        verify(serviceCatalogService, never()).createService(any());
    }

    @Test
    @DisplayName("refuses a workbook without a Services sheet, or with its headings changed")
    void refusesWrongLayout() {
        XlsxWorkbook noSheet = new XlsxWorkbook();
        noSheet.addSheet("Sheet1").addRow(HEADINGS);
        XlsxWorkbook wrongHeadings = new XlsxWorkbook();
        wrongHeadings.addSheet("Services").addRow(headings("Name", "Description", "Category", "Billing Frequency",
                "Fee", "GST %", "Invoice Day", "Invoice Month of Quarter", "Invoice Month"));

        assertThatThrownBy(() -> importService.importServices(noSheet.toBytes()))
                .isInstanceOf(SpreadsheetValidationException.class)
                .hasMessageContaining("no Services sheet");
        assertThatThrownBy(() -> importService.importServices(wrongHeadings.toBytes()))
                .isInstanceOfSatisfying(SpreadsheetValidationException.class, e ->
                        assertThat(e.getErrors()).extracting(ErrorResponse.FieldError::getField)
                                .containsExactly("Services!A1", "Services!E1"));
    }

    @Test
    @DisplayName("refuses more services than one file may import")
    void refusesTooManyServices() {
        Cell[][] rows = IntStream.rangeClosed(1, ServiceCatalogImportServiceImpl.MAX_SERVICES + 1)
                .mapToObj(n -> row("Service " + n, null, "TAX", "ONE_OFF", "100", null, null, null, null))
                .toArray(Cell[][]::new);

        assertThatThrownBy(() -> importService.importServices(workbook(rows)))
                .isInstanceOf(SpreadsheetValidationException.class)
                .hasMessageContaining("import at most 500 per file");

        verify(catalogServiceRepository, never()).findAll();
    }

    private static byte[] workbook(Cell[]... rows) {
        XlsxWorkbook workbook = new XlsxWorkbook();
        XlsxWorkbook.Sheet sheet = workbook.addSheet("Services").addRow(HEADINGS);
        Arrays.stream(rows).forEach(sheet::addRow);
        return workbook.toBytes();
    }

    private static Cell[] row(String... values) {
        return Arrays.stream(values).map(Cell::text).toArray(Cell[]::new);
    }

    private static Cell[] headings(String... headings) {
        return Arrays.stream(headings).map(Cell::bold).toArray(Cell[]::new);
    }

    private static CatalogService stored(String name) {
        CatalogService service = new CatalogService();
        service.setId(UUID.randomUUID());
        service.setServiceName(name);
        return service;
    }

    private static CatalogServiceResponse created(CatalogServiceRequest request) {
        return CatalogServiceResponse.builder()
                .id(UUID.randomUUID())
                .serviceName(request.serviceName())
                .category(request.category())
                .billingFrequency(request.billingFrequency())
                .standardFee(request.standardFee())
                .build();
    }
}
