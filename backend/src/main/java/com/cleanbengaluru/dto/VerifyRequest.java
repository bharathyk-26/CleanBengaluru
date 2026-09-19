package com.cleanbengaluru.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class VerifyRequest {

    /** true = "YES, CLEANED"  ->  VERIFIED -> CLOSED
     *  false = "NO, NOT PROPERLY CLEANED"  ->  REOPENED */
    @NotNull(message = "Please answer whether the area was cleaned")
    private Boolean cleaned;

    @Size(max = 400)
    private String reason;
}
