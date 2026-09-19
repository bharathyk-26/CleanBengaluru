package com.cleanbengaluru.controller;

import com.cleanbengaluru.dto.*;
import com.cleanbengaluru.entity.ReportStatus;
import com.cleanbengaluru.entity.Role;
import com.cleanbengaluru.security.CustomUserDetails;
import com.cleanbengaluru.service.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Everything under /api/admin/** requires ROLE_ADMIN (see SecurityConfig).
 */
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
@Tag(name = "Admin")
public class AdminController {

    private final AnalyticsService analyticsService;
    private final ReportService reportService;
    private final AssignmentService assignmentService;
    private final AdminUserService adminUserService;

    // ----------------------------------------------------------- dashboard

    @GetMapping("/dashboard")
    @Operation(summary = "All dashboard counters in one call")
    public ResponseEntity<ApiResponse<DashboardResponse>> dashboard() {
        return ResponseEntity.ok(ApiResponse.ok("Dashboard", analyticsService.dashboard()));
    }

    @GetMapping("/analytics")
    @Operation(summary = "Same payload as /dashboard, kept for the analytics page")
    public ResponseEntity<ApiResponse<DashboardResponse>> analytics() {
        return ResponseEntity.ok(ApiResponse.ok("Analytics", analyticsService.dashboard()));
    }

    @GetMapping("/analytics/areas")
    @Operation(summary = "System-generated cleanliness score per area (not an official rating)")
    public ResponseEntity<ApiResponse<List<AreaScoreResponse>>> areaScores() {
        return ResponseEntity.ok(ApiResponse.ok("Area cleanliness scores", analyticsService.areaScores()));
    }

    @GetMapping("/analytics/workers")
    public ResponseEntity<ApiResponse<List<WorkerStatsResponse>>> workerStats() {
        return ResponseEntity.ok(ApiResponse.ok("Worker statistics", analyticsService.workerStats()));
    }

    // ----------------------------------------------------------- reports

    @GetMapping("/reports")
    public ResponseEntity<ApiResponse<Page<ReportResponse>>> reports(
            @RequestParam(required = false) ReportStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        return ResponseEntity.ok(ApiResponse.ok("Reports", reportService.findAll(status, page, size)));
    }

    @PostMapping("/reports/{id}/review")
    @Operation(summary = "Move a report to UNDER_REVIEW")
    public ResponseEntity<ApiResponse<ReportResponse>> review(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok("Report under review", reportService.review(id)));
    }

    @PostMapping("/reports/{id}/assign")
    @Operation(summary = "Assign a report to a worker")
    public ResponseEntity<ApiResponse<TaskResponse>> assign(
            @PathVariable Long id,
            @Valid @RequestBody AssignRequest request,
            @AuthenticationPrincipal CustomUserDetails principal) {

        return ResponseEntity.ok(ApiResponse.ok("Worker assigned",
                assignmentService.assign(id, request.getWorkerId(), principal.getId())));
    }

    @PostMapping("/reports/{id}/reject")
    @Operation(summary = "Reject a suspicious or invalid report")
    public ResponseEntity<ApiResponse<ReportResponse>> reject(
            @PathVariable Long id, @Valid @RequestBody RejectRequest request) {

        return ResponseEntity.ok(ApiResponse.ok("Report rejected", reportService.reject(id, request)));
    }

    // ----------------------------------------------------------- users

    @GetMapping("/users")
    public ResponseEntity<ApiResponse<List<UserResponse>>> users(@RequestParam(required = false) Role role) {
        return ResponseEntity.ok(ApiResponse.ok("Users", adminUserService.findAll(role)));
    }

    @GetMapping("/workers")
    public ResponseEntity<ApiResponse<List<UserResponse>>> workers() {
        return ResponseEntity.ok(ApiResponse.ok("Workers", adminUserService.findAll(Role.WORKER)));
    }

    @PostMapping("/users")
    @Operation(summary = "Create a WORKER or ADMIN account")
    public ResponseEntity<ApiResponse<UserResponse>> createStaff(@Valid @RequestBody CreateStaffRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Account created", adminUserService.createStaff(request)));
    }

    @PutMapping("/users/{id}/active")
    @Operation(summary = "Activate or deactivate an account")
    public ResponseEntity<ApiResponse<UserResponse>> setActive(@PathVariable Long id,
                                                               @RequestParam boolean active) {
        return ResponseEntity.ok(ApiResponse.ok("Account updated", adminUserService.setActive(id, active)));
    }
}
