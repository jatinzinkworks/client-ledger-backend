package com.psc.cl.notification.service;

import com.psc.cl.client.model.ClientOptedInToWhatsApp;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Confirms a client's WhatsApp opt-in to them once it has been saved.
 *
 * <p>Runs only after the opt-in commits, so no client is told they opted in when the change was then
 * rolled back. It runs in the background, so the request that recorded the opt-in returns without
 * waiting on WhatsApp. Being after the commit, nothing here can undo the opt-in either: a confirmation
 * that cannot be sent, such as for a phone number WhatsApp cannot use, is logged and the opt-in stands.
 */
@Component
@RequiredArgsConstructor
public class WhatsAppOptInConfirmationListener {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppOptInConfirmationListener.class);

    private final WhatsAppNotificationService whatsAppNotificationService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void confirm(ClientOptedInToWhatsApp event) {
        try {
            whatsAppNotificationService.sendOptInConfirmation(event.clientId());
        } catch (RuntimeException e) {
            // Nobody is waiting on this thread, so the log is where a failed confirmation is reported.
            log.warn("Could not confirm the WhatsApp opt-in of client {}; the opt-in stands", event.clientId(), e);
        }
    }
}
