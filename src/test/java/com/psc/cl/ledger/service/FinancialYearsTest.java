package com.psc.cl.ledger.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.psc.cl.ledger.exception.InvalidFinancialYearException;
import com.psc.cl.ledger.model.FinancialYear;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;

class FinancialYearsTest {

    @ParameterizedTest(name = "{0} falls in {1}")
    @CsvSource({
            "2026-04-01, 2026-27",
            "2026-10-07, 2026-27",
            "2026-12-31, 2026-27",
            "2027-01-01, 2026-27",
            "2027-03-31, 2026-27",
            "2027-04-01, 2027-28",
            "2026-03-31, 2025-26"
    })
    @DisplayName("runs from 1 April to 31 March")
    void runsFromAprilToMarch(LocalDate date, String financialYear) {
        assertThat(FinancialYears.of(date)).isEqualTo(financialYear);
    }

    @Test
    @DisplayName("keeps two digits for the second year across a century")
    void crossesCentury() {
        assertThat(FinancialYears.of(LocalDate.of(2099, 6, 1))).isEqualTo("2099-00");
        assertThat(FinancialYears.of(LocalDate.of(2000, 2, 1))).isEqualTo("1999-00");
        assertThat(FinancialYears.of(LocalDate.of(2008, 5, 1))).isEqualTo("2008-09");
    }

    @Test
    @DisplayName("reads a label back into its 1 April to 31 March range")
    void parsesLabel() {
        FinancialYear year = FinancialYears.parse("2026-27");

        assertThat(year.label()).isEqualTo("2026-27");
        assertThat(year.firstDay()).isEqualTo(LocalDate.of(2026, 4, 1));
        assertThat(year.lastDay()).isEqualTo(LocalDate.of(2027, 3, 31));
    }

    @Test
    @DisplayName("reads a label across a century")
    void parsesLabelAcrossCentury() {
        assertThat(FinancialYears.parse("1999-00").lastDay()).isEqualTo(LocalDate.of(2000, 3, 31));
    }

    @ParameterizedTest(name = "rejects \"{0}\"")
    @ValueSource(strings = {"2026-28", "2026-26", "2026", "2026-2027", "26-27", "2026/27", " 2026-27", "abcd-ef", ""})
    @DisplayName("rejects a label not written as yyyy-yy with consecutive years")
    void rejectsMalformedLabel(String label) {
        assertThatThrownBy(() -> FinancialYears.parse(label))
                .isInstanceOf(InvalidFinancialYearException.class)
                .hasMessageContaining("2026-27");
    }
}
