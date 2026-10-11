package com.psc.cl.client.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.psc.cl.client.dto.ClientRequest;
import com.psc.cl.client.dto.CompanyRequest;
import com.psc.cl.client.dto.CompanyResponse;
import com.psc.cl.client.exception.ClientNotFoundException;
import com.psc.cl.client.exception.CompanyHasServicesException;
import com.psc.cl.client.exception.CompanyLimitExceededException;
import com.psc.cl.client.exception.CompanyNotFoundException;
import com.psc.cl.client.exception.LastCompanyRemovalException;
import com.psc.cl.client.model.Client;
import com.psc.cl.client.model.ClientCompany;
import com.psc.cl.client.model.CompanyType;
import com.psc.cl.client.repository.ClientCompanyRepository;
import com.psc.cl.client.repository.ClientRepository;
import com.psc.cl.client.repository.CompanyServiceCount;
import com.psc.cl.client.repository.ServiceAssignmentRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@ExtendWith(MockitoExtension.class)
class ClientCompanyServiceImplTest {

    private static final UUID MANAGER_ID = UUID.fromString("e9b2c4d1-7f35-4a68-91c0-3d8a5e2f6b47");

    @Mock
    private ClientRepository clientRepository;

    @Mock
    private ClientCompanyRepository clientCompanyRepository;

    @Mock
    private ServiceAssignmentRepository serviceAssignmentRepository;

    @InjectMocks
    private ClientCompanyServiceImpl clientCompanyService;

    @Test
    @DisplayName("lists a client's companies by name, with discounts in force and service counts from one query")
    void listsCompanies() {
        Client stored = stored("Rohan", "Mehta", "Rohan Mehta HUF", "Mehta Textiles Pvt Ltd");
        UUID hufId = stored.getCompanies().get(0).getId();
        UUID textilesId = stored.getCompanies().get(1).getId();
        stored.setDiscountPercent(new BigDecimal("10.00"));
        stored.getCompanies().get(1).setDiscountPercent(new BigDecimal("7.50"));
        when(clientRepository.findById(stored.getId())).thenReturn(Optional.of(stored));
        // The HUF has no services, so the query returns no row for it.
        when(serviceAssignmentRepository.countByCompanyIdIn(Set.of(hufId, textilesId)))
                .thenReturn(List.of(serviceCount(textilesId, 3)));

        List<CompanyResponse> companies = clientCompanyService.listCompanies(stored.getId());

        assertThat(companies).extracting("companyName")
                .containsExactly("Mehta Textiles Pvt Ltd", "Rohan Mehta HUF");
        assertThat(companies).extracting("serviceCount").containsExactly(3L, 0L);
        assertThat(companies).extracting("discountPercent")
                .containsExactly(new BigDecimal("7.50"), new BigDecimal("10.00"));
        assertThat(companies).extracting("discountInherited").containsExactly(false, true);
        assertThat(companies).allSatisfy(company -> assertThat(company.getAddress()).isNotBlank());
    }

    @Test
    @DisplayName("raises not found when listing companies of an unknown client")
    void listCompaniesFailsForUnknownClient() {
        UUID missing = UUID.randomUUID();
        when(clientRepository.findById(missing)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> clientCompanyService.listCompanies(missing))
                .isInstanceOf(ClientNotFoundException.class);
    }

    @Test
    @DisplayName("adds a company to a client, under a lock on the client")
    void addsCompany() {
        Client stored = stored("Rohan", "Mehta", "Rohan Mehta HUF");
        when(clientRepository.findByIdForUpdate(stored.getId())).thenReturn(Optional.of(stored));
        UUID generatedId = UUID.randomUUID();
        // Stands in for the flush that has the database assign the new company its id.
        doAnswer(invocation -> {
            stored.getCompanies().stream()
                    .filter(company -> company.getId() == null)
                    .forEach(company -> company.setId(generatedId));
            return null;
        }).when(clientRepository).flush();

        CompanyResponse response = clientCompanyService.addCompany(stored.getId(), CompanyRequest.builder()
                .companyName("  Mehta Exports LLP  ")
                .companyType(CompanyType.LLP)
                .address("  8 Nariman Point, Mumbai  ")
                .build());

        assertThat(stored.getCompanies()).hasSize(2);
        ClientCompany added = stored.getCompanies().get(1);
        assertThat(added.getClient()).isSameAs(stored);
        assertThat(added.getCompanyName()).isEqualTo("Mehta Exports LLP");
        assertThat(added.getAddress()).isEqualTo("8 Nariman Point, Mumbai");
        assertThat(added.getCompanyType()).isEqualTo(CompanyType.LLP);
        assertThat(stored.getUpdatedAt()).isNotNull();
        assertThat(response.getId()).isEqualTo(generatedId);
        assertThat(response.getCompanyName()).isEqualTo("Mehta Exports LLP");
        verify(clientRepository, never()).findById(any());
    }

    @Test
    @DisplayName("an added company without a discount of its own takes the client's, and keeps its own otherwise")
    void addedCompanyDiscount() {
        Client stored = stored("Rohan", "Mehta", "Rohan Mehta HUF");
        stored.setDiscountPercent(new BigDecimal("10.00"));
        when(clientRepository.findByIdForUpdate(stored.getId())).thenReturn(Optional.of(stored));

        CompanyResponse inherited = clientCompanyService.addCompany(stored.getId(), company("Mehta Exports LLP", null));
        CompanyResponse own = clientCompanyService.addCompany(stored.getId(),
                company("Mehta Textiles Pvt Ltd", BigDecimal.ZERO));

        assertThat(inherited.getDiscountPercent()).isEqualTo(new BigDecimal("10.00"));
        assertThat(inherited.getDiscountInherited()).isTrue();
        assertThat(own.getDiscountPercent()).isEqualTo(new BigDecimal("0.00"));
        assertThat(own.getDiscountInherited()).isFalse();
        assertThat(stored.getCompanies().get(1).getDiscountPercent()).isNull();
    }

    @Test
    @DisplayName("refuses to add a company to a client that already holds the most allowed")
    void addCompanyRejectsOverLimit() {
        String[] names = new String[ClientRequest.MAX_COMPANIES];
        for (int i = 0; i < names.length; i++) {
            names[i] = "Company " + i;
        }
        Client stored = stored("Rohan", "Mehta", names);
        when(clientRepository.findByIdForUpdate(stored.getId())).thenReturn(Optional.of(stored));

        assertThatThrownBy(() -> clientCompanyService.addCompany(stored.getId(), company("One Too Many", null)))
                .isInstanceOf(CompanyLimitExceededException.class)
                .hasMessageContaining(stored.getId().toString());

        assertThat(stored.getCompanies()).hasSize(ClientRequest.MAX_COMPANIES);
        verify(clientRepository, never()).flush();
    }

    @Test
    @DisplayName("raises client not found when adding a company to an unknown client")
    void addCompanyFailsForUnknownClient() {
        UUID missing = UUID.randomUUID();
        when(clientRepository.findByIdForUpdate(missing)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> clientCompanyService.addCompany(missing, company("Mehta Exports LLP", null)))
                .isInstanceOf(ClientNotFoundException.class);

        verify(clientRepository, never()).flush();
    }

    @Test
    @DisplayName("returns one company of a client")
    void readsCompany() {
        Client stored = stored("Rohan", "Mehta", "Rohan Mehta HUF");
        ClientCompany company = stored.getCompanies().getFirst();
        when(clientRepository.existsById(stored.getId())).thenReturn(true);
        when(clientCompanyRepository.findByIdAndClientId(company.getId(), stored.getId()))
                .thenReturn(Optional.of(company));

        CompanyResponse response = clientCompanyService.getCompany(stored.getId(), company.getId());

        assertThat(response.getId()).isEqualTo(company.getId());
        assertThat(response.getCompanyName()).isEqualTo("Rohan Mehta HUF");
    }

    @Test
    @DisplayName("raises client not found when the client is unknown")
    void readCompanyFailsForUnknownClient() {
        UUID missing = UUID.randomUUID();
        when(clientRepository.existsById(missing)).thenReturn(false);

        assertThatThrownBy(() -> clientCompanyService.getCompany(missing, UUID.randomUUID()))
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

        assertThatThrownBy(() -> clientCompanyService.getCompany(clientId, companyId))
                .isInstanceOf(CompanyNotFoundException.class)
                .hasMessageContaining(companyId.toString());
    }

    @Test
    @DisplayName("replaces a company of a client in place, keeping its id")
    void updatesCompany() {
        Client stored = stored("Rohan", "Mehta", "Rohan Mehta HUF", "Mehta Textiles Pvt Ltd");
        ClientCompany target = stored.getCompanies().get(1);
        ClientCompany other = stored.getCompanies().get(0);
        UUID targetId = target.getId();
        when(clientRepository.findById(stored.getId())).thenReturn(Optional.of(stored));

        CompanyResponse response = clientCompanyService.updateCompany(stored.getId(), targetId, CompanyRequest.builder()
                .companyName("  Mehta Exports LLP  ")
                .companyType(CompanyType.LLP)
                .address("  8 Nariman Point, Mumbai  ")
                .discountPercent(new BigDecimal("4"))
                .build());

        verify(clientRepository).flush();
        assertThat(stored.getCompanies()).hasSize(2).containsExactly(other, target);
        assertThat(target.getId()).isEqualTo(targetId);
        assertThat(target.getCompanyName()).isEqualTo("Mehta Exports LLP");
        assertThat(target.getCompanyType()).isEqualTo(CompanyType.LLP);
        assertThat(target.getAddress()).isEqualTo("8 Nariman Point, Mumbai");
        assertThat(target.getDiscountPercent()).isEqualTo(new BigDecimal("4.00"));
        assertThat(other.getCompanyName()).isEqualTo("Rohan Mehta HUF");
        assertThat(stored.getUpdatedAt()).isNotNull();
        assertThat(response.getId()).isEqualTo(targetId);
        assertThat(response.getCompanyName()).isEqualTo("Mehta Exports LLP");
    }

    @Test
    @DisplayName("on company update, an omitted discount clears the company's own, so it takes the client's")
    void updateCompanyTakesClientDiscountWhenOmitted() {
        Client stored = stored("Rohan", "Mehta", "Rohan Mehta HUF");
        stored.setDiscountPercent(new BigDecimal("10.00"));
        ClientCompany company = stored.getCompanies().getFirst();
        company.setDiscountPercent(new BigDecimal("3.00"));
        when(clientRepository.findById(stored.getId())).thenReturn(Optional.of(stored));

        CompanyResponse response = clientCompanyService.updateCompany(stored.getId(), company.getId(),
                company("Rohan Mehta HUF", null));

        assertThat(company.getDiscountPercent()).isNull();
        assertThat(response.getDiscountPercent()).isEqualTo(new BigDecimal("10.00"));
        assertThat(response.getDiscountInherited()).isTrue();
    }

    @Test
    @DisplayName("raises company not found when updating a company the client does not hold")
    void updateCompanyFailsForCompanyOfAnotherClient() {
        Client stored = stored("Rohan", "Mehta", "Rohan Mehta HUF");
        UUID foreign = UUID.randomUUID();
        when(clientRepository.findById(stored.getId())).thenReturn(Optional.of(stored));

        assertThatThrownBy(() -> clientCompanyService.updateCompany(stored.getId(), foreign,
                company("Someone Else's Company", null)))
                .isInstanceOf(CompanyNotFoundException.class)
                .hasMessageContaining(foreign.toString());

        assertThat(stored.getCompanies().getFirst().getCompanyName()).isEqualTo("Rohan Mehta HUF");
        verify(clientRepository, never()).flush();
    }

    @Test
    @DisplayName("raises client not found when updating a company of an unknown client")
    void updateCompanyFailsForUnknownClient() {
        UUID missing = UUID.randomUUID();
        when(clientRepository.findById(missing)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> clientCompanyService.updateCompany(missing, UUID.randomUUID(),
                company("Mehta Exports LLP", null)))
                .isInstanceOf(ClientNotFoundException.class);

        verify(clientRepository, never()).flush();
    }

    @Test
    @DisplayName("deletes one company of a client, under a lock on the client")
    void deletesCompany() {
        Client stored = stored("Rohan", "Mehta", "Rohan Mehta HUF", "Mehta Textiles Pvt Ltd");
        ClientCompany doomed = stored.getCompanies().get(1);
        when(clientRepository.findByIdForUpdate(stored.getId())).thenReturn(Optional.of(stored));
        when(serviceAssignmentRepository.existsByCompanyIdIn(Set.of(doomed.getId()))).thenReturn(false);

        clientCompanyService.deleteCompany(stored.getId(), doomed.getId());

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

        assertThatThrownBy(() -> clientCompanyService.deleteCompany(stored.getId(), onlyId))
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

        assertThatThrownBy(() -> clientCompanyService.deleteCompany(stored.getId(), busy.getId()))
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

        assertThatThrownBy(() -> clientCompanyService.deleteCompany(stored.getId(), foreign))
                .isInstanceOf(CompanyNotFoundException.class);

        assertThat(stored.getCompanies()).hasSize(2);
    }

    @Test
    @DisplayName("raises client not found when deleting a company of an unknown client")
    void deleteCompanyFailsForUnknownClient() {
        UUID missing = UUID.randomUUID();
        when(clientRepository.findByIdForUpdate(missing)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> clientCompanyService.deleteCompany(missing, UUID.randomUUID()))
                .isInstanceOf(ClientNotFoundException.class);
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

    private CompanyServiceCount serviceCount(UUID companyId, long count) {
        return new CompanyServiceCount() {
            @Override
            public UUID getCompanyId() {
                return companyId;
            }

            @Override
            public long getServiceCount() {
                return count;
            }
        };
    }

    private CompanyRequest company(String name, BigDecimal discountPercent) {
        return CompanyRequest.builder()
                .companyName(name)
                .companyType(CompanyType.PRIVATE_LIMITED)
                .address("12 MG Road, Bengaluru, Karnataka 560001")
                .discountPercent(discountPercent)
                .build();
    }
}
