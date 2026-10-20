package com.psc.cl.service;

import com.psc.cl.dto.ErrorResponse;
import com.psc.cl.exception.SpreadsheetValidationException;
import com.psc.cl.service.XlsxWorkbook.Cell;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * What every bulk import from an Excel workbook shares: the layout of its sheets, reading a row's
 * cells as people type them, and gathering every cell at fault so all of them are reported at once.
 *
 * <p>An import reads its workbook with {@link XlsxReader}, checks each sheet against its
 * {@link SheetLayout} with {@link #requireSheet}, reads each row through a {@link Row}, and collects
 * what cannot be read or breaks a rule in {@link CellErrors}. Only a workbook without a single
 * error is imported, so an import is all or nothing.
 */
public final class SpreadsheetImport {

    /** Most cell errors listed in one response; any beyond are counted but not listed. */
    public static final int MAX_ERRORS = 200;

    private static final Pattern WORD_SEPARATORS = Pattern.compile("[\\s-]+");

    private static final Set<String> YES = Set.of("yes", "y", "true", "1");

    private static final Set<String> NO = Set.of("no", "n", "false", "0");

    /** Dates typed as text, besides the yyyy-MM-dd of a date cell: day first, as written in India. */
    private static final List<DateTimeFormatter> TEXT_DATES = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("d/M/uuuu").withResolverStyle(ResolverStyle.STRICT),
            DateTimeFormatter.ofPattern("d-M-uuuu").withResolverStyle(ResolverStyle.STRICT),
            DateTimeFormatter.ofPattern("d.M.uuuu").withResolverStyle(ResolverStyle.STRICT));

    /** What may surround an amount typed as text: thousands separators, spaces and a rupee sign. */
    private static final Pattern AMOUNT_DECORATION = Pattern.compile("[,\\s]|^(?:₹|rs\\.?|inr)",
            Pattern.CASE_INSENSITIVE);

    private SpreadsheetImport() {
        // utility class
    }

    /**
     * One column of an import sheet.
     *
     * @param heading     the heading in row 1, which an uploaded sheet must keep; an optional column
     *                    may be left out entirely when no column after it is there either, as on a
     *                    template downloaded before the column was added
     * @param field       the request field the column fills, as a broken rule names it; null when it
     *                    fills none, such as a reference linking sheets
     * @param width       the column's width in the template, in characters
     * @param required    whether every row must fill it
     * @param description what to put in it, for the instructions
     * @param example     an example value, for the instructions
     * @param choices     the values the template offers in a drop-down, or null for none
     */
    public record Column(String heading, String field, int width, boolean required, String description,
                         String example, Choices choices) {

        /** A column without a drop-down. */
        public Column(String heading, String field, int width, boolean required, String description,
                      String example) {
            this(heading, field, width, required, description, example, null);
        }
    }

    /**
     * Values a template offers in a column's drop-down: either listed, or those in a range of cells.
     *
     * @param values the values, in order; empty when they come from a range
     * @param source the cells holding the values, such as the references on another sheet; null when
     *               they are listed
     * @param strict whether Excel refuses a value typed that is not among them: true when the
     *               import accepts nothing else; false when it does, so Excel only warns
     */
    public record Choices(List<String> values, XlsxWorkbook.Range source, boolean strict) {

        /** Listed values. */
        public Choices(List<String> values, boolean strict) {
            this(values, null, strict);
        }

        /** The values in a range of cells, and nothing else. */
        public static Choices from(XlsxWorkbook.Range source) {
            return new Choices(List.of(), source, true);
        }

        /** Every constant of an enum by name, and nothing else. */
        public static <E extends Enum<E>> Choices of(Class<E> type) {
            return new Choices(Arrays.stream(type.getEnumConstants()).map(Enum::name).toList(), true);
        }

        /** These values as the usual ones, while the import accepts others too. */
        public static Choices suggested(String... values) {
            return new Choices(List.of(values), false);
        }
    }

    /**
     * One sheet of an import workbook.
     *
     * @param name    the tab's name, which an uploaded workbook must keep, ignoring case
     * @param columns its columns, from column A
     * @param order   its place among the workbook's sheets, which orders the errors reported
     */
    public record SheetLayout(String name, List<Column> columns, int order) {

        /** The column filling this field, or -1 when none does. */
        public int columnOf(String field) {
            for (int column = 0; column < columns.size(); column++) {
                if (Objects.equals(columns.get(column).field(), field)) {
                    return column;
                }
            }
            return -1;
        }
    }

    /**
     * Adds a sheet laid out for an import to a template: its headings in a frozen first row, at the
     * columns' widths, and a drop-down below the heading of each column offering choices.
     *
     * @param workbook the template being written
     * @param layout   the sheet to add
     * @return the sheet added, for anything more the template needs on it
     */
    public static XlsxWorkbook.Sheet addSheet(XlsxWorkbook workbook, SheetLayout layout) {
        List<Column> columns = layout.columns();
        XlsxWorkbook.Sheet sheet = workbook.addSheet(layout.name(), columns.stream().mapToInt(Column::width).toArray())
                .freezeHeaderRow()
                .addRow(columns.stream().map(column -> Cell.bold(column.heading())).toArray(Cell[]::new));
        for (int column = 0; column < columns.size(); column++) {
            Choices choices = columns.get(column).choices();
            if (choices != null && choices.source() != null) {
                sheet.addDropDown(column, 2, choices.source(), choices.strict());
            } else if (choices != null) {
                sheet.addDropDown(column, 2, choices.values(), choices.strict());
            }
        }
        return sheet;
    }

    /**
     * Adds a line to an instructions sheet for each column of a sheet: the sheet, the heading,
     * whether it is required, what to enter and an example, in that order.
     *
     * @param instructions the instructions sheet
     * @param layout       the sheet whose columns to describe
     */
    public static void describe(XlsxWorkbook.Sheet instructions, SheetLayout layout) {
        for (Column column : layout.columns()) {
            instructions.addRow(Cell.text(layout.name()), Cell.text(column.heading()),
                    Cell.text(column.required() ? "Yes" : "No"), Cell.text(column.description()),
                    Cell.text(column.example()));
        }
    }

    /**
     * The sheet of a layout, which must keep its headings in row 1. Optional columns at the end may be
     * left out entirely, so a sheet from a template downloaded before they were added is still read;
     * their cells are then blank.
     *
     * @param workbook     the uploaded workbook
     * @param layout       the sheet expected
     * @param templateName what the template is called, to point to it when the sheet is missing
     * @return the sheet
     * @throws SpreadsheetValidationException when the sheet is missing or its headings differ, naming
     *         each heading at fault
     */
    public static XlsxReader.Sheet requireSheet(XlsxReader.Workbook workbook, SheetLayout layout,
                                                String templateName) {
        XlsxReader.Sheet sheet = workbook.sheet(layout.name()).orElseThrow(() -> new SpreadsheetValidationException(
                "The workbook has no " + layout.name() + " sheet. Download the " + templateName + " and fill it in"));
        List<Column> columns = layout.columns();
        int lastHeading = -1;
        for (int column = 0; column < columns.size(); column++) {
            if (sheet.cell(1, column) != null) {
                lastHeading = column;
            }
        }
        List<ErrorResponse.FieldError> wrong = new ArrayList<>();
        for (int column = 0; column < columns.size(); column++) {
            String expected = columns.get(column).heading();
            String heading = sheet.cell(1, column);
            boolean leftOut = heading == null && column > lastHeading && !columns.get(column).required();
            if (!leftOut && (heading == null || !heading.strip().equalsIgnoreCase(expected))) {
                wrong.add(ErrorResponse.FieldError.builder()
                        .field(reference(layout.name(), 1, column))
                        .message("expected the heading " + expected)
                        .build());
            }
        }
        if (!wrong.isEmpty()) {
            throw new SpreadsheetValidationException("The headings of the " + layout.name() + " sheet do not "
                    + "match the " + templateName + "; keep row 1 as the template has it", wrong);
        }
        return sheet;
    }

    /**
     * Every row below the headings with something in it, by row number.
     *
     * @param sheet the sheet
     * @return the rows from row 2 on
     */
    public static Map<Integer, Map<Integer, String>> dataRows(XlsxReader.Sheet sheet) {
        return sheet.rows().tailMap(2);
    }

    /**
     * Text as compared: ignoring case and surrounding spaces.
     *
     * @param text the text
     * @return the text stripped and lower-cased
     */
    public static String key(String text) {
        return text.strip().toLowerCase(Locale.ROOT);
    }

    /** A cell's reference, such as Clients!B5. */
    static String reference(String sheet, int row, int column) {
        return sheet + "!" + XlsxWorkbook.columnName(column) + row;
    }

    /**
     * One row of an import sheet, reading its cells as people type them and reporting each cell
     * that cannot be read. A reader that reports a cell returns null for it.
     *
     * @param layout the sheet the row is on
     * @param row    the row number, from 1
     * @param values the row's non-empty cells, by column index from 0
     * @param errors where to report a cell that cannot be read
     */
    public record Row(SheetLayout layout, int row, Map<Integer, String> values, CellErrors errors) {

        /** The cell's text without surrounding spaces, or null when it is blank. */
        public String text(int column) {
            String value = values.get(column);
            return value == null || value.isBlank() ? null : value.strip();
        }

        /**
         * A percentage, as a number of percent: 10 and 10% both read as 10. A cell formatted as a
         * percentage is read as Excel shows it, so one holding 10% reads as 10 too, not as 0.1.
         */
        public BigDecimal percent(int column) {
            String value = text(column);
            if (value == null) {
                return null;
            }
            String number = value.endsWith("%") ? value.substring(0, value.length() - 1).strip() : value;
            try {
                return new BigDecimal(number);
            } catch (NumberFormatException e) {
                error(column, "must be a number, such as 10, 12.5 or 10%");
                return null;
            }
        }

        /**
         * An amount of money, which may be negative: a number cell, or text such as 25,000.50, -5000
         * or ₹1,23,456, with thousands separators and a rupee sign left out.
         */
        public BigDecimal amount(int column) {
            String value = text(column);
            if (value == null) {
                return null;
            }
            try {
                return new BigDecimal(AMOUNT_DECORATION.matcher(value).replaceAll(""));
            } catch (NumberFormatException e) {
                error(column, "must be an amount, such as 25000, 25,000.50 or -5000");
                return null;
            }
        }

        /** A whole number, such as a day of the month. */
        public Integer wholeNumber(int column) {
            String value = text(column);
            if (value == null) {
                return null;
            }
            try {
                return new BigDecimal(value).intValueExact();
            } catch (NumberFormatException | ArithmeticException e) {
                error(column, "must be a whole number");
                return null;
            }
        }

        /**
         * A date no later than the one given: a date cell, or text as 31/03/2026, 31-03-2026 or
         * 2026-03-31, day first.
         */
        public LocalDate dateUpTo(int column, LocalDate latest) {
            String value = text(column);
            if (value == null) {
                return null;
            }
            for (DateTimeFormatter format : TEXT_DATES) {
                try {
                    LocalDate date = LocalDate.parse(value, format);
                    if (date.isAfter(latest)) {
                        error(column, "must not be after today, " + latest);
                        return null;
                    }
                    return date;
                } catch (DateTimeParseException e) {
                    // Not in this format; the next may fit.
                }
            }
            error(column, "must be a date, such as 31/03/2026 or 2026-03-31");
            return null;
        }

        /** Yes or No, also written Y or N, True or False, 1 or 0, in any case. */
        public Boolean yesNo(int column) {
            String value = text(column);
            if (value == null) {
                return null;
            }
            String answer = key(value);
            if (YES.contains(answer)) {
                return true;
            }
            if (NO.contains(answer)) {
                return false;
            }
            error(column, "must be Yes or No");
            return null;
        }

        /**
         * One of an enum's constants, by name: case does not matter, and spaces or hyphens may stand
         * for underscores, so Private Limited reads as PRIVATE_LIMITED.
         */
        public <E extends Enum<E>> E choice(int column, Class<E> type) {
            String value = text(column);
            if (value == null) {
                return null;
            }
            String name = WORD_SEPARATORS.matcher(value).replaceAll("_").toUpperCase(Locale.ROOT);
            try {
                return Enum.valueOf(type, name);
            } catch (IllegalArgumentException e) {
                error(column, "must be one of " + choices(type));
                return null;
            }
        }

        /** Reports a cell that cannot be read or breaks a rule. */
        public void error(int column, String message) {
            errors.add(layout, row, column, message);
        }
    }

    /**
     * The names of an enum's constants, joined for a message or instructions.
     *
     * @param type the enum
     * @return its constants' names, such as ONE_OFF, MONTHLY
     */
    public static <E extends Enum<E>> String choices(Class<E> type) {
        return Arrays.stream(type.getEnumConstants()).map(Enum::name).collect(Collectors.joining(", "));
    }

    /** Every cell found at fault, at most one error each, listed in sheet, row and column order. */
    public static final class CellErrors {

        private final Map<String, CellError> errors = new HashMap<>();

        /**
         * Reports a cell at fault, unless it is already reported: a cell keeps its first error.
         *
         * @param layout  the sheet the cell is on
         * @param row     the cell's row, from 1
         * @param column  the cell's column, from 0
         * @param message why, after the column's heading
         */
        public void add(SheetLayout layout, int row, int column, String message) {
            String reference = reference(layout.name(), row, column);
            errors.putIfAbsent(reference, new CellError(layout.order(), row, column, reference,
                    layout.columns().get(column).heading() + ": " + message));
        }

        public boolean isEmpty() {
            return errors.isEmpty();
        }

        /**
         * Every error reported, as the exception to reject the workbook with.
         *
         * @return the exception, listing at most {@link SpreadsheetImport#MAX_ERRORS} cells at fault
         */
        public SpreadsheetValidationException toException() {
            List<ErrorResponse.FieldError> listed = errors.values().stream()
                    .sorted(Comparator.comparingInt(CellError::sheet)
                            .thenComparingInt(CellError::row)
                            .thenComparingInt(CellError::column))
                    .limit(MAX_ERRORS)
                    .map(error -> ErrorResponse.FieldError.builder()
                            .field(error.reference())
                            .message(error.message())
                            .build())
                    .toList();
            String message = errors.size() + " cell(s) of the workbook are invalid, so nothing was imported"
                    + (errors.size() > MAX_ERRORS ? "; the first " + MAX_ERRORS + " are listed" : "")
                    + ". Correct them and upload the file again";
            return new SpreadsheetValidationException(message, listed);
        }
    }

    /** @param sheet the order of the sheet the cell is on, to list errors sheet by sheet */
    private record CellError(int sheet, int row, int column, String reference, String message) {
    }
}
