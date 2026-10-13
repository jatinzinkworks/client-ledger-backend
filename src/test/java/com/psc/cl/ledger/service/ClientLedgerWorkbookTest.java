package com.psc.cl.ledger.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.psc.cl.ledger.dto.ClientLedgerSummaryResponse;
import com.psc.cl.ledger.dto.LastPayment;
import com.psc.cl.ledger.model.LedgerEntry;
import com.psc.cl.ledger.model.LedgerEntryType;
import com.psc.cl.ledger.model.PaymentMethod;
import com.psc.cl.servicecatalog.model.BillingFrequency;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

class ClientLedgerWorkbookTest {

    private static final UUID TEXTILES_ID = UUID.fromString("4c1f8a52-2d6b-4e93-b7a0-9e5d3c1f6a28");

    private static final UUID HUF_ID = UUID.fromString("1e7b3c90-4a2d-4f68-b5c1-8d9e0f2a3b47");

    private static final Map<UUID, String> COMPANY_NAMES =
            Map.of(TEXTILES_ID, "Mehta Textiles Pvt Ltd", HUF_ID, "Rohan Mehta HUF");

    private static final LocalDate GENERATED_ON = LocalDate.of(2026, 10, 14);

    @Test
    @DisplayName("fills the Summary sheet with the client's figures for the year and their last payment")
    void writesSummarySheet() {
        byte[] xlsx = ClientLedgerWorkbook.write(summary(LastPayment.builder()
                .date(LocalDate.of(2026, 10, 10))
                .amount(new BigDecimal("10000.00"))
                .companyId(HUF_ID)
                .companyName("Rohan Mehta HUF")
                .build()), List.of(), COMPANY_NAMES, GENERATED_ON);

        Map<String, String> parts = XlsxWorkbookTest.parts(xlsx);
        assertThat(parts.get("xl/workbook.xml")).contains("name=\"Summary\"").contains("name=\"Ledger\"");
        assertThat(parts.get("xl/worksheets/sheet1.xml"))
                .contains(">Client Ledger<")
                .contains(">Rohan Mehta<")
                .contains(">2026-27<")
                // 1 April 2026, 31 March 2027 and 14 October 2026 as Excel day numbers.
                .contains("<v>46113</v>")
                .contains("<v>46477</v>")
                .contains("<v>46309</v>")
                .contains(">Total Billed<").contains("<v>39235.00</v>")
                .contains(">Total Discount<").contains("<v>1750.00</v>")
                .contains(">Total Payment Received<").contains("<v>18605.00</v>")
                .contains(">Pending<").contains("<v>20630.00</v>")
                .contains(">Last Payment Date<").contains("<v>46305</v>")
                .contains(">Last Payment Amount<").contains("<v>10000.00</v>")
                .contains(">Last Payment From<");
    }

    @Test
    @DisplayName("leaves the last payment out of the Summary sheet when the year had none")
    void writesSummaryWithoutPayment() {
        byte[] xlsx = ClientLedgerWorkbook.write(summary(null), List.of(), COMPANY_NAMES, GENERATED_ON);

        assertThat(XlsxWorkbookTest.parts(xlsx).get("xl/worksheets/sheet1.xml"))
                .contains(">Pending<")
                .doesNotContain("Last Payment");
    }

    @Test
    @DisplayName("lists every entry with its company, debits and credits, a running balance and totals")
    void writesLedgerSheet() {
        List<LedgerEntry> entries = List.of(
                charge(TEXTILES_ID, LocalDate.of(2026, 9, 7), "GST Monthly Return Filing", BillingFrequency.MONTHLY,
                        "5000.00", "250.00", "4750.00", "855.00", "5605.00"),
                payment(TEXTILES_ID, LocalDate.of(2026, 9, 10), PaymentMethod.UPI, "-3000.00"),
                charge(HUF_ID, LocalDate.of(2026, 9, 20), "Company Incorporation", BillingFrequency.ONE_OFF,
                        "25000.00", "1250.00", "23750.00", "4275.00", "28025.00"));

        String ledger = XlsxWorkbookTest.parts(ClientLedgerWorkbook.write(summary(null), entries, COMPANY_NAMES,
                GENERATED_ON)).get("xl/worksheets/sheet2.xml");

        assertThat(ledger)
                .contains("state=\"frozen\"")
                .contains("<c r=\"A1\" s=\"1\" t=\"inlineStr\"><is><t xml:space=\"preserve\">Date</t></is></c>")
                .contains(">Posted On<")
                // The charge: a debit, and the balance it leaves.
                .contains("<c r=\"B2\" s=\"0\" t=\"inlineStr\"><is><t xml:space=\"preserve\">Mehta Textiles Pvt Ltd")
                .contains(">Service charge<")
                .contains(">Monthly<")
                .contains("<c r=\"L2\" s=\"2\"><v>5605.00</v></c>")
                .doesNotContain("<c r=\"M2\"")
                .contains("<c r=\"N2\" s=\"2\"><v>5605.00</v></c>")
                // The payment: a positive credit, which brings the balance down.
                .contains(">Payment<")
                .doesNotContain("<c r=\"L3\"")
                .contains("<c r=\"M3\" s=\"2\"><v>3000.00</v></c>")
                .contains("<c r=\"N3\" s=\"2\"><v>2605.00</v></c>")
                .contains("<c r=\"O3\" s=\"0\" t=\"inlineStr\"><is><t xml:space=\"preserve\">UPI</t>")
                // The other company's charge, in the same running balance.
                .contains(">Rohan Mehta HUF<")
                .contains(">One off<")
                .contains("<c r=\"N4\" s=\"2\"><v>30630.00</v></c>")
                // Totals: discount, taxable, GST, debit, credit and closing balance.
                .contains("<c r=\"A5\" s=\"1\" t=\"inlineStr\"><is><t xml:space=\"preserve\">Total</t>")
                .contains("<c r=\"H5\" s=\"4\"><v>1500.00</v></c>")
                .contains("<c r=\"I5\" s=\"4\"><v>28500.00</v></c>")
                .contains("<c r=\"K5\" s=\"4\"><v>5130.00</v></c>")
                .contains("<c r=\"L5\" s=\"4\"><v>33630.00</v></c>")
                .contains("<c r=\"M5\" s=\"4\"><v>3000.00</v></c>")
                .contains("<c r=\"N5\" s=\"4\"><v>30630.00</v></c>");
    }

    @Test
    @DisplayName("writes a ledger with only headings and zero totals for a year with no entries")
    void writesEmptyLedger() {
        String ledger = XlsxWorkbookTest.parts(ClientLedgerWorkbook.write(summary(null), List.of(), COMPANY_NAMES,
                GENERATED_ON)).get("xl/worksheets/sheet2.xml");

        assertThat(ledger)
                .contains("<c r=\"A2\" s=\"1\" t=\"inlineStr\"><is><t xml:space=\"preserve\">Total</t>")
                .contains("<c r=\"N2\" s=\"4\"><v>0.00</v></c>");
    }

    @Test
    @DisplayName("labels enum constants as a reader would write them")
    void labelsConstants() {
        assertThat(ClientLedgerWorkbook.label(LedgerEntryType.SERVICE_CHARGE)).isEqualTo("Service charge");
        assertThat(ClientLedgerWorkbook.label(BillingFrequency.ONE_OFF)).isEqualTo("One off");
        assertThat(ClientLedgerWorkbook.label(null)).isNull();
        assertThat(ClientLedgerWorkbook.paymentMethod(PaymentMethod.BANK_TRANSFER)).isEqualTo("Bank transfer");
        assertThat(ClientLedgerWorkbook.paymentMethod(PaymentMethod.UPI)).isEqualTo("UPI");
        assertThat(ClientLedgerWorkbook.paymentMethod(null)).isNull();
    }

    private static ClientLedgerSummaryResponse summary(LastPayment lastPayment) {
        return ClientLedgerSummaryResponse.builder()
                .clientId(UUID.randomUUID())
                .clientName("Rohan Mehta")
                .financialYear("2026-27")
                .fromDate(LocalDate.of(2026, 4, 1))
                .toDate(LocalDate.of(2027, 3, 31))
                .totalBilled(new BigDecimal("39235.00"))
                .totalPaymentReceived(new BigDecimal("18605.00"))
                .pending(new BigDecimal("20630.00"))
                .totalDiscount(new BigDecimal("1750.00"))
                .lastPayment(lastPayment)
                .build();
    }

    private static LedgerEntry charge(UUID companyId, LocalDate dueDate, String serviceName,
                                      BillingFrequency serviceType, String fee, String discount, String taxable,
                                      String gst, String total) {
        LedgerEntry entry = entry(companyId, LedgerEntryType.SERVICE_CHARGE, dueDate, total);
        entry.setServiceName(serviceName);
        entry.setServiceType(serviceType);
        entry.setServiceFee(new BigDecimal(fee));
        entry.setDiscountPercent(new BigDecimal("5.00"));
        entry.setDiscountAmount(new BigDecimal(discount));
        entry.setTaxableAmount(new BigDecimal(taxable));
        entry.setGstRatePercent(new BigDecimal("18.00"));
        entry.setGstAmount(new BigDecimal(gst));
        return entry;
    }

    private static LedgerEntry payment(UUID companyId, LocalDate dateReceived, PaymentMethod method, String total) {
        LedgerEntry entry = entry(companyId, LedgerEntryType.PAYMENT, dateReceived, total);
        entry.setPaymentMethod(method);
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
