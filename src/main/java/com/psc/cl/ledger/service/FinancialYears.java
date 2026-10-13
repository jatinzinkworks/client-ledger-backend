package com.psc.cl.ledger.service;

import com.psc.cl.ledger.exception.InvalidFinancialYearException;
import com.psc.cl.ledger.model.FinancialYear;

import java.time.LocalDate;
import java.time.Month;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Works out the Indian financial year a date falls in, as used under the Income-tax Act: from
 * 1 April to 31 March of the following year.
 *
 * <p>The V12 migration applies the same rule in SQL to entries that existed before the column
 * did; keep the two in step.
 */
public final class FinancialYears {

    /** The financial year starts on the first day of this month. */
    private static final Month FIRST_MONTH = Month.APRIL;

    /** A label such as {@code 2026-27}: the first calendar year, then the last two digits of the second. */
    private static final Pattern LABEL = Pattern.compile("(\\d{4})-(\\d{2})");

    private FinancialYears() {
        // utility class
    }

    /**
     * The financial year containing the given date, labelled by its first calendar year and the
     * last two digits of its second, the way it is written on Indian invoices and returns: 15 May
     * 2026 and 10 March 2027 both fall in {@code 2026-27}.
     *
     * @param date the date to place
     * @return the financial year label, such as {@code 2026-27}
     */
    static String of(LocalDate date) {
        int startYear = date.getMonthValue() >= FIRST_MONTH.getValue() ? date.getYear() : date.getYear() - 1;
        return label(startYear);
    }

    /**
     * Reads a financial year label, the reverse of {@link #of(LocalDate)}.
     *
     * @param label the year as written on returns, such as {@code 2026-27}
     * @return the financial year with its first and last days
     * @throws InvalidFinancialYearException when the label is not in that form, or its second part
     *                                       does not follow on from the first, such as {@code 2026-28}
     */
    public static FinancialYear parse(String label) {
        Matcher matcher = LABEL.matcher(label);
        if (!matcher.matches()) {
            throw new InvalidFinancialYearException(label);
        }
        int startYear = Integer.parseInt(matcher.group(1));
        if (!label(startYear).equals(label)) {
            throw new InvalidFinancialYearException(label);
        }
        LocalDate firstDay = LocalDate.of(startYear, FIRST_MONTH, 1);
        return new FinancialYear(label, firstDay, firstDay.plusYears(1).minusDays(1));
    }

    private static String label(int startYear) {
        return "%d-%02d".formatted(startYear, (startYear + 1) % 100);
    }
}
