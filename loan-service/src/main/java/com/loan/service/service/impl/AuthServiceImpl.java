package com.loan.service.service.impl;

import com.loan.service.domain.entity.User;
import com.loan.service.dto.request.LoginRequest;
import com.loan.service.dto.request.RegisterRequest;
import com.loan.service.dto.response.AuthResponse;
import com.loan.service.exception.BadRequestException;
import com.loan.service.exception.ResourceNotFoundException;
import com.loan.service.exception.UnauthorizedException;
import com.loan.service.repository.UserRepository;
import com.loan.service.security.JwtTokenProvider;
import com.loan.service.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    @Override
    public AuthResponse register(RegisterRequest request) {

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BadRequestException("Email already registered");
        }

        User user = new User();
        user.setFullName(request.getFullName());
        user.setEmail(request.getEmail());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setEmploymentType(request.getEmploymentType());
        user.setMonthlyIncome(request.getMonthlyIncome());
        user.setCreditScore(request.getCreditScore());
        user.setDateOfBirth(request.getDateOfBirth());

        userRepository.save(user);

        String token = jwtTokenProvider.generateToken(user.getEmail());

        return new AuthResponse(token);
    }

    @Override
    public AuthResponse login(LoginRequest request) {

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new UnauthorizedException("Invalid credentials");
        }

        String token = jwtTokenProvider.generateToken(user.getEmail());

        return new AuthResponse(token);
    }
}
