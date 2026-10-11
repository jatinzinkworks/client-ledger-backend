package com.psc.cl.client.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.psc.cl.client.dto.ServiceAssignmentRequest;
import com.psc.cl.client.dto.ServiceAssignmentResponse;
import com.psc.cl.client.dto.ServiceAssignmentUpdateRequest;
import com.psc.cl.client.exception.AssignedServiceNotFoundException;
import com.psc.cl.client.exception.ClientNotFoundException;
import com.psc.cl.client.exception.CompanyNotFoundException;
import com.psc.cl.client.exception.ServiceAssignmentNotFoundException;
import com.psc.cl.client.model.Client;
import com.psc.cl.client.model.ClientCompany;
import com.psc.cl.client.model.ServiceAssignment;
import com.psc.cl.client.repository.ClientCompanyRepository;
import com.psc.cl.client.repository.ClientRepository;
import com.psc.cl.client.repository.ServiceAssignmentRepository;
import com.psc.cl.servicecatalog.model.BillingFrequency;
import com.psc.cl.servicecatalog.model.CatalogService;
import com.psc.cl.servicecatalog.model.QuarterInvoiceMonth;
import com.psc.cl.servicecatalog.repository.CatalogServiceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@ExtendWith(MockitoExtension.class)
class ServiceAssignmentServiceImplTest {

    private static final UUID CLIENT_ID = UUID.fromString("7a3d9e10-5b2c-4f81-a6e4-2c8b1d0f9e35");

    private static final UUID COMPANY_ID = UUID.fromString("4c1f8a52-2d6b-4e93-b7a0-9e5d3c1f6a28");

    private static final UUID SERVICE_ID = UUID.fromString("3b8e5f21-9c4d-4a17-8e62-1f0d7a9c5b34");

    private static final LocalDate DELIVERY_DATE = LocalDate.of(2026, 10, 15);

    @Mock
    private ClientRepository clientRepository;

    @Mock
    private ClientCompanyRepository clientCompanyRepository;

    @Mock
    private ServiceAssignmentRepository serviceAssignmentRepository;

    @Mock
    private CatalogServiceRepository catalogServiceRepository;

    @InjectMocks
    private ServiceAssignmentServiceImpl serviceAssignmentService;

    @Captor
    private ArgumentCaptor<ServiceAssignment> assignmentCaptor;

    private ClientCompany company;

    @BeforeEach
    void setUp() {
        Client client = new Client();
        client.setId(CLIENT_ID);
        company = new ClientCompany();
        company.setId(COMPANY_ID);
        company.setCompanyName("Mehta Textiles Pvt Ltd");
        company.setDiscountPercent(new BigDecimal("7.50"));
        client.addCompany(company);
    }

    @Test
    @DisplayName("stores every supplied field against the company and returns the service name")
    void assignsService() {
        stubLookups();
        when(serviceAssignmentRepository.existsByCompanyIdAndCatalogServiceId(COMPANY_ID, SERVICE_ID))
                .thenReturn(false);
        stubSave();

        ServiceAssignmentResponse response = serviceAssignmentService.assignService(CLIENT_ID, COMPANY_ID,
                request().discountPercent(new BigDecimal("5")).comment("  Filing for September 2026  ").build());

        verify(serviceAssignmentRepository).save(assignmentCaptor.capture());
        ServiceAssignment saved = assignmentCaptor.getValue();
        assertThat(saved.getCompany()).isSameAs(company);
        assertThat(saved.getCatalogServiceId()).isEqualTo(SERVICE_ID);
        assertThat(saved.getDeliveryDate()).isEqualTo(DELIVERY_DATE);
        assertThat(saved.getServiceType()).isEqualTo(BillingFrequency.MONTHLY);
        assertThat(saved.getNextDueDate()).isEqualTo(LocalDate.of(2026, 11, 7));
        assertThat(saved.getDiscountPercent()).isEqualTo(new BigDecimal("5.00"));
        assertThat(saved.getComment()).isEqualTo("Filing for September 2026");

        assertThat(response.getId()).isEqualTo(saved.getId());
        assertThat(response.getClientId()).isEqualTo(CLIENT_ID);
        assertThat(response.getCompanyId()).isEqualTo(COMPANY_ID);
        assertThat(response.getServiceName()).isEqualTo("GST Monthly Return Filing");
        assertThat(response.getServiceType()).isEqualTo(BillingFrequency.MONTHLY);
        assertThat(response.getNextDueDate()).isEqualTo(LocalDate.of(2026, 11, 7));
    }

    @Test
    @DisplayName("a one-off service is stored as due on its delivery date")
    void oneOffServiceIsDueOnDelivery() {
        stubCompany();
        CatalogService oneOff = catalogService();
        oneOff.setBillingFrequency(BillingFrequency.ONE_OFF);
        oneOff.setInvoiceDayOfMonth(null);
        when(catalogServiceRepository.findByIdForUpdate(SERVICE_ID)).thenReturn(Optional.of(oneOff));
        stubSave();

        serviceAssignmentService.assignService(CLIENT_ID, COMPANY_ID, request().build());

        verify(serviceAssignmentRepository).save(assignmentCaptor.capture());
        assertThat(assignmentCaptor.getValue().getServiceType()).isEqualTo(BillingFrequency.ONE_OFF);
        assertThat(assignmentCaptor.getValue().getNextDueDate()).isEqualTo(DELIVERY_DATE);
    }

    @Test
    @DisplayName("gives the service the company's discount when none is supplied")
    void inheritsCompanyDiscount() {
        stubLookups();
        stubSave();

        serviceAssignmentService.assignService(CLIENT_ID, COMPANY_ID, request().build());

        verify(serviceAssignmentRepository).save(assignmentCaptor.capture());
        assertThat(assignmentCaptor.getValue().getDiscountPercent()).isEqualTo(new BigDecimal("7.50"));
    }

    @Test
    @DisplayName("gives the service the client's current discount when neither it nor the company sets one")
    void inheritsClientDiscountThroughCompany() {
        company.setDiscountPercent(null);
        company.getClient().setDiscountPercent(new BigDecimal("12.00"));
        stubLookups();
        stubSave();

        serviceAssignmentService.assignService(CLIENT_ID, COMPANY_ID, request().build());

        verify(serviceAssignmentRepository).save(assignmentCaptor.capture());
        assertThat(assignmentCaptor.getValue().getDiscountPercent()).isEqualTo(new BigDecimal("12.00"));
    }

    @Test
    @DisplayName("stores a blank comment as no comment")
    void blankCommentIsStoredAsNull() {
        stubLookups();
        stubSave();

        serviceAssignmentService.assignService(CLIENT_ID, COMPANY_ID, request().comment("   ").build());

        verify(serviceAssignmentRepository).save(assignmentCaptor.capture());
        assertThat(assignmentCaptor.getValue().getComment()).isNull();
    }

    @Test
    @DisplayName("counts the company as a new subscriber the first time it takes the service")
    void incrementsSubscriberCountForNewCompany() {
        stubLookups();
        when(serviceAssignmentRepository.existsByCompanyIdAndCatalogServiceId(COMPANY_ID, SERVICE_ID))
                .thenReturn(false);
        stubSave();

        serviceAssignmentService.assignService(CLIENT_ID, COMPANY_ID, request().build());

        verify(catalogServiceRepository).incrementUsedByCompanies(SERVICE_ID);
    }

    @Test
    @DisplayName("does not count the company again when it already takes the service")
    void keepsSubscriberCountForRepeatAssignment() {
        stubLookups();
        when(serviceAssignmentRepository.existsByCompanyIdAndCatalogServiceId(COMPANY_ID, SERVICE_ID))
                .thenReturn(true);
        stubSave();

        serviceAssignmentService.assignService(CLIENT_ID, COMPANY_ID, request().build());

        verify(serviceAssignmentRepository).save(any(ServiceAssignment.class));
        verify(catalogServiceRepository, never()).incrementUsedByCompanies(any());
    }

    @Test
    @DisplayName("raises client not found for an unknown client")
    void rejectsUnknownClient() {
        when(clientRepository.existsById(CLIENT_ID)).thenReturn(false);

        assertThatThrownBy(() -> serviceAssignmentService.assignService(CLIENT_ID, COMPANY_ID, request().build()))
                .isInstanceOf(ClientNotFoundException.class);

        verify(serviceAssignmentRepository, never()).save(any());
    }

    @Test
    @DisplayName("raises company not found when the client does not hold the company")
    void rejectsCompanyOfAnotherClient() {
        when(clientRepository.existsById(CLIENT_ID)).thenReturn(true);
        when(clientCompanyRepository.findByIdAndClientId(COMPANY_ID, CLIENT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> serviceAssignmentService.assignService(CLIENT_ID, COMPANY_ID, request().build()))
                .isInstanceOf(CompanyNotFoundException.class);

        verify(catalogServiceRepository, never()).findByIdForUpdate(any());
        verify(serviceAssignmentRepository, never()).save(any());
    }

    @Test
    @DisplayName("raises service not found when the service is not in the catalog")
    void rejectsUnknownService() {
        when(clientRepository.existsById(CLIENT_ID)).thenReturn(true);
        when(clientCompanyRepository.findByIdAndClientId(COMPANY_ID, CLIENT_ID)).thenReturn(Optional.of(company));
        when(catalogServiceRepository.findByIdForUpdate(SERVICE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> serviceAssignmentService.assignService(CLIENT_ID, COMPANY_ID, request().build()))
                .isInstanceOf(AssignedServiceNotFoundException.class)
                .hasMessageContaining(SERVICE_ID.toString());

        verify(serviceAssignmentRepository, never()).save(any());
        verify(catalogServiceRepository, never()).incrementUsedByCompanies(any());
    }

    @Test
    @DisplayName("lists a company's services with names and fees looked up in one call")
    void listsServices() {
        stubCompany();
        ServiceAssignment october = assignment(LocalDate.of(2026, 10, 15));
        ServiceAssignment september = assignment(LocalDate.of(2026, 9, 15));
        when(serviceAssignmentRepository.findByCompanyIdOrderByDeliveryDateDescCreatedAtDesc(COMPANY_ID))
                .thenReturn(List.of(october, september));
        when(catalogServiceRepository.findAllById(Set.of(SERVICE_ID))).thenReturn(List.of(catalogService()));

        List<ServiceAssignmentResponse> services = serviceAssignmentService.listServices(CLIENT_ID, COMPANY_ID);

        assertThat(services).extracting("id").containsExactly(october.getId(), september.getId());
        assertThat(services).allSatisfy(service -> {
            assertThat(service.getServiceName()).isEqualTo("GST Monthly Return Filing");
            assertThat(service.getServiceFee()).isEqualTo(new BigDecimal("5000.00"));
            assertThat(service.getClientId()).isEqualTo(CLIENT_ID);
            assertThat(service.getCompanyId()).isEqualTo(COMPANY_ID);
        });
    }

    @Test
    @DisplayName("returns an empty list for a company without services")
    void listsNoServices() {
        stubCompany();
        when(serviceAssignmentRepository.findByCompanyIdOrderByDeliveryDateDescCreatedAtDesc(COMPANY_ID))
                .thenReturn(List.of());

        assertThat(serviceAssignmentService.listServices(CLIENT_ID, COMPANY_ID)).isEmpty();
    }

    @Test
    @DisplayName("raises company not found when listing services of a company the client does not hold")
    void listRejectsCompanyOfAnotherClient() {
        when(clientRepository.existsById(CLIENT_ID)).thenReturn(true);
        when(clientCompanyRepository.findByIdAndClientId(COMPANY_ID, CLIENT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> serviceAssignmentService.listServices(CLIENT_ID, COMPANY_ID))
                .isInstanceOf(CompanyNotFoundException.class);
    }

    @Test
    @DisplayName("returns one service of a company")
    void readsService() {
        stubCompany();
        ServiceAssignment stored = assignment(DELIVERY_DATE);
        when(serviceAssignmentRepository.findByIdAndCompanyId(stored.getId(), COMPANY_ID))
                .thenReturn(Optional.of(stored));
        when(catalogServiceRepository.findById(SERVICE_ID)).thenReturn(Optional.of(catalogService()));

        ServiceAssignmentResponse response = serviceAssignmentService.getService(CLIENT_ID, COMPANY_ID, stored.getId());

        assertThat(response.getId()).isEqualTo(stored.getId());
        assertThat(response.getServiceName()).isEqualTo("GST Monthly Return Filing");
        assertThat(response.getDeliveryDate()).isEqualTo(DELIVERY_DATE);
    }

    @Test
    @DisplayName("raises not found when the company does not hold the assignment")
    void readFailsForAssignmentOfAnotherCompany() {
        stubCompany();
        UUID missing = UUID.randomUUID();
        when(serviceAssignmentRepository.findByIdAndCompanyId(missing, COMPANY_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> serviceAssignmentService.getService(CLIENT_ID, COMPANY_ID, missing))
                .isInstanceOf(ServiceAssignmentNotFoundException.class)
                .hasMessageContaining(missing.toString());
    }

    @Test
    @DisplayName("replaces the delivery date, discount and comment, and leaves the subscriber count alone")
    void updatesService() {
        stubCompany();
        ServiceAssignment stored = assignment(DELIVERY_DATE);
        stored.setComment("Old note");
        when(serviceAssignmentRepository.findByIdAndCompanyId(stored.getId(), COMPANY_ID))
                .thenReturn(Optional.of(stored));
        when(catalogServiceRepository.findById(SERVICE_ID)).thenReturn(Optional.of(catalogService()));

        ServiceAssignmentResponse response = serviceAssignmentService.updateService(CLIENT_ID, COMPANY_ID,
                stored.getId(), ServiceAssignmentUpdateRequest.builder()
                        .deliveryDate(LocalDate.of(2026, 11, 20))
                        .discountPercent(new BigDecimal("12"))
                        .comment("  Delivered late  ")
                        .build());

        assertThat(stored.getDeliveryDate()).isEqualTo(LocalDate.of(2026, 11, 20));
        assertThat(stored.getDiscountPercent()).isEqualTo(new BigDecimal("12.00"));
        assertThat(stored.getComment()).isEqualTo("Delivered late");
        assertThat(stored.getCatalogServiceId()).isEqualTo(SERVICE_ID);
        // Delivered after the 7th of November, so next due on the 7th of December.
        assertThat(stored.getNextDueDate()).isEqualTo(LocalDate.of(2026, 12, 7));
        verify(serviceAssignmentRepository).flush();
        assertThat(response.getServiceName()).isEqualTo("GST Monthly Return Filing");
        assertThat(response.getDeliveryDate()).isEqualTo(LocalDate.of(2026, 11, 20));
        assertThat(response.getNextDueDate()).isEqualTo(LocalDate.of(2026, 12, 7));
        verify(catalogServiceRepository, never()).findByIdForUpdate(any());
        verify(catalogServiceRepository, never()).incrementUsedByCompanies(any());
        verify(catalogServiceRepository, never()).decrementUsedByCompanies(any());
    }

    @Test
    @DisplayName("on update, an omitted discount resets to the company's and an omitted comment is cleared")
    void updateResetsOmittedFields() {
        stubCompany();
        ServiceAssignment stored = assignment(DELIVERY_DATE);
        stored.setDiscountPercent(new BigDecimal("20.00"));
        stored.setComment("Old note");
        when(serviceAssignmentRepository.findByIdAndCompanyId(stored.getId(), COMPANY_ID))
                .thenReturn(Optional.of(stored));
        when(catalogServiceRepository.findById(SERVICE_ID)).thenReturn(Optional.of(catalogService()));

        serviceAssignmentService.updateService(CLIENT_ID, COMPANY_ID, stored.getId(),
                ServiceAssignmentUpdateRequest.builder().deliveryDate(DELIVERY_DATE).build());

        assertThat(stored.getDiscountPercent()).isEqualTo(new BigDecimal("7.50"));
        assertThat(stored.getComment()).isNull();
    }

    @Test
    @DisplayName("on update, takes the service type and due date from the catalog's current schedule")
    void updateFollowsCurrentCatalogSchedule() {
        stubCompany();
        ServiceAssignment stored = assignment(DELIVERY_DATE);
        stored.setServiceType(BillingFrequency.MONTHLY);
        stored.setNextDueDate(LocalDate.of(2026, 11, 7));
        when(serviceAssignmentRepository.findByIdAndCompanyId(stored.getId(), COMPANY_ID))
                .thenReturn(Optional.of(stored));
        // The catalog service has since moved to quarterly billing, in the first month, on the 10th.
        CatalogService quarterly = catalogService();
        quarterly.setBillingFrequency(BillingFrequency.QUARTERLY);
        quarterly.setInvoiceDayOfMonth(10);
        quarterly.setInvoiceMonthOfQuarter(QuarterInvoiceMonth.FIRST_MONTH);
        when(catalogServiceRepository.findById(SERVICE_ID)).thenReturn(Optional.of(quarterly));

        ServiceAssignmentResponse response = serviceAssignmentService.updateService(CLIENT_ID, COMPANY_ID,
                stored.getId(), ServiceAssignmentUpdateRequest.builder().deliveryDate(DELIVERY_DATE).build());

        assertThat(stored.getServiceType()).isEqualTo(BillingFrequency.QUARTERLY);
        assertThat(stored.getNextDueDate()).isEqualTo(LocalDate.of(2027, 1, 10));
        assertThat(response.getServiceType()).isEqualTo(BillingFrequency.QUARTERLY);
    }

    @Test
    @DisplayName("raises not found when updating an assignment the company does not hold")
    void updateFailsForUnknownAssignment() {
        stubCompany();
        UUID missing = UUID.randomUUID();
        when(serviceAssignmentRepository.findByIdAndCompanyId(missing, COMPANY_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> serviceAssignmentService.updateService(CLIENT_ID, COMPANY_ID, missing,
                ServiceAssignmentUpdateRequest.builder().deliveryDate(DELIVERY_DATE).build()))
                .isInstanceOf(ServiceAssignmentNotFoundException.class);

        verify(serviceAssignmentRepository, never()).flush();
    }

    @Test
    @DisplayName("removing the company's last assignment of a service removes it as a subscriber")
    void removeDecrementsWhenLastOfService() {
        stubCompany();
        ServiceAssignment stored = assignment(DELIVERY_DATE);
        when(serviceAssignmentRepository.findByIdAndCompanyId(stored.getId(), COMPANY_ID))
                .thenReturn(Optional.of(stored));
        when(catalogServiceRepository.findByIdForUpdate(SERVICE_ID)).thenReturn(Optional.of(catalogService()));
        when(serviceAssignmentRepository.existsByCompanyIdAndCatalogServiceId(COMPANY_ID, SERVICE_ID))
                .thenReturn(false);

        serviceAssignmentService.removeService(CLIENT_ID, COMPANY_ID, stored.getId());

        InOrder inOrder = inOrder(catalogServiceRepository, serviceAssignmentRepository);
        inOrder.verify(catalogServiceRepository).findByIdForUpdate(SERVICE_ID);
        inOrder.verify(serviceAssignmentRepository).delete(stored);
        inOrder.verify(serviceAssignmentRepository).flush();
        inOrder.verify(serviceAssignmentRepository).existsByCompanyIdAndCatalogServiceId(COMPANY_ID, SERVICE_ID);
        inOrder.verify(catalogServiceRepository).decrementUsedByCompanies(SERVICE_ID);
    }

    @Test
    @DisplayName("removing one of several assignments of a service keeps the company as a subscriber")
    void removeKeepsCountWhenOthersRemain() {
        stubCompany();
        ServiceAssignment stored = assignment(DELIVERY_DATE);
        when(serviceAssignmentRepository.findByIdAndCompanyId(stored.getId(), COMPANY_ID))
                .thenReturn(Optional.of(stored));
        when(catalogServiceRepository.findByIdForUpdate(SERVICE_ID)).thenReturn(Optional.of(catalogService()));
        when(serviceAssignmentRepository.existsByCompanyIdAndCatalogServiceId(COMPANY_ID, SERVICE_ID))
                .thenReturn(true);

        serviceAssignmentService.removeService(CLIENT_ID, COMPANY_ID, stored.getId());

        verify(serviceAssignmentRepository).delete(stored);
        verify(catalogServiceRepository, never()).decrementUsedByCompanies(any());
    }

    @Test
    @DisplayName("raises not found when removing an assignment the company does not hold")
    void removeFailsForUnknownAssignment() {
        stubCompany();
        UUID missing = UUID.randomUUID();
        when(serviceAssignmentRepository.findByIdAndCompanyId(missing, COMPANY_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> serviceAssignmentService.removeService(CLIENT_ID, COMPANY_ID, missing))
                .isInstanceOf(ServiceAssignmentNotFoundException.class);

        verify(serviceAssignmentRepository, never()).delete(any());
        verify(catalogServiceRepository, never()).decrementUsedByCompanies(any());
    }

    private ServiceAssignment assignment(LocalDate deliveryDate) {
        ServiceAssignment assignment = new ServiceAssignment();
        assignment.setId(UUID.randomUUID());
        assignment.setCompany(company);
        assignment.setCatalogServiceId(SERVICE_ID);
        assignment.setDeliveryDate(deliveryDate);
        assignment.setDiscountPercent(new BigDecimal("7.50"));
        return assignment;
    }

    /** Billed monthly on the 7th, so a delivery on 15 October 2026 is next due on 7 November. */
    private CatalogService catalogService() {
        CatalogService service = new CatalogService();
        service.setId(SERVICE_ID);
        service.setServiceName("GST Monthly Return Filing");
        service.setStandardFee(new BigDecimal("5000.00"));
        service.setBillingFrequency(BillingFrequency.MONTHLY);
        service.setInvoiceDayOfMonth(7);
        return service;
    }

    private void stubCompany() {
        when(clientRepository.existsById(CLIENT_ID)).thenReturn(true);
        when(clientCompanyRepository.findByIdAndClientId(COMPANY_ID, CLIENT_ID)).thenReturn(Optional.of(company));
    }

    private ServiceAssignmentRequest.ServiceAssignmentRequestBuilder request() {
        return ServiceAssignmentRequest.builder()
                .serviceId(SERVICE_ID)
                .deliveryDate(DELIVERY_DATE);
    }

    private void stubLookups() {
        stubCompany();
        when(catalogServiceRepository.findByIdForUpdate(SERVICE_ID)).thenReturn(Optional.of(catalogService()));
    }

    private void stubSave() {
        when(serviceAssignmentRepository.save(any(ServiceAssignment.class))).thenAnswer(invocation -> {
            ServiceAssignment toSave = invocation.getArgument(0);
            toSave.setId(UUID.randomUUID());
            return toSave;
        });
    }
}
