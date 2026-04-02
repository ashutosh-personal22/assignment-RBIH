package com.loan.service.repository;

import com.loan.service.domain.entity.LoanApplication;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface LoanApplicationRepository extends JpaRepository<LoanApplication, UUID> {

    List<LoanApplication> findByUserId(UUID userId);

    Page<LoanApplication> findByUserId(UUID userId, Pageable pageable);
}
