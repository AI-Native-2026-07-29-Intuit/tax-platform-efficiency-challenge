package com.taxplatform.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/** Running per-customer totals updated on every calculation. */
@Entity
@Table(name = "customer_tax_summary")
@Getter
@Setter
public class CustomerTaxSummary {

    @Id
    private Long id;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "total_amount", nullable = false)
    private BigDecimal totalAmount;

    @Column(name = "total_tax", nullable = false)
    private BigDecimal totalTax;

    @Column(name = "calculation_count", nullable = false)
    private long calculationCount;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public void apply(BigDecimal amount, BigDecimal tax) {
        this.totalAmount = this.totalAmount.add(amount);
        this.totalTax = this.totalTax.add(tax);
        this.calculationCount += 1;
        this.updatedAt = Instant.now();
    }
}
