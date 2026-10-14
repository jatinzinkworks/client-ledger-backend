package com.psc.cl.notification.service;

import com.psc.cl.notification.dto.ClientWhatsAppResult;
import com.psc.cl.notification.dto.WhatsAppReminderResponse;

import java.util.Optional;
import java.util.UUID;

/**
 * Sends clients WhatsApp messages about their accounts.
 */
public interface WhatsAppNotificationService {

    /**
     * Sends one client a WhatsApp payment reminder for what they owe across every financial year and
     * all of their companies, on the phone number in their record: the approved payment reminder
     * template, filled with their first name, the firm's name, the amount pending and today's date.
     *
     * @param clientId the client
     * @return the client reminded, the amount stated and WhatsApp's message identifier
     * @throws com.psc.cl.client.exception.ClientNotFoundException when no client has this identifier
     * @throws com.psc.cl.notification.exception.WhatsAppNotOptedInException when the client has not
     *         agreed to WhatsApp messages
     * @throws com.psc.cl.notification.exception.NothingPendingException when the client owes nothing
     * @throws com.psc.cl.notification.exception.InvalidWhatsAppNumberException when the client's phone
     *         number cannot be used for WhatsApp
     * @throws com.psc.cl.notification.exception.WhatsAppDeliveryException when WhatsApp is not
     *         configured, refuses the message or cannot be reached
     */
    ClientWhatsAppResult sendPaymentReminder(UUID clientId);

    /**
     * Confirms to a client on WhatsApp that they have opted in to messages from the firm: the approved
     * opt-in confirmation template, filled with their first name and the firm's name. Nothing is sent
     * when no confirmation template is set, or when the client is no longer opted in.
     *
     * @param clientId the client who opted in
     * @return the identifier WhatsApp gave the message; empty when nothing was sent
     * @throws com.psc.cl.client.exception.ClientNotFoundException when no client has this identifier
     * @throws com.psc.cl.notification.exception.InvalidWhatsAppNumberException when the client's phone
     *         number cannot be used for WhatsApp
     * @throws com.psc.cl.notification.exception.WhatsAppDeliveryException when WhatsApp is not
     *         configured, refuses the message or cannot be reached
     */
    Optional<String> sendOptInConfirmation(UUID clientId);

    /**
     * Sends a WhatsApp payment reminder, as {@link #sendPaymentReminder} does, to every client who has
     * opted in to WhatsApp messages and has an amount pending. Clients not opted in, and those settled
     * or in credit overall, are skipped. Each client is sent to on its own, so a failure for one, such
     * as a phone number WhatsApp cannot use, is reported and the others are still reminded.
     *
     * @return which clients were reminded, how many were skipped, and which could not be reminded
     * @throws com.psc.cl.notification.exception.WhatsAppDeliveryException when WhatsApp is not
     *         configured, before any client is tried
     */
    WhatsAppReminderResponse sendPaymentReminders();
}
