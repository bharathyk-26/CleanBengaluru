package com.cleanbengaluru.service;

import com.cleanbengaluru.dto.AuthResponse;
import com.cleanbengaluru.dto.LoginRequest;
import com.cleanbengaluru.dto.RegisterRequest;
import com.cleanbengaluru.entity.Role;
import com.cleanbengaluru.entity.User;
import com.cleanbengaluru.exception.DuplicateResourceException;
import com.cleanbengaluru.exception.ResourceNotFoundException;
import com.cleanbengaluru.repository.UserRepository;
import com.cleanbengaluru.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * REGISTER -> hash password -> save user
 * LOGIN    -> look up by email -> BCrypt.matches(raw, hash) -> generate JWT
 *
 * We do NOT use AuthenticationManager.authenticate() here because we already have the
 * user entity in hand and want a clear, explicit flow that is easy to explain.
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    @Transactional
    public AuthResponse register(RegisterRequest request) {

        if (userRepository.existsByEmail(request.getEmail().toLowerCase())) {
            throw new DuplicateResourceException("An account with this email already exists");
        }

        // Public self-registration is citizens only. Workers/admins are created by an admin.
        Role role = (request.getRole() == null) ? Role.CITIZEN : request.getRole();
        if (role != Role.CITIZEN) {
            role = Role.CITIZEN;
        }

        User user = User.builder()
                .name(request.getName().trim())
                .email(request.getEmail().toLowerCase().trim())
                .password(passwordEncoder.encode(request.getPassword()))  // BCrypt, never plain text
                .phone(request.getPhone())
                .areaName(request.getAreaName())
                .role(role)
                .active(true)
                .build();

        User saved = userRepository.save(user);
        return buildAuthResponse(saved);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {

        User user = userRepository.findByEmail(request.getEmail().toLowerCase().trim())
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));

        // BCrypt re-hashes the supplied password with the stored salt and compares.
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BadCredentialsException("Invalid email or password");
        }

        if (!Boolean.TRUE.equals(user.getActive())) {
            throw new BadCredentialsException("This account has been deactivated");
        }

        return buildAuthResponse(user);
    }

    @Transactional(readOnly = true)
    public User requireUser(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", id));
    }

    private AuthResponse buildAuthResponse(User user) {
        String token = jwtUtil.generateToken(user.getEmail(), user.getRole().name(), user.getId());
        return new AuthResponse(token, "Bearer", user.getId(), user.getName(),
                user.getEmail(), user.getRole(), user.getAreaName(), jwtUtil.getExpirationMs());
    }
}
