package com.psc.cl.client.repository;

import com.psc.cl.client.model.ServiceAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence access for services delivered to client companies.
 */
@Repository
public interface ServiceAssignmentRepository extends JpaRepository<ServiceAssignment, UUID> {

    /**
     * Lists the services of one company, most recent delivery first. Served by the
     * (company_id, catalog_service_id) index.
     *
     * @param companyId the company
     * @return the company's assignments, empty when it has none
     */
    List<ServiceAssignment> findByCompanyIdOrderByDeliveryDateDescCreatedAtDesc(UUID companyId);

    /**
     * Finds an assignment only when it belongs to the given company, so it cannot be reached under
     * another company's path.
     *
     * @param id        the assignment identifier
     * @param companyId the company the assignment must belong to
     * @return the assignment, or empty when the company holds no assignment with this identifier
     */
    Optional<ServiceAssignment> findByIdAndCompanyId(UUID id, UUID companyId);

    /**
     * Whether a company takes a given service, which decides whether an assignment adds or
     * removes a subscriber of the catalog service. Served by the (company_id, catalog_service_id)
     * index.
     *
     * @param companyId        the company
     * @param catalogServiceId the catalog service
     * @return true when the company has at least one assignment of the service
     */
    boolean existsByCompanyIdAndCatalogServiceId(UUID companyId, UUID catalogServiceId);

    /**
     * Whether any of the given companies has services assigned, so they are not removed from under
     * those records.
     *
     * @param companyIds the companies to check
     * @return true when at least one of them has a service assigned
     */
    boolean existsByCompanyIdIn(Collection<UUID> companyIds);

    /**
     * Counts the services assigned to each of the given companies in one query. Served by the
     * (company_id, catalog_service_id) index.
     *
     * @param companyIds the companies to count for
     * @return one entry per company that has services; companies without any are left out
     */
    @Query("select a.company.id as companyId, count(a) as serviceCount from ServiceAssignment a "
            + "where a.company.id in :companyIds group by a.company.id")
    List<CompanyServiceCount> countByCompanyIdIn(@Param("companyIds") Collection<UUID> companyIds);
}
