package com.psc.cl.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.psc.cl.AbstractPostgresIT;
import com.psc.cl.client.controller.ClientApi;
import com.psc.cl.client.repository.ClientRepository;
import com.psc.cl.client.repository.ServiceAssignmentRepository;
import com.psc.cl.managercatalog.model.Manager;
import com.psc.cl.managercatalog.model.ManagerRole;
import com.psc.cl.managercatalog.repository.ManagerRepository;
import com.psc.cl.servicecatalog.controller.ServiceCatalogApi;
import com.psc.cl.servicecatalog.repository.CatalogServiceRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.util.UUID;

class ServiceAssignmentIT extends AbstractPostgresIT {

    @Autowired
    private ServiceAssignmentRepository serviceAssignmentRepository;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private ManagerRepository managerRepository;

    @Autowired
    private CatalogServiceRepository catalogServiceRepository;

    private String clientId;

    /** Ordered by name: Mehta Textiles Pvt Ltd, with a 7.5% discount of its own. */
    private String textilesId;

    /** Ordered by name: Rohan Mehta HUF, which takes the client's 10% discount. */
    private String hufId;

    private String serviceId;

    @BeforeEach
    void setUp() throws Exception {
        clearAll();
        UUID managerId = createManager();
        serviceId = createCatalogService("GST Monthly Return Filing");

        String created = mockMvc.perform(post(ClientApi.BASE_PATH)
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
                                      "companyName": "Rohan Mehta HUF",
                                      "companyType": "HUF",
                                      "address": "4 Park Street, Kolkata, West Bengal 700016"
                                    },
                                    {
                                      "companyName": "Mehta Textiles Pvt Ltd",
                                      "companyType": "PRIVATE_LIMITED",
                                      "address": "12 MG Road, Bengaluru, Karnataka 560001",
                                      "discountPercent": 7.5
                                    }
                                  ]
                                }""".formatted(managerId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        clientId = JsonPath.read(created, "$.id");
        textilesId = JsonPath.read(created, "$.companies[0].id");
        hufId = JsonPath.read(created, "$.companies[1].id");
    }

    /** Removed in dependency order: assignments, then clients, managers and catalog services. */
    @AfterEach
    void clearAll() {
        serviceAssignmentRepository.deleteAll();
        clientRepository.deleteAll();
        managerRepository.deleteAll();
        catalogServiceRepository.deleteAll();
    }

    @Test
    @DisplayName("assigns a service to a company and stores it")
    void assignsService() throws Exception {
        String assigned = mockMvc.perform(post(servicesUrl(textilesId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "serviceId": "%s",
                                  "deliveryDate": "2026-10-15",
                                  "discountPercent": 5,
                                  "comment": "Filing for September 2026"
                                }""".formatted(serviceId)))
                .andExpect(status().isCreated())
                .andExpect(header().exists(HttpHeaders.LOCATION))
                .andExpect(jsonPath("$.clientId").value(clientId))
                .andExpect(jsonPath("$.companyId").value(textilesId))
                .andExpect(jsonPath("$.serviceId").value(serviceId))
                .andExpect(jsonPath("$.serviceName").value("GST Monthly Return Filing"))
                .andExpect(jsonPath("$.deliveryDate").value("2026-10-15"))
                .andExpect(jsonPath("$.discountPercent").value(5.0))
                .andExpect(jsonPath("$.comment").value("Filing for September 2026"))
                .andReturn().getResponse().getContentAsString();

        UUID assignmentId = UUID.fromString(JsonPath.read(assigned, "$.id"));
        assertThat(serviceAssignmentRepository.existsById(assignmentId)).isTrue();
    }

    @Test
    @DisplayName("a service without a discount takes the company's, inherited or its own")
    void serviceInheritsCompanyDiscount() throws Exception {
        mockMvc.perform(post(servicesUrl(textilesId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(minimalJson(serviceId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.discountPercent").value(7.5))
                .andExpect(jsonPath("$.comment").doesNotExist());

        mockMvc.perform(post(servicesUrl(hufId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(minimalJson(serviceId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.discountPercent").value(10.0));
    }

    @Test
    @DisplayName("counts each company once towards the catalog's subscriber count")
    void maintainsUsedByCompanies() throws Exception {
        mockMvc.perform(post(servicesUrl(textilesId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(minimalJson(serviceId)))
                .andExpect(status().isCreated());
        // The same company taking the same service again, for another month.
        mockMvc.perform(post(servicesUrl(textilesId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "serviceId": "%s", "deliveryDate": "2026-11-15" }""".formatted(serviceId)))
                .andExpect(status().isCreated());

        mockMvc.perform(get(ServiceCatalogApi.BASE_PATH + "/" + serviceId))
                .andExpect(jsonPath("$.usedByCompanies").value(1));

        mockMvc.perform(post(servicesUrl(hufId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(minimalJson(serviceId)))
                .andExpect(status().isCreated());

        mockMvc.perform(get(ServiceCatalogApi.BASE_PATH + "/" + serviceId))
                .andExpect(jsonPath("$.usedByCompanies").value(2));
        assertThat(serviceAssignmentRepository.count()).isEqualTo(3);
    }

    @Test
    @DisplayName("editing a catalog service does not reset its subscriber count")
    void catalogUpdateKeepsUsedByCompanies() throws Exception {
        mockMvc.perform(post(servicesUrl(textilesId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(minimalJson(serviceId)))
                .andExpect(status().isCreated());

        mockMvc.perform(put(ServiceCatalogApi.BASE_PATH + "/" + serviceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(catalogServiceJson("GST Monthly Return Filing (Revised)")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usedByCompanies").value(1));
    }

    @Test
    @DisplayName("a catalog service taken by a company cannot be deleted")
    void assignedCatalogServiceCannotBeDeleted() throws Exception {
        mockMvc.perform(post(servicesUrl(textilesId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(minimalJson(serviceId)))
                .andExpect(status().isCreated());

        mockMvc.perform(delete(ServiceCatalogApi.BASE_PATH + "/" + serviceId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESOURCE_IN_USE"));
    }

    @Test
    @DisplayName("returns 404 for an unknown service, client, or a company of another client")
    void rejectsUnknownReferences() throws Exception {
        mockMvc.perform(post(servicesUrl(textilesId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(minimalJson(UUID.randomUUID().toString())))
                .andExpect(status().isNotFound());

        mockMvc.perform(post(ClientApi.BASE_PATH + "/" + UUID.randomUUID() + "/companies/" + textilesId
                        + "/services")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(minimalJson(serviceId)))
                .andExpect(status().isNotFound());

        mockMvc.perform(post(servicesUrl(UUID.randomUUID().toString()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(minimalJson(serviceId)))
                .andExpect(status().isNotFound());

        assertThat(serviceAssignmentRepository.count()).isZero();
        mockMvc.perform(get(ServiceCatalogApi.BASE_PATH + "/" + serviceId))
                .andExpect(jsonPath("$.usedByCompanies").value(0));
    }

    @Test
    @DisplayName("lists a company's services most recent delivery first, and fetches one by id")
    void listsAndFetchesServices() throws Exception {
        String september = assign(textilesId, "2026-09-15");
        String november = assign(textilesId, "2026-11-15");
        String october = assign(textilesId, "2026-10-15");
        assign(hufId, "2026-10-15");

        mockMvc.perform(get(servicesUrl(textilesId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].id").value(november))
                .andExpect(jsonPath("$[1].id").value(october))
                .andExpect(jsonPath("$[2].id").value(september))
                .andExpect(jsonPath("$[0].serviceName").value("GST Monthly Return Filing"));

        mockMvc.perform(get(servicesUrl(textilesId) + "/" + october))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(october))
                .andExpect(jsonPath("$.deliveryDate").value("2026-10-15"))
                .andExpect(jsonPath("$.discountPercent").value(7.5));

        // An assignment of one company cannot be read under another company's path.
        mockMvc.perform(get(servicesUrl(hufId) + "/" + october))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("updates a service's details and keeps the catalog service and subscriber count")
    void updatesService() throws Exception {
        String assignment = assign(textilesId, "2026-10-15");
        String otherServiceId = createCatalogService("Annual ROC Filing");

        // A serviceId in the body is not part of the update contract, so it is ignored.
        mockMvc.perform(put(servicesUrl(textilesId) + "/" + assignment)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "serviceId": "%s",
                                  "deliveryDate": "2026-11-20",
                                  "discountPercent": 12,
                                  "comment": "Delivered late"
                                }""".formatted(otherServiceId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(assignment))
                .andExpect(jsonPath("$.serviceId").value(serviceId))
                .andExpect(jsonPath("$.deliveryDate").value("2026-11-20"))
                .andExpect(jsonPath("$.discountPercent").value(12.0))
                .andExpect(jsonPath("$.comment").value("Delivered late"));

        // Read back from the database; omitting the discount and comment resets both.
        mockMvc.perform(put(servicesUrl(textilesId) + "/" + assignment)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "deliveryDate": "2026-11-21" }"""))
                .andExpect(status().isOk());
        mockMvc.perform(get(servicesUrl(textilesId) + "/" + assignment))
                .andExpect(jsonPath("$.deliveryDate").value("2026-11-21"))
                .andExpect(jsonPath("$.discountPercent").value(7.5))
                .andExpect(jsonPath("$.comment").doesNotExist());

        mockMvc.perform(get(ServiceCatalogApi.BASE_PATH + "/" + serviceId))
                .andExpect(jsonPath("$.usedByCompanies").value(1));
        mockMvc.perform(get(ServiceCatalogApi.BASE_PATH + "/" + otherServiceId))
                .andExpect(jsonPath("$.usedByCompanies").value(0));

        // An assignment cannot be updated under another company's path.
        mockMvc.perform(put(servicesUrl(hufId) + "/" + assignment)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "deliveryDate": "2026-12-01" }"""))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("removing services lowers the subscriber count only when the last one goes")
    void removesServices() throws Exception {
        String september = assign(textilesId, "2026-09-15");
        String october = assign(textilesId, "2026-10-15");
        mockMvc.perform(get(ServiceCatalogApi.BASE_PATH + "/" + serviceId))
                .andExpect(jsonPath("$.usedByCompanies").value(1));

        mockMvc.perform(delete(servicesUrl(textilesId) + "/" + september))
                .andExpect(status().isNoContent());
        mockMvc.perform(get(ServiceCatalogApi.BASE_PATH + "/" + serviceId))
                .andExpect(jsonPath("$.usedByCompanies").value(1));

        mockMvc.perform(delete(servicesUrl(textilesId) + "/" + october))
                .andExpect(status().isNoContent());
        mockMvc.perform(get(ServiceCatalogApi.BASE_PATH + "/" + serviceId))
                .andExpect(jsonPath("$.usedByCompanies").value(0));

        mockMvc.perform(get(servicesUrl(textilesId)))
                .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(delete(servicesUrl(textilesId) + "/" + october))
                .andExpect(status().isNotFound());

        // With no company using it any more, the catalog service can be deleted.
        mockMvc.perform(delete(ServiceCatalogApi.BASE_PATH + "/" + serviceId))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("a service cannot be removed under another company's path")
    void cannotRemoveAnotherCompanysService() throws Exception {
        String assignment = assign(textilesId, "2026-10-15");

        mockMvc.perform(delete(servicesUrl(hufId) + "/" + assignment))
                .andExpect(status().isNotFound());

        assertThat(serviceAssignmentRepository.existsById(UUID.fromString(assignment))).isTrue();
        mockMvc.perform(get(ServiceCatalogApi.BASE_PATH + "/" + serviceId))
                .andExpect(jsonPath("$.usedByCompanies").value(1));
    }

    @Test
    @DisplayName("a company can be removed by a client update once its services are removed")
    void companyCanBeRemovedOnceServicesAreGone() throws Exception {
        String assignment = assign(textilesId, "2026-10-15");
        mockMvc.perform(delete(servicesUrl(textilesId) + "/" + assignment))
                .andExpect(status().isNoContent());

        mockMvc.perform(put(ClientApi.BASE_PATH + "/" + clientId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson("""
                                [
                                  {
                                    "id": "%s",
                                    "companyName": "Rohan Mehta HUF",
                                    "companyType": "HUF",
                                    "address": "4 Park Street, Kolkata, West Bengal 700016"
                                  }
                                ]""".formatted(hufId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.companies.length()").value(1));
    }

    @Test
    @DisplayName("a client or company with services cannot be deleted until the services are removed")
    void deletesAreBlockedByServices() throws Exception {
        String assignment = assign(textilesId, "2026-10-15");

        mockMvc.perform(delete(ClientApi.BASE_PATH + "/" + clientId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESOURCE_IN_USE"));
        mockMvc.perform(delete(ClientApi.BASE_PATH + "/" + clientId + "/companies/" + textilesId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESOURCE_IN_USE"));
        mockMvc.perform(get(ClientApi.BASE_PATH + "/" + clientId))
                .andExpect(jsonPath("$.companies.length()").value(2));

        // The other company has no services, so it can go.
        mockMvc.perform(delete(ClientApi.BASE_PATH + "/" + clientId + "/companies/" + hufId))
                .andExpect(status().isNoContent());

        mockMvc.perform(delete(servicesUrl(textilesId) + "/" + assignment))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete(ClientApi.BASE_PATH + "/" + clientId))
                .andExpect(status().isNoContent());
        assertThat(clientRepository.count()).isZero();
    }

    @Test
    @DisplayName("a client update cannot remove a company that has services, but can keep it")
    void clientUpdateCannotRemoveCompanyWithServices() throws Exception {
        mockMvc.perform(post(servicesUrl(textilesId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(minimalJson(serviceId)))
                .andExpect(status().isCreated());

        // Keeps only the HUF, dropping the textiles company that has a service.
        mockMvc.perform(put(ClientApi.BASE_PATH + "/" + clientId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson("""
                                [
                                  {
                                    "id": "%s",
                                    "companyName": "Rohan Mehta HUF",
                                    "companyType": "HUF",
                                    "address": "4 Park Street, Kolkata, West Bengal 700016"
                                  }
                                ]""".formatted(hufId))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESOURCE_IN_USE"));

        mockMvc.perform(get(ClientApi.BASE_PATH + "/" + clientId))
                .andExpect(jsonPath("$.companies.length()").value(2));

        // Keeping the company with the service is fine.
        mockMvc.perform(put(ClientApi.BASE_PATH + "/" + clientId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson("""
                                [
                                  {
                                    "id": "%s",
                                    "companyName": "Mehta Textiles Pvt Ltd",
                                    "companyType": "PRIVATE_LIMITED",
                                    "address": "12 MG Road, Bengaluru, Karnataka 560001"
                                  }
                                ]""".formatted(textilesId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.companies.length()").value(1));
    }

    private String assign(String companyId, String deliveryDate) throws Exception {
        String assigned = mockMvc.perform(post(servicesUrl(companyId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "serviceId": "%s", "deliveryDate": "%s" }""".formatted(serviceId, deliveryDate)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(assigned, "$.id");
    }

    private String servicesUrl(String companyId) {
        return ClientApi.BASE_PATH + "/" + clientId + "/companies/" + companyId + "/services";
    }

    private String minimalJson(String service) {
        return """
                { "serviceId": "%s", "deliveryDate": "2026-10-15" }""".formatted(service);
    }

    private String updateJson(String companies) {
        return """
                {
                  "firstName": "Rohan",
                  "lastName": "Mehta",
                  "phone": "+91 98200 12345",
                  "email": "rohan.mehta@example.com",
                  "managerId": "%s",
                  "companies": %s
                }""".formatted(managerRepository.findAll().getFirst().getId(), companies);
    }

    private UUID createManager() {
        Manager manager = new Manager();
        manager.setFirstName("Pranay");
        manager.setLastName("Singhal");
        manager.setEmail("pranay.singhal@pranaysinghal.in");
        manager.setRole(ManagerRole.SENIOR_MANAGER);
        manager.setMobileNumber("+91 98765 43210");
        return managerRepository.saveAndFlush(manager).getId();
    }

    private String createCatalogService(String name) throws Exception {
        String created = mockMvc.perform(post(ServiceCatalogApi.BASE_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(catalogServiceJson(name)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(created, "$.id");
    }

    private String catalogServiceJson(String name) {
        return """
                {
                  "serviceName": "%s",
                  "category": "COMPLIANCE",
                  "billingFrequency": "MONTHLY",
                  "standardFee": 5000.00,
                  "invoiceSchedule": { "dayOfMonth": 7 }
                }""".formatted(name);
    }
}
