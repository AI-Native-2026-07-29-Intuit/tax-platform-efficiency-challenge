package com.taxplatform.service;

import com.taxplatform.api.dto.TaxRequest;
import com.taxplatform.api.dto.TaxResponse;
import com.taxplatform.api.dto.TaxRuleUpdateRequest;
import com.taxplatform.domain.CustomerTaxSummary;
import com.taxplatform.domain.TaxCalculation;
import com.taxplatform.domain.TaxProfile;
import com.taxplatform.domain.TaxRule;
import com.taxplatform.domain.Transaction;
import com.taxplatform.repo.CityRepository;
import com.taxplatform.repo.CustomerRepository;
import com.taxplatform.repo.CustomerTaxSummaryRepository;
import com.taxplatform.repo.JurisdictionRepository;
import com.taxplatform.repo.ProductTaxCategoryRepository;
import com.taxplatform.repo.StateRepository;
import com.taxplatform.repo.TaxCalculationRepository;
import com.taxplatform.repo.TaxProfileRepository;
import com.taxplatform.repo.TaxRuleRepository;
import com.taxplatform.repo.TransactionRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

/** Core tax calculation service. */
@Service
public class TaxService {

    private final StateRepository stateRepository;
    private final CityRepository cityRepository;
    private final ProductTaxCategoryRepository productTaxCategoryRepository;
    private final JurisdictionRepository jurisdictionRepository;
    private final TaxRuleRepository taxRuleRepository;
    private final CustomerRepository customerRepository;
    private final TaxProfileRepository taxProfileRepository;
    private final CustomerTaxSummaryRepository customerTaxSummaryRepository;
    private final TaxCalculationRepository taxCalculationRepository;
    private final TransactionRepository transactionRepository;
    private final ExternalClassificationService externalClassificationService;

    private final int defaultBatchLimit;

    public TaxService(
            StateRepository stateRepository,
            CityRepository cityRepository,
            ProductTaxCategoryRepository productTaxCategoryRepository,
            JurisdictionRepository jurisdictionRepository,
            TaxRuleRepository taxRuleRepository,
            CustomerRepository customerRepository,
            TaxProfileRepository taxProfileRepository,
            CustomerTaxSummaryRepository customerTaxSummaryRepository,
            TaxCalculationRepository taxCalculationRepository,
            TransactionRepository transactionRepository,
            ExternalClassificationService externalClassificationService,
            @Value("${app.batch.default-limit:100}") int defaultBatchLimit) {
        this.stateRepository = stateRepository;
        this.cityRepository = cityRepository;
        this.productTaxCategoryRepository = productTaxCategoryRepository;
        this.jurisdictionRepository = jurisdictionRepository;
        this.taxRuleRepository = taxRuleRepository;
        this.customerRepository = customerRepository;
        this.taxProfileRepository = taxProfileRepository;
        this.customerTaxSummaryRepository = customerTaxSummaryRepository;
        this.taxCalculationRepository = taxCalculationRepository;
        this.transactionRepository = transactionRepository;
        this.externalClassificationService = externalClassificationService;
        this.defaultBatchLimit = defaultBatchLimit;
    }

    /** Single tax calculation. This is the hot path exercised by the benchmark. */
    @Transactional
    public TaxResponse calculate(TaxRequest request) {
        // Validate the request against reference data.
        if (stateRepository.findByCode(request.state()) == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown state: " + request.state());
        }
        cityRepository.findFirstByStateCodeAndName(request.state(), request.city());
        if (productTaxCategoryRepository.findByCode(request.productType()) == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown product type: " + request.productType());
        }
        jurisdictionRepository.findFirstByStateCodeAndCity(request.state(), request.city());

        // Current tax rule for this key.
        TaxRule rule = taxRuleRepository
                .findFirstByStateAndCityAndProductTypeAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
                        request.state(), request.city(), request.productType(), Instant.now());
        if (rule == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "No tax rule for " + request.state() + "/" + request.city() + "/" + request.productType());
        }

        // Customer + profile (drives exemption).
        customerRepository.findById(request.customerId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Unknown customer: " + request.customerId()));
        TaxProfile profile = taxProfileRepository.findByCustomerId(request.customerId());

        TaxResponse response = computeTax(request.amount(), rule.getRate(), profile);

        // Update the customer's running summary.
        CustomerTaxSummary summary = customerTaxSummaryRepository
                .findByCustomerIdForUpdate(request.customerId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No summary for customer: " + request.customerId()));

        externalClassificationService.classify(request.customerId());

        summary.apply(response.subtotal(), response.tax());
        customerTaxSummaryRepository.save(summary);

        // Persist calculation history.
        TaxCalculation history = new TaxCalculation();
        history.setCustomerId(request.customerId());
        history.setSubtotal(response.subtotal());
        history.setTaxRate(response.taxRate());
        history.setTax(response.tax());
        history.setTotal(response.total());
        history.setCreatedAt(Instant.now());
        taxCalculationRepository.save(history);

        return response;
    }

    /** Batch recalculation over recent transactions. */
    @Transactional(readOnly = true)
    public List<TaxResponse> recalculateBatch(Integer limit) {
        int effectiveLimit = (limit == null || limit <= 0) ? defaultBatchLimit : limit;

        List<Transaction> transactions =
                transactionRepository.findRecent(PageRequest.of(0, effectiveLimit));

        List<TaxResponse> results = new ArrayList<>(transactions.size());
        for (Transaction transaction : transactions) {
            customerRepository.findById(transaction.getCustomerId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                            "Unknown customer: " + transaction.getCustomerId()));
            TaxProfile profile = taxProfileRepository.findByCustomerId(transaction.getCustomerId());

            TaxRule rule = taxRuleRepository
                    .findFirstByStateAndCityAndProductTypeAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
                            transaction.getState(), transaction.getCity(),
                            transaction.getProductType(), Instant.now());
            if (rule == null) {
                continue;
            }
            results.add(computeTax(transaction.getAmount(), rule.getRate(), profile));
        }
        return results;
    }

    /** Read-only current-rule lookup, handy for correctness verification. */
    @Transactional(readOnly = true)
    public TaxRule currentRule(String state, String city, String productType) {
        return taxRuleRepository
                .findFirstByStateAndCityAndProductTypeAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
                        state, city, productType, Instant.now());
    }

    /** Publishes a new tax-rule version (rate change), effective immediately. */
    @Transactional
    public void updateRule(TaxRuleUpdateRequest request) {
        TaxRule current = currentRule(request.state(), request.city(), request.productType());
        if (current == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No existing rule to update");
        }
        TaxRule next = new TaxRule();
        next.setId(System.nanoTime()); // unique enough for a demo update
        next.setState(request.state());
        next.setCity(request.city());
        next.setProductType(request.productType());
        next.setRate(request.rate());
        next.setJurisdictionId(current.getJurisdictionId());
        next.setEffectiveFrom(Instant.now());
        taxRuleRepository.save(next);
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
