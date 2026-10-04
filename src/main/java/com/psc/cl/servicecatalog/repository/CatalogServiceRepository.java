package com.psc.cl.servicecatalog.repository;

import com.psc.cl.servicecatalog.model.CatalogService;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
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

    /**
     * Reads a service and locks its row until the transaction ends, so concurrent assignments of
     * the same service take turns deciding whether they add a subscriber.
     *
     * @param id the service identifier
     * @return the locked service, or empty when it does not exist
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from CatalogService s where s.id = :id")
    Optional<CatalogService> findByIdForUpdate(@Param("id") UUID id);

    /**
     * Adds one subscribing company to a service. Done as a single statement in the database
     * because the column is not written through the entity.
     *
     * @param id the service identifier
     */
    @Modifying(flushAutomatically = true)
    @Query("update CatalogService s set s.usedByCompanies = s.usedByCompanies + 1 where s.id = :id")
    void incrementUsedByCompanies(@Param("id") UUID id);

    /**
     * Removes one subscribing company from a service. Never takes the count below zero, which the
     * column's check constraint would reject.
     *
     * @param id the service identifier
     */
    @Modifying(flushAutomatically = true)
    @Query("update CatalogService s set s.usedByCompanies = s.usedByCompanies - 1 "
            + "where s.id = :id and s.usedByCompanies > 0")
    void decrementUsedByCompanies(@Param("id") UUID id);
}
