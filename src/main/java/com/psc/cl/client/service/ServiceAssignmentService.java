package com.psc.cl.client.service;

import com.psc.cl.client.dto.ServiceAssignmentRequest;
import com.psc.cl.client.dto.ServiceAssignmentResponse;
import com.psc.cl.client.dto.ServiceAssignmentUpdateRequest;
import com.psc.cl.ledger.model.FinancialYear;

import java.util.List;
import java.util.UUID;

/**
 * Business operations over the catalog services delivered to client companies.
 *
 * <p>Every operation first checks the client exists and holds the company, raising
 * {@link com.psc.cl.client.exception.ClientNotFoundException} or
 * {@link com.psc.cl.client.exception.CompanyNotFoundException} otherwise.
 */
public interface ServiceAssignmentService {

    /**
     * Assigns a catalog service to one company of a client.
     *
     * <p>A company may take the same service more than once, for example on different delivery
     * dates. The service counts towards the catalog's subscriber count only the first time a
     * company takes it. A service without a discount of its own takes the company's.
     *
     * @param clientId  the client the company belongs to
     * @param companyId the company receiving the service
     * @param request   the service to assign
     * @return the stored assignment
     * @throws com.psc.cl.client.exception.AssignedServiceNotFoundException
     *         when the service does not exist in the Service Catalog
     */
    ServiceAssignmentResponse assignService(UUID clientId, UUID companyId, ServiceAssignmentRequest request);

    /**
     * Lists the services assigned to one company of a client, most recent delivery first, each with
     * what it was billed in a financial year when one is given.
     *
     * @param clientId      the client the company belongs to
     * @param companyId     the company
     * @param financialYear the year to add each service's billed amount for, or null to leave it out
     * @return the company's assigned services, empty when it has none
     */
    List<ServiceAssignmentResponse> listServices(UUID clientId, UUID companyId, FinancialYear financialYear);

    /**
     * Reads one service assigned to a company of a client.
     *
     * @param clientId     the client the company belongs to
     * @param companyId    the company
     * @param assignmentId the assignment identifier
     * @return the stored assignment
     * @throws com.psc.cl.client.exception.ServiceAssignmentNotFoundException
     *         when the company holds no assignment with this identifier
     */
    ServiceAssignmentResponse getService(UUID clientId, UUID companyId, UUID assignmentId);

    /**
     * Replaces the details of a service assigned to a company of a client. The catalog service
     * stays the same, so the catalog's subscriber count is unaffected. An omitted discount resets
     * to the company's, and an omitted comment is cleared.
     *
     * @param clientId     the client the company belongs to
     * @param companyId    the company
     * @param assignmentId the assignment to update
     * @param request      the new details
     * @return the updated assignment
     * @throws com.psc.cl.client.exception.ServiceAssignmentNotFoundException
     *         when the company holds no assignment with this identifier
     */
    ServiceAssignmentResponse updateService(UUID clientId, UUID companyId, UUID assignmentId,
                                            ServiceAssignmentUpdateRequest request);

    /**
     * Removes a service from a company of a client. When it was the company's last assignment of
     * that service, the company no longer counts towards the catalog's subscriber count.
     *
     * @param clientId     the client the company belongs to
     * @param companyId    the company
     * @param assignmentId the assignment to remove
     * @throws com.psc.cl.client.exception.ServiceAssignmentNotFoundException
     *         when the company holds no assignment with this identifier
     */
    void removeService(UUID clientId, UUID companyId, UUID assignmentId);
}
