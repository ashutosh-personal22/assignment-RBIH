package com.loan.service.service;

import com.loan.service.dto.request.UpdateUserProfileRequest;
import com.loan.service.dto.response.UserResponse;

public interface UserService {

    UserResponse getProfile(String email);

    UserResponse updateProfile(String email, UpdateUserProfileRequest request);
}
