package com.psc.cl.globalsettings.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.psc.cl.globalsettings.dto.FirmDetailsRequest;
import com.psc.cl.globalsettings.dto.FirmDetailsResponse;
import com.psc.cl.globalsettings.dto.PaymentTermsRequest;
import com.psc.cl.globalsettings.dto.PaymentTermsResponse;
import com.psc.cl.globalsettings.exception.FirmDetailsNotFoundException;
import com.psc.cl.globalsettings.exception.PaymentTermsNotFoundException;
import com.psc.cl.globalsettings.service.FirmDetailsSaveResult;
import com.psc.cl.globalsettings.service.FirmDetailsService;
import com.psc.cl.globalsettings.service.PaymentTermsSaveResult;
import com.psc.cl.globalsettings.service.PaymentTermsService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

@WebMvcTest(GlobalSettingsController.class)
class GlobalSettingsControllerTest {

    private static final String PAYMENT_TERMS_URL = GlobalSettingsApi.BASE_PATH
            + GlobalSettingsApi.PAYMENT_TERMS_PATH;

    private static final String FIRM_DETAILS_URL = GlobalSettingsApi.BASE_PATH
            + GlobalSettingsApi.FIRM_DETAILS_PATH;

    /** A complete, valid firm details payload. Variants below alter one field at a time. */
    private static final String VALID_FIRM_JSON = """
            {
              "firmName": "Pranay Singhal & Company",
              "gstin": "27AAKFP4471M1ZS",
              "firmRegistrationNo": "FRN 0148290W",
              "email": "accounts@pranaysinghal.in",
              "phone": "+91 22 4012 8890",
              "address": "302, Hubtown Solaris, Andheri East, Mumbai 400069"
            }""";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private PaymentTermsService paymentTermsService;

    @MockitoBean
    private FirmDetailsService firmDetailsService;

    @Test
    @DisplayName("returns 201 with a Location header when payment terms are first created")
    void createsPaymentTerms() throws Exception {
        when(paymentTermsService.savePaymentTerms(any()))
                .thenReturn(new PaymentTermsSaveResult(response(), true));

        mockMvc.perform(post(PAYMENT_TERMS_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(PaymentTermsRequest.builder()
                                .paymentDueAfterDays(15)
                                .markOverdueAfterDays(60)
                                .paymentReminderEnabled(true)
                                .paymentReminderDays(7)
                                .build())))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, PAYMENT_TERMS_URL))
                .andExpect(jsonPath("$.paymentDueAfterDays").value(15))
                .andExpect(jsonPath("$.markOverdueAfterDays").value(60))
                .andExpect(jsonPath("$.paymentReminderEnabled").value(true))
                .andExpect(jsonPath("$.paymentReminderDays").value(7));
    }

    @Test
    @DisplayName("returns 200 when the same endpoint updates existing payment terms")
    void updatesPaymentTerms() throws Exception {
        when(paymentTermsService.savePaymentTerms(any()))
                .thenReturn(new PaymentTermsSaveResult(response(), false));

        mockMvc.perform(post(PAYMENT_TERMS_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentDueAfterDays\":15}"))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist(HttpHeaders.LOCATION))
                .andExpect(jsonPath("$.paymentDueAfterDays").value(15));
    }

    @Test
    @DisplayName("accepts an empty body and lets the service apply defaults")
    void acceptsEmptyBody() throws Exception {
        when(paymentTermsService.savePaymentTerms(any()))
                .thenReturn(new PaymentTermsSaveResult(response(), true));

        mockMvc.perform(post(PAYMENT_TERMS_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("deserialises a fully populated JSON body onto the request record")
    void deserialisesFullJsonBody() throws Exception {
        when(paymentTermsService.savePaymentTerms(any()))
                .thenReturn(new PaymentTermsSaveResult(response(), true));

        mockMvc.perform(post(PAYMENT_TERMS_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "paymentDueAfterDays": 30,
                                  "markOverdueAfterDays": 90,
                                  "paymentReminderEnabled": true,
                                  "paymentReminderDays": 5
                                }"""))
                .andExpect(status().isCreated());

        ArgumentCaptor<PaymentTermsRequest> captor = ArgumentCaptor.forClass(PaymentTermsRequest.class);
        verify(paymentTermsService).savePaymentTerms(captor.capture());
        PaymentTermsRequest received = captor.getValue();
        assertThat(received.paymentDueAfterDays()).isEqualTo(30);
        assertThat(received.markOverdueAfterDays()).isEqualTo(90);
        assertThat(received.paymentReminderEnabled()).isTrue();
        assertThat(received.paymentReminderDays()).isEqualTo(5);
    }

    @Test
    @DisplayName("returns 400 when a day count is out of range")
    void rejectsOutOfRangeDayCount() throws Exception {
        mockMvc.perform(post(PAYMENT_TERMS_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentDueAfterDays\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("paymentDueAfterDays"));

        verify(paymentTermsService, never()).savePaymentTerms(any());
    }

    @Test
    @DisplayName("returns 400 when reminders are enabled without a reminder lead time")
    void rejectsEnabledRemindersWithoutDays() throws Exception {
        mockMvc.perform(post(PAYMENT_TERMS_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentReminderEnabled\":true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        verify(paymentTermsService, never()).savePaymentTerms(any());
    }

    @Test
    @DisplayName("returns 200 with the stored payment terms")
    void fetchesPaymentTerms() throws Exception {
        when(paymentTermsService.getPaymentTerms()).thenReturn(response());

        mockMvc.perform(get(PAYMENT_TERMS_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentDueAfterDays").value(15))
                .andExpect(jsonPath("$.markOverdueAfterDays").value(60))
                .andExpect(jsonPath("$.paymentReminderEnabled").value(true))
                .andExpect(jsonPath("$.paymentReminderDays").value(7));
    }

    @Test
    @DisplayName("returns 404 when no payment terms have been configured")
    void returnsNotFoundWhenNothingStored() throws Exception {
        when(paymentTermsService.getPaymentTerms()).thenThrow(new PaymentTermsNotFoundException());

        mockMvc.perform(get(PAYMENT_TERMS_URL))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.path").value(PAYMENT_TERMS_URL));
    }

    @Test
    @DisplayName("returns 201 with a Location header when firm details are first created")
    void createsFirmDetails() throws Exception {
        when(firmDetailsService.saveFirmDetails(any()))
                .thenReturn(new FirmDetailsSaveResult(firmResponse(), true));

        mockMvc.perform(post(FIRM_DETAILS_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_FIRM_JSON))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, FIRM_DETAILS_URL))
                .andExpect(jsonPath("$.firmName").value("Pranay Singhal & Company"))
                .andExpect(jsonPath("$.gstin").value("27AAKFP4471M1ZS"));

        ArgumentCaptor<FirmDetailsRequest> captor = ArgumentCaptor.forClass(FirmDetailsRequest.class);
        verify(firmDetailsService).saveFirmDetails(captor.capture());
        FirmDetailsRequest received = captor.getValue();
        assertThat(received.firmName()).isEqualTo("Pranay Singhal & Company");
        assertThat(received.gstin()).isEqualTo("27AAKFP4471M1ZS");
        assertThat(received.firmRegistrationNo()).isEqualTo("FRN 0148290W");
        assertThat(received.email()).isEqualTo("accounts@pranaysinghal.in");
        assertThat(received.phone()).isEqualTo("+91 22 4012 8890");
        assertThat(received.address()).contains("Andheri East");
    }

    @Test
    @DisplayName("returns 200 when the same endpoint updates existing firm details")
    void updatesFirmDetails() throws Exception {
        when(firmDetailsService.saveFirmDetails(any()))
                .thenReturn(new FirmDetailsSaveResult(firmResponse(), false));

        mockMvc.perform(post(FIRM_DETAILS_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_FIRM_JSON))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist(HttpHeaders.LOCATION))
                .andExpect(jsonPath("$.firmName").value("Pranay Singhal & Company"));
    }

    @Test
    @DisplayName("accepts a payload with only the mandatory firm fields")
    void acceptsMandatoryFirmFieldsOnly() throws Exception {
        when(firmDetailsService.saveFirmDetails(any()))
                .thenReturn(new FirmDetailsSaveResult(firmResponse(), true));

        mockMvc.perform(post(FIRM_DETAILS_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firmName": "Acme Associates",
                                  "gstin": "27AAKFP4471M1ZS",
                                  "address": "1 Main Street, Mumbai 400001"
                                }"""))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("returns 400 listing every missing mandatory firm field")
    void rejectsMissingMandatoryFirmFields() throws Exception {
        mockMvc.perform(post(FIRM_DETAILS_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[*].field", hasItem("firmName")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("gstin")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("address")));

        verify(firmDetailsService, never()).saveFirmDetails(any());
    }

    @Test
    @DisplayName("returns 400 when the GSTIN is malformed")
    void rejectsMalformedGstin() throws Exception {
        mockMvc.perform(post(FIRM_DETAILS_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firmName": "Acme Associates",
                                  "gstin": "NOT-A-GSTIN",
                                  "address": "1 Main Street, Mumbai 400001"
                                }"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[*].field", hasItem("gstin")));

        verify(firmDetailsService, never()).saveFirmDetails(any());
    }

    @Test
    @DisplayName("returns 400 when the email is malformed")
    void rejectsMalformedEmail() throws Exception {
        mockMvc.perform(post(FIRM_DETAILS_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firmName": "Acme Associates",
                                  "gstin": "27AAKFP4471M1ZS",
                                  "address": "1 Main Street, Mumbai 400001",
                                  "email": "not-an-email"
                                }"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", hasItem("email")));

        verify(firmDetailsService, never()).saveFirmDetails(any());
    }

    @Test
    @DisplayName("returns 200 with the stored firm details")
    void fetchesFirmDetails() throws Exception {
        when(firmDetailsService.getFirmDetails()).thenReturn(firmResponse());

        mockMvc.perform(get(FIRM_DETAILS_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firmName").value("Pranay Singhal & Company"))
                .andExpect(jsonPath("$.email").value("accounts@pranaysinghal.in"));
    }

    @Test
    @DisplayName("returns 404 when no firm details have been configured")
    void returnsNotFoundWhenNoFirmDetails() throws Exception {
        when(firmDetailsService.getFirmDetails()).thenThrow(new FirmDetailsNotFoundException());

        mockMvc.perform(get(FIRM_DETAILS_URL))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.path").value(FIRM_DETAILS_URL));
    }

    private PaymentTermsResponse response() {
        Instant now = Instant.now();
        return PaymentTermsResponse.builder()
                .id(UUID.randomUUID())
                .paymentDueAfterDays(15)
                .markOverdueAfterDays(60)
                .paymentReminderEnabled(true)
                .paymentReminderDays(7)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    private FirmDetailsResponse firmResponse() {
        Instant now = Instant.now();
        return FirmDetailsResponse.builder()
                .id(UUID.randomUUID())
                .firmName("Pranay Singhal & Company")
                .gstin("27AAKFP4471M1ZS")
                .firmRegistrationNo("FRN 0148290W")
                .email("accounts@pranaysinghal.in")
                .phone("+91 22 4012 8890")
                .address("302, Hubtown Solaris, Andheri East, Mumbai 400069")
                .createdAt(now)
                .updatedAt(now)
                .build();
    }
}
