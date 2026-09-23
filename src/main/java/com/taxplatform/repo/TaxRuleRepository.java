package com.taxplatform.repo;

import com.taxplatform.domain.TaxRule;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaxRuleRepository extends JpaRepository<TaxRule, Long> {

    /**
     * Current tax rule for a (state, city, productType) key: the newest rule
     * that is already effective.
     */
    TaxRule findFirstByStateAndCityAndProductTypeAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
            String state, String city, String productType, Instant asOf);
}
