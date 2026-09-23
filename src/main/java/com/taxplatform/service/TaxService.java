package com.taxplatform.service;

import com.taxplatform.api.dto.TaxRequest;
import com.taxplatform.api.dto.TaxResponse;
import com.taxplatform.api.dto.TaxRuleUpdateRequest;
import com.taxplatform.domain.TaxProfile;
import com.taxplatform.domain.TaxRule;
import com.taxplatform.domain.Transaction;
import com.taxplatform.repo.TaxProfileRepository;
import com.taxplatform.repo.TaxRuleRepository;
import com.taxplatform.repo.TransactionRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Core tax calculation service — SOLUTION.
 *
 * <p>Applies the fixes for the five hidden problems (plus bonuses):
 * <ul>
 *   <li>P1 composite index — see db/schema.sql</li>
 *   <li>P2 batch fetch instead of N+1 — {@link #recalculateBatch}</li>
 *   <li>P3 cache static rules — {@link TaxRuleCache}</li>
 *   <li>P4 short, lock-free write + external call outside the txn — {@link SummaryWriter}</li>
 *   <li>P5 right-sized pool — application.yml / .env</li>
 *   <li>Bonus: dropped the redundant per-request reference-data validation
 *       lookups (state/city/product/jurisdiction/customer).</li>
 * </ul>
 */
@Service
public class TaxService {

    private final TaxRuleRepository taxRuleRepository;
    private final TaxProfileRepository taxProfileRepository;
    private final TransactionRepository transactionRepository;
    private final ExternalClassificationService externalClassificationService;
    private final TaxRuleCache taxRuleCache;
    private final SummaryWriter summaryWriter;

    private final int defaultBatchLimit;

    public TaxService(
            TaxRuleRepository taxRuleRepository,
            TaxProfileRepository taxProfileRepository,
            TransactionRepository transactionRepository,
            ExternalClassificationService externalClassificationService,
            TaxRuleCache taxRuleCache,
            SummaryWriter summaryWriter,
            @Value("${app.batch.default-limit:100}") int defaultBatchLimit) {
        this.taxRuleRepository = taxRuleRepository;
        this.taxProfileRepository = taxProfileRepository;
        this.transactionRepository = transactionRepository;
        this.externalClassificationService = externalClassificationService;
        this.taxRuleCache = taxRuleCache;
        this.summaryWriter = summaryWriter;
        this.defaultBatchLimit = defaultBatchLimit;
    }

    /**
     * Single tax calculation (hot path).
     *
     * <p>No wrapping @Transactional over the whole method: the cached rule
     * lookup and the single profile read run without holding a write
     * transaction, the external call runs OUTSIDE any DB transaction (so it
     * never pins a pooled connection), and the summary/history write is a short
     * atomic transaction.
     */
    public TaxResponse calculate(TaxRequest request) {
        TaxRule rule = taxRuleCache.currentRule(request.state(), request.city(), request.productType());
        if (rule == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "No tax rule for " + request.state() + "/" + request.city() + "/" + request.productType());
        }

        TaxProfile profile = taxProfileRepository.findByCustomerId(request.customerId());

        TaxResponse response = computeTax(request.amount(), rule.getRate(), profile);

        // External dependency is called with no DB transaction/connection held.
        externalClassificationService.classify(request.customerId());

        // Short, lock-free write (atomic UPDATE + history INSERT).
        summaryWriter.persist(request.customerId(), response);

        return response;
    }

    /**
     * Batch recalculation over recent transactions.
     *
     * <p>FIX (Problem 2): ~2 queries instead of {@code 1 + 3N}. Transactions are
     * loaded once, profiles are batch-fetched with a single {@code IN} query,
     * and rules come from the cache (deduplicated by key).
     */
    @Transactional(readOnly = true)
    public List<TaxResponse> recalculateBatch(Integer limit) {
        int effectiveLimit = (limit == null || limit <= 0) ? defaultBatchLimit : limit;

        List<Transaction> transactions =
                transactionRepository.findRecent(PageRequest.of(0, effectiveLimit));

        Set<Long> customerIds = transactions.stream()
                .map(Transaction::getCustomerId)
                .collect(Collectors.toSet());

        Map<Long, TaxProfile> profiles = taxProfileRepository.findByCustomerIdIn(customerIds)
                .stream()
                .collect(Collectors.toMap(TaxProfile::getCustomerId, Function.identity(), (a, b) -> a));

        List<TaxResponse> results = new ArrayList<>(transactions.size());
        for (Transaction transaction : transactions) {
            TaxRule rule = taxRuleCache.currentRule(
                    transaction.getState(), transaction.getCity(), transaction.getProductType());
            if (rule == null) {
                continue;
            }
            results.add(computeTax(transaction.getAmount(), rule.getRate(),
                    profiles.get(transaction.getCustomerId())));
        }
        return results;
    }

    /** Read-only current-rule lookup (bypasses cache), handy for verification. */
    @Transactional(readOnly = true)
    public TaxRule currentRule(String state, String city, String productType) {
        return taxRuleRepository
                .findFirstByStateAndCityAndProductTypeAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
                        state, city, productType, Instant.now());
    }

    /**
     * Publishes a new tax-rule version (rate change) and evicts the cached key
     * so the next request reloads the fresh rate (correctness preserved).
     */
    @Transactional
    public void updateRule(TaxRuleUpdateRequest request) {
        TaxRule current = currentRule(request.state(), request.city(), request.productType());
        if (current == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No existing rule to update");
        }
        TaxRule next = new TaxRule();
        next.setId(System.nanoTime());
        next.setState(request.state());
        next.setCity(request.city());
        next.setProductType(request.productType());
        next.setRate(request.rate());
        next.setJurisdictionId(current.getJurisdictionId());
        next.setEffectiveFrom(Instant.now());
        taxRuleRepository.save(next);

        taxRuleCache.evict(request.state(), request.city(), request.productType());
    }

    private TaxResponse computeTax(BigDecimal amount, BigDecimal ruleRate, TaxProfile profile) {
        BigDecimal subtotal = amount.setScale(2, RoundingMode.HALF_UP);

        BigDecimal effectiveRate = ruleRate;
        if (profile != null) {
            if (profile.isExempt()) {
                effectiveRate = BigDecimal.ZERO;
            } else if (profile.getExemptionRate() != null
                    && profile.getExemptionRate().signum() > 0) {
                effectiveRate = ruleRate.multiply(
                        BigDecimal.ONE.subtract(profile.getExemptionRate()));
            }
        }

        BigDecimal tax = subtotal.multiply(effectiveRate).setScale(2, RoundingMode.HALF_UP);
        BigDecimal total = subtotal.add(tax);
        return new TaxResponse(subtotal, effectiveRate, tax, total);
    }
}
