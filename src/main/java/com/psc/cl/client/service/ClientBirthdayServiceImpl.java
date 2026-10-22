package com.psc.cl.client.service;

import com.psc.cl.client.dto.ClientBirthdayResponse;
import com.psc.cl.client.model.Client;
import com.psc.cl.client.repository.ClientRepository;
import com.psc.cl.managercatalog.model.Manager;
import com.psc.cl.managercatalog.repository.ManagerRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Month;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Default {@link ClientBirthdayService} backed by {@link ClientRepository}.
 */
@Service
@RequiredArgsConstructor
public class ClientBirthdayServiceImpl implements ClientBirthdayService {

    private static final Logger log = LoggerFactory.getLogger(ClientBirthdayServiceImpl.class);

    private final ClientRepository clientRepository;

    private final ManagerRepository managerRepository;

    @Override
    @Transactional(readOnly = true)
    public List<ClientBirthdayResponse> listBirthdays(Month month) {
        List<Client> clients = clientRepository.findByBirthdayMonth(month.getValue());

        // One query for every manager involved, rather than one per client.
        Set<UUID> managerIds = clients.stream().map(Client::getManagerId).collect(Collectors.toSet());
        Map<UUID, Manager> managersById = managerRepository.findAllById(managerIds).stream()
                .collect(Collectors.toMap(Manager::getId, Function.identity()));

        log.debug("Found {} client(s) with a birthday in {}", clients.size(), month);
        return clients.stream()
                .map(client -> ClientBirthdayResponse.from(client,
                        assignedManager(managersById.get(client.getManagerId()), client.getManagerId())))
                .toList();
    }

    /**
     * The foreign key guarantees an assigned manager exists, so a miss here means the data is
     * inconsistent and is reported as a server error.
     */
    private static Manager assignedManager(Manager manager, UUID managerId) {
        if (manager == null) {
            throw new IllegalStateException("Assigned manager " + managerId + " does not exist");
        }
        return manager;
    }
}
