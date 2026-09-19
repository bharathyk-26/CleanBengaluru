package com.cleanbengaluru.controller;

import com.cleanbengaluru.dto.*;
import com.cleanbengaluru.entity.ReportStatus;
import com.cleanbengaluru.entity.Role;
import com.cleanbengaluru.security.CustomUserDetails;
import com.cleanbengaluru.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Citizen-facing report endpoints. Everything here requires a valid JWT.
 */
@RestController
@RequestMapping("/reports")
@RequiredArgsConstructor
@Tag(name = "Garbage reports")
public class ReportController {

    private final ReportService reportService;

    /**
     * POST /api/reports        (multipart/form-data)
     *
     * parts: garbageType, description, latitude, longitude, address, areaName,
     *        severity, roadBlocked, forceCreate  +  image (file, optional)
     *
     * 201 -> report created
     * 200 -> a possible duplicate was found; nothing was saved yet. Resend with
     *        forceCreate=true to file it anyway.
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('CITIZEN','ADMIN')")
    @Operation(summary = "Create a garbage report with a photo")
    public ResponseEntity<ApiResponse<CreateReportResult>> create(
            @AuthenticationPrincipal CustomUserDetails principal,
            @Valid @ModelAttribute ReportRequest request,
            @RequestPart(value = "image", required = false) MultipartFile image) {

        CreateReportResult result = reportService.create(principal.getId(), request, image);

        if (!result.created()) {
            return ResponseEntity.ok(ApiResponse.ok(
                    "Possible existing report found near this location", result));
        }
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Report submitted successfully", result));
    }

    @GetMapping("/my")
    @Operation(summary = "My own reports (paged)")
    public ResponseEntity<ApiResponse<Page<ReportResponse>>> myReports(
            @AuthenticationPrincipal CustomUserDetails principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        return ResponseEntity.ok(ApiResponse.ok("My reports",
                reportService.findMine(principal.getId(), page, size)));
    }

    @GetMapping
    @Operation(summary = "All reports (paged, optional status filter)")
    public ResponseEntity<ApiResponse<Page<ReportResponse>>> all(
            @RequestParam(required = false) ReportStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        return ResponseEntity.ok(ApiResponse.ok("Reports", reportService.findAll(status, page, size)));
    }

    /** GET /api/reports/nearby?latitude=12.9716&longitude=77.5946&radius=2 */
    @GetMapping("/nearby")
    @Operation(summary = "Open reports within `radius` km, nearest first")
    public ResponseEntity<ApiResponse<List<ReportResponse>>> nearby(
            @RequestParam double latitude,
            @RequestParam double longitude,
            @RequestParam(defaultValue = "2") double radius) {

        return ResponseEntity.ok(ApiResponse.ok("Nearby reports",
                reportService.findNearby(latitude, longitude, radius)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "One report by id")
    public ResponseEntity<ApiResponse<ReportResponse>> byId(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok("Report", reportService.findById(id)));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Edit my own report (only before assignment)")
    public ResponseEntity<ApiResponse<ReportResponse>> update(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails principal,
            @Valid @RequestBody ReportRequest request) {

        return ResponseEntity.ok(ApiResponse.ok("Report updated",
                reportService.updateOwn(id, principal.getId(), request)));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete my own report (only while REPORTED)")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails principal) {

        boolean isAdmin = principal.getUser().getRole() == Role.ADMIN;
        reportService.deleteOwn(id, principal.getId(), isAdmin);
        return ResponseEntity.ok(ApiResponse.ok("Report deleted"));
    }

    /** POST /api/reports/{id}/verify  — "YES, CLEANED" or "NO, NOT PROPERLY CLEANED". */
    @PostMapping("/{id}/verify")
    @PreAuthorize("hasAnyRole('CITIZEN','ADMIN')")
    @Operation(summary = "Verify a completed cleanup")
    public ResponseEntity<ApiResponse<ReportResponse>> verify(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails principal,
            @Valid @RequestBody VerifyRequest request) {

        return ResponseEntity.ok(ApiResponse.ok("Verification recorded",
                reportService.verify(id, principal.getId(), request)));
    }

    @PostMapping("/{id}/reopen")
    @PreAuthorize("hasAnyRole('CITIZEN','ADMIN')")
    @Operation(summary = "Reopen a report that was not properly cleaned")
    public ResponseEntity<ApiResponse<ReportResponse>> reopen(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails principal,
            @Valid @RequestBody RejectRequest request) {

        return ResponseEntity.ok(ApiResponse.ok("Report reopened",
                reportService.reopen(id, principal.getId(), request)));
    }
}
