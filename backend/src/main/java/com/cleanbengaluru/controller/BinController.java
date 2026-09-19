package com.cleanbengaluru.controller;

import com.cleanbengaluru.dto.ApiResponse;
import com.cleanbengaluru.dto.BinRequest;
import com.cleanbengaluru.dto.BinResponse;
import com.cleanbengaluru.dto.BinStatusUpdateRequest;
import com.cleanbengaluru.service.BinService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Read endpoints: any logged-in user.
 * Create/update/delete: ADMIN (enforced in SecurityConfig).
 * Status update: WORKER or ADMIN.
 */
@RestController
@RequestMapping("/bins")
@RequiredArgsConstructor
@Tag(name = "Garbage bins")
public class BinController {

    private final BinService binService;

    @GetMapping
    @Operation(summary = "All bins")
    public ResponseEntity<ApiResponse<List<BinResponse>>> all() {
        return ResponseEntity.ok(ApiResponse.ok("Bins", binService.findAll()));
    }

    /** GET /api/bins/nearby?latitude=12.9716&longitude=77.5946&radius=1 */
    @GetMapping("/nearby")
    @Operation(summary = "Bins within `radius` km, nearest first")
    public ResponseEntity<ApiResponse<List<BinResponse>>> nearby(
            @RequestParam double latitude,
            @RequestParam double longitude,
            @RequestParam(defaultValue = "1") double radius) {

        return ResponseEntity.ok(ApiResponse.ok("Nearby bins",
                binService.findNearby(latitude, longitude, radius)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<BinResponse>> byId(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok("Bin", binService.findById(id)));
    }

    @PostMapping
    @Operation(summary = "Create a bin (ADMIN)")
    public ResponseEntity<ApiResponse<BinResponse>> create(@Valid @RequestBody BinRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Bin created", binService.create(request)));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a bin (ADMIN)")
    public ResponseEntity<ApiResponse<BinResponse>> update(@PathVariable Long id,
                                                           @Valid @RequestBody BinRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Bin updated", binService.update(id, request)));
    }

    @PutMapping("/{id}/status")
    @Operation(summary = "Update bin status (WORKER or ADMIN)")
    public ResponseEntity<ApiResponse<BinResponse>> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody BinStatusUpdateRequest request) {

        return ResponseEntity.ok(ApiResponse.ok("Bin status updated", binService.updateStatus(id, request)));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a bin (ADMIN)")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        binService.delete(id);
        return ResponseEntity.ok(ApiResponse.ok("Bin deleted"));
    }
}
