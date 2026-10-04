package com.psc.cl.client.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.psc.cl.client.dto.ClientRequest;
import com.psc.cl.client.dto.ClientResponse;
import com.psc.cl.client.dto.ClientSummaryResponse;
import com.psc.cl.client.dto.ClientUpdateRequest;
import com.psc.cl.client.dto.CompanyRequest;
import com.psc.cl.client.dto.CompanyResponse;
import com.psc.cl.client.dto.CompanyUpdateRequest;
import com.psc.cl.client.exception.AssignedManagerNotFoundException;
import com.psc.cl.client.exception.ClientHasServicesException;
import com.psc.cl.client.exception.ClientNotFoundException;
import com.psc.cl.client.exception.CompanyHasServicesException;
import com.psc.cl.client.exception.CompanyNotFoundException;
import com.psc.cl.client.exception.LastCompanyRemovalException;
import com.psc.cl.client.model.Client;
import com.psc.cl.client.model.ClientCompany;
import com.psc.cl.client.model.CompanyType;
import com.psc.cl.client.repository.ClientCompanyRepository;
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
    private ClientCompanyRepository clientCompanyRepository;

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
    @DisplayName("defaults the client discount to zero and gives it to every company when none is set")
    void defaultsDiscountToZero() {
        when(managerRepository.findById(MANAGER_ID)).thenReturn(Optional.of(manager()));
        stubSave();

        ClientResponse response = clientService.createClient(request().build());

        verify(clientRepository).save(clientCaptor.capture());
        Client saved = clientCaptor.getValue();
        assertThat(saved.getDiscountPercent()).isEqualTo(new BigDecimal("0.00"));
        assertThat(saved.getCompanies())
                .allSatisfy(company -> assertThat(company.getDiscountPercent()).isEqualTo(new BigDecimal("0.00")));
        assertThat(response.getDiscountPercent()).isEqualTo(new BigDecimal("0.00"));
    }

    @Test
    @DisplayName("copies the client discount onto companies that do not set their own")
    void companiesInheritClientDiscount() {
        when(managerRepository.findById(MANAGER_ID)).thenReturn(Optional.of(manager()));
        stubSave();

        clientService.createClient(request().discountPercent(new BigDecimal("10")).build());

        verify(clientRepository).save(clientCaptor.capture());
        Client saved = clientCaptor.getValue();
        assertThat(saved.getDiscountPercent()).isEqualTo(new BigDecimal("10.00"));
        assertThat(saved.getCompanies())
                .allSatisfy(company -> assertThat(company.getDiscountPercent()).isEqualTo(new BigDecimal("10.00")));
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
        assertThat(companies.get(2).getDiscountPercent()).isEqualTo(new BigDecimal("10.00"));
    }

    @Test
    @DisplayName("on update, keeps listed companies in place, adds new ones and removes the rest")
    void updatesClientAndReconcilesCompanies() {
        Client stored = stored("Rohan", "Mehta", "Rohan Mehta HUF", "Mehta Textiles Pvt Ltd");
        ClientCompany kept = stored.getCompanies().get(0);
        ClientCompany dropped = stored.getCompanies().get(1);
        when(clientRepository.findById(stored.getId())).thenReturn(Optional.of(stored));
        when(managerRepository.findById(MANAGER_ID)).thenReturn(Optional.of(manager()));

        ClientResponse response = clientService.updateClient(stored.getId(), updateRequest()
                .firstName("  Rohit  ")
                .companies(List.of(
                        updateCompany(kept.getId(), "Rohan Mehta HUF (Renamed)", null),
                        updateCompany(null, "Mehta Exports LLP", null)))
                .build());

        verify(clientRepository).flush();
        assertThat(stored.getFirstName()).isEqualTo("Rohit");
        assertThat(stored.getCompanies()).hasSize(2).doesNotContain(dropped);
        assertThat(stored.getCompanies().get(0)).isSameAs(kept);
        assertThat(kept.getCompanyName()).isEqualTo("Rohan Mehta HUF (Renamed)");
        ClientCompany added = stored.getCompanies().get(1);
        assertThat(added.getCompanyName()).isEqualTo("Mehta Exports LLP");
        assertThat(added.getClient()).isSameAs(stored);
        assertThat(stored.getUpdatedAt()).isNotNull();
        assertThat(response.getFirstName()).isEqualTo("Rohit");
        assertThat(response.getManagerName()).isEqualTo("Pranay Singhal");
    }

    @Test
    @DisplayName("on update, companies without their own discount take the new client discount")
    void updateAppliesClientDiscountToCompanies() {
        Client stored = stored("Rohan", "Mehta", "Rohan Mehta HUF", "Mehta Textiles Pvt Ltd");
        UUID firstId = stored.getCompanies().get(0).getId();
        UUID secondId = stored.getCompanies().get(1).getId();
        when(clientRepository.findById(stored.getId())).thenReturn(Optional.of(stored));
        when(managerRepository.findById(MANAGER_ID)).thenReturn(Optional.of(manager()));

        clientService.updateClient(stored.getId(), updateRequest()
                .discountPercent(new BigDecimal("12.5"))
                .companies(List.of(
                        updateCompany(firstId, "Rohan Mehta HUF", null),
                        updateCompany(secondId, "Mehta Textiles Pvt Ltd", new BigDecimal("3"))))
                .build());

        assertThat(stored.getDiscountPercent()).isEqualTo(new BigDecimal("12.50"));
        assertThat(stored.getCompanies().get(0).getDiscountPercent()).isEqualTo(new BigDecimal("12.50"));
        assertThat(stored.getCompanies().get(1).getDiscountPercent()).isEqualTo(new BigDecimal("3.00"));
    }

    @Test
    @DisplayName("on update, an omitted client discount resets to zero")
    void updateResetsOmittedDiscount() {
        Client stored = stored("Rohan", "Mehta", "Rohan Mehta HUF");
        stored.setDiscountPercent(new BigDecimal("10.00"));
        UUID companyId = stored.getCompanies().getFirst().getId();
        when(clientRepository.findById(stored.getId())).thenReturn(Optional.of(stored));
        when(managerRepository.findById(MANAGER_ID)).thenReturn(Optional.of(manager()));

        clientService.updateClient(stored.getId(), updateRequest()
                .companies(List.of(updateCompany(companyId, "Rohan Mehta HUF", null)))
                .build());

        assertThat(stored.getDiscountPercent()).isEqualTo(new BigDecimal("0.00"));
        assertThat(stored.getCompanies().getFirst().getDiscountPercent()).isEqualTo(new BigDecimal("0.00"));
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
    @DisplayName("rejects a company id the client does not hold and leaves the client untouched")
    void updateRejectsForeignCompanyId() {
        Client stored = stored("Rohan", "Mehta", "Rohan Mehta HUF");
        UUID foreign = UUID.randomUUID();
        when(clientRepository.findById(stored.getId())).thenReturn(Optional.of(stored));
        when(managerRepository.findById(MANAGER_ID)).thenReturn(Optional.of(manager()));

        assertThatThrownBy(() -> clientService.updateClient(stored.getId(), updateRequest()
                .firstName("Rohit")
                .companies(List.of(updateCompany(foreign, "Someone Else's Company", null)))
                .build()))
                .isInstanceOf(CompanyNotFoundException.class)
                .hasMessageContaining(foreign.toString());

        assertThat(stored.getFirstName()).isEqualTo("Rohan");
        assertThat(stored.getCompanies()).hasSize(1);
        verify(clientRepository, never()).flush();
    }

    @Test
    @DisplayName("refuses an update that would remove a company with services, and changes nothing")
    void updateRejectsRemovingCompanyWithServices() {
        Client stored = stored("Rohan", "Mehta", "Rohan Mehta HUF", "Mehta Textiles Pvt Ltd");
        ClientCompany kept = stored.getCompanies().get(0);
        ClientCompany dropped = stored.getCompanies().get(1);
        when(clientRepository.findById(stored.getId())).thenReturn(Optional.of(stored));
        when(managerRepository.findById(MANAGER_ID)).thenReturn(Optional.of(manager()));
        when(serviceAssignmentRepository.existsByCompanyIdIn(Set.of(dropped.getId()))).thenReturn(true);

        assertThatThrownBy(() -> clientService.updateClient(stored.getId(), updateRequest()
                .firstName("Rohit")
                .companies(List.of(updateCompany(kept.getId(), "Rohan Mehta HUF", null)))
                .build()))
                .isInstanceOf(CompanyHasServicesException.class);

        assertThat(stored.getFirstName()).isEqualTo("Rohan");
        assertThat(stored.getCompanies()).hasSize(2);
        verify(clientRepository, never()).flush();
    }

    @Test
    @DisplayName("does not look for services when the update removes no company")
    void updateSkipsServiceCheckWhenNothingRemoved() {
        Client stored = stored("Rohan", "Mehta", "Rohan Mehta HUF");
        UUID companyId = stored.getCompanies().getFirst().getId();
        when(clientRepository.findById(stored.getId())).thenReturn(Optional.of(stored));
        when(managerRepository.findById(MANAGER_ID)).thenReturn(Optional.of(manager()));

        clientService.updateClient(stored.getId(), updateRequest()
                .companies(List.of(updateCompany(companyId, "Rohan Mehta HUF", null)))
                .build());

        verify(serviceAssignmentRepository, never()).existsByCompanyIdIn(any());
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
    @DisplayName("deletes one company of a client, under a lock on the client")
    void deletesCompany() {
        Client stored = stored("Rohan", "Mehta", "Rohan Mehta HUF", "Mehta Textiles Pvt Ltd");
        ClientCompany doomed = stored.getCompanies().get(1);
        when(clientRepository.findByIdForUpdate(stored.getId())).thenReturn(Optional.of(stored));
        when(serviceAssignmentRepository.existsByCompanyIdIn(Set.of(doomed.getId()))).thenReturn(false);

        clientService.deleteCompany(stored.getId(), doomed.getId());

        assertThat(stored.getCompanies()).hasSize(1).doesNotContain(doomed);
        assertThat(stored.getUpdatedAt()).isNotNull();
        verify(clientRepository).flush();
        verify(clientRepository, never()).findById(any());
    }

    @Test
    @DisplayName("refuses to delete a client's only company")
    void deleteCompanyRejectsLastCompany() {
        Client stored = stored("Rohan", "Mehta", "Rohan Mehta HUF");
        UUID onlyId = stored.getCompanies().getFirst().getId();
        when(clientRepository.findByIdForUpdate(stored.getId())).thenReturn(Optional.of(stored));

        assertThatThrownBy(() -> clientService.deleteCompany(stored.getId(), onlyId))
                .isInstanceOf(LastCompanyRemovalException.class)
                .hasMessageContaining(onlyId.toString());

        assertThat(stored.getCompanies()).hasSize(1);
        verify(clientRepository, never()).flush();
    }

    @Test
    @DisplayName("refuses to delete a company that has services")
    void deleteCompanyRejectsCompanyWithServices() {
        Client stored = stored("Rohan", "Mehta", "Rohan Mehta HUF", "Mehta Textiles Pvt Ltd");
        ClientCompany busy = stored.getCompanies().get(0);
        when(clientRepository.findByIdForUpdate(stored.getId())).thenReturn(Optional.of(stored));
        when(serviceAssignmentRepository.existsByCompanyIdIn(Set.of(busy.getId()))).thenReturn(true);

        assertThatThrownBy(() -> clientService.deleteCompany(stored.getId(), busy.getId()))
                .isInstanceOf(CompanyHasServicesException.class)
                .hasMessageContaining(busy.getId().toString());

        assertThat(stored.getCompanies()).hasSize(2);
        verify(clientRepository, never()).flush();
    }

    @Test
    @DisplayName("raises company not found when the client does not hold the company")
    void deleteCompanyFailsForCompanyOfAnotherClient() {
        Client stored = stored("Rohan", "Mehta", "Rohan Mehta HUF", "Mehta Textiles Pvt Ltd");
        UUID foreign = UUID.randomUUID();
        when(clientRepository.findByIdForUpdate(stored.getId())).thenReturn(Optional.of(stored));

        assertThatThrownBy(() -> clientService.deleteCompany(stored.getId(), foreign))
                .isInstanceOf(CompanyNotFoundException.class);

        assertThat(stored.getCompanies()).hasSize(2);
    }

    @Test
    @DisplayName("raises client not found when deleting a company of an unknown client")
    void deleteCompanyFailsForUnknownClient() {
        UUID missing = UUID.randomUUID();
        when(clientRepository.findByIdForUpdate(missing)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> clientService.deleteCompany(missing, UUID.randomUUID()))
                .isInstanceOf(ClientNotFoundException.class);
    }

    @Test
    @DisplayName("lists a client's companies in full, ordered by name")
    void listsCompanies() {
        Client stored = stored("Rohan", "Mehta", "Rohan Mehta HUF", "Mehta Textiles Pvt Ltd");
        when(clientRepository.findById(stored.getId())).thenReturn(Optional.of(stored));

        List<CompanyResponse> companies = clientService.listCompanies(stored.getId());

        assertThat(companies).extracting("companyName")
                .containsExactly("Mehta Textiles Pvt Ltd", "Rohan Mehta HUF");
        assertThat(companies).allSatisfy(company -> assertThat(company.getAddress()).isNotBlank());
    }

    @Test
    @DisplayName("raises not found when listing companies of an unknown client")
    void listCompaniesFailsForUnknownClient() {
        UUID missing = UUID.randomUUID();
        when(clientRepository.findById(missing)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> clientService.listCompanies(missing))
                .isInstanceOf(ClientNotFoundException.class);
    }

    @Test
    @DisplayName("returns one company of a client")
    void readsCompany() {
        Client stored = stored("Rohan", "Mehta", "Rohan Mehta HUF");
        ClientCompany company = stored.getCompanies().getFirst();
        when(clientRepository.existsById(stored.getId())).thenReturn(true);
        when(clientCompanyRepository.findByIdAndClientId(company.getId(), stored.getId()))
                .thenReturn(Optional.of(company));

        CompanyResponse response = clientService.getCompany(stored.getId(), company.getId());

        assertThat(response.getId()).isEqualTo(company.getId());
        assertThat(response.getCompanyName()).isEqualTo("Rohan Mehta HUF");
    }

    @Test
    @DisplayName("raises client not found when the client is unknown")
    void readCompanyFailsForUnknownClient() {
        UUID missing = UUID.randomUUID();
        when(clientRepository.existsById(missing)).thenReturn(false);

        assertThatThrownBy(() -> clientService.getCompany(missing, UUID.randomUUID()))
                .isInstanceOf(ClientNotFoundException.class);

        verify(clientCompanyRepository, never()).findByIdAndClientId(any(), any());
    }

    @Test
    @DisplayName("raises company not found when the client does not hold the company")
    void readCompanyFailsForCompanyOfAnotherClient() {
        UUID clientId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        when(clientRepository.existsById(clientId)).thenReturn(true);
        when(clientCompanyRepository.findByIdAndClientId(companyId, clientId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> clientService.getCompany(clientId, companyId))
                .isInstanceOf(CompanyNotFoundException.class)
                .hasMessageContaining(companyId.toString());
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

        List<ClientSummaryResponse> clients = clientService.listClients();

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

        assertThat(clientService.listClients()).isEmpty();
    }

    private Client stored(String firstName, String lastName, String... companyNames) {
        Client client = new Client();
        client.setId(UUID.randomUUID());
        client.setFirstName(firstName);
        client.setLastName(lastName);
        client.setPhone("+91 98200 12345");
        client.setEmail(firstName.toLowerCase() + "@example.com");
        client.setManagerId(MANAGER_ID);
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
                .managerId(MANAGER_ID)
                .companies(List.of(updateCompany(null, "Rohan Mehta HUF", null)));
    }

    private CompanyUpdateRequest updateCompany(UUID id, String name, BigDecimal discountPercent) {
        return CompanyUpdateRequest.builder()
                .id(id)
                .companyName(name)
                .companyType(CompanyType.HUF)
                .address("4 Park Street, Kolkata, West Bengal 700016")
                .discountPercent(discountPercent)
                .build();
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
