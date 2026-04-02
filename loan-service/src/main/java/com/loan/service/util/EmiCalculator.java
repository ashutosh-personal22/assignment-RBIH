package com.loan.service.util;

import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

@Component
public class EmiCalculator {

    private static final MathContext MC = new MathContext(10, RoundingMode.HALF_UP);

    public BigDecimal calculate(BigDecimal principal,
                                int tenureMonths,
                                BigDecimal annualInterestRate) {

        // R = annual rate / 12 / 100
        BigDecimal monthlyRate = annualInterestRate
                .divide(BigDecimal.valueOf(12), MC)
                .divide(BigDecimal.valueOf(100), MC);

        // (1 + R)
        BigDecimal onePlusR = BigDecimal.ONE.add(monthlyRate);

        // (1 + R)^N
        BigDecimal power = onePlusR.pow(tenureMonths, MC);

        // EMI = [P × R × (1+R)^N] / [(1+R)^N – 1]
        BigDecimal numerator = principal.multiply(monthlyRate).multiply(power, MC);
        BigDecimal denominator = power.subtract(BigDecimal.ONE, MC);

        return numerator.divide(denominator, 2, RoundingMode.HALF_UP);
    }
}
