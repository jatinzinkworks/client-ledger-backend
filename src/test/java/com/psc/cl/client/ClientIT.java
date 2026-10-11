package com.psc.cl.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.psc.cl.AbstractPostgresIT;
import com.psc.cl.client.controller.ClientApi;
import com.psc.cl.client.repository.ClientCompanyRepository;
import com.psc.cl.client.repository.ClientRepository;
import com.psc.cl.client.repository.ServiceAssignmentRepository;
import com.psc.cl.managercatalog.controller.ManagerCatalogApi;
import com.psc.cl.managercatalog.model.Manager;
import com.psc.cl.managercatalog.model.ManagerRole;
import com.psc.cl.managercatalog.repository.ManagerRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

class ClientIT extends AbstractPostgresIT {

    private static final String CLIENTS_URL = ClientApi.BASE_PATH;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private ClientCompanyRepository clientCompanyRepository;

    @Autowired
    private ServiceAssignmentRepository serviceAssignmentRepository;

    @Autowired
    private ManagerRepository managerRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private UUID managerId;

    @BeforeEach
    void createManager() {
        clearClientsAndManagers();
        Manager manager = new Manager();
        manager.setFirstName("Pranay");
        manager.setLastName("Singhal");
        manager.setEmail("pranay.singhal@pranaysinghal.in");
        manager.setRole(ManagerRole.SENIOR_MANAGER);
        manager.setMobileNumber("+91 98765 43210");
        managerId = managerRepository.saveAndFlush(manager).getId();
    }

    /**
     * Assigned services reference companies, and clients reference managers, so they are removed in
     * that order. Doing it after each test as well keeps the Manager Catalog tests, which clear
     * managers on their own, free of leftover clients.
     */
    @AfterEach
    void clearClientsAndManagers() {
        serviceAssignmentRepository.deleteAll();
        clientRepository.deleteAll();
        managerRepository.deleteAll();
    }

    @Test
    @DisplayName("creates a client with their companies and stores both")
    void createsClientWithCompanies() throws Exception {
        String created = mockMvc.perform(post(CLIENTS_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(clientJson(managerId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.firstName").value("Rohan"))
                .andExpect(jsonPath("$.managerId").value(managerId.toString()))
                .andExpect(jsonPath("$.managerName").value("Pranay Singhal"))
                .andExpect(jsonPath("$.companies.length()").value(2))
                .andExpect(jsonPath("$.companies[0].companyName").value("Mehta Textiles Pvt Ltd"))
                .andExpect(jsonPath("$.companies[0].id").exists())
                .andExpect(jsonPath("$.companies[1].companyType").value("HUF"))
                .andExpect(jsonPath("$.createdAt").exists())
                .andReturn().getResponse().getContentAsString();
        UUID id = UUID.fromString(JsonPath.read(created, "$.id"));

        transactionTemplate.executeWithoutResult(status -> {
            var stored = clientRepository.findById(id).orElseThrow();
            assertThat(stored.getEmail()).isEqualTo("rohan.mehta@example.com");
            assertThat(stored.getCompanies()).hasSize(2);
        });
    }

    @Test
    @DisplayName("returns 404 and stores nothing when the assigned manager does not exist")
    void rejectsUnknownManager() throws Exception {
        mockMvc.perform(post(CLIENTS_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(clientJson(UUID.randomUUID())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

        assertThat(clientRepository.count()).isZero();
    }

    @Test
    @DisplayName("a request failing validation never reaches the database")
    void invalidRequestIsNotStored() throws Exception {
        mockMvc.perform(post(CLIENTS_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstName": "Rohan",
                                  "lastName": "Mehta",
                                  "phone": "+91 98200 12345",
                                  "email": "rohan.mehta@example.com",
                                  "managerId": "%s",
                                  "companies": []
                                }""".formatted(managerId)))
                .andExpect(status().isBadRequest());

        assertThat(clientRepository.count()).isZero();
    }

    @Test
    @DisplayName("fetches a created client by id with companies in full")
    void fetchesClientById() throws Exception {
        String created = mockMvc.perform(post(CLIENTS_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(clientJson(managerId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(created, "$.id");

        mockMvc.perform(get(CLIENTS_URL + "/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.managerName").value("Pranay Singhal"))
                .andExpect(jsonPath("$.companies.length()").value(2))
                .andExpect(jsonPath("$.companies[0].companyName").value("Mehta Textiles Pvt Ltd"))
                .andExpect(jsonPath("$.companies[0].address").value("12 MG Road, Bengaluru, Karnataka 560001"))
                .andExpect(jsonPath("$.companies[1].companyType").value("HUF"));

        mockMvc.perform(get(CLIENTS_URL + "/" + UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("lists clients by last name then first name, with company names only")
    void listsClients() throws Exception {
        createClient("Rohan", "Mehta", "rohan.mehta@example.com");
        createClient("Asha", "Kapoor", "asha.kapoor@example.com");
        createClient("Anil", "Mehta", "anil.mehta@example.com");

        mockMvc.perform(get(CLIENTS_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].lastName").value("Kapoor"))
                .andExpect(jsonPath("$[1].firstName").value("Anil"))
                .andExpect(jsonPath("$[2].firstName").value("Rohan"))
                .andExpect(jsonPath("$[2].managerName").value("Pranay Singhal"))
                .andExpect(jsonPath("$[2].companyNames.length()").value(2))
                .andExpect(jsonPath("$[2].companyNames[0]").value("Mehta Textiles Pvt Ltd"))
                .andExpect(jsonPath("$[2].companyNames[1]").value("Rohan Mehta HUF"))
                .andExpect(jsonPath("$[2].companies").doesNotExist());
    }

    @Test
    @DisplayName("companies without a discount take the client's, and keep their own otherwise")
    void companiesInheritClientDiscount() throws Exception {
        String created = mockMvc.perform(post(CLIENTS_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstName": "Rohan",
                                  "lastName": "Mehta",
                                  "phone": "+91 98200 12345",
                                  "email": "rohan.mehta@example.com",
                                  "managerId": "%s",
                                  "discountPercent": 10,
                                  "companies": [
                                    {
                                      "companyName": "Mehta Textiles Pvt Ltd",
                                      "companyType": "PRIVATE_LIMITED",
                                      "address": "12 MG Road, Bengaluru, Karnataka 560001",
                                      "discountPercent": 2.5
                                    },
                                    {
                                      "companyName": "Rohan Mehta HUF",
                                      "companyType": "HUF",
                                      "address": "4 Park Street, Kolkata, West Bengal 700016"
                                    }
                                  ]
                                }""".formatted(managerId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.discountPercent").value(10.0))
                .andExpect(jsonPath("$.companies[0].discountPercent").value(2.5))
                .andExpect(jsonPath("$.companies[1].discountPercent").value(10.0))
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(created, "$.id");

        // Read back from the database, so the copied value is shown to be stored, not derived.
        mockMvc.perform(get(CLIENTS_URL + "/" + id + "/companies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].discountPercent").value(2.5))
                .andExpect(jsonPath("$[1].discountPercent").value(10.0));
    }

    @Test
    @DisplayName("a client without a discount is stored with zero, as are their companies")
    void discountDefaultsToZero() throws Exception {
        mockMvc.perform(post(CLIENTS_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(clientJson(managerId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.discountPercent").value(0.0))
                .andExpect(jsonPath("$.companies[0].discountPercent").value(0.0))
                .andExpect(jsonPath("$.companies[1].discountPercent").value(0.0));
    }

    @Test
    @DisplayName("lists the companies of a client, and fetches one of them by id")
    void listsAndFetchesCompanies() throws Exception {
        String created = mockMvc.perform(post(CLIENTS_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(clientJson(managerId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(created, "$.id");
        String companyId = JsonPath.read(created, "$.companies[1].id");

        mockMvc.perform(get(CLIENTS_URL + "/" + id + "/companies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].companyName").value("Mehta Textiles Pvt Ltd"))
                .andExpect(jsonPath("$[1].id").value(companyId));

        mockMvc.perform(get(CLIENTS_URL + "/" + id + "/companies/" + companyId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(companyId))
                .andExpect(jsonPath("$.companyName").value("Rohan Mehta HUF"))
                .andExpect(jsonPath("$.companyType").value("HUF"));

        mockMvc.perform(get(CLIENTS_URL + "/" + UUID.randomUUID() + "/companies"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("a company cannot be fetched under a client that does not hold it")
    void companyOfAnotherClientIsNotFound() throws Exception {
        String first = mockMvc.perform(post(CLIENTS_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(clientJson(managerId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String companyOfFirst = JsonPath.read(first, "$.companies[0].id");

        String second = mockMvc.perform(post(CLIENTS_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(clientJson("Asha", "Kapoor", "asha.kapoor@example.com", managerId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String secondId = JsonPath.read(second, "$.id");

        mockMvc.perform(get(CLIENTS_URL + "/" + secondId + "/companies/" + companyOfFirst))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(containsString(companyOfFirst)));

        mockMvc.perform(get(CLIENTS_URL + "/" + UUID.randomUUID() + "/companies/" + companyOfFirst))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("update replaces the client's fields, stores them and leaves the companies as they were")
    void updatesClientWithoutTouchingCompanies() throws Exception {
        String created = mockMvc.perform(post(CLIENTS_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(clientJson(managerId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(created, "$.id");
        // Ordered by name: [0] is Mehta Textiles Pvt Ltd, [1] is Rohan Mehta HUF.
        String textilesId = JsonPath.read(created, "$.companies[0].id");
        String hufId = JsonPath.read(created, "$.companies[1].id");

        mockMvc.perform(put(CLIENTS_URL + "/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstName": "Rohit",
                                  "lastName": "Mehta",
                                  "phone": "+91 98200 99999",
                                  "email": "rohit.mehta@example.com",
                                  "managerId": "%s",
                                  "discountPercent": 7.5
                                }""".formatted(managerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.firstName").value("Rohit"))
                .andExpect(jsonPath("$.discountPercent").value(7.5))
                .andExpect(jsonPath("$.companies.length()").value(2))
                .andExpect(jsonPath("$.companies[0].id").value(textilesId))
                .andExpect(jsonPath("$.companies[1].id").value(hufId))
                // Companies keep the discount they were given when added.
                .andExpect(jsonPath("$.companies[0].discountPercent").value(0.0));

        // Read back from the database.
        mockMvc.perform(get(CLIENTS_URL + "/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phone").value("+91 98200 99999"))
                .andExpect(jsonPath("$.email").value("rohit.mehta@example.com"))
                .andExpect(jsonPath("$.companies.length()").value(2))
                .andExpect(jsonPath("$.companies[1].companyName").value("Rohan Mehta HUF"));
    }

    @Test
    @DisplayName("adds a company to an existing client, taking the client's discount, and stores it")
    void addsCompanyToClient() throws Exception {
        String created = mockMvc.perform(post(CLIENTS_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(clientJson(managerId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(created, "$.id");

        String added = mockMvc.perform(post(CLIENTS_URL + "/" + id + "/companies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "companyName": "  Mehta Exports LLP  ",
                                  "companyType": "LLP",
                                  "address": "8 Nariman Point, Mumbai, Maharashtra 400021"
                                }"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.companyName").value("Mehta Exports LLP"))
                .andExpect(jsonPath("$.companyType").value("LLP"))
                .andExpect(jsonPath("$.discountPercent").value(0.0))
                .andReturn().getResponse().getContentAsString();
        String addedId = JsonPath.read(added, "$.id");

        // Read back from the database.
        mockMvc.perform(get(CLIENTS_URL + "/" + id + "/companies/" + addedId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.companyName").value("Mehta Exports LLP"));
        mockMvc.perform(get(CLIENTS_URL + "/" + id))
                .andExpect(jsonPath("$.companies.length()").value(3));
        assertThat(clientCompanyRepository.count()).isEqualTo(3);
    }

    @Test
    @DisplayName("adding a company to an unknown client, or with an invalid body, stores nothing")
    void addCompanyRejectsUnknownClientAndInvalidBody() throws Exception {
        String created = mockMvc.perform(post(CLIENTS_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(clientJson(managerId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(created, "$.id");
        String company = """
                {
                  "companyName": "Mehta Exports LLP",
                  "companyType": "LLP",
                  "address": "8 Nariman Point, Mumbai, Maharashtra 400021"
                }""";

        mockMvc.perform(post(CLIENTS_URL + "/" + UUID.randomUUID() + "/companies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(company))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

        mockMvc.perform(post(CLIENTS_URL + "/" + id + "/companies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"companyName\": \"Mehta Exports LLP\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        assertThat(clientCompanyRepository.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("updates a company of a client in place and stores it, but not under another client")
    void updatesCompanyOfClient() throws Exception {
        String first = mockMvc.perform(post(CLIENTS_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(clientJson(managerId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(first, "$.id");
        // Ordered by name: [1] is Rohan Mehta HUF.
        String hufId = JsonPath.read(first, "$.companies[1].id");

        mockMvc.perform(put(CLIENTS_URL + "/" + id + "/companies/" + hufId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "companyName": "  Rohit Mehta HUF  ",
                                  "companyType": "HUF",
                                  "address": "9 Park Street, Kolkata, West Bengal 700016",
                                  "discountPercent": 6.5
                                }"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(hufId))
                .andExpect(jsonPath("$.companyName").value("Rohit Mehta HUF"))
                .andExpect(jsonPath("$.discountPercent").value(6.5));

        // Read back from the database.
        mockMvc.perform(get(CLIENTS_URL + "/" + id + "/companies/" + hufId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.companyName").value("Rohit Mehta HUF"))
                .andExpect(jsonPath("$.address").value("9 Park Street, Kolkata, West Bengal 700016"));
        assertThat(clientCompanyRepository.count()).isEqualTo(2);

        String second = mockMvc.perform(post(CLIENTS_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(clientJson("Asha", "Kapoor", "asha.kapoor@example.com", managerId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String secondId = JsonPath.read(second, "$.id");

        mockMvc.perform(put(CLIENTS_URL + "/" + secondId + "/companies/" + hufId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "companyName": "Hijacked",
                                  "companyType": "HUF",
                                  "address": "Nowhere"
                                }"""))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        mockMvc.perform(get(CLIENTS_URL + "/" + id + "/companies/" + hufId))
                .andExpect(jsonPath("$.companyName").value("Rohit Mehta HUF"));
    }

    @Test
    @DisplayName("filters the client list by manager")
    void filtersClientsByManager() throws Exception {
        Manager other = new Manager();
        other.setFirstName("Aarti");
        other.setLastName("Desai");
        other.setEmail("aarti.desai@pranaysinghal.in");
        other.setRole(ManagerRole.MANAGER);
        other.setMobileNumber("+91 99887 66554");
        UUID otherManagerId = managerRepository.saveAndFlush(other).getId();

        createClient("Rohan", "Mehta", "rohan.mehta@example.com");
        createClient("Anil", "Mehta", "anil.mehta@example.com");
        mockMvc.perform(post(CLIENTS_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(clientJson("Asha", "Kapoor", "asha.kapoor@example.com", otherManagerId)))
                .andExpect(status().isCreated());

        mockMvc.perform(get(CLIENTS_URL).param("managerId", managerId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].firstName").value("Anil"))
                .andExpect(jsonPath("$[1].firstName").value("Rohan"))
                .andExpect(jsonPath("$[0].managerName").value("Pranay Singhal"))
                .andExpect(jsonPath("$[0].companyNames.length()").value(2));

        mockMvc.perform(get(CLIENTS_URL).param("managerId", otherManagerId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].lastName").value("Kapoor"))
                .andExpect(jsonPath("$[0].managerName").value("Aarti Desai"));

        mockMvc.perform(get(CLIENTS_URL).param("managerId", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(get(CLIENTS_URL))
                .andExpect(jsonPath("$.length()").value(3));
    }

    @Test
    @DisplayName("deleting a client removes their companies too")
    void deletesClientWithCompanies() throws Exception {
        String created = mockMvc.perform(post(CLIENTS_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(clientJson(managerId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(created, "$.id");
        assertThat(clientCompanyRepository.count()).isEqualTo(2);

        mockMvc.perform(delete(CLIENTS_URL + "/" + id))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(CLIENTS_URL + "/" + id))
                .andExpect(status().isNotFound());
        assertThat(clientRepository.count()).isZero();
        assertThat(clientCompanyRepository.count()).isZero();

        mockMvc.perform(delete(CLIENTS_URL + "/" + id))
                .andExpect(status().isNotFound());
        // With no clients left, their manager can be deleted.
        mockMvc.perform(delete(ManagerCatalogApi.BASE_PATH + "/" + managerId))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("deletes a company of a client, but never the client's only company")
    void deletesCompanyButNotTheLastOne() throws Exception {
        String created = mockMvc.perform(post(CLIENTS_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(clientJson(managerId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(created, "$.id");
        String textilesId = JsonPath.read(created, "$.companies[0].id");
        String hufId = JsonPath.read(created, "$.companies[1].id");

        mockMvc.perform(delete(CLIENTS_URL + "/" + id + "/companies/" + textilesId))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(CLIENTS_URL + "/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.companies.length()").value(1))
                .andExpect(jsonPath("$.companies[0].id").value(hufId));
        mockMvc.perform(get(CLIENTS_URL + "/" + id + "/companies/" + textilesId))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete(CLIENTS_URL + "/" + id + "/companies/" + hufId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"));
        assertThat(clientCompanyRepository.count()).isOne();
    }

    @Test
    @DisplayName("a company cannot be deleted under a client that does not hold it")
    void cannotDeleteAnotherClientsCompany() throws Exception {
        String first = mockMvc.perform(post(CLIENTS_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(clientJson(managerId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String companyOfFirst = JsonPath.read(first, "$.companies[0].id");

        String second = mockMvc.perform(post(CLIENTS_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(clientJson("Asha", "Kapoor", "asha.kapoor@example.com", managerId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String secondId = JsonPath.read(second, "$.id");

        mockMvc.perform(delete(CLIENTS_URL + "/" + secondId + "/companies/" + companyOfFirst))
                .andExpect(status().isNotFound());

        assertThat(clientCompanyRepository.count()).isEqualTo(4);
    }

    @Test
    @DisplayName("a manager with clients cannot be deleted, and can be once the clients are gone")
    void managerWithClientsCannotBeDeleted() throws Exception {
        mockMvc.perform(post(CLIENTS_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(clientJson(managerId)))
                .andExpect(status().isCreated());

        mockMvc.perform(delete(ManagerCatalogApi.BASE_PATH + "/" + managerId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESOURCE_IN_USE"))
                .andExpect(jsonPath("$.message").value(containsString("1 client(s)")));

        assertThat(managerRepository.existsById(managerId)).isTrue();

        clientRepository.deleteAll();

        mockMvc.perform(delete(ManagerCatalogApi.BASE_PATH + "/" + managerId))
                .andExpect(status().isNoContent());
    }

    private void createClient(String firstName, String lastName, String email) throws Exception {
        mockMvc.perform(post(CLIENTS_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(clientJson(firstName, lastName, email, managerId)))
                .andExpect(status().isCreated());
    }

    private String clientJson(UUID manager) {
        return clientJson("Rohan", "Mehta", "rohan.mehta@example.com", manager);
    }

    private String clientJson(String firstName, String lastName, String email, UUID manager) {
        return """
                {
                  "firstName": "%s",
                  "lastName": "%s",
                  "phone": "+91 98200 12345",
                  "email": "%s",
                  "managerId": "%s",
                  "companies": [
                    {
                      "companyName": "Rohan Mehta HUF",
                      "companyType": "HUF",
                      "address": "4 Park Street, Kolkata, West Bengal 700016"
                    },
                    {
                      "companyName": "Mehta Textiles Pvt Ltd",
                      "companyType": "PRIVATE_LIMITED",
                      "address": "12 MG Road, Bengaluru, Karnataka 560001"
                    }
                  ]
                }""".formatted(firstName, lastName, email, manager);
    }
}
