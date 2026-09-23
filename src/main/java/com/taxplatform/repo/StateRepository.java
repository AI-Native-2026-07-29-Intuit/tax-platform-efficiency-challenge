package com.taxplatform.repo;

import com.taxplatform.domain.State;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StateRepository extends JpaRepository<State, Integer> {

    State findByCode(String code);
}
