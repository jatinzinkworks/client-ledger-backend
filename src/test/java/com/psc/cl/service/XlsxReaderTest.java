package com.psc.cl.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.psc.cl.exception.SpreadsheetValidationException;
import com.psc.cl.service.XlsxWorkbook.Cell;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

class XlsxReaderTest {

    private static final String MAIN_NS = "http://schemas.openxmlformats.org/spreadsheetml/2006/main";

    private static final String REL_NS = "http://schemas.openxmlformats.org/officeDocument/2006/relationships";

    @Test
    @DisplayName("reads back the text and numbers of a workbook this service writes, sheet by sheet")
    void readsInlineStringsAndNumbers() {
        XlsxWorkbook workbook = new XlsxWorkbook();
        workbook.addSheet("Clients").addRow(Cell.text("Client Ref"), Cell.text("Phone"))
                .addRow(Cell.text("C1"), Cell.amount(new BigDecimal("9820012345")));
        workbook.addSheet("Companies").addRow(Cell.empty(), Cell.text("Address"));

        XlsxReader.Workbook read = XlsxReader.read(workbook.toBytes());

        assertThat(read.sheets()).extracting(XlsxReader.Sheet::name).containsExactly("Clients", "Companies");
        XlsxReader.Sheet clients = read.sheet("clients").orElseThrow();
        assertThat(clients.cell(1, 0)).isEqualTo("Client Ref");
        assertThat(clients.cell(2, 1)).isEqualTo("9820012345");
        assertThat(clients.cell(3, 0)).isNull();
        // An empty first cell leaves the second where it is.
        assertThat(read.sheet("Companies").orElseThrow().cell(1, 1)).isEqualTo("Address");
    }

    @Test
    @DisplayName("reads shared strings, rich text, booleans and numbers as Excel saves them")
    void readsExcelCellTypes() {
        String sharedStrings = "<sst xmlns=\"" + MAIN_NS + "\">"
                + "<si><t>Rohan</t></si>"
                + "<si><r><t>Mehta </t></r><r><t>Textiles</t></r><rPh><t>phonetic</t></rPh></si>"
                + "</sst>";
        String sheet = "<worksheet xmlns=\"" + MAIN_NS + "\"><sheetData>"
                + "<row r=\"2\">"
                + "<c r=\"A2\" t=\"s\"><v>0</v></c>"
                + "<c r=\"C2\" t=\"s\"><v>1</v></c>"
                + "<c r=\"D2\" t=\"b\"><v>1</v></c>"
                + "<c r=\"E2\"><v>9.820012345E9</v></c>"
                + "<c r=\"F2\"><v>12.50</v></c>"
                + "<c r=\"G2\" t=\"str\"><f>A2</f><v>Rohan</v></c>"
                + "<c r=\"H2\" t=\"inlineStr\"><is><t>  </t></is></c>"
                + "</row></sheetData></worksheet>";

        XlsxReader.Sheet read = XlsxReader.read(excelFile(sheet, sharedStrings)).sheet("Sheet1").orElseThrow();

        assertThat(read.cell(2, 0)).isEqualTo("Rohan");
        assertThat(read.cell(2, 1)).isNull();
        assertThat(read.cell(2, 2)).isEqualTo("Mehta Textiles");
        assertThat(read.cell(2, 3)).isEqualTo("TRUE");
        assertThat(read.cell(2, 4)).isEqualTo("9820012345");
        assertThat(read.cell(2, 5)).isEqualTo("12.5");
        assertThat(read.cell(2, 6)).isEqualTo("Rohan");
        // Blank cells are left out.
        assertThat(read.cell(2, 7)).isNull();
        assertThat(read.rows()).containsOnlyKeys(2);
    }

    @Test
    @DisplayName("reads a number formatted as a percentage as Excel shows it, and other numbers as stored")
    void readsPercentagesAsShown() {
        // Styles: 0 General, 1 built-in 0%, 2 built-in 0.00%, 3 custom 0.0%, 4 custom with % only as text,
        // 5 the amount format.
        String styles = "<styleSheet xmlns=\"" + MAIN_NS + "\"><numFmts count=\"3\">"
                + "<numFmt numFmtId=\"164\" formatCode=\"0.0%\"/>"
                + "<numFmt numFmtId=\"165\" formatCode=\"0&quot; %&quot;\"/>"
                + "<numFmt numFmtId=\"166\" formatCode=\"#,##0.00\"/></numFmts>"
                + "<cellXfs count=\"6\"><xf numFmtId=\"0\"/><xf numFmtId=\"9\"/><xf numFmtId=\"10\"/>"
                + "<xf numFmtId=\"164\"/><xf numFmtId=\"165\"/><xf numFmtId=\"166\"/></cellXfs></styleSheet>";
        String sheet = "<worksheet xmlns=\"" + MAIN_NS + "\"><sheetData><row r=\"1\">"
                + "<c r=\"A1\"><v>0.1</v></c>"
                + "<c r=\"B1\" s=\"1\"><v>0.1</v></c>"
                + "<c r=\"C1\" s=\"2\"><v>0.125</v></c>"
                + "<c r=\"D1\" s=\"3\"><v>0.05</v></c>"
                + "<c r=\"E1\" s=\"4\"><v>10</v></c>"
                + "<c r=\"F1\" s=\"5\"><v>10</v></c>"
                + "<c r=\"G1\" s=\"1\" t=\"inlineStr\"><is><t>10</t></is></c>"
                + "</row></sheetData></worksheet>";

        XlsxReader.Sheet read = XlsxReader.read(excelFile(sheet, null, styles)).sheet("Sheet1").orElseThrow();

        assertThat(read.cell(1, 0)).isEqualTo("0.1");
        assertThat(read.cell(1, 1)).isEqualTo("10%");
        // Every stored decimal is kept, whatever the format shows.
        assertThat(read.cell(1, 2)).isEqualTo("12.5%");
        assertThat(read.cell(1, 3)).isEqualTo("5%");
        // A % inside quotes is text, not a percentage format.
        assertThat(read.cell(1, 4)).isEqualTo("10");
        assertThat(read.cell(1, 5)).isEqualTo("10");
        // Text in a percentage-formatted cell stays as typed.
        assertThat(read.cell(1, 6)).isEqualTo("10");
    }

    @Test
    @DisplayName("reads a number formatted as a date as that date, and other numbers as stored")
    void readsDatesAsDates() {
        // Styles: 0 General, 1 built-in d/m/yyyy, 2 custom dd-mmm-yyyy, 3 custom with a d only as text,
        // 4 a time of day alone.
        String styles = "<styleSheet xmlns=\"" + MAIN_NS + "\"><numFmts count=\"2\">"
                + "<numFmt numFmtId=\"164\" formatCode=\"dd\\-mmm\\-yyyy\"/>"
                + "<numFmt numFmtId=\"165\" formatCode=\"0&quot; days&quot;\"/></numFmts>"
                + "<cellXfs count=\"5\"><xf numFmtId=\"0\"/><xf numFmtId=\"14\"/><xf numFmtId=\"164\"/>"
                + "<xf numFmtId=\"165\"/><xf numFmtId=\"21\"/></cellXfs></styleSheet>";
        String sheet = "<worksheet xmlns=\"" + MAIN_NS + "\"><sheetData><row r=\"1\">"
                + "<c r=\"A1\"><v>46112</v></c>"
                + "<c r=\"B1\" s=\"1\"><v>46112</v></c>"
                + "<c r=\"C1\" s=\"2\"><v>46112.75</v></c>"
                + "<c r=\"D1\" s=\"3\"><v>30</v></c>"
                + "<c r=\"E1\" s=\"4\"><v>0.5</v></c>"
                + "</row></sheetData></worksheet>";

        XlsxReader.Sheet read = XlsxReader.read(excelFile(sheet, null, styles)).sheet("Sheet1").orElseThrow();

        assertThat(read.cell(1, 0)).isEqualTo("46112");
        assertThat(read.cell(1, 1)).isEqualTo("2026-03-31");
        // The time of day is left out.
        assertThat(read.cell(1, 2)).isEqualTo("2026-03-31");
        assertThat(read.cell(1, 3)).isEqualTo("30");
        assertThat(read.cell(1, 4)).isEqualTo("0.5");
    }

    @Test
    @DisplayName("reads back the dates of a workbook this service writes")
    void readsWrittenDates() {
        XlsxWorkbook workbook = new XlsxWorkbook();
        workbook.addSheet("Dates").addRow(Cell.date(LocalDate.of(2026, 4, 1)));

        assertThat(XlsxReader.read(workbook.toBytes()).sheet("Dates").orElseThrow().cell(1, 0))
                .isEqualTo("2026-04-01");
    }

    @Test
    @DisplayName("refuses a file that is not an xlsx workbook")
    void refusesOtherFiles() {
        assertThatThrownBy(() -> XlsxReader.read("Client Ref,First Name\nC1,Rohan".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(SpreadsheetValidationException.class)
                .hasMessageContaining("not an Excel workbook");
    }

    @Test
    @DisplayName("refuses a workbook declaring a DOCTYPE, so no external entity is ever resolved")
    void refusesDoctype() {
        String sheet = "<!DOCTYPE worksheet [<!ENTITY xxe SYSTEM \"file:///etc/passwd\">]>"
                + "<worksheet xmlns=\"" + MAIN_NS + "\"><sheetData><row r=\"1\">"
                + "<c r=\"A1\" t=\"inlineStr\"><is><t>&xxe;</t></is></c></row></sheetData></worksheet>";

        assertThatThrownBy(() -> XlsxReader.read(excelFile(sheet, null)))
                .isInstanceOf(SpreadsheetValidationException.class);
    }

    @Test
    @DisplayName("counts columns from A as 0, through Z and on to AA")
    void readsColumnIndexes() {
        assertThat(XlsxReader.columnIndex("A1")).isZero();
        assertThat(XlsxReader.columnIndex("Z9")).isEqualTo(25);
        assertThat(XlsxReader.columnIndex("AA10")).isEqualTo(26);
    }

    private static byte[] excelFile(String sheetXml, String sharedStringsXml) {
        return excelFile(sheetXml, sharedStringsXml, null);
    }

    /** A one-sheet workbook laid out as Excel saves one, with the given sheet, shared strings and styles. */
    private static byte[] excelFile(String sheetXml, String sharedStringsXml, String stylesXml) {
        Map<String, String> parts = new LinkedHashMap<>();
        if (stylesXml != null) {
            parts.put("xl/styles.xml", stylesXml);
        }
        parts.put("xl/workbook.xml", "<workbook xmlns=\"" + MAIN_NS + "\" xmlns:r=\"" + REL_NS + "\"><sheets>"
                + "<sheet name=\"Sheet1\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>");
        parts.put("xl/_rels/workbook.xml.rels", "<Relationships "
                + "xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                + "<Relationship Id=\"rId1\" Type=\"" + REL_NS + "/worksheet\" Target=\"/xl/worksheets/sheet1.xml\"/>"
                + "</Relationships>");
        parts.put("xl/worksheets/sheet1.xml", sheetXml);
        if (sharedStringsXml != null) {
            parts.put("xl/sharedStrings.xml", sharedStringsXml);
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            for (Map.Entry<String, String> part : parts.entrySet()) {
                zip.putNextEntry(new ZipEntry(part.getKey()));
                zip.write(part.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return bytes.toByteArray();
    }
}
