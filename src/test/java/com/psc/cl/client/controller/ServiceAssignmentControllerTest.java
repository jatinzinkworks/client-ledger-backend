package com.psc.cl.client.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.psc.cl.client.dto.ServiceAssignmentRequest;
import com.psc.cl.client.dto.ServiceAssignmentResponse;
import com.psc.cl.client.dto.ServiceAssignmentUpdateRequest;
import com.psc.cl.client.exception.AssignedServiceNotFoundException;
import com.psc.cl.client.exception.CompanyNotFoundException;
import com.psc.cl.client.exception.ServiceAssignmentNotFoundException;
import com.psc.cl.client.service.ServiceAssignmentService;
import com.psc.cl.servicecatalog.model.BillingFrequency;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@WebMvcTest(ServiceAssignmentController.class)
class ServiceAssignmentControllerTest {

    private static final UUID CLIENT_ID = UUID.fromString("7a3d9e10-5b2c-4f81-a6e4-2c8b1d0f9e35");

    private static final UUID COMPANY_ID = UUID.fromString("4c1f8a52-2d6b-4e93-b7a0-9e5d3c1f6a28");

    private static final UUID SERVICE_ID = UUID.fromString("3b8e5f21-9c4d-4a17-8e62-1f0d7a9c5b34");

    private static final String SERVICES_URL =
            ClientApi.BASE_PATH + "/" + CLIENT_ID + "/companies/" + COMPANY_ID + "/services";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ServiceAssignmentService serviceAssignmentService;

    @Test
    @DisplayName("returns 201 with a Location header and passes both ids and the body through")
    void assignsService() throws Exception {
        UUID assignmentId = UUID.randomUUID();
        when(serviceAssignmentService.assignService(any(), any(), any())).thenReturn(response(assignmentId));

        mockMvc.perform(post(SERVICES_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "serviceId": "%s",
                                  "deliveryDate": "2026-10-15",
                                  "discountPercent": 5,
                                  "comment": "Filing for September 2026"
                                }""".formatted(SERVICE_ID)))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, endsWith(SERVICES_URL + "/" + assignmentId)))
                .andExpect(jsonPath("$.serviceName").value("GST Monthly Return Filing"))
                .andExpect(jsonPath("$.serviceType").value("MONTHLY"))
                .andExpect(jsonPath("$.deliveryDate").value("2026-10-15"))
                .andExpect(jsonPath("$.nextDueDate").value("2026-11-07"));

        ArgumentCaptor<ServiceAssignmentRequest> captor = ArgumentCaptor.forClass(ServiceAssignmentRequest.class);
        verify(serviceAssignmentService).assignService(eq(CLIENT_ID), eq(COMPANY_ID), captor.capture());
        ServiceAssignmentRequest received = captor.getValue();
        assertThat(received.serviceId()).isEqualTo(SERVICE_ID);
        assertThat(received.deliveryDate()).isEqualTo(LocalDate.of(2026, 10, 15));
        assertThat(received.discountPercent()).isEqualByComparingTo("5");
        assertThat(received.comment()).isEqualTo("Filing for September 2026");
    }

    @Test
    @DisplayName("accepts a request with only the service and the delivery date")
    void acceptsMinimalRequest() throws Exception {
        when(serviceAssignmentService.assignService(any(), any(), any())).thenReturn(response(UUID.randomUUID()));

        mockMvc.perform(post(SERVICES_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "serviceId": "%s", "deliveryDate": "2026-10-15" }""".formatted(SERVICE_ID)))
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
                .andExpect(jsonPath("$.errors[*].field", hasItem("serviceId")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("deliveryDate")));

        verify(serviceAssignmentService, never()).assignService(any(), any(), any());
    }

    @Test
    @DisplayName("returns 400 when the discount is out of range or the comment is too long")
    void rejectsInvalidDiscountAndComment() throws Exception {
        mockMvc.perform(post(SERVICES_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "serviceId": "%s",
                                  "deliveryDate": "2026-10-15",
                                  "discountPercent": 100.5,
                                  "comment": "%s"
                                }""".formatted(SERVICE_ID, "x".repeat(1001))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", hasItem("discountPercent")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("comment")));

        verify(serviceAssignmentService, never()).assignService(any(), any(), any());
    }

    @Test
    @DisplayName("returns 400 when the delivery date is not an ISO date")
    void rejectsMalformedDeliveryDate() throws Exception {
        mockMvc.perform(post(SERVICES_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "serviceId": "%s", "deliveryDate": "15/10/2026" }""".formatted(SERVICE_ID)))
                .andExpect(status().isBadRequest());

        verify(serviceAssignmentService, never()).assignService(any(), any(), any());
    }

    @Test
    @DisplayName("returns 404 when the client does not hold the company")
    void returnsNotFoundForUnknownCompany() throws Exception {
        when(serviceAssignmentService.assignService(any(), any(), any()))
                .thenThrow(new CompanyNotFoundException(CLIENT_ID, COMPANY_ID));

        mockMvc.perform(post(SERVICES_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "serviceId": "%s", "deliveryDate": "2026-10-15" }""".formatted(SERVICE_ID)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("returns 404 when the service is not in the catalog")
    void returnsNotFoundForUnknownService() throws Exception {
        when(serviceAssignmentService.assignService(any(), any(), any()))
                .thenThrow(new AssignedServiceNotFoundException(SERVICE_ID));

        mockMvc.perform(post(SERVICES_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "serviceId": "%s", "deliveryDate": "2026-10-15" }""".formatted(SERVICE_ID)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("returns 400 when the company identifier in the path is not a UUID")
    void rejectsMalformedCompanyId() throws Exception {
        mockMvc.perform(post(ClientApi.BASE_PATH + "/" + CLIENT_ID + "/companies/not-a-uuid/services")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "serviceId": "%s", "deliveryDate": "2026-10-15" }""".formatted(SERVICE_ID)))
                .andExpect(status().isBadRequest());

        verify(serviceAssignmentService, never()).assignService(any(), any(), any());
    }

    @Test
    @DisplayName("returns 200 with the company's services on list")
    void listsServices() throws Exception {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        when(serviceAssignmentService.listServices(CLIENT_ID, COMPANY_ID))
                .thenReturn(List.of(response(first), response(second)));

        mockMvc.perform(get(SERVICES_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(first.toString()))
                .andExpect(jsonPath("$[1].id").value(second.toString()))
                .andExpect(jsonPath("$[0].serviceName").value("GST Monthly Return Filing"));
    }

    @Test
    @DisplayName("returns 200 with an empty array when the company has no services")
    void listsNoServices() throws Exception {
        when(serviceAssignmentService.listServices(CLIENT_ID, COMPANY_ID)).thenReturn(List.of());

        mockMvc.perform(get(SERVICES_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("returns 404 when listing services of a company the client does not hold")
    void listReturnsNotFoundForUnknownCompany() throws Exception {
        when(serviceAssignmentService.listServices(CLIENT_ID, COMPANY_ID))
                .thenThrow(new CompanyNotFoundException(CLIENT_ID, COMPANY_ID));

        mockMvc.perform(get(SERVICES_URL))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("returns 200 with one service of the company and passes every id through")
    void fetchesService() throws Exception {
        UUID assignmentId = UUID.randomUUID();
        when(serviceAssignmentService.getService(CLIENT_ID, COMPANY_ID, assignmentId))
                .thenReturn(response(assignmentId));

        mockMvc.perform(get(SERVICES_URL + "/" + assignmentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(assignmentId.toString()))
                .andExpect(jsonPath("$.deliveryDate").value("2026-10-15"))
                .andExpect(jsonPath("$.discountPercent").value(5.0))
                .andExpect(jsonPath("$.comment").value("Filing for September 2026"));
    }

    @Test
    @DisplayName("returns 404 when the company does not hold the assigned service")
    void fetchReturnsNotFound() throws Exception {
        UUID assignmentId = UUID.randomUUID();
        when(serviceAssignmentService.getService(CLIENT_ID, COMPANY_ID, assignmentId))
                .thenThrow(new ServiceAssignmentNotFoundException(COMPANY_ID, assignmentId));

        mockMvc.perform(get(SERVICES_URL + "/" + assignmentId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("returns 400 when the assignment identifier is not a UUID")
    void fetchRejectsMalformedId() throws Exception {
        mockMvc.perform(get(SERVICES_URL + "/not-a-uuid"))
                .andExpect(status().isBadRequest());

        verify(serviceAssignmentService, never()).getService(any(), any(), any());
    }

    @Test
    @DisplayName("returns 200 on update and passes every id and the body through")
    void updatesService() throws Exception {
        UUID assignmentId = UUID.randomUUID();
        when(serviceAssignmentService.updateService(any(), any(), any(), any())).thenReturn(response(assignmentId));

        mockMvc.perform(put(SERVICES_URL + "/" + assignmentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "deliveryDate": "2026-11-20",
                                  "discountPercent": 12,
                                  "comment": "Delivered late"
                                }"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(assignmentId.toString()));

        ArgumentCaptor<ServiceAssignmentUpdateRequest> captor =
                ArgumentCaptor.forClass(ServiceAssignmentUpdateRequest.class);
        verify(serviceAssignmentService).updateService(eq(CLIENT_ID), eq(COMPANY_ID), eq(assignmentId),
                captor.capture());
        ServiceAssignmentUpdateRequest received = captor.getValue();
        assertThat(received.deliveryDate()).isEqualTo(LocalDate.of(2026, 11, 20));
        assertThat(received.discountPercent()).isEqualByComparingTo("12");
        assertThat(received.comment()).isEqualTo("Delivered late");
    }

    @Test
    @DisplayName("returns 400 on update when the delivery date is missing or the discount is out of range")
    void updateRejectsInvalidBody() throws Exception {
        mockMvc.perform(put(SERVICES_URL + "/" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "discountPercent": -5 }"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", hasItem("deliveryDate")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("discountPercent")));

        verify(serviceAssignmentService, never()).updateService(any(), any(), any(), any());
    }

    @Test
    @DisplayName("returns 404 when updating a service the company does not hold")
    void updateReturnsNotFound() throws Exception {
        UUID assignmentId = UUID.randomUUID();
        when(serviceAssignmentService.updateService(any(), any(), any(), any()))
                .thenThrow(new ServiceAssignmentNotFoundException(COMPANY_ID, assignmentId));

        mockMvc.perform(put(SERVICES_URL + "/" + assignmentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "deliveryDate": "2026-11-20" }"""))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("returns 204 with no body on remove")
    void removesService() throws Exception {
        UUID assignmentId = UUID.randomUUID();

        mockMvc.perform(delete(SERVICES_URL + "/" + assignmentId))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(serviceAssignmentService).removeService(CLIENT_ID, COMPANY_ID, assignmentId);
    }

    @Test
    @DisplayName("returns 404 when removing a service the company does not hold")
    void removeReturnsNotFound() throws Exception {
        UUID assignmentId = UUID.randomUUID();
        doThrow(new ServiceAssignmentNotFoundException(COMPANY_ID, assignmentId))
                .when(serviceAssignmentService).removeService(CLIENT_ID, COMPANY_ID, assignmentId);

        mockMvc.perform(delete(SERVICES_URL + "/" + assignmentId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    private ServiceAssignmentResponse response(UUID id) {
        Instant now = Instant.now();
        return ServiceAssignmentResponse.builder()
                .id(id)
                .clientId(CLIENT_ID)
                .companyId(COMPANY_ID)
                .serviceId(SERVICE_ID)
                .serviceName("GST Monthly Return Filing")
                .serviceType(BillingFrequency.MONTHLY)
                .deliveryDate(LocalDate.of(2026, 10, 15))
                .nextDueDate(LocalDate.of(2026, 11, 7))
                .discountPercent(new BigDecimal("5.00"))
                .comment("Filing for September 2026")
                .createdAt(now)
                .updatedAt(now)
                .build();
    }
}
