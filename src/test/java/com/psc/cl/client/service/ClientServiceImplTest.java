package com.psc.cl.client.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.psc.cl.client.dto.ClientRequest;
import com.psc.cl.client.dto.ClientResponse;
import com.psc.cl.client.dto.ClientSummaryResponse;
import com.psc.cl.client.dto.ClientUpdateRequest;
import com.psc.cl.client.dto.CompanyRequest;
import com.psc.cl.client.exception.AssignedManagerNotFoundException;
import com.psc.cl.client.exception.ClientHasServicesException;
import com.psc.cl.client.exception.ClientNotFoundException;
import com.psc.cl.client.model.Client;
import com.psc.cl.client.model.ClientCompany;
import com.psc.cl.client.model.CompanyType;
import com.psc.cl.client.repository.ClientRepository;
import com.psc.cl.client.repository.ServiceAssignmentRepository;
import com.psc.cl.managercatalog.model.Manager;
import com.psc.cl.managercatalog.repository.ManagerRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.util.List;
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
                .companies(List.of(CompanyRequest.builder()
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
    @DisplayName("maps a foreign key violation from a concurrent assignment onto client has services")
    void deleteClientMapsIntegrityViolation() {
        Client stored = stored("Rohan", "Mehta", "Rohan Mehta HUF");
        when(clientRepository.findById(stored.getId())).thenReturn(Optional.of(stored));
        when(serviceAssignmentRepository.existsByCompanyIdIn(any())).thenReturn(false);
        doThrow(new DataIntegrityViolationException("fk_company_services_company"))
                .when(clientRepository).flush();

        assertThatThrownBy(() -> clientService.deleteClient(stored.getId()))
                .isInstanceOf(ClientHasServicesException.class);
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
    @DisplayName("returns the stored client with companies and manager name on read by id")
    void readsClientById() {
        Client stored = stored("Rohan", "Mehta", "Rohan Mehta HUF", "Mehta Textiles Pvt Ltd");
        when(clientRepository.findById(stored.getId())).thenReturn(Optional.of(stored));
        when(managerRepository.findById(MANAGER_ID)).thenReturn(Optional.of(manager()));

        ClientResponse response = clientService.getClient(stored.getId());

        assertThat(response.getId()).isEqualTo(stored.getId());
        assertThat(response.getManagerName()).isEqualTo("Pranay Singhal");
        assertThat(response.getCompanies())
                .extracting("companyName")
                .containsExactly("Mehta Textiles Pvt Ltd", "Rohan Mehta HUF");
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

        List<ClientSummaryResponse> clients = clientService.listClients(null);

        assertThat(clients).extracting("lastName").containsExactly("Kapoor", "Mehta");
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
                        CompanyRequest.builder()
                                .companyName("Rohan Mehta HUF")
                                .companyType(CompanyType.HUF)
                                .address("4 Park Street, Kolkata, West Bengal 700016")
                                .build(),
                        CompanyRequest.builder()
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

    private CompanyRequest company(String name, BigDecimal discountPercent) {
        return CompanyRequest.builder()
                .companyName(name)
                .companyType(CompanyType.PRIVATE_LIMITED)
                .address("12 MG Road, Bengaluru, Karnataka 560001")
                .discountPercent(discountPercent)
                .build();
    }

    private Manager manager() {
        Manager manager = new Manager();
        manager.setId(MANAGER_ID);
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
