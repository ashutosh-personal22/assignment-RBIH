package com.loan.service.dto.request;

import com.loan.service.domain.enums.EmploymentType;
import com.loan.service.domain.enums.LoanPurpose;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
public class LoanRequest {

    @Valid
    @NotNull
    private Applicant applicant;

    @Valid
    @NotNull
    private Loan loan;

    @Getter
    @Setter
    public static class Applicant {

        @NotBlank
        private String name;

        @Min(21)
        @Max(60)
        private Integer age;

        @NotNull
        @DecimalMin(value = "0", inclusive = false, message = "Monthly income must be greater than 0")
        private BigDecimal monthlyIncome;

        @NotNull
        private EmploymentType employmentType;

        @Min(300)
        @Max(900)
        private Short creditScore;

    }

    @Getter
    @Setter
    public static class Loan {

        @NotNull
        @DecimalMin("10000")
        @DecimalMax("5000000")
        private BigDecimal amount;

        @NotNull
        @Min(6)
        @Max(360)
        private Integer tenureMonths;

        @NotNull
        private LoanPurpose purpose;
    }
}

