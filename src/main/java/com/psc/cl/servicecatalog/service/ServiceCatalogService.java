package com.psc.cl.servicecatalog.service;

import com.psc.cl.servicecatalog.dto.CatalogServiceRequest;
import com.psc.cl.servicecatalog.dto.CatalogServiceResponse;

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
     * Lists every service in the catalog, ordered by name.
     *
     * @return the catalog, empty when nothing has been added yet
     */
    List<CatalogServiceResponse> listServices();

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
