package com.loan.service.dto.response;

import lombok.*;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class ErrorResponse {

    private String message;
    private int status;
    private LocalDateTime timestamp;
    private List<String> errors;
}
