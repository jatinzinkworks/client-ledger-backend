package com.psc.cl.ledger.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * The figures of one ledger entry, worked out from a service fee, its discount and its GST rate.
 *
 * <p>The discount comes off the fee first, and GST is charged on what remains. Each amount is
 * rounded half up to the paisa as it is worked out, so the stored figures always add up:
 * {@code taxable = fee - discount} and {@code total = taxable + gst}.
 *
 * @param serviceFee      standard fee of the service, excluding GST
 * @param discountPercent discount granted, as a percentage
 * @param discountAmount  amount taken off the fee
 * @param taxableAmount   fee after discount, on which GST is charged
 * @param gstRatePercent  GST rate, as a percentage
 * @param gstAmount       GST charged on the taxable amount
 * @param totalAmount     taxable amount plus GST
 */
record LedgerAmounts(BigDecimal serviceFee, BigDecimal discountPercent, BigDecimal discountAmount,
                     BigDecimal taxableAmount, BigDecimal gstRatePercent, BigDecimal gstAmount,
                     BigDecimal totalAmount) {

    private static final int SCALE = 2;

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    /**
     * Works out every figure of an entry.
     *
     * @param serviceFee      standard fee of the service, excluding GST
     * @param discountPercent discount granted on the service, as a percentage
     * @param gstRatePercent  GST rate of the service, as a percentage
     * @return the figures to store
     */
    static LedgerAmounts calculate(BigDecimal serviceFee, BigDecimal discountPercent, BigDecimal gstRatePercent) {
        BigDecimal fee = serviceFee.setScale(SCALE, RoundingMode.HALF_UP);
        BigDecimal discount = percentOf(fee, discountPercent);
        BigDecimal taxable = fee.subtract(discount);
        BigDecimal gst = percentOf(taxable, gstRatePercent);
        return new LedgerAmounts(fee, discountPercent, discount, taxable, gstRatePercent, gst, taxable.add(gst));
    }

    private static BigDecimal percentOf(BigDecimal amount, BigDecimal percent) {
        return amount.multiply(percent).divide(HUNDRED, SCALE, RoundingMode.HALF_UP);
    }
}
