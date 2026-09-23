package com.taxplatform.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "transactions")
@Getter
@Setter
public class Transaction {

    @Id
    private Long id;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(nullable = false)
    private String state;

    @Column(nullable = false)
    private String city;

    @Column(name = "product_type", nullable = false)
    private String productType;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
