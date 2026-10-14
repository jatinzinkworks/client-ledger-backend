package com.psc.cl.ledger.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * What one client was billed and paid over a period, and what remains: the ledger's figures
 * offered to other modules, such as the overview's top clients.
 *
 * @param clientId             the client
 * @param totalBilled          sum of the service charges falling due in the period
 * @param totalPaymentReceived sum of the payments received in the period, as a positive number
 * @param pending              billed less received
 */
public record ClientBalance(UUID clientId, BigDecimal totalBilled, BigDecimal totalPaymentReceived,
                            BigDecimal pending) {
}
