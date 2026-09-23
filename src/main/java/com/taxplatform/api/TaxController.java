package com.taxplatform.api;

import com.taxplatform.api.dto.TaxRequest;
import com.taxplatform.api.dto.TaxResponse;
import com.taxplatform.api.dto.TaxRuleUpdateRequest;
import com.taxplatform.domain.TaxRule;
import com.taxplatform.service.TaxService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tax")
public class TaxController {

    private final TaxService taxService;

    public TaxController(TaxService taxService) {
        this.taxService = taxService;
    }

    /** Primary hot path exercised by the benchmark. */
    @PostMapping("/calculate")
    public TaxResponse calculate(@Valid @RequestBody TaxRequest request) {
        return taxService.calculate(request);
    }

    /** Batch recalculation over recent transactions. */
    @PostMapping("/batch")
    public List<TaxResponse> batch(@RequestParam(value = "limit", required = false) Integer limit) {
        return taxService.recalculateBatch(limit);
    }

    /** Inspect the current rule for a key (useful for correctness checks). */
    @GetMapping("/rule")
    public Map<String, Object> rule(
            @RequestParam String state,
            @RequestParam String city,
            @RequestParam String productType) {
        TaxRule rule = taxService.currentRule(state, city, productType);
        if (rule == null) {
            return Map.of("found", false);
        }
        return Map.of(
                "found", true,
                "state", rule.getState(),
                "city", rule.getCity(),
                "productType", rule.getProductType(),
                "rate", rule.getRate(),
                "effectiveFrom", rule.getEffectiveFrom());
    }

    /** Publish a new rate (rate change) for a key. */
    @PostMapping("/rules")
    public ResponseEntity<Void> updateRule(@Valid @RequestBody TaxRuleUpdateRequest request) {
        taxService.updateRule(request);
        return ResponseEntity.accepted().build();
    }
}
