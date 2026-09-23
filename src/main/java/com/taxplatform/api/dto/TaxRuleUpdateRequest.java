package com.taxplatform.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * Updates the current rate for a (state, city, productType) key by inserting a
 * new effective-from-now rule version.
 */
public record TaxRuleUpdateRequest(
        @NotBlank String state,
        @NotBlank String city,
        @NotBlank String productType,
        @NotNull BigDecimal rate) {
}
