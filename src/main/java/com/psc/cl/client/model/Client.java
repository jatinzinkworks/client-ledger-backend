package com.psc.cl.client.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A person the firm works for, together with the companies they own or act for.
 *
 * <p>The assigned manager is held by identifier rather than as an entity relation, so the client
 * module does not load or depend on the Manager Catalog's mapping. A foreign key keeps it valid.
 */
@Entity
@Table(name = "clients")
@Getter
@Setter
@NoArgsConstructor
public class Client {

    /** What every client id starts with. */
    public static final String CLIENT_ID_PREFIX = "PSC-";

    /** How many digits follow the prefix of a client id. */
    public static final int CLIENT_ID_DIGITS = 5;

    /** The shape of a client id: the prefix, then exactly {@link #CLIENT_ID_DIGITS} digits, as PSC-00001. */
    public static final String CLIENT_ID_PATTERN = "^PSC-[0-9]{5}$";

    /** Length of every client id. */
    public static final int CLIENT_ID_LENGTH = 9;

    /** Longest first or last name accepted, matching the column width. */
    public static final int MAX_NAME_LENGTH = 100;

    /** Longest email address accepted, matching the column width. */
    public static final int MAX_EMAIL_LENGTH = 254;

    /** Longest phone number accepted, matching the column width. */
    public static final int MAX_PHONE_LENGTH = 20;

    /** Discount applied when the caller does not supply one. */
    public static final BigDecimal DEFAULT_DISCOUNT_PERCENT = new BigDecimal("0.00");

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /**
     * The firm's own identifier for the client, as PSC-00001, unique. Given when the client is added and
     * never changed afterwards.
     */
    @Column(name = "client_id", nullable = false, updatable = false, length = CLIENT_ID_LENGTH)
    private String clientId;

    @Column(name = "first_name", nullable = false, length = MAX_NAME_LENGTH)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = MAX_NAME_LENGTH)
    private String lastName;

    @Column(name = "phone", nullable = false, length = MAX_PHONE_LENGTH)
    private String phone;

    @Column(name = "email", nullable = false, length = MAX_EMAIL_LENGTH)
    private String email;

    @Column(name = "manager_id", nullable = false)
    private UUID managerId;

    /** Date of birth; optional. */
    @Column(name = "birthday")
    private LocalDate birthday;

    /** Also applies to each company that does not carry a discount of its own. */
    @Column(name = "discount_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal discountPercent;

    /** Owned by the client: saved, replaced and removed together with it. */
    @OneToMany(mappedBy = "client", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ClientCompany> companies = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Attaches a company to this client, setting both sides of the relation.
     *
     * @param company the company to attach
     */
    public void addCompany(ClientCompany company) {
        company.setClient(this);
        companies.add(company);
    }

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
