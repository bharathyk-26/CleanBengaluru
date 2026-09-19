package com.cleanbengaluru.dto;

import com.cleanbengaluru.entity.Role;
import com.cleanbengaluru.entity.User;

import java.time.LocalDateTime;

public record UserResponse(Long id, String name, String email, String phone,
                           Role role, String areaName, Boolean active, LocalDateTime createdAt) {

    public static UserResponse from(User u) {
        return new UserResponse(u.getId(), u.getName(), u.getEmail(), u.getPhone(),
                u.getRole(), u.getAreaName(), u.getActive(), u.getCreatedAt());
    }
}
