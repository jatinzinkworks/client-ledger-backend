package com.psc.cl.servicecatalog.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.psc.cl.servicecatalog.dto.CatalogServiceRequest;
import com.psc.cl.servicecatalog.dto.CatalogServiceResponse;
import com.psc.cl.servicecatalog.dto.InvoiceSchedule;
import com.psc.cl.servicecatalog.exception.CatalogServiceNotFoundException;
import com.psc.cl.servicecatalog.exception.ServiceInUseException;
import com.psc.cl.servicecatalog.exception.ServiceNameAlreadyExistsException;
import com.psc.cl.servicecatalog.model.BillingFrequency;
import com.psc.cl.servicecatalog.model.QuarterInvoiceMonth;
import com.psc.cl.servicecatalog.model.ServiceCategory;
import com.psc.cl.servicecatalog.service.ServiceCatalogService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.Month;
import java.util.List;
import java.util.UUID;

@WebMvcTest(ServiceCatalogController.class)
class ServiceCatalogControllerTest {

    private static final String SERVICES_URL = ServiceCatalogApi.BASE_PATH;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ServiceCatalogService serviceCatalogService;

    @Test
    @DisplayName("returns 201 and deserialises a monthly service onto the request record")
    void createsMonthlyService() throws Exception {
        when(serviceCatalogService.createService(any())).thenReturn(monthlyResponse());

        mockMvc.perform(post(SERVICES_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "serviceName": "GST Monthly Return Filing",
                                  "description": "Filing of GSTR-1 and GSTR-3B each month",
                                  "category": "COMPLIANCE",
                                  "billingFrequency": "MONTHLY",
                                  "standardFee": 5000.00,
                                  "gstRatePercent": 18.00,
                                  "invoiceSchedule": { "dayOfMonth": 7 }
                                }"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.serviceName").value("GST Monthly Return Filing"))
                .andExpect(jsonPath("$.category").value("COMPLIANCE"))
                .andExpect(jsonPath("$.billingFrequency").value("MONTHLY"))
                .andExpect(jsonPath("$.invoiceSchedule.dayOfMonth").value(7))
                .andExpect(jsonPath("$.usedByCompanies").value(12));

        ArgumentCaptor<CatalogServiceRequest> captor =
                ArgumentCaptor.forClass(CatalogServiceRequest.class);
        verify(serviceCatalogService).createService(captor.capture());
        CatalogServiceRequest received = captor.getValue();
        assertThat(received.serviceName()).isEqualTo("GST Monthly Return Filing");
        assertThat(received.category()).isEqualTo(ServiceCategory.COMPLIANCE);
        assertThat(received.billingFrequency()).isEqualTo(BillingFrequency.MONTHLY);
        assertThat(received.standardFee()).isEqualByComparingTo("5000.00");
        assertThat(received.invoiceSchedule().dayOfMonth()).isEqualTo(7);
    }

    @Test
    @DisplayName("accepts a quarterly service carrying a month of quarter")
    void acceptsQuarterlyService() throws Exception {
        when(serviceCatalogService.createService(any())).thenReturn(monthlyResponse());

        mockMvc.perform(post(SERVICES_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "serviceName": "TDS Quarterly Return",
                                  "category": "TAX",
                                  "billingFrequency": "QUARTERLY",
                                  "standardFee": 7500.00,
                                  "invoiceSchedule": {
                                    "monthOfQuarter": "FIRST_MONTH",
                                    "dayOfMonth": 10
                                  }
                                }"""))
                .andExpect(status().isCreated());

        ArgumentCaptor<CatalogServiceRequest> captor =
                ArgumentCaptor.forClass(CatalogServiceRequest.class);
        verify(serviceCatalogService).createService(captor.capture());
        assertThat(captor.getValue().invoiceSchedule().monthOfQuarter())
                .isEqualTo(QuarterInvoiceMonth.FIRST_MONTH);
    }

    @Test
    @DisplayName("accepts an annual service carrying a named month")
    void acceptsAnnualService() throws Exception {
        when(serviceCatalogService.createService(any())).thenReturn(monthlyResponse());

        mockMvc.perform(post(SERVICES_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "serviceName": "Statutory Audit",
                                  "category": "AUDIT",
                                  "billingFrequency": "ANNUAL",
                                  "standardFee": 50000.00,
                                  "invoiceSchedule": { "month": "APRIL", "dayOfMonth": 15 }
                                }"""))
                .andExpect(status().isCreated());

        ArgumentCaptor<CatalogServiceRequest> captor =
                ArgumentCaptor.forClass(CatalogServiceRequest.class);
        verify(serviceCatalogService).createService(captor.capture());
        assertThat(captor.getValue().invoiceSchedule().month()).isEqualTo(Month.APRIL);
    }

    @Test
    @DisplayName("accepts a one-off service with no invoice schedule")
    void acceptsOneOffService() throws Exception {
        when(serviceCatalogService.createService(any())).thenReturn(monthlyResponse());

        mockMvc.perform(post(SERVICES_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "serviceName": "Company Incorporation",
                                  "category": "ADVISORY",
                                  "billingFrequency": "ONE_OFF",
                                  "standardFee": 25000.00
                                }"""))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("returns 400 listing every missing mandatory field")
    void rejectsMissingMandatoryFields() throws Exception {
        mockMvc.perform(post(SERVICES_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[*].field", hasItem("serviceName")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("category")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("billingFrequency")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("standardFee")));

        verify(serviceCatalogService, never()).createService(any());
    }

    @Test
    @DisplayName("returns 400 when a one-off service carries an invoice schedule")
    void rejectsScheduleOnOneOff() throws Exception {
        mockMvc.perform(post(SERVICES_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "serviceName": "Company Incorporation",
                                  "category": "ADVISORY",
                                  "billingFrequency": "ONE_OFF",
                                  "standardFee": 25000.00,
                                  "invoiceSchedule": { "dayOfMonth": 7 }
                                }"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[*].field", hasItem("scheduleAbsentForOneOff")));

        verify(serviceCatalogService, never()).createService(any());
    }

    @Test
    @DisplayName("returns 400 when a monthly service omits the day of month")
    void rejectsMonthlyWithoutDayOfMonth() throws Exception {
        mockMvc.perform(post(SERVICES_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "serviceName": "GST Monthly Return Filing",
                                  "category": "COMPLIANCE",
                                  "billingFrequency": "MONTHLY",
                                  "standardFee": 5000.00
                                }"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", hasItem("dayOfMonthPresentWhenRecurring")));

        verify(serviceCatalogService, never()).createService(any());
    }

    @Test
    @DisplayName("returns 400 when a quarterly service omits the month of quarter")
    void rejectsQuarterlyWithoutMonthOfQuarter() throws Exception {
        mockMvc.perform(post(SERVICES_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "serviceName": "TDS Quarterly Return",
                                  "category": "TAX",
                                  "billingFrequency": "QUARTERLY",
                                  "standardFee": 7500.00,
                                  "invoiceSchedule": { "dayOfMonth": 10 }
                                }"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", hasItem("monthOfQuarterMatchingBilling")));

        verify(serviceCatalogService, never()).createService(any());
    }

    @Test
    @DisplayName("returns 400 when a monthly service carries a month meant for annual billing")
    void rejectsMonthOnMonthlyBilling() throws Exception {
        mockMvc.perform(post(SERVICES_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "serviceName": "GST Monthly Return Filing",
                                  "category": "COMPLIANCE",
                                  "billingFrequency": "MONTHLY",
                                  "standardFee": 5000.00,
                                  "invoiceSchedule": { "dayOfMonth": 7, "month": "APRIL" }
                                }"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", hasItem("monthMatchingBilling")));

        verify(serviceCatalogService, never()).createService(any());
    }

    @Test
    @DisplayName("returns 400 when the day of month is out of range")
    void rejectsDayOfMonthOutOfRange() throws Exception {
        mockMvc.perform(post(SERVICES_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "serviceName": "GST Monthly Return Filing",
                                  "category": "COMPLIANCE",
                                  "billingFrequency": "MONTHLY",
                                  "standardFee": 5000.00,
                                  "invoiceSchedule": { "dayOfMonth": 32 }
                                }"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field",
                        hasItem("invoiceSchedule.dayOfMonth")));

        verify(serviceCatalogService, never()).createService(any());
    }

    @Test
    @DisplayName("returns 400 when the GST rate is above 100 percent")
    void rejectsGstRateAboveHundred() throws Exception {
        mockMvc.perform(post(SERVICES_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "serviceName": "GST Monthly Return Filing",
                                  "category": "COMPLIANCE",
                                  "billingFrequency": "MONTHLY",
                                  "standardFee": 5000.00,
                                  "gstRatePercent": 120.00,
                                  "invoiceSchedule": { "dayOfMonth": 7 }
                                }"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", hasItem("gstRatePercent")));

        verify(serviceCatalogService, never()).createService(any());
    }

    @Test
    @DisplayName("ignores usedByCompanies supplied on the request")
    void ignoresUsedByCompaniesOnRequest() throws Exception {
        when(serviceCatalogService.createService(any())).thenReturn(monthlyResponse());

        mockMvc.perform(post(SERVICES_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "serviceName": "GST Monthly Return Filing",
                                  "category": "COMPLIANCE",
                                  "billingFrequency": "MONTHLY",
                                  "standardFee": 5000.00,
                                  "usedByCompanies": 999,
                                  "invoiceSchedule": { "dayOfMonth": 7 }
                                }"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.usedByCompanies").value(12));
    }

    @Test
    @DisplayName("returns 409 when a service already uses the name")
    void returnsConflictOnDuplicateName() throws Exception {
        when(serviceCatalogService.createService(any()))
                .thenThrow(new ServiceNameAlreadyExistsException("GST Monthly Return Filing"));

        mockMvc.perform(post(SERVICES_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "serviceName": "GST Monthly Return Filing",
                                  "category": "COMPLIANCE",
                                  "billingFrequency": "MONTHLY",
                                  "standardFee": 5000.00,
                                  "invoiceSchedule": { "dayOfMonth": 7 }
                                }"""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESOURCE_ALREADY_EXISTS"));
    }

    @Test
    @DisplayName("returns 200 with the service on fetch by id")
    void fetchesServiceById() throws Exception {
        UUID id = UUID.randomUUID();
        when(serviceCatalogService.getService(id)).thenReturn(monthlyResponse());

        mockMvc.perform(get(SERVICES_URL + "/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.serviceName").value("GST Monthly Return Filing"))
                .andExpect(jsonPath("$.invoiceSchedule.dayOfMonth").value(7));
    }

    @Test
    @DisplayName("returns 404 when the service id is unknown")
    void returnsNotFoundForUnknownId() throws Exception {
        UUID id = UUID.randomUUID();
        when(serviceCatalogService.getService(id)).thenThrow(new CatalogServiceNotFoundException(id));

        mockMvc.perform(get(SERVICES_URL + "/" + id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("returns 400 when the path identifier is not a UUID")
    void returnsBadRequestForMalformedId() throws Exception {
        mockMvc.perform(get(SERVICES_URL + "/not-a-uuid"))
                .andExpect(status().isBadRequest());

        verify(serviceCatalogService, never()).getService(any());
    }

    @Test
    @DisplayName("returns 200 with the catalog on list")
    void listsServices() throws Exception {
        when(serviceCatalogService.listServices()).thenReturn(List.of(monthlyResponse()));

        mockMvc.perform(get(SERVICES_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].serviceName").value("GST Monthly Return Filing"))
                .andExpect(jsonPath("$[0].usedByCompanies").value(12));
    }

    @Test
    @DisplayName("returns 200 with an empty array when the catalog is empty")
    void listsEmptyCatalog() throws Exception {
        when(serviceCatalogService.listServices()).thenReturn(List.of());

        mockMvc.perform(get(SERVICES_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("returns 200 on update and passes the id through")
    void updatesService() throws Exception {
        UUID id = UUID.randomUUID();
        when(serviceCatalogService.updateService(any(), any())).thenReturn(monthlyResponse());

        mockMvc.perform(put(SERVICES_URL + "/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "serviceName": "GST Monthly Return Filing",
                                  "category": "COMPLIANCE",
                                  "billingFrequency": "MONTHLY",
                                  "standardFee": 6000.00,
                                  "invoiceSchedule": { "dayOfMonth": 9 }
                                }"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.serviceName").value("GST Monthly Return Filing"));

        ArgumentCaptor<UUID> idCaptor = ArgumentCaptor.forClass(UUID.class);
        ArgumentCaptor<CatalogServiceRequest> bodyCaptor =
                ArgumentCaptor.forClass(CatalogServiceRequest.class);
        verify(serviceCatalogService).updateService(idCaptor.capture(), bodyCaptor.capture());
        assertThat(idCaptor.getValue()).isEqualTo(id);
        assertThat(bodyCaptor.getValue().standardFee()).isEqualByComparingTo("6000.00");
        assertThat(bodyCaptor.getValue().invoiceSchedule().dayOfMonth()).isEqualTo(9);
    }

    @Test
    @DisplayName("applies the same schedule rules on update as on create")
    void updateRejectsScheduleMismatch() throws Exception {
        mockMvc.perform(put(SERVICES_URL + "/" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "serviceName": "Company Incorporation",
                                  "category": "ADVISORY",
                                  "billingFrequency": "ONE_OFF",
                                  "standardFee": 25000.00,
                                  "invoiceSchedule": { "dayOfMonth": 7 }
                                }"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", hasItem("scheduleAbsentForOneOff")));

        verify(serviceCatalogService, never()).updateService(any(), any());
    }

    @Test
    @DisplayName("returns 404 when updating an unknown service")
    void updateReturnsNotFound() throws Exception {
        UUID id = UUID.randomUUID();
        when(serviceCatalogService.updateService(any(), any()))
                .thenThrow(new CatalogServiceNotFoundException(id));

        mockMvc.perform(put(SERVICES_URL + "/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "serviceName": "GST Monthly Return Filing",
                                  "category": "COMPLIANCE",
                                  "billingFrequency": "MONTHLY",
                                  "standardFee": 5000.00,
                                  "invoiceSchedule": { "dayOfMonth": 7 }
                                }"""))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("returns 204 with no body on delete")
    void deletesService() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(delete(SERVICES_URL + "/" + id))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(serviceCatalogService).deleteService(id);
    }

    @Test
    @DisplayName("returns 404 when deleting an unknown service")
    void deleteReturnsNotFound() throws Exception {
        UUID id = UUID.randomUUID();
        doThrow(new CatalogServiceNotFoundException(id))
                .when(serviceCatalogService).deleteService(id);

        mockMvc.perform(delete(SERVICES_URL + "/" + id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("returns 409 when deleting a service companies still subscribe to")
    void deleteReturnsConflictWhenInUse() throws Exception {
        UUID id = UUID.randomUUID();
        doThrow(new ServiceInUseException("GST Monthly Return Filing", 12))
                .when(serviceCatalogService).deleteService(id);

        mockMvc.perform(delete(SERVICES_URL + "/" + id))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESOURCE_IN_USE"));
    }

    private CatalogServiceResponse monthlyResponse() {
        Instant now = Instant.now();
        return CatalogServiceResponse.builder()
                .id(UUID.randomUUID())
                .serviceName("GST Monthly Return Filing")
                .description("Filing of GSTR-1 and GSTR-3B each month")
                .category(ServiceCategory.COMPLIANCE)
                .billingFrequency(BillingFrequency.MONTHLY)
                .standardFee(new BigDecimal("5000.00"))
                .gstRatePercent(new BigDecimal("18.00"))
                .invoiceSchedule(InvoiceSchedule.builder().dayOfMonth(7).build())
                .usedByCompanies(12)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }
}
