package com.psc.cl.client.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * A company owned by, or acted for on behalf of, a {@link Client}.
 */
@Entity
@Table(name = "client_companies")
@Getter
@Setter
@NoArgsConstructor
public class ClientCompany {

    /** Longest company name accepted, matching the column width. */
    public static final int MAX_COMPANY_NAME_LENGTH = 200;

    /** Longest address accepted, matching the column width. */
    public static final int MAX_ADDRESS_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "client_id", nullable = false, updatable = false)
    private Client client;

    @Column(name = "company_name", nullable = false, length = MAX_COMPANY_NAME_LENGTH)
    private String companyName;

    @Enumerated(EnumType.STRING)
    @Column(name = "company_type", nullable = false, length = 20)
    private CompanyType companyType;

    @Column(name = "address", nullable = false, length = MAX_ADDRESS_LENGTH)
    private String address;

    /** Copied from the client when the company is not given a discount of its own. */
    @Column(name = "discount_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal discountPercent;
}
