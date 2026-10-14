package com.psc.cl.notification.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.psc.cl.notification.config.WhatsAppProperties;
import com.psc.cl.notification.exception.WhatsAppDeliveryException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;
import java.util.Map;

/**
 * {@link WhatsAppSender} for Meta's WhatsApp Cloud API: one POST to
 * {@code /{version}/{phone-number-id}/messages} per message, authorised with the access token as a
 * bearer token. Neither the token nor the recipient's number is logged.
 *
 * <p>Created by {@link com.psc.cl.notification.config.NotificationConfig}, which supplies the
 * {@link RestClient} with the API's address and timeouts.
 */
public class CloudApiWhatsAppSender implements WhatsAppSender {

    private static final Logger log = LoggerFactory.getLogger(CloudApiWhatsAppSender.class);

    private final RestClient restClient;

    private final WhatsAppProperties properties;

    public CloudApiWhatsAppSender(RestClient restClient, WhatsAppProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
    }

    @Override
    public String sendTemplate(String to, String templateName, String languageCode, List<String> bodyParameters) {
        Map<String, Object> request = Map.of(
                "messaging_product", "whatsapp",
                "recipient_type", "individual",
                "to", to,
                "type", "template",
                "template", Map.of(
                        "name", templateName,
                        "language", Map.of("code", languageCode),
                        "components", List.of(Map.of(
                                "type", "body",
                                "parameters", bodyParameters.stream()
                                        .map(value -> Map.of("type", "text", "text", value))
                                        .toList()))));
        try {
            SendResponse response = restClient.post()
                    .uri("/{version}/{phoneNumberId}/messages", properties.apiVersion(), properties.phoneNumberId())
                    .headers(headers -> headers.setBearerAuth(properties.accessToken()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(SendResponse.class);
            if (response == null || response.messages() == null || response.messages().isEmpty()) {
                throw new WhatsAppDeliveryException("WhatsApp accepted the message but returned no message id");
            }
            String messageId = response.messages().getFirst().id();
            log.debug("WhatsApp accepted template {} as message {}", templateName, messageId);
            return messageId;
        } catch (RestClientResponseException e) {
            throw new WhatsAppDeliveryException("WhatsApp refused the message: " + describe(e), e);
        } catch (ResourceAccessException e) {
            throw new WhatsAppDeliveryException("WhatsApp could not be reached", e);
        }
    }

    /** Meta's own explanation of an error, such as an expired token or an unapproved template. */
    private static String describe(RestClientResponseException e) {
        try {
            ErrorBody body = e.getResponseBodyAs(ErrorBody.class);
            if (body != null && body.error() != null && body.error().message() != null) {
                return body.error().message() + " (code " + body.error().code() + ")";
            }
        } catch (RestClientException | IllegalStateException parseFailure) {
            // Not Meta's usual error shape; the status below still says what happened.
            log.debug("WhatsApp error body could not be read", parseFailure);
        }
        return "HTTP " + e.getStatusCode().value();
    }

    /** The part of a send response that matters: the identifiers of the messages accepted. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record SendResponse(List<MessageId> messages) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record MessageId(String id) {
    }

    /** Meta's error response, such as {@code {"error": {"message": "...", "code": 190}}}. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record ErrorBody(ErrorDetail error) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ErrorDetail(String message, Integer code) {
    }
}
