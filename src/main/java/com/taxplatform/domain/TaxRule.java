package com.taxplatform.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/**
 * A versioned tax rate for (state, city, product_type). Multiple rows exist
 * for the same key with different {@code effectiveFrom} dates; the "current"
 * rule is the newest one that is already effective.
 */
@Entity
@Table(name = "tax_rules")
@Getter
@Setter
public class TaxRule {

    @Id
    private Long id;

    @Column(nullable = false)
    private String state;

    @Column(nullable = false)
    private String city;

    @Column(name = "product_type", nullable = false)
    private String productType;

    @Column(nullable = false)
    private BigDecimal rate;

    @Column(name = "jurisdiction_id")
    private Long jurisdictionId;

    @Column(name = "effective_from", nullable = false)
    private Instant effectiveFrom;
}
