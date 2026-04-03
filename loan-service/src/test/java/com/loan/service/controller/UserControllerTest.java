package com.loan.service.controller;

import com.loan.service.domain.enums.EmploymentType;
import com.loan.service.dto.response.UserResponse;
import com.loan.service.exception.ResourceNotFoundException;
import com.loan.service.security.JwtAuthenticationFilter;
import com.loan.service.security.JwtTokenProvider;
import com.loan.service.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = UserController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = JwtAuthenticationFilter.class
        )
)
@AutoConfigureMockMvc(addFilters = false)
class UserControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @Nested
    @DisplayName("GET /api/users/me")
    class GetProfile {

        @Test
        @DisplayName("Returns 200 with user profile")
        @WithMockUser(username = "test@example.com")
        void shouldReturnUserProfile() throws Exception {
            UserResponse response = UserResponse.builder()
                    .id(UUID.randomUUID())
                    .fullName("Ashutosh Kumar")
                    .email("test@example.com")
                    .employmentType(EmploymentType.SALARIED)
                    .monthlyIncome(new BigDecimal("75000"))
                    .creditScore((short) 750)
                    .build();

            when(userService.getProfile(any())).thenReturn(response);

            mockMvc.perform(get("/api/users/me"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.fullName").value("Ashutosh Kumar"))
                    .andExpect(jsonPath("$.email").value("test@example.com"))
                    .andExpect(jsonPath("$.employmentType").value("SALARIED"))
                    .andExpect(jsonPath("$.monthlyIncome").value(75000))
                    .andExpect(jsonPath("$.creditScore").value(750));
        }

        @Test
        @DisplayName("Returns 404 when user not found")
        @WithMockUser(username = "unknown@example.com")
        void shouldReturn404WhenUserNotFound() throws Exception {
            when(userService.getProfile(any()))
                    .thenThrow(new ResourceNotFoundException("User not found"));

            mockMvc.perform(get("/api/users/me"))
                    .andExpect(status().isNotFound());
        }
    }
}