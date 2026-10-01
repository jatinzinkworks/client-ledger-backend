package com.psc.cl.servicecatalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.psc.cl.AbstractPostgresIT;
import com.psc.cl.servicecatalog.controller.ServiceCatalogApi;
import com.psc.cl.servicecatalog.model.CatalogService;
import com.psc.cl.servicecatalog.repository.CatalogServiceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.util.UUID;

class ServiceCatalogIT extends AbstractPostgresIT {

    private static final String SERVICES_URL = ServiceCatalogApi.BASE_PATH;

    private static final String MONTHLY_SERVICE_JSON = """
            {
              "serviceName": "GST Monthly Return Filing",
              "description": "Filing of GSTR-1 and GSTR-3B each month",
              "category": "COMPLIANCE",
              "billingFrequency": "MONTHLY",
              "standardFee": 5000.00,
              "invoiceSchedule": { "dayOfMonth": 7 }
            }""";

    @Autowired
    private CatalogServiceRepository catalogServiceRepository;

    @BeforeEach
    void clearCatalog() {
        catalogServiceRepository.deleteAll();
    }

    @Test
    @DisplayName("creates, reads, lists, updates and deletes a service end to end")
    void fullLifecycle() throws Exception {
        String created = mockMvc.perform(post(SERVICES_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MONTHLY_SERVICE_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.gstRatePercent").value(18.00))
                .andExpect(jsonPath("$.usedByCompanies").value(0))
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(created, "$.id");

        mockMvc.perform(get(SERVICES_URL + "/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.serviceName").value("GST Monthly Return Filing"))
                .andExpect(jsonPath("$.invoiceSchedule.dayOfMonth").value(7));

        mockMvc.perform(get(SERVICES_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(id));

        // Switching MONTHLY to ANNUAL must clear the old day-of-month-only schedule and set the
        // month, otherwise the database check constraint on the schedule columns would reject it.
        mockMvc.perform(put(SERVICES_URL + "/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "serviceName": "Statutory Audit",
                                  "category": "AUDIT",
                                  "billingFrequency": "ANNUAL",
                                  "standardFee": 50000.00,
                                  "invoiceSchedule": { "month": "APRIL", "dayOfMonth": 15 }
                                }"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.billingFrequency").value("ANNUAL"))
                .andExpect(jsonPath("$.invoiceSchedule.month").value("APRIL"))
                .andExpect(jsonPath("$.invoiceSchedule.monthOfQuarter").doesNotExist())
                // Omitted on the update, so cleared.
                .andExpect(jsonPath("$.description").doesNotExist());

        mockMvc.perform(delete(SERVICES_URL + "/" + id))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(SERVICES_URL + "/" + id))
                .andExpect(status().isNotFound());

        assertThat(catalogServiceRepository.count()).isZero();
    }

    @Test
    @DisplayName("stores a one-off service with every schedule column left null")
    void storesOneOffService() throws Exception {
        String created = mockMvc.perform(post(SERVICES_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "serviceName": "Company Incorporation",
                                  "category": "ADVISORY",
                                  "billingFrequency": "ONE_OFF",
                                  "standardFee": 25000.00
                                }"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.invoiceSchedule").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        UUID id = UUID.fromString(JsonPath.read(created, "$.id"));
        CatalogService stored = catalogServiceRepository.findById(id).orElseThrow();
        assertThat(stored.getInvoiceDayOfMonth()).isNull();
        assertThat(stored.getInvoiceMonthOfQuarter()).isNull();
        assertThat(stored.getInvoiceMonth()).isNull();
    }

    @Test
    @DisplayName("stores a quarterly service with its month of quarter")
    void storesQuarterlyService() throws Exception {
        mockMvc.perform(post(SERVICES_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "serviceName": "TDS Quarterly Return",
                                  "category": "TAX",
                                  "billingFrequency": "QUARTERLY",
                                  "standardFee": 7500.00,
                                  "invoiceSchedule": {
                                    "monthOfQuarter": "SECOND_MONTH",
                                    "dayOfMonth": 10
                                  }
                                }"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.invoiceSchedule.monthOfQuarter").value("SECOND_MONTH"))
                .andExpect(jsonPath("$.invoiceSchedule.dayOfMonth").value(10))
                .andExpect(jsonPath("$.invoiceSchedule.month").doesNotExist());
    }

    @Test
    @DisplayName("the unique name index rejects a duplicate differing only in case")
    void rejectsDuplicateNameIgnoringCase() throws Exception {
        mockMvc.perform(post(SERVICES_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MONTHLY_SERVICE_JSON))
                .andExpect(status().isCreated());

        mockMvc.perform(post(SERVICES_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "serviceName": "gst monthly return filing",
                                  "category": "COMPLIANCE",
                                  "billingFrequency": "MONTHLY",
                                  "standardFee": 5000.00,
                                  "invoiceSchedule": { "dayOfMonth": 7 }
                                }"""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESOURCE_ALREADY_EXISTS"));

        assertThat(catalogServiceRepository.count()).isOne();
    }

    @Test
    @DisplayName("a service keeps its own name on update without conflicting with itself")
    void updateKeepingOwnName() throws Exception {
        String created = mockMvc.perform(post(SERVICES_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MONTHLY_SERVICE_JSON))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(created, "$.id");

        mockMvc.perform(put(SERVICES_URL + "/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MONTHLY_SERVICE_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id));
    }

    @Test
    @DisplayName("lists services ordered by name")
    void listsOrderedByName() throws Exception {
        mockMvc.perform(post(SERVICES_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MONTHLY_SERVICE_JSON))
                .andExpect(status().isCreated());
        mockMvc.perform(post(SERVICES_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "serviceName": "Annual Audit",
                                  "category": "AUDIT",
                                  "billingFrequency": "ONE_OFF",
                                  "standardFee": 25000.00
                                }"""))
                .andExpect(status().isCreated());

        mockMvc.perform(get(SERVICES_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].serviceName").value("Annual Audit"))
                .andExpect(jsonPath("$[1].serviceName").value("GST Monthly Return Filing"));
    }

    @Test
    @DisplayName("refuses to delete a service companies still subscribe to")
    void refusesDeleteWhenInUse() throws Exception {
        String created = mockMvc.perform(post(SERVICES_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MONTHLY_SERVICE_JSON))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID id = UUID.fromString(JsonPath.read(created, "$.id"));

        // Nothing increments the subscriber count yet, so set it directly to exercise the guard.
        CatalogService stored = catalogServiceRepository.findById(id).orElseThrow();
        stored.setUsedByCompanies(3);
        catalogServiceRepository.saveAndFlush(stored);

        mockMvc.perform(delete(SERVICES_URL + "/" + id))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESOURCE_IN_USE"));

        assertThat(catalogServiceRepository.existsById(id)).isTrue();
    }

    @Test
    @DisplayName("returns 404 for an unknown id and 400 for a malformed one")
    void unknownAndMalformedIds() throws Exception {
        mockMvc.perform(get(SERVICES_URL + "/" + UUID.randomUUID()))
                .andExpect(status().isNotFound());

        mockMvc.perform(get(SERVICES_URL + "/not-a-uuid"))
                .andExpect(status().isBadRequest());
    }
}
