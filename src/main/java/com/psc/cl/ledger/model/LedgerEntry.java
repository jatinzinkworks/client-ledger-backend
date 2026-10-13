package com.psc.cl.ledger.model;

import com.psc.cl.servicecatalog.model.BillingFrequency;
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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One line of a company's ledger: either an amount owed for a service falling due on one date, or
 * a payment received from the company. {@link #entryType} tells them apart.
 *
 * <p>A {@link LedgerEntryType#SERVICE_CHARGE} fills in every service figure, each a snapshot taken
 * when the entry is posted, so later changes to the catalog service or the assignment leave it as
 * it was. Only the rates and the amounts worked out from them can be corrected afterwards; what the
 * entry bills, and when, stays fixed. Its payment fields are null. The service layer holds these
 * rules; the due date and financial year columns are updatable only so a payment can be re-dated.
 *
 * <p>A {@link LedgerEntryType#PAYMENT} leaves every service figure null. Its {@link #totalAmount}
 * is the amount received as a negative number, so adding up a ledger's totals gives the balance
 * still owed, and its {@link #dueDate} is the date the payment was received. Every detail of a
 * payment can be updated, except when it was recorded. A database check constraint holds each
 * shape.
 *
 * <p>Clients, companies and services are held by identifier rather than as entity relations, so the
 * ledger does not depend on their mappings; foreign keys keep them valid.
 */
@Entity
@Table(name = "ledger_entries")
@Getter
@Setter
@NoArgsConstructor
public class LedgerEntry {

    /** Longest payment reference accepted, matching the column width. */
    public static final int MAX_REFERENCE_LENGTH = 100;

    /** Longest payment comment accepted, matching the column width. */
    public static final int MAX_COMMENT_LENGTH = 1000;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "client_id", nullable = false, updatable = false)
    private UUID clientId;

    @Column(name = "company_id", nullable = false, updatable = false)
    private UUID companyId;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, updatable = false, length = 20)
    private LedgerEntryType entryType;

    /** The service as assigned to the company. Null for a payment. */
    @Column(name = "company_service_id", updatable = false)
    private UUID companyServiceId;

    /** The service in the Service Catalog. Null for a payment. */
    @Column(name = "catalog_service_id", updatable = false)
    private UUID catalogServiceId;

    @Column(name = "service_name", updatable = false, length = 150)
    private String serviceName;

    @Enumerated(EnumType.STRING)
    @Column(name = "service_type", updatable = false, length = 20)
    private BillingFrequency serviceType;

    /** For a service charge, the date the service fell due; for a payment, the date it was received. */
    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    /** Indian financial year (1 April to 31 March) the due date falls in, such as {@code 2026-27}. */
    @Column(name = "financial_year", nullable = false, length = 7)
    private String financialYear;

    /** The date the entry was posted to the ledger. */
    @Column(name = "entry_date", nullable = false, updatable = false)
    private LocalDate entryDate;

    /** Standard fee of the service, before discount and excluding GST. Null for a payment. */
    @Column(name = "service_fee", precision = 12, scale = 2)
    private BigDecimal serviceFee;

    @Column(name = "discount_percent", precision = 5, scale = 2)
    private BigDecimal discountPercent;

    @Column(name = "discount_amount", precision = 12, scale = 2)
    private BigDecimal discountAmount;

    /** The fee after discount, on which GST is charged. */
    @Column(name = "taxable_amount", precision = 12, scale = 2)
    private BigDecimal taxableAmount;

    @Column(name = "gst_rate_percent", precision = 5, scale = 2)
    private BigDecimal gstRatePercent;

    @Column(name = "gst_amount", precision = 14, scale = 2)
    private BigDecimal gstAmount;

    /**
     * For a service charge, the taxable amount plus GST: what the company owes for it. For a
     * payment, the amount received as a negative number.
     */
    @Column(name = "total_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalAmount;

    /** How a payment was received. Null for a service charge. */
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", length = 20)
    private PaymentMethod paymentMethod;

    /** The payer's reference for a payment, such as a UTR or cheque number. Null for a service charge. */
    @Column(name = "payment_reference", length = MAX_REFERENCE_LENGTH)
    private String paymentReference;

    /** Free-text note about a payment. Null for a service charge. */
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
