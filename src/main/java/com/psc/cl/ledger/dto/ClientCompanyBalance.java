package com.psc.cl.ledger.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * What one company of a client was billed and paid over a period, and what remains: the ledger's
 * figures offered to other modules, such as the overview's top companies.
 *
 * @param companyId            the company
 * @param clientId             the client the company belongs to
 * @param totalBilled          sum of the service charges falling due in the period
 * @param totalPaymentReceived sum of the payments received in the period, as a positive number
 * @param pending              billed less received
 */
public record ClientCompanyBalance(UUID companyId, UUID clientId, BigDecimal totalBilled,
                                   BigDecimal totalPaymentReceived, BigDecimal pending) {
}
