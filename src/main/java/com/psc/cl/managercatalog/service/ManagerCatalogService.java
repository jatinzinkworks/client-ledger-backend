package com.psc.cl.managercatalog.service;

import com.psc.cl.managercatalog.dto.ManagerRequest;
import com.psc.cl.managercatalog.dto.ManagerResponse;

import java.util.List;
import java.util.UUID;

/**
 * Business operations over the Manager Catalog.
 */
public interface ManagerCatalogService {

    /**
     * Adds a new manager to the catalog.
     *
     * @param request the manager to create
     * @return the created manager, including the generated identifier
     * @throws com.psc.cl.managercatalog.exception.ManagerEmailAlreadyExistsException
     *         when another manager already holds the same email address
     */
    ManagerResponse createManager(ManagerRequest request);

    /**
     * Reads one manager by identifier.
     *
     * @param id the manager identifier
     * @return the stored manager
     * @throws com.psc.cl.managercatalog.exception.ManagerNotFoundException
     *         when no manager carries this identifier
     */
    ManagerResponse getManager(UUID id);

    /**
     * Lists every manager, ordered by last name then first name.
     *
     * @return the catalog, empty when nobody has been added yet
     */
    List<ManagerResponse> listManagers();

    /**
     * Replaces a manager wholesale.
     *
     * @param id      the manager to replace
     * @param request the new content
     * @return the updated manager
     * @throws com.psc.cl.managercatalog.exception.ManagerNotFoundException
     *         when no manager carries this identifier
     * @throws com.psc.cl.managercatalog.exception.ManagerEmailAlreadyExistsException
     *         when a different manager already holds the requested email address
     */
    ManagerResponse updateManager(UUID id, ManagerRequest request);

    /**
     * Removes a manager from the catalog.
     *
     * @param id the manager to remove
     * @throws com.psc.cl.managercatalog.exception.ManagerNotFoundException
     *         when no manager carries this identifier
     */
    void deleteManager(UUID id);
}
