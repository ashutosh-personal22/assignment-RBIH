package com.loan.service.dto.response;

import com.loan.service.domain.enums.ApplicationStatus;
import com.loan.service.domain.enums.RiskBand;
import lombok.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
@AllArgsConstructor
public class LoanApplicationResponse {

    private UUID applicationId;
    private ApplicationStatus status;
    private RiskBand riskBand;
    private Offer offer;
    private List<String> rejectionReasons;

    @Getter
    @Builder
    public static class Offer {
        private BigDecimal interestRate;
        private Integer tenureMonths;
        private BigDecimal emi;
        private BigDecimal totalPayable;
    }

}
