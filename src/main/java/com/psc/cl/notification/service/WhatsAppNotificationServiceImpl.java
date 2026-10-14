package com.psc.cl.notification.service;

import com.psc.cl.client.exception.ClientNotFoundException;
import com.psc.cl.client.model.Client;
import com.psc.cl.client.repository.ClientRepository;
import com.psc.cl.globalsettings.model.FirmDetails;
import com.psc.cl.globalsettings.repository.FirmDetailsRepository;
import com.psc.cl.ledger.service.LedgerService;
import com.psc.cl.notification.config.WhatsAppProperties;
import com.psc.cl.notification.dto.ClientWhatsAppFailure;
import com.psc.cl.notification.dto.ClientWhatsAppResult;
import com.psc.cl.notification.dto.WhatsAppReminderResponse;
import com.psc.cl.notification.exception.InvalidWhatsAppNumberException;
import com.psc.cl.notification.exception.NothingPendingException;
import com.psc.cl.notification.exception.WhatsAppDeliveryException;
import com.psc.cl.notification.exception.WhatsAppNotOptedInException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Default {@link WhatsAppNotificationService}, sending through {@link WhatsAppSender}.
 *
 * <p>Deliberately not transactional, so no database connection is held while waiting on WhatsApp.
 * Phone numbers are not logged.
 */
@Service
@RequiredArgsConstructor
public class WhatsAppNotificationServiceImpl implements WhatsAppNotificationService {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppNotificationServiceImpl.class);

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

    /** Signs the reminder when no firm name is saved; a template parameter cannot be empty. */
    private static final String DEFAULT_SENDER = "your accounts team";

    private final LedgerService ledgerService;

    private final ClientRepository clientRepository;

    private final FirmDetailsRepository firmDetailsRepository;

    private final WhatsAppSender whatsAppSender;

    private final WhatsAppProperties properties;

    private final Clock clock;

    @Override
    public ClientWhatsAppResult sendPaymentReminder(UUID clientId) {
        requireConfigured();
        Client client = clientRepository.findById(clientId).orElseThrow(() -> new ClientNotFoundException(clientId));
        if (!client.isWhatsappOptIn()) {
            throw new WhatsAppNotOptedInException(clientId);
        }
        BigDecimal pending = ledgerService.pendingByClient(List.of(clientId)).getOrDefault(clientId, BigDecimal.ZERO);
        if (pending.signum() <= 0) {
            throw new NothingPendingException(clientId);
        }
        String messageId = remind(client, pending, firmName(), DATE.format(LocalDate.now(clock)));
        return result(client, pending, messageId);
    }

    @Override
    public Optional<String> sendOptInConfirmation(UUID clientId) {
        if (!properties.confirmsOptIn()) {
            log.debug("No opt-in confirmation template is set, so client {} is not sent one", clientId);
            return Optional.empty();
        }
        requireConfigured();
        Client client = clientRepository.findById(clientId).orElseThrow(() -> new ClientNotFoundException(clientId));
        if (!client.isWhatsappOptIn()) {
            // Opted out again before the confirmation went: confirming would contradict their choice.
            log.info("Client {} is no longer opted in to WhatsApp, so no confirmation was sent", clientId);
            return Optional.empty();
        }
        String messageId = whatsAppSender.sendTemplate(whatsAppNumber(client), properties.optInConfirmationTemplate(),
                properties.templateLanguage(), List.of(client.getFirstName(), firmName()));
        log.info("Confirmed the WhatsApp opt-in of client {}", clientId);
        return Optional.of(messageId);
    }

    @Override
    public WhatsAppReminderResponse sendPaymentReminders() {
        // Every message would fail the same way, so missing settings are reported once, up front.
        requireConfigured();
        String asOf = DATE.format(LocalDate.now(clock));
        String firmName = firmName();
        List<Client> clients = clientRepository.findAll(Sort.by("firstName", "lastName", "id"));
        List<Client> optedIn = clients.stream().filter(Client::isWhatsappOptIn).toList();
        // What each opted-in client owes across every year, in one query; clients without entries are left out.
        Map<UUID, BigDecimal> pendingByClient = optedIn.isEmpty() ? Map.of()
                : ledgerService.pendingByClient(optedIn.stream().map(Client::getId).toList());
        log.info("Sending WhatsApp payment reminders to the clients owing among {} opted in, of {}", optedIn.size(),
                clients.size());

        List<ClientWhatsAppResult> sent = new ArrayList<>();
        List<ClientWhatsAppFailure> failures = new ArrayList<>();
        int skipped = 0;
        for (Client client : optedIn) {
            BigDecimal pending = pendingByClient.getOrDefault(client.getId(), BigDecimal.ZERO);
            if (pending.signum() <= 0) {
                // Settled or in credit across every year: nothing to remind them of.
                skipped++;
                continue;
            }
            try {
                sent.add(result(client, pending, remind(client, pending, firmName, asOf)));
            } catch (RuntimeException e) {
                // Reported in the response and logged; the remaining clients are still reminded.
                log.error("Failed to send a WhatsApp payment reminder to client {}", client.getId(), e);
                failures.add(ClientWhatsAppFailure.builder()
                        .clientId(client.getId())
                        .clientName(fullName(client))
                        .error(e.getMessage())
                        .build());
            }
        }
        int notOptedIn = clients.size() - optedIn.size();
        log.info("Sent WhatsApp payment reminders to {} clients; {} not opted in; {} had nothing pending; {} failed",
                sent.size(), notOptedIn, skipped, failures.size());

        return WhatsAppReminderResponse.builder()
                .clientCount(clients.size())
                .sentCount(sent.size())
                .notOptedInCount(notOptedIn)
                .skippedCount(skipped)
                .sent(sent)
                .failures(failures)
                .build();
    }

    private void requireConfigured() {
        if (!properties.isConfigured()) {
            throw new WhatsAppDeliveryException("WhatsApp is not configured: set WHATSAPP_PHONE_NUMBER_ID and "
                    + "WHATSAPP_ACCESS_TOKEN");
        }
    }

    private String firmName() {
        return firmDetailsRepository.findFirstByOrderByCreatedAtAsc()
                .map(FirmDetails::getFirmName)
                .filter(name -> !name.isBlank())
                .orElse(DEFAULT_SENDER);
    }

    /**
     * Sends one client the reminder template: {@code {{1}}} their first name, {@code {{2}}} the firm,
     * {@code {{3}}} the amount pending and {@code {{4}}} the date it is as of.
     *
     * @return the identifier WhatsApp gave the message
     */
    private String remind(Client client, BigDecimal pending, String firmName, String asOf) {
        String messageId = whatsAppSender.sendTemplate(whatsAppNumber(client), properties.paymentReminderTemplate(),
                properties.templateLanguage(),
                List.of(client.getFirstName(), firmName, LedgerSummaryEmail.rupees(pending), asOf));
        log.info("Sent a WhatsApp payment reminder to client {}", client.getId());
        return messageId;
    }

    private String whatsAppNumber(Client client) {
        return PhoneNumbers.forWhatsApp(client.getPhone(), properties.defaultCountryCode())
                .orElseThrow(() -> new InvalidWhatsAppNumberException(client.getId()));
    }

    private static ClientWhatsAppResult result(Client client, BigDecimal pending, String messageId) {
        return ClientWhatsAppResult.builder()
                .clientId(client.getId())
                .clientName(fullName(client))
                .pending(pending)
                .messageId(messageId)
                .build();
    }

    private static String fullName(Client client) {
        return client.getFirstName() + " " + client.getLastName();
    }
}
