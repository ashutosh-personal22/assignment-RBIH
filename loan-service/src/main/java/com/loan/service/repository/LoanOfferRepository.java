package com.loan.service.repository;

import com.loan.service.domain.entity.LoanOffer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface LoanOfferRepository extends JpaRepository<LoanOffer, UUID> {

    Optional<LoanOffer> findByApplicationId(UUID applicationId);
}
