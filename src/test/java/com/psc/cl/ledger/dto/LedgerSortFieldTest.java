package com.psc.cl.ledger.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.psc.cl.ledger.exception.InvalidSortException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class LedgerSortFieldTest {

    @ParameterizedTest(name = "reads \"{0}\" as {1}")
    @CsvSource({
            "dueDate, DUE_DATE",
            "entryDate, ENTRY_DATE",
            "serviceName, SERVICE_NAME",
            "totalAmount, TOTAL_AMOUNT"
    })
    void readsEachField(String value, LedgerSortField field) {
        assertThat(LedgerSortField.fromParam(value)).isEqualTo(field);
        assertThat(field.property()).isEqualTo(value);
    }

    @ParameterizedTest(name = "rejects \"{0}\"")
    @ValueSource(strings = {"DUE_DATE", "duedate", "due_date", "clientId", "id", ""})
    void rejectsAnythingElse(String value) {
        assertThatThrownBy(() -> LedgerSortField.fromParam(value))
                .isInstanceOf(InvalidSortException.class)
                .hasMessageContaining(LedgerSortField.ACCEPTED);
    }
}
