package com.loan.service.service;

import com.loan.service.dto.request.LoginRequest;
import com.loan.service.dto.request.RegisterRequest;
import com.loan.service.dto.response.AuthResponse;

public interface AuthService {

    AuthResponse register(RegisterRequest request);

    AuthResponse login(LoginRequest request);
}
