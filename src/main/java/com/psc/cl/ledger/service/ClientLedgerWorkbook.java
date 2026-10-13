package com.psc.cl.ledger.service;

import com.psc.cl.ledger.dto.ClientLedgerSummaryResponse;
import com.psc.cl.ledger.dto.LastPayment;
import com.psc.cl.ledger.model.LedgerEntry;
import com.psc.cl.ledger.model.LedgerEntryType;
import com.psc.cl.ledger.model.PaymentMethod;
import com.psc.cl.ledger.service.XlsxWorkbook.Cell;
import com.psc.cl.ledger.service.XlsxWorkbook.Sheet;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Lays out a client's ledger for one financial year as an Excel workbook: a Summary sheet with the
 * same figures as the client's ledger summary, and a Ledger sheet with every entry of the year in
 * date order, a running balance, and a totals row.
 */
final class ClientLedgerWorkbook {

    static final String SUMMARY_SHEET = "Summary";

    static final String LEDGER_SHEET = "Ledger";

    /** Headings of the Ledger sheet, column A onwards; {@link #entryRow} fills them in this order. */
    static final List<String> LEDGER_COLUMNS = List.of("Date", "Company", "Entry Type", "Service",
            "Billing Frequency", "Service Fee", "Discount %", "Discount Amount", "Taxable Amount", "GST %",
            "GST Amount", "Debit", "Credit", "Balance", "Payment Method", "Payment Reference", "Comment",
            "Posted On");

    private static final int[] LEDGER_WIDTHS = {13, 32, 15, 36, 17, 13, 11, 16, 15, 8, 13, 14, 14, 14, 16, 22, 40, 13};

    private static final int[] SUMMARY_WIDTHS = {26, 36};

    private static final BigDecimal ZERO = new BigDecimal("0.00");

    private ClientLedgerWorkbook() {
        // utility class
    }

    /**
     * Writes the workbook.
     *
     * @param summary      the client's summary for the year, which fills the Summary sheet
     * @param entries      every entry of the year, in the order the Ledger sheet lists them
     * @param companyNames the name of each of the client's companies, by identifier
     * @param generatedOn  the firm's calendar day the export was made
     * @return the content of the .xlsx file
     */
    static byte[] write(ClientLedgerSummaryResponse summary, List<LedgerEntry> entries,
                        Map<UUID, String> companyNames, LocalDate generatedOn) {
        XlsxWorkbook workbook = new XlsxWorkbook();
        addSummary(workbook.addSheet(SUMMARY_SHEET, SUMMARY_WIDTHS), summary, generatedOn);
        addLedger(workbook.addSheet(LEDGER_SHEET, LEDGER_WIDTHS).freezeHeaderRow(), entries, companyNames);
        return workbook.toBytes();
    }

    private static void addSummary(Sheet sheet, ClientLedgerSummaryResponse summary, LocalDate generatedOn) {
        sheet.addRow(Cell.bold("Client Ledger"))
                .addBlankRow()
                .addRow(Cell.text("Client"), Cell.text(summary.getClientName()))
                .addRow(Cell.text("Financial Year"), Cell.text(summary.getFinancialYear()))
                .addRow(Cell.text("From"), Cell.date(summary.getFromDate()))
                .addRow(Cell.text("To"), Cell.date(summary.getToDate()))
                .addRow(Cell.text("Generated On"), Cell.date(generatedOn))
                .addBlankRow()
                .addRow(Cell.text("Total Billed"), Cell.amount(summary.getTotalBilled()))
                .addRow(Cell.text("Total Discount"), Cell.amount(summary.getTotalDiscount()))
                .addRow(Cell.text("Total Payment Received"), Cell.amount(summary.getTotalPaymentReceived()))
                .addRow(Cell.bold("Pending"), Cell.boldAmount(summary.getPending()));

        LastPayment lastPayment = summary.getLastPayment();
        if (lastPayment != null) {
            sheet.addBlankRow()
                    .addRow(Cell.text("Last Payment Date"), Cell.date(lastPayment.getDate()))
                    .addRow(Cell.text("Last Payment Amount"), Cell.amount(lastPayment.getAmount()))
                    .addRow(Cell.text("Last Payment From"), Cell.text(lastPayment.getCompanyName()));
        }
    }

    private static void addLedger(Sheet sheet, List<LedgerEntry> entries, Map<UUID, String> companyNames) {
        sheet.addRow(LEDGER_COLUMNS.stream().map(Cell::bold).toArray(Cell[]::new));

        // Charges are stored positive and payments negative, so the running sum is what is still owed.
        BigDecimal balance = ZERO;
        BigDecimal discount = ZERO;
        BigDecimal taxable = ZERO;
        BigDecimal gst = ZERO;
        BigDecimal debit = ZERO;
        BigDecimal credit = ZERO;
        for (LedgerEntry entry : entries) {
            balance = balance.add(entry.getTotalAmount());
            if (entry.getEntryType() == LedgerEntryType.PAYMENT) {
                credit = credit.add(entry.getTotalAmount().negate());
            } else {
                discount = discount.add(entry.getDiscountAmount());
                taxable = taxable.add(entry.getTaxableAmount());
                gst = gst.add(entry.getGstAmount());
                debit = debit.add(entry.getTotalAmount());
            }
            sheet.addRow(entryRow(entry, companyNames.get(entry.getCompanyId()), balance));
        }

        sheet.addRow(Cell.bold("Total"), Cell.empty(), Cell.empty(), Cell.empty(), Cell.empty(), Cell.empty(),
                Cell.empty(), Cell.boldAmount(discount), Cell.boldAmount(taxable), Cell.empty(),
                Cell.boldAmount(gst), Cell.boldAmount(debit), Cell.boldAmount(credit), Cell.boldAmount(balance));
    }

    private static Cell[] entryRow(LedgerEntry entry, String companyName, BigDecimal balance) {
        boolean payment = entry.getEntryType() == LedgerEntryType.PAYMENT;
        return new Cell[] {
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
                Cell.date(entry.getEntryDate())
        };
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
}
