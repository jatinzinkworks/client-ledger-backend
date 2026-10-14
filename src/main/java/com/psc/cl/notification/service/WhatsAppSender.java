package com.psc.cl.notification.service;

import java.util.List;

/**
 * Sends WhatsApp messages. Behind an interface so the provider can change and tests need no network.
 */
public interface WhatsAppSender {

    /**
     * Sends an approved template message. WhatsApp only lets a business start a conversation with a
     * template approved in advance; its body's placeholders are filled in order.
     *
     * @param to             the recipient's number in international form, digits only, such as
     *                       {@code 919820012345}
     * @param templateName   the approved template
     * @param languageCode   the language the template was approved in, such as {@code en}
     * @param bodyParameters the values for the body's {@code {{1}}}, {@code {{2}}} and so on, in order
     * @return the identifier WhatsApp gave the message
     * @throws com.psc.cl.notification.exception.WhatsAppDeliveryException when WhatsApp refuses the
     *         message or cannot be reached
     */
    String sendTemplate(String to, String templateName, String languageCode, List<String> bodyParameters);
}
