package com.loan.service.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "loan_offers")
@Getter @Setter
public class LoanOffer extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "application_id", nullable = false, unique = true)
    private LoanApplication application;

    @Column(name = "interest_rate", nullable = false)
    private BigDecimal interestRate;

    @Column(name = "tenure_months", nullable = false)
    private Short tenureMonths;

    @Column(nullable = false)
    private BigDecimal emi;

    @Column(name = "total_payable", nullable = false)
    private BigDecimal totalPayable;
}
