package com.psc.cl.ledger.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * What the clients of one manager were billed and paid over a period, and what remains: the
 * ledger's figures offered to other modules, such as the overview's top managers.
 *
 * @param managerId            the manager
 * @param totalBilled          sum of the service charges falling due in the period
 * @param totalPaymentReceived sum of the payments received in the period, as a positive number
 * @param pending              billed less received
 */
public record ManagerBalance(UUID managerId, BigDecimal totalBilled, BigDecimal totalPaymentReceived,
                             BigDecimal pending) {
}
