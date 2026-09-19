package com.cleanbengaluru.dto;

import com.cleanbengaluru.entity.BinStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class BinStatusUpdateRequest {

    @NotNull(message = "Status is required")
    private BinStatus status;

    /** When true, lastCollectionAt is stamped with "now" (the worker just emptied it). */
    private Boolean collected = false;
}
