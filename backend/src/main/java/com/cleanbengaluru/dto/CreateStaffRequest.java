package com.cleanbengaluru.dto;

import com.cleanbengaluru.entity.Role;
import jakarta.validation.constraints.*;
import lombok.Data;

/** Admin-only: create a WORKER or another ADMIN. */
@Data
public class CreateStaffRequest {

    @NotBlank @Size(min = 3, max = 100)
    private String name;

    @NotBlank @Email
    private String email;

    @NotBlank @Size(min = 6, max = 50)
    private String password;

    @Pattern(regexp = "^$|^[0-9]{10}$", message = "Phone must be 10 digits")
    private String phone;

    @Size(max = 100)
    private String areaName;

    @NotNull(message = "Role is required")
    private Role role;
}
