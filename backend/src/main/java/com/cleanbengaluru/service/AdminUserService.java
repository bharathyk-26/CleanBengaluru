package com.cleanbengaluru.service;

import com.cleanbengaluru.dto.CreateStaffRequest;
import com.cleanbengaluru.dto.UserResponse;
import com.cleanbengaluru.entity.Role;
import com.cleanbengaluru.entity.User;
import com.cleanbengaluru.exception.BadRequestException;
import com.cleanbengaluru.exception.DuplicateResourceException;
import com.cleanbengaluru.exception.ResourceNotFoundException;
import com.cleanbengaluru.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public List<UserResponse> findAll(Role role) {
        List<User> users = (role == null) ? userRepository.findAll() : userRepository.findByRole(role);
        return users.stream().map(UserResponse::from).toList();
    }

    @Transactional
    public UserResponse createStaff(CreateStaffRequest request) {
        if (request.getRole() == Role.CITIZEN) {
            throw new BadRequestException("Citizens must register themselves at /auth/register");
        }
        if (userRepository.existsByEmail(request.getEmail().toLowerCase())) {
            throw new DuplicateResourceException("An account with this email already exists");
        }

        User user = User.builder()
                .name(request.getName().trim())
                .email(request.getEmail().toLowerCase().trim())
                .password(passwordEncoder.encode(request.getPassword()))
                .phone(request.getPhone())
                .areaName(request.getAreaName())
                .role(request.getRole())
                .active(true)
                .build();

        return UserResponse.from(userRepository.save(user));
    }

    /** Soft delete: we deactivate rather than delete, so their reports and tasks keep their history. */
    @Transactional
    public UserResponse setActive(Long userId, boolean active) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
        user.setActive(active);
        return UserResponse.from(userRepository.save(user));
    }
}
