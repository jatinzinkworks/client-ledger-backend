package com.psc.cl.globalsettings.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

import java.time.Instant;
import java.util.UUID;

/**
 * Tenant-wide payment terms held under Global Settings.
 *
 * <p>These drive when payment falls due for completed work and when an unpaid invoice is
 * flagged as overdue, plus the optional reminder cadence.
 */
@Entity
@Table(name = "payment_terms")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentTerms {

    /** Days after which payment falls due when no explicit value is supplied. */
    public static final int DEFAULT_PAYMENT_DUE_AFTER_DAYS = 15;

    /** Days past the due date after which an unpaid invoice is marked overdue, when unset. */
    public static final int DEFAULT_MARK_OVERDUE_AFTER_DAYS = 60;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "payment_due_after_days", nullable = false)
    private Integer paymentDueAfterDays;

    @Column(name = "mark_overdue_after_days", nullable = false)
    private Integer markOverdueAfterDays;

    @Column(name = "payment_reminder_enabled", nullable = false)
    private Boolean paymentReminderEnabled;

    /** Days before the due date on which a reminder is sent. Null when reminders are disabled. */
    @Column(name = "payment_reminder_days")
    private Integer paymentReminderDays;

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
