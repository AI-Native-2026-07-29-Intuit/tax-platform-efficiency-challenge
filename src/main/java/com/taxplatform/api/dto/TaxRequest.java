package com.taxplatform.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record TaxRequest(
        @NotNull Long customerId,
        @NotNull @Positive BigDecimal amount,
        @NotBlank String state,
        @NotBlank String city,
        @NotBlank String productType) {
}
