package com.loan.service.serviceImpl;

import com.loan.service.domain.entity.LoanApplication;
import com.loan.service.domain.entity.LoanOffer;
import com.loan.service.domain.entity.User;
import com.loan.service.domain.enums.ApplicationStatus;
import com.loan.service.domain.enums.EmploymentType;
import com.loan.service.domain.enums.LoanPurpose;
import com.loan.service.domain.enums.RiskBand;
import com.loan.service.dto.request.LoanRequest;
import com.loan.service.dto.response.LoanApplicationResponse;
import com.loan.service.exception.ResourceNotFoundException;
import com.loan.service.mapper.ObjectConverter;
import com.loan.service.repository.LoanApplicationRepository;
import com.loan.service.repository.LoanOfferRepository;
import com.loan.service.repository.UserRepository;
import com.loan.service.service.impl.LoanApplicationServiceImpl;
import com.loan.service.util.EmiCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LoanApplicationServiceImplTest {

    @Mock private UserRepository userRepository;
    @Mock private LoanApplicationRepository loanRepo;
    @Mock private LoanOfferRepository offerRepo;
    @Mock private EmiCalculator emiCalculator;
    @Mock private ObjectConverter objectConverter;

    @InjectMocks
    private LoanApplicationServiceImpl service;

    @Captor private ArgumentCaptor<LoanApplication> applicationCaptor;
    @Captor private ArgumentCaptor<LoanOffer> offerCaptor;

    private static final String TEST_EMAIL = "test@example.com";
    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(UUID.randomUUID());
        testUser.setEmail(TEST_EMAIL);
        testUser.setFullName("Test User");
    }


    private LoanRequest buildRequest(int age, BigDecimal income, EmploymentType empType,
                                     short creditScore, BigDecimal amount,
                                     int tenure, LoanPurpose purpose) {
        LoanRequest request = new LoanRequest();

        LoanRequest.Applicant applicant = new LoanRequest.Applicant();
        applicant.setName("Test Applicant");
        applicant.setAge(age);
        applicant.setMonthlyIncome(income);
        applicant.setEmploymentType(empType);
        applicant.setCreditScore(creditScore);

        LoanRequest.Loan loan = new LoanRequest.Loan();
        loan.setAmount(amount);
        loan.setTenureMonths(tenure);
        loan.setPurpose(purpose);

        request.setApplicant(applicant);
        request.setLoan(loan);
        return request;
    }

    private LoanRequest buildApprovedRequest() {
        return buildRequest(30, new BigDecimal("75000"), EmploymentType.SALARIED,
                (short) 750, new BigDecimal("500000"), 36, LoanPurpose.PERSONAL);
    }

    private void mockUserLookup() {
        when(userRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.of(testUser));
    }

    private void mockSaveApplication() {
        when(loanRepo.save(any(LoanApplication.class))).thenAnswer(invocation -> {
            LoanApplication app = invocation.getArgument(0);
            app.setId(UUID.randomUUID());
            return app;
        });
    }

    private void mockSaveOffer() {
        when(offerRepo.save(any(LoanOffer.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }


    @Nested
    @DisplayName("Section 3 — Eligibility: Credit Score")
    class CreditScoreEligibility {

        @Test
        @DisplayName("Reject when credit score < 600")
        void shouldRejectWhenCreditScoreBelow600() {
            mockUserLookup();
            mockSaveApplication();

            LoanRequest request = buildRequest(30, new BigDecimal("75000"),
                    EmploymentType.SALARIED, (short) 580, new BigDecimal("500000"),
                    36, LoanPurpose.PERSONAL);

            // EMI calc still needed for the 60% check
            when(emiCalculator.calculate(any(), anyInt(), any()))
                    .thenReturn(new BigDecimal("10000"));

            LoanApplicationResponse response = service.applyLoan(TEST_EMAIL, request);

            assertThat(response.getStatus()).isEqualTo(ApplicationStatus.REJECTED);
            assertThat(response.getRiskBand()).isNull();
            assertThat(response.getRejectionReasons()).contains("LOW_CREDIT_SCORE");
            assertThat(response.getOffer()).isNull();
        }

        @Test
        @DisplayName("Accept when credit score = 600 (boundary)")
        void shouldAcceptWhenCreditScoreExactly600() {
            mockUserLookup();
            mockSaveApplication();
            mockSaveOffer();

            LoanRequest request = buildRequest(30, new BigDecimal("75000"),
                    EmploymentType.SALARIED, (short) 600, new BigDecimal("500000"),
                    36, LoanPurpose.PERSONAL);

            when(emiCalculator.calculate(any(), anyInt(), any()))
                    .thenReturn(new BigDecimal("10000")); // well under 50%

            LoanApplicationResponse response = service.applyLoan(TEST_EMAIL, request);

            assertThat(response.getStatus()).isEqualTo(ApplicationStatus.APPROVED);
            assertThat(response.getRejectionReasons()).isNull();
        }

        @Test
        @DisplayName("Reject when credit score = 599 (boundary)")
        void shouldRejectWhenCreditScore599() {
            mockUserLookup();
            mockSaveApplication();

            LoanRequest request = buildRequest(30, new BigDecimal("75000"),
                    EmploymentType.SALARIED, (short) 599, new BigDecimal("500000"),
                    36, LoanPurpose.PERSONAL);

            when(emiCalculator.calculate(any(), anyInt(), any()))
                    .thenReturn(new BigDecimal("10000"));

            LoanApplicationResponse response = service.applyLoan(TEST_EMAIL, request);

            assertThat(response.getStatus()).isEqualTo(ApplicationStatus.REJECTED);
            assertThat(response.getRejectionReasons()).contains("LOW_CREDIT_SCORE");
        }
    }

    @Nested
    @DisplayName("Section 3 — Eligibility: Age + Tenure")
    class AgeTenureEligibility {

        @Test
        @DisplayName("Reject when age + tenure(years) > 65")
        void shouldRejectWhenAgePlusTenureExceeds65() {
            mockUserLookup();
            mockSaveApplication();

            // age=55, tenure=180 months = 15 years → 55+15 = 70 > 65
            LoanRequest request = buildRequest(55, new BigDecimal("100000"),
                    EmploymentType.SALARIED, (short) 750, new BigDecimal("500000"),
                    180, LoanPurpose.HOME);

            when(emiCalculator.calculate(any(), anyInt(), any()))
                    .thenReturn(new BigDecimal("5000"));

            LoanApplicationResponse response = service.applyLoan(TEST_EMAIL, request);

            assertThat(response.getStatus()).isEqualTo(ApplicationStatus.REJECTED);
            assertThat(response.getRejectionReasons()).contains("AGE_TENURE_LIMIT_EXCEEDED");
        }

        @Test
        @DisplayName("Accept when age + tenure(years) = 65 (boundary)")
        void shouldAcceptWhenAgePlusTenureEquals65() {
            mockUserLookup();
            mockSaveApplication();
            mockSaveOffer();

            // age=53, tenure=144 months = 12 years → 53+12 = 65 (not > 65)
            LoanRequest request = buildRequest(53, new BigDecimal("100000"),
                    EmploymentType.SALARIED, (short) 750, new BigDecimal("500000"),
                    144, LoanPurpose.HOME);

            when(emiCalculator.calculate(any(), anyInt(), any()))
                    .thenReturn(new BigDecimal("5000"));

            LoanApplicationResponse response = service.applyLoan(TEST_EMAIL, request);

            assertThat(response.getStatus()).isEqualTo(ApplicationStatus.APPROVED);
        }

        @Test
        @DisplayName("Reject when age + tenure(years) = 66 (boundary)")
        void shouldRejectWhenAgePlusTenureEquals66() {
            mockUserLookup();
            mockSaveApplication();

            // age=54, tenure=144 months = 12 years → 54+12 = 66 > 65
            LoanRequest request = buildRequest(54, new BigDecimal("100000"),
                    EmploymentType.SALARIED, (short) 750, new BigDecimal("500000"),
                    144, LoanPurpose.HOME);

            when(emiCalculator.calculate(any(), anyInt(), any()))
                    .thenReturn(new BigDecimal("5000"));

            LoanApplicationResponse response = service.applyLoan(TEST_EMAIL, request);

            assertThat(response.getStatus()).isEqualTo(ApplicationStatus.REJECTED);
            assertThat(response.getRejectionReasons()).contains("AGE_TENURE_LIMIT_EXCEEDED");
        }
    }

    @Nested
    @DisplayName("Section 3 — Eligibility: EMI > 60%")
    class EmiEligibility {

        @Test
        @DisplayName("Reject when EMI > 60% of monthly income")
        void shouldRejectWhenEmiExceeds60Percent() {
            mockUserLookup();
            mockSaveApplication();

            LoanRequest request = buildRequest(28, new BigDecimal("25000"),
                    EmploymentType.SALARIED, (short) 700, new BigDecimal("4500000"),
                    60, LoanPurpose.AUTO);

            // EMI = 20000 > 60% of 25000 = 15000
            when(emiCalculator.calculate(any(), anyInt(), any()))
                    .thenReturn(new BigDecimal("20000"));

            LoanApplicationResponse response = service.applyLoan(TEST_EMAIL, request);

            assertThat(response.getStatus()).isEqualTo(ApplicationStatus.REJECTED);
            assertThat(response.getRejectionReasons()).contains("EMI_EXCEEDS_60_PERCENT");
        }

        @Test
        @DisplayName("Accept when EMI = exactly 60% of monthly income (boundary)")
        void shouldAcceptWhenEmiExactly60Percent() {
            mockUserLookup();
            mockSaveApplication();

            LoanRequest request = buildRequest(30, new BigDecimal("50000"),
                    EmploymentType.SALARIED, (short) 750, new BigDecimal("500000"),
                    36, LoanPurpose.PERSONAL);

            // EMI = 30000 = exactly 60% of 50000 → not > 60%, passes eligibility
            // But 30000 > 50% of 50000 = 25000 → rejected by Section 6
            when(emiCalculator.calculate(any(), anyInt(), any()))
                    .thenReturn(new BigDecimal("30000"));

            LoanApplicationResponse response = service.applyLoan(TEST_EMAIL, request);

            assertThat(response.getStatus()).isEqualTo(ApplicationStatus.REJECTED);
            assertThat(response.getRejectionReasons()).contains("EMI_EXCEEDS_50_PERCENT");
        }
    }

    @Nested
    @DisplayName("Section 3 — Multiple Rejection Reasons")
    class MultipleRejections {

        @Test
        @DisplayName("Return all applicable rejection reasons at once")
        void shouldReturnAllRejectionReasons() {
            mockUserLookup();
            mockSaveApplication();

            // credit=500 (<600), age=58+tenure=120m(10y)=68 (>65), EMI will exceed 60%
            LoanRequest request = buildRequest(58, new BigDecimal("20000"),
                    EmploymentType.SELF_EMPLOYED, (short) 500, new BigDecimal("4000000"),
                    120, LoanPurpose.HOME);

            when(emiCalculator.calculate(any(), anyInt(), any()))
                    .thenReturn(new BigDecimal("50000")); // 50000 > 60% of 20000 = 12000

            LoanApplicationResponse response = service.applyLoan(TEST_EMAIL, request);

            assertThat(response.getStatus()).isEqualTo(ApplicationStatus.REJECTED);
            assertThat(response.getRejectionReasons())
                    .containsExactly("LOW_CREDIT_SCORE", "AGE_TENURE_LIMIT_EXCEEDED", "EMI_EXCEEDS_60_PERCENT");
        }
    }

    @Nested
    @DisplayName("Section 4 — Risk Band Classification")
    class RiskBandClassification {

        @Test
        @DisplayName("Credit score 750+ → LOW risk band")
        void shouldClassifyAsLowRisk() {
            mockUserLookup();
            mockSaveApplication();
            mockSaveOffer();

            LoanRequest request = buildRequest(30, new BigDecimal("75000"),
                    EmploymentType.SALARIED, (short) 750, new BigDecimal("500000"),
                    36, LoanPurpose.PERSONAL);

            when(emiCalculator.calculate(any(), anyInt(), any()))
                    .thenReturn(new BigDecimal("10000"));

            LoanApplicationResponse response = service.applyLoan(TEST_EMAIL, request);

            assertThat(response.getRiskBand()).isEqualTo(RiskBand.LOW);
        }

        @Test
        @DisplayName("Credit score 650–749 → MEDIUM risk band")
        void shouldClassifyAsMediumRisk() {
            mockUserLookup();
            mockSaveApplication();
            mockSaveOffer();

            LoanRequest request = buildRequest(30, new BigDecimal("75000"),
                    EmploymentType.SALARIED, (short) 700, new BigDecimal("500000"),
                    36, LoanPurpose.PERSONAL);

            when(emiCalculator.calculate(any(), anyInt(), any()))
                    .thenReturn(new BigDecimal("10000"));

            LoanApplicationResponse response = service.applyLoan(TEST_EMAIL, request);

            assertThat(response.getRiskBand()).isEqualTo(RiskBand.MEDIUM);
        }

        @Test
        @DisplayName("Credit score 600–649 → HIGH risk band")
        void shouldClassifyAsHighRisk() {
            mockUserLookup();
            mockSaveApplication();
            mockSaveOffer();

            LoanRequest request = buildRequest(30, new BigDecimal("100000"),
                    EmploymentType.SALARIED, (short) 620, new BigDecimal("300000"),
                    24, LoanPurpose.AUTO);

            when(emiCalculator.calculate(any(), anyInt(), any()))
                    .thenReturn(new BigDecimal("10000"));

            LoanApplicationResponse response = service.applyLoan(TEST_EMAIL, request);

            assertThat(response.getRiskBand()).isEqualTo(RiskBand.HIGH);
        }

        @Test
        @DisplayName("Credit score 749 → MEDIUM (upper boundary)")
        void shouldClassifyAsMediumAt749() {
            mockUserLookup();
            mockSaveApplication();
            mockSaveOffer();

            LoanRequest request = buildRequest(30, new BigDecimal("75000"),
                    EmploymentType.SALARIED, (short) 749, new BigDecimal("500000"),
                    36, LoanPurpose.PERSONAL);

            when(emiCalculator.calculate(any(), anyInt(), any()))
                    .thenReturn(new BigDecimal("10000"));

            LoanApplicationResponse response = service.applyLoan(TEST_EMAIL, request);

            assertThat(response.getRiskBand()).isEqualTo(RiskBand.MEDIUM);
        }

        @Test
        @DisplayName("Credit score 650 → MEDIUM (lower boundary)")
        void shouldClassifyAsMediumAt650() {
            mockUserLookup();
            mockSaveApplication();
            mockSaveOffer();

            LoanRequest request = buildRequest(30, new BigDecimal("75000"),
                    EmploymentType.SALARIED, (short) 650, new BigDecimal("500000"),
                    36, LoanPurpose.PERSONAL);

            when(emiCalculator.calculate(any(), anyInt(), any()))
                    .thenReturn(new BigDecimal("10000"));

            LoanApplicationResponse response = service.applyLoan(TEST_EMAIL, request);

            assertThat(response.getRiskBand()).isEqualTo(RiskBand.MEDIUM);
        }

        @Test
        @DisplayName("Credit score 649 → HIGH (boundary)")
        void shouldClassifyAsHighAt649() {
            mockUserLookup();
            mockSaveApplication();
            mockSaveOffer();

            LoanRequest request = buildRequest(30, new BigDecimal("100000"),
                    EmploymentType.SALARIED, (short) 649, new BigDecimal("300000"),
                    24, LoanPurpose.AUTO);

            when(emiCalculator.calculate(any(), anyInt(), any()))
                    .thenReturn(new BigDecimal("10000"));

            LoanApplicationResponse response = service.applyLoan(TEST_EMAIL, request);

            assertThat(response.getRiskBand()).isEqualTo(RiskBand.HIGH);
        }
    }



    @Nested
    @DisplayName("Section 5 — Interest Rate Calculation")
    class InterestRateCalculation {

        @Test
        @DisplayName("LOW risk + SALARIED + small loan → 12%")
        void shouldCalculateBaseRate() {
            mockUserLookup();
            mockSaveApplication();
            mockSaveOffer();

            LoanRequest request = buildRequest(30, new BigDecimal("75000"),
                    EmploymentType.SALARIED, (short) 800, new BigDecimal("500000"),
                    36, LoanPurpose.PERSONAL);

            when(emiCalculator.calculate(any(), anyInt(), any()))
                    .thenReturn(new BigDecimal("10000"));

            service.applyLoan(TEST_EMAIL, request);

            verify(offerRepo).save(offerCaptor.capture());
            assertThat(offerCaptor.getValue().getInterestRate())
                    .isEqualByComparingTo(new BigDecimal("12"));
        }

        @Test
        @DisplayName("MEDIUM risk + SALARIED + small loan → 12 + 1.5 = 13.5%")
        void shouldAddRiskPremiumForMedium() {
            mockUserLookup();
            mockSaveApplication();
            mockSaveOffer();

            LoanRequest request = buildRequest(30, new BigDecimal("75000"),
                    EmploymentType.SALARIED, (short) 700, new BigDecimal("500000"),
                    36, LoanPurpose.PERSONAL);

            when(emiCalculator.calculate(any(), anyInt(), any()))
                    .thenReturn(new BigDecimal("10000"));

            service.applyLoan(TEST_EMAIL, request);

            verify(offerRepo).save(offerCaptor.capture());
            assertThat(offerCaptor.getValue().getInterestRate())
                    .isEqualByComparingTo(new BigDecimal("13.5"));
        }

        @Test
        @DisplayName("HIGH risk + SALARIED + small loan → 12 + 3 = 15%")
        void shouldAddRiskPremiumForHigh() {
            mockUserLookup();
            mockSaveApplication();
            mockSaveOffer();

            LoanRequest request = buildRequest(30, new BigDecimal("100000"),
                    EmploymentType.SALARIED, (short) 620, new BigDecimal("300000"),
                    24, LoanPurpose.AUTO);

            when(emiCalculator.calculate(any(), anyInt(), any()))
                    .thenReturn(new BigDecimal("10000"));

            service.applyLoan(TEST_EMAIL, request);

            verify(offerRepo).save(offerCaptor.capture());
            assertThat(offerCaptor.getValue().getInterestRate())
                    .isEqualByComparingTo(new BigDecimal("15"));
        }

        @Test
        @DisplayName("SELF_EMPLOYED adds +1% employment premium")
        void shouldAddEmploymentPremium() {
            mockUserLookup();
            mockSaveApplication();
            mockSaveOffer();

            LoanRequest request = buildRequest(30, new BigDecimal("100000"),
                    EmploymentType.SELF_EMPLOYED, (short) 800, new BigDecimal("500000"),
                    36, LoanPurpose.PERSONAL);

            when(emiCalculator.calculate(any(), anyInt(), any()))
                    .thenReturn(new BigDecimal("10000"));

            service.applyLoan(TEST_EMAIL, request);

            verify(offerRepo).save(offerCaptor.capture());
            // 12 (base) + 0 (low risk) + 1 (self employed) = 13
            assertThat(offerCaptor.getValue().getInterestRate())
                    .isEqualByComparingTo(new BigDecimal("13"));
        }

        @Test
        @DisplayName("Loan > 10,00,000 adds +0.5% loan size premium")
        void shouldAddLoanSizePremium() {
            mockUserLookup();
            mockSaveApplication();
            mockSaveOffer();

            LoanRequest request = buildRequest(35, new BigDecimal("200000"),
                    EmploymentType.SALARIED, (short) 800, new BigDecimal("1500000"),
                    60, LoanPurpose.HOME);

            when(emiCalculator.calculate(any(), anyInt(), any()))
                    .thenReturn(new BigDecimal("20000"));

            service.applyLoan(TEST_EMAIL, request);

            verify(offerRepo).save(offerCaptor.capture());
            // 12 (base) + 0 (low risk) + 0 (salaried) + 0.5 (large loan) = 12.5
            assertThat(offerCaptor.getValue().getInterestRate())
                    .isEqualByComparingTo(new BigDecimal("12.5"));
        }

        @Test
        @DisplayName("All premiums combined: MEDIUM + SELF_EMPLOYED + large loan → 15.5%")
        void shouldCombineAllPremiums() {
            mockUserLookup();
            mockSaveApplication();
            mockSaveOffer();

            LoanRequest request = buildRequest(35, new BigDecimal("200000"),
                    EmploymentType.SELF_EMPLOYED, (short) 700, new BigDecimal("1500000"),
                    60, LoanPurpose.HOME);

            when(emiCalculator.calculate(any(), anyInt(), any()))
                    .thenReturn(new BigDecimal("20000"));

            service.applyLoan(TEST_EMAIL, request);

            verify(offerRepo).save(offerCaptor.capture());
            // 12 + 1.5 + 1 + 0.5 = 15
            assertThat(offerCaptor.getValue().getInterestRate())
                    .isEqualByComparingTo(new BigDecimal("15"));
        }

        @Test
        @DisplayName("Loan = exactly 10,00,000 → no loan size premium")
        void shouldNotAddLoanSizePremiumAtBoundary() {
            mockUserLookup();
            mockSaveApplication();
            mockSaveOffer();

            LoanRequest request = buildRequest(30, new BigDecimal("100000"),
                    EmploymentType.SALARIED, (short) 800, new BigDecimal("1000000"),
                    60, LoanPurpose.HOME);

            when(emiCalculator.calculate(any(), anyInt(), any()))
                    .thenReturn(new BigDecimal("15000"));

            service.applyLoan(TEST_EMAIL, request);

            verify(offerRepo).save(offerCaptor.capture());
            // 12 + 0 + 0 + 0 = 12 (1000000 is NOT > 1000000)
            assertThat(offerCaptor.getValue().getInterestRate())
                    .isEqualByComparingTo(new BigDecimal("12"));
        }
    }

    @Nested
    @DisplayName("Section 6 — Offer Generation (EMI ≤ 50%)")
    class OfferGeneration {

        @Test
        @DisplayName("Reject when EMI > 50% but ≤ 60% (passes eligibility, fails offer)")
        void shouldRejectAtOfferStageWhenEmiExceeds50Percent() {
            mockUserLookup();
            mockSaveApplication();

            LoanRequest request = buildRequest(30, new BigDecimal("50000"),
                    EmploymentType.SALARIED, (short) 750, new BigDecimal("500000"),
                    36, LoanPurpose.PERSONAL);

            // EMI = 28000 → 56% of 50000 (> 50% but < 60%)
            when(emiCalculator.calculate(any(), anyInt(), any()))
                    .thenReturn(new BigDecimal("28000"));

            LoanApplicationResponse response = service.applyLoan(TEST_EMAIL, request);

            assertThat(response.getStatus()).isEqualTo(ApplicationStatus.REJECTED);
            assertThat(response.getRejectionReasons()).contains("EMI_EXCEEDS_50_PERCENT");
        }

        @Test
        @DisplayName("Approve when EMI = exactly 50% of income (boundary)")
        void shouldApproveWhenEmiExactly50Percent() {
            mockUserLookup();
            mockSaveApplication();
            mockSaveOffer();

            LoanRequest request = buildRequest(30, new BigDecimal("50000"),
                    EmploymentType.SALARIED, (short) 750, new BigDecimal("500000"),
                    36, LoanPurpose.PERSONAL);

            // EMI = 25000 = exactly 50% of 50000
            when(emiCalculator.calculate(any(), anyInt(), any()))
                    .thenReturn(new BigDecimal("25000"));

            LoanApplicationResponse response = service.applyLoan(TEST_EMAIL, request);

            assertThat(response.getStatus()).isEqualTo(ApplicationStatus.APPROVED);
            assertThat(response.getOffer()).isNotNull();
        }

        @Test
        @DisplayName("Approved response contains correct offer details")
        void shouldReturnCorrectOfferDetails() {
            mockUserLookup();
            mockSaveApplication();
            mockSaveOffer();

            LoanRequest request = buildRequest(30, new BigDecimal("75000"),
                    EmploymentType.SALARIED, (short) 750, new BigDecimal("500000"),
                    36, LoanPurpose.PERSONAL);

            BigDecimal emi = new BigDecimal("16607.15");
            when(emiCalculator.calculate(any(), anyInt(), any())).thenReturn(emi);

            LoanApplicationResponse response = service.applyLoan(TEST_EMAIL, request);

            assertThat(response.getStatus()).isEqualTo(ApplicationStatus.APPROVED);
            assertThat(response.getOffer()).isNotNull();
            assertThat(response.getOffer().getInterestRate()).isEqualByComparingTo(new BigDecimal("12"));
            assertThat(response.getOffer().getTenureMonths()).isEqualTo(36);
            assertThat(response.getOffer().getEmi()).isEqualByComparingTo(emi);
            assertThat(response.getOffer().getTotalPayable())
                    .isEqualByComparingTo(emi.multiply(BigDecimal.valueOf(36)));
        }
    }


    @Nested
    @DisplayName("Persistence")
    class Persistence {

        @Test
        @DisplayName("Approved application persists LoanApplication and LoanOffer")
        void shouldPersistApplicationAndOffer() {
            mockUserLookup();
            mockSaveApplication();
            mockSaveOffer();

            LoanRequest request = buildApprovedRequest();
            when(emiCalculator.calculate(any(), anyInt(), any()))
                    .thenReturn(new BigDecimal("10000"));

            service.applyLoan(TEST_EMAIL, request);

            verify(loanRepo).save(applicationCaptor.capture());
            verify(offerRepo).save(offerCaptor.capture());

            LoanApplication savedApp = applicationCaptor.getValue();
            assertThat(savedApp.getUser()).isEqualTo(testUser);
            assertThat(savedApp.getStatus()).isEqualTo(ApplicationStatus.APPROVED);
            assertThat(savedApp.getLoanAmount()).isEqualByComparingTo(new BigDecimal("500000"));
            assertThat(savedApp.getTenureMonths()).isEqualTo((short) 36);
            assertThat(savedApp.getLoanPurpose()).isEqualTo(LoanPurpose.PERSONAL);
            assertThat(savedApp.getApplicantAgeSnapshot()).isEqualTo((short) 30);
            assertThat(savedApp.getMonthlyIncomeSnapshot()).isEqualByComparingTo(new BigDecimal("75000"));
            assertThat(savedApp.getEmploymentTypeSnapshot()).isEqualTo(EmploymentType.SALARIED);
            assertThat(savedApp.getCreditScoreSnapshot()).isEqualTo((short) 750);
        }

        @Test
        @DisplayName("Rejected application persists LoanApplication only, no offer")
        void shouldPersistOnlyApplicationWhenRejected() {
            mockUserLookup();
            mockSaveApplication();

            LoanRequest request = buildRequest(30, new BigDecimal("75000"),
                    EmploymentType.SALARIED, (short) 580, new BigDecimal("500000"),
                    36, LoanPurpose.PERSONAL);

            when(emiCalculator.calculate(any(), anyInt(), any()))
                    .thenReturn(new BigDecimal("10000"));

            service.applyLoan(TEST_EMAIL, request);

            verify(loanRepo).save(applicationCaptor.capture());
            verify(offerRepo, never()).save(any());

            LoanApplication savedApp = applicationCaptor.getValue();
            assertThat(savedApp.getStatus()).isEqualTo(ApplicationStatus.REJECTED);
            assertThat(savedApp.getRejectionReasons()).contains("LOW_CREDIT_SCORE");
        }

        @Test
        @DisplayName("Rejected response has applicationId (UUID)")
        void shouldReturnApplicationIdOnRejection() {
            mockUserLookup();
            mockSaveApplication();

            LoanRequest request = buildRequest(30, new BigDecimal("75000"),
                    EmploymentType.SALARIED, (short) 580, new BigDecimal("500000"),
                    36, LoanPurpose.PERSONAL);

            when(emiCalculator.calculate(any(), anyInt(), any()))
                    .thenReturn(new BigDecimal("10000"));

            LoanApplicationResponse response = service.applyLoan(TEST_EMAIL, request);

            assertThat(response.getApplicationId()).isNotNull();
        }
    }


    @Nested
    @DisplayName("getUserLoans")
    class GetUserLoans {

        @Test
        @DisplayName("Returns empty list when no applications exist")
        void shouldReturnEmptyListWhenNoLoans() {
            mockUserLookup();
            when(loanRepo.findByUserId(testUser.getId())).thenReturn(List.of());

            List<LoanApplicationResponse> result = service.getUserLoans(TEST_EMAIL);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Throws ResourceNotFoundException for unknown email")
        void shouldThrowWhenUserNotFound() {
            when(userRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getUserLoans("unknown@example.com"))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("User not found");
        }
    }


    @Nested
    @DisplayName("Edge Cases")
    class EdgeCases {

        @Test
        @DisplayName("Throws ResourceNotFoundException for unknown email on applyLoan")
        void shouldThrowWhenUserNotFoundOnApply() {
            when(userRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());

            LoanRequest request = buildApprovedRequest();

            assertThatThrownBy(() -> service.applyLoan("unknown@example.com", request))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("User not found");
        }

        @Test
        @DisplayName("All three loan purposes are accepted")
        void shouldAcceptAllLoanPurposes() {
            for (LoanPurpose purpose : LoanPurpose.values()) {
                mockUserLookup();
                mockSaveApplication();
                mockSaveOffer();

                LoanRequest request = buildRequest(30, new BigDecimal("75000"),
                        EmploymentType.SALARIED, (short) 750, new BigDecimal("500000"),
                        36, purpose);

                when(emiCalculator.calculate(any(), anyInt(), any()))
                        .thenReturn(new BigDecimal("10000"));

                LoanApplicationResponse response = service.applyLoan(TEST_EMAIL, request);
                assertThat(response.getStatus()).isEqualTo(ApplicationStatus.APPROVED);

                reset(loanRepo, offerRepo, userRepository);
            }
        }
    }
}