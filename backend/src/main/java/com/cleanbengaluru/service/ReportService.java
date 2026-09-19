package com.cleanbengaluru.service;

import com.cleanbengaluru.dto.*;
import com.cleanbengaluru.entity.*;
import com.cleanbengaluru.exception.BadRequestException;
import com.cleanbengaluru.exception.InvalidStatusTransitionException;
import com.cleanbengaluru.exception.ResourceNotFoundException;
import com.cleanbengaluru.mapper.ReportMapper;
import com.cleanbengaluru.repository.*;
import com.cleanbengaluru.util.DistanceCalculator;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * All business rules for garbage reports.
 *
 * Nothing here knows about HTTP. The controller converts HTTP -> arguments, calls a
 * method, and converts the return value -> HTTP. That separation is what makes these
 * rules unit-testable with plain Mockito, no Spring context needed.
 */
@Service
@RequiredArgsConstructor
public class ReportService {

    private static final double MAX_RADIUS_KM = 20d;

    private final GarbageReportRepository reportRepository;
    private final AssignmentRepository assignmentRepository;
    private final CitizenVerificationRepository verificationRepository;
    private final UserRepository userRepository;
    private final FileStorageService fileStorageService;
    private final DuplicateDetectionService duplicateDetectionService;
    private final PriorityService priorityService;
    private final NotificationService notificationService;
    private final ReportMapper reportMapper;

    // ------------------------------------------------------------------ CREATE

    /**
     * Citizen files a report.
     *
     * If duplicate detection finds a match and the citizen has not passed forceCreate=true,
     * we save NOTHING and return the existing report so the UI can ask
     * "Possible existing report found - is this the same pile?".
     */
    @Transactional
    public CreateReportResult create(Long citizenId, ReportRequest request, MultipartFile image) {

        User citizen = userRepository.findById(citizenId)
                .orElseThrow(() -> new ResourceNotFoundException("User", citizenId));

        Optional<DuplicateDetectionService.Match> match = duplicateDetectionService
                .findPossibleDuplicate(request.getLatitude(), request.getLongitude(), request.getGarbageType());

        if (match.isPresent() && !Boolean.TRUE.equals(request.getForceCreate())) {
            GarbageReport existing = match.get().report();
            return new CreateReportResult(
                    false,
                    null,
                    reportMapper.toResponse(existing, activeAssignment(existing.getId()).orElse(null), null),
                    match.get().distanceMetres());
        }

        String storedImage = (image != null && !image.isEmpty()) ? fileStorageService.store(image) : null;

        GarbageReport report = GarbageReport.builder()
                .reporter(citizen)
                .garbageType(request.getGarbageType())
                .description(request.getDescription())
                .beforeImage(storedImage)
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .address(request.getAddress())
                .areaName(request.getAreaName() != null ? request.getAreaName() : citizen.getAreaName())
                .severity(request.getSeverity() == null ? 3 : request.getSeverity())
                .roadBlocked(Boolean.TRUE.equals(request.getRoadBlocked()))
                .status(ReportStatus.REPORTED)
                .duplicateCount(0)
                .reopenCount(0)
                .build();

        // Linked to an existing incident: the new report becomes a child and bumps the parent.
        if (match.isPresent()) {
            GarbageReport parent = match.get().report();
            report.setParentReport(parent);

            parent.setDuplicateCount(parent.getDuplicateCount() + 1);
            parent.setPriority(priorityService.calculate(parent));
            reportRepository.save(parent);
        }

        report.setPriority(priorityService.calculate(report));
        GarbageReport saved = reportRepository.save(report);

        notificationService.create(citizen, "Report submitted",
                "Your report #" + saved.getId() + " has been received and is awaiting review.", saved.getId());

        notifyAdmins("New garbage report",
                "Report #" + saved.getId() + " (" + saved.getGarbageType() + ", priority "
                        + saved.getPriority() + ") was submitted.", saved.getId());

        return new CreateReportResult(true, toResponse(saved, null), null, null);
    }

    // ------------------------------------------------------------------ READ

    @Transactional(readOnly = true)
    public ReportResponse findById(Long id) {
        GarbageReport report = requireReport(id);
        return toResponse(report, null);
    }

    @Transactional(readOnly = true)
    public Page<ReportResponse> findMine(Long citizenId, int page, int size) {
        return reportRepository
                .findByReporterIdOrderByCreatedAtDesc(citizenId, PageRequest.of(page, size))
                .map(r -> toResponse(r, null));
    }

    @Transactional(readOnly = true)
    public Page<ReportResponse> findAll(ReportStatus status, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<GarbageReport> reports = (status == null)
                ? reportRepository.findAll(pageable)
                : reportRepository.findByStatusOrderByCreatedAtDesc(status, PageRequest.of(page, size));
        return reports.map(r -> toResponse(r, null));
    }

    /**
     * GET /reports/nearby?latitude=..&longitude=..&radius=2
     *
     * Note what is NOT returned: nothing about the reporter beyond their display name.
     * We never expose a citizen's email, phone or home address through this endpoint.
     */
    @Transactional(readOnly = true)
    public List<ReportResponse> findNearby(double latitude, double longitude, double radiusKm) {

        if (radiusKm <= 0 || radiusKm > MAX_RADIUS_KM) {
            throw new BadRequestException("Radius must be between 0 and " + MAX_RADIUS_KM + " km");
        }

        double radiusMetres = radiusKm * 1000;
        double[] box = DistanceCalculator.boundingBox(latitude, longitude, radiusMetres);

        return reportRepository.findInBoundingBox(box[0], box[1], box[2], box[3]).stream()
                .map(r -> toResponse(r, DistanceCalculator.distanceMetres(
                        latitude, longitude, r.getLatitude(), r.getLongitude())))
                .filter(r -> r.distanceMetres() <= radiusMetres)
                .sorted(Comparator.comparingDouble(ReportResponse::distanceMetres))
                .toList();
    }

    // ------------------------------------------------------------------ UPDATE / DELETE

    /** A citizen may edit description/severity only while the report is still untouched. */
    @Transactional
    public ReportResponse updateOwn(Long reportId, Long citizenId, ReportRequest request) {
        GarbageReport report = requireOwnedReport(reportId, citizenId);

        if (report.getStatus() != ReportStatus.REPORTED && report.getStatus() != ReportStatus.UNDER_REVIEW) {
            throw new BadRequestException("A report can only be edited before a worker is assigned");
        }

        report.setDescription(request.getDescription());
        if (request.getSeverity() != null) report.setSeverity(request.getSeverity());
        if (request.getRoadBlocked() != null) report.setRoadBlocked(request.getRoadBlocked());
        report.setPriority(priorityService.calculate(report));

        return toResponse(reportRepository.save(report), null);
    }

    @Transactional
    public void deleteOwn(Long reportId, Long citizenId, boolean isAdmin) {
        GarbageReport report = requireReport(reportId);

        if (!isAdmin) {
            if (!report.getReporter().getId().equals(citizenId)) {
                throw new BadRequestException("You can only delete your own reports");
            }
            if (report.getStatus() != ReportStatus.REPORTED) {
                throw new BadRequestException("A report can only be deleted before it is reviewed");
            }
        }
        reportRepository.delete(report);
    }

    // ------------------------------------------------------------------ CITIZEN VERIFICATION

    /**
     * YES -> VERIFIED -> CLOSED (and resolvedAt is stamped, feeding resolution-time analytics)
     * NO  -> REOPENED (admins are notified so they can reassign)
     */
    @Transactional
    public ReportResponse verify(Long reportId, Long citizenId, VerifyRequest request) {

        GarbageReport report = requireOwnedReport(reportId, citizenId);

        if (report.getStatus() != ReportStatus.VERIFICATION_PENDING) {
            throw new BadRequestException("This report is not waiting for your verification");
        }

        verificationRepository.save(CitizenVerification.builder()
                .report(report)
                .citizen(report.getReporter())
                .cleaned(request.getCleaned())
                .reason(request.getReason())
                .build());

        if (Boolean.TRUE.equals(request.getCleaned())) {
            transition(report, ReportStatus.VERIFIED);
            transition(report, ReportStatus.CLOSED);
            report.setResolvedAt(LocalDateTime.now());

            notificationService.create(report.getReporter(), "Report closed",
                    "Thank you for verifying. Report #" + report.getId() + " is now closed.", report.getId());

            activeAssignment(report.getId()).ifPresent(a ->
                    notificationService.create(a.getWorker(), "Cleanup verified",
                            "The citizen confirmed report #" + report.getId() + " was cleaned properly.",
                            report.getId()));
        } else {
            transition(report, ReportStatus.REOPENED);
            report.setReopenCount(report.getReopenCount() + 1);
            report.setPriority(priorityService.calculate(report));

            notificationService.create(report.getReporter(), "Report reopened",
                    "Report #" + report.getId() + " has been reopened and sent back to the authority.",
                    report.getId());

            notifyAdmins("Report reopened",
                    "Citizen reopened report #" + report.getId()
                            + (request.getReason() != null ? ": " + request.getReason() : ""), report.getId());
        }

        return toResponse(reportRepository.save(report), null);
    }

    /** Explicit reopen endpoint, kept separate from verify for a clearer API. */
    @Transactional
    public ReportResponse reopen(Long reportId, Long citizenId, RejectRequest request) {
        VerifyRequest verify = new VerifyRequest();
        verify.setCleaned(false);
        verify.setReason(request.getReason());
        return verify(reportId, citizenId, verify);
    }

    // ------------------------------------------------------------------ ADMIN ACTIONS

    @Transactional
    public ReportResponse review(Long reportId) {
        GarbageReport report = requireReport(reportId);
        transition(report, ReportStatus.UNDER_REVIEW);
        return toResponse(reportRepository.save(report), null);
    }

    @Transactional
    public ReportResponse reject(Long reportId, RejectRequest request) {
        GarbageReport report = requireReport(reportId);
        transition(report, ReportStatus.REJECTED);
        report.setRejectionReason(request.getReason());

        notificationService.create(report.getReporter(), "Report rejected",
                "Report #" + report.getId() + " was rejected: " + request.getReason(), report.getId());

        return toResponse(reportRepository.save(report), null);
    }

    // ------------------------------------------------------------------ SHARED HELPERS

    /**
     * The ONLY place a report's status is allowed to change. Every illegal move
     * (e.g. REPORTED -> CLOSED) fails loudly with HTTP 409 instead of corrupting data.
     */
    public void transition(GarbageReport report, ReportStatus target) {
        if (!report.getStatus().canMoveTo(target)) {
            throw new InvalidStatusTransitionException(report.getStatus(), target);
        }
        report.setStatus(target);
    }

    public GarbageReport requireReport(Long id) {
        return reportRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Report", id));
    }

    public ReportResponse toResponse(GarbageReport report, Double distanceMetres) {
        return reportMapper.toResponse(report, activeAssignment(report.getId()).orElse(null), distanceMetres);
    }

    private Optional<Assignment> activeAssignment(Long reportId) {
        return assignmentRepository.findFirstByReportIdOrderByAssignedAtDesc(reportId);
    }

    private GarbageReport requireOwnedReport(Long reportId, Long citizenId) {
        GarbageReport report = requireReport(reportId);
        if (!report.getReporter().getId().equals(citizenId)) {
            throw new BadRequestException("This report does not belong to you");
        }
        return report;
    }

    private void notifyAdmins(String title, String message, Long reportId) {
        List<User> admins = userRepository.findByRole(Role.ADMIN);
        for (User admin : admins) {
            notificationService.create(admin, title, message, reportId);
        }
    }
}
