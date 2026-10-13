package com.psc.cl.client.service;

import com.psc.cl.servicecatalog.model.BillingFrequency;
import com.psc.cl.servicecatalog.model.CatalogService;

import java.time.LocalDate;
import java.time.Month;
import java.time.YearMonth;

/**
 * Works out when an assigned service next falls due, from the invoice schedule of its catalog
 * service.
 *
 * <p>The V9 migration applies the same rules in SQL to rows that existed before the column did;
 * keep the two in step.
 */
final class NextDueDates {

    /** An annual schedule recurs within twelve months, so a year's search always finds a date. */
    private static final int MONTHS_TO_SEARCH = 12;

    private NextDueDates() {
        // utility class
    }

    /**
     * The first date the service's invoice schedule falls on, on or after the given date.
     *
     * <ul>
     *   <li>{@code ONE_OFF}: the given date itself, as the service is billed once on delivery.</li>
     *   <li>{@code MONTHLY}: the invoice day of the month.</li>
     *   <li>{@code QUARTERLY}: the invoice day, in the chosen month after each quarter closes.
     *       For the quarter ending March, {@code FIRST_MONTH} is April, so {@code FIRST_MONTH}
     *       falls in January, April, July and October.</li>
     *   <li>{@code ANNUAL}: the invoice day, in the chosen month.</li>
     * </ul>
     *
     * <p>In a month shorter than the invoice day, the last day of that month is used.
     *
     * @param service the catalog service, whose schedule matches its billing frequency
     * @param from    the date to search from, inclusive
     * @return the next due date
     */
    static LocalDate onOrAfter(CatalogService service, LocalDate from) {
        BillingFrequency frequency = service.getBillingFrequency();
        if (frequency == BillingFrequency.ONE_OFF) {
            return from;
        }
        Integer dayOfMonth = service.getInvoiceDayOfMonth();
        if (dayOfMonth == null) {
            throw inconsistent(service, "has no invoice day");
        }

        YearMonth start = YearMonth.from(from);
        for (int offset = 0; offset <= MONTHS_TO_SEARCH; offset++) {
            YearMonth month = start.plusMonths(offset);
            if (!billsIn(service, month.getMonth())) {
                continue;
            }
            LocalDate candidate = month.atDay(Math.min(dayOfMonth, month.lengthOfMonth()));
            if (!candidate.isBefore(from)) {
                return candidate;
            }
        }
        throw inconsistent(service, "has a schedule that never falls due");
    }

    private static boolean billsIn(CatalogService service, Month month) {
        return switch (service.getBillingFrequency()) {
            case MONTHLY -> true;
            case QUARTERLY -> {
                if (service.getInvoiceMonthOfQuarter() == null) {
                    throw inconsistent(service, "has no invoice month of quarter");
                }
                yield (month.getValue() - 1) % 3 == service.getInvoiceMonthOfQuarter().ordinal();
            }
            case ANNUAL -> {
                if (service.getInvoiceMonth() == null) {
                    throw inconsistent(service, "has no invoice month");
                }
                yield month == service.getInvoiceMonth().toMonth();
            }
            case ONE_OFF -> false;
        };
    }

    /**
     * A database constraint ties the schedule to the billing frequency, so a gap here means the
     * data is inconsistent and is reported as a server error.
     */
    private static IllegalStateException inconsistent(CatalogService service, String problem) {
        return new IllegalStateException("Catalog service " + service.getId() + " billed "
                + service.getBillingFrequency() + " " + problem);
    }
}
