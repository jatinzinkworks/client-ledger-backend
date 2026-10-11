package com.psc.cl.client.service;

import com.psc.cl.client.dto.ServiceAssignmentRequest;
import com.psc.cl.client.dto.ServiceAssignmentResponse;
import com.psc.cl.client.dto.ServiceAssignmentUpdateRequest;
import com.psc.cl.client.exception.AssignedServiceNotFoundException;
import com.psc.cl.client.exception.ClientNotFoundException;
import com.psc.cl.client.exception.CompanyNotFoundException;
import com.psc.cl.client.exception.ServiceAssignmentNotFoundException;
import com.psc.cl.client.model.ClientCompany;
import com.psc.cl.client.model.ServiceAssignment;
import com.psc.cl.client.repository.ClientCompanyRepository;
import com.psc.cl.client.repository.ClientRepository;
import com.psc.cl.client.repository.ServiceAssignmentRepository;
import com.psc.cl.servicecatalog.model.CatalogService;
import com.psc.cl.servicecatalog.repository.CatalogServiceRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Default {@link ServiceAssignmentService} backed by {@link ServiceAssignmentRepository}.
 *
 * <p>Assigning and removing a service both lock the catalog service's row before deciding whether
 * the company starts or stops counting as a subscriber. Concurrent changes to the same service
 * therefore take turns, and the subscriber count cannot drift.
 */
@Service
@RequiredArgsConstructor
public class ServiceAssignmentServiceImpl implements ServiceAssignmentService {

    private static final Logger log = LoggerFactory.getLogger(ServiceAssignmentServiceImpl.class);

    private final ClientRepository clientRepository;

    private final ClientCompanyRepository clientCompanyRepository;

    private final ServiceAssignmentRepository serviceAssignmentRepository;

    private final CatalogServiceRepository catalogServiceRepository;

    @Override
    @Transactional
    public ServiceAssignmentResponse assignService(UUID clientId, UUID companyId,
                                                   ServiceAssignmentRequest request) {
        ClientCompany company = requireCompany(clientId, companyId);

        UUID serviceId = request.serviceId();
        CatalogService service = catalogServiceRepository.findByIdForUpdate(serviceId)
                .orElseThrow(() -> new AssignedServiceNotFoundException(serviceId));
        boolean newSubscriber = !serviceAssignmentRepository.existsByCompanyIdAndCatalogServiceId(
                companyId, serviceId);

        ServiceAssignment assignment = new ServiceAssignment();
        assignment.setCompany(company);
        assignment.setCatalogServiceId(serviceId);
        assignment.setDeliveryDate(request.deliveryDate());
        applySchedule(service, assignment);
        assignment.setDiscountPercent(
                Percentages.orElse(request.discountPercent(), company.effectiveDiscountPercent()));
        assignment.setComment(trimToNull(request.comment()));

        ServiceAssignment saved = serviceAssignmentRepository.save(assignment);
        if (newSubscriber) {
            catalogServiceRepository.incrementUsedByCompanies(serviceId);
        }
        log.info("Assigned service {} to company {} of client {} as assignment {}{}",
                serviceId, companyId, clientId, saved.getId(), newSubscriber ? " (new subscriber)" : "");

        return ServiceAssignmentResponse.from(saved, clientId, service);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ServiceAssignmentResponse> listServices(UUID clientId, UUID companyId) {
        requireCompany(clientId, companyId);
        List<ServiceAssignment> assignments =
                serviceAssignmentRepository.findByCompanyIdOrderByDeliveryDateDescCreatedAtDesc(companyId);

        // One query for every catalog service involved, rather than one per assignment.
        Set<UUID> serviceIds = assignments.stream()
                .map(ServiceAssignment::getCatalogServiceId)
                .collect(Collectors.toSet());
        Map<UUID, CatalogService> servicesById = catalogServiceRepository.findAllById(serviceIds).stream()
                .collect(Collectors.toMap(CatalogService::getId, Function.identity()));

        return assignments.stream()
                .map(assignment -> ServiceAssignmentResponse.from(assignment, clientId,
                        catalogService(servicesById.get(assignment.getCatalogServiceId()),
                                assignment.getCatalogServiceId())))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ServiceAssignmentResponse getService(UUID clientId, UUID companyId, UUID assignmentId) {
        requireCompany(clientId, companyId);
        ServiceAssignment assignment = requireAssignment(companyId, assignmentId);
        UUID serviceId = assignment.getCatalogServiceId();
        return ServiceAssignmentResponse.from(assignment, clientId,
                catalogService(catalogServiceRepository.findById(serviceId).orElse(null), serviceId));
    }

    @Override
    @Transactional
    public ServiceAssignmentResponse updateService(UUID clientId, UUID companyId, UUID assignmentId,
                                                   ServiceAssignmentUpdateRequest request) {
        ClientCompany company = requireCompany(clientId, companyId);
        ServiceAssignment assignment = requireAssignment(companyId, assignmentId);
        UUID serviceId = assignment.getCatalogServiceId();
        CatalogService service = catalogService(catalogServiceRepository.findById(serviceId).orElse(null), serviceId);

        assignment.setDeliveryDate(request.deliveryDate());
        applySchedule(service, assignment);
        assignment.setDiscountPercent(
                Percentages.orElse(request.discountPercent(), company.effectiveDiscountPercent()));
        assignment.setComment(trimToNull(request.comment()));

        // Flushing here stamps updatedAt before it is returned.
        serviceAssignmentRepository.flush();
        log.info("Updated service assignment {} of company {} of client {}, next due {}",
                assignmentId, companyId, clientId, assignment.getNextDueDate());

        return ServiceAssignmentResponse.from(assignment, clientId, service);
    }

    @Override
    @Transactional
    public void removeService(UUID clientId, UUID companyId, UUID assignmentId) {
        requireCompany(clientId, companyId);
        ServiceAssignment assignment = requireAssignment(companyId, assignmentId);
        UUID serviceId = assignment.getCatalogServiceId();

        // Taken before the delete, so the check below sees every other change to this service.
        catalogService(catalogServiceRepository.findByIdForUpdate(serviceId).orElse(null), serviceId);

        serviceAssignmentRepository.delete(assignment);
        serviceAssignmentRepository.flush();
        boolean lastOfService = !serviceAssignmentRepository.existsByCompanyIdAndCatalogServiceId(
                companyId, serviceId);
        if (lastOfService) {
            catalogServiceRepository.decrementUsedByCompanies(serviceId);
        }
        log.info("Removed service assignment {} of service {} from company {} of client {}{}",
                assignmentId, serviceId, companyId, clientId, lastOfService ? " (no longer a subscriber)" : "");
    }

    /**
     * Takes the service type and next due date from the catalog's current configuration, so a
     * write always reflects how the service is billed now. Call after setting the delivery date.
     */
    private void applySchedule(CatalogService service, ServiceAssignment assignment) {
        assignment.setServiceType(service.getBillingFrequency());
        assignment.setNextDueDate(NextDueDates.onOrAfter(service, assignment.getDeliveryDate()));
    }

    private ClientCompany requireCompany(UUID clientId, UUID companyId) {
        if (!clientRepository.existsById(clientId)) {
            throw new ClientNotFoundException(clientId);
        }
        return clientCompanyRepository.findByIdAndClientId(companyId, clientId)
                .orElseThrow(() -> new CompanyNotFoundException(clientId, companyId));
    }

    private ServiceAssignment requireAssignment(UUID companyId, UUID assignmentId) {
        return serviceAssignmentRepository.findByIdAndCompanyId(assignmentId, companyId)
                .orElseThrow(() -> new ServiceAssignmentNotFoundException(companyId, assignmentId));
    }

    /**
     * The foreign key guarantees an assigned catalog service exists, so a miss here means the data
     * is inconsistent and is reported as a server error.
     */
    private CatalogService catalogService(CatalogService service, UUID serviceId) {
        if (service == null) {
            throw new IllegalStateException("Assigned catalog service " + serviceId + " does not exist");
        }
        return service;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
