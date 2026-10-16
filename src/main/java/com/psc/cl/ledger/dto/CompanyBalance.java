package com.psc.cl.ledger.dto;

import java.math.BigDecimal;

/**
 * What one company was billed and paid in a financial year, and what remains: the ledger's figures
 * offered to other modules, such as a client's list of companies.
 *
 * @param totalBilled          sum of the service charges falling due in the year
 * @param totalPaymentReceived sum of the payments received in the year, as a positive number
 * @param pending              billed plus opening balance less received; negative when in credit
 * @param openingBalance       the opening balance the company was taken on with, when dated in the year:
 *                             positive when owed, negative when in credit; zero otherwise
 */
public record CompanyBalance(BigDecimal totalBilled, BigDecimal totalPaymentReceived, BigDecimal pending,
                             BigDecimal openingBalance) {

    /** The balance of a company with nothing billed, received or brought over in the year. */
    public static final CompanyBalance ZERO = new CompanyBalance(new BigDecimal("0.00"), new BigDecimal("0.00"),
            new BigDecimal("0.00"), new BigDecimal("0.00"));
}
