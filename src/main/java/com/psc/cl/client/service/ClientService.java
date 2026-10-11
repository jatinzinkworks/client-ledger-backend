package com.psc.cl.client.service;

import com.psc.cl.client.dto.ClientRequest;
import com.psc.cl.client.dto.ClientResponse;
import com.psc.cl.client.dto.ClientSummaryResponse;
import com.psc.cl.client.dto.ClientUpdateRequest;

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
     * discount is given the client's.
     *
     * @param request the client to create
     * @return the created client, including the generated identifiers
     * @throws com.psc.cl.client.exception.AssignedManagerNotFoundException
     *         when the assigned manager does not exist
     */
    ClientResponse createClient(ClientRequest request);

    /**
     * Replaces a client's own fields wholesale, leaving their companies as they are.
     *
     * <p>An omitted client discount resets to zero. Companies without a discount of their own
     * take the new client discount.
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
     * Removes a client together with their companies.
     *
     * @param id the client to remove
     * @throws com.psc.cl.client.exception.ClientNotFoundException
     *         when no client carries this identifier
     * @throws com.psc.cl.client.exception.ClientHasServicesException
     *         when services are still assigned to any of the client's companies
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
