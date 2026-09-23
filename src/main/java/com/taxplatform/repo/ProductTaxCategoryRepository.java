package com.taxplatform.repo;

import com.taxplatform.domain.ProductTaxCategory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductTaxCategoryRepository extends JpaRepository<ProductTaxCategory, Integer> {

    ProductTaxCategory findByCode(String code);
}
