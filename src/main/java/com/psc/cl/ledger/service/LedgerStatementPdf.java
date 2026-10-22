package com.psc.cl.ledger.service;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.ColumnText;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfWriter;
import com.psc.cl.ledger.dto.ClientLedgerSummaryResponse;
import com.psc.cl.ledger.dto.CompanyLedgerSummaryResponse;
import com.psc.cl.ledger.dto.LastPayment;
import com.psc.cl.ledger.dto.OpeningBalance;
import com.psc.cl.ledger.model.LedgerEntry;
import com.psc.cl.ledger.model.LedgerEntryType;
import com.psc.cl.service.FirmLogo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Lays out a ledger statement for one financial year as a PDF, to send to a client: first a summary
 * of the year, then the details of each company, every entry in date order with a running balance
 * and a totals row.
 *
 * <p>Each charge is shown only as its total fee, with the discount and GST already folded in, and
 * each payment as the amount paid. No discount is shown anywhere: the statement says what was
 * charged and what was paid, not how the charge was reached.
 *
 * <p>An opening balance, the balance a company was taken on with, is shown as a figure of its own,
 * not as billed, and as a line of the company's details: under Total Fee when owed, under Amount Paid
 * when the company started in credit. Above its details, each company also shows its opening balance
 * and the date it stood at, whatever the year it falls in, or None; only one dated in the year counts
 * in the year's figures.
 *
 * <p>Every page is headed by the firm's logo ({@link FirmLogo}) and name, each left out when missing.
 *
 * <p>Amounts carry the rupee sign when the fonts {@value #REGULAR_FONT} and {@value #BOLD_FONT} are on
 * the classpath, and are embedded. Without them the built-in Helvetica is used, which has no rupee
 * sign, so amounts are written with "Rs." instead.
 */
final class LedgerStatementPdf {

    private static final Logger log = LoggerFactory.getLogger(LedgerStatementPdf.class);

    static final String REGULAR_FONT = "fonts/NotoSans-Regular.ttf";

    static final String BOLD_FONT = "fonts/NotoSans-Bold.ttf";

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

    private static final Color BRAND = new Color(0x1f, 0x3a, 0x5f);

    private static final Color RULE = new Color(0xe3, 0xe6, 0xea);

    private static final Color SHADE = new Color(0xf0, 0xf2, 0xf5);

    private static final Color MUTED = new Color(0x55, 0x55, 0x55);

    /** Headings of a company's entries table, in the order {@link #addEntries} fills them. */
    static final List<String> ENTRY_COLUMNS = List.of("Date", "Entry Type", "Service", "Frequency", "Total Fee",
            "Amount Paid", "Balance", "Payment Method", "Reference");

    private static final float[] ENTRY_WIDTHS = {10, 10, 25, 10, 11, 11, 11, 11, 15};

    /** Position of the first amount column; it and the two after it are right-aligned. */
    private static final int FIRST_AMOUNT_COLUMN = ENTRY_COLUMNS.indexOf("Total Fee");

    private static final BigDecimal ZERO = new BigDecimal("0.00");

    private LedgerStatementPdf() {
        // utility class
    }

    /**
     * Writes the statement of a client across all of their companies: the client's summary for the
     * year and a line per company, then each company's entries on a page of its own, in order of
     * company name. A company with no entries in the year still gets its page, saying so.
     *
     * @param summary      the client's summary for the year
     * @param entries      every entry of the year, in the order to list them
     * @param companyNames the name of each of the client's companies, by identifier
     * @param openings     the opening balance of each company that has one, by identifier
     * @param generatedOn  the firm's calendar day the statement was made
     * @param firmName     the firm's name, shown with its logo at the top of every page; null when none
     *                     is saved
     * @return the content of the .pdf file
     */
    static byte[] forClient(ClientLedgerSummaryResponse summary, List<LedgerEntry> entries,
                            Map<UUID, String> companyNames, Map<UUID, OpeningBalance> openings,
                            LocalDate generatedOn, String firmName) {
        Map<UUID, List<LedgerEntry>> entriesByCompany = entries.stream()
                .collect(Collectors.groupingBy(LedgerEntry::getCompanyId));
        List<Section> sections = companyNames.entrySet().stream()
                .sorted(Map.Entry.<UUID, String>comparingByValue(String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(Map.Entry.comparingByKey()))
                .map(company -> new Section(company.getValue(), openings.get(company.getKey()),
                        entriesByCompany.getOrDefault(company.getKey(), List.of())))
                .toList();
        Figures figures = new Figures(summary.getFinancialYear(), summary.getFromDate(), summary.getToDate(),
                summary.getTotalBilled(), orZero(summary.getOpeningBalance()), summary.getTotalPaymentReceived(),
                summary.getPending(), summary.getLastPayment());
        return write(summary.getClientName(), null, figures, sections, generatedOn, firmName);
    }

    /**
     * Writes the statement of one company of a client: the company's summary for the year, then its
     * entries.
     *
     * @param clientName  the name of the client the company belongs to
     * @param summary     the company's summary for the year
     * @param entries     every entry of the year, in the order to list them
     * @param opening     the company's opening balance, or null when it has none
     * @param generatedOn the firm's calendar day the statement was made
     * @param firmName    the firm's name, shown with its logo at the top of every page; null when none is
     *                    saved
     * @return the content of the .pdf file
     */
    static byte[] forCompany(String clientName, CompanyLedgerSummaryResponse summary, List<LedgerEntry> entries,
                             OpeningBalance opening, LocalDate generatedOn, String firmName) {
        Figures figures = new Figures(summary.getFinancialYear(), summary.getFromDate(), summary.getToDate(),
                summary.getTotalBilled(), orZero(summary.getOpeningBalance()), summary.getTotalPaymentReceived(),
                summary.getPending(), summary.getLastPayment());
        return write(clientName, summary.getCompanyName(), figures,
                List.of(new Section(summary.getCompanyName(), opening, entries)), generatedOn, firmName);
    }

    /**
     * @param companyName the company a single company's statement is for; null for a client's, which
     *                    lists a line per company in its summary and gives each company a page
     * @param firmName    the firm's name for the page header; null when none is saved
     */
    private static byte[] write(String clientName, String companyName, Figures figures, List<Section> sections,
                                LocalDate generatedOn, String firmName) {
        Fonts fonts = Fonts.load();
        boolean forClient = companyName == null;
        Header header = Header.of(fonts, firmName);
        // A page header needs room above the content; without one, the page starts at the usual margin.
        float topMargin = header == null ? 36 : 36 + Header.HEIGHT + Header.GAP;
        Document document = new Document(PageSize.A4.rotate(), 36, 36, topMargin, 48);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            PdfWriter writer = PdfWriter.getInstance(document, out);
            if (header != null) {
                writer.setPageEvent(header);
            }
            writer.setPageEvent(new Footer(fonts, "Ledger statement for " + clientName + ", FY "
                    + figures.financialYear()));
            document.open();

            addHeading(document, fonts, clientName, companyName, figures, generatedOn);
            addSummary(document, fonts, figures, forClient);
            if (forClient) {
                addBreakdown(document, fonts, sections);
            }
            for (Section section : sections) {
                if (forClient) {
                    // Each company's details start on a page of their own.
                    document.newPage();
                }
                addSection(document, fonts, section, forClient);
            }
            document.close();
        } catch (DocumentException e) {
            throw new IllegalStateException("The ledger statement PDF could not be written", e);
        }
        return out.toByteArray();
    }

    private static void addHeading(Document document, Fonts fonts, String clientName, String companyName,
                                   Figures figures, LocalDate generatedOn) throws DocumentException {
        document.add(paragraph("Ledger Statement", fonts.bold(20, BRAND), 0, 4));
        document.add(paragraph(clientName, fonts.bold(13, Color.BLACK), 0, 2));
        if (companyName != null) {
            document.add(paragraph(companyName, fonts.regular(12, Color.BLACK), 0, 2));
        }
        document.add(paragraph("Financial year " + figures.financialYear() + " (" + DATE.format(figures.fromDate())
                + " to " + DATE.format(figures.toDate()) + ")  |  Generated on " + DATE.format(generatedOn),
                fonts.regular(10, MUTED), 0, 14));
    }

    /**
     * The year's billed, any opening balance, received and pending, then the last payment when there
     * was one.
     */
    private static void addSummary(Document document, Fonts fonts, Figures figures, boolean namesPayer)
            throws DocumentException {
        document.add(paragraph("Summary", fonts.bold(14, BRAND), 0, 6));
        PdfPTable table = new PdfPTable(new float[] {3, 2});
        table.setWidthPercentage(50);
        table.setHorizontalAlignment(Element.ALIGN_LEFT);
        addFigure(table, fonts, "Total billed", fonts.money(figures.totalBilled()), false);
        if (figures.openingBalance().signum() != 0) {
            addFigure(table, fonts, "Opening balance", pendingCell(fonts, figures.openingBalance()), false);
        }
        addFigure(table, fonts, "Payments received", fonts.money(figures.totalPaymentReceived()), false);
        addFigure(table, fonts, pendingLabel(figures.pending()), fonts.money(figures.pending().abs()), true);
        document.add(table);

        LastPayment lastPayment = figures.lastPayment();
        if (lastPayment != null) {
            String payer = namesPayer && lastPayment.getCompanyName() != null
                    ? " from " + lastPayment.getCompanyName() : "";
            document.add(paragraph("Last payment received: " + fonts.money(lastPayment.getAmount()) + " on "
                    + DATE.format(lastPayment.getDate()) + payer + ".", fonts.regular(10, MUTED), 8, 0));
        }
    }

    private static void addFigure(PdfPTable table, Fonts fonts, String label, String amount, boolean bottomLine) {
        Font font = bottomLine ? fonts.bold(11, Color.BLACK) : fonts.regular(11, Color.BLACK);
        PdfPCell labelCell = cell(label, font, Element.ALIGN_LEFT);
        PdfPCell amountCell = cell(amount, font, Element.ALIGN_RIGHT);
        if (bottomLine) {
            for (PdfPCell cell : List.of(labelCell, amountCell)) {
                cell.setBorder(Rectangle.TOP);
                cell.setBorderColor(BRAND);
                cell.setBorderWidth(1.5f);
            }
        }
        table.addCell(labelCell);
        table.addCell(amountCell);
    }

    /**
     * Each company's billed, received and pending for the year, in a client's summary, with an
     * opening balance column when any company has an opening balance in the year.
     */
    private static void addBreakdown(Document document, Fonts fonts, List<Section> sections)
            throws DocumentException {
        if (sections.isEmpty()) {
            return;
        }
        boolean withOpening = sections.stream().anyMatch(section -> section.openingInYear().signum() != 0);
        document.add(paragraph("By company", fonts.bold(14, BRAND), 18, 6));
        PdfPTable table = new PdfPTable(withOpening ? new float[] {4, 2, 2, 2, 2} : new float[] {4, 2, 2, 2});
        table.setWidthPercentage(withOpening ? 90 : 75);
        table.setHorizontalAlignment(Element.ALIGN_LEFT);
        table.setHeaderRows(1);
        List<String> headings = withOpening
                ? List.of("Company", "Billed", "Opening balance", "Received", "Pending")
                : List.of("Company", "Billed", "Received", "Pending");
        addHeadings(table, fonts, headings, 1, headings.size() - 1);
        Font font = fonts.regular(10, Color.BLACK);
        for (Section section : sections) {
            table.addCell(cell(section.companyName(), font, Element.ALIGN_LEFT));
            table.addCell(cell(fonts.money(section.billed()), font, Element.ALIGN_RIGHT));
            if (withOpening) {
                table.addCell(cell(pendingCell(fonts, section.openingInYear()), font, Element.ALIGN_RIGHT));
            }
            table.addCell(cell(fonts.money(section.received()), font, Element.ALIGN_RIGHT));
            table.addCell(cell(pendingCell(fonts, section.pending()), font, Element.ALIGN_RIGHT));
        }
        document.add(table);
    }

    /**
     * @param titled whether to head the section with the company's name and figures, as a client's
     *               statement does; a single company's statement has already named it
     */
    private static void addSection(Document document, Fonts fonts, Section section, boolean titled)
            throws DocumentException {
        if (titled) {
            document.add(paragraph(section.companyName(), fonts.bold(14, BRAND), 0, 4));
            String opening = section.openingInYear().signum() == 0 ? ""
                    : "  |  Opening balance " + pendingCell(fonts, section.openingInYear());
            document.add(paragraph("Billed " + fonts.money(section.billed()) + opening + "  |  Received "
                    + fonts.money(section.received()) + "  |  Pending " + pendingCell(fonts, section.pending()),
                    fonts.regular(10, MUTED), 0, 10));
        } else {
            document.add(paragraph("Details", fonts.bold(14, BRAND), 18, 6));
        }
        addOpeningBalance(document, fonts, section.openingBalance());
        if (section.entries().isEmpty()) {
            document.add(paragraph("No charges or payments in this financial year.", fonts.regular(10, MUTED), 0,
                    0));
            return;
        }
        addEntries(document, fonts, section);
    }

    /**
     * The company's opening balance and the date it stood at, whatever the year it falls in; None when
     * it has none.
     */
    private static void addOpeningBalance(Document document, Fonts fonts, OpeningBalance opening)
            throws DocumentException {
        PdfPTable table = new PdfPTable(new float[] {3, 2});
        table.setWidthPercentage(40);
        table.setHorizontalAlignment(Element.ALIGN_LEFT);
        table.setSpacingAfter(10);
        addFigure(table, fonts, "Opening balance", opening == null ? "None" : pendingCell(fonts, opening.amount()),
                false);
        if (opening != null) {
            addFigure(table, fonts, "Opening balance date", DATE.format(opening.date()), false);
        }
        document.add(table);
    }

    /** Every entry with its running balance, then a totals row. */
    private static void addEntries(Document document, Fonts fonts, Section section) throws DocumentException {
        PdfPTable table = new PdfPTable(ENTRY_WIDTHS);
        table.setWidthPercentage(100);
        // The headings repeat at the top of every page the table runs onto.
        table.setHeaderRows(1);
        addHeadings(table, fonts, ENTRY_COLUMNS, FIRST_AMOUNT_COLUMN, FIRST_AMOUNT_COLUMN + 2);

        Font font = fonts.regular(9, Color.BLACK);
        // Charges and opening balances owed are stored positive, payments and credits negative, so the
        // running sum is what is still owed, and the sign says which column an amount goes in.
        BigDecimal balance = ZERO;
        BigDecimal fees = ZERO;
        BigDecimal paid = ZERO;
        for (LedgerEntry entry : section.entries()) {
            boolean payment = LedgerWorkbook.isCredit(entry);
            balance = balance.add(entry.getTotalAmount());
            if (payment) {
                paid = paid.add(entry.getTotalAmount().negate());
            } else {
                fees = fees.add(entry.getTotalAmount());
            }
            table.addCell(cell(DATE.format(entry.getDueDate()), font, Element.ALIGN_LEFT));
            table.addCell(cell(LedgerWorkbook.label(entry.getEntryType()), font, Element.ALIGN_LEFT));
            table.addCell(cell(entry.getServiceName(), font, Element.ALIGN_LEFT));
            table.addCell(cell(LedgerWorkbook.label(entry.getServiceType()), font, Element.ALIGN_LEFT));
            table.addCell(cell(payment ? null : fonts.money(entry.getTotalAmount()), font, Element.ALIGN_RIGHT));
            table.addCell(cell(payment ? fonts.money(entry.getTotalAmount().negate()) : null, font,
                    Element.ALIGN_RIGHT));
            table.addCell(cell(fonts.money(balance), font, Element.ALIGN_RIGHT));
            table.addCell(cell(LedgerWorkbook.paymentMethod(entry.getPaymentMethod()), font, Element.ALIGN_LEFT));
            table.addCell(cell(entry.getPaymentReference(), font, Element.ALIGN_LEFT));
        }

        Font bold = fonts.bold(9, Color.BLACK);
        PdfPCell label = cell("Total", bold, Element.ALIGN_LEFT);
        label.setColspan(FIRST_AMOUNT_COLUMN);
        table.addCell(shaded(label));
        table.addCell(shaded(cell(fonts.money(fees), bold, Element.ALIGN_RIGHT)));
        table.addCell(shaded(cell(fonts.money(paid), bold, Element.ALIGN_RIGHT)));
        table.addCell(shaded(cell(fonts.money(balance), bold, Element.ALIGN_RIGHT)));
        PdfPCell rest = cell(null, bold, Element.ALIGN_LEFT);
        rest.setColspan(ENTRY_COLUMNS.size() - FIRST_AMOUNT_COLUMN - 3);
        table.addCell(shaded(rest));
        document.add(table);
    }

    /**
     * @param firstAmountColumn the first heading over amounts
     * @param lastAmountColumn  the last heading over amounts; those between are right-aligned too
     */
    private static void addHeadings(PdfPTable table, Fonts fonts, List<String> headings, int firstAmountColumn,
                                    int lastAmountColumn) {
        Font font = fonts.bold(9, Color.WHITE);
        for (int i = 0; i < headings.size(); i++) {
            boolean amount = i >= firstAmountColumn && i <= lastAmountColumn;
            PdfPCell cell = cell(headings.get(i), font, amount ? Element.ALIGN_RIGHT : Element.ALIGN_LEFT);
            cell.setBackgroundColor(BRAND);
            cell.setBorder(Rectangle.NO_BORDER);
            table.addCell(cell);
        }
    }

    private static PdfPCell cell(String text, Font font, int alignment) {
        PdfPCell cell = new PdfPCell(new Phrase(text == null ? "" : text, font));
        cell.setHorizontalAlignment(alignment);
        cell.setPadding(5);
        cell.setBorder(Rectangle.BOTTOM);
        cell.setBorderColor(RULE);
        return cell;
    }

    private static PdfPCell shaded(PdfPCell cell) {
        cell.setBackgroundColor(SHADE);
        return cell;
    }

    private static Paragraph paragraph(String text, Font font, float spacingBefore, float spacingAfter) {
        Paragraph paragraph = new Paragraph(text, font);
        paragraph.setSpacingBefore(spacingBefore);
        paragraph.setSpacingAfter(spacingAfter);
        return paragraph;
    }

    /** What the pending amount means for the client: owed, nothing, or paid ahead. */
    private static String pendingLabel(BigDecimal pending) {
        return switch (pending.signum()) {
            case 1 -> "Amount pending";
            case -1 -> "Amount in credit";
            default -> "Amount pending (fully settled)";
        };
    }

    /** A pending amount in a table, marking a credit rather than showing it negative. */
    private static String pendingCell(Fonts fonts, BigDecimal pending) {
        return pending.signum() < 0 ? fonts.money(pending.abs()) + " in credit" : fonts.money(pending);
    }

    /**
     * One company's part of a statement: its opening balance, its entries of the year, and what they
     * add up to.
     *
     * @param openingBalance the company's opening balance whatever its year, or null when it has none
     */
    private record Section(String companyName, OpeningBalance openingBalance, List<LedgerEntry> entries) {

        BigDecimal billed() {
            return total(LedgerEntryType.SERVICE_CHARGE);
        }

        /** The opening balance dated in the year: positive when owed, negative when in credit; else zero. */
        BigDecimal openingInYear() {
            return total(LedgerEntryType.OPENING_BALANCE);
        }

        /** Payments are stored negative; this is what was received, as a positive amount. */
        BigDecimal received() {
            return total(LedgerEntryType.PAYMENT).negate();
        }

        BigDecimal pending() {
            return billed().add(openingInYear()).subtract(received());
        }

        private BigDecimal total(LedgerEntryType type) {
            return entries.stream()
                    .filter(entry -> entry.getEntryType() == type)
                    .map(LedgerEntry::getTotalAmount)
                    .reduce(ZERO, BigDecimal::add);
        }
    }

    /** The figures a statement's summary shows, whichever summary they come from. */
    private record Figures(String financialYear, LocalDate fromDate, LocalDate toDate, BigDecimal totalBilled,
                           BigDecimal openingBalance, BigDecimal totalPaymentReceived, BigDecimal pending,
                           LastPayment lastPayment) {
    }

    /** Summaries always carry an opening balance; zero stands in for one built without it. */
    private static BigDecimal orZero(BigDecimal amount) {
        return amount == null ? ZERO : amount;
    }

    /**
     * The firm's logo and name at the top of every page, above a rule: the logo on the left, the name
     * beside it. Either is left out when missing.
     */
    private static final class Header extends PdfPageEventHelper {

        /** Height of the header, which the logo is scaled to. */
        static final float HEIGHT = 40;

        /** Space between the rule under the header and the content. */
        static final float GAP = 14;

        /** Widest the logo is drawn. */
        private static final float LOGO_MAX_WIDTH = 120;

        private static final float LOGO_TO_NAME = 10;

        private final Image logo;

        private final Phrase firmName;

        private Header(Image logo, Phrase firmName) {
            this.logo = logo;
            this.firmName = firmName;
        }

        /** The header of the firm's logo and name; null when there is neither. */
        static Header of(Fonts fonts, String firmName) {
            Image logo = logo();
            boolean named = firmName != null && !firmName.isBlank();
            if (logo == null && !named) {
                return null;
            }
            return new Header(logo, named ? new Phrase(firmName.strip(), fonts.bold(16, BRAND)) : null);
        }

        /** The logo scaled to the header, or null when it is missing or unreadable. */
        private static Image logo() {
            byte[] png = FirmLogo.png().orElse(null);
            if (png == null) {
                return null;
            }
            try {
                Image logo = Image.getInstance(png);
                logo.scaleToFit(LOGO_MAX_WIDTH, HEIGHT);
                return logo;
            } catch (IOException | DocumentException e) {
                log.error("Logo {} could not be read as an image; writing the statement without it",
                        FirmLogo.RESOURCE, e);
                return null;
            }
        }

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            PdfContentByte canvas = writer.getDirectContent();
            // The header sits in the top margin: its top at the page margin, the rule under it.
            float top = document.getPageSize().getTop() - 36;
            float bottom = top - HEIGHT;
            float x = document.left();
            if (logo != null) {
                logo.setAbsolutePosition(x, bottom + (HEIGHT - logo.getScaledHeight()) / 2);
                try {
                    canvas.addImage(logo);
                } catch (DocumentException e) {
                    throw new IllegalStateException("The logo could not be added to the ledger statement", e);
                }
                x += logo.getScaledWidth() + LOGO_TO_NAME;
            }
            if (firmName != null) {
                // Roughly centred on the header's height: the baseline sits below the middle by a third of
                // the text's size.
                float baseline = bottom + HEIGHT / 2 - firmName.getFont().getSize() / 3;
                ColumnText.showTextAligned(canvas, Element.ALIGN_LEFT, firmName, x, baseline, 0);
            }
            float rule = bottom - 4;
            canvas.saveState();
            canvas.setColorStroke(RULE);
            canvas.setLineWidth(1);
            canvas.moveTo(document.left(), rule);
            canvas.lineTo(document.right(), rule);
            canvas.stroke();
            canvas.restoreState();
        }
    }

    /** Names the statement and numbers the page at the foot of every page. */
    private static final class Footer extends PdfPageEventHelper {

        private final Font font;

        private final String title;

        Footer(Fonts fonts, String title) {
            this.font = fonts.regular(8, MUTED);
            this.title = title;
        }

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            float y = document.bottom() - 20;
            ColumnText.showTextAligned(writer.getDirectContent(), Element.ALIGN_LEFT, new Phrase(title, font),
                    document.left(), y, 0);
            ColumnText.showTextAligned(writer.getDirectContent(), Element.ALIGN_RIGHT,
                    new Phrase("Page " + writer.getPageNumber(), font), document.right(), y, 0);
        }
    }

    /**
     * The fonts a statement is written in, and how its amounts are written in them.
     *
     * @param currency what an amount starts with: the rupee sign when the fonts have it, else "Rs. "
     */
    private record Fonts(BaseFont regular, BaseFont bold, String currency) {

        static Fonts load() {
            try {
                BaseFont regular = embedded(REGULAR_FONT);
                BaseFont bold = embedded(BOLD_FONT);
                if (regular != null && bold != null) {
                    return new Fonts(regular, bold, "₹");
                }
                log.warn("Fonts {} and {} are not on the classpath; writing the ledger statement in Helvetica, "
                        + "with amounts in Rs. as it has no rupee sign", REGULAR_FONT, BOLD_FONT);
                return new Fonts(BaseFont.createFont(BaseFont.HELVETICA, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED),
                        BaseFont.createFont(BaseFont.HELVETICA_BOLD, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED),
                        "Rs. ");
            } catch (IOException e) {
                throw new UncheckedIOException("The ledger statement fonts could not be read", e);
            } catch (DocumentException e) {
                throw new IllegalStateException("The ledger statement fonts could not be loaded", e);
            }
        }

        /** A TrueType font from the classpath, embedded so the PDF shows it anywhere; null when absent. */
        private static BaseFont embedded(String path) throws IOException, DocumentException {
            try (InputStream in = LedgerStatementPdf.class.getClassLoader().getResourceAsStream(path)) {
                if (in == null) {
                    return null;
                }
                // Cached by name, so the font is parsed once rather than for every statement.
                return BaseFont.createFont(path, BaseFont.IDENTITY_H, BaseFont.EMBEDDED, true, in.readAllBytes(),
                        null);
            }
        }

        Font regular(float size, Color colour) {
            return new Font(regular, size, Font.NORMAL, colour);
        }

        Font bold(float size, Color colour) {
            return new Font(bold, size, Font.NORMAL, colour);
        }

        String money(BigDecimal amount) {
            return rupees(amount, currency);
        }
    }

    /**
     * An amount with Indian digit grouping, such as {@code ₹1,23,456.00}: the last three digits, then
     * pairs. {@link java.text.DecimalFormat} cannot group unevenly, so this is done here.
     */
    static String rupees(BigDecimal amount, String currency) {
        String plain = amount.abs().setScale(2, RoundingMode.HALF_UP).toPlainString();
        int point = plain.indexOf('.');
        String whole = plain.substring(0, point);

        StringBuilder grouped = new StringBuilder();
        int end = whole.length();
        int start = Math.max(0, end - 3);
        grouped.insert(0, whole, start, end);
        while (start > 0) {
            end = start;
            start = Math.max(0, end - 2);
            grouped.insert(0, ',').insert(0, whole, start, end);
        }
        return (amount.signum() < 0 ? "-" : "") + currency + grouped + plain.substring(point);
    }
}
