package com.psc.cl.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.psc.cl.notification.config.WhatsAppProperties;
import com.psc.cl.notification.exception.WhatsAppDeliveryException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;

class CloudApiWhatsAppSenderTest {

    private static final WhatsAppProperties PROPERTIES = new WhatsAppProperties("https://graph.facebook.com",
            "v23.0", "106540352242922", "test-token", "payment_reminder", "en", "91", Duration.ofSeconds(10), null);

    private static final String MESSAGES_URL = "https://graph.facebook.com/v23.0/106540352242922/messages";

    private MockRestServiceServer server;

    private CloudApiWhatsAppSender sender;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(PROPERTIES.apiBaseUrl());
        server = MockRestServiceServer.bindTo(builder).build();
        sender = new CloudApiWhatsAppSender(builder.build(), PROPERTIES);
    }

    @Test
    @DisplayName("posts the template with its parameters in order, and returns the message id")
    void sendsTemplate() {
        server.expect(requestTo(MESSAGES_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer test-token"))
                .andExpect(jsonPath("$.messaging_product").value("whatsapp"))
                .andExpect(jsonPath("$.to").value("919820012345"))
                .andExpect(jsonPath("$.type").value("template"))
                .andExpect(jsonPath("$.template.name").value("payment_reminder"))
                .andExpect(jsonPath("$.template.language.code").value("en"))
                .andExpect(jsonPath("$.template.components[0].type").value("body"))
                .andExpect(jsonPath("$.template.components[0].parameters[0].text").value("Rohan"))
                .andExpect(jsonPath("$.template.components[0].parameters[2].text").value("₹24,235.00"))
                .andRespond(withSuccess("""
                        {"messaging_product":"whatsapp",
                         "contacts":[{"input":"919820012345","wa_id":"919820012345"}],
                         "messages":[{"id":"wamid.ABC123"}]}
                        """, MediaType.APPLICATION_JSON));

        String messageId = sender.sendTemplate("919820012345", "payment_reminder", "en",
                List.of("Rohan", "Sharma & Associates", "₹24,235.00", "14 Oct 2026"));

        assertThat(messageId).isEqualTo("wamid.ABC123");
        server.verify();
    }

    @Test
    @DisplayName("reports Meta's explanation when the message is refused")
    void reportsRefusal() {
        server.expect(requestTo(MESSAGES_URL))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON).body("""
                        {"error":{"message":"(#132001) Template name does not exist in the translation",
                                  "type":"OAuthException","code":132001}}
                        """));

        assertThatThrownBy(() -> sender.sendTemplate("919820012345", "payment_reminder", "en", List.of("Rohan")))
                .isInstanceOf(WhatsAppDeliveryException.class)
                .hasMessageContaining("Template name does not exist")
                .hasMessageContaining("132001")
                .hasMessageNotContaining("test-token");
    }

    @Test
    @DisplayName("reports the status when the error is not in Meta's usual shape")
    void reportsUnreadableRefusal() {
        server.expect(requestTo(MESSAGES_URL))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE).body("upstream unavailable"));

        assertThatThrownBy(() -> sender.sendTemplate("919820012345", "payment_reminder", "en", List.of("Rohan")))
                .isInstanceOf(WhatsAppDeliveryException.class)
                .hasMessageContaining("HTTP 503");
    }

    @Test
    @DisplayName("treats a response without a message id as a failure")
    void rejectsResponseWithoutMessageId() {
        server.expect(requestTo(MESSAGES_URL))
                .andRespond(withSuccess("{\"messaging_product\":\"whatsapp\",\"messages\":[]}",
                        MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> sender.sendTemplate("919820012345", "payment_reminder", "en", List.of("Rohan")))
                .isInstanceOf(WhatsAppDeliveryException.class)
                .hasMessageContaining("no message id");
    }
}
