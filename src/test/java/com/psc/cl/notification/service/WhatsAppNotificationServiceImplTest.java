package com.psc.cl.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@ExtendWith(MockitoExtension.class)
class WhatsAppNotificationServiceImplTest {

    /** 14 October 2026 in India. */
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-13T20:00:00Z"), ZoneId.of("Asia/Kolkata"));

    private static final WhatsAppProperties CONFIGURED = properties("106540352242922", "test-token");

    /** Configured, with an opt-in confirmation template set. */
    private static final WhatsAppProperties CONFIRMING = new WhatsAppProperties("https://graph.facebook.com",
            "v23.0", "106540352242922", "test-token", "payment_reminder", "en", "91", Duration.ofSeconds(10),
            "opt_in_confirmation");

    @Mock
    private LedgerService ledgerService;

    @Mock
    private ClientRepository clientRepository;

    @Mock
    private FirmDetailsRepository firmDetailsRepository;

    @Mock
    private WhatsAppSender whatsAppSender;

    @Test
    @DisplayName("reminds one opted-in client of what they owe across every year, with the template")
    void sendsReminderToOneClient() {
        Client client = client("Rohan", "Mehta", "+91 98200 12345", true);
        when(clientRepository.findById(client.getId())).thenReturn(Optional.of(client));
        when(ledgerService.pendingByClient(List.of(client.getId())))
                .thenReturn(Map.of(client.getId(), new BigDecimal("24235.00")));
        when(firmDetailsRepository.findFirstByOrderByCreatedAtAsc()).thenReturn(Optional.of(FirmDetails.builder()
                .firmName("Sharma & Associates").build()));
        when(whatsAppSender.sendTemplate("919820012345", "payment_reminder", "en",
                List.of("Rohan", "Sharma & Associates", "₹24,235.00", "14 Oct 2026")))
                .thenReturn("wamid.ABC123");

        ClientWhatsAppResult result = service(CONFIGURED).sendPaymentReminder(client.getId());

        assertThat(result.getClientId()).isEqualTo(client.getId());
        assertThat(result.getClientName()).isEqualTo("Rohan Mehta");
        assertThat(result.getPending()).isEqualTo(new BigDecimal("24235.00"));
        assertThat(result.getMessageId()).isEqualTo("wamid.ABC123");
    }

    @Test
    @DisplayName("refuses to message one client who has not opted in, without looking up what they owe")
    void rejectsClientNotOptedIn() {
        Client client = client("Rohan", "Mehta", "+91 98200 12345", false);
        when(clientRepository.findById(client.getId())).thenReturn(Optional.of(client));

        assertThatThrownBy(() -> service(CONFIGURED).sendPaymentReminder(client.getId()))
                .isInstanceOf(WhatsAppNotOptedInException.class);

        verify(ledgerService, never()).pendingByClient(anyList());
        verifyNothingSent();
    }

    @Test
    @DisplayName("refuses to remind one client who owes nothing")
    void rejectsClientOwingNothing() {
        Client client = client("Rohan", "Mehta", "+91 98200 12345", true);
        when(clientRepository.findById(client.getId())).thenReturn(Optional.of(client));
        when(ledgerService.pendingByClient(List.of(client.getId()))).thenReturn(Map.of());

        assertThatThrownBy(() -> service(CONFIGURED).sendPaymentReminder(client.getId()))
                .isInstanceOf(NothingPendingException.class);

        verifyNothingSent();
    }

    @Test
    @DisplayName("refuses to remind one client whose phone number WhatsApp cannot use")
    void rejectsUnusableNumber() {
        Client client = client("Rohan", "Mehta", "12345", true);
        // Fixed so the check below that the number is left out of the message cannot match the identifier.
        client.setId(UUID.fromString("7a3d9e10-5b2c-4f81-a6e4-2c8b1d0f9e35"));
        when(clientRepository.findById(client.getId())).thenReturn(Optional.of(client));
        when(ledgerService.pendingByClient(List.of(client.getId())))
                .thenReturn(Map.of(client.getId(), new BigDecimal("100.00")));
        when(firmDetailsRepository.findFirstByOrderByCreatedAtAsc()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service(CONFIGURED).sendPaymentReminder(client.getId()))
                .isInstanceOf(InvalidWhatsAppNumberException.class)
                .hasMessageNotContaining("12345");

        verifyNothingSent();
    }

    @Test
    @DisplayName("reports an unknown client as not found")
    void rejectsUnknownClient() {
        UUID clientId = UUID.randomUUID();
        when(clientRepository.findById(clientId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service(CONFIGURED).sendPaymentReminder(clientId))
                .isInstanceOf(ClientNotFoundException.class);
    }

    @Test
    @DisplayName("reminds the opted-in clients owing, counting those not opted in and those owing nothing apart")
    void sendsReminders() {
        Client owing = client("Rohan", "Mehta", "+91 98200 12345", true);
        Client inCredit = client("Anita", "Shah", "98200 54321", true);
        Client neverBilled = client("Vikram", "Rao", "98200 99999", true);
        Client notOptedIn = client("Meera", "Iyer", "98200 11111", false);
        when(firmDetailsRepository.findFirstByOrderByCreatedAtAsc()).thenReturn(Optional.of(FirmDetails.builder()
                .firmName("Sharma & Associates").build()));
        when(clientRepository.findAll(any(Sort.class))).thenReturn(List.of(inCredit, notOptedIn, neverBilled, owing));
        // Only opted-in clients are looked up, so the one not opted in is never asked about.
        when(ledgerService.pendingByClient(List.of(inCredit.getId(), neverBilled.getId(), owing.getId())))
                .thenReturn(Map.of(owing.getId(), new BigDecimal("24235.00"),
                        inCredit.getId(), new BigDecimal("-100.00")));
        when(whatsAppSender.sendTemplate("919820012345", "payment_reminder", "en",
                List.of("Rohan", "Sharma & Associates", "₹24,235.00", "14 Oct 2026")))
                .thenReturn("wamid.ABC123");

        WhatsAppReminderResponse response = service(CONFIGURED).sendPaymentReminders();

        assertThat(response.getClientCount()).isEqualTo(4);
        assertThat(response.getSentCount()).isEqualTo(1);
        assertThat(response.getNotOptedInCount()).isEqualTo(1);
        assertThat(response.getSkippedCount()).isEqualTo(2);
        assertThat(response.getSent()).singleElement().satisfies(sent -> {
            assertThat(sent.getClientId()).isEqualTo(owing.getId());
            assertThat(sent.getPending()).isEqualTo(new BigDecimal("24235.00"));
            assertThat(sent.getMessageId()).isEqualTo("wamid.ABC123");
        });
        assertThat(response.getFailures()).isEmpty();
    }

    @Test
    @DisplayName("reports a client whose number cannot be used, or whose message is refused, and carries on")
    void continuesPastFailures() {
        Client noPhone = client("Anita", "Shah", "12345", true);
        Client refused = client("Rohan", "Mehta", "98200 12345", true);
        Client reminded = client("Vikram", "Rao", "+91 98200 99999", true);
        when(firmDetailsRepository.findFirstByOrderByCreatedAtAsc()).thenReturn(Optional.empty());
        when(clientRepository.findAll(any(Sort.class))).thenReturn(List.of(noPhone, refused, reminded));
        when(ledgerService.pendingByClient(anyList())).thenReturn(Map.of(
                noPhone.getId(), new BigDecimal("100.00"),
                refused.getId(), new BigDecimal("200.00"),
                reminded.getId(), new BigDecimal("300.00")));
        when(whatsAppSender.sendTemplate(anyString(), anyString(), anyString(), anyList()))
                .thenAnswer(invocation -> {
                    if ("919820012345".equals(invocation.getArgument(0))) {
                        throw new WhatsAppDeliveryException("WhatsApp refused the message: Invalid parameter");
                    }
                    List<String> parameters = invocation.getArgument(3);
                    assertThat(parameters.get(1)).isEqualTo("your accounts team");
                    return "wamid.XYZ";
                });

        WhatsAppReminderResponse response = service(CONFIGURED).sendPaymentReminders();

        assertThat(response.getSentCount()).isEqualTo(1);
        assertThat(response.getSent().getFirst().getClientId()).isEqualTo(reminded.getId());
        assertThat(response.getFailures()).extracting(ClientWhatsAppFailure::getClientName)
                .containsExactly("Anita Shah", "Rohan Mehta");
        assertThat(response.getFailures().getFirst().getError()).contains("cannot be used for WhatsApp");
        assertThat(response.getFailures().getLast().getError()).contains("Invalid parameter");
    }

    @Test
    @DisplayName("asks the ledger nothing when no client has opted in")
    void skipsLedgerWhenNoneOptedIn() {
        when(firmDetailsRepository.findFirstByOrderByCreatedAtAsc()).thenReturn(Optional.empty());
        when(clientRepository.findAll(any(Sort.class)))
                .thenReturn(List.of(client("Meera", "Iyer", "98200 11111", false)));

        WhatsAppReminderResponse response = service(CONFIGURED).sendPaymentReminders();

        assertThat(response.getNotOptedInCount()).isEqualTo(1);
        assertThat(response.getSentCount()).isZero();
        verify(ledgerService, never()).pendingByClient(anyList());
        verifyNothingSent();
    }

    @Test
    @DisplayName("refuses to send when WhatsApp is not configured, trying no client")
    void rejectsWhenNotConfigured() {
        WhatsAppNotificationServiceImpl unconfigured = service(properties("", ""));

        assertThatThrownBy(unconfigured::sendPaymentReminders)
                .isInstanceOf(WhatsAppDeliveryException.class)
                .hasMessageContaining("WHATSAPP_ACCESS_TOKEN");
        assertThatThrownBy(() -> unconfigured.sendPaymentReminder(UUID.randomUUID()))
                .isInstanceOf(WhatsAppDeliveryException.class);

        verify(clientRepository, never()).findAll(any(Sort.class));
        verify(clientRepository, never()).findById(any());
        verifyNothingSent();
    }

    @Test
    @DisplayName("confirms an opt-in with the confirmation template, naming the client and the firm")
    void sendsOptInConfirmation() {
        Client client = client("Rohan", "Mehta", "+91 98200 12345", true);
        when(clientRepository.findById(client.getId())).thenReturn(Optional.of(client));
        when(firmDetailsRepository.findFirstByOrderByCreatedAtAsc()).thenReturn(Optional.of(FirmDetails.builder()
                .firmName("Sharma & Associates").build()));
        when(whatsAppSender.sendTemplate("919820012345", "opt_in_confirmation", "en",
                List.of("Rohan", "Sharma & Associates"))).thenReturn("wamid.OPTIN");

        assertThat(service(CONFIRMING).sendOptInConfirmation(client.getId())).contains("wamid.OPTIN");
    }

    @Test
    @DisplayName("sends no confirmation, and looks nothing up, while no confirmation template is set")
    void skipsConfirmationWithoutTemplate() {
        assertThat(service(CONFIGURED).sendOptInConfirmation(UUID.randomUUID())).isEmpty();

        verify(clientRepository, never()).findById(any());
        verifyNothingSent();
    }

    @Test
    @DisplayName("sends no confirmation to a client who opted out again before it went")
    void skipsConfirmationAfterOptOut() {
        Client client = client("Rohan", "Mehta", "+91 98200 12345", false);
        when(clientRepository.findById(client.getId())).thenReturn(Optional.of(client));

        assertThat(service(CONFIRMING).sendOptInConfirmation(client.getId())).isEmpty();

        verifyNothingSent();
    }

    @Test
    @DisplayName("refuses to confirm when the template is set but WhatsApp is not configured")
    void rejectsConfirmationWhenNotConfigured() {
        WhatsAppProperties templateOnly = new WhatsAppProperties("https://graph.facebook.com", "v23.0", "", "",
                "payment_reminder", "en", "91", Duration.ofSeconds(10), "opt_in_confirmation");

        assertThatThrownBy(() -> service(templateOnly).sendOptInConfirmation(UUID.randomUUID()))
                .isInstanceOf(WhatsAppDeliveryException.class);

        verifyNothingSent();
    }

    private void verifyNothingSent() {
        verify(whatsAppSender, never()).sendTemplate(anyString(), anyString(), anyString(), anyList());
    }

    private WhatsAppNotificationServiceImpl service(WhatsAppProperties properties) {
        return new WhatsAppNotificationServiceImpl(ledgerService, clientRepository, firmDetailsRepository,
                whatsAppSender, properties, CLOCK);
    }

    private static WhatsAppProperties properties(String phoneNumberId, String accessToken) {
        return new WhatsAppProperties("https://graph.facebook.com", "v23.0", phoneNumberId, accessToken,
                "payment_reminder", "en", "91", Duration.ofSeconds(10), null);
    }

    private static Client client(String firstName, String lastName, String phone, boolean whatsappOptIn) {
        Client client = new Client();
        client.setId(UUID.randomUUID());
        client.setFirstName(firstName);
        client.setLastName(lastName);
        client.setPhone(phone);
        client.setWhatsappOptIn(whatsappOptIn);
        return client;
    }
}
