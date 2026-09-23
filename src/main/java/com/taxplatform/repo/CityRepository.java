package com.taxplatform.repo;

import com.taxplatform.domain.City;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CityRepository extends JpaRepository<City, Long> {

    City findFirstByStateCodeAndName(String stateCode, String name);
}
