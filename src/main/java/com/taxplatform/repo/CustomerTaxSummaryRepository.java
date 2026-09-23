package com.taxplatform.repo;

import com.taxplatform.domain.CustomerTaxSummary;
import java.math.BigDecimal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CustomerTaxSummaryRepository extends JpaRepository<CustomerTaxSummary, Long> {

    /**
     * FIX (Problem 4): atomic, lock-free increment of a customer's running
     * totals. Replaces the pessimistic {@code SELECT ... FOR UPDATE} + read /
     * modify / save cycle with a single UPDATE statement.
     */
    @Modifying
    @Query("update CustomerTaxSummary s set s.totalAmount = s.totalAmount + :amount, "
            + "s.totalTax = s.totalTax + :tax, s.calculationCount = s.calculationCount + 1, "
            + "s.updatedAt = CURRENT_TIMESTAMP where s.customerId = :customerId")
    int applyDelta(@Param("customerId") Long customerId,
                   @Param("amount") BigDecimal amount,
                   @Param("tax") BigDecimal tax);
}
