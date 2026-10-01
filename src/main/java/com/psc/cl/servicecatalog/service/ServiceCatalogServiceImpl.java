package com.psc.cl.servicecatalog.service;

import com.psc.cl.servicecatalog.dto.CatalogServiceRequest;
import com.psc.cl.servicecatalog.dto.CatalogServiceResponse;
import com.psc.cl.servicecatalog.exception.CatalogServiceNotFoundException;
import com.psc.cl.servicecatalog.exception.ServiceInUseException;
import com.psc.cl.servicecatalog.exception.ServiceNameAlreadyExistsException;
import com.psc.cl.servicecatalog.model.CatalogService;
import com.psc.cl.servicecatalog.repository.CatalogServiceRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Default {@link ServiceCatalogService} backed by {@link CatalogServiceRepository}.
 */
@Service
@RequiredArgsConstructor
public class ServiceCatalogServiceImpl implements ServiceCatalogService {

    private static final Logger log = LoggerFactory.getLogger(ServiceCatalogServiceImpl.class);

    private static final Sort BY_NAME = Sort.by(Sort.Direction.ASC, "serviceName");

    private final CatalogServiceRepository catalogServiceRepository;

    @Override
    @Transactional
    public CatalogServiceResponse createService(CatalogServiceRequest request) {
        String serviceName = request.serviceName().trim();
        if (catalogServiceRepository.existsByServiceNameIgnoreCase(serviceName)) {
            throw new ServiceNameAlreadyExistsException(serviceName);
        }

        CatalogService service = new CatalogService();
        service.setUsedByCompanies(0);
        apply(request, serviceName, service);

        CatalogService saved = persist(service, serviceName);
        log.info("Created catalog service {} '{}' - {} billing, category {}", saved.getId(),
                saved.getServiceName(), saved.getBillingFrequency(), saved.getCategory());

        return CatalogServiceResponse.from(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public CatalogServiceResponse getService(UUID id) {
        return CatalogServiceResponse.from(require(id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<CatalogServiceResponse> listServices() {
        return catalogServiceRepository.findAll(BY_NAME).stream()
                .map(CatalogServiceResponse::from)
                .toList();
    }

    @Override
    @Transactional
    public CatalogServiceResponse updateService(UUID id, CatalogServiceRequest request) {
        CatalogService service = require(id);

        String serviceName = request.serviceName().trim();
        if (catalogServiceRepository.existsByServiceNameIgnoreCaseAndIdNot(serviceName, id)) {
            throw new ServiceNameAlreadyExistsException(serviceName);
        }

        apply(request, serviceName, service);

        CatalogService saved = persist(service, serviceName);
        log.info("Updated catalog service {} '{}' - {} billing, category {}", saved.getId(),
                saved.getServiceName(), saved.getBillingFrequency(), saved.getCategory());

        return CatalogServiceResponse.from(saved);
    }

    @Override
    @Transactional
    public void deleteService(UUID id) {
        CatalogService service = require(id);

        int usedByCompanies = service.getUsedByCompanies() == null ? 0 : service.getUsedByCompanies();
        if (usedByCompanies > 0) {
            throw new ServiceInUseException(service.getServiceName(), usedByCompanies);
        }

        catalogServiceRepository.delete(service);
        log.info("Deleted catalog service {} '{}'", id, service.getServiceName());
    }

    /**
     * Copies the request onto the entity. The subscriber count is deliberately not touched: it is
     * derived from company subscriptions and never travels on a request.
     */
    private void apply(CatalogServiceRequest request, String serviceName, CatalogService service) {
        service.setServiceName(serviceName);
        service.setDescription(trimToNull(request.description()));
        service.setCategory(request.category());
        service.setBillingFrequency(request.billingFrequency());
        service.setStandardFee(request.standardFee());
        service.setGstRatePercent(request.effectiveGstRatePercent());
        service.setInvoiceDayOfMonth(request.scheduledDayOfMonth());
        service.setInvoiceMonthOfQuarter(request.scheduledMonthOfQuarter());
        service.setInvoiceMonth(request.scheduledMonth());
    }

    private CatalogService persist(CatalogService service, String serviceName) {
        try {
            return catalogServiceRepository.saveAndFlush(service);
        } catch (DataIntegrityViolationException ex) {
            // The unique index caught a concurrent write that slipped past the name check above.
            log.warn("Concurrent write for service name '{}' was rejected by the database",
                    serviceName, ex);
            throw new ServiceNameAlreadyExistsException(serviceName);
        }
    }

    private CatalogService require(UUID id) {
        return catalogServiceRepository.findById(id)
                .orElseThrow(() -> new CatalogServiceNotFoundException(id));
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
