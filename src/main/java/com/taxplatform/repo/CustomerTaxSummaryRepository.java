package com.taxplatform.repo;

import com.taxplatform.domain.CustomerTaxSummary;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CustomerTaxSummaryRepository extends JpaRepository<CustomerTaxSummary, Long> {

    /** Loads a customer's running summary row for update. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from CustomerTaxSummary s where s.customerId = :customerId")
    Optional<CustomerTaxSummary> findByCustomerIdForUpdate(@Param("customerId") Long customerId);
}
