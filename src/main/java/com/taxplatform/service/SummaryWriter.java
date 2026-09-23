package com.taxplatform.service;

import com.taxplatform.api.dto.TaxResponse;
import com.taxplatform.domain.TaxCalculation;
import com.taxplatform.repo.CustomerTaxSummaryRepository;
import com.taxplatform.repo.TaxCalculationRepository;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * FIX (Problem 4): the running-summary update is now a short, contention-free
 * transaction. Instead of {@code SELECT ... FOR UPDATE} (pessimistic lock) held
 * across an external call, it issues a single atomic UPDATE and one INSERT, so
 * the DB connection is held only for two fast statements. The external
 * classification call happens OUTSIDE this transaction (see TaxService).
 */
@Service
public class SummaryWriter {

    private final CustomerTaxSummaryRepository summaryRepository;
    private final TaxCalculationRepository taxCalculationRepository;

    public SummaryWriter(CustomerTaxSummaryRepository summaryRepository,
                         TaxCalculationRepository taxCalculationRepository) {
        this.summaryRepository = summaryRepository;
        this.taxCalculationRepository = taxCalculationRepository;
    }

    @Transactional
    public void persist(Long customerId, TaxResponse response) {
        summaryRepository.applyDelta(customerId, response.subtotal(), response.tax());

        TaxCalculation history = new TaxCalculation();
        history.setCustomerId(customerId);
        history.setSubtotal(response.subtotal());
        history.setTaxRate(response.taxRate());
        history.setTax(response.tax());
        history.setTotal(response.total());
        history.setCreatedAt(Instant.now());
        taxCalculationRepository.save(history);
    }
}
