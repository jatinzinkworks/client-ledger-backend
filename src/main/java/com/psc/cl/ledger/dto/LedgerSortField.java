package com.psc.cl.ledger.dto;

import com.psc.cl.ledger.exception.InvalidSortException;

import java.util.Arrays;

/**
 * The fields a ledger list can be sorted by.
 *
 * <p>Each is named in the API as the {@link LedgerEntryResponse} field it orders by, which is also
 * the {@code LedgerEntry} property the database sorts on. Only these are accepted, so a caller
 * cannot sort on an arbitrary column.
 */
public enum LedgerSortField {

    DUE_DATE("dueDate"),
    ENTRY_DATE("entryDate"),
    SERVICE_NAME("serviceName"),
    TOTAL_AMOUNT("totalAmount");

    /** Every value accepted, as written in the API; for documentation and error messages. */
    public static final String ACCEPTED = "dueDate, entryDate, serviceName, totalAmount";

    private final String property;

    LedgerSortField(String property) {
        this.property = property;
    }

    /** @return the name of the field in the API, and of the entity property sorted on */
    public String property() {
        return property;
    }

    /**
     * Reads a sortBy query parameter.
     *
     * @param value the field as written in the API, such as {@code totalAmount}
     * @return the field
     * @throws InvalidSortException when the value names no sortable field
     */
    public static LedgerSortField fromParam(String value) {
        return Arrays.stream(values())
                .filter(field -> field.property.equals(value))
                .findFirst()
                .orElseThrow(() -> new InvalidSortException("sortBy '" + value + "' is not one of " + ACCEPTED + "."));
    }
}
