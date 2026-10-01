package com.psc.cl.globalsettings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.psc.cl.AbstractPostgresIT;
import com.psc.cl.globalsettings.controller.GlobalSettingsApi;
import com.psc.cl.globalsettings.repository.FirmDetailsRepository;
import com.psc.cl.globalsettings.repository.PaymentTermsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

class GlobalSettingsIT extends AbstractPostgresIT {

    private static final String PAYMENT_TERMS_URL =
            GlobalSettingsApi.BASE_PATH + GlobalSettingsApi.PAYMENT_TERMS_PATH;

    private static final String FIRM_DETAILS_URL =
            GlobalSettingsApi.BASE_PATH + GlobalSettingsApi.FIRM_DETAILS_PATH;

    @Autowired
    private PaymentTermsRepository paymentTermsRepository;

    @Autowired
    private FirmDetailsRepository firmDetailsRepository;

    @BeforeEach
    void clearSettings() {
        paymentTermsRepository.deleteAll();
        firmDetailsRepository.deleteAll();
    }

    @Test
    @DisplayName("payment terms: 404 until created, then 201, then 200 on the same record")
    void paymentTermsLifecycle() throws Exception {
        mockMvc.perform(get(PAYMENT_TERMS_URL))
                .andExpect(status().isNotFound());

        String created = mockMvc.perform(post(PAYMENT_TERMS_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "paymentDueAfterDays": 30,
                                  "markOverdueAfterDays": 90,
                                  "paymentReminderEnabled": true,
                                  "paymentReminderDays": 7
                                }"""))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(created, "$.id");

        mockMvc.perform(get(PAYMENT_TERMS_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.paymentDueAfterDays").value(30))
                .andExpect(jsonPath("$.paymentReminderDays").value(7));

        // A second save must update the singleton in place rather than insert a second row.
        mockMvc.perform(post(PAYMENT_TERMS_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.paymentDueAfterDays").value(15))
                .andExpect(jsonPath("$.markOverdueAfterDays").value(60))
                .andExpect(jsonPath("$.paymentReminderEnabled").value(false))
                .andExpect(jsonPath("$.paymentReminderDays").doesNotExist());

        assertThat(paymentTermsRepository.count()).isOne();
    }

    @Test
    @DisplayName("payment terms: the singleton index keeps exactly one row")
    void paymentTermsStaySingleton() throws Exception {
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post(PAYMENT_TERMS_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().is2xxSuccessful());
        }

        assertThat(paymentTermsRepository.count()).isOne();
    }

    @Test
    @DisplayName("firm details: 404 until created, then 201, then 200 on the same record")
    void firmDetailsLifecycle() throws Exception {
        mockMvc.perform(get(FIRM_DETAILS_URL))
                .andExpect(status().isNotFound());

        String created = mockMvc.perform(post(FIRM_DETAILS_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firmName": "  Pranay Singhal & Company  ",
                                  "gstin": "27aakfp4471m1zs",
                                  "firmRegistrationNo": "FRN 0148290W",
                                  "email": "accounts@pranaysinghal.in",
                                  "phone": "+91 22 4012 8890",
                                  "address": "302, Hubtown Solaris, Andheri East, Mumbai 400069"
                                }"""))
                .andExpect(status().isCreated())
                // Trimming and GSTIN upper casing survive the round trip to the database.
                .andExpect(jsonPath("$.firmName").value("Pranay Singhal & Company"))
                .andExpect(jsonPath("$.gstin").value("27AAKFP4471M1ZS"))
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(created, "$.id");

        mockMvc.perform(get(FIRM_DETAILS_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.address").value(
                        "302, Hubtown Solaris, Andheri East, Mumbai 400069"));

        // Optional fields omitted on a later save are cleared, because a save replaces wholesale.
        mockMvc.perform(post(FIRM_DETAILS_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firmName": "Pranay Singhal LLP",
                                  "gstin": "27AAKFP4471M1ZS",
                                  "address": "1 Main Street, Mumbai 400001"
                                }"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.firmName").value("Pranay Singhal LLP"))
                .andExpect(jsonPath("$.phone").doesNotExist())
                .andExpect(jsonPath("$.firmRegistrationNo").doesNotExist());

        assertThat(firmDetailsRepository.count()).isOne();
    }

    @Test
    @DisplayName("firm details: a malformed GSTIN never reaches the database")
    void firmDetailsRejectMalformedGstin() throws Exception {
        mockMvc.perform(post(FIRM_DETAILS_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firmName": "Acme Associates",
                                  "gstin": "NOT-A-GSTIN",
                                  "address": "1 Main Street, Mumbai 400001"
                                }"""))
                .andExpect(status().isBadRequest());

        assertThat(firmDetailsRepository.count()).isZero();
    }
}
