package com.cleanbengaluru.dto;

import com.cleanbengaluru.entity.GarbageType;
import jakarta.validation.constraints.*;
import lombok.Data;

/**
 * Bound from multipart/form-data fields when a citizen submits a report.
 * The photo itself arrives as a separate MultipartFile part named "image".
 */
@Data
public class ReportRequest {

    @NotNull(message = "Garbage type is required")
    private GarbageType garbageType;

    @Size(max = 500, message = "Description must be at most 500 characters")
    private String description;

    /*
     * Bounded to the Bengaluru service area rather than the whole globe.
     * A report outside it cannot be dispatched to a ward crew, so accepting one
     * only produces a record that sits on the map in the wrong ocean. The
     * client places the pin on a map, so a value outside this box means the
     * coordinate was typed or left at a default.
     */
    @NotNull(message = "Latitude is required. Place the location pin on the map.")
    @DecimalMin(value = "12.70", message = "That location is outside the Bengaluru service area")
    @DecimalMax(value = "13.25", message = "That location is outside the Bengaluru service area")
    private Double latitude;

    @NotNull(message = "Longitude is required. Place the location pin on the map.")
    @DecimalMin(value = "77.30", message = "That location is outside the Bengaluru service area")
    @DecimalMax(value = "77.95", message = "That location is outside the Bengaluru service area")
    private Double longitude;

    @Size(max = 255)
    private String address;

    @Size(max = 100)
    private String areaName;

    @Min(value = 1, message = "Severity must be 1-5")
    @Max(value = 5, message = "Severity must be 1-5")
    private Integer severity = 3;

    private Boolean roadBlocked = false;

    /** When true, the citizen was warned about a possible duplicate and chose to file anyway. */
    private Boolean forceCreate = false;
}
