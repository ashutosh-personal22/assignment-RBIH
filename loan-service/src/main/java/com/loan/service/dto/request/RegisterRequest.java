package com.loan.service.dto.request;

import com.loan.service.domain.enums.EmploymentType;
import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
public class RegisterRequest {

    @NotBlank
    private String fullName;

    @Email
    @NotBlank
    private String email;

    @NotBlank
    @Size(min = 6)
    private String password;

    @NotNull
    private EmploymentType employmentType;

    @NotNull
    @DecimalMin("1.0")
    private BigDecimal monthlyIncome;

    @NotNull
    @Min(300)
    @Max(900)
    private Short creditScore;

    @NotNull
    private LocalDate dateOfBirth;
}
