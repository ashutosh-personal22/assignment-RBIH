package com.loan.service.mapper;

import com.loan.service.domain.entity.LoanApplication;
import com.loan.service.domain.entity.LoanOffer;
import com.loan.service.domain.enums.ApplicationStatus;
import com.loan.service.dto.response.LoanApplicationResponse;
import com.loan.service.exception.ResourceNotFoundException;
import com.loan.service.repository.LoanOfferRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class ObjectConverter {

    private  final  LoanOfferRepository offerRepo;


    public LoanApplicationResponse mapToResponse(LoanApplication app) {

        LoanApplicationResponse.LoanApplicationResponseBuilder builder =
                LoanApplicationResponse.builder()
                        .applicationId(app.getId())
                        .status(app.getStatus())
                        .riskBand(app.getRiskBand());

        if (app.getStatus() == ApplicationStatus.APPROVED) {
            LoanOffer offer = offerRepo.findByApplicationId(app.getId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Offer not found for application: " + app.getId()));

            builder.offer(
                    LoanApplicationResponse.Offer.builder()
                            .interestRate(offer.getInterestRate())
                            .tenureMonths(offer.getTenureMonths().intValue())
                            .emi(offer.getEmi())
                            .totalPayable(offer.getTotalPayable())
                            .build()
            );
        }

        if (app.getStatus() == ApplicationStatus.REJECTED) {
            builder.rejectionReasons(
                    app.getRejectionReasons() != null
                            ? List.of(app.getRejectionReasons())
                            : List.of()
            );
        }

        return builder.build();
    }
}
