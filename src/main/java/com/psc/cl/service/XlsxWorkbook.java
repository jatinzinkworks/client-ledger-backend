package com.psc.cl.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Writes a small Excel workbook (Office Open XML, .xlsx) with nothing but the JDK: a zip of the few
 * XML parts Excel, LibreOffice and Google Sheets need. Enough for a ledger export: text, amounts
 * and dates, a bold heading style, column widths, frozen rows at the top and drop-downs of choices.
 *
 * <p>Text is written as inline strings, never as formulas, so a value such as {@code =SUM(A1)}
 * typed into a comment shows as typed and is not evaluated when the file is opened.
 *
 * <p>Shared by every module that writes a workbook: the ledger's exports and the bulk import templates.
 */
public final class XlsxWorkbook {

    /** Excel counts dates as days since this one (its 1900 date system). */
    private static final LocalDate EXCEL_EPOCH = LocalDate.of(1899, 12, 30);

    /** Excel refuses sheet names longer than this. */
    private static final int MAX_SHEET_NAME_LENGTH = 31;

    /** Excel's last row, to offer a drop-down down the whole of a column. */
    private static final int MAX_ROW = 1_048_576;

    /** Excel refuses a drop-down whose choices, joined by commas, are longer than this. */
    private static final int MAX_CHOICES_LENGTH = 255;

    /** Characters Excel refuses in a sheet name. */
    private static final Pattern FORBIDDEN_IN_SHEET_NAME = Pattern.compile("[:\\\\/?*\\[\\]]");

    private static final String MAIN_NS = "http://schemas.openxmlformats.org/spreadsheetml/2006/main";

    private static final String REL_NS = "http://schemas.openxmlformats.org/officeDocument/2006/relationships";

    private static final String PACKAGE_REL_NS = "http://schemas.openxmlformats.org/package/2006/relationships";

    private static final String XML_DECLARATION = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n";

    /**
     * Cell styles, in the order of the cellXfs written to styles.xml; the ordinal is the style index.
     * Amounts show two decimals with thousands separators, dates as 07-Oct-2026.
     */
    public enum Style {
        TEXT,
        BOLD,
        AMOUNT,
        DATE,
        BOLD_AMOUNT
    }

    private static final String STYLES = XML_DECLARATION
            + "<styleSheet xmlns=\"" + MAIN_NS + "\">"
            + "<numFmts count=\"2\"><numFmt numFmtId=\"164\" formatCode=\"#,##0.00\"/>"
            + "<numFmt numFmtId=\"165\" formatCode=\"dd\\-mmm\\-yyyy\"/></numFmts>"
            + "<fonts count=\"2\"><font><sz val=\"11\"/><name val=\"Calibri\"/></font>"
            + "<font><b/><sz val=\"11\"/><name val=\"Calibri\"/></font></fonts>"
            + "<fills count=\"2\"><fill><patternFill patternType=\"none\"/></fill>"
            + "<fill><patternFill patternType=\"gray125\"/></fill></fills>"
            + "<borders count=\"1\"><border><left/><right/><top/><bottom/><diagonal/></border></borders>"
            + "<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>"
            + "<cellXfs count=\"5\">"
            + "<xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/>"
            + "<xf numFmtId=\"0\" fontId=\"1\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyFont=\"1\"/>"
            + "<xf numFmtId=\"164\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyNumberFormat=\"1\"/>"
            + "<xf numFmtId=\"165\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyNumberFormat=\"1\"/>"
            + "<xf numFmtId=\"164\" fontId=\"1\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyNumberFormat=\"1\" "
            + "applyFont=\"1\"/>"
            + "</cellXfs>"
            + "<cellStyles count=\"1\"><cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/></cellStyles>"
            + "</styleSheet>";

    private final List<Sheet> sheets = new ArrayList<>();

    /**
     * Adds a sheet after those already added.
     *
     * @param name         the tab's name: at most 31 characters, without {@code : \ / ? * [ ]}, not
     *                     starting or ending with an apostrophe, and unlike any other sheet's name
     *                     ignoring case, as Excel requires; see {@link #sheetName}
     * @param columnWidths width of each column from A, in characters
     * @return the sheet, to add rows to
     */
    public Sheet addSheet(String name, int... columnWidths) {
        if (!isValidSheetName(name)) {
            throw new IllegalArgumentException("Not a valid sheet name: " + name);
        }
        if (isTaken(name)) {
            throw new IllegalArgumentException("The workbook already has a sheet named " + name);
        }
        Sheet sheet = new Sheet(name, columnWidths.clone());
        sheets.add(sheet);
        return sheet;
    }

    /**
     * Turns any text, such as a company's name, into a sheet name {@link #addSheet} accepts: the
     * characters Excel forbids become hyphens, the name is cut to 31 characters, and a name already
     * taken gets a number, such as {@code Mehta Textiles (2)}.
     *
     * @param text     the text to name the sheet after
     * @param fallback the name to use when nothing of the text is left, itself a valid sheet name
     * @return a valid name no other sheet of this workbook has yet
     */
    public String sheetName(String text, String fallback) {
        String base = FORBIDDEN_IN_SHEET_NAME.matcher(text).replaceAll("-").strip();
        if (base.length() > MAX_SHEET_NAME_LENGTH) {
            base = base.substring(0, MAX_SHEET_NAME_LENGTH);
        }
        base = trimApostrophes(base);
        if (base.isEmpty()) {
            base = fallback;
        }
        String name = base;
        for (int n = 2; isTaken(name); n++) {
            String suffix = " (" + n + ")";
            int room = MAX_SHEET_NAME_LENGTH - suffix.length();
            name = trimApostrophes(base.length() > room ? base.substring(0, room) : base) + suffix;
        }
        return name;
    }

    private boolean isTaken(String name) {
        return sheets.stream().anyMatch(sheet -> sheet.name.equalsIgnoreCase(name));
    }

    private static boolean isValidSheetName(String name) {
        return !name.isBlank() && name.length() <= MAX_SHEET_NAME_LENGTH
                && !FORBIDDEN_IN_SHEET_NAME.matcher(name).find()
                && !name.startsWith("'") && !name.endsWith("'");
    }

    private static String trimApostrophes(String name) {
        return name.replaceAll("^['\\s]+|['\\s]+$", "");
    }

    /**
     * The workbook as the bytes of an .xlsx file.
     *
     * @return the file's content
     */
    public byte[] toBytes() {
        if (sheets.isEmpty()) {
            throw new IllegalStateException("A workbook needs at least one sheet");
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes, StandardCharsets.UTF_8)) {
            put(zip, "[Content_Types].xml", contentTypes());
            put(zip, "_rels/.rels", XML_DECLARATION + "<Relationships xmlns=\"" + PACKAGE_REL_NS + "\">"
                    + "<Relationship Id=\"rId1\" Type=\"" + REL_NS + "/officeDocument\" Target=\"xl/workbook.xml\"/>"
                    + "</Relationships>");
            put(zip, "xl/workbook.xml", workbook());
            put(zip, "xl/_rels/workbook.xml.rels", workbookRelationships());
            put(zip, "xl/styles.xml", STYLES);
            for (int i = 0; i < sheets.size(); i++) {
                put(zip, "xl/worksheets/sheet" + (i + 1) + ".xml", sheets.get(i).toXml());
            }
        } catch (IOException e) {
            // Only memory is written to, so this is not expected.
            throw new UncheckedIOException("Could not write the workbook", e);
        }
        return bytes.toByteArray();
    }

    private String contentTypes() {
        StringBuilder xml = new StringBuilder(XML_DECLARATION)
                .append("<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">")
                .append("<Default Extension=\"rels\" ")
                .append("ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>")
                .append("<Default Extension=\"xml\" ContentType=\"application/xml\"/>")
                .append("<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/")
                .append("vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>")
                .append("<Override PartName=\"/xl/styles.xml\" ContentType=\"application/")
                .append("vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>");
        for (int i = 1; i <= sheets.size(); i++) {
            xml.append("<Override PartName=\"/xl/worksheets/sheet").append(i).append(".xml\" ")
                    .append("ContentType=\"application/")
                    .append("vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>");
        }
        return xml.append("</Types>").toString();
    }

    private String workbook() {
        StringBuilder xml = new StringBuilder(XML_DECLARATION)
                .append("<workbook xmlns=\"").append(MAIN_NS).append("\" xmlns:r=\"").append(REL_NS).append("\">")
                .append("<sheets>");
        for (int i = 1; i <= sheets.size(); i++) {
            xml.append("<sheet name=\"").append(escape(sheets.get(i - 1).name)).append("\" sheetId=\"").append(i)
                    .append("\" r:id=\"rId").append(i).append("\"/>");
        }
        return xml.append("</sheets></workbook>").toString();
    }

    private String workbookRelationships() {
        StringBuilder xml = new StringBuilder(XML_DECLARATION)
                .append("<Relationships xmlns=\"").append(PACKAGE_REL_NS).append("\">");
        for (int i = 1; i <= sheets.size(); i++) {
            xml.append("<Relationship Id=\"rId").append(i).append("\" Type=\"").append(REL_NS)
                    .append("/worksheet\" Target=\"worksheets/sheet").append(i).append(".xml\"/>");
        }
        // The styles take the identifier after the last sheet's.
        xml.append("<Relationship Id=\"rId").append(sheets.size() + 1).append("\" Type=\"").append(REL_NS)
                .append("/styles\" Target=\"styles.xml\"/>");
        return xml.append("</Relationships>").toString();
    }

    private static void put(ZipOutputStream zip, String name, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    /**
     * Escapes text for XML, dropping the control characters XML 1.0 cannot carry at all.
     */
    static String escape(String text) {
        StringBuilder escaped = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '&' -> escaped.append("&amp;");
                case '<' -> escaped.append("&lt;");
                case '>' -> escaped.append("&gt;");
                case '"' -> escaped.append("&quot;");
                default -> {
                    if (c >= 0x20 || c == '\t' || c == '\n' || c == '\r') {
                        escaped.append(c);
                    }
                }
            }
        }
        return escaped.toString();
    }

    /**
     * The letters naming a column, counting from 0: A to Z, then AA, AB and so on.
     */
    public static String columnName(int index) {
        StringBuilder name = new StringBuilder();
        for (int n = index + 1; n > 0; n = (n - 1) / 26) {
            name.insert(0, (char) ('A' + (n - 1) % 26));
        }
        return name.toString();
    }

    /** A value and how to show it; a null value leaves the cell empty. */
    public record Cell(Object value, Style style) {

        public static Cell text(String value) {
            return new Cell(value, Style.TEXT);
        }

        public static Cell bold(String value) {
            return new Cell(value, Style.BOLD);
        }

        public static Cell amount(BigDecimal value) {
            return new Cell(value, Style.AMOUNT);
        }

        public static Cell boldAmount(BigDecimal value) {
            return new Cell(value, Style.BOLD_AMOUNT);
        }

        public static Cell date(LocalDate value) {
            return new Cell(value, Style.DATE);
        }

        public static Cell empty() {
            return new Cell(null, Style.TEXT);
        }
    }

    /** One tab of the workbook. */
    public static final class Sheet {

        private final String name;

        private final int[] columnWidths;

        private final List<List<Cell>> rows = new ArrayList<>();

        private final List<DropDown> dropDowns = new ArrayList<>();

        private int frozenRows;

        private Sheet(String name, int[] columnWidths) {
            this.name = name;
            this.columnWidths = columnWidths;
        }

        /**
         * Adds a row below those already added.
         *
         * @param cells the row's cells, from column A
         * @return this sheet, to add more rows
         */
        public Sheet addRow(Cell... cells) {
            rows.add(Arrays.asList(cells));
            return this;
        }

        /** Adds an empty row, to space out sections. */
        public Sheet addBlankRow() {
            rows.add(List.of());
            return this;
        }

        /** Keeps the first row in view while scrolling down. */
        public Sheet freezeHeaderRow() {
            return freezeRows(1);
        }

        /**
         * Keeps the first rows in view while scrolling down, such as a few lines above a table's
         * headings and the headings themselves.
         *
         * @param count how many rows from the top to keep in view, at least 1
         * @return this sheet
         */
        public Sheet freezeRows(int count) {
            if (count < 1) {
                throw new IllegalArgumentException("Freeze at least one row, not " + count);
            }
            frozenRows = count;
            return this;
        }

        /**
         * Offers a drop-down of choices in a column, from a row to the bottom of the sheet.
         *
         * @param column   the column, from 0
         * @param firstRow the first row to offer it in, from 1
         * @param choices  the values offered, in order: none may hold a comma or a double quote, and
         *                 joined by commas they may be at most 255 characters, as Excel requires
         * @param strict   whether Excel refuses a value typed that is not among them; otherwise it
         *                 warns, and keeps the value if asked to
         * @return this sheet
         */
        public Sheet addDropDown(int column, int firstRow, List<String> choices, boolean strict) {
            if (choices.isEmpty() || choices.stream().anyMatch(choice -> choice.isBlank()
                    || choice.contains(",") || choice.contains("\""))) {
                throw new IllegalArgumentException("Not valid drop-down choices: " + choices);
            }
            if (String.join(",", choices).length() > MAX_CHOICES_LENGTH) {
                throw new IllegalArgumentException("Drop-down choices longer than " + MAX_CHOICES_LENGTH
                        + " characters: " + choices);
            }
            dropDowns.add(new DropDown(column, firstRow, "\"" + String.join(",", choices) + "\"", strict));
            return this;
        }

        /**
         * Offers a drop-down of the values in a range of cells, such as a list on another sheet, in a
         * column from a row to the bottom of the sheet. Blank cells of the range offer nothing.
         *
         * @param column   the column, from 0
         * @param firstRow the first row to offer it in, from 1
         * @param source   the cells whose values are offered
         * @param strict   whether Excel refuses a value typed that is not among them; otherwise it
         *                 warns, and keeps the value if asked to
         * @return this sheet
         */
        public Sheet addDropDown(int column, int firstRow, Range source, boolean strict) {
            dropDowns.add(new DropDown(column, firstRow, source.formula(), strict));
            return this;
        }

        private String toXml() {
            StringBuilder xml = new StringBuilder(XML_DECLARATION)
                    .append("<worksheet xmlns=\"").append(MAIN_NS).append("\">");
            if (frozenRows > 0) {
                xml.append("<sheetViews><sheetView workbookViewId=\"0\">")
                        .append("<pane ySplit=\"").append(frozenRows).append("\" topLeftCell=\"A")
                        .append(frozenRows + 1).append("\" activePane=\"bottomLeft\" state=\"frozen\"/>")
                        .append("</sheetView></sheetViews>");
            }
            if (columnWidths.length > 0) {
                xml.append("<cols>");
                for (int i = 0; i < columnWidths.length; i++) {
                    xml.append("<col min=\"").append(i + 1).append("\" max=\"").append(i + 1)
                            .append("\" width=\"").append(columnWidths[i]).append("\" customWidth=\"1\"/>");
                }
                xml.append("</cols>");
            }
            xml.append("<sheetData>");
            for (int r = 0; r < rows.size(); r++) {
                appendRow(xml, r + 1, rows.get(r));
            }
            xml.append("</sheetData>");
            // Excel requires the validations after the sheet's data.
            if (!dropDowns.isEmpty()) {
                xml.append("<dataValidations count=\"").append(dropDowns.size()).append("\">");
                dropDowns.forEach(dropDown -> dropDown.appendXml(xml));
                xml.append("</dataValidations>");
            }
            return xml.append("</worksheet>").toString();
        }

        private static void appendRow(StringBuilder xml, int rowNumber, List<Cell> cells) {
            xml.append("<row r=\"").append(rowNumber).append("\">");
            for (int c = 0; c < cells.size(); c++) {
                Cell cell = cells.get(c);
                if (cell != null && cell.value() != null) {
                    appendCell(xml, columnName(c) + rowNumber, cell);
                }
            }
            xml.append("</row>");
        }

        private static void appendCell(StringBuilder xml, String reference, Cell cell) {
            xml.append("<c r=\"").append(reference).append("\" s=\"").append(cell.style().ordinal()).append('"');
            switch (cell.value()) {
                case BigDecimal number -> xml.append("><v>").append(number.toPlainString()).append("</v></c>");
                case LocalDate date -> xml.append("><v>").append(ChronoUnit.DAYS.between(EXCEL_EPOCH, date))
                        .append("</v></c>");
                default -> xml.append(" t=\"inlineStr\"><is><t xml:space=\"preserve\">")
                        .append(escape(cell.value().toString())).append("</t></is></c>");
            }
        }
    }

    /**
     * Cells of one column of a sheet, from one row to another.
     *
     * @param sheet    the sheet's name
     * @param column   the column, from 0
     * @param firstRow the first row, from 1
     * @param lastRow  the last row, no earlier than the first
     */
    public record Range(String sheet, int column, int firstRow, int lastRow) {

        public Range {
            if (firstRow < 1 || lastRow < firstRow) {
                throw new IllegalArgumentException("Not a valid range of rows: " + firstRow + " to " + lastRow);
            }
        }

        /** The range as a formula refers to it, such as 'Clients'!$A$2:$A$501. */
        String formula() {
            String letters = columnName(column);
            return "'" + sheet.replace("'", "''") + "'!$" + letters + "$" + firstRow + ":$" + letters + "$" + lastRow;
        }
    }

    /**
     * A drop-down down a column, written as a list validation.
     *
     * @param formula what the list offers: its choices in double quotes, separated by commas, or a range
     */
    private record DropDown(int column, int firstRow, String formula, boolean strict) {

        private void appendXml(StringBuilder xml) {
            String letters = columnName(column);
            xml.append("<dataValidation type=\"list\"");
            if (!strict) {
                xml.append(" errorStyle=\"warning\"");
            }
            xml.append(" allowBlank=\"1\" showErrorMessage=\"1\" errorTitle=\"Not in the list\" error=\"")
                    .append(strict ? "Choose a value from the drop-down." : "This is not one of the values listed.")
                    .append("\" sqref=\"").append(letters).append(firstRow).append(':').append(letters)
                    .append(MAX_ROW).append("\"><formula1>")
                    .append(escape(formula))
                    .append("</formula1></dataValidation>");
        }
    }
}
