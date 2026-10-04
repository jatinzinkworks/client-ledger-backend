package com.psc.cl.client.service;

import com.psc.cl.client.dto.ClientRequest;
import com.psc.cl.client.dto.ClientResponse;
import com.psc.cl.client.dto.ClientSummaryResponse;
import com.psc.cl.client.dto.ClientUpdateRequest;
import com.psc.cl.client.dto.CompanyResponse;

import java.util.List;
import java.util.UUID;

/**
 * Business operations over clients and their companies.
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
     * Replaces a client wholesale, together with their full set of companies.
     *
     * <p>A company carrying an id is updated in place, one without an id is added, and any
     * existing company left out of the request is removed. An omitted client discount resets to
     * zero, and every company that does not set its own discount is given the client's.
     *
     * @param id      the client to replace
     * @param request the new content
     * @return the updated client
     * @throws com.psc.cl.client.exception.ClientNotFoundException
     *         when no client carries this identifier
     * @throws com.psc.cl.client.exception.AssignedManagerNotFoundException
     *         when the assigned manager does not exist
     * @throws com.psc.cl.client.exception.CompanyNotFoundException
     *         when a company id in the request is not one of this client's companies
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
     * Removes one company of a client.
     *
     * @param clientId  the client the company belongs to
     * @param companyId the company to remove
     * @throws com.psc.cl.client.exception.ClientNotFoundException
     *         when no client carries this identifier
     * @throws com.psc.cl.client.exception.CompanyNotFoundException
     *         when the client holds no company with this identifier
     * @throws com.psc.cl.client.exception.LastCompanyRemovalException
     *         when it is the client's only company
     * @throws com.psc.cl.client.exception.CompanyHasServicesException
     *         when services are still assigned to the company
     */
    void deleteCompany(UUID clientId, UUID companyId);

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
     * Lists every client, ordered by last name then first name, naming their companies only.
     *
     * @return every client, empty when none has been added yet
     */
    List<ClientSummaryResponse> listClients();

    /**
     * Lists every company a client holds, in full, ordered by company name.
     *
     * @param clientId the client whose companies to list
     * @return the client's companies
     * @throws com.psc.cl.client.exception.ClientNotFoundException
     *         when no client carries this identifier
     */
    List<CompanyResponse> listCompanies(UUID clientId);

    /**
     * Reads one company of a client.
     *
     * @param clientId  the client the company belongs to
     * @param companyId the company identifier
     * @return the stored company
     * @throws com.psc.cl.client.exception.ClientNotFoundException
     *         when no client carries this identifier
     * @throws com.psc.cl.client.exception.CompanyNotFoundException
     *         when the client holds no company with this identifier
     */
    CompanyResponse getCompany(UUID clientId, UUID companyId);
}
