package com.taxplatform.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/** Persisted history of every tax calculation performed. */
@Entity
@Table(name = "tax_calculations")
@Getter
@Setter
public class TaxCalculation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(nullable = false)
    private BigDecimal subtotal;

    @Column(name = "tax_rate", nullable = false)
    private BigDecimal taxRate;

    @Column(nullable = false)
    private BigDecimal tax;

    @Column(nullable = false)
    private BigDecimal total;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
