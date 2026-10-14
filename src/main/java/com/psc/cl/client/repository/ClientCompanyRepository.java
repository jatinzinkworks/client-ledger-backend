package com.psc.cl.client.repository;

import com.psc.cl.client.model.ClientCompany;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Read access to individual client companies. Companies are written only through their client.
 */
@Repository
public interface ClientCompanyRepository extends JpaRepository<ClientCompany, UUID> {

    /**
     * Finds a company only when it belongs to the given client, so a company cannot be reached
     * under another client's path.
     *
     * @param id       the company identifier
     * @param clientId the client the company must belong to
     * @return the company, or empty when the client holds no company with this identifier
     */
    Optional<ClientCompany> findByIdAndClientId(UUID id, UUID clientId);

    /**
     * Lists the companies of one client, by name.
     *
     * @param clientId the client
     * @return the client's companies; empty when they have none or the client does not exist
     */
    List<ClientCompany> findByClientIdOrderByCompanyNameAsc(UUID clientId);

    /**
     * Lists every company of every client with its client and name, in one query and without loading
     * the entities, ordered by client and then company so the order is stable.
     *
     * @return one row per company; empty when there are no companies
     */
    @Query("select c.id as companyId, c.client.id as clientId, c.companyName as companyName "
            + "from ClientCompany c order by c.client.id, c.id")
    List<CompanyRef> findAllCompanyRefs();

    /**
     * A company of a client, as returned by {@link #findAllCompanyRefs()}.
     */
    interface CompanyRef {

        UUID getCompanyId();

        UUID getClientId();

        String getCompanyName();
    }
}
