package com.psc.cl.client.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.psc.cl.servicecatalog.model.BillingFrequency;
import com.psc.cl.servicecatalog.model.CatalogService;
import com.psc.cl.servicecatalog.model.InvoiceMonth;
import com.psc.cl.servicecatalog.model.QuarterInvoiceMonth;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDate;
import java.util.UUID;

class NextDueDatesTest {

    @Test
    @DisplayName("a one-off service is due on its delivery date")
    void oneOffIsDueOnDelivery() {
        CatalogService service = service(BillingFrequency.ONE_OFF, null, null, null);

        assertThat(NextDueDates.onOrAfter(service, LocalDate.of(2026, 10, 15)))
                .isEqualTo(LocalDate.of(2026, 10, 15));
    }

    @Nested
    @DisplayName("monthly")
    class Monthly {

        @ParameterizedTest(name = "delivered {0}, invoiced on day {1} -> due {2}")
        @CsvSource({
                // Before the invoice day: due later the same month.
                "2026-10-03, 7, 2026-10-07",
                // On the invoice day: due that day.
                "2026-10-07, 7, 2026-10-07",
                // After the invoice day: due the following month.
                "2026-10-15, 7, 2026-11-07",
                // Across a year end.
                "2026-12-20, 7, 2027-01-07",
                // Day 31 in a 30 day month falls on the 30th.
                "2026-11-15, 31, 2026-11-30",
                // Day 31 in February falls on the 28th, or the 29th in a leap year.
                "2027-02-01, 31, 2027-02-28",
                "2028-02-01, 31, 2028-02-29",
                // Past the day in January: due on the last day of February, as it is shorter.
                "2028-01-31, 30, 2028-02-29",
                // Delivered on the last day of February itself: due that day.
                "2027-02-28, 31, 2027-02-28"
        })
        void monthly(LocalDate delivered, int day, LocalDate due) {
            CatalogService service = service(BillingFrequency.MONTHLY, day, null, null);

            assertThat(NextDueDates.onOrAfter(service, delivered)).isEqualTo(due);
        }
    }

    @Nested
    @DisplayName("quarterly")
    class Quarterly {

        @ParameterizedTest(name = "delivered {0}, {1} day {2} -> due {3}")
        @CsvSource({
                // FIRST_MONTH falls in January, April, July and October.
                "2026-02-10, FIRST_MONTH, 7, 2026-04-07",
                "2026-04-07, FIRST_MONTH, 7, 2026-04-07",
                "2026-04-08, FIRST_MONTH, 7, 2026-07-07",
                "2026-11-01, FIRST_MONTH, 7, 2027-01-07",
                // SECOND_MONTH falls in February, May, August and November.
                "2026-10-15, SECOND_MONTH, 20, 2026-11-20",
                "2026-11-21, SECOND_MONTH, 20, 2027-02-20",
                // THIRD_MONTH falls in March, June, September and December.
                "2026-10-15, THIRD_MONTH, 15, 2026-12-15",
                // Day 31 in June falls on the 30th.
                "2026-04-01, THIRD_MONTH, 31, 2026-06-30"
        })
        void quarterly(LocalDate delivered, QuarterInvoiceMonth monthOfQuarter, int day, LocalDate due) {
            CatalogService service = service(BillingFrequency.QUARTERLY, day, monthOfQuarter, null);

            assertThat(NextDueDates.onOrAfter(service, delivered)).isEqualTo(due);
        }
    }

    @Nested
    @DisplayName("annual")
    class Annual {

        @ParameterizedTest(name = "delivered {0}, {1} day {2} -> due {3}")
        @CsvSource({
                // Before the invoice month: due later the same year.
                "2026-02-10, APRIL, 30, 2026-04-30",
                // In the invoice month, before and on the day.
                "2026-04-10, APRIL, 30, 2026-04-30",
                "2026-04-30, APRIL, 30, 2026-04-30",
                // Just past the day: due a year on, the furthest a schedule can be.
                "2026-05-01, APRIL, 30, 2027-04-30",
                "2026-04-30, APRIL, 29, 2027-04-29",
                // Day 29 of February in a non-leap year falls on the 28th.
                "2026-10-15, FEBRUARY, 29, 2027-02-28",
                "2027-10-15, FEBRUARY, 29, 2028-02-29"
        })
        void annual(LocalDate delivered, InvoiceMonth month, int day, LocalDate due) {
            CatalogService service = service(BillingFrequency.ANNUAL, day, null, month);

            assertThat(NextDueDates.onOrAfter(service, delivered)).isEqualTo(due);
        }
    }

    @Test
    @DisplayName("reports a recurring service without an invoice day as inconsistent data")
    void rejectsRecurringServiceWithoutDay() {
        CatalogService service = service(BillingFrequency.MONTHLY, null, null, null);

        assertThatThrownBy(() -> NextDueDates.onOrAfter(service, LocalDate.of(2026, 10, 15)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("invoice day");
    }

    @Test
    @DisplayName("reports a quarterly service without a month of quarter as inconsistent data")
    void rejectsQuarterlyServiceWithoutMonthOfQuarter() {
        CatalogService service = service(BillingFrequency.QUARTERLY, 7, null, null);

        assertThatThrownBy(() -> NextDueDates.onOrAfter(service, LocalDate.of(2026, 10, 15)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("month of quarter");
    }

    @Test
    @DisplayName("reports an annual service without a month as inconsistent data")
    void rejectsAnnualServiceWithoutMonth() {
        CatalogService service = service(BillingFrequency.ANNUAL, 7, null, null);

        assertThatThrownBy(() -> NextDueDates.onOrAfter(service, LocalDate.of(2026, 10, 15)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("invoice month");
    }

    private static CatalogService service(BillingFrequency frequency, Integer day,
                                          QuarterInvoiceMonth monthOfQuarter, InvoiceMonth month) {
        CatalogService service = new CatalogService();
        service.setId(UUID.randomUUID());
        service.setBillingFrequency(frequency);
        service.setInvoiceDayOfMonth(day);
        service.setInvoiceMonthOfQuarter(monthOfQuarter);
        service.setInvoiceMonth(month);
        return service;
    }
}
