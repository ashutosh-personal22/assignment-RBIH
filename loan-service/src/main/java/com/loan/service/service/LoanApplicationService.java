package com.loan.service.service;

import com.loan.service.dto.request.LoanRequest;
import com.loan.service.dto.response.LoanApplicationResponse;

import java.util.List;

public interface LoanApplicationService {

    LoanApplicationResponse applyLoan(String email, LoanRequest request);

    List<LoanApplicationResponse> getUserLoans(String email);
}
