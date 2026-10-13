package com.psc.cl.ledger.service;

import com.psc.cl.ledger.dto.CompanyBalance;
import com.psc.cl.ledger.dto.CompanyTotals;
import com.psc.cl.ledger.dto.FinancialYearTotals;
import com.psc.cl.ledger.dto.LedgerSummary;
import com.psc.cl.ledger.dto.PaymentSummary;
import com.psc.cl.ledger.model.LedgerEntryType;
import com.psc.cl.ledger.repository.LedgerTotalsRow;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Builds a ledger summary from the per company, per financial year, per entry type totals the
 * database returns.
 *
 * <p>Every entry falls in exactly one company, one financial year and one type, so adding up the
 * rows gives the overall totals, and adding up the rows of one year or one company gives that
 * year's or that company's. Service charge rows give the charged figures; payment rows give what
 * was paid, their negative totals turned positive.
 */
final class LedgerSummaries {

    private LedgerSummaries() {
        // utility class
    }

    /**
     * The summary of a company's ledger, broken down by financial year only.
     *
     * @param rows the totals of the matching entries, one row per company, financial year and type
     * @return the summary; every total zero when there are no rows
     */
    static LedgerSummary of(List<LedgerTotalsRow> rows) {
        return overall(rows).build();
    }

    /**
     * The summary of a client's ledger, broken down by financial year and by company.
     *
     * @param rows         the totals of the matching entries, one row per company, financial year and type
     * @param companyNames the name of every company of the client, by identifier
     * @return the summary; every total zero, and no year or company, when there are no rows
     * @throws IllegalStateException when a row's company is missing from the names given
     */
    static LedgerSummary of(List<LedgerTotalsRow> rows, Map<UUID, String> companyNames) {
        return overall(rows).byCompany(byCompany(rows, companyNames)).build();
    }

    /**
     * The totals of a company's payments alone, for a payment list.
     *
     * @param rows the totals of the matching entries, one row per company, financial year and type;
     *             rows of service charges are left out
     * @return the number of payments and the total received; zero when there are no payment rows
     */
    static PaymentSummary payments(List<LedgerTotalsRow> rows) {
        Totals totals = Totals.of(Totals.ofType(rows, LedgerEntryType.PAYMENT));
        return PaymentSummary.builder()
                .paymentCount(totals.entryCount())
                .totalReceived(totals.paid())
                .build();
    }

    /**
     * Each company's balance, for a list of companies.
     *
     * @param rows the totals of the matching entries, one row per company, financial year and type
     * @return each company's billed, received and pending amounts, by company; companies without rows
     *         are left out
     */
    static Map<UUID, CompanyBalance> balancesByCompany(List<LedgerTotalsRow> rows) {
        return rows.stream()
                .collect(Collectors.groupingBy(LedgerTotalsRow::getCompanyId))
                .entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, company -> {
                    Totals totals = Totals.of(company.getValue());
                    return new CompanyBalance(totals.charged(), totals.paid(), totals.balance());
                }));
    }

    private static LedgerSummary.LedgerSummaryBuilder overall(List<LedgerTotalsRow> rows) {
        Totals totals = Totals.of(rows);
        return LedgerSummary.builder()
                .entryCount(totals.entryCount())
                .totalServiceFee(totals.serviceFee())
                .totalDiscountAmount(totals.discountAmount())
                .totalTaxableAmount(totals.taxableAmount())
                .totalGstAmount(totals.gstAmount())
                .totalAmount(totals.charged())
                .totalPaid(totals.paid())
                .balance(totals.balance())
                .byFinancialYear(byFinancialYear(rows));
    }

    /** Labels such as 2025-26 lead with the starting year, so ordering them as text puts them in date order. */
    private static List<FinancialYearTotals> byFinancialYear(List<LedgerTotalsRow> rows) {
        Map<String, List<LedgerTotalsRow>> rowsByYear = rows.stream()
                .collect(Collectors.groupingBy(LedgerTotalsRow::getFinancialYear, TreeMap::new, Collectors.toList()));
        return rowsByYear.entrySet().stream()
                .map(year -> {
                    Totals totals = Totals.of(year.getValue());
                    return FinancialYearTotals.builder()
                            .financialYear(year.getKey())
                            .entryCount(totals.entryCount())
                            .totalServiceFee(totals.serviceFee())
                            .totalDiscountAmount(totals.discountAmount())
                            .totalTaxableAmount(totals.taxableAmount())
                            .totalGstAmount(totals.gstAmount())
                            .totalAmount(totals.charged())
                            .totalPaid(totals.paid())
                            .balance(totals.balance())
                            .build();
                })
                .toList();
    }

    /** Ordered by name, as companies are listed elsewhere; the identifier settles a tie. */
    private static List<CompanyTotals> byCompany(List<LedgerTotalsRow> rows, Map<UUID, String> companyNames) {
        Map<UUID, List<LedgerTotalsRow>> rowsByCompany = rows.stream()
                .collect(Collectors.groupingBy(LedgerTotalsRow::getCompanyId));
        return rowsByCompany.entrySet().stream()
                .map(company -> {
                    Totals totals = Totals.of(company.getValue());
                    return CompanyTotals.builder()
                            .companyId(company.getKey())
                            .companyName(companyName(companyNames, company.getKey()))
                            .entryCount(totals.entryCount())
                            .totalServiceFee(totals.serviceFee())
                            .totalDiscountAmount(totals.discountAmount())
                            .totalTaxableAmount(totals.taxableAmount())
                            .totalGstAmount(totals.gstAmount())
                            .totalAmount(totals.charged())
                            .totalPaid(totals.paid())
                            .balance(totals.balance())
                            .build();
                })
                .sorted(Comparator.comparing(CompanyTotals::getCompanyName).thenComparing(CompanyTotals::getCompanyId))
                .toList();
    }

    /**
     * An entry's company is held to its client by foreign key, so a missing name means the data is
     * inconsistent and is reported as a server error.
     */
    private static String companyName(Map<UUID, String> companyNames, UUID companyId) {
        String name = companyNames.get(companyId);
        if (name == null) {
            throw new IllegalStateException("Ledger entries refer to company " + companyId
                    + ", which is not a company of their client");
        }
        return name;
    }

    /**
     * Totals added up across rows, at the two decimal places the amount columns hold.
     *
     * @param entryCount     entries of either type
     * @param serviceFee     service fees charged
     * @param discountAmount discounts taken off them
     * @param taxableAmount  taxable amounts charged
     * @param gstAmount      GST charged
     * @param charged        amounts charged for services
     * @param paid           amounts received, as a positive number
     */
    private record Totals(long entryCount, BigDecimal serviceFee, BigDecimal discountAmount,
                          BigDecimal taxableAmount, BigDecimal gstAmount, BigDecimal charged, BigDecimal paid) {

        private static final BigDecimal ZERO = new BigDecimal("0.00");

        static Totals of(List<LedgerTotalsRow> rows) {
            List<LedgerTotalsRow> charges = ofType(rows, LedgerEntryType.SERVICE_CHARGE);
            List<LedgerTotalsRow> payments = ofType(rows, LedgerEntryType.PAYMENT);
            return new Totals(
                    rows.stream().mapToLong(LedgerTotalsRow::getEntryCount).sum(),
                    sum(charges, LedgerTotalsRow::getTotalServiceFee),
                    sum(charges, LedgerTotalsRow::getTotalDiscountAmount),
                    sum(charges, LedgerTotalsRow::getTotalTaxableAmount),
                    sum(charges, LedgerTotalsRow::getTotalGstAmount),
                    sum(charges, LedgerTotalsRow::getTotalAmount),
                    sum(payments, LedgerTotalsRow::getTotalAmount).negate());
        }

        /** What is still owed; negative when more has been paid than charged. */
        BigDecimal balance() {
            return charged.subtract(paid);
        }

        private static List<LedgerTotalsRow> ofType(List<LedgerTotalsRow> rows, LedgerEntryType type) {
            return rows.stream().filter(row -> row.getEntryType() == type).toList();
        }

        private static BigDecimal sum(List<LedgerTotalsRow> rows, Function<LedgerTotalsRow, BigDecimal> amount) {
            return rows.stream().map(amount).reduce(ZERO, BigDecimal::add).setScale(2, RoundingMode.UNNECESSARY);
        }
    }
}
