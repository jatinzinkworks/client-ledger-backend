package com.psc.cl.client.controller;

import static org.assertj.core.api.Assertions.assertThat;
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

import com.psc.cl.client.dto.ClientRequest;
import com.psc.cl.client.dto.CompanyRequest;
import com.psc.cl.client.dto.CompanyResponse;
import com.psc.cl.client.exception.ClientNotFoundException;
import com.psc.cl.client.exception.CompanyHasServicesException;
import com.psc.cl.client.exception.CompanyLimitExceededException;
import com.psc.cl.client.exception.CompanyNotFoundException;
import com.psc.cl.client.exception.LastCompanyRemovalException;
import com.psc.cl.client.model.CompanyType;
import com.psc.cl.client.service.ClientCompanyService;
import com.psc.cl.ledger.service.FinancialYears;
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
import java.util.List;
import java.util.UUID;

@WebMvcTest(ClientCompanyController.class)
class ClientCompanyControllerTest {

    private static final String CLIENTS_URL = ClientApi.BASE_PATH;

    /** A complete, valid company payload, for adding or updating a company. */
    private static final String COMPANY_JSON = """
            {
              "companyName": "Mehta Exports LLP",
              "companyType": "LLP",
              "address": "8 Nariman Point, Mumbai, Maharashtra 400021",
              "discountPercent": 2
            }""";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ClientCompanyService clientCompanyService;

    @Test
    @DisplayName("returns 200 with the client's companies on list companies")
    void listsCompanies() throws Exception {
        UUID id = UUID.randomUUID();
        CompanyResponse listed = CompanyResponse.builder()
                .id(UUID.randomUUID())
                .companyName("Mehta Textiles Pvt Ltd")
                .companyType(CompanyType.PRIVATE_LIMITED)
                .address("12 MG Road, Bengaluru, Karnataka 560001")
                .discountPercent(new BigDecimal("5.00"))
                .serviceCount(3L)
                .build();
        when(clientCompanyService.listCompanies(id, null)).thenReturn(List.of(listed));

        mockMvc.perform(get(CLIENTS_URL + "/" + id + "/companies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].companyName").value("Mehta Textiles Pvt Ltd"))
                .andExpect(jsonPath("$[0].discountPercent").value(5.0))
                .andExpect(jsonPath("$[0].serviceCount").value(3));
    }

    @Test
    @DisplayName("passes the financial year through and returns each company's billed, received and pending")
    void listsCompaniesForFinancialYear() throws Exception {
        UUID id = UUID.randomUUID();
        CompanyResponse listed = CompanyResponse.builder()
                .id(UUID.randomUUID())
                .companyName("Mehta Textiles Pvt Ltd")
                .serviceCount(1L)
                .financialYear("2026-27")
                .totalBilled(new BigDecimal("39235.00"))
                .totalPaymentReceived(new BigDecimal("15605.00"))
                .pending(new BigDecimal("23630.00"))
                .build();
        when(clientCompanyService.listCompanies(id, FinancialYears.parse("2026-27"))).thenReturn(List.of(listed));

        mockMvc.perform(get(CLIENTS_URL + "/" + id + "/companies").param("financialYear", "2026-27"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].financialYear").value("2026-27"))
                .andExpect(jsonPath("$[0].totalBilled").value(39235.0))
                .andExpect(jsonPath("$[0].totalPaymentReceived").value(15605.0))
                .andExpect(jsonPath("$[0].pending").value(23630.0));
    }

    @Test
    @DisplayName("returns 400 when the financial year is not written as yyyy-yy")
    void listCompaniesRejectsMalformedFinancialYear() throws Exception {
        mockMvc.perform(get(CLIENTS_URL + "/" + UUID.randomUUID() + "/companies").param("financialYear", "2026"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        verify(clientCompanyService, never()).listCompanies(any(), any());
    }

    @Test
    @DisplayName("returns 404 when listing companies of an unknown client")
    void listCompaniesReturnsNotFound() throws Exception {
        UUID id = UUID.randomUUID();
        when(clientCompanyService.listCompanies(id, null)).thenThrow(new ClientNotFoundException(id));

        mockMvc.perform(get(CLIENTS_URL + "/" + id + "/companies"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("returns 201 with a Location header on add company and passes the id and body through")
    void addsCompany() throws Exception {
        UUID clientId = UUID.randomUUID();
        CompanyResponse added = company();
        when(clientCompanyService.addCompany(any(), any())).thenReturn(added);

        mockMvc.perform(post(CLIENTS_URL + "/" + clientId + "/companies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(COMPANY_JSON))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION,
                        CLIENTS_URL + "/" + clientId + "/companies/" + added.getId()))
                .andExpect(jsonPath("$.id").value(added.getId().toString()))
                .andExpect(jsonPath("$.companyName").value("Mehta Textiles Pvt Ltd"));

        ArgumentCaptor<CompanyRequest> bodyCaptor = ArgumentCaptor.forClass(CompanyRequest.class);
        verify(clientCompanyService).addCompany(eq(clientId), bodyCaptor.capture());
        CompanyRequest received = bodyCaptor.getValue();
        assertThat(received.companyName()).isEqualTo("Mehta Exports LLP");
        assertThat(received.companyType()).isEqualTo(CompanyType.LLP);
        assertThat(received.address()).isEqualTo("8 Nariman Point, Mumbai, Maharashtra 400021");
        assertThat(received.discountPercent()).isEqualByComparingTo("2");
    }

    @Test
    @DisplayName("returns 400 listing every missing mandatory company field on add company")
    void addCompanyRejectsInvalidBody() throws Exception {
        mockMvc.perform(post(CLIENTS_URL + "/" + UUID.randomUUID() + "/companies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[*].field", hasItem("companyName")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("companyType")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("address")));

        verify(clientCompanyService, never()).addCompany(any(), any());
    }

    @Test
    @DisplayName("returns 400 on add company when the client id is not a UUID")
    void addCompanyRejectsMalformedId() throws Exception {
        mockMvc.perform(post(CLIENTS_URL + "/not-a-uuid/companies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(COMPANY_JSON))
                .andExpect(status().isBadRequest());

        verify(clientCompanyService, never()).addCompany(any(), any());
    }

    @Test
    @DisplayName("returns 404 when adding a company to an unknown client")
    void addCompanyReturnsNotFound() throws Exception {
        UUID clientId = UUID.randomUUID();
        when(clientCompanyService.addCompany(any(), any())).thenThrow(new ClientNotFoundException(clientId));

        mockMvc.perform(post(CLIENTS_URL + "/" + clientId + "/companies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(COMPANY_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("returns 409 BUSINESS_RULE_VIOLATION when the client already holds the most companies")
    void addCompanyReturnsConflictOverLimit() throws Exception {
        UUID clientId = UUID.randomUUID();
        when(clientCompanyService.addCompany(any(), any()))
                .thenThrow(new CompanyLimitExceededException(clientId, ClientRequest.MAX_COMPANIES));

        mockMvc.perform(post(CLIENTS_URL + "/" + clientId + "/companies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(COMPANY_JSON))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"));
    }

    @Test
    @DisplayName("returns 200 with one company of the client and passes both ids through")
    void fetchesCompany() throws Exception {
        UUID clientId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        when(clientCompanyService.getCompany(clientId, companyId)).thenReturn(company());

        mockMvc.perform(get(CLIENTS_URL + "/" + clientId + "/companies/" + companyId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.companyType").value("PRIVATE_LIMITED"))
                .andExpect(jsonPath("$.address").value("12 MG Road, Bengaluru, Karnataka 560001"));
    }

    @Test
    @DisplayName("returns 404 when the client does not hold the company")
    void fetchCompanyReturnsNotFound() throws Exception {
        UUID clientId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        when(clientCompanyService.getCompany(clientId, companyId))
                .thenThrow(new CompanyNotFoundException(clientId, companyId));

        mockMvc.perform(get(CLIENTS_URL + "/" + clientId + "/companies/" + companyId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("returns 400 when the company identifier is not a UUID")
    void fetchCompanyRejectsMalformedId() throws Exception {
        mockMvc.perform(get(CLIENTS_URL + "/" + UUID.randomUUID() + "/companies/not-a-uuid"))
                .andExpect(status().isBadRequest());

        verify(clientCompanyService, never()).getCompany(any(), any());
    }

    @Test
    @DisplayName("returns 200 on update company and passes both ids and the body through")
    void updatesCompany() throws Exception {
        UUID clientId = UUID.randomUUID();
        CompanyResponse updated = company();
        when(clientCompanyService.updateCompany(any(), any(), any())).thenReturn(updated);

        mockMvc.perform(put(CLIENTS_URL + "/" + clientId + "/companies/" + updated.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(COMPANY_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(updated.getId().toString()))
                .andExpect(jsonPath("$.companyName").value("Mehta Textiles Pvt Ltd"));

        ArgumentCaptor<CompanyRequest> bodyCaptor = ArgumentCaptor.forClass(CompanyRequest.class);
        verify(clientCompanyService).updateCompany(eq(clientId), eq(updated.getId()), bodyCaptor.capture());
        CompanyRequest received = bodyCaptor.getValue();
        assertThat(received.companyName()).isEqualTo("Mehta Exports LLP");
        assertThat(received.companyType()).isEqualTo(CompanyType.LLP);
        assertThat(received.discountPercent()).isEqualByComparingTo("2");
    }

    @Test
    @DisplayName("returns 400 listing every missing mandatory company field on update company")
    void updateCompanyRejectsInvalidBody() throws Exception {
        mockMvc.perform(put(CLIENTS_URL + "/" + UUID.randomUUID() + "/companies/" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[*].field", hasItem("companyName")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("companyType")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("address")));

        verify(clientCompanyService, never()).updateCompany(any(), any(), any());
    }

    @Test
    @DisplayName("returns 400 on update company when the company id is not a UUID")
    void updateCompanyRejectsMalformedId() throws Exception {
        mockMvc.perform(put(CLIENTS_URL + "/" + UUID.randomUUID() + "/companies/not-a-uuid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(COMPANY_JSON))
                .andExpect(status().isBadRequest());

        verify(clientCompanyService, never()).updateCompany(any(), any(), any());
    }

    @Test
    @DisplayName("returns 404 when the client does not hold the company to update")
    void updateCompanyReturnsNotFound() throws Exception {
        UUID clientId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        when(clientCompanyService.updateCompany(any(), any(), any()))
                .thenThrow(new CompanyNotFoundException(clientId, companyId));

        mockMvc.perform(put(CLIENTS_URL + "/" + clientId + "/companies/" + companyId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(COMPANY_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("returns 204 with no body on delete company and passes both ids through")
    void deletesCompany() throws Exception {
        UUID clientId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();

        mockMvc.perform(delete(CLIENTS_URL + "/" + clientId + "/companies/" + companyId))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(clientCompanyService).deleteCompany(clientId, companyId);
    }

    @Test
    @DisplayName("returns 409 BUSINESS_RULE_VIOLATION when deleting the client's only company")
    void deleteCompanyReturnsConflictForLastCompany() throws Exception {
        UUID clientId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        doThrow(new LastCompanyRemovalException(clientId, companyId))
                .when(clientCompanyService).deleteCompany(clientId, companyId);

        mockMvc.perform(delete(CLIENTS_URL + "/" + clientId + "/companies/" + companyId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"));
    }

    @Test
    @DisplayName("returns 409 RESOURCE_IN_USE when deleting a company that has services")
    void deleteCompanyReturnsConflictWhenServicesAssigned() throws Exception {
        UUID clientId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        doThrow(new CompanyHasServicesException(clientId, companyId))
                .when(clientCompanyService).deleteCompany(clientId, companyId);

        mockMvc.perform(delete(CLIENTS_URL + "/" + clientId + "/companies/" + companyId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESOURCE_IN_USE"));
    }

    @Test
    @DisplayName("returns 404 when the client does not hold the company to delete")
    void deleteCompanyReturnsNotFound() throws Exception {
        UUID clientId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        doThrow(new CompanyNotFoundException(clientId, companyId))
                .when(clientCompanyService).deleteCompany(clientId, companyId);

        mockMvc.perform(delete(CLIENTS_URL + "/" + clientId + "/companies/" + companyId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    private CompanyResponse company() {
        return CompanyResponse.builder()
                .id(UUID.randomUUID())
                .companyName("Mehta Textiles Pvt Ltd")
                .companyType(CompanyType.PRIVATE_LIMITED)
                .address("12 MG Road, Bengaluru, Karnataka 560001")
                .discountPercent(new BigDecimal("5.00"))
                .build();
    }
}
