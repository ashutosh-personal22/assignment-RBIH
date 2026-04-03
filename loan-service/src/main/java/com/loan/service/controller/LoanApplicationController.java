package com.loan.service.controller;

import com.loan.service.dto.request.LoanRequest;
import com.loan.service.dto.response.LoanApplicationResponse;
import com.loan.service.service.LoanApplicationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/applications")
@RequiredArgsConstructor
public class LoanApplicationController {

    private final LoanApplicationService loanApplicationService;

    @PostMapping
    public ResponseEntity<LoanApplicationResponse> createApplication(
            @AuthenticationPrincipal String email,
            @Valid @RequestBody LoanRequest request) {

        LoanApplicationResponse response = loanApplicationService.applyLoan(email, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<LoanApplicationResponse>> getUserLoans(
            @AuthenticationPrincipal String email) {

        List<LoanApplicationResponse> loans = loanApplicationService.getUserLoans(email);
        return ResponseEntity.ok(loans);
    }
}