package com.psc.cl.ledger.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

class LedgerAmountsTest {

    @Test
    @DisplayName("takes the discount off the fee, then charges GST on what remains")
    void appliesDiscountThenGst() {
        LedgerAmounts amounts = LedgerAmounts.calculate(new BigDecimal("5000.00"), new BigDecimal("5.00"),
                new BigDecimal("18.00"));

        assertThat(amounts.serviceFee()).isEqualByComparingTo("5000.00");
        assertThat(amounts.discountPercent()).isEqualByComparingTo("5.00");
        assertThat(amounts.discountAmount()).isEqualByComparingTo("250.00");
        assertThat(amounts.taxableAmount()).isEqualByComparingTo("4750.00");
        assertThat(amounts.gstRatePercent()).isEqualByComparingTo("18.00");
        assertThat(amounts.gstAmount()).isEqualByComparingTo("855.00");
        assertThat(amounts.totalAmount()).isEqualByComparingTo("5605.00");
    }

    @Test
    @DisplayName("rounds each amount half up to two places, so the figures still add up")
    void roundsEachAmount() {
        LedgerAmounts amounts = LedgerAmounts.calculate(new BigDecimal("999.99"), new BigDecimal("7.50"),
                new BigDecimal("18.00"));

        // 999.99 x 7.5% = 74.99925, and 924.99 x 18% = 166.4982.
        assertThat(amounts.discountAmount()).isEqualTo(new BigDecimal("75.00"));
        assertThat(amounts.taxableAmount()).isEqualTo(new BigDecimal("924.99"));
        assertThat(amounts.gstAmount()).isEqualTo(new BigDecimal("166.50"));
        assertThat(amounts.totalAmount()).isEqualTo(new BigDecimal("1091.49"));
    }

    @Test
    @DisplayName("charges GST on the full fee when there is no discount")
    void noDiscount() {
        LedgerAmounts amounts = LedgerAmounts.calculate(new BigDecimal("2000.00"), new BigDecimal("0.00"),
                new BigDecimal("18.00"));

        assertThat(amounts.discountAmount()).isEqualByComparingTo("0.00");
        assertThat(amounts.taxableAmount()).isEqualByComparingTo("2000.00");
        assertThat(amounts.gstAmount()).isEqualByComparingTo("360.00");
        assertThat(amounts.totalAmount()).isEqualByComparingTo("2360.00");
    }

    @Test
    @DisplayName("charges no GST on a zero-rated service")
    void zeroRatedGst() {
        LedgerAmounts amounts = LedgerAmounts.calculate(new BigDecimal("2000.00"), new BigDecimal("10.00"),
                new BigDecimal("0.00"));

        assertThat(amounts.gstAmount()).isEqualByComparingTo("0.00");
        assertThat(amounts.totalAmount()).isEqualByComparingTo("1800.00");
    }
}
