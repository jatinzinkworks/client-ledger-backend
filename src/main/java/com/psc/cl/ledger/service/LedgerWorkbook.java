package com.psc.cl.ledger.service;

import com.psc.cl.ledger.dto.ClientLedgerSummaryResponse;
import com.psc.cl.ledger.dto.CompanyLedgerSummaryResponse;
import com.psc.cl.ledger.dto.LastPayment;
import com.psc.cl.ledger.dto.OpeningBalance;
import com.psc.cl.ledger.model.LedgerEntry;
import com.psc.cl.ledger.model.LedgerEntryType;
import com.psc.cl.ledger.model.PaymentMethod;
import com.psc.cl.service.XlsxWorkbook;
import com.psc.cl.service.XlsxWorkbook.Cell;
import com.psc.cl.service.XlsxWorkbook.Sheet;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Lays out a ledger for one financial year as an Excel workbook, for a whole client or one of
 * their companies: a Summary sheet with the same figures as the matching ledger summary, and a
 * Ledger sheet with every entry of the year in date order, a running balance, and a totals row.
 * A client's Ledger sheet names each entry's company, and is followed by one sheet per company,
 * laid out like a company's own Ledger sheet: without that column, below the company's opening
 * balance and its date.
 */
final class LedgerWorkbook {

    static final String SUMMARY_SHEET = "Summary";

    static final String LEDGER_SHEET = "Ledger";

    /** Headings of a client's Ledger sheet, column A onwards; {@link #entryRow} fills them in this order. */
    static final List<String> LEDGER_COLUMNS = List.of("Date", "Company", "Entry Type", "Service",
            "Billing Frequency", "Service Fee", "Discount %", "Discount Amount", "Taxable Amount", "GST %",
            "GST Amount", "Debit", "Credit", "Balance", "Payment Method", "Payment Reference", "Comment",
            "Posted On");

    /** Position of the Company column, left out of a single company's ledger. */
    private static final int COMPANY_COLUMN = LEDGER_COLUMNS.indexOf("Company");

    private static final int[] LEDGER_WIDTHS = {13, 32, 15, 36, 17, 13, 11, 16, 15, 8, 13, 14, 14, 14, 16, 22, 40, 13};

    private static final int[] SUMMARY_WIDTHS = {26, 36};

    /** Width of column A on a company's ledger sheet, wide enough for the opening balance labels above the Date. */
    private static final int COMPANY_SHEET_LABEL_WIDTH = 21;

    /** Rows of a company's ledger sheet kept in view: the opening balance, its date, a blank row and the headings. */
    private static final int COMPANY_SHEET_FROZEN_ROWS = 4;

    private static final BigDecimal ZERO = new BigDecimal("0.00");

    private LedgerWorkbook() {
        // utility class
    }

    /**
     * Writes the workbook of a client's ledger across all of their companies, with a sheet for each
     * company after the combined Ledger sheet, in order of company name. Each company sheet is named
     * after its company and keeps its own running balance and totals; a company with no entries in
     * the year still gets one, with only headings and zero totals. Above its ledger, each company
     * sheet shows the company's opening balance and its date, whatever the year it falls in, or None.
     *
     * @param summary      the client's summary for the year, which fills the Summary sheet
     * @param entries      every entry of the year, in the order the Ledger sheets list them
     * @param companyNames the name of each of the client's companies, by identifier
     * @param openings     the opening balance of each company that has one, by identifier
     * @param generatedOn  the firm's calendar day the export was made
     * @return the content of the .xlsx file
     */
    static byte[] forClient(ClientLedgerSummaryResponse summary, List<LedgerEntry> entries,
                            Map<UUID, String> companyNames, Map<UUID, OpeningBalance> openings,
                            LocalDate generatedOn) {
        List<Cell[]> parties = List.<Cell[]>of(row(Cell.text("Client"), Cell.text(summary.getClientName())));
        Figures figures = new Figures(summary.getFinancialYear(), summary.getFromDate(), summary.getToDate(),
                summary.getTotalBilled(), summary.getTotalDiscount(), summary.getOpeningBalance(),
                summary.getTotalPaymentReceived(), summary.getPending(), summary.getLastPayment());
        XlsxWorkbook workbook = new XlsxWorkbook();
        addSummary(workbook.addSheet(SUMMARY_SHEET, SUMMARY_WIDTHS), "Client Ledger", parties, figures,
                generatedOn, true);
        addLedger(workbook.addSheet(LEDGER_SHEET, LEDGER_WIDTHS).freezeHeaderRow(), entries, companyNames);
        addCompanySheets(workbook, entries, companyNames, openings);
        return workbook.toBytes();
    }

    /**
     * One sheet per company, in order of name, each with the company's opening balance above only
     * that company's entries.
     */
    private static void addCompanySheets(XlsxWorkbook workbook, List<LedgerEntry> entries,
                                         Map<UUID, String> companyNames, Map<UUID, OpeningBalance> openings) {
        Map<UUID, List<LedgerEntry>> entriesByCompany = entries.stream()
                .collect(Collectors.groupingBy(LedgerEntry::getCompanyId));
        companyNames.entrySet().stream()
                .sorted(Map.Entry.<UUID, String>comparingByValue(String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(Map.Entry.comparingByKey()))
                .forEach(company -> addCompanyLedger(workbook, workbook.sheetName(company.getValue(), "Company"),
                        openings.get(company.getKey()), entriesByCompany.getOrDefault(company.getKey(), List.of())));
    }

    /**
     * Adds a sheet with one company's ledger, without the Company column, below the company's
     * opening balance and its date.
     */
    private static void addCompanyLedger(XlsxWorkbook workbook, String sheetName, OpeningBalance opening,
                                         List<LedgerEntry> entries) {
        int[] widths = withoutCompany(LEDGER_WIDTHS);
        widths[0] = Math.max(widths[0], COMPANY_SHEET_LABEL_WIDTH);
        Sheet sheet = workbook.addSheet(sheetName, widths).freezeRows(COMPANY_SHEET_FROZEN_ROWS);
        addOpeningBalance(sheet, opening);
        addLedger(sheet, entries, null);
    }

    /**
     * The company's opening balance and its date, then a blank row; None when it has none. The rows
     * are written either way, so the ledger's headings are always on the same row.
     */
    private static void addOpeningBalance(Sheet sheet, OpeningBalance opening) {
        sheet.addRow(Cell.text("Opening Balance"), opening == null ? Cell.text("None") : Cell.amount(opening.amount()))
                .addRow(Cell.text("Opening Balance Date"), opening == null ? Cell.empty() : Cell.date(opening.date()))
                .addBlankRow();
    }

    /**
     * Writes the workbook of the ledger of one company of a client. Above its ledger, the Ledger
     * sheet shows the company's opening balance and its date, whatever the year it falls in, or None.
     *
     * @param clientName  the name of the client the company belongs to
     * @param summary     the company's summary for the year, which fills the Summary sheet
     * @param entries     every entry of the year, in the order the Ledger sheet lists them
     * @param opening     the company's opening balance, or null when it has none
     * @param generatedOn the firm's calendar day the export was made
     * @return the content of the .xlsx file
     */
    static byte[] forCompany(String clientName, CompanyLedgerSummaryResponse summary, List<LedgerEntry> entries,
                             OpeningBalance opening, LocalDate generatedOn) {
        List<Cell[]> parties = List.of(row(Cell.text("Client"), Cell.text(clientName)),
                row(Cell.text("Company"), Cell.text(summary.getCompanyName())));
        Figures figures = new Figures(summary.getFinancialYear(), summary.getFromDate(), summary.getToDate(),
                summary.getTotalBilled(), summary.getTotalDiscount(), summary.getOpeningBalance(),
                summary.getTotalPaymentReceived(), summary.getPending(), summary.getLastPayment());
        XlsxWorkbook workbook = new XlsxWorkbook();
        addSummary(workbook.addSheet(SUMMARY_SHEET, SUMMARY_WIDTHS), "Company Ledger", parties, figures,
                generatedOn, false);
        addCompanyLedger(workbook, LEDGER_SHEET, opening, entries);
        return workbook.toBytes();
    }

    /**
     * @param parties     rows naming whose ledger it is
     * @param namesPayer  whether to say which company made the last payment, which only a client's
     *                    ledger needs
     */
    private static void addSummary(Sheet sheet, String title, List<Cell[]> parties, Figures figures,
                                   LocalDate generatedOn, boolean namesPayer) {
        sheet.addRow(Cell.bold(title)).addBlankRow();
        parties.forEach(sheet::addRow);
        sheet.addRow(Cell.text("Financial Year"), Cell.text(figures.financialYear()))
                .addRow(Cell.text("From"), Cell.date(figures.fromDate()))
                .addRow(Cell.text("To"), Cell.date(figures.toDate()))
                .addRow(Cell.text("Generated On"), Cell.date(generatedOn))
                .addBlankRow()
                .addRow(Cell.text("Total Billed"), Cell.amount(figures.totalBilled()))
                .addRow(Cell.text("Total Discount"), Cell.amount(figures.totalDiscount()))
                .addRow(Cell.text("Opening Balance"), Cell.amount(figures.openingBalance()))
                .addRow(Cell.text("Total Payment Received"), Cell.amount(figures.totalPaymentReceived()))
                .addRow(Cell.bold("Pending"), Cell.boldAmount(figures.pending()));

        LastPayment lastPayment = figures.lastPayment();
        if (lastPayment != null) {
            sheet.addBlankRow()
                    .addRow(Cell.text("Last Payment Date"), Cell.date(lastPayment.getDate()))
                    .addRow(Cell.text("Last Payment Amount"), Cell.amount(lastPayment.getAmount()));
            if (namesPayer) {
                sheet.addRow(Cell.text("Last Payment From"), Cell.text(lastPayment.getCompanyName()));
            }
        }
    }

    /**
     * @param companyNames the name of each company by identifier, or null to leave the Company
     *                     column out
     */
    private static void addLedger(Sheet sheet, List<LedgerEntry> entries, Map<UUID, String> companyNames) {
        boolean withCompany = companyNames != null;
        List<Cell> headings = new ArrayList<>(LEDGER_COLUMNS.stream().map(Cell::bold).toList());
        sheet.addRow(columns(headings, withCompany));

        // Charges are stored positive and payments negative, so the running sum is what is still owed.
        BigDecimal balance = ZERO;
        BigDecimal discount = ZERO;
        BigDecimal taxable = ZERO;
        BigDecimal gst = ZERO;
        BigDecimal debit = ZERO;
        BigDecimal credit = ZERO;
        for (LedgerEntry entry : entries) {
            balance = balance.add(entry.getTotalAmount());
            if (entry.getEntryType() == LedgerEntryType.SERVICE_CHARGE) {
                discount = discount.add(entry.getDiscountAmount());
                taxable = taxable.add(entry.getTaxableAmount());
                gst = gst.add(entry.getGstAmount());
            }
            if (isCredit(entry)) {
                credit = credit.add(entry.getTotalAmount().negate());
            } else {
                debit = debit.add(entry.getTotalAmount());
            }
            String companyName = withCompany ? companyNames.get(entry.getCompanyId()) : null;
            sheet.addRow(columns(entryRow(entry, companyName, balance), withCompany));
        }

        List<Cell> totals = new ArrayList<>(Arrays.asList(Cell.bold("Total"), Cell.empty(), Cell.empty(),
                Cell.empty(), Cell.empty(), Cell.empty(), Cell.empty(), Cell.boldAmount(discount),
                Cell.boldAmount(taxable), Cell.empty(), Cell.boldAmount(gst), Cell.boldAmount(debit),
                Cell.boldAmount(credit), Cell.boldAmount(balance)));
        sheet.addRow(columns(totals, withCompany));
    }

    /**
     * Whether an entry goes in the Credit column rather than Debit: a payment, or an opening balance
     * in credit. Both are stored negative, so this is the sign of the total.
     */
    static boolean isCredit(LedgerEntry entry) {
        return entry.getTotalAmount().signum() < 0;
    }

    private static List<Cell> entryRow(LedgerEntry entry, String companyName, BigDecimal balance) {
        boolean payment = isCredit(entry);
        return new ArrayList<>(Arrays.asList(
                Cell.date(entry.getDueDate()),
                Cell.text(companyName),
                Cell.text(label(entry.getEntryType())),
                Cell.text(entry.getServiceName()),
                Cell.text(label(entry.getServiceType())),
                Cell.amount(entry.getServiceFee()),
                Cell.amount(entry.getDiscountPercent()),
                Cell.amount(entry.getDiscountAmount()),
                Cell.amount(entry.getTaxableAmount()),
                Cell.amount(entry.getGstRatePercent()),
                Cell.amount(entry.getGstAmount()),
                Cell.amount(payment ? null : entry.getTotalAmount()),
                Cell.amount(payment ? entry.getTotalAmount().negate() : null),
                Cell.amount(balance),
                Cell.text(paymentMethod(entry.getPaymentMethod())),
                Cell.text(entry.getPaymentReference()),
                Cell.text(entry.getComment()),
                Cell.date(entry.getEntryDate())));
    }

    /** A row laid out for the client's columns, with the Company column dropped when not wanted. */
    private static Cell[] columns(List<Cell> row, boolean withCompany) {
        if (!withCompany) {
            row.remove(COMPANY_COLUMN);
        }
        return row.toArray(Cell[]::new);
    }

    private static int[] withoutCompany(int[] widths) {
        return IntStream.range(0, widths.length)
                .filter(i -> i != COMPANY_COLUMN)
                .map(i -> widths[i])
                .toArray();
    }

    private static Cell[] row(Cell... cells) {
        return cells;
    }

    /** UPI is an acronym, so payment methods are spelt out rather than derived from the constant. */
    static String paymentMethod(PaymentMethod method) {
        if (method == null) {
            return null;
        }
        return switch (method) {
            case BANK_TRANSFER -> "Bank transfer";
            case UPI -> "UPI";
            case CHEQUE -> "Cheque";
            case CASH -> "Cash";
        };
    }

    /** An enum constant as a reader would write it: SERVICE_CHARGE as "Service charge"; null stays null. */
    static String label(Enum<?> value) {
        if (value == null) {
            return null;
        }
        String words = value.name().replace('_', ' ').toLowerCase(Locale.ROOT);
        return Character.toUpperCase(words.charAt(0)) + words.substring(1);
    }

    /** The figures a Summary sheet shows, whichever summary they come from. */
    private record Figures(String financialYear, LocalDate fromDate, LocalDate toDate, BigDecimal totalBilled,
                           BigDecimal totalDiscount, BigDecimal openingBalance, BigDecimal totalPaymentReceived,
                           BigDecimal pending, LastPayment lastPayment) {
    }
}
