package com.psc.cl.client.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A service from the Service Catalog delivered to one company of a client.
 *
 * <p>The catalog service is held by identifier rather than as an entity relation, so the client
 * module does not depend on the Service Catalog's mapping. A foreign key keeps it valid.
 */
@Entity
@Table(name = "company_services")
@Getter
@Setter
@NoArgsConstructor
public class ServiceAssignment {

    /** Longest comment accepted, matching the column width. */
    public static final int MAX_COMMENT_LENGTH = 1000;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false, updatable = false)
    private ClientCompany company;

    @Column(name = "catalog_service_id", nullable = false, updatable = false)
    private UUID catalogServiceId;

    @Column(name = "delivery_date", nullable = false)
    private LocalDate deliveryDate;

    /** Copied from the company when the service is not given a discount of its own. */
    @Column(name = "discount_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal discountPercent;

    @Column(name = "comment", length = MAX_COMMENT_LENGTH)
    private String comment;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
