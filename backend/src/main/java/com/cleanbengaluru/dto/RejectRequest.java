package com.cleanbengaluru.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RejectRequest {

    @NotBlank(message = "A reason is required")
    @Size(max = 300)
    private String reason;
}
