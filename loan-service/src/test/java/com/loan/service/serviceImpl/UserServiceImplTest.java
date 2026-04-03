package com.loan.service.serviceImpl;

import com.loan.service.domain.entity.User;
import com.loan.service.domain.enums.EmploymentType;
import com.loan.service.dto.request.UpdateUserProfileRequest;
import com.loan.service.dto.response.UserResponse;
import com.loan.service.exception.ResourceNotFoundException;
import com.loan.service.repository.UserRepository;
import com.loan.service.service.impl.UserServiceImpl;
import org.junit.jupiter.api.BeforeEach;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock private UserRepository userRepository;

    @InjectMocks
    private UserServiceImpl userService;

    private User testUser;
    private static final String TEST_EMAIL = "test@example.com";

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(UUID.randomUUID());
        testUser.setFullName("Ashutosh Kumar");
        testUser.setEmail(TEST_EMAIL);
        testUser.setEmploymentType(EmploymentType.SALARIED);
        testUser.setMonthlyIncome(new BigDecimal("75000"));
        testUser.setCreditScore((short) 750);
    }

    @Nested
    @DisplayName("getProfile")
    class GetProfile {

        @Test
        @DisplayName("Returns user profile for valid email")
        void shouldReturnProfile() {
            when(userRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.of(testUser));

            UserResponse response = userService.getProfile(TEST_EMAIL);

            assertThat(response.getId()).isEqualTo(testUser.getId());
            assertThat(response.getFullName()).isEqualTo("Ashutosh Kumar");
            assertThat(response.getEmail()).isEqualTo(TEST_EMAIL);
            assertThat(response.getEmploymentType()).isEqualTo(EmploymentType.SALARIED);
            assertThat(response.getMonthlyIncome()).isEqualByComparingTo(new BigDecimal("75000"));
            assertThat(response.getCreditScore()).isEqualTo((short) 750);
        }

        @Test
        @DisplayName("Throws ResourceNotFoundException for unknown email")
        void shouldThrowWhenUserNotFound() {
            when(userRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userService.getProfile("unknown@example.com"))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("User not found");
        }
    }

    @Nested
    @DisplayName("updateProfile")
    class UpdateProfile {

        @Test
        @DisplayName("Updates fullName when provided")
        void shouldUpdateFullName() {
            when(userRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.of(testUser));
            when(userRepository.save(any(User.class))).thenReturn(testUser);

            UpdateUserProfileRequest request = new UpdateUserProfileRequest();
            request.setFullName("Updated Name");

            UserResponse response = userService.updateProfile(TEST_EMAIL, request);

            assertThat(testUser.getFullName()).isEqualTo("Updated Name");
            verify(userRepository).save(testUser);
        }

        @Test
        @DisplayName("Updates employmentType when provided")
        void shouldUpdateEmploymentType() {
            when(userRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.of(testUser));
            when(userRepository.save(any(User.class))).thenReturn(testUser);

            UpdateUserProfileRequest request = new UpdateUserProfileRequest();
            request.setEmploymentType(EmploymentType.SELF_EMPLOYED);

            userService.updateProfile(TEST_EMAIL, request);

            assertThat(testUser.getEmploymentType()).isEqualTo(EmploymentType.SELF_EMPLOYED);
        }

        @Test
        @DisplayName("Updates monthlyIncome when provided")
        void shouldUpdateMonthlyIncome() {
            when(userRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.of(testUser));
            when(userRepository.save(any(User.class))).thenReturn(testUser);

            UpdateUserProfileRequest request = new UpdateUserProfileRequest();
            request.setMonthlyIncome(new BigDecimal("100000"));

            userService.updateProfile(TEST_EMAIL, request);

            assertThat(testUser.getMonthlyIncome()).isEqualByComparingTo(new BigDecimal("100000"));
        }

        @Test
        @DisplayName("Updates phoneNumber when provided")
        void shouldUpdatePhoneNumber() {
            when(userRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.of(testUser));
            when(userRepository.save(any(User.class))).thenReturn(testUser);

            UpdateUserProfileRequest request = new UpdateUserProfileRequest();
            request.setPhoneNumber("+919876543210");

            userService.updateProfile(TEST_EMAIL, request);

            assertThat(testUser.getPhoneNumber()).isEqualTo("+919876543210");
        }

        @Test
        @DisplayName("Does not overwrite fields when null in request")
        void shouldNotOverwriteNullFields() {
            when(userRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.of(testUser));
            when(userRepository.save(any(User.class))).thenReturn(testUser);

            UpdateUserProfileRequest request = new UpdateUserProfileRequest();
            // All fields null — nothing should change

            userService.updateProfile(TEST_EMAIL, request);

            assertThat(testUser.getFullName()).isEqualTo("Ashutosh Kumar");
            assertThat(testUser.getEmploymentType()).isEqualTo(EmploymentType.SALARIED);
            assertThat(testUser.getMonthlyIncome()).isEqualByComparingTo(new BigDecimal("75000"));
        }

        @Test
        @DisplayName("Throws ResourceNotFoundException for unknown email")
        void shouldThrowWhenUserNotFound() {
            when(userRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());

            UpdateUserProfileRequest request = new UpdateUserProfileRequest();
            request.setFullName("New Name");

            assertThatThrownBy(() -> userService.updateProfile("unknown@example.com", request))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }
}