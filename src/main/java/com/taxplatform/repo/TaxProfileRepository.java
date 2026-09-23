package com.taxplatform.repo;

import com.taxplatform.domain.TaxProfile;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaxProfileRepository extends JpaRepository<TaxProfile, Long> {

    TaxProfile findByCustomerId(Long customerId);
}
