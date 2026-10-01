package com.psc.cl.managercatalog.model;

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

import java.time.Instant;
import java.util.UUID;

/**
 * A member of staff who can be assigned to client work, held in the Manager Catalog.
 */
@Entity
@Table(name = "managers")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Manager {

    /** Longest first or last name accepted, matching the column width. */
    public static final int MAX_NAME_LENGTH = 100;

    /** Longest email address accepted, matching the column width. */
    public static final int MAX_EMAIL_LENGTH = 254;

    /** Longest mobile number accepted, matching the column width. */
    public static final int MAX_MOBILE_NUMBER_LENGTH = 20;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "first_name", nullable = false, length = MAX_NAME_LENGTH)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = MAX_NAME_LENGTH)
    private String lastName;

    /** Unique across the catalog, ignoring case. */
    @Column(name = "email", nullable = false, length = MAX_EMAIL_LENGTH)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private ManagerRole role;

    @Column(name = "mobile_number", nullable = false, length = MAX_MOBILE_NUMBER_LENGTH)
    private String mobileNumber;

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
