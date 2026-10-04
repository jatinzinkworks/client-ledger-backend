package com.psc.cl.client.repository;

import com.psc.cl.client.model.ClientCompany;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

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
}
