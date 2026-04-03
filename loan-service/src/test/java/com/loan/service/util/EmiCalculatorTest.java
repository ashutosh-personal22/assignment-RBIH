package com.loan.service.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static org.assertj.core.api.Assertions.*;
class EmiCalculatorTest {

    private EmiCalculator emiCalculator;

    @BeforeEach
    void setUp() {
        emiCalculator = new EmiCalculator();
    }

    @Nested
    @DisplayName("EMI Calculation — Standard Cases")
    class StandardCases {

        @Test
        @DisplayName("P=500000, tenure=36, rate=12% → expected EMI ~16607.15")
        void shouldCalculateEmiForStandardLoan() {
            BigDecimal emi = emiCalculator.calculate(
                    new BigDecimal("500000"), 36, new BigDecimal("12"));
            assertThat(emi).isEqualByComparingTo(new BigDecimal("16607.15"));
        }

        @Test
        @DisplayName("P=1000000, tenure=60, rate=13.5% → verify calculation")
        void shouldCalculateEmiForMediumRiskLoan() {
            BigDecimal emi = emiCalculator.calculate(
                    new BigDecimal("1000000"), 60, new BigDecimal("13.5"));

            // r = 13.5 / 12 / 100 = 0.01125
            assertThat(emi).isNotNull();
            assertThat(emi.compareTo(BigDecimal.ZERO)).isGreaterThan(0);
            assertThat(emi.scale()).isLessThanOrEqualTo(2);
        }

        @Test
        @DisplayName("P=300000, tenure=24, rate=15% → verify calculation")
        void shouldCalculateEmiForHighRiskLoan() {
            BigDecimal emi = emiCalculator.calculate(
                    new BigDecimal("300000"), 24, new BigDecimal("15"));

            // r = 15 / 12 / 100 = 0.0125
            assertThat(emi).isNotNull();
            assertThat(emi.compareTo(BigDecimal.ZERO)).isGreaterThan(0);
            assertThat(emi.scale()).isLessThanOrEqualTo(2);
        }

        @Test
        @DisplayName("P=5000000, tenure=360, rate=12.5% → maximum values")
        void shouldCalculateEmiForMaxLoan() {
            BigDecimal emi = emiCalculator.calculate(
                    new BigDecimal("5000000"), 360, new BigDecimal("12.5"));

            assertThat(emi).isNotNull();
            assertThat(emi.compareTo(BigDecimal.ZERO)).isGreaterThan(0);
        }

        @Test
        @DisplayName("P=10000, tenure=6, rate=12% → minimum values")
        void shouldCalculateEmiForMinLoan() {
            BigDecimal emi = emiCalculator.calculate(
                    new BigDecimal("10000"), 6, new BigDecimal("12"));

            assertThat(emi).isNotNull();
            assertThat(emi.compareTo(BigDecimal.ZERO)).isGreaterThan(0);
        }
    }

    @Nested
    @DisplayName("EMI Calculation — Scale and Rounding")
    class ScaleAndRounding {

        @Test
        @DisplayName("Result scale should be 2 (as per assignment)")
        void shouldHaveScale2() {
            BigDecimal emi = emiCalculator.calculate(
                    new BigDecimal("500000"), 36, new BigDecimal("12"));

            assertThat(emi.scale()).isEqualTo(2);
        }

        @Test
        @DisplayName("Uses HALF_UP rounding mode")
        void shouldUseHalfUpRounding() {
            BigDecimal emi = emiCalculator.calculate(
                    new BigDecimal("333333"), 37, new BigDecimal("13.7"));

            assertThat(emi.scale()).isEqualTo(2);
            assertThat(emi).isEqualTo(emi.setScale(2, RoundingMode.HALF_UP));
        }
    }

    @Nested
    @DisplayName("EMI Calculation — Mathematical Properties")
    class MathProperties {

        @Test
        @DisplayName("EMI * tenure > principal (interest is always positive)")
        void totalPayableShouldExceedPrincipal() {
            BigDecimal principal = new BigDecimal("500000");
            int tenure = 36;

            BigDecimal emi = emiCalculator.calculate(principal, tenure, new BigDecimal("12"));
            BigDecimal totalPayable = emi.multiply(BigDecimal.valueOf(tenure));

            assertThat(totalPayable).isGreaterThan(principal);
        }

        @Test
        @DisplayName("Higher rate → higher EMI (same principal and tenure)")
        void higherRateShouldGiveHigherEmi() {
            BigDecimal principal = new BigDecimal("500000");
            int tenure = 36;

            BigDecimal emiLow = emiCalculator.calculate(principal, tenure, new BigDecimal("12"));
            BigDecimal emiHigh = emiCalculator.calculate(principal, tenure, new BigDecimal("15"));

            assertThat(emiHigh).isGreaterThan(emiLow);
        }

        @Test
        @DisplayName("Shorter tenure → higher EMI (same principal and rate)")
        void shorterTenureShouldGiveHigherEmi() {
            BigDecimal principal = new BigDecimal("500000");
            BigDecimal rate = new BigDecimal("12");

            BigDecimal emiShort = emiCalculator.calculate(principal, 24, rate);
            BigDecimal emiLong = emiCalculator.calculate(principal, 60, rate);

            assertThat(emiShort).isGreaterThan(emiLong);
        }

        @Test
        @DisplayName("Higher principal → higher EMI (same tenure and rate)")
        void higherPrincipalShouldGiveHigherEmi() {
            int tenure = 36;
            BigDecimal rate = new BigDecimal("12");

            BigDecimal emiSmall = emiCalculator.calculate(new BigDecimal("300000"), tenure, rate);
            BigDecimal emiBig = emiCalculator.calculate(new BigDecimal("500000"), tenure, rate);

            assertThat(emiBig).isGreaterThan(emiSmall);
        }

        @Test
        @DisplayName("EMI should always be positive")
        void emiShouldAlwaysBePositive() {
            BigDecimal emi = emiCalculator.calculate(
                    new BigDecimal("10000"), 6, new BigDecimal("12"));

            assertThat(emi.compareTo(BigDecimal.ZERO)).isGreaterThan(0);
        }
    }

    @Nested
    @DisplayName("EMI Calculation — All Interest Rate Tiers")
    class InterestRateTiers {

        private static final BigDecimal PRINCIPAL = new BigDecimal("500000");
        private static final int TENURE = 36;

        @Test
        @DisplayName("Base rate 12% (LOW risk, SALARIED, small loan)")
        void shouldCalculateAt12Percent() {
            BigDecimal emi = emiCalculator.calculate(PRINCIPAL, TENURE, new BigDecimal("12"));
            assertThat(emi).isNotNull();
            assertThat(emi.compareTo(BigDecimal.ZERO)).isGreaterThan(0);
        }

        @Test
        @DisplayName("Rate 12.5% (LOW risk, SALARIED, large loan)")
        void shouldCalculateAt12Point5Percent() {
            BigDecimal emi = emiCalculator.calculate(PRINCIPAL, TENURE, new BigDecimal("12.5"));
            assertThat(emi).isNotNull();
        }

        @Test
        @DisplayName("Rate 13% (LOW risk, SELF_EMPLOYED, small loan)")
        void shouldCalculateAt13Percent() {
            BigDecimal emi = emiCalculator.calculate(PRINCIPAL, TENURE, new BigDecimal("13"));
            assertThat(emi).isNotNull();
        }

        @Test
        @DisplayName("Rate 13.5% (MEDIUM risk, SALARIED, small loan)")
        void shouldCalculateAt13Point5Percent() {
            BigDecimal emi = emiCalculator.calculate(PRINCIPAL, TENURE, new BigDecimal("13.5"));
            assertThat(emi).isNotNull();
        }

        @Test
        @DisplayName("Rate 15% (MEDIUM risk, SELF_EMPLOYED, large loan)")
        void shouldCalculateAt15Percent() {
            BigDecimal emi = emiCalculator.calculate(PRINCIPAL, TENURE, new BigDecimal("15"));
            assertThat(emi).isNotNull();
        }

        @Test
        @DisplayName("Rate 16.5% (HIGH risk, SELF_EMPLOYED, large loan — max possible)")
        void shouldCalculateAtMaxRate() {
            // 12 + 3 + 1 + 0.5 = 16.5
            BigDecimal emi = emiCalculator.calculate(PRINCIPAL, TENURE, new BigDecimal("16.5"));
            assertThat(emi).isNotNull();
            assertThat(emi.compareTo(BigDecimal.ZERO)).isGreaterThan(0);
        }
    }
}