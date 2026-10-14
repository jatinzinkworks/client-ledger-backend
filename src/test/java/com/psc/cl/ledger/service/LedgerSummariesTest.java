package com.psc.cl.ledger.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.psc.cl.ledger.dto.CompanyBalance;
import com.psc.cl.ledger.dto.CompanyTotals;
import com.psc.cl.ledger.dto.CompanyYearStatus;
import com.psc.cl.ledger.dto.FinancialYearStatus;
import com.psc.cl.ledger.dto.FinancialYearTotals;
import com.psc.cl.ledger.dto.LedgerSummary;
import com.psc.cl.ledger.dto.LedgerYearStatus;
import com.psc.cl.ledger.dto.PaymentSummary;
import com.psc.cl.ledger.model.FinancialYear;
import com.psc.cl.ledger.model.LedgerEntryType;
import com.psc.cl.ledger.repository.LedgerTotalsRow;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

class LedgerSummariesTest {

    private static final UUID TEXTILES = UUID.fromString("4c1f8a52-2d6b-4e93-b7a0-9e5d3c1f6a28");

    private static final UUID HUF = UUID.fromString("1e7b3c90-4a2d-4f68-b5c1-8d9e0f2a3b47");

    private static final Map<UUID, String> NAMES =
            Map.of(TEXTILES, "Mehta Textiles Pvt Ltd", HUF, "Rohan Mehta HUF");

    /** Two monthly entries of 5605.00 billed to Mehta Textiles in 2026-27. */
    private static final LedgerTotalsRow TEXTILES_2026_27 =
            row(TEXTILES, "2026-27", 2, "10000.00", "500.00", "9500.00", "1710.00", "11210.00");

    /** A one-off of 28025.00 billed to Rohan Mehta HUF in 2025-26. */
    private static final LedgerTotalsRow HUF_2025_26 =
            row(HUF, "2025-26", 1, "25000.00", "1250.00", "23750.00", "4275.00", "28025.00");

    /** A one-off of 28025.00 billed to Rohan Mehta HUF in 2026-27. */
    private static final LedgerTotalsRow HUF_2026_27 =
            row(HUF, "2026-27", 1, "25000.00", "1250.00", "23750.00", "4275.00", "28025.00");

    @Test
    @DisplayName("adds up every row into the overall totals")
    void totalsEveryRow() {
        LedgerSummary summary = LedgerSummaries.of(List.of(TEXTILES_2026_27, HUF_2025_26, HUF_2026_27));

        assertThat(summary.getEntryCount()).isEqualTo(4);
        assertThat(summary.getTotalServiceFee()).isEqualTo(new BigDecimal("60000.00"));
        assertThat(summary.getTotalDiscountAmount()).isEqualTo(new BigDecimal("3000.00"));
        assertThat(summary.getTotalTaxableAmount()).isEqualTo(new BigDecimal("57000.00"));
        assertThat(summary.getTotalGstAmount()).isEqualTo(new BigDecimal("10260.00"));
        assertThat(summary.getTotalAmount()).isEqualTo(new BigDecimal("67260.00"));
        assertThat(summary.getTotalPaid()).isEqualTo(new BigDecimal("0.00"));
        assertThat(summary.getBalance()).isEqualTo(new BigDecimal("67260.00"));
    }

    @Test
    @DisplayName("keeps payments out of the charged totals, and takes them off the balance")
    void totalsPayments() {
        LedgerSummary summary = LedgerSummaries.of(List.of(TEXTILES_2026_27, HUF_2025_26,
                payment(TEXTILES, "2026-27", 2, "-15000.00")));

        assertThat(summary.getEntryCount()).isEqualTo(5);
        // Charges only: payments have no service figures, and their negative total is not a charge.
        assertThat(summary.getTotalServiceFee()).isEqualTo(new BigDecimal("35000.00"));
        assertThat(summary.getTotalGstAmount()).isEqualTo(new BigDecimal("5985.00"));
        assertThat(summary.getTotalAmount()).isEqualTo(new BigDecimal("39235.00"));
        assertThat(summary.getTotalPaid()).isEqualTo(new BigDecimal("15000.00"));
        assertThat(summary.getBalance()).isEqualTo(new BigDecimal("24235.00"));

        FinancialYearTotals year = summary.getByFinancialYear().get(1);
        assertThat(year.getFinancialYear()).isEqualTo("2026-27");
        assertThat(year.getEntryCount()).isEqualTo(4);
        assertThat(year.getTotalAmount()).isEqualTo(new BigDecimal("11210.00"));
        assertThat(year.getTotalPaid()).isEqualTo(new BigDecimal("15000.00"));
        assertThat(year.getBalance()).isEqualTo(new BigDecimal("-3790.00"));
    }

    @Test
    @DisplayName("totals a company that has only paid, with zero charged and a negative balance")
    void totalsPaymentsOnly() {
        LedgerSummary summary = LedgerSummaries.of(List.of(payment(HUF, "2026-27", 1, "-5000.00")), NAMES);

        CompanyTotals huf = summary.getByCompany().get(0);
        assertThat(huf.getEntryCount()).isEqualTo(1);
        assertThat(huf.getTotalServiceFee()).isEqualTo(new BigDecimal("0.00"));
        assertThat(huf.getTotalDiscountAmount()).isEqualTo(new BigDecimal("0.00"));
        assertThat(huf.getTotalTaxableAmount()).isEqualTo(new BigDecimal("0.00"));
        assertThat(huf.getTotalGstAmount()).isEqualTo(new BigDecimal("0.00"));
        assertThat(huf.getTotalAmount()).isEqualTo(new BigDecimal("0.00"));
        assertThat(huf.getTotalPaid()).isEqualTo(new BigDecimal("5000.00"));
        assertThat(huf.getBalance()).isEqualTo(new BigDecimal("-5000.00"));
    }

    @Test
    @DisplayName("totals zero, to two places, with no year or company, when there are no rows")
    void totalsNoRows() {
        LedgerSummary summary = LedgerSummaries.of(List.of(), NAMES);

        assertThat(summary.getEntryCount()).isZero();
        assertThat(summary.getTotalServiceFee()).isEqualTo(new BigDecimal("0.00"));
        assertThat(summary.getTotalDiscountAmount()).isEqualTo(new BigDecimal("0.00"));
        assertThat(summary.getTotalTaxableAmount()).isEqualTo(new BigDecimal("0.00"));
        assertThat(summary.getTotalGstAmount()).isEqualTo(new BigDecimal("0.00"));
        assertThat(summary.getTotalAmount()).isEqualTo(new BigDecimal("0.00"));
        assertThat(summary.getTotalPaid()).isEqualTo(new BigDecimal("0.00"));
        assertThat(summary.getBalance()).isEqualTo(new BigDecimal("0.00"));
        assertThat(summary.getByFinancialYear()).isEmpty();
        assertThat(summary.getByCompany()).isEmpty();
    }

    @Test
    @DisplayName("combines the rows of each financial year, earliest year first")
    void totalsEachFinancialYear() {
        LedgerSummary summary = LedgerSummaries.of(List.of(TEXTILES_2026_27, HUF_2025_26, HUF_2026_27));

        assertThat(summary.getByFinancialYear())
                .extracting(FinancialYearTotals::getFinancialYear)
                .containsExactly("2025-26", "2026-27");

        FinancialYearTotals earlier = summary.getByFinancialYear().get(0);
        assertThat(earlier.getEntryCount()).isEqualTo(1);
        assertThat(earlier.getTotalAmount()).isEqualTo(new BigDecimal("28025.00"));

        FinancialYearTotals later = summary.getByFinancialYear().get(1);
        assertThat(later.getEntryCount()).isEqualTo(3);
        assertThat(later.getTotalServiceFee()).isEqualTo(new BigDecimal("35000.00"));
        assertThat(later.getTotalDiscountAmount()).isEqualTo(new BigDecimal("1750.00"));
        assertThat(later.getTotalTaxableAmount()).isEqualTo(new BigDecimal("33250.00"));
        assertThat(later.getTotalGstAmount()).isEqualTo(new BigDecimal("5985.00"));
        assertThat(later.getTotalAmount()).isEqualTo(new BigDecimal("39235.00"));
    }

    @Test
    @DisplayName("totals only the payments for a payment list, leaving service charges out")
    void totalsPaymentsAlone() {
        PaymentSummary summary = LedgerSummaries.payments(List.of(TEXTILES_2026_27,
                payment(TEXTILES, "2025-26", 1, "-1000.00"),
                payment(TEXTILES, "2026-27", 2, "-15000.00")));

        assertThat(summary.getPaymentCount()).isEqualTo(3);
        assertThat(summary.getTotalReceived()).isEqualTo(new BigDecimal("16000.00"));
    }

    @Test
    @DisplayName("gives each company its billed, received and pending amounts across its rows")
    void balancesEachCompany() {
        Map<UUID, CompanyBalance> balances = LedgerSummaries.balancesByCompany(List.of(TEXTILES_2026_27,
                payment(TEXTILES, "2026-27", 1, "-5605.00"), payment(HUF, "2026-27", 1, "-2000.00")));

        assertThat(balances).containsOnlyKeys(TEXTILES, HUF);
        assertThat(balances.get(TEXTILES)).isEqualTo(new CompanyBalance(new BigDecimal("11210.00"),
                new BigDecimal("5605.00"), new BigDecimal("5605.00")));
        // Paid without being billed: nothing charged, so a negative pending amount.
        assertThat(balances.get(HUF)).isEqualTo(new CompanyBalance(new BigDecimal("0.00"),
                new BigDecimal("2000.00"), new BigDecimal("-2000.00")));
        assertThat(LedgerSummaries.balancesByCompany(List.of())).isEmpty();
    }

    @Test
    @DisplayName("totals no payments as zero")
    void totalsNoPayments() {
        PaymentSummary summary = LedgerSummaries.payments(List.of(TEXTILES_2026_27));

        assertThat(summary.getPaymentCount()).isZero();
        assertThat(summary.getTotalReceived()).isEqualTo(new BigDecimal("0.00"));
    }

    @Test
    @DisplayName("orders financial years across a century by date")
    void ordersYearsAcrossCentury() {
        LedgerSummary summary = LedgerSummaries.of(List.of(
                row(TEXTILES, "2000-01", 1, "100.00", "0.00", "100.00", "18.00", "118.00"),
                row(TEXTILES, "1999-00", 1, "100.00", "0.00", "100.00", "18.00", "118.00")));

        assertThat(summary.getByFinancialYear())
                .extracting(FinancialYearTotals::getFinancialYear)
                .containsExactly("1999-00", "2000-01");
    }

    @Test
    @DisplayName("leaves out the company breakdown on a company's ledger")
    void noCompanyBreakdownWithoutNames() {
        assertThat(LedgerSummaries.of(List.of(TEXTILES_2026_27)).getByCompany()).isNull();
    }

    @Test
    @DisplayName("combines the rows of each company, ordered by company name")
    void totalsEachCompany() {
        LedgerSummary summary = LedgerSummaries.of(List.of(HUF_2025_26, TEXTILES_2026_27, HUF_2026_27), NAMES);

        assertThat(summary.getByCompany())
                .extracting(CompanyTotals::getCompanyName)
                .containsExactly("Mehta Textiles Pvt Ltd", "Rohan Mehta HUF");

        CompanyTotals textiles = summary.getByCompany().get(0);
        assertThat(textiles.getCompanyId()).isEqualTo(TEXTILES);
        assertThat(textiles.getEntryCount()).isEqualTo(2);
        assertThat(textiles.getTotalAmount()).isEqualTo(new BigDecimal("11210.00"));

        CompanyTotals huf = summary.getByCompany().get(1);
        assertThat(huf.getCompanyId()).isEqualTo(HUF);
        assertThat(huf.getEntryCount()).isEqualTo(2);
        assertThat(huf.getTotalServiceFee()).isEqualTo(new BigDecimal("50000.00"));
        assertThat(huf.getTotalDiscountAmount()).isEqualTo(new BigDecimal("2500.00"));
        assertThat(huf.getTotalTaxableAmount()).isEqualTo(new BigDecimal("47500.00"));
        assertThat(huf.getTotalGstAmount()).isEqualTo(new BigDecimal("8550.00"));
        assertThat(huf.getTotalAmount()).isEqualTo(new BigDecimal("56050.00"));
    }

    @Test
    @DisplayName("refuses a row whose company is not among those named")
    void rejectsUnknownCompany() {
        UUID stranger = UUID.randomUUID();
        List<LedgerTotalsRow> rows = List.of(row(stranger, "2026-27", 1, "5000.00", "250.00", "4750.00", "855.00",
                "5605.00"));

        assertThatThrownBy(() -> LedgerSummaries.of(rows, NAMES))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(stranger.toString());
    }

    /**
     * A row of service charges. Also used by {@link LedgerServiceImplTest} to stand in for the
     * database's totals.
     */
    @Test
    @DisplayName("gives each year's billed before discount, received, closing balance and status, and each company's")
    void givesStatusesByYear() {
        List<FinancialYear> years = List.of(FinancialYears.parse("2026-27"), FinancialYears.parse("2025-26"),
                FinancialYears.parse("2024-25"), FinancialYears.parse("2023-24"));
        List<LedgerTotalsRow> rows = List.of(TEXTILES_2026_27, HUF_2026_27, payment(TEXTILES, "2026-27", 1, "-5605.00"),
                HUF_2025_26, payment(HUF, "2025-26", 1, "-20000.00"), payment(TEXTILES, "2023-24", 1, "-1000.00"));

        List<FinancialYearStatus> statuses = LedgerSummaries.statusesByYear(rows, NAMES, years, years.getFirst(),
                false);

        assertThat(statuses).extracting(FinancialYearStatus::getFinancialYear)
                .containsExactly("2026-27", "2025-26", "2024-25", "2023-24");

        // The current year: owing, so pending, and still open.
        FinancialYearStatus current = statuses.get(0);
        assertThat(current.isCurrent()).isTrue();
        assertThat(current.getClosedOn()).isNull();
        assertThat(current.getFromDate()).isEqualTo(LocalDate.of(2026, 4, 1));
        assertThat(current.getToDate()).isEqualTo(LocalDate.of(2027, 3, 31));
        assertThat(current.getCompanyCount()).isEqualTo(2);
        // 39235.00 charged after a 1750.00 discount.
        assertThat(current.getBilled()).isEqualTo(new BigDecimal("40985.00"));
        assertThat(current.getDiscount()).isEqualTo(new BigDecimal("1750.00"));
        assertThat(current.getReceived()).isEqualTo(new BigDecimal("5605.00"));
        assertThat(current.getClosingBalance()).isEqualTo(new BigDecimal("33630.00"));
        assertThat(current.getStatus()).isEqualTo(LedgerYearStatus.PENDING);
        assertThat(current.getCompanies()).extracting(CompanyYearStatus::getCompanyName)
                .containsExactly("Mehta Textiles Pvt Ltd", "Rohan Mehta HUF");
        CompanyYearStatus textiles = current.getCompanies().get(0);
        assertThat(textiles.getBilled()).isEqualTo(new BigDecimal("11710.00"));
        assertThat(textiles.getDiscount()).isEqualTo(new BigDecimal("500.00"));
        assertThat(textiles.getReceived()).isEqualTo(new BigDecimal("5605.00"));
        assertThat(textiles.getClosingBalance()).isEqualTo(new BigDecimal("5605.00"));
        assertThat(textiles.getStatus()).isEqualTo(LedgerYearStatus.PENDING);
        CompanyYearStatus huf = current.getCompanies().get(1);
        assertThat(huf.getReceived()).isEqualTo(new BigDecimal("0.00"));
        assertThat(huf.getClosingBalance()).isEqualTo(new BigDecimal("28025.00"));

        // A closed year left owing: carried.
        FinancialYearStatus carried = statuses.get(1);
        assertThat(carried.isCurrent()).isFalse();
        assertThat(carried.getClosedOn()).isEqualTo(LocalDate.of(2026, 3, 31));
        assertThat(carried.getCompanyCount()).isEqualTo(1);
        assertThat(carried.getBilled()).isEqualTo(new BigDecimal("29275.00"));
        assertThat(carried.getReceived()).isEqualTo(new BigDecimal("20000.00"));
        assertThat(carried.getClosingBalance()).isEqualTo(new BigDecimal("8025.00"));
        assertThat(carried.getStatus()).isEqualTo(LedgerYearStatus.CARRIED);
        assertThat(carried.getCompanies().getFirst().getStatus()).isEqualTo(LedgerYearStatus.CARRIED);

        // A year with no entries: zeros, settled, no companies.
        FinancialYearStatus empty = statuses.get(2);
        assertThat(empty.getBilled()).isEqualTo(new BigDecimal("0.00"));
        assertThat(empty.getReceived()).isEqualTo(new BigDecimal("0.00"));
        assertThat(empty.getClosingBalance()).isEqualTo(new BigDecimal("0.00"));
        assertThat(empty.getStatus()).isEqualTo(LedgerYearStatus.SETTLED);
        assertThat(empty.getCompanyCount()).isZero();
        assertThat(empty.getCompanies()).isEmpty();

        // Only a payment: more received than billed, which owes nothing, so is settled.
        FinancialYearStatus overpaid = statuses.get(3);
        assertThat(overpaid.getClosingBalance()).isEqualTo(new BigDecimal("-1000.00"));
        assertThat(overpaid.getStatus()).isEqualTo(LedgerYearStatus.SETTLED);
    }

    @Test
    @DisplayName("lists only the companies still owing when asked, keeping the year's figures for every company")
    void givesStatusesOfOwingCompaniesOnly() {
        List<FinancialYear> years = List.of(FinancialYears.parse("2026-27"));
        // Mehta Textiles has paid in full; Rohan Mehta HUF has paid nothing.
        List<LedgerTotalsRow> rows = List.of(TEXTILES_2026_27, payment(TEXTILES, "2026-27", 1, "-11210.00"),
                HUF_2026_27);

        FinancialYearStatus year = LedgerSummaries.statusesByYear(rows, NAMES, years, years.getFirst(), true)
                .getFirst();

        assertThat(year.getCompanies()).extracting(CompanyYearStatus::getCompanyName)
                .containsExactly("Rohan Mehta HUF");
        assertThat(year.getCompanyCount()).isEqualTo(2);
        assertThat(year.getBilled()).isEqualTo(new BigDecimal("40985.00"));
        assertThat(year.getReceived()).isEqualTo(new BigDecimal("11210.00"));
        assertThat(year.getClosingBalance()).isEqualTo(new BigDecimal("28025.00"));
    }

    static LedgerTotalsRow row(UUID companyId, String financialYear, long entryCount, String fee,
                                       String discount, String taxable, String gst, String total) {
        return row(companyId, financialYear, LedgerEntryType.SERVICE_CHARGE, entryCount, fee, discount, taxable,
                gst, total);
    }

    /** A row of payments, which carry no service figures; the total received is given as stored, negative. */
    static LedgerTotalsRow payment(UUID companyId, String financialYear, long entryCount, String total) {
        return row(companyId, financialYear, LedgerEntryType.PAYMENT, entryCount, null, null, null, null, total);
    }

    private static LedgerTotalsRow row(UUID companyId, String financialYear, LedgerEntryType entryType,
                                       long entryCount, String fee, String discount, String taxable, String gst,
                                       String total) {
        return new LedgerTotalsRow() {
            @Override
            public UUID getCompanyId() {
                return companyId;
            }

            @Override
            public String getFinancialYear() {
                return financialYear;
            }

            @Override
            public LedgerEntryType getEntryType() {
                return entryType;
            }

            @Override
            public long getEntryCount() {
                return entryCount;
            }

            @Override
            public BigDecimal getTotalServiceFee() {
                return fee == null ? null : new BigDecimal(fee);
            }

            @Override
            public BigDecimal getTotalDiscountAmount() {
                return discount == null ? null : new BigDecimal(discount);
            }

            @Override
            public BigDecimal getTotalTaxableAmount() {
                return taxable == null ? null : new BigDecimal(taxable);
            }

            @Override
            public BigDecimal getTotalGstAmount() {
                return gst == null ? null : new BigDecimal(gst);
            }

            @Override
            public BigDecimal getTotalAmount() {
                return new BigDecimal(total);
            }
        };
    }
}
