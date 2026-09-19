package com.cleanbengaluru.controller;

import com.cleanbengaluru.dto.ApiResponse;
import com.cleanbengaluru.dto.RejectRequest;
import com.cleanbengaluru.dto.TaskResponse;
import com.cleanbengaluru.entity.Role;
import com.cleanbengaluru.security.CustomUserDetails;
import com.cleanbengaluru.service.AssignmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * GET /api/workers/tasks
 * GET /api/tasks/{id}
 * PUT /api/tasks/{id}/accept
 * PUT /api/tasks/{id}/reject
 * PUT /api/tasks/{id}/start
 * PUT /api/tasks/{id}/photo      (multipart)
 * PUT /api/tasks/{id}/complete   (multipart)
 *
 * Both path prefixes are restricted to WORKER and ADMIN in SecurityConfig.
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Worker tasks")
public class WorkerController {

    private final AssignmentService assignmentService;

    @GetMapping("/workers/tasks")
    @Operation(summary = "My assigned tasks")
    public ResponseEntity<ApiResponse<List<TaskResponse>>> myTasks(
            @AuthenticationPrincipal CustomUserDetails principal,
            @RequestParam(defaultValue = "false") boolean activeOnly) {

        return ResponseEntity.ok(ApiResponse.ok("My tasks",
                assignmentService.myTasks(principal.getId(), activeOnly)));
    }

    @GetMapping("/tasks/{id}")
    public ResponseEntity<ApiResponse<TaskResponse>> task(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails principal) {

        boolean isAdmin = principal.getUser().getRole() == Role.ADMIN;
        return ResponseEntity.ok(ApiResponse.ok("Task",
                assignmentService.getTask(id, principal.getId(), isAdmin)));
    }

    @PutMapping("/tasks/{id}/accept")
    @Operation(summary = "Accept an assigned task")
    public ResponseEntity<ApiResponse<TaskResponse>> accept(
            @PathVariable Long id, @AuthenticationPrincipal CustomUserDetails principal) {

        return ResponseEntity.ok(ApiResponse.ok("Task accepted",
                assignmentService.accept(id, principal.getId())));
    }

    @PutMapping("/tasks/{id}/reject")
    @Operation(summary = "Reject an assigned task with a reason")
    public ResponseEntity<ApiResponse<TaskResponse>> reject(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails principal,
            @Valid @RequestBody RejectRequest request) {

        return ResponseEntity.ok(ApiResponse.ok("Task rejected",
                assignmentService.reject(id, principal.getId(), request)));
    }

    @PutMapping("/tasks/{id}/start")
    @Operation(summary = "Start cleaning")
    public ResponseEntity<ApiResponse<TaskResponse>> start(
            @PathVariable Long id, @AuthenticationPrincipal CustomUserDetails principal) {

        return ResponseEntity.ok(ApiResponse.ok("Cleaning started",
                assignmentService.start(id, principal.getId())));
    }

    @PutMapping(value = "/tasks/{id}/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload the after-cleaning photo")
    public ResponseEntity<ApiResponse<TaskResponse>> uploadPhoto(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails principal,
            @RequestPart("image") MultipartFile image) {

        return ResponseEntity.ok(ApiResponse.ok("Photo uploaded",
                assignmentService.uploadAfterPhoto(id, principal.getId(), image)));
    }

    /** The after-photo is mandatory: without one this returns 400. */
    @PutMapping(value = "/tasks/{id}/complete", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Mark cleaning complete (after-photo required)")
    public ResponseEntity<ApiResponse<TaskResponse>> complete(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails principal,
            @RequestPart(value = "image", required = false) MultipartFile image,
            @RequestParam(required = false) String notes) {

        return ResponseEntity.ok(ApiResponse.ok("Cleaning completed",
                assignmentService.complete(id, principal.getId(), image, notes)));
    }
}
