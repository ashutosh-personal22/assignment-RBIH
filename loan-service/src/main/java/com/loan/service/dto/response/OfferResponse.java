package com.loan.service.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class OfferResponse {

    private BigDecimal interestRate;
    private Short tenureMonths;
    private BigDecimal emi;
    private BigDecimal totalPayable;
}
