package com.cleanbengaluru.dto;

import com.cleanbengaluru.entity.BinStatus;
import com.cleanbengaluru.entity.BinType;
import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class BinRequest {

    @NotBlank(message = "Bin code is required")
    @Size(max = 40)
    private String code;

    @NotBlank(message = "Location name is required")
    @Size(max = 200)
    private String locationName;

    @NotNull @DecimalMin("-90.0") @DecimalMax("90.0")
    private Double latitude;

    @NotNull @DecimalMin("-180.0") @DecimalMax("180.0")
    private Double longitude;

    @NotNull(message = "Bin type is required")
    private BinType binType;

    @NotNull @Min(10) @Max(10000)
    private Integer capacityLitres;

    private BinStatus status = BinStatus.NORMAL;

    @Size(max = 100)
    private String areaName;
}
