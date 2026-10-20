package com.psc.cl.client.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.psc.cl.client.dto.ClientManagerBulkUpdateRequest;
import com.psc.cl.client.dto.ClientRequest;
import com.psc.cl.client.dto.ClientResponse;
import com.psc.cl.client.dto.ClientSummaryResponse;
import com.psc.cl.client.dto.ClientUpdateRequest;
import com.psc.cl.client.dto.NewCompanyRequest;
import com.psc.cl.client.exception.AssignedManagerNotFoundException;
import com.psc.cl.client.exception.ClientHasLedgerEntriesException;
import com.psc.cl.client.exception.ClientHasServicesException;
import com.psc.cl.client.exception.ClientNotFoundException;
import com.psc.cl.client.exception.ClientsNotFoundException;
import com.psc.cl.client.model.Client;
import com.psc.cl.client.model.ClientCompany;
import com.psc.cl.client.model.CompanyType;
import com.psc.cl.client.repository.ClientRepository;
import com.psc.cl.client.repository.ServiceAssignmentRepository;
import com.psc.cl.exception.ResourceInUseException;
import com.psc.cl.ledger.dto.OpeningBalance;
import com.psc.cl.ledger.service.LedgerService;
import com.psc.cl.managercatalog.model.Manager;
import com.psc.cl.managercatalog.repository.ManagerRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@ExtendWith(MockitoExtension.class)
class ClientServiceImplTest {

    private static final UUID MANAGER_ID = UUID.fromString("e9b2c4d1-7f35-4a68-91c0-3d8a5e2f6b47");

    @Mock
    private ClientRepository clientRepository;

    @Mock
    private ServiceAssignmentRepository serviceAssignmentRepository;

    @Mock
    private ManagerRepository managerRepository;

    @Mock
    private LedgerService ledgerService;

    @InjectMocks
    private ClientServiceImpl clientService;

    @Captor
    private ArgumentCaptor<Client> clientCaptor;

    @Test
    @DisplayName("stores every supplied field and each company on create")
    void createsClient() {
        when(managerRepository.findById(MANAGER_ID)).thenReturn(Optional.of(manager()));
        stubSave();

        ClientResponse response = clientService.createClient(request().build());

        verify(clientRepository).save(clientCaptor.capture());
        Client saved = clientCaptor.getValue();
        assertThat(saved.getFirstName()).isEqualTo("Rohan");
        assertThat(saved.getLastName()).isEqualTo("Mehta");
        assertThat(saved.getPhone()).isEqualTo("+91 98200 12345");
        assertThat(saved.getEmail()).isEqualTo("rohan.mehta@example.com");
        assertThat(saved.getManagerId()).isEqualTo(MANAGER_ID);
        assertThat(saved.getCompanies()).hasSize(2);
        assertThat(saved.getCompanies()).allSatisfy(company -> assertThat(company.getClient()).isSameAs(saved));

        ClientCompany first = saved.getCompanies().getFirst();
        assertThat(first.getCompanyName()).isEqualTo("Rohan Mehta HUF");
        assertThat(first.getCompanyType()).isEqualTo(CompanyType.HUF);
        assertThat(first.getAddress()).isEqualTo("4 Park Street, Kolkata, West Bengal 700016");

        assertThat(response.getId()).isEqualTo(saved.getId());
        assertThat(response.getManagerId()).isEqualTo(MANAGER_ID);
        assertThat(response.getManagerName()).isEqualTo("Pranay Singhal");
    }

    @Test
    @DisplayName("records the opening balance of each company that brings one, against that company")
    void recordsOpeningBalances() {
        when(managerRepository.findById(MANAGER_ID)).thenReturn(Optional.of(manager()));
        stubSave();
        LocalDate dated = LocalDate.of(2026, 3, 31);

        clientService.createClient(request()
                .companies(List.of(
                        company("Rohan Mehta HUF", null).toBuilder()
                                .openingBalance(new BigDecimal("25000.00"))
                                .openingBalanceDate(dated)
                                .build(),
                        company("Mehta Textiles Pvt Ltd", null),
                        company("Mehta Exports LLP", null).toBuilder()
                                .openingBalance(new BigDecimal("-5000.50"))
                                .build(),
                        company("Mehta Trust", null).toBuilder()
                                .openingBalance(BigDecimal.ZERO)
                                .build()))
                .build());

        verify(clientRepository).save(clientCaptor.capture());
        List<ClientCompany> saved = clientCaptor.getValue().getCompanies();
        UUID clientId = clientCaptor.getValue().getId();
        verify(ledgerService).recordOpeningBalance(clientId, saved.get(0).getId(), new BigDecimal("25000.00"),
                dated);
        // Undated, so dated by the ledger on the day it is recorded; a credit stays negative.
        verify(ledgerService).recordOpeningBalance(clientId, saved.get(2).getId(), new BigDecimal("-5000.50"),
                null);
        // Neither the company without one nor the one with zero records anything.
        verify(ledgerService, times(2)).recordOpeningBalance(any(), any(), any(), any());
    }

    @Test
    @DisplayName("records no opening balance when no company brings one")
    void recordsNoOpeningBalance() {
        when(managerRepository.findById(MANAGER_ID)).thenReturn(Optional.of(manager()));
        stubSave();

        clientService.createClient(request().build());

        verify(ledgerService, never()).recordOpeningBalance(any(), any(), any(), any());
    }

    @Test
    @DisplayName("returns companies ordered by name, whatever order they were supplied in")
    void ordersCompaniesByName() {
        when(managerRepository.findById(MANAGER_ID)).thenReturn(Optional.of(manager()));
        stubSave();

        ClientResponse response = clientService.createClient(request().build());

        assertThat(response.getCompanies())
                .extracting("companyName")
                .containsExactly("Mehta Textiles Pvt Ltd", "Rohan Mehta HUF");
    }

    @Test
    @DisplayName("trims surrounding whitespace before storing")
    void trimsValues() {
        when(managerRepository.findById(MANAGER_ID)).thenReturn(Optional.of(manager()));
        stubSave();

        clientService.createClient(request()
                .firstName("  Rohan  ")
                .lastName("  Mehta  ")
                .phone("  +91 98200 12345  ")
                .email("  rohan.mehta@example.com  ")
                .companies(List.of(NewCompanyRequest.builder()
                        .companyName("  Mehta Textiles Pvt Ltd  ")
                        .companyType(CompanyType.PRIVATE_LIMITED)
                        .address("  12 MG Road, Bengaluru  ")
                        .build()))
                .build());

        verify(clientRepository).save(clientCaptor.capture());
        Client saved = clientCaptor.getValue();
        assertThat(saved.getFirstName()).isEqualTo("Rohan");
        assertThat(saved.getLastName()).isEqualTo("Mehta");
        assertThat(saved.getPhone()).isEqualTo("+91 98200 12345");
        assertThat(saved.getEmail()).isEqualTo("rohan.mehta@example.com");
        assertThat(saved.getCompanies().getFirst().getCompanyName()).isEqualTo("Mehta Textiles Pvt Ltd");
        assertThat(saved.getCompanies().getFirst().getAddress()).isEqualTo("12 MG Road, Bengaluru");
    }

    @Test
    @DisplayName("rejects a manager that does not exist without saving anything")
    void rejectsUnknownManager() {
        when(managerRepository.findById(MANAGER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> clientService.createClient(request().build()))
                .isInstanceOf(AssignedManagerNotFoundException.class)
                .hasMessageContaining(MANAGER_ID.toString());

        verify(clientRepository, never()).save(any());
    }

    @Test
    @DisplayName("defaults the client discount to zero, which companies without their own take")
    void defaultsDiscountToZero() {
        when(managerRepository.findById(MANAGER_ID)).thenReturn(Optional.of(manager()));
        stubSave();

        ClientResponse response = clientService.createClient(request().build());

        verify(clientRepository).save(clientCaptor.capture());
        Client saved = clientCaptor.getValue();
        assertThat(saved.getDiscountPercent()).isEqualTo(new BigDecimal("0.00"));
        assertThat(saved.getCompanies()).allSatisfy(company -> {
            assertThat(company.getDiscountPercent()).isNull();
            assertThat(company.effectiveDiscountPercent()).isEqualTo(new BigDecimal("0.00"));
        });
        assertThat(response.getDiscountPercent()).isEqualTo(new BigDecimal("0.00"));
        assertThat(response.getCompanies()).allSatisfy(company -> {
            assertThat(company.getDiscountPercent()).isEqualTo(new BigDecimal("0.00"));
            assertThat(company.getDiscountInherited()).isTrue();
        });
    }

    @Test
    @DisplayName("stores no discount on companies that do not set their own, so they take the client's")
    void companiesInheritClientDiscount() {
        when(managerRepository.findById(MANAGER_ID)).thenReturn(Optional.of(manager()));
        stubSave();

        clientService.createClient(request().discountPercent(new BigDecimal("10")).build());

        verify(clientRepository).save(clientCaptor.capture());
        Client saved = clientCaptor.getValue();
        assertThat(saved.getDiscountPercent()).isEqualTo(new BigDecimal("10.00"));
        assertThat(saved.getCompanies()).allSatisfy(company -> {
            assertThat(company.getDiscountPercent()).isNull();
            assertThat(company.effectiveDiscountPercent()).isEqualTo(new BigDecimal("10.00"));
        });
    }

    @Test
    @DisplayName("keeps a company's own discount, even zero, over the client discount")
    void companyDiscountOverridesClientDiscount() {
        when(managerRepository.findById(MANAGER_ID)).thenReturn(Optional.of(manager()));
        stubSave();

        clientService.createClient(request()
                .discountPercent(new BigDecimal("10.00"))
                .companies(List.of(
                        company("Rohan Mehta HUF", new BigDecimal("2.5")),
                        company("Mehta Textiles Pvt Ltd", BigDecimal.ZERO),
                        company("Mehta Exports LLP", null)))
                .build());

        verify(clientRepository).save(clientCaptor.capture());
        List<ClientCompany> companies = clientCaptor.getValue().getCompanies();
        assertThat(companies.get(0).getDiscountPercent()).isEqualTo(new BigDecimal("2.50"));
        assertThat(companies.get(1).getDiscountPercent()).isEqualTo(new BigDecimal("0.00"));
        assertThat(companies.get(1).isDiscountInherited()).isFalse();
        assertThat(companies.get(2).getDiscountPercent()).isNull();
        assertThat(companies.get(2).effectiveDiscountPercent()).isEqualTo(new BigDecimal("10.00"));
    }

    @Test
    @DisplayName("on update, replaces the client's fields and leaves their companies untouched")
    void updatesClientWithoutTouchingCompanies() {
        Client stored = stored("Rohan", "Mehta", "Rohan Mehta HUF", "Mehta Textiles Pvt Ltd");
        List<ClientCompany> companiesBefore = List.copyOf(stored.getCompanies());
        when(clientRepository.findById(stored.getId())).thenReturn(Optional.of(stored));
        when(managerRepository.findById(MANAGER_ID)).thenReturn(Optional.of(manager()));

        ClientResponse response = clientService.updateClient(stored.getId(), updateRequest()
                .firstName("  Rohit  ")
                .email("  rohit.mehta@example.com  ")
                .build());

        verify(clientRepository).flush();
        assertThat(stored.getFirstName()).isEqualTo("Rohit");
        assertThat(stored.getEmail()).isEqualTo("rohit.mehta@example.com");
        assertThat(stored.getCompanies()).containsExactlyElementsOf(companiesBefore);
        assertThat(stored.getCompanies().get(0).getCompanyName()).isEqualTo("Rohan Mehta HUF");
        assertThat(response.getFirstName()).isEqualTo("Rohit");
        assertThat(response.getManagerName()).isEqualTo("Pranay Singhal");
        assertThat(response.getCompanies()).hasSize(2);
        verify(serviceAssignmentRepository, never()).existsByCompanyIdIn(any());
    }

    @Test
    @DisplayName("on update, companies without their own discount take the new client discount, the rest keep theirs")
    void updateAppliesClientDiscountToInheritingCompanies() {
        Client stored = stored("Rohan", "Mehta", "Rohan Mehta HUF", "Mehta Textiles Pvt Ltd");
        ClientCompany own = stored.getCompanies().get(0);
        ClientCompany inheriting = stored.getCompanies().get(1);
        own.setDiscountPercent(new BigDecimal("3.00"));
        when(clientRepository.findById(stored.getId())).thenReturn(Optional.of(stored));
        when(managerRepository.findById(MANAGER_ID)).thenReturn(Optional.of(manager()));

        ClientResponse response = clientService.updateClient(stored.getId(), updateRequest()
                .discountPercent(new BigDecimal("12.5"))
                .build());

        assertThat(stored.getDiscountPercent()).isEqualTo(new BigDecimal("12.50"));
        assertThat(own.effectiveDiscountPercent()).isEqualTo(new BigDecimal("3.00"));
        assertThat(inheriting.getDiscountPercent()).isNull();
        assertThat(inheriting.effectiveDiscountPercent()).isEqualTo(new BigDecimal("12.50"));
        // Ordered by name: [0] is Mehta Textiles Pvt Ltd, [1] is Rohan Mehta HUF.
        assertThat(response.getCompanies()).extracting("discountPercent")
                .containsExactly(new BigDecimal("12.50"), new BigDecimal("3.00"));
    }

    @Test
    @DisplayName("on update, an omitted client discount resets to zero")
    void updateResetsOmittedDiscount() {
        Client stored = stored("Rohan", "Mehta", "Rohan Mehta HUF");
        stored.setDiscountPercent(new BigDecimal("10.00"));
        when(clientRepository.findById(stored.getId())).thenReturn(Optional.of(stored));
        when(managerRepository.findById(MANAGER_ID)).thenReturn(Optional.of(manager()));

        clientService.updateClient(stored.getId(), updateRequest().build());

        assertThat(stored.getDiscountPercent()).isEqualTo(new BigDecimal("0.00"));
    }

    @Test
    @DisplayName("raises not found when updating an unknown client")
    void updateFailsForUnknownClient() {
        UUID missing = UUID.randomUUID();
        when(clientRepository.findById(missing)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> clientService.updateClient(missing, updateRequest().build()))
                .isInstanceOf(ClientNotFoundException.class);

        verify(clientRepository, never()).flush();
    }

    @Test
    @DisplayName("rejects an update onto a manager that does not exist and leaves the client untouched")
    void updateRejectsUnknownManager() {
        Client stored = stored("Rohan", "Mehta", "Rohan Mehta HUF");
        when(clientRepository.findById(stored.getId())).thenReturn(Optional.of(stored));
        when(managerRepository.findById(MANAGER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> clientService.updateClient(stored.getId(),
                updateRequest().firstName("Rohit").build()))
                .isInstanceOf(AssignedManagerNotFoundException.class);

        assertThat(stored.getFirstName()).isEqualTo("Rohan");
        verify(clientRepository, never()).flush();
    }

    @Test
    @DisplayName("assigns several clients to a manager, changing nothing else, in the order first named")
    void updatesManagerInBulk() {
        Client rohan = stored("Rohan", "Mehta", "Rohan Mehta HUF");
        Client asha = stored("Asha", "Kapoor", "Kapoor & Sons");
        asha.setDiscountPercent(new BigDecimal("10.00"));
        UUID newManagerId = UUID.randomUUID();
        when(managerRepository.findById(newManagerId)).thenReturn(Optional.of(manager(newManagerId)));
        when(clientRepository.findByIdIn(any())).thenReturn(List.of(rohan, asha));
        when(ledgerService.pendingByClient(List.of(asha.getId(), rohan.getId())))
                .thenReturn(Map.of(rohan.getId(), new BigDecimal("19630.00")));

        List<ClientSummaryResponse> updated = clientService.updateManager(ClientManagerBulkUpdateRequest.builder()
                .managerId(newManagerId)
                // Asha is named twice, and is assigned once.
                .clientIds(List.of(asha.getId(), rohan.getId(), asha.getId()))
                .build());

        verify(clientRepository).flush();
        assertThat(rohan.getManagerId()).isEqualTo(newManagerId);
        assertThat(asha.getManagerId()).isEqualTo(newManagerId);
        assertThat(asha.getDiscountPercent()).isEqualTo(new BigDecimal("10.00"));
        assertThat(updated).extracting(ClientSummaryResponse::getId).containsExactly(asha.getId(), rohan.getId());
        assertThat(updated).allSatisfy(client -> {
            assertThat(client.getManagerId()).isEqualTo(newManagerId);
            assertThat(client.getManagerName()).isEqualTo("Pranay Singhal");
        });
        assertThat(updated).extracting(ClientSummaryResponse::getTotalPending)
                .containsExactly(new BigDecimal("0.00"), new BigDecimal("19630.00"));
    }

    @Test
    @DisplayName("reassigns no client when any is unknown, naming every unknown one")
    void updateManagerRejectsUnknownClients() {
        Client rohan = stored("Rohan", "Mehta", "Rohan Mehta HUF");
        UUID unknown = UUID.randomUUID();
        UUID newManagerId = UUID.randomUUID();
        when(managerRepository.findById(newManagerId)).thenReturn(Optional.of(manager(newManagerId)));
        when(clientRepository.findByIdIn(any())).thenReturn(List.of(rohan));

        assertThatThrownBy(() -> clientService.updateManager(ClientManagerBulkUpdateRequest.builder()
                .managerId(newManagerId)
                .clientIds(List.of(rohan.getId(), unknown))
                .build()))
                .isInstanceOf(ClientsNotFoundException.class)
                .hasMessageContaining(unknown.toString())
                .hasMessageNotContaining(rohan.getId().toString());

        assertThat(rohan.getManagerId()).isEqualTo(MANAGER_ID);
        verify(clientRepository, never()).flush();
    }

    @Test
    @DisplayName("reassigns no client onto a manager that does not exist")
    void updateManagerRejectsUnknownManager() {
        UUID unknown = UUID.randomUUID();
        when(managerRepository.findById(unknown)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> clientService.updateManager(ClientManagerBulkUpdateRequest.builder()
                .managerId(unknown)
                .clientIds(List.of(UUID.randomUUID()))
                .build()))
                .isInstanceOf(AssignedManagerNotFoundException.class)
                .hasMessageContaining(unknown.toString());

        verify(clientRepository, never()).findByIdIn(any());
        verify(clientRepository, never()).flush();
    }

    @Test
    @DisplayName("maps a foreign key violation from the manager deleted concurrently onto manager not found")
    void updateManagerMapsIntegrityViolation() {
        Client rohan = stored("Rohan", "Mehta", "Rohan Mehta HUF");
        UUID newManagerId = UUID.randomUUID();
        when(managerRepository.findById(newManagerId)).thenReturn(Optional.of(manager(newManagerId)));
        when(clientRepository.findByIdIn(any())).thenReturn(List.of(rohan));
        doThrow(new DataIntegrityViolationException("fk_clients_manager")).when(clientRepository).flush();

        assertThatThrownBy(() -> clientService.updateManager(ClientManagerBulkUpdateRequest.builder()
                .managerId(newManagerId)
                .clientIds(List.of(rohan.getId()))
                .build()))
                .isInstanceOf(AssignedManagerNotFoundException.class);
    }

    @Test
    @DisplayName("deletes a client without services")
    void deletesClient() {
        Client stored = stored("Rohan", "Mehta", "Rohan Mehta HUF", "Mehta Textiles Pvt Ltd");
        when(clientRepository.findById(stored.getId())).thenReturn(Optional.of(stored));
        when(serviceAssignmentRepository.existsByCompanyIdIn(any())).thenReturn(false);

        clientService.deleteClient(stored.getId());

        verify(clientRepository).delete(stored);
        verify(clientRepository).flush();
        verify(serviceAssignmentRepository).existsByCompanyIdIn(Set.of(
                stored.getCompanies().get(0).getId(), stored.getCompanies().get(1).getId()));
    }

    @Test
    @DisplayName("deletes a client whose only ledger entries are opening balances, removing them first")
    void deletesClientWithOnlyOpeningBalances() {
        Client stored = stored("Rohan", "Mehta", "Rohan Mehta HUF", "Mehta Textiles Pvt Ltd");
        when(clientRepository.findById(stored.getId())).thenReturn(Optional.of(stored));
        when(serviceAssignmentRepository.existsByCompanyIdIn(any())).thenReturn(false);
        when(ledgerService.clientHasEntriesBesidesOpeningBalances(stored.getId())).thenReturn(false);
        when(ledgerService.removeClientOpeningBalances(stored.getId())).thenReturn(2);

        clientService.deleteClient(stored.getId());

        // The balances go before the client, which their foreign keys would otherwise stop.
        InOrder order = inOrder(ledgerService, clientRepository);
        order.verify(ledgerService).removeClientOpeningBalances(stored.getId());
        order.verify(clientRepository).delete(stored);
        order.verify(clientRepository).flush();
    }

    @Test
    @DisplayName("refuses to delete a client whose companies have services")
    void deleteRejectsClientWithServices() {
        Client stored = stored("Rohan", "Mehta", "Rohan Mehta HUF");
        when(clientRepository.findById(stored.getId())).thenReturn(Optional.of(stored));
        when(serviceAssignmentRepository.existsByCompanyIdIn(any())).thenReturn(true);

        assertThatThrownBy(() -> clientService.deleteClient(stored.getId()))
                .isInstanceOf(ClientHasServicesException.class);

        verify(clientRepository, never()).delete(any());
    }

    @Test
    @DisplayName("refuses to delete a client without services but with ledger entries, naming the entries")
    void deleteRejectsClientWithLedgerEntries() {
        Client stored = stored("Rohan", "Mehta", "Rohan Mehta HUF");
        when(clientRepository.findById(stored.getId())).thenReturn(Optional.of(stored));
        when(serviceAssignmentRepository.existsByCompanyIdIn(any())).thenReturn(false);
        when(ledgerService.clientHasEntriesBesidesOpeningBalances(stored.getId())).thenReturn(true);

        assertThatThrownBy(() -> clientService.deleteClient(stored.getId()))
                .isInstanceOf(ClientHasLedgerEntriesException.class)
                .hasMessageContaining("payments or service charges")
                .hasMessageNotContaining("services assigned");

        verify(clientRepository, never()).delete(any());
        // Nothing is removed from the ledger when the client stays.
        verify(ledgerService, never()).removeClientOpeningBalances(any());
    }

    @Test
    @DisplayName("maps a foreign key violation from a concurrent service or ledger entry onto resource in use")
    void deleteClientMapsIntegrityViolation() {
        Client stored = stored("Rohan", "Mehta", "Rohan Mehta HUF");
        when(clientRepository.findById(stored.getId())).thenReturn(Optional.of(stored));
        when(serviceAssignmentRepository.existsByCompanyIdIn(any())).thenReturn(false);
        doThrow(new DataIntegrityViolationException("fk_ledger_entries_company"))
                .when(clientRepository).flush();

        assertThatThrownBy(() -> clientService.deleteClient(stored.getId()))
                .isInstanceOf(ResourceInUseException.class)
                .hasMessageContaining("a service or a ledger entry");
    }

    @Test
    @DisplayName("raises not found when deleting an unknown client")
    void deleteFailsForUnknownClient() {
        UUID missing = UUID.randomUUID();
        when(clientRepository.findById(missing)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> clientService.deleteClient(missing))
                .isInstanceOf(ClientNotFoundException.class);

        verify(clientRepository, never()).delete(any());
    }

    @Test
    @DisplayName("returns zero pending on read by id for a client with no ledger entries")
    void readsClientWithoutEntries() {
        Client stored = stored("Asha", "Kapoor", "Kapoor & Sons");
        when(clientRepository.findById(stored.getId())).thenReturn(Optional.of(stored));
        when(managerRepository.findById(MANAGER_ID)).thenReturn(Optional.of(manager()));
        when(ledgerService.pendingByClient(List.of(stored.getId()))).thenReturn(Map.of());

        assertThat(clientService.getClient(stored.getId()).getTotalPending()).isEqualTo(new BigDecimal("0.00"));
    }

    @Test
    @DisplayName("returns the stored client with companies and manager name on read by id")
    void readsClientById() {
        Client stored = stored("Rohan", "Mehta", "Rohan Mehta HUF", "Mehta Textiles Pvt Ltd");
        when(clientRepository.findById(stored.getId())).thenReturn(Optional.of(stored));
        when(managerRepository.findById(MANAGER_ID)).thenReturn(Optional.of(manager()));
        when(ledgerService.pendingByClient(List.of(stored.getId())))
                .thenReturn(Map.of(stored.getId(), new BigDecimal("19630.00")));
        UUID textilesId = stored.getCompanies().get(1).getId();
        when(ledgerService.openingBalances(any())).thenReturn(Map.of(textilesId,
                new OpeningBalance(new BigDecimal("25000.00"), LocalDate.of(2026, 3, 31))));

        ClientResponse response = clientService.getClient(stored.getId());

        assertThat(response.getId()).isEqualTo(stored.getId());
        assertThat(response.getManagerName()).isEqualTo("Pranay Singhal");
        assertThat(response.getTotalPending()).isEqualTo(new BigDecimal("19630.00"));
        assertThat(response.getCompanies())
                .extracting("companyName")
                .containsExactly("Mehta Textiles Pvt Ltd", "Rohan Mehta HUF");
        assertThat(response.getCompanies())
                .extracting("openingBalanceAmount")
                .containsExactly(new BigDecimal("25000.00"), null);
        assertThat(response.getCompanies())
                .extracting("openingBalanceDate")
                .containsExactly(LocalDate.of(2026, 3, 31), null);
    }

    @Test
    @DisplayName("raises not found when reading an unknown id")
    void readFailsForUnknownId() {
        UUID missing = UUID.randomUUID();
        when(clientRepository.findById(missing)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> clientService.getClient(missing))
                .isInstanceOf(ClientNotFoundException.class)
                .hasMessageContaining(missing.toString());
    }

    @Test
    @DisplayName("lists clients by name with company names and managers looked up in one call")
    void listsClients() {
        Client rohan = stored("Rohan", "Mehta", "Rohan Mehta HUF", "Mehta Textiles Pvt Ltd");
        Client asha = stored("Asha", "Kapoor", "Kapoor & Sons");
        when(clientRepository.findAll(any(Sort.class))).thenReturn(List.of(asha, rohan));
        when(managerRepository.findAllById(any())).thenReturn(List.of(manager()));
        // Asha has no ledger entries, so the ledger has no amount for her.
        when(ledgerService.pendingByClient(List.of(asha.getId(), rohan.getId())))
                .thenReturn(Map.of(rohan.getId(), new BigDecimal("19630.00")));

        List<ClientSummaryResponse> clients = clientService.listClients(null);

        assertThat(clients).extracting("lastName").containsExactly("Kapoor", "Mehta");
        assertThat(clients).extracting(ClientSummaryResponse::getTotalPending)
                .containsExactly(new BigDecimal("0.00"), new BigDecimal("19630.00"));
        assertThat(clients.get(1).getCompanyNames())
                .containsExactly("Mehta Textiles Pvt Ltd", "Rohan Mehta HUF");
        assertThat(clients).allSatisfy(client ->
                assertThat(client.getManagerName()).isEqualTo("Pranay Singhal"));

        ArgumentCaptor<Sort> sortCaptor = ArgumentCaptor.forClass(Sort.class);
        verify(clientRepository).findAll(sortCaptor.capture());
        assertThat(sortCaptor.getValue()).isEqualTo(Sort.by(Sort.Direction.ASC, "lastName", "firstName"));
        verify(managerRepository).findAllById(Set.of(MANAGER_ID));
    }

    @Test
    @DisplayName("returns an empty list when no client has been added")
    void listsNoClients() {
        when(clientRepository.findAll(any(Sort.class))).thenReturn(List.of());

        assertThat(clientService.listClients(null)).isEmpty();
    }

    @Test
    @DisplayName("lists only the clients of the given manager, by name")
    void listsClientsOfManager() {
        Client rohan = stored("Rohan", "Mehta", "Rohan Mehta HUF");
        when(clientRepository.findByManagerId(eq(MANAGER_ID), any(Sort.class))).thenReturn(List.of(rohan));
        when(managerRepository.findAllById(any())).thenReturn(List.of(manager()));

        List<ClientSummaryResponse> clients = clientService.listClients(MANAGER_ID);

        assertThat(clients).extracting("id").containsExactly(rohan.getId());
        assertThat(clients.getFirst().getManagerName()).isEqualTo("Pranay Singhal");
        verify(clientRepository).findByManagerId(MANAGER_ID,
                Sort.by(Sort.Direction.ASC, "lastName", "firstName"));
        verify(clientRepository, never()).findAll(any(Sort.class));
    }

    @Test
    @DisplayName("returns an empty list for a manager without clients, or one that does not exist")
    void listsNoClientsOfManager() {
        UUID unknown = UUID.randomUUID();
        when(clientRepository.findByManagerId(eq(unknown), any(Sort.class))).thenReturn(List.of());

        assertThat(clientService.listClients(unknown)).isEmpty();
    }

    private Client stored(String firstName, String lastName, String... companyNames) {
        Client client = new Client();
        client.setId(UUID.randomUUID());
        client.setFirstName(firstName);
        client.setLastName(lastName);
        client.setPhone("+91 98200 12345");
        client.setEmail(firstName.toLowerCase() + "@example.com");
        client.setManagerId(MANAGER_ID);
        client.setDiscountPercent(Client.DEFAULT_DISCOUNT_PERCENT);
        for (String companyName : companyNames) {
            ClientCompany company = new ClientCompany();
            company.setId(UUID.randomUUID());
            company.setCompanyName(companyName);
            company.setCompanyType(CompanyType.PRIVATE_LIMITED);
            company.setAddress("12 MG Road, Bengaluru");
            client.addCompany(company);
        }
        return client;
    }

    private ClientRequest.ClientRequestBuilder request() {
        return ClientRequest.builder()
                .firstName("Rohan")
                .lastName("Mehta")
                .phone("+91 98200 12345")
                .email("rohan.mehta@example.com")
                .managerId(MANAGER_ID)
                .companies(List.of(
                        NewCompanyRequest.builder()
                                .companyName("Rohan Mehta HUF")
                                .companyType(CompanyType.HUF)
                                .address("4 Park Street, Kolkata, West Bengal 700016")
                                .build(),
                        NewCompanyRequest.builder()
                                .companyName("Mehta Textiles Pvt Ltd")
                                .companyType(CompanyType.PRIVATE_LIMITED)
                                .address("12 MG Road, Bengaluru, Karnataka 560001")
                                .build()));
    }

    private ClientUpdateRequest.ClientUpdateRequestBuilder updateRequest() {
        return ClientUpdateRequest.builder()
                .firstName("Rohan")
                .lastName("Mehta")
                .phone("+91 98200 12345")
                .email("rohan.mehta@example.com")
                .managerId(MANAGER_ID);
    }

    private NewCompanyRequest company(String name, BigDecimal discountPercent) {
        return NewCompanyRequest.builder()
                .companyName(name)
                .companyType(CompanyType.PRIVATE_LIMITED)
                .address("12 MG Road, Bengaluru, Karnataka 560001")
                .discountPercent(discountPercent)
                .build();
    }

    private Manager manager() {
        return manager(MANAGER_ID);
    }

    private Manager manager(UUID id) {
        Manager manager = new Manager();
        manager.setId(id);
        manager.setFirstName("Pranay");
        manager.setLastName("Singhal");
        return manager;
    }

    private void stubSave() {
        when(clientRepository.save(any(Client.class))).thenAnswer(invocation -> {
            Client toSave = invocation.getArgument(0);
            toSave.setId(UUID.randomUUID());
            toSave.getCompanies().forEach(company -> company.setId(UUID.randomUUID()));
            return toSave;
        });
    }
}
