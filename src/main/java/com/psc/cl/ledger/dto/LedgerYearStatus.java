package com.psc.cl.ledger.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

/**
 * Where a ledger stands at the close of a financial year, or to date for the current one.
 */
@Schema(description = "PENDING: the current year still has an amount owing. CARRIED: a closed year ended with "
        + "an amount owing. SETTLED: nothing is owed for the year, including when more was received in it than "
        + "billed; the closing balance is then negative")
public enum LedgerYearStatus {

    PENDING,
    CARRIED,
    SETTLED;

    /**
     * The status of a year's closing balance. A year where more was received than billed owes
     * nothing, so it is settled too; its negative closing balance shows the excess.
     *
     * @param closingBalance billed less discount less received in the year
     * @param current        whether the year is the current one, still open
     * @return the status
     */
    public static LedgerYearStatus of(BigDecimal closingBalance, boolean current) {
        if (closingBalance.signum() > 0) {
            return current ? PENDING : CARRIED;
        }
        return SETTLED;
    }
}
