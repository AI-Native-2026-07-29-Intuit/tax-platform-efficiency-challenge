package com.taxplatform.repo;

import com.taxplatform.domain.TaxProfile;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaxProfileRepository extends JpaRepository<TaxProfile, Long> {

    TaxProfile findByCustomerId(Long customerId);

    /** FIX (Problem 2): batch-fetch profiles for many customers in one query. */
    List<TaxProfile> findByCustomerIdIn(Collection<Long> customerIds);
}
