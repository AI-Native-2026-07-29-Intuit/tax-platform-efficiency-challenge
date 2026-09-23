package com.taxplatform.api.dto;

import java.math.BigDecimal;

public record TaxResponse(
        BigDecimal subtotal,
        BigDecimal taxRate,
        BigDecimal tax,
        BigDecimal total) {
}
