package com.psc.cl.servicecatalog.service;

import com.psc.cl.ledger.model.FinancialYear;
import com.psc.cl.servicecatalog.dto.CatalogServiceRequest;
import com.psc.cl.servicecatalog.dto.CatalogServiceResponse;
import com.psc.cl.servicecatalog.dto.ServiceFeeBulkUpdateRequest;
import com.psc.cl.servicecatalog.model.ServiceCategory;

import java.util.List;
import java.util.UUID;

/**
 * Business operations over the Service Catalog.
 */
public interface ServiceCatalogService {

    /**
     * Adds a new service to the catalog.
     *
     * @param request the service to create
     * @return the created service, including its generated identifier
     * @throws com.psc.cl.servicecatalog.exception.ServiceNameAlreadyExistsException
     *         when another service already uses the same name
     */
    CatalogServiceResponse createService(CatalogServiceRequest request);

    /**
     * Reads one service by its identifier.
     *
     * @param id the service identifier
     * @return the stored service
     * @throws com.psc.cl.servicecatalog.exception.CatalogServiceNotFoundException
     *         when no service carries this identifier
     */
    CatalogServiceResponse getService(UUID id);

    /**
     * Lists every service in the catalog, ordered by name, each with what it was billed across
     * every client: in a financial year when one is given, otherwise across every year to date.
     *
     * @param financialYear the year to total each service's billed amount for, or null for every
     *                      year to date
     * @return the catalog, empty when nothing has been added yet
     */
    List<CatalogServiceResponse> listServices(FinancialYear financialYear);

    /**
     * Lists every category a service can belong to, in their declared order.
     *
     * @return every service category
     */
    List<ServiceCategory> listCategories();

    /**
     * Replaces a service wholesale. Fields omitted from the request are cleared, and the
     * subscriber count is left untouched because it is never caller supplied.
     *
     * @param id      the service to replace
     * @param request the new content
     * @return the updated service
     * @throws com.psc.cl.servicecatalog.exception.CatalogServiceNotFoundException
     *         when no service carries this identifier
     * @throws com.psc.cl.servicecatalog.exception.ServiceNameAlreadyExistsException
     *         when a different service already uses the requested name
     */
    CatalogServiceResponse updateService(UUID id, CatalogServiceRequest request);

    /**
     * Changes the standard fees of several services at once, all or nothing: when any service named
     * cannot be updated, none is. Only the fee changes; every other field is left as it is. Charges
     * already in the ledger keep the fee they were posted with; later postings use the new one.
     *
     * @param request the new fee of each service to change
     * @return the updated services, in the order the request named them
     * @throws com.psc.cl.servicecatalog.exception.DuplicateServiceFeeUpdateException
     *         when the request names a service more than once
     * @throws com.psc.cl.servicecatalog.exception.CatalogServicesNotFoundException
     *         when any identifier names no service, listing each such identifier
     */
    List<CatalogServiceResponse> updateFees(ServiceFeeBulkUpdateRequest request);

    /**
     * Removes a service from the catalog.
     *
     * @param id the service to remove
     * @throws com.psc.cl.servicecatalog.exception.CatalogServiceNotFoundException
     *         when no service carries this identifier
     * @throws com.psc.cl.servicecatalog.exception.ServiceInUseException
     *         when companies still subscribe to the service
     */
    void deleteService(UUID id);
}
