package com.taxplatform.repo;

import com.taxplatform.domain.Transaction;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    @Query("select t from Transaction t order by t.id desc")
    java.util.List<Transaction> findRecent(Pageable pageable);
}
