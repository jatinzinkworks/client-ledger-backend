package com.psc.cl.ledger.service;

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
 * and dates, a bold heading style, column widths and a frozen header row.
 *
 * <p>Text is written as inline strings, never as formulas, so a value such as {@code =SUM(A1)}
 * typed into a comment shows as typed and is not evaluated when the file is opened.
 */
final class XlsxWorkbook {

    /** Excel counts dates as days since this one (its 1900 date system). */
    private static final LocalDate EXCEL_EPOCH = LocalDate.of(1899, 12, 30);

    /** Excel refuses sheet names longer than this. */
    private static final int MAX_SHEET_NAME_LENGTH = 31;

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
    enum Style {
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
    Sheet addSheet(String name, int... columnWidths) {
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
    String sheetName(String text, String fallback) {
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
    byte[] toBytes() {
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
    static String columnName(int index) {
        StringBuilder name = new StringBuilder();
        for (int n = index + 1; n > 0; n = (n - 1) / 26) {
            name.insert(0, (char) ('A' + (n - 1) % 26));
        }
        return name.toString();
    }

    /** A value and how to show it; a null value leaves the cell empty. */
    record Cell(Object value, Style style) {

        static Cell text(String value) {
            return new Cell(value, Style.TEXT);
        }

        static Cell bold(String value) {
            return new Cell(value, Style.BOLD);
        }

        static Cell amount(BigDecimal value) {
            return new Cell(value, Style.AMOUNT);
        }

        static Cell boldAmount(BigDecimal value) {
            return new Cell(value, Style.BOLD_AMOUNT);
        }

        static Cell date(LocalDate value) {
            return new Cell(value, Style.DATE);
        }

        static Cell empty() {
            return new Cell(null, Style.TEXT);
        }
    }

    /** One tab of the workbook. */
    static final class Sheet {

        private final String name;

        private final int[] columnWidths;

        private final List<List<Cell>> rows = new ArrayList<>();

        private boolean headerFrozen;

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
        Sheet addRow(Cell... cells) {
            rows.add(Arrays.asList(cells));
            return this;
        }

        /** Adds an empty row, to space out sections. */
        Sheet addBlankRow() {
            rows.add(List.of());
            return this;
        }

        /** Keeps the first row in view while scrolling down. */
        Sheet freezeHeaderRow() {
            headerFrozen = true;
            return this;
        }

        private String toXml() {
            StringBuilder xml = new StringBuilder(XML_DECLARATION)
                    .append("<worksheet xmlns=\"").append(MAIN_NS).append("\">");
            if (headerFrozen) {
                xml.append("<sheetViews><sheetView workbookViewId=\"0\">")
                        .append("<pane ySplit=\"1\" topLeftCell=\"A2\" activePane=\"bottomLeft\" state=\"frozen\"/>")
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
            return xml.append("</sheetData></worksheet>").toString();
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
}
