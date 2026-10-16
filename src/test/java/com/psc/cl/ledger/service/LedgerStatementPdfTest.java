package com.psc.cl.ledger.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import com.psc.cl.ledger.dto.ClientLedgerSummaryResponse;
import com.psc.cl.ledger.dto.CompanyLedgerSummaryResponse;
import com.psc.cl.ledger.dto.LastPayment;
import com.psc.cl.ledger.model.LedgerEntry;
import com.psc.cl.ledger.model.LedgerEntryType;
import com.psc.cl.ledger.model.PaymentMethod;
import com.psc.cl.servicecatalog.model.BillingFrequency;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The statement fonts are not on the test classpath, so these statements are written in Helvetica,
 * with amounts in "Rs.".
 */
class LedgerStatementPdfTest {

    private static final UUID TEXTILES_ID = UUID.fromString("4c1f8a52-2d6b-4e93-b7a0-9e5d3c1f6a28");

    private static final UUID HUF_ID = UUID.fromString("1e7b3c90-4a2d-4f68-b5c1-8d9e0f2a3b47");

    private static final Map<UUID, String> COMPANY_NAMES =
            Map.of(TEXTILES_ID, "Mehta Textiles Pvt Ltd", HUF_ID, "Rohan Mehta HUF");

    private static final LocalDate GENERATED_ON = LocalDate.of(2026, 10, 14);

    @Test
    @DisplayName("opens a client's statement with the summary and a line per company, then a page per company")
    void writesClientStatement() throws IOException {
        List<LedgerEntry> entries = List.of(
                charge(TEXTILES_ID, LocalDate.of(2026, 9, 7), "Monthly Return Filing", "5605.00"),
                payment(TEXTILES_ID, LocalDate.of(2026, 9, 10), "-3000.00"),
                charge(HUF_ID, LocalDate.of(2026, 9, 20), "Company Incorporation", "28025.00"));

        byte[] pdf = LedgerStatementPdf.forClient(summary(), entries, COMPANY_NAMES, GENERATED_ON);

        assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        PdfReader reader = new PdfReader(pdf);
        // The summary, then one page for each company, by name.
        assertThat(reader.getNumberOfPages()).isEqualTo(3);
        String summaryPage = text(reader, 1);
        assertThat(summaryPage)
                .contains("Ledger Statement")
                .contains("Rohan Mehta")
                .contains("2026-27")
                .contains("Total billed").contains("Rs. 33,630.00")
                .contains("Payments received").contains("Rs. 3,000.00")
                .contains("Amount pending").contains("Rs. 30,630.00")
                .contains("Last payment received: Rs. 3,000.00 on 10 Sep 2026 from Mehta Textiles Pvt Ltd")
                .contains("By company");

        String textiles = text(reader, 2);
        assertThat(textiles)
                .contains("Mehta Textiles Pvt Ltd")
                .contains("Total Fee").contains("Amount Paid").contains("Balance")
                .contains("Monthly Return Filing")
                .contains("Rs. 5,605.00")
                .contains("UPI")
                // What is left after the payment, which is also the closing balance.
                .contains("Rs. 2,605.00")
                .doesNotContain("Company Incorporation");
        assertThat(text(reader, 3))
                .contains("Rohan Mehta HUF")
                .contains("Company Incorporation")
                .contains("Rs. 28,025.00");
    }

    @Test
    @DisplayName("shows each charge as one total fee: no discount, taxable amount or GST anywhere")
    void foldsFeeBreakdownIntoTotalFee() throws IOException {
        List<LedgerEntry> entries = List.of(charge(TEXTILES_ID, LocalDate.of(2026, 9, 7), "Monthly Return Filing",
                "5605.00"));

        PdfReader reader = new PdfReader(LedgerStatementPdf.forClient(summary(), entries, COMPANY_NAMES,
                GENERATED_ON));

        for (int page = 1; page <= reader.getNumberOfPages(); page++) {
            assertThat(text(reader, page))
                    .doesNotContain("Discount")
                    .doesNotContain("Taxable")
                    .doesNotContain("GST")
                    .doesNotContain("5,000.00");
        }
    }

    @Test
    @DisplayName("shows an opening balance as its own figure and line, not as billed, and counts it in pending")
    void showsOpeningBalance() throws IOException {
        LedgerEntry opening = entry(TEXTILES_ID, LedgerEntryType.OPENING_BALANCE, LocalDate.of(2026, 4, 1),
                "25000.00");
        LedgerEntry credit = entry(HUF_ID, LedgerEntryType.OPENING_BALANCE, LocalDate.of(2026, 4, 1), "-2000.00");
        List<LedgerEntry> entries = List.of(opening, credit,
                charge(TEXTILES_ID, LocalDate.of(2026, 9, 7), "Monthly Return Filing", "5605.00"));
        ClientLedgerSummaryResponse summary = ClientLedgerSummaryResponse.builder()
                .clientName("Rohan Mehta")
                .financialYear("2026-27")
                .fromDate(LocalDate.of(2026, 4, 1))
                .toDate(LocalDate.of(2027, 3, 31))
                .totalBilled(new BigDecimal("5605.00"))
                .openingBalance(new BigDecimal("23000.00"))
                .totalPaymentReceived(new BigDecimal("0.00"))
                .pending(new BigDecimal("28605.00"))
                .build();

        PdfReader reader = new PdfReader(LedgerStatementPdf.forClient(summary, entries, COMPANY_NAMES,
                GENERATED_ON));

        assertThat(text(reader, 1))
                .contains("Total billed").contains("Rs. 5,605.00")
                .contains("Opening balance").contains("Rs. 23,000.00")
                .contains("Amount pending").contains("Rs. 28,605.00")
                .contains("Rs. 2,000.00 in credit");
        assertThat(text(reader, 2))
                .contains("Opening balance Rs. 25,000.00")
                // 5,605.00 billed + 25,000.00 brought over.
                .contains("Pending Rs. 30,605.00")
                .contains("Rs. 30,605.00");
        assertThat(text(reader, 3)).contains("Opening balance Rs. 2,000.00 in credit");
    }

    @Test
    @DisplayName("gives a company with no entries in the year its page, saying so")
    void writesIdleCompany() throws IOException {
        PdfReader reader = new PdfReader(LedgerStatementPdf.forClient(summary(), List.of(), COMPANY_NAMES,
                GENERATED_ON));

        assertThat(reader.getNumberOfPages()).isEqualTo(3);
        assertThat(text(reader, 3))
                .contains("Rohan Mehta HUF")
                .contains("No charges or payments in this financial year.");
    }

    @Test
    @DisplayName("writes a company's statement as its summary followed by its details, without a company breakdown")
    void writesCompanyStatement() throws IOException {
        List<LedgerEntry> entries = List.of(
                charge(TEXTILES_ID, LocalDate.of(2026, 9, 7), "Monthly Return Filing", "5605.00"),
                payment(TEXTILES_ID, LocalDate.of(2026, 9, 10), "-3000.00"));

        PdfReader reader = new PdfReader(LedgerStatementPdf.forCompany("Rohan Mehta", companySummary(), entries,
                GENERATED_ON));

        assertThat(reader.getNumberOfPages()).isEqualTo(1);
        assertThat(text(reader, 1))
                .contains("Rohan Mehta")
                .contains("Mehta Textiles Pvt Ltd")
                .contains("Summary")
                .contains("Details")
                .contains("Monthly Return Filing")
                .contains("Rs. 2,605.00")
                .doesNotContain("By company");
    }

    @Test
    @DisplayName("writes amounts with Indian digit grouping")
    void groupsDigitsTheIndianWay() {
        assertThat(LedgerStatementPdf.rupees(new BigDecimal("1234567.5"), "₹")).isEqualTo("₹12,34,567.50");
        assertThat(LedgerStatementPdf.rupees(new BigDecimal("999"), "Rs. ")).isEqualTo("Rs. 999.00");
        assertThat(LedgerStatementPdf.rupees(new BigDecimal("-1000"), "₹")).isEqualTo("-₹1,000.00");
    }

    private static String text(PdfReader reader, int page) throws IOException {
        return new PdfTextExtractor(reader).getTextFromPage(page);
    }

    private static ClientLedgerSummaryResponse summary() {
        return ClientLedgerSummaryResponse.builder()
                .clientId(UUID.randomUUID())
                .clientName("Rohan Mehta")
                .financialYear("2026-27")
                .fromDate(LocalDate.of(2026, 4, 1))
                .toDate(LocalDate.of(2027, 3, 31))
                .totalBilled(new BigDecimal("33630.00"))
                .totalPaymentReceived(new BigDecimal("3000.00"))
                .pending(new BigDecimal("30630.00"))
                .totalDiscount(new BigDecimal("1500.00"))
                .lastPayment(LastPayment.builder()
                        .date(LocalDate.of(2026, 9, 10))
                        .amount(new BigDecimal("3000.00"))
                        .companyId(TEXTILES_ID)
                        .companyName("Mehta Textiles Pvt Ltd")
                        .build())
                .build();
    }

    private static CompanyLedgerSummaryResponse companySummary() {
        return CompanyLedgerSummaryResponse.builder()
                .clientId(UUID.randomUUID())
                .companyId(TEXTILES_ID)
                .companyName("Mehta Textiles Pvt Ltd")
                .financialYear("2026-27")
                .fromDate(LocalDate.of(2026, 4, 1))
                .toDate(LocalDate.of(2027, 3, 31))
                .totalBilled(new BigDecimal("5605.00"))
                .totalPaymentReceived(new BigDecimal("3000.00"))
                .pending(new BigDecimal("2605.00"))
                .totalDiscount(new BigDecimal("250.00"))
                .build();
    }

    /** A charge of a 5,000.00 fee less a 5% discount, plus 18% GST. */
    private static LedgerEntry charge(UUID companyId, LocalDate dueDate, String serviceName, String total) {
        LedgerEntry entry = entry(companyId, LedgerEntryType.SERVICE_CHARGE, dueDate, total);
        entry.setServiceName(serviceName);
        entry.setServiceType(BillingFrequency.MONTHLY);
        entry.setServiceFee(new BigDecimal("5000.00"));
        entry.setDiscountPercent(new BigDecimal("5.00"));
        entry.setDiscountAmount(new BigDecimal("250.00"));
        entry.setTaxableAmount(new BigDecimal("4750.00"));
        entry.setGstRatePercent(new BigDecimal("18.00"));
        entry.setGstAmount(new BigDecimal("855.00"));
        return entry;
    }

    private static LedgerEntry payment(UUID companyId, LocalDate dateReceived, String total) {
        LedgerEntry entry = entry(companyId, LedgerEntryType.PAYMENT, dateReceived, total);
        entry.setPaymentMethod(PaymentMethod.UPI);
        entry.setPaymentReference("UTR-1");
        return entry;
    }

    private static LedgerEntry entry(UUID companyId, LedgerEntryType type, LocalDate dueDate, String total) {
        LedgerEntry entry = new LedgerEntry();
        entry.setId(UUID.randomUUID());
        entry.setCompanyId(companyId);
        entry.setEntryType(type);
        entry.setDueDate(dueDate);
        entry.setEntryDate(GENERATED_ON);
        entry.setTotalAmount(new BigDecimal(total));
        return entry;
    }
}
