package com.loan.service.dto.request;

import com.loan.service.domain.enums.EmploymentType;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class UpdateUserProfileRequest {

    private String fullName;

    private String phoneNumber;

    private EmploymentType employmentType;

    private BigDecimal monthlyIncome;
}
