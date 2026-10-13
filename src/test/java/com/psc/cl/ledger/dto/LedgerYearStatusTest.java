package com.psc.cl.ledger.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

class LedgerYearStatusTest {

    @Test
    @DisplayName("an amount owing is pending in the current year and carried in a closed one")
    void owing() {
        assertThat(LedgerYearStatus.of(new BigDecimal("98150.00"), true)).isEqualTo(LedgerYearStatus.PENDING);
        assertThat(LedgerYearStatus.of(new BigDecimal("21000.00"), false)).isEqualTo(LedgerYearStatus.CARRIED);
    }

    @Test
    @DisplayName("nothing owing is settled, whatever the scale, in any year")
    void settled() {
        assertThat(LedgerYearStatus.of(new BigDecimal("0.00"), false)).isEqualTo(LedgerYearStatus.SETTLED);
        assertThat(LedgerYearStatus.of(BigDecimal.ZERO, true)).isEqualTo(LedgerYearStatus.SETTLED);
    }

    @Test
    @DisplayName("more received than billed owes nothing, so is settled too, in any year")
    void overpaid() {
        assertThat(LedgerYearStatus.of(new BigDecimal("-1000.00"), false)).isEqualTo(LedgerYearStatus.SETTLED);
        assertThat(LedgerYearStatus.of(new BigDecimal("-0.01"), true)).isEqualTo(LedgerYearStatus.SETTLED);
    }
}
