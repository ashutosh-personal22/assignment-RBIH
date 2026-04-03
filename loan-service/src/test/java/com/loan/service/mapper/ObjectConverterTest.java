package com.loan.service.mapper;

import com.loan.service.domain.entity.LoanApplication;
import com.loan.service.domain.entity.LoanOffer;
import com.loan.service.domain.enums.ApplicationStatus;
import com.loan.service.domain.enums.RiskBand;
import com.loan.service.dto.response.LoanApplicationResponse;
import com.loan.service.exception.ResourceNotFoundException;
import com.loan.service.repository.LoanOfferRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ObjectConverterTest {

    @Mock
    private LoanOfferRepository offerRepo;

    @InjectMocks
    private ObjectConverter objectConverter;

    private LoanApplication buildApprovedApplication() {
        LoanApplication app = new LoanApplication();
        app.setId(UUID.randomUUID());
        app.setStatus(ApplicationStatus.APPROVED);
        app.setRiskBand(RiskBand.LOW);
        return app;
    }

    private LoanApplication buildRejectedApplication(String... reasons) {
        LoanApplication app = new LoanApplication();
        app.setId(UUID.randomUUID());
        app.setStatus(ApplicationStatus.REJECTED);
        app.setRiskBand(null);
        app.setRejectionReasons(reasons);
        return app;
    }

    private LoanOffer buildOffer(UUID applicationId) {
        LoanOffer offer = new LoanOffer();
        offer.setInterestRate(new BigDecimal("13.5"));
        offer.setTenureMonths((short) 36);
        offer.setEmi(new BigDecimal("16607.15"));
        offer.setTotalPayable(new BigDecimal("597857.40"));
        return offer;
    }

    @Nested
    @DisplayName("Mapping APPROVED applications")
    class ApprovedMapping {

        @Test
        @DisplayName("Maps approved application with offer correctly")
        void shouldMapApprovedWithOffer() {
            LoanApplication app = buildApprovedApplication();
            LoanOffer offer = buildOffer(app.getId());

            when(offerRepo.findByApplicationId(app.getId())).thenReturn(Optional.of(offer));

            LoanApplicationResponse response = objectConverter.mapToResponse(app);

            assertThat(response.getApplicationId()).isEqualTo(app.getId());
            assertThat(response.getStatus()).isEqualTo(ApplicationStatus.APPROVED);
            assertThat(response.getRiskBand()).isEqualTo(RiskBand.LOW);
            assertThat(response.getOffer()).isNotNull();
            assertThat(response.getOffer().getInterestRate()).isEqualByComparingTo(new BigDecimal("13.5"));
            assertThat(response.getOffer().getTenureMonths()).isEqualTo(36);
            assertThat(response.getOffer().getEmi()).isEqualByComparingTo(new BigDecimal("16607.15"));
            assertThat(response.getOffer().getTotalPayable()).isEqualByComparingTo(new BigDecimal("597857.40"));
        }

        @Test
        @DisplayName("Throws ResourceNotFoundException when offer missing for approved app")
        void shouldThrowWhenOfferMissing() {
            LoanApplication app = buildApprovedApplication();

            when(offerRepo.findByApplicationId(app.getId())).thenReturn(Optional.empty());

            assertThatThrownBy(() -> objectConverter.mapToResponse(app))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Offer not found for application");
        }
    }

    @Nested
    @DisplayName("Mapping REJECTED applications")
    class RejectedMapping {

        @Test
        @DisplayName("Maps rejected application with single reason")
        void shouldMapRejectedWithSingleReason() {
            LoanApplication app = buildRejectedApplication("LOW_CREDIT_SCORE");

            LoanApplicationResponse response = objectConverter.mapToResponse(app);

            assertThat(response.getApplicationId()).isEqualTo(app.getId());
            assertThat(response.getStatus()).isEqualTo(ApplicationStatus.REJECTED);
            assertThat(response.getRiskBand()).isNull();
            assertThat(response.getOffer()).isNull();
            assertThat(response.getRejectionReasons()).containsExactly("LOW_CREDIT_SCORE");
        }

        @Test
        @DisplayName("Maps rejected application with multiple reasons")
        void shouldMapRejectedWithMultipleReasons() {
            LoanApplication app = buildRejectedApplication(
                    "LOW_CREDIT_SCORE", "AGE_TENURE_LIMIT_EXCEEDED", "EMI_EXCEEDS_60_PERCENT");

            LoanApplicationResponse response = objectConverter.mapToResponse(app);

            assertThat(response.getRejectionReasons())
                    .containsExactly("LOW_CREDIT_SCORE", "AGE_TENURE_LIMIT_EXCEEDED", "EMI_EXCEEDS_60_PERCENT");
        }

        @Test
        @DisplayName("Maps rejected application with null rejection reasons as empty list")
        void shouldMapNullReasonsAsEmptyList() {
            LoanApplication app = new LoanApplication();
            app.setId(UUID.randomUUID());
            app.setStatus(ApplicationStatus.REJECTED);
            app.setRiskBand(null);
            app.setRejectionReasons(null);

            LoanApplicationResponse response = objectConverter.mapToResponse(app);

            assertThat(response.getRejectionReasons()).isEmpty();
        }
    }
}