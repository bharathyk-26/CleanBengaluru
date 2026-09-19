package com.cleanbengaluru.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AssignRequest {

    @NotNull(message = "Worker id is required")
    private Long workerId;
}
