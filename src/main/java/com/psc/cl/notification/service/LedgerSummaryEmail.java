package com.psc.cl.notification.service;

import com.psc.cl.globalsettings.model.FirmDetails;
import com.psc.cl.ledger.dto.ClientLedgerSummaryResponse;
import com.psc.cl.ledger.dto.CompanyLedgerSummaryResponse;
import com.psc.cl.ledger.dto.LastPayment;
import org.springframework.web.util.HtmlUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The email telling a client where their account stands for a financial year, for one of their
 * companies or across all of them: its subject, an HTML body, and a plain-text body for mail
 * readers that do not show HTML.
 *
 * <p>Every value taken from the data is HTML-escaped before it goes into the HTML body. Styles are
 * inline and the layout is a table, as most mail readers ignore style sheets.
 *
 * @param subject the subject line
 * @param html    the HTML body
 * @param text    the plain-text body
 */
record LedgerSummaryEmail(String subject, String html, String text) {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

    private static final String RUPEE = "₹";

    private static final String BRAND_COLOUR = "#1f3a5f";

    private static final String CELL = "padding:10px 16px;border-top:1px solid #e3e6ea;";

    /**
     * One company's line in the breakdown of a client-wide summary.
     *
     * @param companyName the company
     * @param billed      what it was billed in the year
     * @param opening     the opening balance it was taken on with, when dated in the year: positive when
     *                    owed, negative when in credit; zero otherwise
     * @param received    what it paid in the year
     * @param pending     billed plus opening balance less received; negative when in credit
     */
    record CompanyLine(String companyName, BigDecimal billed, BigDecimal opening, BigDecimal received,
                       BigDecimal pending) {
    }

    /**
     * What both kinds of email say, worked out from either kind of summary.
     *
     * @param topic       what the subject line says the summary is for
     * @param accountHtml the account the intro speaks of, already HTML-escaped where needed
     * @param accountText the same in plain text
     * @param opening      opening balances dated in the year: positive when owed, negative when in credit
     * @param pending      billed plus opening balance less received in the year
     * @param totalPending billed plus opening balances less received across every year; the same as
     *                     pending for a summary
     * @param companies    the per-company breakdown; empty for a single company's summary
     */
    private record Content(String topic, String accountHtml, String accountText, String financialYear,
                           LocalDate fromDate, LocalDate toDate, BigDecimal billed, BigDecimal opening,
                           BigDecimal received, BigDecimal pending, BigDecimal totalPending,
                           LastPayment lastPayment, List<CompanyLine> companies) {

        /** What was carried into the year from earlier ones; zero for a summary. */
        BigDecimal earlierBalance() {
            return totalPending.subtract(pending);
        }
    }

    /**
     * Writes the email for one company's summary.
     *
     * @param summary        the company's summary for the year
     * @param clientName     the name to greet the client by
     * @param firm           the firm's details for the signature and footer, or null when none are saved
     * @param asOf           the day the figures are as of
     * @param attachmentName the name of the attached PDF ledger statement
     * @return the email
     */
    static LedgerSummaryEmail forCompany(CompanyLedgerSummaryResponse summary, String clientName, FirmDetails firm,
                                         LocalDate asOf, String attachmentName) {
        return of(new Content(summary.getCompanyName(),
                        "the account of <strong>" + escape(summary.getCompanyName()) + "</strong>",
                        "the account of " + summary.getCompanyName(),
                        summary.getFinancialYear(), summary.getFromDate(), summary.getToDate(),
                        summary.getTotalBilled(), orZero(summary.getOpeningBalance()),
                        summary.getTotalPaymentReceived(), summary.getPending(), summary.getPending(),
                        summary.getLastPayment(), List.of()),
                Purpose.SUMMARY, clientName, firm, asOf, attachmentName);
    }

    /**
     * Writes the email for a client's summary across all of their companies, with a line for each.
     *
     * @param summary        the client's summary for the year
     * @param companies      each of the client's companies with its figures for the year, in the order
     *                       to list them
     * @param clientName     the name to greet the client by
     * @param firm           the firm's details for the signature and footer, or null when none are saved
     * @param asOf           the day the figures are as of
     * @param attachmentName the name of the attached PDF ledger statement
     * @return the email
     */
    static LedgerSummaryEmail forClient(ClientLedgerSummaryResponse summary, List<CompanyLine> companies,
                                        String clientName, FirmDetails firm, LocalDate asOf,
                                        String attachmentName) {
        return of(clientContent(summary, summary.getPending(), companies), Purpose.SUMMARY, clientName, firm, asOf,
                attachmentName);
    }

    /**
     * Writes a payment reminder for a client owing across their companies: the same figures and
     * per-company lines for the year as {@link #forClient}, framed as a request to pay everything
     * pending across every year. When earlier years left a balance, the figures add it to the year's
     * so they come to the amount asked for.
     *
     * @param summary        the client's summary for the year
     * @param totalPending   what the client owes across every year: the amount asked for
     * @param companies      each of the client's companies with its figures for the year, in the order
     *                       to list them
     * @param clientName     the name to greet the client by
     * @param firm           the firm's details for the signature and footer, or null when none are saved
     * @param asOf           the day the figures are as of
     * @param attachmentName the name of the attached PDF ledger statement
     * @return the email
     */
    static LedgerSummaryEmail paymentReminderForClient(ClientLedgerSummaryResponse summary, BigDecimal totalPending,
                                                       List<CompanyLine> companies, String clientName,
                                                       FirmDetails firm, LocalDate asOf, String attachmentName) {
        return of(clientContent(summary, totalPending, companies), Purpose.PAYMENT_REMINDER, clientName, firm,
                asOf, attachmentName);
    }

    private static Content clientContent(ClientLedgerSummaryResponse summary, BigDecimal totalPending,
                                         List<CompanyLine> companies) {
        String account = companies.size() <= 1 ? "your account"
                : "your account across all " + companies.size() + " of your companies";
        return new Content("all of your companies", account, account,
                summary.getFinancialYear(), summary.getFromDate(), summary.getToDate(),
                summary.getTotalBilled(), orZero(summary.getOpeningBalance()), summary.getTotalPaymentReceived(),
                summary.getPending(), totalPending, summary.getLastPayment(), companies);
    }

    /** Summaries always carry an opening balance; zero stands in for one built without it. */
    private static BigDecimal orZero(BigDecimal amount) {
        return amount == null ? BigDecimal.ZERO : amount;
    }

    /** What the email is for, which decides its subject, heading, opening and closing. */
    private enum Purpose {
        SUMMARY,
        PAYMENT_REMINDER
    }

    private static LedgerSummaryEmail of(Content content, Purpose purpose, String clientName, FirmDetails firm,
                                         LocalDate asOf, String attachmentName) {
        String firmName = firm == null ? null : firm.getFirmName();
        String subject = (firmName == null ? "" : firmName + ": ") + switch (purpose) {
            case SUMMARY -> "Your ledger summary for " + content.topic() + ", FY " + content.financialYear();
            case PAYMENT_REMINDER -> "Payment reminder: " + rupees(content.totalPending()) + " pending";
        };
        return new LedgerSummaryEmail(subject, html(content, purpose, clientName, firm, asOf, attachmentName),
                text(content, purpose, clientName, firm, asOf, attachmentName));
    }

    /** The heading under the firm's name, the page title, and how a summary or reminder opens and closes. */
    private static String heading(Purpose purpose) {
        return purpose == Purpose.SUMMARY ? "Ledger summary" : "Payment reminder";
    }

    private static String introHtml(Content content, Purpose purpose, LocalDate asOf) {
        String year = "the financial year " + escape(content.financialYear()) + " ("
                + DATE.format(content.fromDate()) + " to " + DATE.format(content.toDate()) + ")";
        return purpose == Purpose.SUMMARY
                ? "Here is where " + content.accountHtml() + " stands for " + year + ", as of "
                        + DATE.format(asOf) + "."
                : "This is a friendly reminder that <strong>" + escape(rupees(content.totalPending()))
                        + "</strong> is pending on " + content.accountHtml() + " as of " + DATE.format(asOf)
                        + ". The details for " + year + " are below.";
    }

    private static String introText(Content content, Purpose purpose, LocalDate asOf) {
        String year = "the financial year " + content.financialYear() + " (" + DATE.format(content.fromDate())
                + " to " + DATE.format(content.toDate()) + ")";
        return purpose == Purpose.SUMMARY
                ? "Here is where " + content.accountText() + " stands for " + year + ", as of "
                        + DATE.format(asOf) + "."
                : "This is a friendly reminder that " + rupees(content.totalPending()) + " is pending on "
                        + content.accountText() + " as of " + DATE.format(asOf) + ". The details for " + year
                        + " are below.";
    }

    /**
     * One line of the figures table.
     *
     * @param highlight whether it is the bottom line, the amount that stands
     */
    private record Figure(String label, String amount, boolean highlight) {
    }

    /**
     * The year's billed and received, then what is pending. No discount is shown: what was billed
     * already has it taken off, and the client is told what they were charged, not how. When earlier
     * years left a
     * balance, the year's own balance and that earlier balance come before the total pending, so the
     * lines add up to the bottom one.
     */
    private static List<Figure> figures(Content content) {
        List<Figure> figures = new ArrayList<>();
        figures.add(new Figure("Total billed", rupees(content.billed()), false));
        if (content.opening().signum() != 0) {
            figures.add(new Figure("Opening balance", pendingCell(content.opening()), false));
        }
        figures.add(new Figure("Payments received", rupees(content.received()), false));
        if (content.earlierBalance().signum() == 0) {
            figures.add(new Figure(pendingLabel(content.pending()), rupees(content.pending().abs()), true));
        } else {
            figures.add(new Figure("Balance for FY " + content.financialYear(), pendingCell(content.pending()),
                    false));
            figures.add(new Figure("Balance from earlier years", pendingCell(content.earlierBalance()), false));
            figures.add(new Figure("Total " + pendingLabel(content.totalPending()).toLowerCase(Locale.ENGLISH),
                    rupees(content.totalPending().abs()), true));
        }
        return figures;
    }

    /** The closing paragraph, with the attachment's name; HTML-escaped by the caller where needed. */
    private static String closing(Purpose purpose, String attachment) {
        return purpose == Purpose.SUMMARY
                ? "The full ledger for the year is attached as " + attachment + ". If anything does not look "
                        + "right, please reply to this email."
                : "Please arrange payment of the pending amount at your earliest convenience. The full ledger "
                        + "for the year is attached as " + attachment + ". If you have already paid, please "
                        + "reply with the payment details so we can update our records.";
    }

    private static String html(Content content, Purpose purpose, String clientName, FirmDetails firm,
                               LocalDate asOf, String attachmentName) {
        StringBuilder rows = new StringBuilder();
        for (Figure figure : figures(content)) {
            rows.append(row(figure.label(), figure.amount(), figure.highlight()));
        }

        String lastPayment = content.lastPayment() == null ? ""
                : "<p style=\"margin:16px 0 0;font-size:14px;color:#555555;\">"
                        + escape(lastPaymentLine(content.lastPayment())) + "</p>";
        String firmName = firm == null || firm.getFirmName() == null ? "" : firm.getFirmName();

        return """
                <!DOCTYPE html>
                <html lang="en">
                <head><meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1.0">\
                <title>%s</title></head>
                <body style="margin:0;padding:0;background-color:#f4f6f8;font-family:Arial,Helvetica,sans-serif;\
                color:#222222;">
                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" \
                style="background-color:#f4f6f8;padding:24px 0;">
                <tr><td align="center">
                <table role="presentation" width="600" cellpadding="0" cellspacing="0" \
                style="max-width:600px;width:100%%;background-color:#ffffff;border-radius:8px;overflow:hidden;">
                <tr><td style="background-color:%s;padding:24px 32px;color:#ffffff;">
                <div style="font-size:20px;font-weight:bold;">%s</div>
                <div style="font-size:14px;opacity:0.85;margin-top:4px;">%s &middot; FY %s</div>
                </td></tr>
                <tr><td style="padding:32px;">
                <p style="margin:0 0 16px;font-size:15px;">Dear %s,</p>
                <p style="margin:0 0 24px;font-size:15px;line-height:1.5;">%s</p>
                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" \
                style="border-collapse:collapse;font-size:15px;">
                %s
                </table>
                %s
                %s
                <p style="margin:24px 0 0;font-size:15px;line-height:1.5;">%s</p>
                <p style="margin:24px 0 0;font-size:15px;">Regards,<br>%s</p>
                </td></tr>
                <tr><td style="background-color:#f0f2f5;padding:16px 32px;font-size:12px;color:#777777;\
                line-height:1.6;">%s</td></tr>
                </table>
                </td></tr>
                </table>
                </body>
                </html>
                """.formatted(
                escape(heading(purpose) + " for " + content.topic()),
                BRAND_COLOUR,
                escape(firmName.isEmpty() ? heading(purpose) : firmName),
                heading(purpose),
                escape(content.financialYear()),
                escape(clientName),
                introHtml(content, purpose, asOf),
                rows,
                lastPayment,
                breakdownHtml(content.companies()),
                closing(purpose, "<em>" + escape(attachmentName) + "</em>"),
                escape(firmName.isEmpty() ? "Accounts team" : firmName),
                escape(String.join(" | ", footer(firm))));
    }

    private static String row(String label, String amount, boolean highlight) {
        String style = highlight
                ? "padding:12px 16px;border-top:2px solid " + BRAND_COLOUR + ";font-weight:bold;font-size:16px;"
                : CELL;
        return "<tr><td style=\"" + style + "\">" + escape(label) + "</td>"
                + "<td align=\"right\" style=\"" + style + "\">" + escape(amount) + "</td></tr>";
    }

    /**
     * The table of each company's figures in a client-wide summary, with an opening balance column
     * when any company has one; nothing for a single company.
     */
    private static String breakdownHtml(List<CompanyLine> companies) {
        if (companies.isEmpty()) {
            return "";
        }
        boolean withOpening = companies.stream().anyMatch(company -> company.opening().signum() != 0);
        String head = "padding:8px 16px;font-size:13px;color:#555555;text-align:%s;border-bottom:1px solid "
                + BRAND_COLOUR + ";";
        StringBuilder table = new StringBuilder()
                .append("<p style=\"margin:28px 0 8px;font-size:15px;font-weight:bold;\">By company</p>")
                .append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" ")
                .append("style=\"border-collapse:collapse;font-size:14px;\"><tr>")
                .append("<th style=\"").append(head.formatted("left")).append("\">Company</th>")
                .append("<th style=\"").append(head.formatted("right")).append("\">Billed</th>");
        if (withOpening) {
            table.append("<th style=\"").append(head.formatted("right")).append("\">Opening balance</th>");
        }
        table.append("<th style=\"").append(head.formatted("right")).append("\">Received</th>")
                .append("<th style=\"").append(head.formatted("right")).append("\">Pending</th></tr>");
        for (CompanyLine company : companies) {
            table.append("<tr><td style=\"").append(CELL).append("\">").append(escape(company.companyName()))
                    .append("</td>")
                    .append(amountCell(rupees(company.billed())));
            if (withOpening) {
                table.append(amountCell(pendingCell(company.opening())));
            }
            table.append(amountCell(rupees(company.received())))
                    .append(amountCell(pendingCell(company.pending())))
                    .append("</tr>");
        }
        return table.append("</table>").toString();
    }

    private static String amountCell(String amount) {
        return "<td align=\"right\" style=\"" + CELL + "\">" + escape(amount) + "</td>";
    }

    private static String text(Content content, Purpose purpose, String clientName, FirmDetails firm,
                               LocalDate asOf, String attachmentName) {
        String firmName = firm == null || firm.getFirmName() == null ? "Accounts team" : firm.getFirmName();
        StringBuilder text = new StringBuilder()
                .append("Dear ").append(clientName).append(",\n\n")
                .append(introText(content, purpose, asOf)).append("\n\n");
        for (Figure figure : figures(content)) {
            // Lines above the bottom one are padded so their amounts line up.
            String label = figure.label() + ":";
            text.append(label).append(figure.highlight() ? " " : " ".repeat(Math.max(1, 20 - label.length())))
                    .append(figure.amount()).append('\n');
        }
        if (content.lastPayment() != null) {
            text.append('\n').append(lastPaymentLine(content.lastPayment())).append('\n');
        }
        if (!content.companies().isEmpty()) {
            text.append("\nBy company:\n");
            for (CompanyLine company : content.companies()) {
                text.append("- ").append(company.companyName()).append(": billed ").append(rupees(company.billed()));
                if (company.opening().signum() != 0) {
                    text.append(", opening balance ").append(pendingCell(company.opening()));
                }
                text.append(", received ").append(rupees(company.received()))
                        .append(", pending ").append(pendingCell(company.pending())).append('\n');
            }
        }
        text.append('\n').append(closing(purpose, attachmentName)).append("\n\n")
                .append("Regards,\n").append(firmName).append('\n');
        List<String> footer = footer(firm);
        if (!footer.isEmpty()) {
            text.append("\n--\n").append(String.join("\n", footer)).append('\n');
        }
        return text.toString();
    }

    /** What the pending amount means for the client: owed, nothing, or paid ahead. */
    private static String pendingLabel(BigDecimal pending) {
        return switch (pending.signum()) {
            case 1 -> "Amount pending";
            case -1 -> "Amount in credit";
            default -> "Amount pending (fully settled)";
        };
    }

    /** A company's pending amount in the breakdown, marking a credit rather than showing it negative. */
    private static String pendingCell(BigDecimal pending) {
        return pending.signum() < 0 ? rupees(pending.abs()) + " in credit" : rupees(pending);
    }

    /** The last payment, naming the company that made it when the summary spans several. */
    private static String lastPaymentLine(LastPayment payment) {
        return "Last payment received: " + rupees(payment.getAmount()) + " on " + DATE.format(payment.getDate())
                + (payment.getCompanyName() == null ? "" : " from " + payment.getCompanyName()) + ".";
    }

    /** The firm's contact details that are saved, for the footer. */
    private static List<String> footer(FirmDetails firm) {
        List<String> lines = new ArrayList<>();
        if (firm == null) {
            return lines;
        }
        addIfPresent(lines, firm.getFirmName());
        addIfPresent(lines, firm.getAddress());
        addIfPresent(lines, firm.getPhone());
        addIfPresent(lines, firm.getEmail());
        if (firm.getGstin() != null && !firm.getGstin().isBlank()) {
            lines.add("GSTIN " + firm.getGstin());
        }
        return lines;
    }

    private static void addIfPresent(List<String> lines, String value) {
        if (value != null && !value.isBlank()) {
            lines.add(value);
        }
    }

    /**
     * An amount in rupees with Indian digit grouping, such as {@code ₹1,23,456.00}: the last three
     * digits, then pairs. {@link java.text.DecimalFormat} cannot group unevenly, so this is done here.
     */
    static String rupees(BigDecimal amount) {
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
        return (amount.signum() < 0 ? "-" : "") + RUPEE + grouped + plain.substring(point);
    }

    private static String escape(String value) {
        return HtmlUtils.htmlEscape(value == null ? "" : value);
    }
}
