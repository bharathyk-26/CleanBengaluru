package com.cleanbengaluru.dto;

import com.cleanbengaluru.entity.Role;
import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class RegisterRequest {

    @NotBlank(message = "Name is required")
    @Size(min = 3, max = 100, message = "Name must be 3-100 characters")
    private String name;

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 6, max = 50, message = "Password must be at least 6 characters")
    private String password;

    @Pattern(regexp = "^$|^[0-9]{10}$", message = "Phone must be 10 digits")
    private String phone;

    @Size(max = 100)
    private String areaName;

    /**
     * Public self-registration is only allowed for CITIZEN.
     * Workers and admins are created by an admin (see AdminController).
     */
    private Role role = Role.CITIZEN;
}
