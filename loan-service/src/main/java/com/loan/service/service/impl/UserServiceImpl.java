package com.loan.service.service.impl;

import com.loan.service.domain.entity.User;
import com.loan.service.dto.request.UpdateUserProfileRequest;
import com.loan.service.dto.response.UserResponse;
import com.loan.service.exception.ResourceNotFoundException;
import com.loan.service.repository.UserRepository;
import com.loan.service.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;

    @Override
    public UserResponse getProfile(String email) {

        User user = getUserByEmail(email);

        return mapToResponse(user);
    }

    @Override
    public UserResponse updateProfile(String email, UpdateUserProfileRequest request) {

        User user = getUserByEmail(email);

        if (request.getFullName() != null) {
            user.setFullName(request.getFullName());
        }

        if (request.getPhoneNumber() != null) {
            user.setPhoneNumber(request.getPhoneNumber());
        }

        if (request.getEmploymentType() != null) {
            user.setEmploymentType(request.getEmploymentType());
        }

        if (request.getMonthlyIncome() != null) {
            user.setMonthlyIncome(request.getMonthlyIncome());
        }

        userRepository.save(user);

        return mapToResponse(user);
    }

    private User getUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private UserResponse mapToResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .employmentType(user.getEmploymentType())
                .monthlyIncome(user.getMonthlyIncome())
                .creditScore(user.getCreditScore())
                .build();
    }
}
