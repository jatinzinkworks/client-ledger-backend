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
     * @param received    what it paid in the year
     * @param pending     billed less received; negative when in credit
     */
    record CompanyLine(String companyName, BigDecimal billed, BigDecimal received, BigDecimal pending) {
    }

    /**
     * What both kinds of email say, worked out from either kind of summary.
     *
     * @param topic       what the subject line says the summary is for
     * @param accountHtml the account the intro speaks of, already HTML-escaped where needed
     * @param accountText the same in plain text
     * @param companies   the per-company breakdown; empty for a single company's summary
     */
    private record Content(String topic, String accountHtml, String accountText, String financialYear,
                           LocalDate fromDate, LocalDate toDate, BigDecimal billed, BigDecimal discount,
                           BigDecimal received, BigDecimal pending, LastPayment lastPayment,
                           List<CompanyLine> companies) {
    }

    /**
     * Writes the email for one company's summary.
     *
     * @param summary        the company's summary for the year
     * @param clientName     the name to greet the client by
     * @param firm           the firm's details for the signature and footer, or null when none are saved
     * @param asOf           the day the figures are as of
     * @param attachmentName the name of the attached Excel ledger
     * @return the email
     */
    static LedgerSummaryEmail forCompany(CompanyLedgerSummaryResponse summary, String clientName, FirmDetails firm,
                                         LocalDate asOf, String attachmentName) {
        return of(new Content(summary.getCompanyName(),
                        "the account of <strong>" + escape(summary.getCompanyName()) + "</strong>",
                        "the account of " + summary.getCompanyName(),
                        summary.getFinancialYear(), summary.getFromDate(), summary.getToDate(),
                        summary.getTotalBilled(), summary.getTotalDiscount(), summary.getTotalPaymentReceived(),
                        summary.getPending(), summary.getLastPayment(), List.of()),
                clientName, firm, asOf, attachmentName);
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
     * @param attachmentName the name of the attached Excel ledger
     * @return the email
     */
    static LedgerSummaryEmail forClient(ClientLedgerSummaryResponse summary, List<CompanyLine> companies,
                                        String clientName, FirmDetails firm, LocalDate asOf,
                                        String attachmentName) {
        String account = companies.size() <= 1 ? "your account"
                : "your account across all " + companies.size() + " of your companies";
        return of(new Content("all of your companies", account, account,
                        summary.getFinancialYear(), summary.getFromDate(), summary.getToDate(),
                        summary.getTotalBilled(), summary.getTotalDiscount(), summary.getTotalPaymentReceived(),
                        summary.getPending(), summary.getLastPayment(), companies),
                clientName, firm, asOf, attachmentName);
    }

    private static LedgerSummaryEmail of(Content content, String clientName, FirmDetails firm, LocalDate asOf,
                                         String attachmentName) {
        String firmName = firm == null ? null : firm.getFirmName();
        String subject = (firmName == null ? "" : firmName + ": ") + "Your ledger summary for " + content.topic()
                + ", FY " + content.financialYear();
        return new LedgerSummaryEmail(subject, html(content, clientName, firm, asOf, attachmentName),
                text(content, clientName, firm, asOf, attachmentName));
    }

    private static String html(Content content, String clientName, FirmDetails firm, LocalDate asOf,
                               String attachmentName) {
        StringBuilder rows = new StringBuilder();
        rows.append(row("Total billed", rupees(content.billed()), false));
        rows.append(row("Discount given", rupees(content.discount()), false));
        rows.append(row("Payments received", rupees(content.received()), false));
        rows.append(row(pendingLabel(content.pending()), rupees(content.pending().abs()), true));

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
                <div style="font-size:14px;opacity:0.85;margin-top:4px;">Ledger summary &middot; FY %s</div>
                </td></tr>
                <tr><td style="padding:32px;">
                <p style="margin:0 0 16px;font-size:15px;">Dear %s,</p>
                <p style="margin:0 0 24px;font-size:15px;line-height:1.5;">Here is where %s stands for the \
                financial year %s (%s to %s), as of %s.</p>
                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" \
                style="border-collapse:collapse;font-size:15px;">
                %s
                </table>
                %s
                %s
                <p style="margin:24px 0 0;font-size:15px;line-height:1.5;">The full ledger for the year is \
                attached as <em>%s</em>. If anything does not look right, please reply to this email.</p>
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
                escape("Ledger summary for " + content.topic()),
                BRAND_COLOUR,
                escape(firmName.isEmpty() ? "Ledger summary" : firmName),
                escape(content.financialYear()),
                escape(clientName),
                content.accountHtml(),
                escape(content.financialYear()),
                DATE.format(content.fromDate()),
                DATE.format(content.toDate()),
                DATE.format(asOf),
                rows,
                lastPayment,
                breakdownHtml(content.companies()),
                escape(attachmentName),
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

    /** The table of each company's figures in a client-wide summary; nothing for a single company. */
    private static String breakdownHtml(List<CompanyLine> companies) {
        if (companies.isEmpty()) {
            return "";
        }
        String head = "padding:8px 16px;font-size:13px;color:#555555;text-align:%s;border-bottom:1px solid "
                + BRAND_COLOUR + ";";
        StringBuilder table = new StringBuilder()
                .append("<p style=\"margin:28px 0 8px;font-size:15px;font-weight:bold;\">By company</p>")
                .append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" ")
                .append("style=\"border-collapse:collapse;font-size:14px;\"><tr>")
                .append("<th style=\"").append(head.formatted("left")).append("\">Company</th>")
                .append("<th style=\"").append(head.formatted("right")).append("\">Billed</th>")
                .append("<th style=\"").append(head.formatted("right")).append("\">Received</th>")
                .append("<th style=\"").append(head.formatted("right")).append("\">Pending</th></tr>");
        for (CompanyLine company : companies) {
            table.append("<tr><td style=\"").append(CELL).append("\">").append(escape(company.companyName()))
                    .append("</td>")
                    .append(amountCell(rupees(company.billed())))
                    .append(amountCell(rupees(company.received())))
                    .append(amountCell(pendingCell(company.pending())))
                    .append("</tr>");
        }
        return table.append("</table>").toString();
    }

    private static String amountCell(String amount) {
        return "<td align=\"right\" style=\"" + CELL + "\">" + escape(amount) + "</td>";
    }

    private static String text(Content content, String clientName, FirmDetails firm, LocalDate asOf,
                               String attachmentName) {
        String firmName = firm == null || firm.getFirmName() == null ? "Accounts team" : firm.getFirmName();
        StringBuilder text = new StringBuilder()
                .append("Dear ").append(clientName).append(",\n\n")
                .append("Here is where ").append(content.accountText())
                .append(" stands for the financial year ").append(content.financialYear())
                .append(" (").append(DATE.format(content.fromDate())).append(" to ")
                .append(DATE.format(content.toDate())).append("), as of ").append(DATE.format(asOf))
                .append(".\n\n")
                .append("Total billed:       ").append(rupees(content.billed())).append('\n')
                .append("Discount given:     ").append(rupees(content.discount())).append('\n')
                .append("Payments received:  ").append(rupees(content.received())).append('\n')
                .append(pendingLabel(content.pending())).append(": ")
                .append(rupees(content.pending().abs())).append('\n');
        if (content.lastPayment() != null) {
            text.append('\n').append(lastPaymentLine(content.lastPayment())).append('\n');
        }
        if (!content.companies().isEmpty()) {
            text.append("\nBy company:\n");
            for (CompanyLine company : content.companies()) {
                text.append("- ").append(company.companyName()).append(": billed ").append(rupees(company.billed()))
                        .append(", received ").append(rupees(company.received()))
                        .append(", pending ").append(pendingCell(company.pending())).append('\n');
            }
        }
        text.append("\nThe full ledger for the year is attached as ").append(attachmentName)
                .append(". If anything does not look right, please reply to this email.\n\n")
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
