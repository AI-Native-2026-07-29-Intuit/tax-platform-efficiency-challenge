package com.taxplatform.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "jurisdictions")
@Getter
@Setter
public class Jurisdiction {

    @Id
    private Long id;

    @Column(name = "state_code", nullable = false)
    private String stateCode;

    @Column(nullable = false)
    private String city;

    @Column(nullable = false)
    private String name;
}
