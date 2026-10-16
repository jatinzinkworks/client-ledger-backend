package com.psc.cl.service;

import com.psc.cl.exception.SpreadsheetValidationException;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

/**
 * Reads the cell values of an Excel workbook (Office Open XML, .xlsx) with nothing but the JDK: the
 * text of every non-empty cell of every sheet, by row and column. Enough to import a table typed or
 * pasted into Excel, LibreOffice or Google Sheets; formatting and formulas are not interpreted. A
 * formula cell gives the value Excel last calculated for it.
 *
 * <p>A number is read as Excel stores it, without its display format, with two exceptions, which
 * Excel stores as numbers but shows otherwise:
 * <ul>
 *   <li>a number formatted as a percentage is read as Excel shows it, so 10% typed into a cell,
 *       which Excel stores as 0.1, is read as {@code 10%}. It keeps every decimal Excel stores, not
 *       only those the format shows;</li>
 *   <li>a number formatted as a date is read as that date, written as {@code yyyy-MM-dd}, so 31/03/2026
 *       typed into a cell, which Excel stores as 46112, is read as {@code 2026-03-31}. A time of day
 *       on it is left out.</li>
 * </ul>
 * A whole number such as a phone number is read without a decimal point or exponent.
 *
 * <p>The file is untrusted, so the XML parser refuses DOCTYPE declarations, which shuts out
 * external entities and entity expansion, and the zip is read only up to fixed limits on its number
 * of entries and its total unpacked size.
 *
 * <p>Public, with {@link SpreadsheetImport}, so every module's bulk import reads workbooks the same way.
 */
public final class XlsxReader {

    /** Most entries accepted in the zip; a workbook of a few sheets has a dozen or so. */
    static final int MAX_ENTRIES = 1_000;

    /** Most bytes accepted once the zip is unpacked, to stop a small file expanding without bound. */
    static final long MAX_UNPACKED_BYTES = 20L * 1024 * 1024;

    /** Excel's built-in percentage number formats, 0% and 0.00%. */
    private static final Set<Integer> BUILT_IN_PERCENT_FORMATS = Set.of(9, 10);

    /**
     * Excel's built-in date number formats: 14 to 17 and 22, and those of East Asian locales, 27 to
     * 36 and 50 to 58. Times alone (18 to 21, 45 to 47) are not dates.
     */
    private static final Set<Integer> BUILT_IN_DATE_FORMATS = Set.of(14, 15, 16, 17, 22, 27, 28, 29, 30, 31, 32,
            33, 34, 35, 36, 50, 51, 52, 53, 54, 55, 56, 57, 58);

    /** Excel counts dates as days since this one (its 1900 date system). */
    private static final LocalDate EXCEL_EPOCH = LocalDate.of(1899, 12, 30);

    /**
     * The parts of a number format code that are not placeholders: quoted text, escaped characters
     * and bracketed colours, conditions or elapsed times. A % in any of them is shown as text, not as
     * a percentage, and a d or y as text, not as a date.
     */
    private static final Pattern LITERAL_IN_FORMAT = Pattern.compile("\"[^\"]*\"|\\\\.|\\[[^\\]]*\\]");

    /** A day or year placeholder, which makes a number format a date's. */
    private static final Pattern DATE_PLACEHOLDER = Pattern.compile("[dDyY]");

    /** How a number format shows a number, when not as the number itself. */
    private enum Shown {
        PERCENTAGE,
        DATE
    }

    private static final String NOT_A_WORKBOOK = "The file is not an Excel workbook (.xlsx). Save it from Excel, "
            + "LibreOffice or Google Sheets as .xlsx and upload it again";

    private XlsxReader() {
        // utility class
    }

    /**
     * Reads every sheet of a workbook.
     *
     * @param content the bytes of the .xlsx file
     * @return the workbook's sheets, in tab order
     * @throws SpreadsheetValidationException when the file is not a readable .xlsx workbook
     */
    public static Workbook read(byte[] content) {
        Map<String, byte[]> parts = unzip(content);
        byte[] workbookXml = parts.get("xl/workbook.xml");
        if (workbookXml == null) {
            throw new SpreadsheetValidationException(NOT_A_WORKBOOK);
        }
        List<String> sharedStrings = sharedStrings(parts.get("xl/sharedStrings.xml"));
        Map<Integer, Shown> shownStyles = shownStyles(parts.get("xl/styles.xml"));
        Map<String, String> targets = relationshipTargets(parts.get("xl/_rels/workbook.xml.rels"));

        List<Sheet> sheets = new ArrayList<>();
        for (Element sheet : elements(parse(workbookXml).getDocumentElement(), "sheet")) {
            String target = targets.get(relationshipId(sheet));
            byte[] sheetXml = target == null ? null : parts.get(partName(target));
            if (sheetXml == null) {
                throw new SpreadsheetValidationException(NOT_A_WORKBOOK);
            }
            sheets.add(new Sheet(sheet.getAttribute("name"), rows(parse(sheetXml), sharedStrings, shownStyles)));
        }
        return new Workbook(sheets);
    }

    /** A workbook's sheets, in tab order. */
    public record Workbook(List<Sheet> sheets) {

        /** The sheet of this name, ignoring case, as Excel does. */
        public Optional<Sheet> sheet(String name) {
            return sheets.stream().filter(sheet -> sheet.name().equalsIgnoreCase(name)).findFirst();
        }
    }

    /**
     * One sheet's non-empty cells.
     *
     * @param name the tab's name
     * @param rows each row with a non-empty cell, by row number from 1; each maps column index from 0
     *             (A is 0) to the cell's text
     */
    public record Sheet(String name, SortedMap<Integer, Map<Integer, String>> rows) {

        /** The text of a cell, or null when it is empty. */
        public String cell(int row, int column) {
            return rows.getOrDefault(row, Map.of()).get(column);
        }
    }

    private static Map<String, byte[]> unzip(byte[] content) {
        Map<String, byte[]> parts = new HashMap<>();
        long unpacked = 0;
        int entries = 0;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(content))) {
            ZipEntry entry;
            byte[] buffer = new byte[8192];
            while ((entry = zip.getNextEntry()) != null) {
                if (++entries > MAX_ENTRIES) {
                    throw new SpreadsheetValidationException("The workbook has too many parts to be read");
                }
                ByteArrayOutputStream part = new ByteArrayOutputStream();
                for (int read; (read = zip.read(buffer)) > 0; ) {
                    unpacked += read;
                    if (unpacked > MAX_UNPACKED_BYTES) {
                        throw new SpreadsheetValidationException("The workbook is too large to be read; split it "
                                + "into smaller files");
                    }
                    part.write(buffer, 0, read);
                }
                if (!entry.isDirectory()) {
                    parts.put(entry.getName(), part.toByteArray());
                }
            }
        } catch (IOException | IllegalArgumentException e) {
            // Not a zip at all, or a damaged one.
            throw new SpreadsheetValidationException(NOT_A_WORKBOOK);
        }
        return parts;
    }

    /** The text of each shared string, by index; cells of type "s" refer to them. */
    private static List<String> sharedStrings(byte[] xml) {
        if (xml == null) {
            return List.of();
        }
        List<String> strings = new ArrayList<>();
        Element root = parse(xml).getDocumentElement();
        for (Node node = root.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element element && "si".equals(element.getLocalName())) {
                strings.add(text(element));
            }
        }
        return strings;
    }

    /**
     * The cell styles, by index, whose number format shows a number as something else: a percentage,
     * by one of Excel's built-in percentage formats or a custom format with a % outside its literal
     * text; or a date, by one of Excel's built-in date formats or a custom format with a day or year
     * outside its literal text. Cells refer to their style by this index in their s attribute.
     */
    private static Map<Integer, Shown> shownStyles(byte[] xml) {
        if (xml == null) {
            return Map.of();
        }
        Element root = parse(xml).getDocumentElement();
        Map<Integer, Shown> formats = new HashMap<>();
        BUILT_IN_PERCENT_FORMATS.forEach(id -> formats.put(id, Shown.PERCENTAGE));
        BUILT_IN_DATE_FORMATS.forEach(id -> formats.put(id, Shown.DATE));
        for (Element format : elements(root, "numFmt")) {
            String placeholders = LITERAL_IN_FORMAT.matcher(format.getAttribute("formatCode")).replaceAll("");
            if (placeholders.contains("%")) {
                formats.put(integer(format.getAttribute("numFmtId")), Shown.PERCENTAGE);
            } else if (DATE_PLACEHOLDER.matcher(placeholders).find()) {
                formats.put(integer(format.getAttribute("numFmtId")), Shown.DATE);
            }
        }
        List<Element> cellFormats = elements(root, "cellXfs");
        if (cellFormats.isEmpty()) {
            return Map.of();
        }
        Map<Integer, Shown> styles = new HashMap<>();
        int index = 0;
        for (Node node = cellFormats.getFirst().getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element style && "xf".equals(style.getLocalName())) {
                int formatId = style.hasAttribute("numFmtId") ? integer(style.getAttribute("numFmtId")) : 0;
                Shown shown = formats.get(formatId);
                if (shown != null) {
                    styles.put(index, shown);
                }
                index++;
            }
        }
        return styles;
    }

    /** Where each relationship of the workbook points, by its identifier. */
    private static Map<String, String> relationshipTargets(byte[] xml) {
        if (xml == null) {
            throw new SpreadsheetValidationException(NOT_A_WORKBOOK);
        }
        Map<String, String> targets = new HashMap<>();
        for (Element relationship : elements(parse(xml).getDocumentElement(), "Relationship")) {
            targets.put(relationship.getAttribute("Id"), relationship.getAttribute("Target"));
        }
        return targets;
    }

    /**
     * The r:id of a sheet entry. Looked up by local name, as the relationships namespace differs
     * between the transitional and strict forms of the format.
     */
    private static String relationshipId(Element sheet) {
        NamedNodeMap attributes = sheet.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            Attr attribute = (Attr) attributes.item(i);
            if ("id".equals(attribute.getLocalName()) && attribute.getNamespaceURI() != null) {
                return attribute.getValue();
            }
        }
        return null;
    }

    /** A relationship target as a zip entry name; relative targets are relative to xl/. */
    private static String partName(String target) {
        return target.startsWith("/") ? target.substring(1) : "xl/" + target;
    }

    private static SortedMap<Integer, Map<Integer, String>> rows(Document sheet, List<String> sharedStrings,
                                                                 Map<Integer, Shown> shownStyles) {
        SortedMap<Integer, Map<Integer, String>> rows = new TreeMap<>();
        int rowNumber = 0;
        for (Element row : elements(sheet.getDocumentElement(), "row")) {
            // Rows and cells normally carry their position; when one does not, it follows the last.
            rowNumber = row.hasAttribute("r") ? integer(row.getAttribute("r")) : rowNumber + 1;
            Map<Integer, String> cells = new HashMap<>();
            int column = -1;
            for (Node node = row.getFirstChild(); node != null; node = node.getNextSibling()) {
                if (!(node instanceof Element cell) || !"c".equals(cell.getLocalName())) {
                    continue;
                }
                column = cell.hasAttribute("r") ? columnIndex(cell.getAttribute("r")) : column + 1;
                String value = value(cell, sharedStrings, shownStyles);
                if (value != null && !value.isBlank()) {
                    cells.put(column, value);
                }
            }
            if (!cells.isEmpty()) {
                rows.put(rowNumber, Collections.unmodifiableMap(cells));
            }
        }
        return Collections.unmodifiableSortedMap(rows);
    }

    private static String value(Element cell, List<String> sharedStrings, Map<Integer, Shown> shownStyles) {
        String type = cell.getAttribute("t");
        if ("inlineStr".equals(type)) {
            Element inline = child(cell, "is");
            return inline == null ? null : text(inline);
        }
        Element valueElement = child(cell, "v");
        if (valueElement == null) {
            return null;
        }
        String raw = valueElement.getTextContent();
        return switch (type) {
            case "s" -> sharedString(raw, sharedStrings);
            case "b" -> "1".equals(raw.strip()) ? "TRUE" : "FALSE";
            case "str", "e" -> raw;
            default -> switch (shownStyles.get(style(cell))) {
                case PERCENTAGE -> percentage(raw);
                case DATE -> date(raw);
                case null -> number(raw);
            };
        };
    }

    /**
     * A number formatted as a date, as that date: Excel's count of days since its epoch, written as
     * yyyy-MM-dd, any fraction of a day, its time, left out. Anything that is not a number is kept as
     * it is.
     */
    private static String date(String raw) {
        try {
            long days = new BigDecimal(raw.strip()).setScale(0, RoundingMode.FLOOR).longValueExact();
            return EXCEL_EPOCH.plusDays(days).toString();
        } catch (NumberFormatException | ArithmeticException | DateTimeException e) {
            return raw;
        }
    }

    /** The index of a cell's style; a cell without one has the default style, 0. */
    private static int style(Element cell) {
        return cell.hasAttribute("s") ? integer(cell.getAttribute("s")) : 0;
    }

    /**
     * A number formatted as a percentage, as Excel shows it: 0.1 as 10%, and 0.125 as 12.5% whatever
     * decimals the format shows. Anything that is not a number is kept as it is.
     */
    private static String percentage(String raw) {
        try {
            return new BigDecimal(raw.strip()).movePointRight(2).stripTrailingZeros().toPlainString() + "%";
        } catch (NumberFormatException e) {
            return raw;
        }
    }

    private static String sharedString(String index, List<String> sharedStrings) {
        try {
            return sharedStrings.get(Integer.parseInt(index.strip()));
        } catch (NumberFormatException | IndexOutOfBoundsException e) {
            throw new SpreadsheetValidationException(NOT_A_WORKBOOK);
        }
    }

    /**
     * A number as plain text, such as 9820012345 rather than 9.820012345E9, and 10.5 rather than
     * 10.50; anything that is not a number is kept as it is.
     */
    private static String number(String raw) {
        try {
            return new BigDecimal(raw.strip()).stripTrailingZeros().toPlainString();
        } catch (NumberFormatException e) {
            return raw;
        }
    }

    /** A whole number from the workbook's XML, such as a row number or a style index. */
    private static int integer(String number) {
        try {
            return Integer.parseInt(number.strip());
        } catch (NumberFormatException e) {
            throw new SpreadsheetValidationException(NOT_A_WORKBOOK);
        }
    }

    /** The column of a reference such as AB12, counting from 0 for A. */
    static int columnIndex(String reference) {
        int index = 0;
        for (int i = 0; i < reference.length() && Character.isLetter(reference.charAt(i)); i++) {
            index = index * 26 + (Character.toUpperCase(reference.charAt(i)) - 'A' + 1);
        }
        return index - 1;
    }

    /**
     * The text of a string item: its own text, or its rich-text runs joined, leaving out phonetic
     * guides (rPh), which are not part of the value.
     */
    private static String text(Element item) {
        StringBuilder text = new StringBuilder();
        for (Element t : elements(item, "t")) {
            Node parent = t.getParentNode();
            if (!(parent instanceof Element element && "rPh".equals(element.getLocalName()))) {
                text.append(t.getTextContent());
            }
        }
        return text.toString();
    }

    private static Element child(Element parent, String localName) {
        for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element element && localName.equals(element.getLocalName())) {
                return element;
            }
        }
        return null;
    }

    /** Every element of this local name below the given one, in any namespace. */
    private static List<Element> elements(Element root, String localName) {
        NodeList nodes = root.getElementsByTagNameNS("*", localName);
        List<Element> elements = new ArrayList<>(nodes.getLength());
        for (int i = 0; i < nodes.getLength(); i++) {
            elements.add((Element) nodes.item(i));
        }
        return elements;
    }

    private static Document parse(byte[] xml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            try (InputStream in = new ByteArrayInputStream(xml)) {
                return factory.newDocumentBuilder().parse(in);
            }
        } catch (ParserConfigurationException e) {
            throw new IllegalStateException("The XML parser could not be configured securely", e);
        } catch (SAXException | IOException e) {
            throw new SpreadsheetValidationException(NOT_A_WORKBOOK);
        }
    }
}
