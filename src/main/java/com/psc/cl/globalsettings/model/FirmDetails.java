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
 * Identity of the firm that owns this ledger, held under Global Settings.
 *
 * <p>These values are printed on invoices and Excel exports, so they describe the firm as it
 * should appear to clients rather than any internal naming.
 */
@Entity
@Table(name = "firm_details")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FirmDetails {

    /** Longest firm name accepted, matching the column width. */
    public static final int MAX_FIRM_NAME_LENGTH = 200;

    /** A GSTIN is always exactly 15 characters. */
    public static final int GSTIN_LENGTH = 15;

    /** Longest firm registration number accepted, matching the column width. */
    public static final int MAX_REGISTRATION_NO_LENGTH = 50;

    /** Longest email address accepted, matching the column width. */
    public static final int MAX_EMAIL_LENGTH = 254;

    /** Longest phone number accepted, matching the column width. */
    public static final int MAX_PHONE_LENGTH = 30;

    /** Longest address accepted, matching the column width. */
    public static final int MAX_ADDRESS_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "firm_name", nullable = false, length = MAX_FIRM_NAME_LENGTH)
    private String firmName;

    @Column(name = "gstin", nullable = false, length = GSTIN_LENGTH)
    private String gstin;

    @Column(name = "firm_registration_no", length = MAX_REGISTRATION_NO_LENGTH)
    private String firmRegistrationNo;

    @Column(name = "email", length = MAX_EMAIL_LENGTH)
    private String email;

    @Column(name = "phone", length = MAX_PHONE_LENGTH)
    private String phone;

    @Column(name = "address", nullable = false, length = MAX_ADDRESS_LENGTH)
    private String address;

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
