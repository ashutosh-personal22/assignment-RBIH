package com.loan.service.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loan.service.domain.enums.ApplicationStatus;
import com.loan.service.domain.enums.RiskBand;
import com.loan.service.dto.response.LoanApplicationResponse;
import com.loan.service.security.JwtAuthenticationFilter;
import com.loan.service.security.JwtTokenProvider;
import com.loan.service.service.LoanApplicationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = LoanApplicationController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = JwtAuthenticationFilter.class
        )
)
@AutoConfigureMockMvc(addFilters = false)
class LoanApplicationControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean
    private LoanApplicationService loanApplicationService;
    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    private static final String APPLICATIONS_URL = "/applications";

    private String validRequestJson() {
        return """
                {
                  "applicant": {
                    "name": "Ashutosh Kumar",
                    "age": 30,
                    "monthlyIncome": 75000,
                    "employmentType": "SALARIED",
                    "creditScore": 750
                  },
                  "loan": {
                    "amount": 500000,
                    "tenureMonths": 36,
                    "purpose": "PERSONAL"
                  }
                }
                """;
    }

    private LoanApplicationResponse approvedResponse() {
        return LoanApplicationResponse.builder()
                .applicationId(UUID.randomUUID())
                .status(ApplicationStatus.APPROVED)
                .riskBand(RiskBand.LOW)
                .offer(LoanApplicationResponse.Offer.builder()
                        .interestRate(new BigDecimal("12"))
                        .tenureMonths(36)
                        .emi(new BigDecimal("16607.15"))
                        .totalPayable(new BigDecimal("597857.40"))
                        .build())
                .build();
    }

    private LoanApplicationResponse rejectedResponse(List<String> reasons) {
        return LoanApplicationResponse.builder()
                .applicationId(UUID.randomUUID())
                .status(ApplicationStatus.REJECTED)
                .riskBand(null)
                .rejectionReasons(reasons)
                .build();
    }

    @Nested
    @DisplayName("POST /applications — Approved")
    class CreateApplicationApproved {

        @Test
        @DisplayName("Returns 201 with APPROVED status and offer")
        @WithMockUser(username = "test@example.com")
        void shouldReturnCreatedWithApprovedResponse() throws Exception {
            LoanApplicationResponse response = approvedResponse();
            when(loanApplicationService.applyLoan(any(), any())).thenReturn(response);

            mockMvc.perform(post(APPLICATIONS_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validRequestJson()))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.applicationId").isNotEmpty())
                    .andExpect(jsonPath("$.status").value("APPROVED"))
                    .andExpect(jsonPath("$.riskBand").value("LOW"))
                    .andExpect(jsonPath("$.offer").isNotEmpty())
                    .andExpect(jsonPath("$.offer.interestRate").value(12))
                    .andExpect(jsonPath("$.offer.tenureMonths").value(36))
                    .andExpect(jsonPath("$.offer.emi").value(16607.15))
                    .andExpect(jsonPath("$.offer.totalPayable").value(597857.40))
                    .andExpect(jsonPath("$.rejectionReasons").doesNotExist());
        }
    }

    @Nested
    @DisplayName("POST /applications — Rejected")
    class CreateApplicationRejected {

        @Test
        @DisplayName("Returns 201 with REJECTED status and single reason")
        @WithMockUser(username = "test@example.com")
        void shouldReturnRejectedWithSingleReason() throws Exception {
            LoanApplicationResponse response = rejectedResponse(List.of("LOW_CREDIT_SCORE"));
            when(loanApplicationService.applyLoan(any(), any())).thenReturn(response);

            String requestJson = """
                    {
                      "applicant": {
                        "name": "Test User",
                        "age": 30,
                        "monthlyIncome": 75000,
                        "employmentType": "SALARIED",
                        "creditScore": 580
                      },
                      "loan": {
                        "amount": 500000,
                        "tenureMonths": 36,
                        "purpose": "PERSONAL"
                      }
                    }
                    """;

            mockMvc.perform(post(APPLICATIONS_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestJson))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.status").value("REJECTED"))
                    .andExpect(jsonPath("$.riskBand").isEmpty())
                    .andExpect(jsonPath("$.offer").doesNotExist())
                    .andExpect(jsonPath("$.rejectionReasons", hasSize(1)))
                    .andExpect(jsonPath("$.rejectionReasons[0]").value("LOW_CREDIT_SCORE"));
        }

        @Test
        @DisplayName("Returns 201 with multiple rejection reasons")
        @WithMockUser(username = "test@example.com")
        void shouldReturnMultipleRejectionReasons() throws Exception {
            List<String> reasons = List.of("LOW_CREDIT_SCORE", "AGE_TENURE_LIMIT_EXCEEDED", "EMI_EXCEEDS_60_PERCENT");
            LoanApplicationResponse response = rejectedResponse(reasons);
            when(loanApplicationService.applyLoan(any(), any())).thenReturn(response);

            String requestJson = """
                    {
                      "applicant": {
                        "name": "Bad Applicant",
                        "age": 58,
                        "monthlyIncome": 20000,
                        "employmentType": "SELF_EMPLOYED",
                        "creditScore": 500
                      },
                      "loan": {
                        "amount": 4000000,
                        "tenureMonths": 120,
                        "purpose": "HOME"
                      }
                    }
                    """;

            mockMvc.perform(post(APPLICATIONS_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestJson))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.status").value("REJECTED"))
                    .andExpect(jsonPath("$.rejectionReasons", hasSize(3)))
                    .andExpect(jsonPath("$.rejectionReasons",
                            containsInAnyOrder("LOW_CREDIT_SCORE", "AGE_TENURE_LIMIT_EXCEEDED", "EMI_EXCEEDS_60_PERCENT")));
        }

        @Test
        @DisplayName("Returns 201 with EMI_EXCEEDS_50_PERCENT (Section 6 rejection)")
        @WithMockUser(username = "test@example.com")
        void shouldReturnSection6Rejection() throws Exception {
            LoanApplicationResponse response = rejectedResponse(List.of("EMI_EXCEEDS_50_PERCENT"));
            when(loanApplicationService.applyLoan(any(), any())).thenReturn(response);

            mockMvc.perform(post(APPLICATIONS_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validRequestJson()))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.status").value("REJECTED"))
                    .andExpect(jsonPath("$.rejectionReasons[0]").value("EMI_EXCEEDS_50_PERCENT"));
        }
    }

    @Nested
    @DisplayName("POST /applications — Validation (400)")
    class ValidationErrors {

        @Test
        @DisplayName("400 when age < 21")
        @WithMockUser(username = "test@example.com")
        void shouldReturn400WhenAgeBelow21() throws Exception {
            String json = """
                    {
                      "applicant": {
                        "name": "Young User",
                        "age": 18,
                        "monthlyIncome": 50000,
                        "employmentType": "SALARIED",
                        "creditScore": 700
                      },
                      "loan": {
                        "amount": 500000,
                        "tenureMonths": 36,
                        "purpose": "PERSONAL"
                      }
                    }
                    """;

            mockMvc.perform(post(APPLICATIONS_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("400 when age > 60")
        @WithMockUser(username = "test@example.com")
        void shouldReturn400WhenAgeAbove60() throws Exception {
            String json = """
                    {
                      "applicant": {
                        "name": "Old User",
                        "age": 65,
                        "monthlyIncome": 50000,
                        "employmentType": "SALARIED",
                        "creditScore": 700
                      },
                      "loan": {
                        "amount": 500000,
                        "tenureMonths": 36,
                        "purpose": "PERSONAL"
                      }
                    }
                    """;

            mockMvc.perform(post(APPLICATIONS_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("400 when credit score < 300")
        @WithMockUser(username = "test@example.com")
        void shouldReturn400WhenCreditScoreBelow300() throws Exception {
            String json = """
                    {
                      "applicant": {
                        "name": "Bad Score",
                        "age": 30,
                        "monthlyIncome": 50000,
                        "employmentType": "SALARIED",
                        "creditScore": 200
                      },
                      "loan": {
                        "amount": 500000,
                        "tenureMonths": 36,
                        "purpose": "PERSONAL"
                      }
                    }
                    """;

            mockMvc.perform(post(APPLICATIONS_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("400 when credit score > 900")
        @WithMockUser(username = "test@example.com")
        void shouldReturn400WhenCreditScoreAbove900() throws Exception {
            String json = """
                    {
                      "applicant": {
                        "name": "Too Good",
                        "age": 30,
                        "monthlyIncome": 50000,
                        "employmentType": "SALARIED",
                        "creditScore": 950
                      },
                      "loan": {
                        "amount": 500000,
                        "tenureMonths": 36,
                        "purpose": "PERSONAL"
                      }
                    }
                    """;

            mockMvc.perform(post(APPLICATIONS_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("400 when loan amount < 10,000")
        @WithMockUser(username = "test@example.com")
        void shouldReturn400WhenAmountTooLow() throws Exception {
            String json = """
                    {
                      "applicant": {
                        "name": "Test User",
                        "age": 30,
                        "monthlyIncome": 50000,
                        "employmentType": "SALARIED",
                        "creditScore": 700
                      },
                      "loan": {
                        "amount": 5000,
                        "tenureMonths": 36,
                        "purpose": "PERSONAL"
                      }
                    }
                    """;

            mockMvc.perform(post(APPLICATIONS_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("400 when loan amount > 50,00,000")
        @WithMockUser(username = "test@example.com")
        void shouldReturn400WhenAmountTooHigh() throws Exception {
            String json = """
                    {
                      "applicant": {
                        "name": "Test User",
                        "age": 30,
                        "monthlyIncome": 50000,
                        "employmentType": "SALARIED",
                        "creditScore": 700
                      },
                      "loan": {
                        "amount": 6000000,
                        "tenureMonths": 36,
                        "purpose": "PERSONAL"
                      }
                    }
                    """;

            mockMvc.perform(post(APPLICATIONS_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("400 when tenure < 6 months")
        @WithMockUser(username = "test@example.com")
        void shouldReturn400WhenTenureTooShort() throws Exception {
            String json = """
                    {
                      "applicant": {
                        "name": "Test User",
                        "age": 30,
                        "monthlyIncome": 50000,
                        "employmentType": "SALARIED",
                        "creditScore": 700
                      },
                      "loan": {
                        "amount": 500000,
                        "tenureMonths": 3,
                        "purpose": "PERSONAL"
                      }
                    }
                    """;

            mockMvc.perform(post(APPLICATIONS_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("400 when tenure > 360 months")
        @WithMockUser(username = "test@example.com")
        void shouldReturn400WhenTenureTooLong() throws Exception {
            String json = """
                    {
                      "applicant": {
                        "name": "Test User",
                        "age": 30,
                        "monthlyIncome": 50000,
                        "employmentType": "SALARIED",
                        "creditScore": 700
                      },
                      "loan": {
                        "amount": 500000,
                        "tenureMonths": 400,
                        "purpose": "PERSONAL"
                      }
                    }
                    """;

            mockMvc.perform(post(APPLICATIONS_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("400 when required fields are missing")
        @WithMockUser(username = "test@example.com")
        void shouldReturn400WhenFieldsMissing() throws Exception {
            String json = """
                    {
                      "applicant": {
                        "name": ""
                      },
                      "loan": {}
                    }
                    """;

            mockMvc.perform(post(APPLICATIONS_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("400 when request body is empty")
        @WithMockUser(username = "test@example.com")
        void shouldReturn400WhenBodyEmpty() throws Exception {
            mockMvc.perform(post(APPLICATIONS_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("400 when invalid employment type")
        @WithMockUser(username = "test@example.com")
        void shouldReturn400WhenInvalidEmploymentType() throws Exception {
            String json = """
                    {
                      "applicant": {
                        "name": "Test User",
                        "age": 30,
                        "monthlyIncome": 50000,
                        "employmentType": "FREELANCER",
                        "creditScore": 700
                      },
                      "loan": {
                        "amount": 500000,
                        "tenureMonths": 36,
                        "purpose": "PERSONAL"
                      }
                    }
                    """;

            mockMvc.perform(post(APPLICATIONS_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("400 when invalid loan purpose")
        @WithMockUser(username = "test@example.com")
        void shouldReturn400WhenInvalidPurpose() throws Exception {
            String json = """
                    {
                      "applicant": {
                        "name": "Test User",
                        "age": 30,
                        "monthlyIncome": 50000,
                        "employmentType": "SALARIED",
                        "creditScore": 700
                      },
                      "loan": {
                        "amount": 500000,
                        "tenureMonths": 36,
                        "purpose": "EDUCATION"
                      }
                    }
                    """;

            mockMvc.perform(post(APPLICATIONS_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("GET /applications")
    class GetUserLoans {

        @Test
        @DisplayName("Returns 200 with list of applications")
        @WithMockUser(username = "test@example.com")
        void shouldReturnListOfApplications() throws Exception {
            List<LoanApplicationResponse> loans = List.of(approvedResponse(), rejectedResponse(List.of("LOW_CREDIT_SCORE")));
            when(loanApplicationService.getUserLoans(any())).thenReturn(loans);

            mockMvc.perform(get(APPLICATIONS_URL))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(2)))
                    .andExpect(jsonPath("$[0].status").value("APPROVED"))
                    .andExpect(jsonPath("$[1].status").value("REJECTED"));
        }

        @Test
        @DisplayName("Returns 200 with empty list when no applications")
        @WithMockUser(username = "test@example.com")
        void shouldReturnEmptyList() throws Exception {
            when(loanApplicationService.getUserLoans(any())).thenReturn(List.of());

            mockMvc.perform(get(APPLICATIONS_URL))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(0)));
        }
    }

    @Nested
    @DisplayName("Section 7 — Response Format")
    class ResponseFormat {

        @Test
        @DisplayName("Approved response matches assignment format exactly")
        @WithMockUser(username = "test@example.com")
        void shouldMatchApprovedFormat() throws Exception {
            LoanApplicationResponse response = approvedResponse();
            when(loanApplicationService.applyLoan(any(), any())).thenReturn(response);

            mockMvc.perform(post(APPLICATIONS_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validRequestJson()))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.applicationId").isString())
                    .andExpect(jsonPath("$.status").isString())
                    .andExpect(jsonPath("$.riskBand").isString())
                    .andExpect(jsonPath("$.offer").isMap())
                    .andExpect(jsonPath("$.offer.interestRate").isNumber())
                    .andExpect(jsonPath("$.offer.tenureMonths").isNumber())
                    .andExpect(jsonPath("$.offer.emi").isNumber())
                    .andExpect(jsonPath("$.offer.totalPayable").isNumber());
        }

        @Test
        @DisplayName("Rejected response matches assignment format exactly")
        @WithMockUser(username = "test@example.com")
        void shouldMatchRejectedFormat() throws Exception {
            LoanApplicationResponse response = rejectedResponse(
                    List.of("EMI_EXCEEDS_60_PERCENT", "AGE_TENURE_LIMIT_EXCEEDED"));
            when(loanApplicationService.applyLoan(any(), any())).thenReturn(response);

            String json = """
                    {
                      "applicant": {
                        "name": "Bad Applicant",
                        "age": 58,
                        "monthlyIncome": 20000,
                        "employmentType": "SELF_EMPLOYED",
                        "creditScore": 500
                      },
                      "loan": {
                        "amount": 4000000,
                        "tenureMonths": 120,
                        "purpose": "HOME"
                      }
                    }
                    """;

            mockMvc.perform(post(APPLICATIONS_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.applicationId").isString())
                    .andExpect(jsonPath("$.status").value("REJECTED"))
                    .andExpect(jsonPath("$.riskBand").isEmpty())
                    .andExpect(jsonPath("$.rejectionReasons").isArray())
                    .andExpect(jsonPath("$.rejectionReasons", hasSize(2)));
        }
    }
}