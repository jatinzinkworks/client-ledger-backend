package com.psc.cl.ledger.dto;

import java.math.BigDecimal;

/**
 * What one company was billed and paid in a financial year, and what remains: the ledger's figures
 * offered to other modules, such as a client's list of companies.
 *
 * @param totalBilled          sum of the service charges falling due in the year
 * @param totalPaymentReceived sum of the payments received in the year, as a positive number
 * @param pending              billed less received; negative when more was received than billed
 */
public record CompanyBalance(BigDecimal totalBilled, BigDecimal totalPaymentReceived, BigDecimal pending) {

    /** The balance of a company with nothing billed or received in the year. */
    public static final CompanyBalance ZERO =
            new CompanyBalance(new BigDecimal("0.00"), new BigDecimal("0.00"), new BigDecimal("0.00"));
}
