package com.psc.cl.exception;

import com.psc.cl.dto.ErrorResponse;

import java.util.List;

/**
 * Raised when an uploaded spreadsheet cannot be imported: it is not a readable workbook, it does not
 * follow the expected layout, or some of its cells hold values that are missing or invalid. Each
 * cell at fault is listed with its reference, such as {@code Clients!B5}, so every problem can be
 * corrected before uploading again.
 */
public class SpreadsheetValidationException extends RuntimeException {

    private final transient List<ErrorResponse.FieldError> errors;

    /**
     * @param message what is wrong with the spreadsheet as a whole
     * @param errors  each cell at fault and why; empty when the problem is not with particular cells
     */
    public SpreadsheetValidationException(String message, List<ErrorResponse.FieldError> errors) {
        super(message);
        this.errors = List.copyOf(errors);
    }

    public SpreadsheetValidationException(String message) {
        this(message, List.of());
    }

    public List<ErrorResponse.FieldError> getErrors() {
        return errors;
    }
}
