package com.psc.cl.servicecatalog.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.Month;
import java.util.UUID;

/**
 * A billable service offered by the firm, held in the Service Catalog.
 *
 * <p>The invoice schedule columns are flattened here rather than modelled as a separate table:
 * which of them carry a value is decided entirely by {@link #billingFrequency}, and a database
 * check constraint holds that relationship.
 */
@Entity
@Table(name = "catalog_services")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CatalogService {

    /** GST rate applied when the caller does not supply one. */
    public static final BigDecimal DEFAULT_GST_RATE_PERCENT = new BigDecimal("18.00");

    /** Longest service name accepted, matching the column width. */
    public static final int MAX_SERVICE_NAME_LENGTH = 150;

    /** Longest description accepted, matching the column width. */
    public static final int MAX_DESCRIPTION_LENGTH = 1000;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "service_name", nullable = false, length = MAX_SERVICE_NAME_LENGTH)
    private String serviceName;

    @Column(name = "description", length = MAX_DESCRIPTION_LENGTH)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 20)
    private ServiceCategory category;

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_frequency", nullable = false, length = 20)
    private BillingFrequency billingFrequency;

    @Column(name = "standard_fee", nullable = false, precision = 12, scale = 2)
    private BigDecimal standardFee;

    @Column(name = "gst_rate_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal gstRatePercent;

    /** Day the invoice is raised. Null only for {@link BillingFrequency#ONE_OFF}. */
    @Column(name = "invoice_day_of_month")
    private Integer invoiceDayOfMonth;

    /** Set only for {@link BillingFrequency#QUARTERLY}. */
    @Enumerated(EnumType.STRING)
    @Column(name = "invoice_month_of_quarter", length = 20)
    private QuarterInvoiceMonth invoiceMonthOfQuarter;

    /** Set only for {@link BillingFrequency#ANNUAL}. */
    @Enumerated(EnumType.STRING)
    @Column(name = "invoice_month", length = 20)
    private Month invoiceMonth;

    /**
     * How many companies currently subscribe to this service. Maintained by the assignment flow,
     * never accepted from an API caller.
     */
    @Column(name = "used_by_companies", nullable = false)
    private Integer usedByCompanies;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.usedByCompanies == null) {
            this.usedByCompanies = 0;
        }
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
