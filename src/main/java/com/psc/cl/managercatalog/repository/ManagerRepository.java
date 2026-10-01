package com.psc.cl.managercatalog.repository;

import com.psc.cl.managercatalog.model.Manager;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Persistence access for the Manager Catalog.
 */
@Repository
public interface ManagerRepository extends JpaRepository<Manager, UUID> {

    /**
     * Email identifies a person, so it must not repeat under different capitalisation.
     *
     * @param email the address to look for
     * @return true when a manager already holds this address
     */
    boolean existsByEmailIgnoreCase(String email);

    /**
     * Same uniqueness check, excluding one record so a manager can keep their own address on
     * update.
     *
     * @param email the address to look for
     * @param id    the manager being updated, who is allowed to hold the address
     * @return true when a different manager already holds this address
     */
    boolean existsByEmailIgnoreCaseAndIdNot(String email, UUID id);
}
