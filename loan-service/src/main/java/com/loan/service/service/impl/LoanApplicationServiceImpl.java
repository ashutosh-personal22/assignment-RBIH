package com.loan.service.service.impl;

import com.loan.service.domain.entity.LoanApplication;
import com.loan.service.domain.entity.LoanOffer;
import com.loan.service.domain.entity.User;
import com.loan.service.domain.enums.ApplicationStatus;
import com.loan.service.domain.enums.EmploymentType;
import com.loan.service.domain.enums.RiskBand;
import com.loan.service.dto.request.LoanRequest;
import com.loan.service.dto.response.LoanApplicationResponse;
import com.loan.service.exception.ResourceNotFoundException;
import com.loan.service.mapper.ObjectConverter;
import com.loan.service.repository.LoanApplicationRepository;
import com.loan.service.repository.LoanOfferRepository;
import com.loan.service.repository.UserRepository;
import com.loan.service.service.LoanApplicationService;
import com.loan.service.util.EmiCalculator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class LoanApplicationServiceImpl implements LoanApplicationService {

    private static final BigDecimal BASE_RATE              = new BigDecimal("12");
    private static final BigDecimal ELIGIBILITY_EMI_LIMIT  = new BigDecimal("0.60");
    private static final BigDecimal OFFER_EMI_LIMIT        = new BigDecimal("0.50");
    private static final int        MAX_AGE_AT_LOAN_END    = 65;
    private static final int        MIN_CREDIT_SCORE       = 600;

    private final UserRepository            userRepository;
    private final LoanApplicationRepository loanRepo;
    private final LoanOfferRepository       offerRepo;
    private final EmiCalculator             emiCalculator;
    private final ObjectConverter            objectConverter;

    @Override
    @Transactional
    public LoanApplicationResponse applyLoan(String email, LoanRequest request) {

        User user = getUserByEmail(email);

        LoanRequest.Applicant applicant = request.getApplicant();
        LoanRequest.Loan      loan      = request.getLoan();

        List<String> rejectionReasons = evaluateEligibility(applicant, loan);

        if (!rejectionReasons.isEmpty()) {
            return persistAndBuildRejectedResponse(user, applicant, loan, rejectionReasons);
        }

        RiskBand   riskBand     = classifyRiskBand(applicant.getCreditScore());
        BigDecimal interestRate = calculateInterestRate(applicant, loan, riskBand);

        BigDecimal emi          = emiCalculator.calculate(loan.getAmount(), loan.getTenureMonths(), interestRate);
        BigDecimal maxOfferEmi  = applicant.getMonthlyIncome().multiply(OFFER_EMI_LIMIT);

        if (emi.compareTo(maxOfferEmi) > 0) {
            List<String> offerRejection = List.of("EMI_EXCEEDS_50_PERCENT");
            return persistAndBuildRejectedResponse(user, applicant, loan, offerRejection);
        }

        BigDecimal totalPayable = emi.multiply(BigDecimal.valueOf(loan.getTenureMonths()));

        return persistAndBuildApprovedResponse(user, applicant, loan, riskBand, interestRate, emi, totalPayable);
    }

    @Override
    @Transactional(readOnly = true)
    public List<LoanApplicationResponse> getUserLoans(String email) {
        User user = getUserByEmail(email);
        List<LoanApplication> applications = loanRepo.findByUserId(user.getId());
        log.info("Fetching {} loan applications for user id: {}", applications.size(), user.getId());
        return applications.stream()
                .map(objectConverter::mapToResponse)
                .toList();
    }

    private List<String> evaluateEligibility(LoanRequest.Applicant applicant,
                                             LoanRequest.Loan loan) {
        List<String> reasons = new ArrayList<>();

        // Rule 1: credit score < 600
        if (applicant.getCreditScore() < MIN_CREDIT_SCORE) {
            reasons.add("LOW_CREDIT_SCORE");
        }

        // Rule 2: age + tenure(years) > 65
        int ageAtLoanEnd = applicant.getAge() + (loan.getTenureMonths() / 12);
        if (ageAtLoanEnd > MAX_AGE_AT_LOAN_END) {
            reasons.add("AGE_TENURE_LIMIT_EXCEEDED");
        }

        // Rule 3: EMI > 60% of monthly income
        RiskBand   provisionalRiskBand = classifyRiskBand(applicant.getCreditScore());
        BigDecimal provisionalRate     = calculateInterestRate(applicant, loan, provisionalRiskBand);
        BigDecimal emi                 = emiCalculator.calculate(loan.getAmount(), loan.getTenureMonths(), provisionalRate);
        BigDecimal maxAllowedEmi       = applicant.getMonthlyIncome().multiply(ELIGIBILITY_EMI_LIMIT);

        if (emi.compareTo(maxAllowedEmi) > 0) {
            reasons.add("EMI_EXCEEDS_60_PERCENT");
        }

        return reasons;
    }


    private RiskBand classifyRiskBand(int creditScore) {
        if (creditScore >= 750) return RiskBand.LOW;
        if (creditScore >= 650) return RiskBand.MEDIUM;
        return RiskBand.HIGH;
    }


    private BigDecimal calculateInterestRate(LoanRequest.Applicant applicant,
                                             LoanRequest.Loan loan,
                                             RiskBand riskBand) {
        BigDecimal riskPremium = switch (riskBand) {
            case LOW    -> BigDecimal.ZERO;
            case MEDIUM -> new BigDecimal("1.5");
            case HIGH   -> new BigDecimal("3");
        };

        BigDecimal employmentPremium =
                applicant.getEmploymentType() == EmploymentType.SELF_EMPLOYED
                        ? new BigDecimal("1")
                        : BigDecimal.ZERO;

        BigDecimal loanSizePremium =
                loan.getAmount().compareTo(new BigDecimal("1000000")) > 0
                        ? new BigDecimal("0.5")
                        : BigDecimal.ZERO;

        return BASE_RATE
                .add(riskPremium)
                .add(employmentPremium)
                .add(loanSizePremium);
    }


    private LoanApplicationResponse persistAndBuildRejectedResponse(
            User user,
            LoanRequest.Applicant applicant,
            LoanRequest.Loan loan,
            List<String> rejectionReasons) {

        LoanApplication application = buildBaseApplication(user, applicant, loan);
        application.setStatus(ApplicationStatus.REJECTED);
        application.setRiskBand(null);
        application.setRejectionReasons(rejectionReasons.toArray(String[]::new));

        LoanApplication saved = loanRepo.save(application);
        log.info("Loan application REJECTED — id: {}, reasons: {}", saved.getId(), rejectionReasons);

        return LoanApplicationResponse.builder()
                .applicationId(saved.getId())
                .status(ApplicationStatus.REJECTED)
                .riskBand(null)
                .rejectionReasons(rejectionReasons)
                .build();
    }

    private LoanApplicationResponse persistAndBuildApprovedResponse(
            User user,
            LoanRequest.Applicant applicant,
            LoanRequest.Loan loan,
            RiskBand riskBand,
            BigDecimal interestRate,
            BigDecimal emi,
            BigDecimal totalPayable) {

        LoanApplication application = buildBaseApplication(user, applicant, loan);
        application.setStatus(ApplicationStatus.APPROVED);
        application.setRiskBand(riskBand);

        LoanApplication savedApp = loanRepo.save(application);

        LoanOffer offer = new LoanOffer();
        offer.setApplication(savedApp);
        offer.setInterestRate(interestRate);
        offer.setTenureMonths(loan.getTenureMonths().shortValue());
        offer.setEmi(emi);
        offer.setTotalPayable(totalPayable);

        offerRepo.save(offer);
        log.info("Loan application APPROVED — id: {}, riskBand: {}, rate: {}%",
                savedApp.getId(), riskBand, interestRate);

        return LoanApplicationResponse.builder()
                .applicationId(savedApp.getId())
                .status(ApplicationStatus.APPROVED)
                .riskBand(riskBand)
                .offer(LoanApplicationResponse.Offer.builder()
                        .interestRate(interestRate)
                        .tenureMonths(loan.getTenureMonths())
                        .emi(emi)
                        .totalPayable(totalPayable)
                        .build())
                .build();
    }


    private LoanApplication buildBaseApplication(User user,
                                                 LoanRequest.Applicant applicant,
                                                 LoanRequest.Loan loan) {
        LoanApplication app = new LoanApplication();
        app.setUser(user);
        app.setLoanAmount(loan.getAmount());
        app.setTenureMonths(loan.getTenureMonths().shortValue());
        app.setLoanPurpose(loan.getPurpose());
        app.setApplicantAgeSnapshot(applicant.getAge().shortValue());
        app.setMonthlyIncomeSnapshot(applicant.getMonthlyIncome());
        app.setEmploymentTypeSnapshot(applicant.getEmploymentType());
        app.setCreditScoreSnapshot((short) applicant.getCreditScore());

        return app;
    }


    private User getUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + email));
    }
}