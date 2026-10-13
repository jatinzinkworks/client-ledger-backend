package com.psc.cl.client.service;

import com.psc.cl.client.dto.CompanyRequest;
import com.psc.cl.client.dto.CompanyResponse;

import com.psc.cl.ledger.model.FinancialYear;

import java.util.List;
import java.util.UUID;

/**
 * Business operations over the companies of a client.
 */
public interface ClientCompanyService {

    /**
     * Lists every company a client holds, in full, ordered by company name.
     *
     * @param clientId the client whose companies to list
     * @return the client's companies
     * @throws com.psc.cl.client.exception.ClientNotFoundException
     *         when no client carries this identifier
     */
    List<CompanyResponse> listCompanies(UUID clientId, FinancialYear financialYear);

    /**
     * Adds one company to an existing client.
     *
     * <p>A company that does not set its own discount is given the client's.
     *
     * @param clientId the client to add the company to
     * @param request  the company to add
     * @return the created company, including the generated identifier
     * @throws com.psc.cl.client.exception.ClientNotFoundException
     *         when no client carries this identifier
     * @throws com.psc.cl.client.exception.CompanyLimitExceededException
     *         when the client already holds the most companies allowed
     */
    CompanyResponse addCompany(UUID clientId, CompanyRequest request);

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

    /**
     * Replaces one company of a client wholesale. The company keeps its identifier.
     *
     * <p>Nothing is left behind from the previous version: an omitted discount gives the company
     * the client's.
     *
     * @param clientId  the client the company belongs to
     * @param companyId the company to replace
     * @param request   the new content
     * @return the updated company
     * @throws com.psc.cl.client.exception.ClientNotFoundException
     *         when no client carries this identifier
     * @throws com.psc.cl.client.exception.CompanyNotFoundException
     *         when the client holds no company with this identifier
     */
    CompanyResponse updateCompany(UUID clientId, UUID companyId, CompanyRequest request);

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
}
