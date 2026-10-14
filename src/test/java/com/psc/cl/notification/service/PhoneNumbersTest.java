package com.psc.cl.notification.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class PhoneNumbersTest {

    @ParameterizedTest
    @CsvSource({
            "'+91 98200 12345', 919820012345",
            "'+91-98200-12345', 919820012345",
            "'(+91) 98200 12345', 919820012345",
            "'98200 12345', 919820012345",
            "'098200 12345', 919820012345",
            "'919820012345', 919820012345",
            "'0091 98200 12345', 919820012345",
            "'+44 20 7946 0958', 442079460958",
            "'+1 (415) 555-0100', 14155550100"})
    void convertsToWhatsAppForm(String phone, String expected) {
        assertThat(PhoneNumbers.forWhatsApp(phone, "91")).contains(expected);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  ", "12345", "not a number", "+91 98200 12345 678 901"})
    void rejectsUnusableNumbers(String phone) {
        assertThat(PhoneNumbers.forWhatsApp(phone, "91")).isEmpty();
    }
}
