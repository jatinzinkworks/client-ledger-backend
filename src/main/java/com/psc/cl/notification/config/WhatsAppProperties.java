package com.psc.cl.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Settings for WhatsApp messages sent through Meta's WhatsApp Cloud API, read from
 * {@code client-ledger.notification.whatsapp}. Every value comes from {@code application.yml}, which
 * reads secrets from the environment.
 *
 * @param apiBaseUrl              the Graph API's address
 * @param apiVersion              the Graph API version, such as {@code v23.0}
 * @param phoneNumberId           the Phone Number ID messages are sent from; empty when not configured
 * @param accessToken             the System User access token; empty when not configured. Never logged
 * @param paymentReminderTemplate the approved template payment reminders are sent with
 * @param templateLanguage        the language code the template was approved in, such as {@code en}
 * @param defaultCountryCode      the calling code added to numbers stored without one, such as {@code 91}
 * @param timeout                 how long to wait to connect to, and hear back from, the API
 * @param optInConfirmationTemplate the approved template sent to confirm a client's opt-in; empty sends
 *                                  no confirmation
 */
@ConfigurationProperties(prefix = "client-ledger.notification.whatsapp")
public record WhatsAppProperties(
        @DefaultValue("https://graph.facebook.com") String apiBaseUrl,
        @DefaultValue("v23.0") String apiVersion,
        String phoneNumberId,
        String accessToken,
        @DefaultValue("payment_reminder") String paymentReminderTemplate,
        @DefaultValue("en") String templateLanguage,
        @DefaultValue("91") String defaultCountryCode,
        @DefaultValue("10s") Duration timeout,
        String optInConfirmationTemplate) {

    /** Whether the account to send from has been set; nothing can be sent until it has. */
    public boolean isConfigured() {
        return phoneNumberId != null && !phoneNumberId.isBlank() && accessToken != null && !accessToken.isBlank();
    }

    /** Whether opt-ins are confirmed to clients: only once a template for it has been approved and set. */
    public boolean confirmsOptIn() {
        return optInConfirmationTemplate != null && !optInConfirmationTemplate.isBlank();
    }

    /** Leaves the access token out, so the settings can be logged safely. */
    @Override
    public String toString() {
        return "WhatsAppProperties[apiBaseUrl=" + apiBaseUrl + ", apiVersion=" + apiVersion
                + ", phoneNumberId=" + phoneNumberId + ", accessToken=" + (accessToken == null ? "null" : "***")
                + ", paymentReminderTemplate=" + paymentReminderTemplate + ", templateLanguage=" + templateLanguage
                + ", defaultCountryCode=" + defaultCountryCode + ", timeout=" + timeout
                + ", optInConfirmationTemplate=" + optInConfirmationTemplate + "]";
    }
}
