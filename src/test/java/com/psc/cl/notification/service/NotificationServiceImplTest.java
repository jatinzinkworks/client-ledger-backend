package com.psc.cl.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.psc.cl.client.exception.ClientNotFoundException;
import com.psc.cl.client.exception.CompanyNotFoundException;
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
import com.psc.cl.ledger.model.FinancialYear;
import com.psc.cl.ledger.service.FinancialYears;
import com.psc.cl.ledger.service.LedgerService;
import com.psc.cl.managercatalog.model.Manager;
import com.psc.cl.managercatalog.repository.ManagerRepository;
import com.psc.cl.notification.config.NotificationProperties;
import com.psc.cl.notification.dto.BulkLedgerSummaryEmailResponse;
import com.psc.cl.notification.dto.ClientEmailResult;
import com.psc.cl.notification.dto.LedgerSummaryEmailResponse;
import com.psc.cl.notification.dto.PaymentReminderResponse;
import com.psc.cl.notification.exception.EmailDeliveryException;
import jakarta.mail.Address;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    /** 14 October 2026 in India, in financial year 2026-27. */
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-13T20:00:00Z"), ZoneId.of("Asia/Kolkata"));

    private static final FinancialYear YEAR = FinancialYears.parse("2026-27");

    private static final UUID CLIENT_ID = UUID.fromString("7a3d9e10-5b2c-4f81-a6e4-2c8b1d0f9e35");

    private static final UUID COMPANY_ID = UUID.fromString("4c1f8a52-2d6b-4e93-b7a0-9e5d3c1f6a28");

    private static final UUID MANAGER_ID = UUID.fromString("e9b2c4d1-7f35-4a68-91c0-3d8a5e2f6b47");

    private static final String FILE_NAME = "Mehta_Textiles_Pvt_Ltd_Ledger_FY2026-27.xlsx";

    @Mock
    private LedgerService ledgerService;

    @Mock
    private ClientRepository clientRepository;

    @Mock
    private ClientCompanyRepository clientCompanyRepository;

    @Mock
    private ManagerRepository managerRepository;

    @Mock
    private FirmDetailsRepository firmDetailsRepository;

    @Mock
    private JavaMailSender mailSender;

    @Test
    @DisplayName("emails the client the current year's summary, copying their manager, with the ledger attached")
    void sendsLedgerSummary() throws MessagingException {
        stubLedger();
        when(clientRepository.findById(CLIENT_ID)).thenReturn(Optional.of(client()));
        when(managerRepository.findById(MANAGER_ID)).thenReturn(Optional.of(manager()));
        when(firmDetailsRepository.findFirstByOrderByCreatedAtAsc()).thenReturn(Optional.of(FirmDetails.builder()
                .firmName("Sharma & Associates")
                .email("accounts@sharma.example")
                .build()));
        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage((Session) null));

        LedgerSummaryEmailResponse response = service("ledger@sharma.example", null)
                .sendLedgerSummary(CLIENT_ID, COMPANY_ID);

        ArgumentCaptor<MimeMessage> sent = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(sent.capture());
        MimeMessage message = sent.getValue();
        assertThat(addresses(message.getRecipients(Message.RecipientType.TO))).containsExactly("rohan@example.com");
        assertThat(addresses(message.getRecipients(Message.RecipientType.CC)))
                .containsExactly("pranay@sharma.example");
        assertThat(addresses(message.getFrom())).singleElement().asString()
                .contains("ledger@sharma.example").contains("Sharma & Associates");
        assertThat(addresses(message.getReplyTo())).containsExactly("accounts@sharma.example");
        assertThat(message.getSubject())
                .isEqualTo("Sharma & Associates: Your ledger summary for Mehta Textiles Pvt Ltd, FY 2026-27");

        assertThat(response.getFinancialYear()).isEqualTo("2026-27");
        assertThat(response.getTo()).isEqualTo("rohan@example.com");
        assertThat(response.getCc()).containsExactly("pranay@sharma.example");
        assertThat(response.getAttachmentName()).isEqualTo(FILE_NAME);
        assertThat(response.getSentAt()).isEqualTo(CLOCK.instant());
    }

    @Test
    @DisplayName("sends without a copy when the manager cannot be found")
    void sendsWithoutManager() throws MessagingException {
        stubLedger();
        when(clientRepository.findById(CLIENT_ID)).thenReturn(Optional.of(client()));
        when(managerRepository.findById(MANAGER_ID)).thenReturn(Optional.empty());
        when(firmDetailsRepository.findFirstByOrderByCreatedAtAsc()).thenReturn(Optional.empty());
        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage((Session) null));

        LedgerSummaryEmailResponse response = service("ledger@sharma.example", "Accounts")
                .sendLedgerSummary(CLIENT_ID, COMPANY_ID);

        ArgumentCaptor<MimeMessage> sent = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(sent.capture());
        assertThat(sent.getValue().getRecipients(Message.RecipientType.CC)).isNull();
        assertThat(response.getCc()).isEmpty();
    }

    @Test
    @DisplayName("sends nothing when the company is not the client's")
    void rejectsUnknownCompany() {
        when(ledgerService.summarizeCompany(CLIENT_ID, COMPANY_ID, YEAR))
                .thenThrow(new CompanyNotFoundException(CLIENT_ID, COMPANY_ID));

        assertThatThrownBy(() -> service("ledger@sharma.example", null).sendLedgerSummary(CLIENT_ID, COMPANY_ID))
                .isInstanceOf(CompanyNotFoundException.class);

        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("refuses to send when no from address is configured")
    void rejectsWhenNotConfigured() {
        stubLedger();
        when(clientRepository.findById(CLIENT_ID)).thenReturn(Optional.of(client()));
        when(managerRepository.findById(MANAGER_ID)).thenReturn(Optional.of(manager()));
        when(firmDetailsRepository.findFirstByOrderByCreatedAtAsc()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service(" ", null).sendLedgerSummary(CLIENT_ID, COMPANY_ID))
                .isInstanceOf(EmailDeliveryException.class)
                .hasMessageContaining("MAIL_FROM");

        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("reports the mail server failing as an email delivery failure")
    void reportsMailServerFailure() {
        stubLedger();
        when(clientRepository.findById(CLIENT_ID)).thenReturn(Optional.of(client()));
        when(managerRepository.findById(MANAGER_ID)).thenReturn(Optional.of(manager()));
        when(firmDetailsRepository.findFirstByOrderByCreatedAtAsc()).thenReturn(Optional.empty());
        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage((Session) null));
        doThrow(new MailSendException("Connection refused")).when(mailSender).send(any(MimeMessage.class));

        assertThatThrownBy(() -> service("ledger@sharma.example", null).sendLedgerSummary(CLIENT_ID, COMPANY_ID))
                .isInstanceOf(EmailDeliveryException.class)
                .hasCauseInstanceOf(MailSendException.class);
    }

    @Test
    @DisplayName("emails the client the summary across all companies, listing every company, with their ledger")
    void sendsClientLedgerSummary() throws MessagingException {
        UUID otherCompanyId = UUID.randomUUID();
        when(ledgerService.summarizeClient(CLIENT_ID, YEAR)).thenReturn(ClientLedgerSummaryResponse.builder()
                .clientId(CLIENT_ID)
                .clientName("Rohan Mehta")
                .financialYear("2026-27")
                .fromDate(LocalDate.of(2026, 4, 1))
                .toDate(LocalDate.of(2027, 3, 31))
                .totalBilled(new BigDecimal("39235.00"))
                .totalPaymentReceived(new BigDecimal("15000.00"))
                .pending(new BigDecimal("24235.00"))
                .totalDiscount(new BigDecimal("1750.00"))
                .build());
        when(clientRepository.findById(CLIENT_ID)).thenReturn(Optional.of(client()));
        when(managerRepository.findById(MANAGER_ID)).thenReturn(Optional.of(manager()));
        when(firmDetailsRepository.findFirstByOrderByCreatedAtAsc()).thenReturn(Optional.empty());
        when(ledgerService.companyBalances(CLIENT_ID, YEAR)).thenReturn(Map.of(COMPANY_ID,
                new CompanyBalance(new BigDecimal("39235.00"), new BigDecimal("15000.00"),
                        new BigDecimal("24235.00"))));
        when(clientCompanyRepository.findByClientIdOrderByCompanyNameAsc(CLIENT_ID)).thenReturn(List.of(
                company(COMPANY_ID, "Mehta Textiles Pvt Ltd"), company(otherCompanyId, "Rohan Mehta HUF")));
        when(ledgerService.exportClient(CLIENT_ID, YEAR))
                .thenReturn(new LedgerExport("Rohan_Mehta_Ledger_FY2026-27.xlsx", new byte[] {1, 2, 3}));
        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage((Session) null));

        LedgerSummaryEmailResponse response = service("ledger@sharma.example", null)
                .sendClientLedgerSummary(CLIENT_ID);

        ArgumentCaptor<MimeMessage> sent = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(sent.capture());
        MimeMessage message = sent.getValue();
        assertThat(addresses(message.getRecipients(Message.RecipientType.TO))).containsExactly("rohan@example.com");
        assertThat(addresses(message.getRecipients(Message.RecipientType.CC)))
                .containsExactly("pranay@sharma.example");
        assertThat(message.getSubject()).isEqualTo("Your ledger summary for all of your companies, FY 2026-27");

        assertThat(response.getCompanyId()).isNull();
        assertThat(response.getAttachmentName()).isEqualTo("Rohan_Mehta_Ledger_FY2026-27.xlsx");
        assertThat(response.getCc()).containsExactly("pranay@sharma.example");
    }

    @Test
    @DisplayName("sends nothing when the client does not exist")
    void rejectsUnknownClient() {
        when(ledgerService.summarizeClient(CLIENT_ID, YEAR)).thenThrow(new ClientNotFoundException(CLIENT_ID));

        assertThatThrownBy(() -> service("ledger@sharma.example", null).sendClientLedgerSummary(CLIENT_ID))
                .isInstanceOf(ClientNotFoundException.class);

        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("emails every client, reporting one that fails and still emailing the rest")
    void sendsToEveryClient() {
        Client failing = new Client();
        failing.setId(UUID.randomUUID());
        failing.setFirstName("Anita");
        failing.setLastName("Shah");
        when(clientRepository.findAll(any(Sort.class))).thenReturn(List.of(failing, client()));
        when(ledgerService.summarizeClient(failing.getId(), YEAR))
                .thenThrow(new IllegalStateException("Ledger entries refer to a missing company"));
        when(ledgerService.summarizeClient(CLIENT_ID, YEAR)).thenReturn(ClientLedgerSummaryResponse.builder()
                .clientId(CLIENT_ID)
                .financialYear("2026-27")
                .fromDate(LocalDate.of(2026, 4, 1))
                .toDate(LocalDate.of(2027, 3, 31))
                .totalBilled(new BigDecimal("0.00"))
                .totalPaymentReceived(new BigDecimal("0.00"))
                .pending(new BigDecimal("0.00"))
                .totalDiscount(new BigDecimal("0.00"))
                .build());
        when(clientRepository.findById(CLIENT_ID)).thenReturn(Optional.of(client()));
        when(managerRepository.findById(MANAGER_ID)).thenReturn(Optional.of(manager()));
        when(firmDetailsRepository.findFirstByOrderByCreatedAtAsc()).thenReturn(Optional.empty());
        when(ledgerService.companyBalances(CLIENT_ID, YEAR)).thenReturn(Map.of());
        when(clientCompanyRepository.findByClientIdOrderByCompanyNameAsc(CLIENT_ID)).thenReturn(List.of());
        when(ledgerService.exportClient(CLIENT_ID, YEAR))
                .thenReturn(new LedgerExport("Rohan_Mehta_Ledger_FY2026-27.xlsx", new byte[] {1}));
        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage((Session) null));

        BulkLedgerSummaryEmailResponse response = service("ledger@sharma.example", null)
                .sendAllClientLedgerSummaries();

        verify(mailSender).send(any(MimeMessage.class));
        assertThat(response.getFinancialYear()).isEqualTo("2026-27");
        assertThat(response.getClientCount()).isEqualTo(2);
        assertThat(response.getSentCount()).isEqualTo(1);
        assertThat(response.getSent()).extracting(ClientEmailResult::getClientName).containsExactly("Rohan Mehta");
        assertThat(response.getFailures()).singleElement().satisfies(failure -> {
            assertThat(failure.getClientId()).isEqualTo(failing.getId());
            assertThat(failure.getClientName()).isEqualTo("Anita Shah");
            assertThat(failure.getError()).isEqualTo("Ledger entries refer to a missing company");
        });
    }

    @Test
    @DisplayName("refuses to email every client when no from address is configured, trying none")
    void rejectsBulkWhenNotConfigured() {
        assertThatThrownBy(() -> service("", null).sendAllClientLedgerSummaries())
                .isInstanceOf(EmailDeliveryException.class)
                .hasMessageContaining("MAIL_FROM");

        verify(clientRepository, never()).findAll(any(Sort.class));
        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("reminds only the clients owing across every year, for that whole amount, skipping those in credit")
    void sendsPaymentReminders() throws MessagingException {
        Client settled = new Client();
        settled.setId(UUID.randomUUID());
        settled.setFirstName("Anita");
        settled.setLastName("Shah");
        when(clientRepository.findAll(any(Sort.class))).thenReturn(List.of(settled, client()));
        // The client owes 24,235.00 for this year and 5,765.00 carried from earlier ones.
        when(ledgerService.pendingByClient(List.of(settled.getId(), CLIENT_ID))).thenReturn(Map.of(
                settled.getId(), new BigDecimal("-100.00"), CLIENT_ID, new BigDecimal("30000.00")));
        when(ledgerService.summarizeClient(CLIENT_ID, YEAR)).thenReturn(clientSummary(CLIENT_ID, "24235.00"));
        when(clientRepository.findById(CLIENT_ID)).thenReturn(Optional.of(client()));
        when(managerRepository.findById(MANAGER_ID)).thenReturn(Optional.of(manager()));
        when(firmDetailsRepository.findFirstByOrderByCreatedAtAsc()).thenReturn(Optional.empty());
        when(ledgerService.companyBalances(CLIENT_ID, YEAR)).thenReturn(Map.of());
        when(clientCompanyRepository.findByClientIdOrderByCompanyNameAsc(CLIENT_ID)).thenReturn(List.of());
        when(ledgerService.exportClient(CLIENT_ID, YEAR))
                .thenReturn(new LedgerExport("Rohan_Mehta_Ledger_FY2026-27.xlsx", new byte[] {1}));
        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage((Session) null));

        PaymentReminderResponse response = service("ledger@sharma.example", null).sendPaymentReminders();

        ArgumentCaptor<MimeMessage> sent = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(sent.capture());
        assertThat(addresses(sent.getValue().getRecipients(Message.RecipientType.TO)))
                .containsExactly("rohan@example.com");
        assertThat(addresses(sent.getValue().getRecipients(Message.RecipientType.CC)))
                .containsExactly("pranay@sharma.example");
        assertThat(sent.getValue().getSubject()).isEqualTo("Payment reminder: \u20B930,000.00 pending");
        verify(ledgerService, never()).summarizeClient(settled.getId(), YEAR);
        verify(ledgerService, never()).exportClient(settled.getId(), YEAR);

        assertThat(response.getFinancialYear()).isEqualTo("2026-27");
        assertThat(response.getClientCount()).isEqualTo(2);
        assertThat(response.getSentCount()).isEqualTo(1);
        assertThat(response.getSkippedCount()).isEqualTo(1);
        assertThat(response.getSent()).singleElement().satisfies(reminded -> {
            assertThat(reminded.getClientId()).isEqualTo(CLIENT_ID);
            assertThat(reminded.getPending()).isEqualTo(new BigDecimal("30000.00"));
        });
        assertThat(response.getFailures()).isEmpty();
    }

    @Test
    @DisplayName("reports a client whose reminder fails and still reminds the rest")
    void continuesRemindersPastFailure() {
        UUID failingId = UUID.randomUUID();
        Client failing = new Client();
        failing.setId(failingId);
        failing.setFirstName("Anita");
        failing.setLastName("Shah");
        when(clientRepository.findAll(any(Sort.class))).thenReturn(List.of(failing));
        when(ledgerService.pendingByClient(List.of(failingId))).thenReturn(Map.of(failingId, new BigDecimal("500.00")));
        when(ledgerService.summarizeClient(failingId, YEAR)).thenReturn(clientSummary(failingId, "500.00"));
        when(clientRepository.findById(failingId)).thenReturn(Optional.empty());

        PaymentReminderResponse response = service("ledger@sharma.example", null).sendPaymentReminders();

        assertThat(response.getSentCount()).isZero();
        assertThat(response.getFailures()).singleElement()
                .satisfies(failure -> assertThat(failure.getClientName()).isEqualTo("Anita Shah"));
        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("refuses to send reminders when no from address is configured, trying none")
    void rejectsRemindersWhenNotConfigured() {
        assertThatThrownBy(() -> service(null, null).sendPaymentReminders())
                .isInstanceOf(EmailDeliveryException.class);

        verify(clientRepository, never()).findAll(any(Sort.class));
    }

    private static ClientLedgerSummaryResponse clientSummary(UUID clientId, String pending) {
        return ClientLedgerSummaryResponse.builder()
                .clientId(clientId)
                .financialYear("2026-27")
                .fromDate(LocalDate.of(2026, 4, 1))
                .toDate(LocalDate.of(2027, 3, 31))
                .totalBilled(new BigDecimal("39235.00"))
                .totalPaymentReceived(new BigDecimal("15000.00"))
                .pending(new BigDecimal(pending))
                .totalDiscount(new BigDecimal("1750.00"))
                .build();
    }

    private static ClientCompany company(UUID id, String name) {
        ClientCompany company = new ClientCompany();
        company.setId(id);
        company.setCompanyName(name);
        return company;
    }

    private NotificationServiceImpl service(String from, String fromName) {
        return new NotificationServiceImpl(ledgerService, clientRepository, clientCompanyRepository, managerRepository,
                firmDetailsRepository, mailSender, new NotificationProperties(from, fromName), CLOCK);
    }

    private void stubLedger() {
        when(ledgerService.summarizeCompany(CLIENT_ID, COMPANY_ID, YEAR)).thenReturn(
                CompanyLedgerSummaryResponse.builder()
                        .clientId(CLIENT_ID)
                        .companyId(COMPANY_ID)
                        .companyName("Mehta Textiles Pvt Ltd")
                        .financialYear("2026-27")
                        .fromDate(LocalDate.of(2026, 4, 1))
                        .toDate(LocalDate.of(2027, 3, 31))
                        .totalBilled(new BigDecimal("39235.00"))
                        .totalPaymentReceived(new BigDecimal("15000.00"))
                        .pending(new BigDecimal("24235.00"))
                        .totalDiscount(new BigDecimal("1750.00"))
                        .build());
        when(ledgerService.exportCompany(CLIENT_ID, COMPANY_ID, YEAR))
                .thenReturn(new LedgerExport(FILE_NAME, new byte[] {1, 2, 3}));
    }

    private static List<String> addresses(Address[] addresses) {
        return Arrays.stream(addresses).map(Address::toString).toList();
    }

    private static Client client() {
        Client client = new Client();
        client.setId(CLIENT_ID);
        client.setFirstName("Rohan");
        client.setLastName("Mehta");
        client.setEmail("rohan@example.com");
        client.setManagerId(MANAGER_ID);
        return client;
    }

    private static Manager manager() {
        Manager manager = new Manager();
        manager.setId(MANAGER_ID);
        manager.setFirstName("Pranay");
        manager.setLastName("Singhal");
        manager.setEmail("pranay@sharma.example");
        return manager;
    }
}
