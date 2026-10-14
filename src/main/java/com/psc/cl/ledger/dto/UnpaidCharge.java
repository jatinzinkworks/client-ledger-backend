package com.psc.cl.ledger.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * What a company still owes of the service charges that fell due on one date, once its payments
 * have been applied to its oldest charges first. For other modules, such as the overview's
 * receivables ageing.
 *
 * @param companyId the company
 * @param dueDate   the date the charges fell due
 * @param unpaid    what remains unpaid of them, always more than zero
 */
public record UnpaidCharge(UUID companyId, LocalDate dueDate, BigDecimal unpaid) {
}
