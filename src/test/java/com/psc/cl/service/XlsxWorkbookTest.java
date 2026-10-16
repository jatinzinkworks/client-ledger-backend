package com.psc.cl.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.psc.cl.service.XlsxWorkbook.Cell;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class XlsxWorkbookTest {

    @Test
    @DisplayName("packages the parts Excel needs, with one worksheet per sheet, named in order")
    void packagesWorkbookParts() {
        XlsxWorkbook workbook = new XlsxWorkbook();
        workbook.addSheet("Summary").addRow(Cell.text("a"));
        workbook.addSheet("Ledger").addRow(Cell.text("b"));

        Map<String, String> parts = parts(workbook.toBytes());

        assertThat(parts).containsOnlyKeys("[Content_Types].xml", "_rels/.rels", "xl/workbook.xml",
                "xl/_rels/workbook.xml.rels", "xl/styles.xml", "xl/worksheets/sheet1.xml",
                "xl/worksheets/sheet2.xml");
        assertThat(parts.get("xl/workbook.xml"))
                .contains("<sheet name=\"Summary\" sheetId=\"1\" r:id=\"rId1\"/>")
                .contains("<sheet name=\"Ledger\" sheetId=\"2\" r:id=\"rId2\"/>");
        // The styles take the identifier after the last sheet's.
        assertThat(parts.get("xl/_rels/workbook.xml.rels")).contains("Id=\"rId3\"").contains("styles.xml");
        assertThat(parts.get("[Content_Types].xml"))
                .contains("/xl/worksheets/sheet1.xml")
                .contains("/xl/worksheets/sheet2.xml");
    }

    @Test
    @DisplayName("writes text inline, amounts as plain numbers and dates as Excel day numbers, each styled")
    void writesCells() {
        XlsxWorkbook workbook = new XlsxWorkbook();
        workbook.addSheet("Ledger", 13, 32)
                .freezeHeaderRow()
                .addRow(Cell.bold("Date"), Cell.bold("Amount"))
                .addRow(Cell.date(LocalDate.of(2026, 10, 7)), Cell.amount(new BigDecimal("5605.00")))
                .addBlankRow()
                .addRow(Cell.empty(), Cell.boldAmount(new BigDecimal("-1000.50")));

        String sheet = parts(workbook.toBytes()).get("xl/worksheets/sheet1.xml");

        assertThat(sheet)
                .contains("<pane ySplit=\"1\" topLeftCell=\"A2\" activePane=\"bottomLeft\" state=\"frozen\"/>")
                .contains("<col min=\"1\" max=\"1\" width=\"13\" customWidth=\"1\"/>")
                .contains("<col min=\"2\" max=\"2\" width=\"32\" customWidth=\"1\"/>")
                .contains("<c r=\"A1\" s=\"1\" t=\"inlineStr\"><is><t xml:space=\"preserve\">Date</t></is></c>")
                // 7 October 2026 is day 46302 counting from 30 December 1899.
                .contains("<c r=\"A2\" s=\"3\"><v>46302</v></c>")
                .contains("<c r=\"B2\" s=\"2\"><v>5605.00</v></c>")
                .contains("<row r=\"3\"></row>")
                // Empty cells are left out, not written blank.
                .contains("<row r=\"4\"><c r=\"B4\" s=\"4\"><v>-1000.50</v></c></row>");
    }

    @Test
    @DisplayName("escapes markup, drops control characters, and never writes text as a formula")
    void escapesText() {
        XlsxWorkbook workbook = new XlsxWorkbook();
        workbook.addSheet("Ledger").addRow(Cell.text("=HYPERLINK(\"x\") & <b>\u0007"));

        String sheet = parts(workbook.toBytes()).get("xl/worksheets/sheet1.xml");

        assertThat(sheet)
                .contains("t=\"inlineStr\"><is><t xml:space=\"preserve\">=HYPERLINK(&quot;x&quot;) &amp; "
                        + "&lt;b&gt;</t></is>")
                .doesNotContain("<f>");
    }

    @Test
    @DisplayName("names columns A to Z, then AA onwards")
    void namesColumns() {
        assertThat(XlsxWorkbook.columnName(0)).isEqualTo("A");
        assertThat(XlsxWorkbook.columnName(25)).isEqualTo("Z");
        assertThat(XlsxWorkbook.columnName(26)).isEqualTo("AA");
        assertThat(XlsxWorkbook.columnName(701)).isEqualTo("ZZ");
        assertThat(XlsxWorkbook.columnName(702)).isEqualTo("AAA");
    }

    @Test
    @DisplayName("refuses sheet names Excel would reject, and a workbook with no sheets")
    void rejectsInvalidWorkbooks() {
        XlsxWorkbook workbook = new XlsxWorkbook();

        assertThatThrownBy(workbook::toBytes).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> workbook.addSheet("")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> workbook.addSheet("FY 2026/27")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> workbook.addSheet("A".repeat(32))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> workbook.addSheet("'Mehta'")).isInstanceOf(IllegalArgumentException.class);

        workbook.addSheet("Ledger");
        assertThatThrownBy(() -> workbook.addSheet("LEDGER")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("turns any name into a valid sheet name: forbidden characters replaced, cut to 31, kept unique")
    void namesSheets() {
        XlsxWorkbook workbook = new XlsxWorkbook();
        workbook.addSheet("Ledger");

        assertThat(workbook.sheetName("M/s Mehta: [HUF]?", "Company")).isEqualTo("M-s Mehta- -HUF--");
        assertThat(workbook.sheetName("Mehta Textiles and Garments Private Limited", "Company"))
                .isEqualTo("Mehta Textiles and Garments Pri");
        // Excel compares sheet names ignoring case.
        assertThat(workbook.sheetName("ledger", "Company")).isEqualTo("ledger (2)");
        assertThat(workbook.sheetName(" ''' ", "Company")).isEqualTo("Company");

        String longName = "Mehta Textiles and Garments Private Limited";
        workbook.addSheet(workbook.sheetName(longName, "Company"));
        String second = workbook.sheetName(longName, "Company");
        assertThat(second).isEqualTo("Mehta Textiles and Garments (2)").hasSize(31);
        workbook.addSheet(second);
        assertThat(workbook.sheetName(longName, "Company")).isEqualTo("Mehta Textiles and Garments (3)");
    }

    /**
     * Unzips a workbook into its parts, by name, each read as UTF-8 text. Public for the tests of the
     * workbooks other modules write.
     */
    public static Map<String, String> parts(byte[] xlsx) {
        Map<String, String> parts = new LinkedHashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(xlsx), StandardCharsets.UTF_8)) {
            for (ZipEntry entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                parts.put(entry.getName(), new String(zip.readAllBytes(), StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return parts;
    }
}
