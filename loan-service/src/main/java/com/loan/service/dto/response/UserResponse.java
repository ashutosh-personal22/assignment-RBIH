package com.loan.service.dto.response;

import com.loan.service.domain.enums.EmploymentType;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Builder
public class UserResponse {

    private UUID id;
    private String fullName;
    private String email;
    private EmploymentType employmentType;
    private BigDecimal monthlyIncome;
    private Short creditScore;
}
