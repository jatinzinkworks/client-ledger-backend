package com.psc.cl.client.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Discount handling shared by clients, their companies and the services assigned to them.
 */
final class Percentages {

    private Percentages() {
        // utility class
    }

    /**
     * Picks the supplied percentage, or the fallback when none was supplied, at the two decimal
     * places the discount columns hold. Bringing it to that scale here means the value returned on
     * a write matches what a later read returns. Validation has already limited it to two places.
     *
     * @param supplied the percentage the caller sent, or null
     * @param fallback the percentage to inherit when the caller sent none
     * @return the percentage to store
     */
    static BigDecimal orElse(BigDecimal supplied, BigDecimal fallback) {
        return Objects.requireNonNullElse(supplied, fallback).setScale(2, RoundingMode.UNNECESSARY);
    }

    /**
     * Brings an optional percentage to the two decimal places the discount columns hold.
     *
     * @param supplied the percentage the caller sent, or null
     * @return the percentage to store, or null when none was sent
     */
    static BigDecimal orNull(BigDecimal supplied) {
        return supplied == null ? null : supplied.setScale(2, RoundingMode.UNNECESSARY);
    }
}
