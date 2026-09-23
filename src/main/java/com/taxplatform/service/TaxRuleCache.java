package com.taxplatform.service;

import com.taxplatform.domain.TaxRule;
import com.taxplatform.repo.TaxRuleRepository;
import java.time.Instant;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/**
 * FIX (Problem 3): cache the rarely-changing current tax rule so we stop
 * hitting the DB for (nearly) identical lookups on every request.
 *
 * <p>Lives in its own bean so Spring's @Cacheable proxy actually applies
 * (self-invocation inside {@code TaxService} would bypass the proxy).
 * Invalidation is key-scoped via {@link #evict} on a rate change, which keeps
 * tax results correct.
 */
@Service
public class TaxRuleCache {

    private final TaxRuleRepository taxRuleRepository;

    public TaxRuleCache(TaxRuleRepository taxRuleRepository) {
        this.taxRuleRepository = taxRuleRepository;
    }

    @Cacheable(value = "taxRules", key = "#state + ':' + #city + ':' + #productType")
    public TaxRule currentRule(String state, String city, String productType) {
        return taxRuleRepository
                .findFirstByStateAndCityAndProductTypeAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
                        state, city, productType, Instant.now());
    }

    /** Invalidate a single key after its rate changes (performance vs correctness). */
    @CacheEvict(value = "taxRules", key = "#state + ':' + #city + ':' + #productType")
    public void evict(String state, String city, String productType) {
        // eviction only
    }
}
