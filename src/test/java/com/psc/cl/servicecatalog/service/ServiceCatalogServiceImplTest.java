package com.psc.cl.servicecatalog.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.psc.cl.servicecatalog.dto.CatalogServiceRequest;
import com.psc.cl.servicecatalog.dto.CatalogServiceResponse;
import com.psc.cl.servicecatalog.dto.InvoiceSchedule;
import com.psc.cl.servicecatalog.exception.CatalogServiceNotFoundException;
import com.psc.cl.servicecatalog.exception.ServiceInUseException;
import com.psc.cl.servicecatalog.exception.ServiceNameAlreadyExistsException;
import com.psc.cl.servicecatalog.model.BillingFrequency;
import com.psc.cl.servicecatalog.model.CatalogService;
import com.psc.cl.servicecatalog.model.QuarterInvoiceMonth;
import com.psc.cl.servicecatalog.model.ServiceCategory;
import com.psc.cl.servicecatalog.repository.CatalogServiceRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.time.Month;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ExtendWith(MockitoExtension.class)
class ServiceCatalogServiceImplTest {

    @Mock
    private CatalogServiceRepository catalogServiceRepository;

    @InjectMocks
    private ServiceCatalogServiceImpl serviceCatalogService;

    @Captor
    private ArgumentCaptor<CatalogService> catalogServiceCaptor;

    @Test
    @DisplayName("stores a monthly service with its day of month")
    void createsMonthlyService() {
        stubNameFree();
        stubSave();

        CatalogServiceResponse response = serviceCatalogService.createService(
                monthlyRequest().build());

        verify(catalogServiceRepository).saveAndFlush(catalogServiceCaptor.capture());
        CatalogService saved = catalogServiceCaptor.getValue();
        assertThat(saved.getServiceName()).isEqualTo("GST Monthly Return Filing");
        assertThat(saved.getBillingFrequency()).isEqualTo(BillingFrequency.MONTHLY);
        assertThat(saved.getInvoiceDayOfMonth()).isEqualTo(7);
        assertThat(saved.getInvoiceMonthOfQuarter()).isNull();
        assertThat(saved.getInvoiceMonth()).isNull();
        assertThat(response.getInvoiceSchedule().dayOfMonth()).isEqualTo(7);
    }

    @Test
    @DisplayName("stores a quarterly service with its month of quarter and day of month")
    void createsQuarterlyService() {
        stubNameFree();
        stubSave();

        CatalogServiceResponse response = serviceCatalogService.createService(
                CatalogServiceRequest.builder()
                        .serviceName("TDS Quarterly Return")
                        .category(ServiceCategory.COMPLIANCE)
                        .billingFrequency(BillingFrequency.QUARTERLY)
                        .standardFee(new BigDecimal("7500.00"))
                        .invoiceSchedule(InvoiceSchedule.builder()
                                .monthOfQuarter(QuarterInvoiceMonth.FIRST_MONTH)
                                .dayOfMonth(10)
                                .build())
                        .build());

        assertThat(response.getInvoiceSchedule().monthOfQuarter())
                .isEqualTo(QuarterInvoiceMonth.FIRST_MONTH);
        assertThat(response.getInvoiceSchedule().dayOfMonth()).isEqualTo(10);
        assertThat(response.getInvoiceSchedule().month()).isNull();
    }

    @Test
    @DisplayName("stores an annual service with its month and day of month")
    void createsAnnualService() {
        stubNameFree();
        stubSave();

        CatalogServiceResponse response = serviceCatalogService.createService(
                CatalogServiceRequest.builder()
                        .serviceName("Statutory Audit")
                        .category(ServiceCategory.AUDIT)
                        .billingFrequency(BillingFrequency.ANNUAL)
                        .standardFee(new BigDecimal("50000.00"))
                        .invoiceSchedule(InvoiceSchedule.builder()
                                .month(Month.APRIL)
                                .dayOfMonth(15)
                                .build())
                        .build());

        assertThat(response.getInvoiceSchedule().month()).isEqualTo(Month.APRIL);
        assertThat(response.getInvoiceSchedule().dayOfMonth()).isEqualTo(15);
        assertThat(response.getInvoiceSchedule().monthOfQuarter()).isNull();
    }

    @Test
    @DisplayName("stores a one-off service with no invoice schedule at all")
    void createsOneOffService() {
        stubNameFree();
        stubSave();

        CatalogServiceResponse response = serviceCatalogService.createService(
                CatalogServiceRequest.builder()
                        .serviceName("Company Incorporation")
                        .category(ServiceCategory.ADVISORY)
                        .billingFrequency(BillingFrequency.ONE_OFF)
                        .standardFee(new BigDecimal("25000.00"))
                        .build());

        verify(catalogServiceRepository).saveAndFlush(catalogServiceCaptor.capture());
        CatalogService saved = catalogServiceCaptor.getValue();
        assertThat(saved.getInvoiceDayOfMonth()).isNull();
        assertThat(saved.getInvoiceMonthOfQuarter()).isNull();
        assertThat(saved.getInvoiceMonth()).isNull();
        assertThat(response.getInvoiceSchedule()).isNull();
    }

    @Test
    @DisplayName("applies the 18 percent GST default when no rate is supplied")
    void appliesDefaultGstRate() {
        stubNameFree();
        stubSave();

        CatalogServiceResponse response = serviceCatalogService.createService(
                monthlyRequest().build());

        assertThat(response.getGstRatePercent()).isEqualByComparingTo("18.00");
    }

    @Test
    @DisplayName("keeps a supplied GST rate instead of the default")
    void keepsSuppliedGstRate() {
        stubNameFree();
        stubSave();

        CatalogServiceResponse response = serviceCatalogService.createService(
                monthlyRequest().gstRatePercent(new BigDecimal("5.00")).build());

        assertThat(response.getGstRatePercent()).isEqualByComparingTo("5.00");
    }

    @Test
    @DisplayName("starts a new service at zero subscribing companies")
    void startsAtZeroUsedByCompanies() {
        stubNameFree();
        stubSave();

        CatalogServiceResponse response = serviceCatalogService.createService(
                monthlyRequest().build());

        assertThat(response.getUsedByCompanies()).isZero();
    }

    @Test
    @DisplayName("trims the service name and blank description before storing")
    void normalisesText() {
        stubNameFree();
        stubSave();

        serviceCatalogService.createService(monthlyRequest()
                .serviceName("  GST Monthly Return Filing  ")
                .description("   ")
                .build());

        verify(catalogServiceRepository).saveAndFlush(catalogServiceCaptor.capture());
        CatalogService saved = catalogServiceCaptor.getValue();
        assertThat(saved.getServiceName()).isEqualTo("GST Monthly Return Filing");
        assertThat(saved.getDescription()).isNull();
    }

    @Test
    @DisplayName("rejects a name another service already uses, ignoring case")
    void rejectsDuplicateName() {
        when(catalogServiceRepository.existsByServiceNameIgnoreCase("GST Monthly Return Filing"))
                .thenReturn(true);

        assertThatThrownBy(() -> serviceCatalogService.createService(monthlyRequest().build()))
                .isInstanceOf(ServiceNameAlreadyExistsException.class)
                .hasMessageContaining("GST Monthly Return Filing");

        verify(catalogServiceRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("maps a unique index violation from a concurrent create onto a conflict")
    void mapsIntegrityViolationToConflict() {
        stubNameFree();
        when(catalogServiceRepository.saveAndFlush(any(CatalogService.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        assertThatThrownBy(() -> serviceCatalogService.createService(monthlyRequest().build()))
                .isInstanceOf(ServiceNameAlreadyExistsException.class);
    }

    @Test
    @DisplayName("returns the stored service on read by id")
    void readsServiceById() {
        CatalogService stored = stored();
        when(catalogServiceRepository.findById(stored.getId())).thenReturn(Optional.of(stored));

        CatalogServiceResponse response = serviceCatalogService.getService(stored.getId());

        assertThat(response.getId()).isEqualTo(stored.getId());
        assertThat(response.getServiceName()).isEqualTo("GST Monthly Return Filing");
    }

    @Test
    @DisplayName("raises not found when reading an unknown id")
    void readFailsForUnknownId() {
        UUID missing = UUID.randomUUID();
        when(catalogServiceRepository.findById(missing)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> serviceCatalogService.getService(missing))
                .isInstanceOf(CatalogServiceNotFoundException.class)
                .hasMessageContaining(missing.toString());
    }

    @Test
    @DisplayName("lists the catalog ordered by service name")
    void listsCatalogOrderedByName() {
        when(catalogServiceRepository.findAll(any(Sort.class))).thenReturn(List.of(stored()));

        List<CatalogServiceResponse> services = serviceCatalogService.listServices();

        assertThat(services).hasSize(1);
        assertThat(services.getFirst().getServiceName()).isEqualTo("GST Monthly Return Filing");

        ArgumentCaptor<Sort> sortCaptor = ArgumentCaptor.forClass(Sort.class);
        verify(catalogServiceRepository).findAll(sortCaptor.capture());
        assertThat(sortCaptor.getValue()).isEqualTo(Sort.by(Sort.Direction.ASC, "serviceName"));
    }

    @Test
    @DisplayName("returns an empty list when the catalog holds nothing")
    void listsEmptyCatalog() {
        when(catalogServiceRepository.findAll(any(Sort.class))).thenReturn(List.of());

        assertThat(serviceCatalogService.listServices()).isEmpty();
    }

    @Test
    @DisplayName("replaces a service wholesale on update")
    void updatesService() {
        CatalogService stored = stored();
        when(catalogServiceRepository.findById(stored.getId())).thenReturn(Optional.of(stored));
        when(catalogServiceRepository.existsByServiceNameIgnoreCaseAndIdNot(any(), any()))
                .thenReturn(false);
        stubSave();

        CatalogServiceResponse response = serviceCatalogService.updateService(stored.getId(),
                CatalogServiceRequest.builder()
                        .serviceName("Statutory Audit")
                        .category(ServiceCategory.AUDIT)
                        .billingFrequency(BillingFrequency.ANNUAL)
                        .standardFee(new BigDecimal("50000.00"))
                        .invoiceSchedule(InvoiceSchedule.builder()
                                .month(Month.APRIL)
                                .dayOfMonth(15)
                                .build())
                        .build());

        verify(catalogServiceRepository).saveAndFlush(catalogServiceCaptor.capture());
        CatalogService saved = catalogServiceCaptor.getValue();
        assertThat(saved).isSameAs(stored);
        assertThat(saved.getServiceName()).isEqualTo("Statutory Audit");
        assertThat(saved.getBillingFrequency()).isEqualTo(BillingFrequency.ANNUAL);
        assertThat(saved.getInvoiceMonth()).isEqualTo(Month.APRIL);
        assertThat(saved.getInvoiceDayOfMonth()).isEqualTo(15);
        assertThat(response.getInvoiceSchedule().month()).isEqualTo(Month.APRIL);
    }

    @Test
    @DisplayName("leaves the subscriber count untouched on update")
    void updateLeavesSubscriberCountAlone() {
        CatalogService stored = stored();
        stored.setUsedByCompanies(12);
        when(catalogServiceRepository.findById(stored.getId())).thenReturn(Optional.of(stored));
        when(catalogServiceRepository.existsByServiceNameIgnoreCaseAndIdNot(any(), any()))
                .thenReturn(false);
        stubSave();

        CatalogServiceResponse response = serviceCatalogService.updateService(stored.getId(),
                monthlyRequest().build());

        assertThat(response.getUsedByCompanies()).isEqualTo(12);
    }

    @Test
    @DisplayName("lets a service keep its own name on update")
    void updateAllowsKeepingOwnName() {
        CatalogService stored = stored();
        when(catalogServiceRepository.findById(stored.getId())).thenReturn(Optional.of(stored));
        when(catalogServiceRepository.existsByServiceNameIgnoreCaseAndIdNot(any(), any()))
                .thenReturn(false);
        stubSave();

        serviceCatalogService.updateService(stored.getId(), monthlyRequest().build());

        verify(catalogServiceRepository).existsByServiceNameIgnoreCaseAndIdNot(
                eq("GST Monthly Return Filing"), eq(stored.getId()));
        verify(catalogServiceRepository).saveAndFlush(any(CatalogService.class));
    }

    @Test
    @DisplayName("rejects an update onto a name a different service already uses")
    void updateRejectsDuplicateName() {
        CatalogService stored = stored();
        when(catalogServiceRepository.findById(stored.getId())).thenReturn(Optional.of(stored));
        when(catalogServiceRepository.existsByServiceNameIgnoreCaseAndIdNot(any(), any()))
                .thenReturn(true);

        assertThatThrownBy(() -> serviceCatalogService.updateService(stored.getId(),
                monthlyRequest().build()))
                .isInstanceOf(ServiceNameAlreadyExistsException.class);

        verify(catalogServiceRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("raises not found when updating an unknown id")
    void updateFailsForUnknownId() {
        UUID missing = UUID.randomUUID();
        when(catalogServiceRepository.findById(missing)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> serviceCatalogService.updateService(missing,
                monthlyRequest().build()))
                .isInstanceOf(CatalogServiceNotFoundException.class);

        verify(catalogServiceRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("deletes a service no company subscribes to")
    void deletesUnusedService() {
        CatalogService stored = stored();
        when(catalogServiceRepository.findById(stored.getId())).thenReturn(Optional.of(stored));

        serviceCatalogService.deleteService(stored.getId());

        verify(catalogServiceRepository).delete(stored);
    }

    @Test
    @DisplayName("refuses to delete a service companies still subscribe to")
    void refusesToDeleteServiceInUse() {
        CatalogService stored = stored();
        stored.setUsedByCompanies(12);
        when(catalogServiceRepository.findById(stored.getId())).thenReturn(Optional.of(stored));

        assertThatThrownBy(() -> serviceCatalogService.deleteService(stored.getId()))
                .isInstanceOf(ServiceInUseException.class)
                .hasMessageContaining("12");

        verify(catalogServiceRepository, never()).delete(any());
    }

    @Test
    @DisplayName("raises not found when deleting an unknown id")
    void deleteFailsForUnknownId() {
        UUID missing = UUID.randomUUID();
        when(catalogServiceRepository.findById(missing)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> serviceCatalogService.deleteService(missing))
                .isInstanceOf(CatalogServiceNotFoundException.class);

        verify(catalogServiceRepository, never()).delete(any());
    }

    private CatalogService stored() {
        CatalogService service = new CatalogService();
        service.setId(UUID.randomUUID());
        service.setServiceName("GST Monthly Return Filing");
        service.setCategory(ServiceCategory.COMPLIANCE);
        service.setBillingFrequency(BillingFrequency.MONTHLY);
        service.setStandardFee(new BigDecimal("5000.00"));
        service.setGstRatePercent(new BigDecimal("18.00"));
        service.setInvoiceDayOfMonth(7);
        service.setUsedByCompanies(0);
        return service;
    }

    private CatalogServiceRequest.CatalogServiceRequestBuilder monthlyRequest() {
        return CatalogServiceRequest.builder()
                .serviceName("GST Monthly Return Filing")
                .description("Preparation and filing of GSTR-1 and GSTR-3B each month")
                .category(ServiceCategory.COMPLIANCE)
                .billingFrequency(BillingFrequency.MONTHLY)
                .standardFee(new BigDecimal("5000.00"))
                .invoiceSchedule(InvoiceSchedule.builder().dayOfMonth(7).build());
    }

    private void stubNameFree() {
        when(catalogServiceRepository.existsByServiceNameIgnoreCase(any())).thenReturn(false);
    }

    private void stubSave() {
        when(catalogServiceRepository.saveAndFlush(any(CatalogService.class))).thenAnswer(invocation -> {
            CatalogService toSave = invocation.getArgument(0);
            if (toSave.getId() == null) {
                toSave.setId(UUID.randomUUID());
            }
            return toSave;
        });
    }
}
