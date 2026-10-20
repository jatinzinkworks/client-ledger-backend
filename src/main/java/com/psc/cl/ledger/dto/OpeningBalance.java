package com.psc.cl.ledger.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * The balance a company was taken on with, brought over from before: the ledger's record of it
 * offered to other modules, such as a client's companies.
 *
 * @param amount the balance: positive when owed, negative when in credit; never zero
 * @param date   the date the balance stood at, which places it in a financial year
 */
public record OpeningBalance(BigDecimal amount, LocalDate date) {
}
