package com.psc.cl.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings for the emails sent to clients, read from {@code client-ledger.notification}. The SMTP
 * server itself is configured under {@code spring.mail}; every value comes from the environment.
 *
 * @param from     the address emails are sent from; it must be one the SMTP account may send as.
 *                 Empty when email has not been configured, in which case sending is refused
 * @param fromName the name shown beside the from address; when empty, the firm's name from Global
 *                 Settings is shown instead
 */
@ConfigurationProperties(prefix = "client-ledger.notification")
public record NotificationProperties(String from, String fromName) {
}
