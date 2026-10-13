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
import com.psc.cl.managercatalog.model.Manager;
import com.psc.cl.managercatalog.model.ManagerRole;
import com.psc.cl.managercatalog.repository.ManagerRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.util.UUID;

class ClientCompanyIT extends AbstractPostgresIT {

    private static final String CLIENTS_URL = ClientApi.BASE_PATH;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private ClientCompanyRepository clientCompanyRepository;

    @Autowired
    private ServiceAssignmentRepository serviceAssignmentRepository;

    @Autowired
    private ManagerRepository managerRepository;

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
                .andExpect(jsonPath("$[0].serviceCount").value(0))
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
