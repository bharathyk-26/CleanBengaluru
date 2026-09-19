package com.cleanbengaluru.dto;

import com.cleanbengaluru.entity.Role;

/** Returned by /auth/login and /auth/register. Never contains the password hash. */
public record AuthResponse(String token,
                           String tokenType,
                           Long userId,
                           String name,
                           String email,
                           Role role,
                           String areaName,
                           long expiresInMs) {
}
