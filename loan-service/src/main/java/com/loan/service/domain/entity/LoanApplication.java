package com.loan.service.domain.entity;

import com.loan.service.domain.enums.ApplicationStatus;
import com.loan.service.domain.enums.EmploymentType;
import com.loan.service.domain.enums.LoanPurpose;
import com.loan.service.domain.enums.RiskBand;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.util.List;


@Entity
@Table(name = "loan_applications")
@Getter @Setter
public class LoanApplication extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "applicant_age_snapshot", nullable = false, columnDefinition = "SMALLINT")
    private Short applicantAgeSnapshot;

    @Column(name = "monthly_income_snapshot", nullable = false)
    private BigDecimal monthlyIncomeSnapshot;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "employment_type_snapshot", nullable = false)
    private EmploymentType employmentTypeSnapshot;

    @Column(name = "credit_score_snapshot", nullable = false, columnDefinition = "SMALLINT")
    private Short creditScoreSnapshot;

    @Column(name = "loan_amount", nullable = false)
    private BigDecimal loanAmount;

    @Column(name = "tenure_months", nullable = false, columnDefinition = "SMALLINT")
    private Short tenureMonths;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "loan_purpose", nullable = false)
    private LoanPurpose loanPurpose;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private ApplicationStatus status;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "risk_band")
    private RiskBand riskBand;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "rejection_reasons")
    private String[] rejectionReasons;
}
