package com.psc.cl.notification.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.psc.cl.globalsettings.model.FirmDetails;
import com.psc.cl.ledger.dto.ClientLedgerSummaryResponse;
import com.psc.cl.ledger.dto.CompanyLedgerSummaryResponse;
import com.psc.cl.ledger.dto.LastPayment;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

class LedgerSummaryEmailTest {

    private static final LocalDate AS_OF = LocalDate.of(2026, 10, 14);

    private static final String ATTACHMENT = "Mehta_Textiles_Pvt_Ltd_Ledger_FY2026-27.xlsx";

    @ParameterizedTest
    @CsvSource({
            "0, ₹0.00",
            "999.5, ₹999.50",
            "1000, '₹1,000.00'",
            "123456.78, '₹1,23,456.78'",
            "12345678.9, '₹1,23,45,678.90'",
            "-24235, '-₹24,235.00'"})
    @DisplayName("writes rupees with Indian digit grouping")
    void writesRupees(String amount, String expected) {
        assertThat(LedgerSummaryEmail.rupees(new BigDecimal(amount))).isEqualTo(expected);
    }

    @Test
    @DisplayName("writes the year's figures, last payment and attachment into both bodies, signed by the firm")
    void writesSummary() {
        LedgerSummaryEmail email = LedgerSummaryEmail.forCompany(
                summary("Mehta Textiles Pvt Ltd", "24235.00", true), "Rohan", firm(), AS_OF, ATTACHMENT);

        assertThat(email.subject())
                .isEqualTo("Sharma & Associates: Your ledger summary for Mehta Textiles Pvt Ltd, FY 2026-27");
        assertThat(email.html())
                .contains("Dear Rohan,")
                .contains("Sharma &amp; Associates")
                .contains("(1 Apr 2026 to 31 Mar 2027), as of 14 Oct 2026")
                .contains("₹39,235.00")
                .contains("Amount pending")
                .contains("₹24,235.00")
                .contains("Last payment received: ₹15,000.00 on 12 Oct 2026.")
                .contains(ATTACHMENT)
                .contains("GSTIN 27AAAPL1234C1ZV");
        assertThat(email.text())
                .contains("Dear Rohan,")
                .contains("Total billed:       ₹39,235.00")
                .contains("Amount pending: ₹24,235.00")
                .contains("Regards,\nSharma & Associates");
    }

    @Test
    @DisplayName("escapes values taken from the data in the HTML body")
    void escapesHtml() {
        LedgerSummaryEmail email = LedgerSummaryEmail.forCompany(summary("<b>Mehta</b> & Sons", "0.00", false),
                "<script>", null, AS_OF, ATTACHMENT);

        assertThat(email.html())
                .doesNotContain("<b>Mehta</b>")
                .doesNotContain("<script>")
                .contains("&lt;b&gt;Mehta&lt;/b&gt; &amp; Sons")
                .contains("&lt;script&gt;");
    }

    @Test
    @DisplayName("shows a credit as a positive amount in credit, and leaves out a last payment there was not")
    void writesCredit() {
        LedgerSummaryEmail email = LedgerSummaryEmail.forCompany(
                summary("Mehta Textiles Pvt Ltd", "-500.00", false), "Rohan", null, AS_OF, ATTACHMENT);

        assertThat(email.subject()).isEqualTo("Your ledger summary for Mehta Textiles Pvt Ltd, FY 2026-27");
        assertThat(email.html()).contains("Amount in credit").contains("₹500.00")
                .doesNotContain("Last payment received")
                .doesNotContain("By company");
        assertThat(email.text()).contains("Amount in credit: ₹500.00").contains("Accounts team");
    }

    @Test
    @DisplayName("summarises a client across companies, with a line per company and the paying company named")
    void writesClientSummary() {
        ClientLedgerSummaryResponse summary = ClientLedgerSummaryResponse.builder()
                .clientName("Rohan Mehta")
                .financialYear("2026-27")
                .fromDate(LocalDate.of(2026, 4, 1))
                .toDate(LocalDate.of(2027, 3, 31))
                .totalBilled(new BigDecimal("50000.00"))
                .totalDiscount(new BigDecimal("2000.00"))
                .totalPaymentReceived(new BigDecimal("20000.00"))
                .pending(new BigDecimal("30000.00"))
                .lastPayment(LastPayment.builder()
                        .date(LocalDate.of(2026, 10, 12))
                        .amount(new BigDecimal("15000.00"))
                        .companyName("Mehta Textiles Pvt Ltd")
                        .build())
                .build();
        List<LedgerSummaryEmail.CompanyLine> companies = List.of(
                new LedgerSummaryEmail.CompanyLine("Mehta Textiles Pvt Ltd", new BigDecimal("39235.00"),
                        new BigDecimal("15000.00"), new BigDecimal("24235.00")),
                new LedgerSummaryEmail.CompanyLine("Rohan Mehta & Sons", new BigDecimal("10765.00"),
                        new BigDecimal("11000.00"), new BigDecimal("-235.00")));

        LedgerSummaryEmail email = LedgerSummaryEmail.forClient(summary, companies, "Rohan", firm(), AS_OF,
                "Rohan_Mehta_Ledger_FY2026-27.xlsx");

        assertThat(email.subject())
                .isEqualTo("Sharma & Associates: Your ledger summary for all of your companies, FY 2026-27");
        assertThat(email.html())
                .contains("your account across all 2 of your companies")
                .contains("₹50,000.00")
                .contains("By company")
                .contains("Mehta Textiles Pvt Ltd")
                .contains("Rohan Mehta &amp; Sons")
                .contains("₹235.00 in credit")
                .contains("Last payment received: ₹15,000.00 on 12 Oct 2026 from Mehta Textiles Pvt Ltd.")
                .contains("Rohan_Mehta_Ledger_FY2026-27.xlsx");
        assertThat(email.text())
                .contains("By company:")
                .contains("- Mehta Textiles Pvt Ltd: billed ₹39,235.00, received ₹15,000.00, "
                        + "pending ₹24,235.00")
                .contains("- Rohan Mehta & Sons: billed ₹10,765.00, received ₹11,000.00, "
                        + "pending ₹235.00 in credit");
    }

    @Test
    @DisplayName("frames a payment reminder around the amount pending, with the same figures and company lines")
    void writesPaymentReminder() {
        LedgerSummaryEmail email = LedgerSummaryEmail.paymentReminderForClient(reminderSummary("24235.00"),
                new BigDecimal("24235.00"), reminderCompanies(), "Rohan", firm(), AS_OF,
                "Rohan_Mehta_Ledger_FY2026-27.xlsx");

        assertThat(email.subject()).isEqualTo("Sharma & Associates: Payment reminder: \u20B924,235.00 pending");
        assertThat(email.html())
                .contains("Payment reminder &middot; FY 2026-27")
                .contains("This is a friendly reminder that <strong>\u20B924,235.00</strong> is pending on your "
                        + "account as of 14 Oct 2026")
                .contains("Total billed")
                .contains("Amount pending")
                .doesNotContain("Balance from earlier years")
                .contains("By company")
                .contains("Please arrange payment of the pending amount")
                .doesNotContain("Ledger summary &middot;");
        assertThat(email.text())
                .contains("This is a friendly reminder that \u20B924,235.00 is pending on your account as of "
                        + "14 Oct 2026. The details for the financial year 2026-27 (1 Apr 2026 to 31 Mar 2027) "
                        + "are below.")
                .contains("Payments received:  \u20B915,000.00")
                .contains("Amount pending: \u20B924,235.00")
                .contains("- Mehta Textiles Pvt Ltd: billed \u20B939,235.00")
                .contains("If you have already paid, please reply with the payment details");
    }

    @Test
    @DisplayName("adds the balance from earlier years to the year's, so the figures come to the amount asked for")
    void writesPaymentReminderWithEarlierBalance() {
        LedgerSummaryEmail email = LedgerSummaryEmail.paymentReminderForClient(reminderSummary("-500.00"),
                new BigDecimal("12000.00"), reminderCompanies(), "Rohan", null, AS_OF,
                "Rohan_Mehta_Ledger_FY2026-27.xlsx");

        assertThat(email.subject()).isEqualTo("Payment reminder: \u20B912,000.00 pending");
        assertThat(email.html())
                .contains("<strong>\u20B912,000.00</strong> is pending")
                .contains("Balance for FY 2026-27")
                .contains("\u20B9500.00 in credit")
                .contains("Balance from earlier years")
                .contains("\u20B912,500.00")
                .contains("Total amount pending");
        assertThat(email.text())
                .contains("Balance for FY 2026-27: \u20B9500.00 in credit")
                .contains("Balance from earlier years: \u20B912,500.00")
                .contains("Total amount pending: \u20B912,000.00");
    }

    private static ClientLedgerSummaryResponse reminderSummary(String pending) {
        return ClientLedgerSummaryResponse.builder()
                .financialYear("2026-27")
                .fromDate(LocalDate.of(2026, 4, 1))
                .toDate(LocalDate.of(2027, 3, 31))
                .totalBilled(new BigDecimal("39235.00"))
                .totalDiscount(new BigDecimal("1750.00"))
                .totalPaymentReceived(new BigDecimal("15000.00"))
                .pending(new BigDecimal(pending))
                .build();
    }

    private static List<LedgerSummaryEmail.CompanyLine> reminderCompanies() {
        return List.of(new LedgerSummaryEmail.CompanyLine("Mehta Textiles Pvt Ltd", new BigDecimal("39235.00"),
                new BigDecimal("15000.00"), new BigDecimal("24235.00")));
    }

    private static CompanyLedgerSummaryResponse summary(String companyName, String pending, boolean paid) {
        return CompanyLedgerSummaryResponse.builder()
                .companyName(companyName)
                .financialYear("2026-27")
                .fromDate(LocalDate.of(2026, 4, 1))
                .toDate(LocalDate.of(2027, 3, 31))
                .totalBilled(new BigDecimal("39235.00"))
                .totalDiscount(new BigDecimal("1750.00"))
                .totalPaymentReceived(new BigDecimal("15000.00"))
                .pending(new BigDecimal(pending))
                .lastPayment(paid ? LastPayment.builder()
                        .date(LocalDate.of(2026, 10, 12))
                        .amount(new BigDecimal("15000.00"))
                        .build() : null)
                .build();
    }

    private static FirmDetails firm() {
        return FirmDetails.builder()
                .firmName("Sharma & Associates")
                .email("accounts@sharma.example")
                .phone("+91 22 4000 1234")
                .address("12 Marine Drive, Mumbai")
                .gstin("27AAAPL1234C1ZV")
                .build();
    }
}
