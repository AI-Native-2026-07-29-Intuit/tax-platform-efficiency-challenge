package com.taxplatform.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "states")
@Getter
@Setter
public class State {

    @Id
    private Integer ordinal;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String name;
}
