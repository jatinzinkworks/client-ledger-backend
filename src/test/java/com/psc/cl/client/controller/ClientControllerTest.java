package com.psc.cl.client.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.psc.cl.client.dto.ClientRequest;
import com.psc.cl.client.dto.ClientResponse;
import com.psc.cl.client.dto.ClientSummaryResponse;
import com.psc.cl.client.dto.ClientUpdateRequest;
import com.psc.cl.client.dto.CompanyResponse;
import com.psc.cl.client.dto.WhatsAppOptInRequest;
import com.psc.cl.client.exception.AssignedManagerNotFoundException;
import com.psc.cl.client.exception.ClientHasServicesException;
import com.psc.cl.client.exception.ClientNotFoundException;
import com.psc.cl.client.model.CompanyType;
import com.psc.cl.client.service.ClientService;
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
import java.util.List;
import java.util.UUID;

@WebMvcTest(ClientController.class)
class ClientControllerTest {

    private static final String CLIENTS_URL = ClientApi.BASE_PATH;

    private static final String MANAGER_ID = "e9b2c4d1-7f35-4a68-91c0-3d8a5e2f6b47";

    /** A complete, valid client payload. Variants below alter one field at a time. */
    private static final String VALID_CLIENT_JSON = """
            {
              "firstName": "Rohan",
              "lastName": "Mehta",
              "phone": "+91 98200 12345",
              "email": "rohan.mehta@example.com",
              "managerId": "%s",
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
            }""".formatted(MANAGER_ID);

    /** A complete, valid client update payload; it carries no companies. */
    private static final String UPDATE_JSON = """
            {
              "firstName": "Rohit",
              "lastName": "Mehta",
              "phone": "+91 98200 12345",
              "email": "rohit.mehta@example.com",
              "managerId": "%s",
              "discountPercent": 15
            }""".formatted(MANAGER_ID);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ClientService clientService;

    @Test
    @DisplayName("returns 201 with a Location header and deserialises the body onto the record")
    void createsClient() throws Exception {
        when(clientService.createClient(any())).thenReturn(response());

        mockMvc.perform(post(CLIENTS_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_CLIENT_JSON))
                .andExpect(status().isCreated())
                .andExpect(header().exists(HttpHeaders.LOCATION))
                .andExpect(jsonPath("$.firstName").value("Rohan"))
                .andExpect(jsonPath("$.managerId").value(MANAGER_ID))
                .andExpect(jsonPath("$.managerName").value("Pranay Singhal"))
                .andExpect(jsonPath("$.companies.length()").value(1))
                .andExpect(jsonPath("$.companies[0].companyType").value("PRIVATE_LIMITED"));

        ArgumentCaptor<ClientRequest> captor = ArgumentCaptor.forClass(ClientRequest.class);
        verify(clientService).createClient(captor.capture());
        ClientRequest received = captor.getValue();
        assertThat(received.firstName()).isEqualTo("Rohan");
        assertThat(received.lastName()).isEqualTo("Mehta");
        assertThat(received.phone()).isEqualTo("+91 98200 12345");
        assertThat(received.email()).isEqualTo("rohan.mehta@example.com");
        assertThat(received.managerId()).isEqualTo(UUID.fromString(MANAGER_ID));
        assertThat(received.companies()).hasSize(2);
        assertThat(received.companies().get(1).companyType()).isEqualTo(CompanyType.HUF);
        assertThat(received.companies().get(1).address())
                .isEqualTo("4 Park Street, Kolkata, West Bengal 700016");
    }

    @Test
    @DisplayName("returns 400 listing every missing mandatory field")
    void rejectsMissingMandatoryFields() throws Exception {
        mockMvc.perform(post(CLIENTS_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[*].field", hasItem("firstName")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("lastName")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("phone")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("email")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("managerId")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("companies")));

        verify(clientService, never()).createClient(any());
    }

    @Test
    @DisplayName("returns 400 when no company is supplied")
    void rejectsEmptyCompanies() throws Exception {
        mockMvc.perform(post(CLIENTS_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(clientJson("+91 98200 12345", "rohan.mehta@example.com", "[]")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", hasItem("companies")));

        verify(clientService, never()).createClient(any());
    }

    @Test
    @DisplayName("returns 400 naming the nested field when a company is incomplete")
    void rejectsIncompleteCompany() throws Exception {
        mockMvc.perform(post(CLIENTS_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(clientJson("+91 98200 12345", "rohan.mehta@example.com", """
                                [
                                  {
                                    "companyName": "Mehta Textiles Pvt Ltd",
                                    "companyType": "PRIVATE_LIMITED",
                                    "address": "12 MG Road, Bengaluru, Karnataka 560001"
                                  },
                                  { "companyName": " " }
                                ]""")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", hasItem("companies[1].companyName")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("companies[1].companyType")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("companies[1].address")));

        verify(clientService, never()).createClient(any());
    }

    @Test
    @DisplayName("returns 400 when the company type is not one of the allowed values")
    void rejectsUnknownCompanyType() throws Exception {
        mockMvc.perform(post(CLIENTS_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(clientJson("+91 98200 12345", "rohan.mehta@example.com", """
                                [
                                  {
                                    "companyName": "Mehta Textiles",
                                    "companyType": "CORPORATION",
                                    "address": "12 MG Road, Bengaluru, Karnataka 560001"
                                  }
                                ]""")))
                .andExpect(status().isBadRequest());

        verify(clientService, never()).createClient(any());
    }

    @Test
    @DisplayName("returns 400 when the email is malformed")
    void rejectsMalformedEmail() throws Exception {
        mockMvc.perform(post(CLIENTS_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(clientJson("+91 98200 12345", "not-an-email", oneCompany())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", hasItem("email")));

        verify(clientService, never()).createClient(any());
    }

    @Test
    @DisplayName("returns 400 when the phone number contains letters")
    void rejectsMalformedPhone() throws Exception {
        mockMvc.perform(post(CLIENTS_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(clientJson("call me maybe", "rohan.mehta@example.com", oneCompany())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", hasItem("phone")));

        verify(clientService, never()).createClient(any());
    }

    @Test
    @DisplayName("returns 404 when the assigned manager does not exist")
    void returnsNotFoundForUnknownManager() throws Exception {
        when(clientService.createClient(any()))
                .thenThrow(new AssignedManagerNotFoundException(UUID.fromString(MANAGER_ID)));

        mockMvc.perform(post(CLIENTS_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_CLIENT_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("returns 200 with the client and their companies in full on fetch by id")
    void fetchesClientById() throws Exception {
        UUID id = UUID.randomUUID();
        when(clientService.getClient(id)).thenReturn(response());

        mockMvc.perform(get(CLIENTS_URL + "/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("rohan.mehta@example.com"))
                .andExpect(jsonPath("$.managerName").value("Pranay Singhal"))
                .andExpect(jsonPath("$.companies[0].companyType").value("PRIVATE_LIMITED"))
                .andExpect(jsonPath("$.companies[0].address").value("12 MG Road, Bengaluru, Karnataka 560001"))
                .andExpect(jsonPath("$.totalPending").value(19630.0));
    }

    @Test
    @DisplayName("returns 404 when the client id is unknown")
    void returnsNotFoundForUnknownId() throws Exception {
        UUID id = UUID.randomUUID();
        when(clientService.getClient(id)).thenThrow(new ClientNotFoundException(id));

        mockMvc.perform(get(CLIENTS_URL + "/" + id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("returns 400 when the path identifier is not a UUID")
    void returnsBadRequestForMalformedId() throws Exception {
        mockMvc.perform(get(CLIENTS_URL + "/not-a-uuid"))
                .andExpect(status().isBadRequest());

        verify(clientService, never()).getClient(any());
    }

    @Test
    @DisplayName("returns 200 with company names only on list")
    void listsClients() throws Exception {
        when(clientService.listClients(null)).thenReturn(List.of(ClientSummaryResponse.builder()
                .id(UUID.randomUUID())
                .firstName("Rohan")
                .lastName("Mehta")
                .phone("+91 98200 12345")
                .email("rohan.mehta@example.com")
                .managerId(UUID.fromString(MANAGER_ID))
                .managerName("Pranay Singhal")
                .companyNames(List.of("Mehta Textiles Pvt Ltd", "Rohan Mehta HUF"))
                .totalPending(new BigDecimal("19630.00"))
                .build()));

        mockMvc.perform(get(CLIENTS_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].managerName").value("Pranay Singhal"))
                .andExpect(jsonPath("$[0].companyNames[0]").value("Mehta Textiles Pvt Ltd"))
                .andExpect(jsonPath("$[0].companyNames[1]").value("Rohan Mehta HUF"))
                .andExpect(jsonPath("$[0].totalPending").value(19630.0))
                .andExpect(jsonPath("$[0].companies").doesNotExist());
    }

    @Test
    @DisplayName("returns 200 with an empty array when no client has been added")
    void listsNoClients() throws Exception {
        when(clientService.listClients(null)).thenReturn(List.of());

        mockMvc.perform(get(CLIENTS_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("passes the manager filter through to the service")
    void listsClientsOfManager() throws Exception {
        when(clientService.listClients(UUID.fromString(MANAGER_ID))).thenReturn(List.of());

        mockMvc.perform(get(CLIENTS_URL).param("managerId", MANAGER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());

        verify(clientService).listClients(UUID.fromString(MANAGER_ID));
    }

    @Test
    @DisplayName("returns 400 when the manager filter is not a UUID")
    void listRejectsMalformedManagerId() throws Exception {
        mockMvc.perform(get(CLIENTS_URL).param("managerId", "not-a-uuid"))
                .andExpect(status().isBadRequest());

        verify(clientService, never()).listClients(any());
    }

    @Test
    @DisplayName("passes client and company discounts through, leaving an omitted one null")
    void acceptsDiscounts() throws Exception {
        when(clientService.createClient(any())).thenReturn(response());

        mockMvc.perform(post(CLIENTS_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(discountJson("10.00", """
                                [
                                  {
                                    "companyName": "Mehta Textiles Pvt Ltd",
                                    "companyType": "PRIVATE_LIMITED",
                                    "address": "12 MG Road, Bengaluru, Karnataka 560001",
                                    "discountPercent": 5.5
                                  },
                                  {
                                    "companyName": "Rohan Mehta HUF",
                                    "companyType": "HUF",
                                    "address": "4 Park Street, Kolkata, West Bengal 700016"
                                  }
                                ]""")))
                .andExpect(status().isCreated());

        ArgumentCaptor<ClientRequest> captor = ArgumentCaptor.forClass(ClientRequest.class);
        verify(clientService).createClient(captor.capture());
        ClientRequest received = captor.getValue();
        assertThat(received.discountPercent()).isEqualByComparingTo("10");
        assertThat(received.companies().get(0).discountPercent()).isEqualByComparingTo("5.5");
        assertThat(received.companies().get(1).discountPercent()).isNull();
    }

    @Test
    @DisplayName("returns 400 when a discount is above 100, negative or has more than two decimals")
    void rejectsInvalidDiscounts() throws Exception {
        mockMvc.perform(post(CLIENTS_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(discountJson("100.01", """
                                [
                                  {
                                    "companyName": "Mehta Textiles Pvt Ltd",
                                    "companyType": "PRIVATE_LIMITED",
                                    "address": "12 MG Road, Bengaluru, Karnataka 560001",
                                    "discountPercent": -1
                                  },
                                  {
                                    "companyName": "Rohan Mehta HUF",
                                    "companyType": "HUF",
                                    "address": "4 Park Street, Kolkata, West Bengal 700016",
                                    "discountPercent": 2.555
                                  }
                                ]""")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", hasItem("discountPercent")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("companies[0].discountPercent")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("companies[1].discountPercent")));

        verify(clientService, never()).createClient(any());
    }

    @Test
    @DisplayName("returns 200 on update and passes the id and body through")
    void updatesClient() throws Exception {
        UUID id = UUID.randomUUID();
        when(clientService.updateClient(any(), any())).thenReturn(response());

        mockMvc.perform(put(CLIENTS_URL + "/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(UPDATE_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Rohan"));

        ArgumentCaptor<UUID> idCaptor = ArgumentCaptor.forClass(UUID.class);
        ArgumentCaptor<ClientUpdateRequest> bodyCaptor = ArgumentCaptor.forClass(ClientUpdateRequest.class);
        verify(clientService).updateClient(idCaptor.capture(), bodyCaptor.capture());
        assertThat(idCaptor.getValue()).isEqualTo(id);
        ClientUpdateRequest received = bodyCaptor.getValue();
        assertThat(received.firstName()).isEqualTo("Rohit");
        assertThat(received.email()).isEqualTo("rohit.mehta@example.com");
        assertThat(received.managerId()).isEqualTo(UUID.fromString(MANAGER_ID));
        assertThat(received.discountPercent()).isEqualByComparingTo("15");
    }

    @Test
    @DisplayName("records the WhatsApp opt-in alone through PATCH")
    void updatesWhatsAppOptIn() throws Exception {
        UUID id = UUID.randomUUID();
        when(clientService.updateWhatsAppOptIn(any(), any())).thenReturn(ClientResponse.builder()
                .id(id)
                .firstName("Rohan")
                .whatsappOptIn(true)
                .build());

        mockMvc.perform(patch(CLIENTS_URL + "/" + id + "/whatsapp-opt-in")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"whatsappOptIn\": true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.whatsappOptIn").value(true));

        ArgumentCaptor<WhatsAppOptInRequest> bodyCaptor = ArgumentCaptor.forClass(WhatsAppOptInRequest.class);
        verify(clientService).updateWhatsAppOptIn(eq(id), bodyCaptor.capture());
        assertThat(bodyCaptor.getValue().whatsappOptIn()).isTrue();
    }

    @Test
    @DisplayName("rejects a WhatsApp opt-in without a value, and returns 404 for an unknown client")
    void rejectsInvalidWhatsAppOptIn() throws Exception {
        UUID id = UUID.randomUUID();
        when(clientService.updateWhatsAppOptIn(eq(id), any())).thenThrow(new ClientNotFoundException(id));

        mockMvc.perform(patch(CLIENTS_URL + "/" + id + "/whatsapp-opt-in")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", hasItem("whatsappOptIn")));
        mockMvc.perform(patch(CLIENTS_URL + "/" + id + "/whatsapp-opt-in")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"whatsappOptIn\": false}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("applies the same validation to the client fields on update as on create")
    void updateRejectsInvalidBody() throws Exception {
        mockMvc.perform(put(CLIENTS_URL + "/" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", hasItem("firstName")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("managerId")))
                .andExpect(jsonPath("$.errors[*].field", not(hasItem("companies"))));

        verify(clientService, never()).updateClient(any(), any());
    }

    @Test
    @DisplayName("returns 404 when updating an unknown client")
    void updateReturnsNotFound() throws Exception {
        UUID id = UUID.randomUUID();
        when(clientService.updateClient(any(), any())).thenThrow(new ClientNotFoundException(id));

        mockMvc.perform(put(CLIENTS_URL + "/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(UPDATE_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("returns 204 with no body on delete client")
    void deletesClient() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(delete(CLIENTS_URL + "/" + id))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(clientService).deleteClient(id);
    }

    @Test
    @DisplayName("returns 404 when deleting an unknown client")
    void deleteClientReturnsNotFound() throws Exception {
        UUID id = UUID.randomUUID();
        doThrow(new ClientNotFoundException(id)).when(clientService).deleteClient(id);

        mockMvc.perform(delete(CLIENTS_URL + "/" + id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("returns 409 when deleting a client whose companies have services")
    void deleteClientReturnsConflictWhenServicesAssigned() throws Exception {
        UUID id = UUID.randomUUID();
        doThrow(new ClientHasServicesException(id)).when(clientService).deleteClient(id);

        mockMvc.perform(delete(CLIENTS_URL + "/" + id))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESOURCE_IN_USE"));
    }

    private String discountJson(String clientDiscount, String companies) {
        return """
                {
                  "firstName": "Rohan",
                  "lastName": "Mehta",
                  "phone": "+91 98200 12345",
                  "email": "rohan.mehta@example.com",
                  "managerId": "%s",
                  "discountPercent": %s,
                  "companies": %s
                }""".formatted(MANAGER_ID, clientDiscount, companies);
    }

    private String clientJson(String phone, String email, String companies) {
        return """
                {
                  "firstName": "Rohan",
                  "lastName": "Mehta",
                  "phone": "%s",
                  "email": "%s",
                  "managerId": "%s",
                  "companies": %s
                }""".formatted(phone, email, MANAGER_ID, companies);
    }

    private String oneCompany() {
        return """
                [
                  {
                    "companyName": "Mehta Textiles Pvt Ltd",
                    "companyType": "PRIVATE_LIMITED",
                    "address": "12 MG Road, Bengaluru, Karnataka 560001"
                  }
                ]""";
    }

    private ClientResponse response() {
        Instant now = Instant.now();
        return ClientResponse.builder()
                .id(UUID.randomUUID())
                .firstName("Rohan")
                .lastName("Mehta")
                .phone("+91 98200 12345")
                .email("rohan.mehta@example.com")
                .managerId(UUID.fromString(MANAGER_ID))
                .managerName("Pranay Singhal")
                .companies(List.of(CompanyResponse.builder()
                        .id(UUID.randomUUID())
                        .companyName("Mehta Textiles Pvt Ltd")
                        .companyType(CompanyType.PRIVATE_LIMITED)
                        .address("12 MG Road, Bengaluru, Karnataka 560001")
                        .discountPercent(new BigDecimal("5.00"))
                        .build()))
                .totalPending(new BigDecimal("19630.00"))
                .createdAt(now)
                .updatedAt(now)
                .build();
    }
}
