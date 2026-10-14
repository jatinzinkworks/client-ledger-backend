package com.psc.cl.client.model;

import java.util.UUID;

/**
 * Published when a client comes to agree to receive WhatsApp messages: created opted in, or changed
 * from opted out to opted in. Not published when an opted-in client is saved again.
 *
 * <p>Other modules react to it without the client module depending on them; the notification module,
 * for one, confirms the opt-in to the client on WhatsApp once the change has been committed.
 *
 * @param clientId the client who opted in
 */
public record ClientOptedInToWhatsApp(UUID clientId) {
}
