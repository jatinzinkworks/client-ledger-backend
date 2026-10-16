package com.psc.cl.client.service;

import com.psc.cl.client.dto.ClientManagerBulkUpdateRequest;
import com.psc.cl.client.dto.ClientRequest;
import com.psc.cl.client.dto.ClientResponse;
import com.psc.cl.client.dto.ClientSummaryResponse;
import com.psc.cl.client.dto.ClientUpdateRequest;
import com.psc.cl.client.dto.WhatsAppOptInRequest;

import java.util.List;
import java.util.UUID;

/**
 * Business operations over clients.
 */
public interface ClientService {

    /**
     * Adds a new client together with their companies.
     *
     * <p>The client discount defaults to zero, and every company that does not set its own
     * discount is given the client's. Each company bringing an opening balance other than zero has
     * it recorded in its ledger, in the same transaction.
     *
     * @param request the client to create
     * @return the created client, including the generated identifiers
     * @throws com.psc.cl.client.exception.AssignedManagerNotFoundException
     *         when the assigned manager does not exist
     * @throws com.psc.cl.ledger.exception.FutureOpeningBalanceDateException
     *         when an opening balance is dated after today; nothing is created
     */
    ClientResponse createClient(ClientRequest request);

    /**
     * Replaces a client's own fields wholesale, leaving their companies as they are.
     *
     * <p>An omitted client discount resets to zero. Companies without a discount of their own
     * take the new client discount. The WhatsApp opt-in is left as it is; see
     * {@link #updateWhatsAppOptIn}.
     *
     * @param id      the client to replace
     * @param request the new content
     * @return the updated client
     * @throws com.psc.cl.client.exception.ClientNotFoundException
     *         when no client carries this identifier
     * @throws com.psc.cl.client.exception.AssignedManagerNotFoundException
     *         when the assigned manager does not exist
     */
    ClientResponse updateClient(UUID id, ClientUpdateRequest request);

    /**
     * Records whether a client agrees to receive WhatsApp messages, leaving everything else of theirs
     * as it is. WhatsApp messages, such as payment reminders, are only sent to clients who have.
     * A change from opted out to opted in publishes
     * {@link com.psc.cl.client.model.ClientOptedInToWhatsApp}.
     *
     * @param id      the client
     * @param request whether the client agrees
     * @return the updated client
     * @throws com.psc.cl.client.exception.ClientNotFoundException
     *         when no client carries this identifier
     */
    ClientResponse updateWhatsAppOptIn(UUID id, WhatsAppOptInRequest request);

    /**
     * Assigns several clients to one manager at once, all or nothing: when any client named does not
     * exist, none is reassigned. Only the assigned manager changes; everything else about each client
     * is left as it is. A client named more than once is assigned once.
     *
     * @param request the manager and the clients to assign to them
     * @return the updated clients, in the order the request first named them
     * @throws com.psc.cl.client.exception.AssignedManagerNotFoundException
     *         when the manager does not exist
     * @throws com.psc.cl.client.exception.ClientsNotFoundException
     *         when any identifier names no client, listing each such identifier
     */
    List<ClientSummaryResponse> updateManager(ClientManagerBulkUpdateRequest request);

    /**
     * Removes a client together with their companies.
     *
     * @param id the client to remove
     * @throws com.psc.cl.client.exception.ClientNotFoundException
     *         when no client carries this identifier
     * @throws com.psc.cl.client.exception.ClientHasServicesException
     *         when services are still assigned to any of the client's companies
     * @throws com.psc.cl.client.exception.ClientHasLedgerEntriesException
     *         when any of the client's companies has ledger entries, such as payments or an opening balance
     */
    void deleteClient(UUID id);

    /**
     * Reads one client by identifier, with their companies in full.
     *
     * @param id the client identifier
     * @return the stored client
     * @throws com.psc.cl.client.exception.ClientNotFoundException
     *         when no client carries this identifier
     */
    ClientResponse getClient(UUID id);

    /**
     * Lists clients, ordered by last name then first name, naming their companies only.
     *
     * @param managerId when given, only the clients assigned to this manager; when null, every
     *                  client
     * @return the matching clients, empty when there are none
     */
    List<ClientSummaryResponse> listClients(UUID managerId);
}
