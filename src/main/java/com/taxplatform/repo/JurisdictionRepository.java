package com.taxplatform.repo;

import com.taxplatform.domain.Jurisdiction;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JurisdictionRepository extends JpaRepository<Jurisdiction, Long> {

    Jurisdiction findFirstByStateCodeAndCity(String stateCode, String city);
}
