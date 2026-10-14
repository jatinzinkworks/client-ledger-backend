package com.psc.cl.notification.service;

import java.util.Optional;

/**
 * Turns phone numbers as clients' records hold them, free text such as {@code +91 98200 12345} or
 * {@code 098200 12345}, into the form WhatsApp takes: the country calling code and number, digits only.
 */
final class PhoneNumbers {

    /** Shortest and longest international numbers, country code included (ITU-T E.164). */
    private static final int MIN_DIGITS = 8;

    private static final int MAX_DIGITS = 15;

    /** Length of a national mobile number in India, which gets the default country code. */
    private static final int NATIONAL_DIGITS = 10;

    private PhoneNumbers() {
        // utility class
    }

    /**
     * The number in WhatsApp's form.
     *
     * <ul>
     *   <li>Starting with {@code +} or {@code 00}: already international; kept as it is.</li>
     *   <li>Otherwise, leading zeros (a trunk prefix) are dropped, and a 10-digit number gets the
     *       default country code. Other lengths are taken to include their country code already.</li>
     * </ul>
     *
     * @param phone              the number as stored
     * @param defaultCountryCode the calling code to add to a national number, such as {@code 91}
     * @return the number, such as {@code 919820012345}; empty when there is none or it cannot be a
     *         valid international number
     */
    static Optional<String> forWhatsApp(String phone, String defaultCountryCode) {
        if (phone == null || phone.isBlank()) {
            return Optional.empty();
        }
        String trimmed = phone.strip();
        String digits = trimmed.replaceAll("\\D", "");
        if (trimmed.startsWith("00")) {
            digits = digits.substring(2);
        } else if (!trimmed.startsWith("+")) {
            digits = digits.replaceFirst("^0+", "");
            if (digits.length() == NATIONAL_DIGITS) {
                digits = defaultCountryCode + digits;
            }
        }
        return digits.length() >= MIN_DIGITS && digits.length() <= MAX_DIGITS ? Optional.of(digits) : Optional.empty();
    }
}
