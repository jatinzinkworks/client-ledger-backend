package com.psc.cl.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;
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
import com.psc.cl.config.ApiPaths;
import com.psc.cl.ledger.repository.LedgerEntryRepository;
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
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;

class LedgerIT extends AbstractPostgresIT {

    /** Fixes "today" at 13 October 2026 in India, so what falls due does not depend on the run date. */
    @TestConfiguration
    static class FixedClockConfig {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2026-10-13T06:30:00Z"), ZoneId.of("Asia/Kolkata"));
        }
    }

    @Autowired
    private LedgerEntryRepository ledgerEntryRepository;

    @Autowired
    private ServiceAssignmentRepository serviceAssignmentRepository;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private ManagerRepository managerRepository;

    @Autowired
    private CatalogServiceRepository catalogServiceRepository;

    private String clientId;

    /** Mehta Textiles Pvt Ltd. */
    private String companyId;

    /** Rohan Mehta HUF, the client's second company. */
    private String hufId;

    /** Billed monthly on the 7th, for 5000.00 at the default 18% GST. */
    private String monthlyServiceId;

    /** Billed once, for 25000.00 at the default 18% GST. */
    private String oneOffServiceId;

    @BeforeEach
    void setUp() throws Exception {
        clearAll();
        UUID managerId = createManager();
        monthlyServiceId = createCatalogService("GST Monthly Return Filing", "5000.00",
                "\"billingFrequency\": \"MONTHLY\", \"invoiceSchedule\": { \"dayOfMonth\": 7 }");
        oneOffServiceId = createCatalogService("Company Incorporation", "25000.00",
                "\"billingFrequency\": \"ONE_OFF\"");

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
                                      "companyName": "Mehta Textiles Pvt Ltd",
                                      "companyType": "PRIVATE_LIMITED",
                                      "address": "12 MG Road, Bengaluru, Karnataka 560001"
                                    },
                                    {
                                      "companyName": "Rohan Mehta HUF",
                                      "companyType": "HUF",
                                      "address": "4 Park Street, Kolkata, West Bengal 700016"
                                    }
                                  ]
                                }""".formatted(managerId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        clientId = JsonPath.read(created, "$.id");
        // Companies come back ordered by name.
        companyId = JsonPath.read(created, "$.companies[0].id");
        hufId = JsonPath.read(created, "$.companies[1].id");
    }

    /** Removed in dependency order: ledger entries, assignments, then clients, managers and services. */
    @AfterEach
    void clearAll() {
        ledgerEntryRepository.deleteAll();
        serviceAssignmentRepository.deleteAll();
        clientRepository.deleteAll();
        managerRepository.deleteAll();
        catalogServiceRepository.deleteAll();
    }

    @Test
    @DisplayName("posts every due date of a recurring service and moves it on to its next due date")
    void postsRecurringService() throws Exception {
        // Next due on 7 September, so 7 September and 7 October have both fallen due by 13 October.
        String assignmentId = assign(monthlyServiceId, "2026-08-10", "5");

        mockMvc.perform(post(ledgerUrl()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].dueDate").value("2026-09-07"))
                .andExpect(jsonPath("$[1].dueDate").value("2026-10-07"))
                .andExpect(jsonPath("$[0].clientId").value(clientId))
                .andExpect(jsonPath("$[0].companyId").value(companyId))
                .andExpect(jsonPath("$[0].companyServiceId").value(assignmentId))
                .andExpect(jsonPath("$[0].serviceId").value(monthlyServiceId))
                .andExpect(jsonPath("$[0].serviceType").value("MONTHLY"))
                .andExpect(jsonPath("$[0].financialYear").value("2026-27"))
                .andExpect(jsonPath("$[0].entryDate").value("2026-10-13"))
                .andExpect(jsonPath("$[0].serviceFee").value(5000.0))
                .andExpect(jsonPath("$[0].discountPercent").value(5.0))
                .andExpect(jsonPath("$[0].discountAmount").value(250.0))
                .andExpect(jsonPath("$[0].taxableAmount").value(4750.0))
                .andExpect(jsonPath("$[0].gstRatePercent").value(18.0))
                .andExpect(jsonPath("$[0].gstAmount").value(855.0))
                .andExpect(jsonPath("$[0].totalAmount").value(5605.0));

        mockMvc.perform(get(servicesUrl() + "/" + assignmentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nextDueDate").value("2026-11-07"));
        assertThat(ledgerEntryRepository.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("posts a ONE_OFF service once and marks it as posted")
    void postsOneOffServiceOnce() throws Exception {
        // No discount given, so the service takes the client's 10%.
        String assignmentId = assign(oneOffServiceId, "2026-10-01", null);

        mockMvc.perform(post(ledgerUrl()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].dueDate").value("2026-10-01"))
                .andExpect(jsonPath("$[0].serviceType").value("ONE_OFF"))
                .andExpect(jsonPath("$[0].discountAmount").value(2500.0))
                .andExpect(jsonPath("$[0].taxableAmount").value(22500.0))
                .andExpect(jsonPath("$[0].gstAmount").value(4050.0))
                .andExpect(jsonPath("$[0].totalAmount").value(26550.0));

        assertThat(serviceAssignmentRepository.findById(UUID.fromString(assignmentId)))
                .hasValueSatisfying(assignment -> assertThat(assignment.isLedgerPosted()).isTrue());
    }

    @Test
    @DisplayName("posting again creates nothing new, and services not yet due are left alone")
    void postingAgainCreatesNothing() throws Exception {
        assign(monthlyServiceId, "2026-10-01", "5");
        assign(oneOffServiceId, "2026-10-01", "5");
        assign(oneOffServiceId, "2026-12-01", "5");

        mockMvc.perform(post(ledgerUrl()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        mockMvc.perform(post(ledgerUrl()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        assertThat(ledgerEntryRepository.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("a billed service cannot be removed from under its ledger entries")
    void billedServiceCannotBeRemoved() throws Exception {
        String assignmentId = assign(oneOffServiceId, "2026-10-01", "5");
        mockMvc.perform(post(ledgerUrl()))
                .andExpect(status().isOk());

        mockMvc.perform(delete(servicesUrl() + "/" + assignmentId))
                .andExpect(status().isConflict());

        assertThat(serviceAssignmentRepository.existsById(UUID.fromString(assignmentId))).isTrue();
    }

    @Test
    @DisplayName("lists a company's entries, and the entries of every company of the client")
    void listsCompanyAndClientEntries() throws Exception {
        assign(monthlyServiceId, "2026-08-10", "5");
        assign(hufId, oneOffServiceId, "2026-09-20", "5");
        mockMvc.perform(post(ledgerUrl()))
                .andExpect(status().isOk());
        mockMvc.perform(post(ApiPaths.V1 + "/clients/" + clientId + "/companies/" + hufId + "/ledger-entries"))
                .andExpect(status().isOk());

        mockMvc.perform(get(ledgerUrl()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries.length()").value(2))
                .andExpect(jsonPath("$.entries[0].dueDate").value("2026-09-07"))
                .andExpect(jsonPath("$.entries[1].dueDate").value("2026-10-07"))
                .andExpect(jsonPath("$.entries[*].companyId").value(everyItem(is(companyId))))
                .andExpect(jsonPath("$.summary.byCompany").doesNotExist());

        mockMvc.perform(get(ApiPaths.V1 + "/clients/" + clientId + "/ledger-entries"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries.length()").value(3))
                .andExpect(jsonPath("$.entries[0].dueDate").value("2026-09-07"))
                .andExpect(jsonPath("$.entries[1].dueDate").value("2026-09-20"))
                .andExpect(jsonPath("$.entries[1].companyId").value(hufId))
                .andExpect(jsonPath("$.entries[2].dueDate").value("2026-10-07"))
                // Two monthly entries of 5605.00 and a one-off of 28025.00, all at a 5% discount.
                .andExpect(jsonPath("$.summary.entryCount").value(3))
                .andExpect(jsonPath("$.summary.totalServiceFee").value(35000.0))
                .andExpect(jsonPath("$.summary.totalDiscountAmount").value(1750.0))
                .andExpect(jsonPath("$.summary.totalTaxableAmount").value(33250.0))
                .andExpect(jsonPath("$.summary.totalGstAmount").value(5985.0))
                .andExpect(jsonPath("$.summary.totalAmount").value(39235.0))
                // Ordered by name: Mehta Textiles Pvt Ltd, then Rohan Mehta HUF.
                .andExpect(jsonPath("$.summary.byCompany.length()").value(2))
                .andExpect(jsonPath("$.summary.byCompany[0].companyId").value(companyId))
                .andExpect(jsonPath("$.summary.byCompany[0].companyName").value("Mehta Textiles Pvt Ltd"))
                .andExpect(jsonPath("$.summary.byCompany[0].entryCount").value(2))
                .andExpect(jsonPath("$.summary.byCompany[0].totalServiceFee").value(10000.0))
                .andExpect(jsonPath("$.summary.byCompany[0].totalDiscountAmount").value(500.0))
                .andExpect(jsonPath("$.summary.byCompany[0].totalTaxableAmount").value(9500.0))
                .andExpect(jsonPath("$.summary.byCompany[0].totalGstAmount").value(1710.0))
                .andExpect(jsonPath("$.summary.byCompany[0].totalAmount").value(11210.0))
                .andExpect(jsonPath("$.summary.byCompany[1].companyId").value(hufId))
                .andExpect(jsonPath("$.summary.byCompany[1].companyName").value("Rohan Mehta HUF"))
                .andExpect(jsonPath("$.summary.byCompany[1].entryCount").value(1))
                .andExpect(jsonPath("$.summary.byCompany[1].totalAmount").value(28025.0));
    }

    @Test
    @DisplayName("filters listed entries by due date range, and places each in its financial year")
    void filtersByDueDateRange() throws Exception {
        assign(monthlyServiceId, "2026-08-10", "5");
        // Due before 1 April 2026, so it falls in the previous financial year.
        assign(hufId, oneOffServiceId, "2026-03-20", "5");
        mockMvc.perform(post(ledgerUrl()))
                .andExpect(status().isOk());
        mockMvc.perform(post(ApiPaths.V1 + "/clients/" + clientId + "/companies/" + hufId + "/ledger-entries"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].financialYear").value("2025-26"));

        String clientLedger = ApiPaths.V1 + "/clients/" + clientId + "/ledger-entries";
        mockMvc.perform(get(clientLedger))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.entryCount").value(3))
                .andExpect(jsonPath("$.summary.totalAmount").value(39235.0))
                .andExpect(jsonPath("$.summary.byFinancialYear.length()").value(2))
                // The one-off due in March 2026 closes 2025-26; the two monthly entries fall in 2026-27.
                .andExpect(jsonPath("$.summary.byFinancialYear[0].financialYear").value("2025-26"))
                .andExpect(jsonPath("$.summary.byFinancialYear[0].entryCount").value(1))
                .andExpect(jsonPath("$.summary.byFinancialYear[0].totalServiceFee").value(25000.0))
                .andExpect(jsonPath("$.summary.byFinancialYear[0].totalDiscountAmount").value(1250.0))
                .andExpect(jsonPath("$.summary.byFinancialYear[0].totalTaxableAmount").value(23750.0))
                .andExpect(jsonPath("$.summary.byFinancialYear[0].totalGstAmount").value(4275.0))
                .andExpect(jsonPath("$.summary.byFinancialYear[0].totalAmount").value(28025.0))
                .andExpect(jsonPath("$.summary.byFinancialYear[1].financialYear").value("2026-27"))
                .andExpect(jsonPath("$.summary.byFinancialYear[1].entryCount").value(2))
                .andExpect(jsonPath("$.summary.byFinancialYear[1].totalAmount").value(11210.0));

        mockMvc.perform(get(clientLedger).param("fromDate", "2026-04-01").param("toDate", "2027-03-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries.length()").value(2))
                .andExpect(jsonPath("$.entries[*].financialYear").value(everyItem(is("2026-27"))))
                .andExpect(jsonPath("$.summary.entryCount").value(2))
                .andExpect(jsonPath("$.summary.totalAmount").value(11210.0))
                .andExpect(jsonPath("$.summary.byFinancialYear.length()").value(1))
                .andExpect(jsonPath("$.summary.byFinancialYear[0].financialYear").value("2026-27"));

        // Both ends are inclusive.
        mockMvc.perform(get(ledgerUrl()).param("fromDate", "2026-09-07").param("toDate", "2026-09-07"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries.length()").value(1))
                .andExpect(jsonPath("$.entries[0].dueDate").value("2026-09-07"));

        mockMvc.perform(get(clientLedger).param("toDate", "2026-03-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries.length()").value(1))
                .andExpect(jsonPath("$.entries[0].dueDate").value("2026-03-20"));

        mockMvc.perform(get(clientLedger).param("financialYear", "2025-26"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries.length()").value(1))
                .andExpect(jsonPath("$.entries[0].dueDate").value("2026-03-20"));

        mockMvc.perform(get(clientLedger).param("financialYear", "2026-27").param("toDate", "2026-09-30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries.length()").value(1))
                .andExpect(jsonPath("$.entries[0].dueDate").value("2026-09-07"));

        mockMvc.perform(get(ledgerUrl()).param("financialYear", "2027-28"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries.length()").value(0));

        mockMvc.perform(get(clientLedger).param("financialYear", "2026-2027"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        mockMvc.perform(get(clientLedger).param("fromDate", "2026-10-01").param("toDate", "2026-09-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("pages through entries in due date order, with totals of every entry on each page")
    void pagesThroughEntries() throws Exception {
        // Next due on 7 February, so nine monthly dates have fallen due by 13 October.
        assign(monthlyServiceId, "2026-01-10", "5");
        mockMvc.perform(post(ledgerUrl()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(9));

        mockMvc.perform(get(ledgerUrl()).param("page", "0").param("size", "4"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries.length()").value(4))
                .andExpect(jsonPath("$.entries[0].dueDate").value("2026-02-07"))
                .andExpect(jsonPath("$.entries[3].dueDate").value("2026-05-07"))
                .andExpect(jsonPath("$.page.number").value(0))
                .andExpect(jsonPath("$.page.size").value(4))
                .andExpect(jsonPath("$.page.numberOfElements").value(4))
                .andExpect(jsonPath("$.page.totalElements").value(9))
                .andExpect(jsonPath("$.page.totalPages").value(3))
                .andExpect(jsonPath("$.page.hasNext").value(true))
                // Totals cover all nine entries, not just the four on this page.
                .andExpect(jsonPath("$.summary.entryCount").value(9))
                .andExpect(jsonPath("$.summary.totalServiceFee").value(45000.0))
                .andExpect(jsonPath("$.summary.totalAmount").value(50445.0));

        mockMvc.perform(get(ledgerUrl()).param("page", "1").param("size", "4"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries[0].dueDate").value("2026-06-07"))
                .andExpect(jsonPath("$.entries[3].dueDate").value("2026-09-07"));

        mockMvc.perform(get(ApiPaths.V1 + "/clients/" + clientId + "/ledger-entries")
                        .param("page", "2").param("size", "4"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries.length()").value(1))
                .andExpect(jsonPath("$.entries[0].dueDate").value("2026-10-07"))
                .andExpect(jsonPath("$.page.hasNext").value(false))
                .andExpect(jsonPath("$.summary.entryCount").value(9))
                .andExpect(jsonPath("$.summary.byCompany[0].entryCount").value(9));

        // Past the last page: no entries, but the same totals.
        mockMvc.perform(get(ledgerUrl()).param("page", "5").param("size", "4"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries.length()").value(0))
                .andExpect(jsonPath("$.page.totalElements").value(9))
                .andExpect(jsonPath("$.summary.totalAmount").value(50445.0));

        mockMvc.perform(get(ledgerUrl()).param("size", "201"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("sorts by the field and direction asked for, breaking ties in due date order")
    void sortsEntries() throws Exception {
        // Two monthly entries of 5605.00, on 7 September and 7 October, and a one-off of 28025.00 on 20 September.
        assign(monthlyServiceId, "2026-08-10", "5");
        assign(oneOffServiceId, "2026-09-20", "5");
        mockMvc.perform(post(ledgerUrl()))
                .andExpect(status().isOk());

        mockMvc.perform(get(ledgerUrl()).param("sortBy", "totalAmount").param("sortDirection", "desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries[0].totalAmount").value(28025.0))
                // The two equal totals follow in due date order.
                .andExpect(jsonPath("$.entries[1].dueDate").value("2026-09-07"))
                .andExpect(jsonPath("$.entries[2].dueDate").value("2026-10-07"));

        mockMvc.perform(get(ledgerUrl()).param("sortBy", "dueDate").param("sortDirection", "desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries[0].dueDate").value("2026-10-07"))
                .andExpect(jsonPath("$.entries[1].dueDate").value("2026-09-20"))
                .andExpect(jsonPath("$.entries[2].dueDate").value("2026-09-07"));

        mockMvc.perform(get(ApiPaths.V1 + "/clients/" + clientId + "/ledger-entries").param("sortBy", "serviceName"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries[0].serviceName").value("Company Incorporation"))
                .andExpect(jsonPath("$.entries[1].serviceName").value("GST Monthly Return Filing"))
                .andExpect(jsonPath("$.entries[1].dueDate").value("2026-09-07"));

        // Sorting changes the order only; the totals stay those of every entry.
        mockMvc.perform(get(ledgerUrl()).param("sortBy", "totalAmount").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries[0].totalAmount").value(5605.0))
                .andExpect(jsonPath("$.summary.totalAmount").value(39235.0));

        mockMvc.perform(get(ledgerUrl()).param("sortBy", "companyId"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("fetches a posted entry by id, only under the company it was billed to")
    void fetchesEntryById() throws Exception {
        assign(oneOffServiceId, "2026-10-01", "5");
        String posted = mockMvc.perform(post(ledgerUrl()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String entryId = JsonPath.read(posted, "$[0].id");

        mockMvc.perform(get(ledgerUrl() + "/" + entryId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(entryId))
                .andExpect(jsonPath("$.companyId").value(companyId))
                .andExpect(jsonPath("$.serviceType").value("ONE_OFF"))
                .andExpect(jsonPath("$.financialYear").value("2026-27"))
                .andExpect(jsonPath("$.totalAmount").value(28025.0));

        // The client's other company was not billed for it.
        mockMvc.perform(get(ApiPaths.V1 + "/clients/" + clientId + "/companies/" + hufId + "/ledger-entries/"
                        + entryId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

        mockMvc.perform(get(ledgerUrl() + "/" + UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("corrects the rates of an entry and stores the amounts worked out again")
    void correctsEntry() throws Exception {
        assign(oneOffServiceId, "2026-10-01", "5");
        String posted = mockMvc.perform(post(ledgerUrl()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String entryId = JsonPath.read(posted, "$[0].id");

        // 30000 less 10% is 27000; 18% GST on 27000 is 4860.
        mockMvc.perform(put(ledgerUrl() + "/" + entryId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "serviceFee": 30000, "discountPercent": 10, "gstRatePercent": 18 }"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.serviceFee").value(30000.0))
                .andExpect(jsonPath("$.discountAmount").value(3000.0))
                .andExpect(jsonPath("$.taxableAmount").value(27000.0))
                .andExpect(jsonPath("$.gstAmount").value(4860.0))
                .andExpect(jsonPath("$.totalAmount").value(31860.0))
                .andExpect(jsonPath("$.updatedAt").exists());

        // Read back from the database: stored, and what the entry bills is unchanged.
        mockMvc.perform(get(ledgerUrl() + "/" + entryId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalAmount").value(31860.0))
                .andExpect(jsonPath("$.dueDate").value("2026-10-01"))
                .andExpect(jsonPath("$.financialYear").value("2026-27"))
                .andExpect(jsonPath("$.serviceName").value("Company Incorporation"));
        mockMvc.perform(get(ledgerUrl()))
                .andExpect(jsonPath("$.summary.totalAmount").value(31860.0));

        mockMvc.perform(put(ApiPaths.V1 + "/clients/" + clientId + "/companies/" + hufId + "/ledger-entries/"
                        + entryId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "serviceFee": 1, "discountPercent": 0, "gstRatePercent": 0 }"""))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("deletes an entry without billing its date again")
    void deletesEntry() throws Exception {
        assign(monthlyServiceId, "2026-08-10", "5");
        assign(oneOffServiceId, "2026-10-01", "5");
        String posted = mockMvc.perform(post(ledgerUrl()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andReturn().getResponse().getContentAsString();
        // Earliest due first: 7 September, 1 October (the one-off), 7 October.
        String monthlyEntryId = JsonPath.read(posted, "$[0].id");
        String oneOffEntryId = JsonPath.read(posted, "$[1].id");

        mockMvc.perform(delete(ledgerUrl() + "/" + monthlyEntryId))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete(ledgerUrl() + "/" + oneOffEntryId))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(ledgerUrl() + "/" + oneOffEntryId))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete(ledgerUrl() + "/" + oneOffEntryId))
                .andExpect(status().isNotFound());
        assertThat(ledgerEntryRepository.count()).isEqualTo(1);

        // The one-off stays posted and the monthly service keeps its next due date, so nothing is billed again.
        mockMvc.perform(post(ledgerUrl()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("records payments as negative ledger entries that bring the balance down")
    void recordsPayments() throws Exception {
        // A one-off charge of 28025.00, due 1 October.
        assign(oneOffServiceId, "2026-10-01", "5");
        mockMvc.perform(post(ledgerUrl()))
                .andExpect(status().isOk());

        String recorded = mockMvc.perform(post(paymentsUrl())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "amountReceived": 15000,
                                  "dateReceived": "2026-10-12",
                                  "method": "BANK_TRANSFER",
                                  "reference": "HDFC0001234567890",
                                  "comment": "Part payment"
                                }"""))
                .andExpect(status().isCreated())
                .andExpect(header().exists(HttpHeaders.LOCATION))
                .andExpect(jsonPath("$.entryType").value("PAYMENT"))
                .andExpect(jsonPath("$.clientId").value(clientId))
                .andExpect(jsonPath("$.companyId").value(companyId))
                .andExpect(jsonPath("$.totalAmount").value(-15000.0))
                .andExpect(jsonPath("$.dueDate").value("2026-10-12"))
                .andExpect(jsonPath("$.financialYear").value("2026-27"))
                .andExpect(jsonPath("$.entryDate").value("2026-10-13"))
                .andExpect(jsonPath("$.paymentMethod").value("BANK_TRANSFER"))
                .andExpect(jsonPath("$.paymentReference").value("HDFC0001234567890"))
                .andExpect(jsonPath("$.comment").value("Part payment"))
                .andExpect(jsonPath("$.serviceFee").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        String paymentId = JsonPath.read(recorded, "$.id");

        // Received before 1 April 2026, so it falls in the previous financial year.
        mockMvc.perform(post(paymentsUrl())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "amountReceived": 1000, "dateReceived": "2026-03-20", "method": "CASH" }"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.financialYear").value("2025-26"));

        mockMvc.perform(get(ledgerUrl() + "/" + paymentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entryType").value("PAYMENT"));

        // Payments and charges share one ledger, in date order.
        mockMvc.perform(get(ledgerUrl()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries.length()").value(3))
                .andExpect(jsonPath("$.entries[0].entryType").value("PAYMENT"))
                .andExpect(jsonPath("$.entries[0].dueDate").value("2026-03-20"))
                .andExpect(jsonPath("$.entries[1].entryType").value("SERVICE_CHARGE"))
                .andExpect(jsonPath("$.entries[2].id").value(paymentId))
                .andExpect(jsonPath("$.summary.entryCount").value(3))
                .andExpect(jsonPath("$.summary.totalServiceFee").value(25000.0))
                .andExpect(jsonPath("$.summary.totalAmount").value(28025.0))
                .andExpect(jsonPath("$.summary.totalPaid").value(16000.0))
                .andExpect(jsonPath("$.summary.balance").value(12025.0))
                .andExpect(jsonPath("$.summary.byFinancialYear[0].financialYear").value("2025-26"))
                .andExpect(jsonPath("$.summary.byFinancialYear[0].totalAmount").value(0.0))
                .andExpect(jsonPath("$.summary.byFinancialYear[0].totalPaid").value(1000.0))
                .andExpect(jsonPath("$.summary.byFinancialYear[0].balance").value(-1000.0))
                .andExpect(jsonPath("$.summary.byFinancialYear[1].balance").value(13025.0));

        mockMvc.perform(get(ApiPaths.V1 + "/clients/" + clientId + "/ledger-entries"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.byCompany[0].companyId").value(companyId))
                .andExpect(jsonPath("$.summary.byCompany[0].totalPaid").value(16000.0))
                .andExpect(jsonPath("$.summary.byCompany[0].balance").value(12025.0));

        // A payment has no rates to correct.
        mockMvc.perform(put(ledgerUrl() + "/" + paymentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "serviceFee": 15000, "discountPercent": 0, "gstRatePercent": 0 }"""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"));

        // A payment recorded in error can be deleted, and the balance goes back up.
        mockMvc.perform(delete(ledgerUrl() + "/" + paymentId))
                .andExpect(status().isNoContent());
        mockMvc.perform(get(ledgerUrl()))
                .andExpect(jsonPath("$.summary.totalPaid").value(1000.0))
                .andExpect(jsonPath("$.summary.balance").value(27025.0));
    }

    @Test
    @DisplayName("summarises a company's financial year, keeping charges and payments in their own years")
    void summarisesCompanyYear() throws Exception {
        // Monthly on the 7th from February: 7 February and 7 March fall in 2025-26, April to October in 2026-27.
        assign(monthlyServiceId, "2026-01-10", "5");
        mockMvc.perform(post(ledgerUrl()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(9));
        recordPayment("5000", "2026-03-25", "CHEQUE");
        recordPayment("10000", "2026-06-15", "BANK_TRANSFER");
        recordPayment("5605", "2026-09-01", "UPI");

        mockMvc.perform(get(companySummaryUrl(companyId)).param("financialYear", "2026-27"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clientId").value(clientId))
                .andExpect(jsonPath("$.companyId").value(companyId))
                .andExpect(jsonPath("$.companyName").value("Mehta Textiles Pvt Ltd"))
                .andExpect(jsonPath("$.financialYear").value("2026-27"))
                .andExpect(jsonPath("$.fromDate").value("2026-04-01"))
                .andExpect(jsonPath("$.toDate").value("2027-03-31"))
                // Seven monthly charges of 5605.00, each 250.00 off.
                .andExpect(jsonPath("$.totalBilled").value(39235.0))
                .andExpect(jsonPath("$.totalDiscount").value(1750.0))
                .andExpect(jsonPath("$.totalPaymentReceived").value(15605.0))
                .andExpect(jsonPath("$.pending").value(23630.0))
                .andExpect(jsonPath("$.lastPayment.date").value("2026-09-01"))
                .andExpect(jsonPath("$.lastPayment.amount").value(5605.0));

        mockMvc.perform(get(companySummaryUrl(companyId)).param("financialYear", "2025-26"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalBilled").value(11210.0))
                .andExpect(jsonPath("$.totalDiscount").value(500.0))
                .andExpect(jsonPath("$.totalPaymentReceived").value(5000.0))
                .andExpect(jsonPath("$.pending").value(6210.0))
                .andExpect(jsonPath("$.lastPayment.date").value("2026-03-25"))
                .andExpect(jsonPath("$.lastPayment.amount").value(5000.0));

        // A year with nothing in it, and a company with nothing billed, both summarise as zeros.
        mockMvc.perform(get(companySummaryUrl(companyId)).param("financialYear", "2027-28"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalBilled").value(0.0))
                .andExpect(jsonPath("$.totalPaymentReceived").value(0.0))
                .andExpect(jsonPath("$.pending").value(0.0))
                .andExpect(jsonPath("$.totalDiscount").value(0.0))
                .andExpect(jsonPath("$.lastPayment").doesNotExist());
        mockMvc.perform(get(companySummaryUrl(hufId)).param("financialYear", "2026-27"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.companyName").value("Rohan Mehta HUF"))
                .andExpect(jsonPath("$.totalBilled").value(0.0))
                .andExpect(jsonPath("$.lastPayment").doesNotExist());

        mockMvc.perform(get(companySummaryUrl(companyId)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(companySummaryUrl(companyId)).param("financialYear", "2026-28"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("summarises a client's year across companies, each with its own last payment")
    void summarisesClientYear() throws Exception {
        // Mehta Textiles: monthly charges of 5605.00 on 7 September and 7 October.
        assign(monthlyServiceId, "2026-08-10", "5");
        // Rohan Mehta HUF: a one-off charge of 28025.00 on 20 September.
        assign(hufId, oneOffServiceId, "2026-09-20", "5");
        mockMvc.perform(post(ledgerUrl()))
                .andExpect(status().isOk());
        mockMvc.perform(post(ApiPaths.V1 + "/clients/" + clientId + "/companies/" + hufId + "/ledger-entries"))
                .andExpect(status().isOk());
        recordPayment("1000", "2026-03-15", "CASH");
        recordPayment("5605", "2026-09-10", "BANK_TRANSFER");
        recordPayment("3000", "2026-10-02", "UPI");
        recordPayment(hufId, "10000", "2026-10-10", "CHEQUE");

        String clientSummary = ApiPaths.V1 + "/clients/" + clientId + "/ledger-summary";
        mockMvc.perform(get(clientSummary).param("financialYear", "2026-27"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clientId").value(clientId))
                .andExpect(jsonPath("$.clientName").value("Rohan Mehta"))
                .andExpect(jsonPath("$.financialYear").value("2026-27"))
                .andExpect(jsonPath("$.totalBilled").value(39235.0))
                .andExpect(jsonPath("$.totalDiscount").value(1750.0))
                // The March payment falls in 2025-26 and is left out.
                .andExpect(jsonPath("$.totalPaymentReceived").value(18605.0))
                .andExpect(jsonPath("$.pending").value(20630.0))
                .andExpect(jsonPath("$.lastPayment.date").value("2026-10-10"))
                .andExpect(jsonPath("$.lastPayment.amount").value(10000.0))
                .andExpect(jsonPath("$.lastPayment.companyId").value(hufId))
                .andExpect(jsonPath("$.lastPayment.companyName").value("Rohan Mehta HUF"))
                .andExpect(jsonPath("$.companies").doesNotExist());

        // The year before: only the March payment.
        mockMvc.perform(get(clientSummary).param("financialYear", "2025-26"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalBilled").value(0.0))
                .andExpect(jsonPath("$.totalPaymentReceived").value(1000.0))
                .andExpect(jsonPath("$.pending").value(-1000.0))
                .andExpect(jsonPath("$.lastPayment.companyId").value(companyId));

        // The client's list of companies carries each company's figures for the year asked for.
        String companies = ClientApi.BASE_PATH + "/" + clientId + "/companies";
        mockMvc.perform(get(companies).param("financialYear", "2026-27"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].companyName").value("Mehta Textiles Pvt Ltd"))
                .andExpect(jsonPath("$[0].financialYear").value("2026-27"))
                .andExpect(jsonPath("$[0].totalBilled").value(11210.0))
                .andExpect(jsonPath("$[0].totalPaymentReceived").value(8605.0))
                .andExpect(jsonPath("$[0].pending").value(2605.0))
                .andExpect(jsonPath("$[1].companyName").value("Rohan Mehta HUF"))
                .andExpect(jsonPath("$[1].totalBilled").value(28025.0))
                .andExpect(jsonPath("$[1].totalPaymentReceived").value(10000.0))
                .andExpect(jsonPath("$[1].pending").value(18025.0));
        mockMvc.perform(get(companies).param("financialYear", "2025-26"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].totalPaymentReceived").value(1000.0))
                .andExpect(jsonPath("$[0].pending").value(-1000.0))
                // No activity in that year: zeros rather than nothing.
                .andExpect(jsonPath("$[1].totalBilled").value(0.0))
                .andExpect(jsonPath("$[1].pending").value(0.0));
        mockMvc.perform(get(companies))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].serviceCount").value(1))
                .andExpect(jsonPath("$[0].totalBilled").doesNotExist())
                .andExpect(jsonPath("$[0].financialYear").doesNotExist());
        mockMvc.perform(get(companies).param("financialYear", "26-27"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get(clientSummary))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(ApiPaths.V1 + "/clients/" + UUID.randomUUID() + "/ledger-summary")
                        .param("financialYear", "2026-27"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("lists only a company's payments, filtered, sorted and paged, with totals of them all")
    void listsPayments() throws Exception {
        // Service charges, and another company's payment, which the list must leave out.
        assign(monthlyServiceId, "2026-08-10", "5");
        mockMvc.perform(post(ledgerUrl()))
                .andExpect(status().isOk());
        recordPayment(hufId, "9999", "2026-10-01", "CASH");
        recordPayment("1000", "2026-03-15", "CASH");
        recordPayment("5605", "2026-09-10", "BANK_TRANSFER");
        recordPayment("3000", "2026-10-02", "UPI");

        mockMvc.perform(get(paymentsUrl()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payments.length()").value(3))
                .andExpect(jsonPath("$.payments[*].entryType").value(everyItem(is("PAYMENT"))))
                .andExpect(jsonPath("$.payments[*].companyId").value(everyItem(is(companyId))))
                .andExpect(jsonPath("$.payments[0].dueDate").value("2026-03-15"))
                .andExpect(jsonPath("$.payments[0].paymentMethod").value("CASH"))
                .andExpect(jsonPath("$.payments[2].dueDate").value("2026-10-02"))
                .andExpect(jsonPath("$.page.totalElements").value(3))
                .andExpect(jsonPath("$.summary.paymentCount").value(3))
                .andExpect(jsonPath("$.summary.totalReceived").value(9605.0));

        // The financial year narrows to 2026-27; latest first, one to a page.
        mockMvc.perform(get(paymentsUrl())
                        .param("financialYear", "2026-27")
                        .param("sortDirection", "desc")
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payments.length()").value(1))
                .andExpect(jsonPath("$.payments[0].dueDate").value("2026-10-02"))
                .andExpect(jsonPath("$.payments[0].totalAmount").value(-3000.0))
                .andExpect(jsonPath("$.page.totalElements").value(2))
                .andExpect(jsonPath("$.page.totalPages").value(2))
                .andExpect(jsonPath("$.page.hasNext").value(true))
                // Totals of both 2026-27 payments, not just the one on this page.
                .andExpect(jsonPath("$.summary.paymentCount").value(2))
                .andExpect(jsonPath("$.summary.totalReceived").value(8605.0));

        // Largest payment first: amounts are stored as negative, so ascending by totalAmount.
        mockMvc.perform(get(paymentsUrl()).param("sortBy", "totalAmount"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payments[0].totalAmount").value(-5605.0))
                .andExpect(jsonPath("$.payments[2].totalAmount").value(-1000.0));

        mockMvc.perform(get(ApiPaths.V1 + "/clients/" + clientId + "/companies/" + hufId + "/payments")
                        .param("financialYear", "2027-28"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payments.length()").value(0))
                .andExpect(jsonPath("$.summary.paymentCount").value(0))
                .andExpect(jsonPath("$.summary.totalReceived").value(0.0));

        mockMvc.perform(get(paymentsUrl()).param("financialYear", "2026"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(ApiPaths.V1 + "/clients/" + clientId + "/companies/" + UUID.randomUUID() + "/payments"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("updates a payment, moving it between financial years, but not a service charge")
    void updatesPayment() throws Exception {
        assign(oneOffServiceId, "2026-10-01", "5");
        String posted = mockMvc.perform(post(ledgerUrl()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String chargeId = JsonPath.read(posted, "$[0].id");
        String recorded = mockMvc.perform(post(paymentsUrl())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "amountReceived": 10000,
                                  "dateReceived": "2026-10-05",
                                  "method": "CASH",
                                  "reference": "RCPT-1",
                                  "comment": "Counter payment"
                                }"""))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String paymentId = JsonPath.read(recorded, "$.id");

        // Re-dated before 1 April, so it moves to 2025-26; the comment is left out, so it is cleared.
        mockMvc.perform(put(paymentsUrl() + "/" + paymentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "amountReceived": 12000,
                                  "dateReceived": "2026-03-30",
                                  "method": "CHEQUE",
                                  "reference": "000777"
                                }"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(paymentId))
                .andExpect(jsonPath("$.entryType").value("PAYMENT"))
                .andExpect(jsonPath("$.totalAmount").value(-12000.0))
                .andExpect(jsonPath("$.dueDate").value("2026-03-30"))
                .andExpect(jsonPath("$.financialYear").value("2025-26"))
                .andExpect(jsonPath("$.entryDate").value("2026-10-13"))
                .andExpect(jsonPath("$.paymentMethod").value("CHEQUE"))
                .andExpect(jsonPath("$.paymentReference").value("000777"))
                .andExpect(jsonPath("$.comment").doesNotExist());

        // Stored: read back, and the summaries move the payment between years.
        mockMvc.perform(get(ledgerUrl() + "/" + paymentId))
                .andExpect(jsonPath("$.totalAmount").value(-12000.0))
                .andExpect(jsonPath("$.financialYear").value("2025-26"));
        mockMvc.perform(get(companySummaryUrl(companyId)).param("financialYear", "2026-27"))
                .andExpect(jsonPath("$.totalPaymentReceived").value(0.0))
                .andExpect(jsonPath("$.pending").value(28025.0))
                .andExpect(jsonPath("$.lastPayment").doesNotExist());
        mockMvc.perform(get(companySummaryUrl(companyId)).param("financialYear", "2025-26"))
                .andExpect(jsonPath("$.totalPaymentReceived").value(12000.0))
                .andExpect(jsonPath("$.lastPayment.date").value("2026-03-30"));

        // A service charge cannot be updated as a payment, nor a payment dated after today.
        String paymentUpdate = """
                { "amountReceived": 500, "dateReceived": "%s", "method": "UPI" }""";
        mockMvc.perform(put(paymentsUrl() + "/" + chargeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(paymentUpdate.formatted("2026-10-12")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"));
        mockMvc.perform(get(ledgerUrl() + "/" + chargeId))
                .andExpect(jsonPath("$.entryType").value("SERVICE_CHARGE"))
                .andExpect(jsonPath("$.totalAmount").value(28025.0));
        mockMvc.perform(put(paymentsUrl() + "/" + paymentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(paymentUpdate.formatted("2026-10-14")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        // Only under the company it was recorded for.
        mockMvc.perform(put(ApiPaths.V1 + "/clients/" + clientId + "/companies/" + hufId + "/payments/" + paymentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(paymentUpdate.formatted("2026-10-12")))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("refuses a payment dated after today, or with missing or invalid details")
    void rejectsInvalidPayments() throws Exception {
        mockMvc.perform(post(paymentsUrl())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "amountReceived": 500, "dateReceived": "2026-10-14", "method": "UPI" }"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        mockMvc.perform(post(paymentsUrl())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "amountReceived": -500, "dateReceived": "2026-10-12" }"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        mockMvc.perform(post(ApiPaths.V1 + "/clients/" + clientId + "/companies/" + UUID.randomUUID() + "/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "amountReceived": 500, "dateReceived": "2026-10-12", "method": "CHEQUE" }"""))
                .andExpect(status().isNotFound());

        assertThat(ledgerEntryRepository.count()).isZero();
    }

    @Test
    @DisplayName("lists no entries before anything has been posted")
    void listsNothingBeforePosting() throws Exception {
        assign(monthlyServiceId, "2026-08-10", "5");

        mockMvc.perform(get(ledgerUrl()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries.length()").value(0));
        mockMvc.perform(get(ApiPaths.V1 + "/clients/" + clientId + "/ledger-entries"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries.length()").value(0))
                .andExpect(jsonPath("$.summary.entryCount").value(0))
                .andExpect(jsonPath("$.summary.totalAmount").value(0.0));
    }

    @Test
    @DisplayName("returns 404 when listing the entries of an unknown client")
    void listsEntriesOfUnknownClient() throws Exception {
        mockMvc.perform(get(ApiPaths.V1 + "/clients/" + UUID.randomUUID() + "/ledger-entries"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("returns 404 for a company the client does not hold")
    void unknownCompany() throws Exception {
        mockMvc.perform(post(ApiPaths.V1 + "/clients/" + clientId + "/companies/" + UUID.randomUUID()
                        + "/ledger-entries"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    /** Assigns a service to Mehta Textiles Pvt Ltd. */
    private String assign(String serviceId, String deliveryDate, String discountPercent) throws Exception {
        return assign(companyId, serviceId, deliveryDate, discountPercent);
    }

    /**
     * @param discountPercent the discount to grant, or null to take the company's
     */
    private String assign(String company, String serviceId, String deliveryDate, String discountPercent)
            throws Exception {
        String discount = discountPercent == null ? "" : ", \"discountPercent\": " + discountPercent;
        String assigned = mockMvc.perform(post(ClientApi.BASE_PATH + "/" + clientId + "/companies/" + company
                        + "/services")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "serviceId": "%s", "deliveryDate": "%s"%s }"""
                                .formatted(serviceId, deliveryDate, discount)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(assigned, "$.id");
    }

    /** Records a payment from Mehta Textiles Pvt Ltd. */
    private void recordPayment(String amount, String dateReceived, String method) throws Exception {
        recordPayment(companyId, amount, dateReceived, method);
    }

    private void recordPayment(String company, String amount, String dateReceived, String method)
            throws Exception {
        mockMvc.perform(post(ApiPaths.V1 + "/clients/" + clientId + "/companies/" + company + "/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "amountReceived": %s, "dateReceived": "%s", "method": "%s" }"""
                                .formatted(amount, dateReceived, method)))
                .andExpect(status().isCreated());
    }

    private String companySummaryUrl(String company) {
        return ApiPaths.V1 + "/clients/" + clientId + "/companies/" + company + "/ledger-summary";
    }

    private String paymentsUrl() {
        return ApiPaths.V1 + "/clients/" + clientId + "/companies/" + companyId + "/payments";
    }

    private String ledgerUrl() {
        return ApiPaths.V1 + "/clients/" + clientId + "/companies/" + companyId + "/ledger-entries";
    }

    private String servicesUrl() {
        return ClientApi.BASE_PATH + "/" + clientId + "/companies/" + companyId + "/services";
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

    /**
     * @param billing the billingFrequency and invoiceSchedule members of the catalog payload
     */
    private String createCatalogService(String name, String fee, String billing) throws Exception {
        String created = mockMvc.perform(post(ServiceCatalogApi.BASE_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "serviceName": "%s",
                                  "category": "COMPLIANCE",
                                  "standardFee": %s,
                                  %s
                                }""".formatted(name, fee, billing)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(created, "$.id");
    }
}
