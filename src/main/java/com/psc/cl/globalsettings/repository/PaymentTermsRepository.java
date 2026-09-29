package com.psc.cl.globalsettings.repository;

import com.psc.cl.globalsettings.model.PaymentTerms;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Persistence access for the single {@link PaymentTerms} record held under Global Settings.
 */
@Repository
public interface PaymentTermsRepository extends JpaRepository<PaymentTerms, UUID> {

    /**
     * Payment terms are a singleton global setting, so at most one record exists. Ordering keeps
     * the lookup deterministic even if the singleton index were ever relaxed.
     *
     * @return the stored payment terms, or empty when none have been saved yet
     */
    Optional<PaymentTerms> findFirstByOrderByCreatedAtAsc();
}
