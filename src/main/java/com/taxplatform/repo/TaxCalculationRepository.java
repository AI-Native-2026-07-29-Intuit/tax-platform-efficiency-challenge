package com.taxplatform.repo;

import com.taxplatform.domain.TaxCalculation;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaxCalculationRepository extends JpaRepository<TaxCalculation, Long> {
}
