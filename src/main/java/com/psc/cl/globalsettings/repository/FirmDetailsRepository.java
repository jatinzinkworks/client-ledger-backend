package com.psc.cl.globalsettings.repository;

import com.psc.cl.globalsettings.model.FirmDetails;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Persistence access for the single {@link FirmDetails} record held under Global Settings.
 */
@Repository
public interface FirmDetailsRepository extends JpaRepository<FirmDetails, UUID> {

    /**
     * Firm details are a singleton global setting, so at most one record exists. Ordering keeps
     * the lookup deterministic even if the singleton index were ever relaxed.
     *
     * @return the stored firm details, or empty when none have been saved yet
     */
    Optional<FirmDetails> findFirstByOrderByCreatedAtAsc();
}
