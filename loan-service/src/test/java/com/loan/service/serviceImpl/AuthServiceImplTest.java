package com.loan.service.serviceImpl;

import com.loan.service.domain.entity.User;
import com.loan.service.domain.enums.EmploymentType;
import com.loan.service.dto.request.LoginRequest;
import com.loan.service.dto.request.RegisterRequest;
import com.loan.service.dto.response.AuthResponse;
import com.loan.service.exception.BadRequestException;
import com.loan.service.exception.ResourceNotFoundException;
import com.loan.service.exception.UnauthorizedException;
import com.loan.service.repository.UserRepository;
import com.loan.service.security.JwtTokenProvider;
import com.loan.service.service.impl.AuthServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtTokenProvider jwtTokenProvider;

    @InjectMocks
    private AuthServiceImpl authService;

    @Captor private ArgumentCaptor<User> userCaptor;

    @Nested
    @DisplayName("register")
    class Register {

        private RegisterRequest validRequest;

        @BeforeEach
        void setUp() {
            validRequest = new RegisterRequest();
            validRequest.setFullName("Ashutosh Kumar");
            validRequest.setEmail("ashutosh@example.com");
            validRequest.setPassword("secret123");
            validRequest.setEmploymentType(EmploymentType.SALARIED);
            validRequest.setMonthlyIncome(new BigDecimal("75000"));
            validRequest.setCreditScore((short) 750);
            validRequest.setDateOfBirth(LocalDate.of(1995, 6, 15));
        }

        @Test
        @DisplayName("Registers user and returns JWT token")
        void shouldRegisterAndReturnToken() {
            when(userRepository.existsByEmail("ashutosh@example.com")).thenReturn(false);
            when(passwordEncoder.encode("secret123")).thenReturn("hashed-password");
            when(userRepository.save(any(User.class))).thenAnswer(inv -> {
                User u = inv.getArgument(0);
                u.setId(UUID.randomUUID());
                return u;
            });
            when(jwtTokenProvider.generateToken("ashutosh@example.com")).thenReturn("jwt-token");

            AuthResponse response = authService.register(validRequest);

            assertThat(response.getToken()).isEqualTo("jwt-token");

            verify(userRepository).save(userCaptor.capture());
            User savedUser = userCaptor.getValue();
            assertThat(savedUser.getFullName()).isEqualTo("Ashutosh Kumar");
            assertThat(savedUser.getEmail()).isEqualTo("ashutosh@example.com");
            assertThat(savedUser.getPasswordHash()).isEqualTo("hashed-password");
            assertThat(savedUser.getEmploymentType()).isEqualTo(EmploymentType.SALARIED);
            assertThat(savedUser.getMonthlyIncome()).isEqualByComparingTo(new BigDecimal("75000"));
            assertThat(savedUser.getCreditScore()).isEqualTo((short) 750);
            assertThat(savedUser.getDateOfBirth()).isEqualTo(LocalDate.of(1995, 6, 15));
        }

        @Test
        @DisplayName("Throws BadRequestException when email already exists")
        void shouldThrowWhenEmailExists() {
            when(userRepository.existsByEmail("ashutosh@example.com")).thenReturn(true);

            assertThatThrownBy(() -> authService.register(validRequest))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("Email already registered");

            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("Password is encoded before saving")
        void shouldEncodePassword() {
            when(userRepository.existsByEmail(any())).thenReturn(false);
            when(passwordEncoder.encode("secret123")).thenReturn("$2a$10$encoded");
            when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(jwtTokenProvider.generateToken(any())).thenReturn("token");

            authService.register(validRequest);

            verify(userRepository).save(userCaptor.capture());
            assertThat(userCaptor.getValue().getPasswordHash()).isEqualTo("$2a$10$encoded");
            assertThat(userCaptor.getValue().getPasswordHash()).isNotEqualTo("secret123");
        }
    }

    @Nested
    @DisplayName("login")
    class Login {

        private User existingUser;
        private LoginRequest loginRequest;

        @BeforeEach
        void setUp() {
            existingUser = new User();
            existingUser.setId(UUID.randomUUID());
            existingUser.setEmail("ashutosh@example.com");
            existingUser.setPasswordHash("hashed-password");

            loginRequest = new LoginRequest();
            loginRequest.setEmail("ashutosh@example.com");
            loginRequest.setPassword("secret123");
        }

        @Test
        @DisplayName("Returns JWT token on successful login")
        void shouldReturnTokenOnSuccess() {
            when(userRepository.findByEmail("ashutosh@example.com"))
                    .thenReturn(Optional.of(existingUser));
            when(passwordEncoder.matches("secret123", "hashed-password")).thenReturn(true);
            when(jwtTokenProvider.generateToken("ashutosh@example.com")).thenReturn("jwt-token");

            AuthResponse response = authService.login(loginRequest);

            assertThat(response.getToken()).isEqualTo("jwt-token");
        }

        @Test
        @DisplayName("Throws ResourceNotFoundException when user not found")
        void shouldThrowWhenUserNotFound() {
            when(userRepository.findByEmail("ashutosh@example.com"))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.login(loginRequest))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("User not found");
        }

        @Test
        @DisplayName("Throws UnauthorizedException when password is wrong")
        void shouldThrowWhenPasswordWrong() {
            when(userRepository.findByEmail("ashutosh@example.com"))
                    .thenReturn(Optional.of(existingUser));
            when(passwordEncoder.matches("secret123", "hashed-password")).thenReturn(false);

            assertThatThrownBy(() -> authService.login(loginRequest))
                    .isInstanceOf(UnauthorizedException.class)
                    .hasMessage("Invalid credentials");

            verify(jwtTokenProvider, never()).generateToken(any());
        }
    }
}