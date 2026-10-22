package com.psc.cl.notification.service;

import com.psc.cl.client.exception.ClientNotFoundException;
import com.psc.cl.client.model.Client;
import com.psc.cl.client.model.ClientCompany;
import com.psc.cl.client.repository.ClientCompanyRepository;
import com.psc.cl.client.repository.ClientRepository;
import com.psc.cl.globalsettings.model.FirmDetails;
import com.psc.cl.globalsettings.repository.FirmDetailsRepository;
import com.psc.cl.ledger.dto.ClientLedgerSummaryResponse;
import com.psc.cl.ledger.dto.CompanyBalance;
import com.psc.cl.ledger.dto.CompanyLedgerSummaryResponse;
import com.psc.cl.ledger.dto.LedgerExport;
import com.psc.cl.ledger.dto.OpeningBalance;
import com.psc.cl.ledger.model.FinancialYear;
import com.psc.cl.ledger.service.FinancialYears;
import com.psc.cl.ledger.service.LedgerService;
import com.psc.cl.managercatalog.model.Manager;
import com.psc.cl.managercatalog.repository.ManagerRepository;
import com.psc.cl.notification.config.NotificationProperties;
import com.psc.cl.notification.dto.BulkLedgerSummaryEmailResponse;
import com.psc.cl.notification.dto.ClientEmailFailure;
import com.psc.cl.notification.dto.ClientEmailResult;
import com.psc.cl.notification.dto.ClientReminderResult;
import com.psc.cl.notification.dto.LedgerSummaryEmailResponse;
import com.psc.cl.notification.dto.PaymentReminderResponse;
import com.psc.cl.notification.exception.EmailDeliveryException;
import com.psc.cl.service.FirmLogo;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.data.domain.Sort;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Default {@link NotificationService}, sending through the SMTP server configured under
 * {@code spring.mail}.
 *
 * <p>Deliberately not transactional: the ledger is read through {@link LedgerService}, whose own
 * read-only transactions end before the email is sent, so no database connection is held while
 * waiting on the SMTP server. Email addresses are not logged.
 */
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationServiceImpl.class);

    private final LedgerService ledgerService;

    private final ClientRepository clientRepository;

    private final ClientCompanyRepository clientCompanyRepository;

    private final ManagerRepository managerRepository;

    private final FirmDetailsRepository firmDetailsRepository;

    private final JavaMailSender mailSender;

    private final NotificationProperties properties;

    private final Clock clock;

    @Override
    public LedgerSummaryEmailResponse sendLedgerSummary(UUID clientId, UUID companyId) {
        LocalDate today = LocalDate.now(clock);
        FinancialYear year = FinancialYears.containing(today);

        // Summarising first checks the client and company exist, so a bad path is a 404 before anything else.
        CompanyLedgerSummaryResponse summary = ledgerService.summarizeCompany(clientId, companyId, year);
        Client client = requireClient(clientId);
        List<String> cc = managerEmail(client);
        FirmDetails firm = firm();
        LedgerExport ledger = ledgerService.statementForCompany(clientId, companyId, year, firmName(firm));

        OpeningBalance opening = ledgerService.openingBalances(Set.of(companyId)).get(companyId);

        LedgerSummaryEmail email = LedgerSummaryEmail.forCompany(summary, opening, client.getFirstName(), firm,
                today, ledger.fileName());
        send(client.getEmail(), cc, firm, email, ledger);
        log.info("Emailed the FY {} ledger summary of company {} to client {}", year.label(), companyId, clientId);

        return response(client, companyId, year, cc, email, ledger);
    }

    @Override
    public LedgerSummaryEmailResponse sendClientLedgerSummary(UUID clientId) {
        FinancialYear year = FinancialYears.containing(LocalDate.now(clock));
        // Summarising first checks the client exists, so a bad path is a 404 before anything else.
        ClientLedgerSummaryResponse summary = ledgerService.summarizeClient(clientId, year);
        return sendClientEmail(clientId, summary, year, null);
    }

    @Override
    public PaymentReminderResponse sendPaymentReminders() {
        // Every email would fail the same way, so an unconfigured sender is reported once, up front.
        requireSender();
        FinancialYear year = FinancialYears.containing(LocalDate.now(clock));
        List<Client> clients = clientRepository.findAll(Sort.by("firstName", "lastName", "id"));
        // What each client owes across every year, in one query; clients without entries are left out.
        Map<UUID, BigDecimal> pendingByClient = clients.isEmpty() ? Map.of()
                : ledgerService.pendingByClient(clients.stream().map(Client::getId).toList());
        log.info("Sending payment reminders to the clients owing among {}", clients.size());

        List<ClientReminderResult> sent = new ArrayList<>();
        List<ClientEmailFailure> failures = new ArrayList<>();
        int skipped = 0;
        for (Client client : clients) {
            BigDecimal totalPending = pendingByClient.getOrDefault(client.getId(), BigDecimal.ZERO);
            if (totalPending.signum() <= 0) {
                // Settled or in credit across every year: nothing to remind them of.
                skipped++;
                continue;
            }
            String clientName = client.getFirstName() + " " + client.getLastName();
            try {
                ClientLedgerSummaryResponse summary = ledgerService.summarizeClient(client.getId(), year);
                sendClientEmail(client.getId(), summary, year, totalPending);
                sent.add(ClientReminderResult.builder()
                        .clientId(client.getId())
                        .clientName(clientName)
                        .pending(totalPending)
                        .build());
            } catch (RuntimeException e) {
                // Reported in the response and logged; the remaining clients are still reminded.
                log.error("Failed to send a payment reminder to client {}", client.getId(), e);
                failures.add(ClientEmailFailure.builder()
                        .clientId(client.getId())
                        .clientName(clientName)
                        .error(e.getMessage())
                        .build());
            }
        }
        log.info("Sent payment reminders to {} clients; {} had nothing pending; {} failed", sent.size(), skipped,
                failures.size());

        return PaymentReminderResponse.builder()
                .financialYear(year.label())
                .clientCount(clients.size())
                .sentCount(sent.size())
                .skippedCount(skipped)
                .sent(sent)
                .failures(failures)
                .build();
    }

    /**
     * Emails a client their figures across all of their companies for the year, with the year's ledger
     * attached: as a summary, or as a reminder to pay what they owe across every year.
     *
     * @param reminderPending what the client owes across every year, to send a payment reminder for; null
     *                        to send a summary
     */
    private LedgerSummaryEmailResponse sendClientEmail(UUID clientId, ClientLedgerSummaryResponse summary,
                                                       FinancialYear year, BigDecimal reminderPending) {
        boolean paymentReminder = reminderPending != null;
        LocalDate today = LocalDate.now(clock);
        Client client = requireClient(clientId);
        List<String> cc = managerEmail(client);
        FirmDetails firm = firm();
        List<LedgerSummaryEmail.CompanyLine> companies = companyLines(clientId, year);
        LedgerExport ledger = ledgerService.statementForClient(clientId, year, firmName(firm));

        LedgerSummaryEmail email = paymentReminder
                ? LedgerSummaryEmail.paymentReminderForClient(summary, reminderPending, companies,
                        client.getFirstName(), firm, today, ledger.fileName())
                : LedgerSummaryEmail.forClient(summary, companies, client.getFirstName(), firm, today,
                        ledger.fileName());
        send(client.getEmail(), cc, firm, email, ledger);
        log.info("Emailed the FY {} {} across {} companies to client {}", year.label(),
                paymentReminder ? "payment reminder" : "ledger summary", companies.size(), clientId);

        return response(client, null, year, cc, email, ledger);
    }

    @Override
    public BulkLedgerSummaryEmailResponse sendAllClientLedgerSummaries() {
        // Every email would fail the same way, so an unconfigured sender is reported once, up front.
        requireSender();
        FinancialYear year = FinancialYears.containing(LocalDate.now(clock));
        List<Client> clients = clientRepository.findAll(Sort.by("firstName", "lastName", "id"));
        log.info("Emailing the FY {} ledger summary to {} clients", year.label(), clients.size());

        List<ClientEmailResult> sent = new ArrayList<>();
        List<ClientEmailFailure> failures = new ArrayList<>();
        for (Client client : clients) {
            String clientName = client.getFirstName() + " " + client.getLastName();
            try {
                sendClientLedgerSummary(client.getId());
                sent.add(ClientEmailResult.builder().clientId(client.getId()).clientName(clientName).build());
            } catch (RuntimeException e) {
                // Reported in the response and logged; the remaining clients are still emailed.
                log.error("Failed to email the ledger summary to client {}", client.getId(), e);
                failures.add(ClientEmailFailure.builder()
                        .clientId(client.getId())
                        .clientName(clientName)
                        .error(e.getMessage())
                        .build());
            }
        }
        log.info("Emailed the FY {} ledger summary to {} of {} clients; {} failed", year.label(), sent.size(),
                clients.size(), failures.size());

        return BulkLedgerSummaryEmailResponse.builder()
                .financialYear(year.label())
                .clientCount(clients.size())
                .sentCount(sent.size())
                .sent(sent)
                .failures(failures)
                .build();
    }

    /**
     * Every company of the client by name, with its figures for the year, zero for those without
     * entries, and its opening balance whatever its year.
     */
    private List<LedgerSummaryEmail.CompanyLine> companyLines(UUID clientId, FinancialYear year) {
        Map<UUID, CompanyBalance> balances = ledgerService.companyBalances(clientId, year);
        List<ClientCompany> companies = clientCompanyRepository.findByClientIdOrderByCompanyNameAsc(clientId);
        Map<UUID, OpeningBalance> openings = ledgerService.openingBalances(
                companies.stream().map(ClientCompany::getId).toList());
        return companies.stream()
                .map(company -> {
                    CompanyBalance balance = balances.getOrDefault(company.getId(), CompanyBalance.ZERO);
                    return new LedgerSummaryEmail.CompanyLine(company.getCompanyName(), balance.totalBilled(),
                            balance.openingBalance(), balance.totalPaymentReceived(), balance.pending(),
                            openings.get(company.getId()));
                })
                .toList();
    }

    private Client requireClient(UUID clientId) {
        return clientRepository.findById(clientId).orElseThrow(() -> new ClientNotFoundException(clientId));
    }

    private FirmDetails firm() {
        return firmDetailsRepository.findFirstByOrderByCreatedAtAsc().orElse(null);
    }

    /** The firm's name, for the top of the statement's pages; null when none is saved. */
    private static String firmName(FirmDetails firm) {
        return firm == null ? null : firm.getFirmName();
    }

    private LedgerSummaryEmailResponse response(Client client, UUID companyId, FinancialYear year, List<String> cc,
                                                LedgerSummaryEmail email, LedgerExport ledger) {
        return LedgerSummaryEmailResponse.builder()
                .clientId(client.getId())
                .companyId(companyId)
                .financialYear(year.label())
                .to(client.getEmail())
                .cc(cc)
                .subject(email.subject())
                .attachmentName(ledger.fileName())
                .sentAt(clock.instant())
                .build();
    }

    /**
     * The client's manager, to copy in. A manager's email is optional, so one without leaves the
     * email without a copy. Clients are held to their manager by foreign key, so a missing manager
     * likewise only leaves the email without a copy rather than failing it.
     */
    private List<String> managerEmail(Client client) {
        Manager manager = managerRepository.findById(client.getManagerId()).orElse(null);
        if (manager == null) {
            log.warn("Manager {} of client {} not found; sending without a copy to them",
                    client.getManagerId(), client.getId());
            return List.of();
        }
        String email = manager.getEmail();
        if (email == null || email.isBlank()) {
            log.debug("Manager {} of client {} has no email; sending without a copy to them",
                    manager.getId(), client.getId());
            return List.of();
        }
        return List.of(email);
    }

    private void send(String to, List<String> cc, FirmDetails firm, LedgerSummaryEmail email, LedgerExport ledger) {
        String from = requireSender();
        try {
            MimeMessage message = mailSender.createMimeMessage();
            // Multipart, for the plain-text and HTML bodies and the attachment.
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            String fromName = senderName(firm);
            if (fromName == null) {
                helper.setFrom(from);
            } else {
                helper.setFrom(from, fromName);
            }
            helper.setTo(to);
            if (!cc.isEmpty()) {
                helper.setCc(cc.toArray(String[]::new));
            }
            // Replies go to the firm's own address when it has one, rather than the sending account.
            if (firm != null && firm.getEmail() != null && !firm.getEmail().isBlank()) {
                helper.setReplyTo(firm.getEmail());
            }
            helper.setSubject(email.subject());
            helper.setText(email.text(), email.html());
            // Shown at the top of the HTML body, which refers to it by content id; inline parts must follow
            // the text.
            if (email.logo() != null) {
                helper.addInline(LedgerSummaryEmail.LOGO_CONTENT_ID, new ByteArrayResource(email.logo()),
                        FirmLogo.MEDIA_TYPE);
            }
            helper.addAttachment(ledger.fileName(), new ByteArrayResource(ledger.content()),
                    LedgerExport.PDF_MEDIA_TYPE);
            mailSender.send(message);
        } catch (MessagingException | UnsupportedEncodingException e) {
            throw new EmailDeliveryException("The ledger summary email could not be prepared", e);
        } catch (MailException e) {
            throw new EmailDeliveryException("The ledger summary email could not be sent: the mail server "
                    + "refused it or could not be reached", e);
        }
    }

    /** The configured from address; without one, no email can be sent. */
    private String requireSender() {
        String from = properties.from();
        if (from == null || from.isBlank()) {
            throw new EmailDeliveryException("Email is not configured: set MAIL_FROM, or MAIL_USERNAME, "
                    + "and the SMTP settings");
        }
        return from;
    }

    /** The configured sender name, else the firm's name, else none. */
    private String senderName(FirmDetails firm) {
        if (properties.fromName() != null && !properties.fromName().isBlank()) {
            return properties.fromName();
        }
        if (firm != null && firm.getFirmName() != null && !firm.getFirmName().isBlank()) {
            return firm.getFirmName();
        }
        return null;
    }
}
