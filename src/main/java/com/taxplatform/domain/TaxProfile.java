package com.taxplatform.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

/**
 * Per-customer tax profile. Drives whether a customer is tax exempt (and by
 * how much).
 */
@Entity
@Table(name = "tax_profiles")
@Getter
@Setter
public class TaxProfile {

    @Id
    private Long id;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(nullable = false)
    private boolean exempt;

    @Column(name = "exemption_rate", nullable = false)
    private BigDecimal exemptionRate;

    @Column(name = "tax_id_number")
    private String taxIdNumber;
}
