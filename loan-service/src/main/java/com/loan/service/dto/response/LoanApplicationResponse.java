package com.loan.service.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.loan.service.domain.enums.ApplicationStatus;
import com.loan.service.domain.enums.RiskBand;
import lombok.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LoanApplicationResponse {

    private UUID applicationId;
    private ApplicationStatus status;

    @JsonInclude(JsonInclude.Include.ALWAYS)
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
