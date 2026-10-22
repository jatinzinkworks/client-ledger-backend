package com.psc.cl.client.repository;

import com.psc.cl.client.model.Client;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence access for clients and, through cascading, their companies.
 */
@Repository
public interface ClientRepository extends JpaRepository<Client, UUID> {

    /**
     * Loads every client together with their companies in one query, so listing clients does not
     * issue a further query per client.
     *
     * @param sort the order to return clients in
     * @return every client, with companies initialised
     */
    @Override
    @EntityGraph(attributePaths = "companies")
    List<Client> findAll(Sort sort);

    /**
     * Loads the clients of one manager together with their companies in one query. Served by the
     * index on {@code manager_id}.
     *
     * @param managerId the manager whose clients to load
     * @param sort      the order to return clients in
     * @return the manager's clients, with companies initialised; empty when there are none
     */
    @EntityGraph(attributePaths = "companies")
    List<Client> findByManagerId(UUID managerId, Sort sort);

    /**
     * Loads the given clients together with their companies in one query. Identifiers that name no
     * client are skipped.
     *
     * @param ids the clients to load
     * @return the clients found, with companies initialised, in no particular order
     */
    @EntityGraph(attributePaths = "companies")
    List<Client> findByIdIn(Collection<UUID> ids);

    /**
     * Reads a client and locks its row until the transaction ends, so concurrent deletions of the
     * client's companies take turns and cannot together remove the last one.
     *
     * @param id the client identifier
     * @return the locked client, or empty when it does not exist
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Client c where c.id = :id")
    Optional<Client> findByIdForUpdate(@Param("id") UUID id);

    /**
     * Loads the clients whose birthday falls in a month, whatever the year, in the order to greet them:
     * by day of the month, then first and last name. Served by the index on the birthday's month.
     * Companies are not loaded.
     *
     * @param month the month of the year, 1 for January to 12 for December
     * @return the clients with a birthday in the month; empty when there are none
     */
    @Query("select c from Client c where extract(month from c.birthday) = :month "
            + "order by extract(day from c.birthday), c.firstName, c.lastName, c.id")
    List<Client> findByBirthdayMonth(@Param("month") int month);

    /**
     * Counts the clients a manager looks after, served by the index on {@code manager_id}.
     *
     * @param managerId the manager to count clients for
     * @return how many clients are assigned to the manager, zero when none
     */
    long countByManagerId(UUID managerId);

    /**
     * Counts the clients of every manager that has at least one, in a single grouped query.
     * Managers without clients do not appear.
     *
     * @return one row per manager with clients
     */
    @Query("select c.managerId as managerId, count(c) as clientCount from Client c group by c.managerId")
    List<ManagerClientCount> countClientsByManager();

    /**
     * How many clients one manager looks after, as returned by {@link #countClientsByManager()}.
     */
    interface ManagerClientCount {

        UUID getManagerId();

        long getClientCount();
    }
}
