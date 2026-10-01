package com.psc.cl.servicecatalog.repository;

import com.psc.cl.servicecatalog.model.CatalogService;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Persistence access for the Service Catalog.
 */
@Repository
public interface CatalogServiceRepository extends JpaRepository<CatalogService, UUID> {

    /**
     * Service names are unique in the catalog, ignoring case, so that the same offering cannot be
     * listed twice under different capitalisation.
     *
     * @param serviceName the name to look for
     * @return true when a service already carries this name
     */
    boolean existsByServiceNameIgnoreCase(String serviceName);

    /**
     * Same uniqueness check, excluding one record so a service can keep its own name on update.
     *
     * @param serviceName the name to look for
     * @param id          the service being updated, which is allowed to hold the name
     * @return true when a different service already carries this name
     */
    boolean existsByServiceNameIgnoreCaseAndIdNot(String serviceName, UUID id);
}
