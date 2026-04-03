package com.loan.service.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loan.service.dto.response.AuthResponse;
import com.loan.service.exception.BadRequestException;
import com.loan.service.exception.ResourceNotFoundException;
import com.loan.service.exception.UnauthorizedException;
import com.loan.service.security.JwtAuthenticationFilter;
import com.loan.service.security.JwtTokenProvider;
import com.loan.service.service.AuthService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = AuthController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = JwtAuthenticationFilter.class
        )
)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @Nested
    @DisplayName("POST /api/auth/register")
    class Register {

        @Test
        @DisplayName("Returns 200 with token on successful registration")
        void shouldReturnTokenOnSuccess() throws Exception {
            when(authService.register(any())).thenReturn(new AuthResponse("jwt-token-123"));

            String json = """
                    {
                      "fullName": "Ashutosh Kumar",
                      "email": "ashutosh@example.com",
                      "password": "secret123",
                      "employmentType": "SALARIED",
                      "monthlyIncome": 75000,
                      "creditScore": 750,
                      "dateOfBirth": "1995-06-15"
                    }
                    """;

            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.token").value("jwt-token-123"));
        }

        @Test
        @DisplayName("Returns 400 when email already registered")
        void shouldReturn400WhenEmailExists() throws Exception {
            when(authService.register(any()))
                    .thenThrow(new BadRequestException("Email already registered"));

            String json = """
                    {
                      "fullName": "Ashutosh Kumar",
                      "email": "existing@example.com",
                      "password": "secret123",
                      "employmentType": "SALARIED",
                      "monthlyIncome": 75000,
                      "creditScore": 750,
                      "dateOfBirth": "1995-06-15"
                    }
                    """;

            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Returns 400 when email format is invalid")
        void shouldReturn400WhenEmailInvalid() throws Exception {
            String json = """
                    {
                      "fullName": "Test User",
                      "email": "not-an-email",
                      "password": "secret123",
                      "employmentType": "SALARIED",
                      "monthlyIncome": 75000,
                      "creditScore": 750,
                      "dateOfBirth": "1995-06-15"
                    }
                    """;

            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Returns 400 when password too short")
        void shouldReturn400WhenPasswordShort() throws Exception {
            String json = """
                    {
                      "fullName": "Test User",
                      "email": "test@example.com",
                      "password": "12345",
                      "employmentType": "SALARIED",
                      "monthlyIncome": 75000,
                      "creditScore": 750,
                      "dateOfBirth": "1995-06-15"
                    }
                    """;

            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Returns 400 when required fields missing")
        void shouldReturn400WhenFieldsMissing() throws Exception {
            String json = """
                    {
                      "email": "test@example.com"
                    }
                    """;

            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Returns 400 when credit score out of range")
        void shouldReturn400WhenCreditScoreOutOfRange() throws Exception {
            String json = """
                    {
                      "fullName": "Test User",
                      "email": "test@example.com",
                      "password": "secret123",
                      "employmentType": "SALARIED",
                      "monthlyIncome": 75000,
                      "creditScore": 200,
                      "dateOfBirth": "1995-06-15"
                    }
                    """;

            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("POST /api/auth/login")
    class Login {

        @Test
        @DisplayName("Returns 200 with token on successful login")
        void shouldReturnTokenOnSuccess() throws Exception {
            when(authService.login(any())).thenReturn(new AuthResponse("jwt-token-456"));

            String json = """
                    {
                      "email": "ashutosh@example.com",
                      "password": "secret123"
                    }
                    """;

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.token").value("jwt-token-456"));
        }

        @Test
        @DisplayName("Returns 404 when user not found")
        void shouldReturn404WhenUserNotFound() throws Exception {
            when(authService.login(any()))
                    .thenThrow(new ResourceNotFoundException("User not found"));

            String json = """
                    {
                      "email": "unknown@example.com",
                      "password": "secret123"
                    }
                    """;

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Returns 401 when password is wrong")
        void shouldReturn401WhenWrongPassword() throws Exception {
            when(authService.login(any()))
                    .thenThrow(new UnauthorizedException("Invalid credentials"));

            String json = """
                    {
                      "email": "ashutosh@example.com",
                      "password": "wrongpassword"
                    }
                    """;

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Returns 400 when email is blank")
        void shouldReturn400WhenEmailBlank() throws Exception {
            String json = """
                    {
                      "email": "",
                      "password": "secret123"
                    }
                    """;

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Returns 400 when password is blank")
        void shouldReturn400WhenPasswordBlank() throws Exception {
            String json = """
                    {
                      "email": "test@example.com",
                      "password": ""
                    }
                    """;

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isBadRequest());
        }
    }
}