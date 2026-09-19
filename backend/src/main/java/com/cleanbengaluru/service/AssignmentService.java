package com.cleanbengaluru.service;

import com.cleanbengaluru.dto.RejectRequest;
import com.cleanbengaluru.dto.TaskResponse;
import com.cleanbengaluru.entity.*;
import com.cleanbengaluru.exception.BadRequestException;
import com.cleanbengaluru.exception.ResourceNotFoundException;
import com.cleanbengaluru.mapper.ReportMapper;
import com.cleanbengaluru.repository.AssignmentRepository;
import com.cleanbengaluru.repository.GarbageReportRepository;
import com.cleanbengaluru.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Worker assignment and the cleanup workflow.
 *
 * ADMIN  : assign(reportId, workerId)                 report -> ASSIGNED
 * WORKER : accept(taskId)                             report -> WORKER_ACCEPTED
 *          reject(taskId, reason)                     report -> UNDER_REVIEW (back to admin)
 *          start(taskId)                              report -> CLEANING_IN_PROGRESS
 *          complete(taskId, afterPhoto, notes)        report -> CLEANING_COMPLETED -> VERIFICATION_PENDING
 *
 * The after-photo is enforced in complete(): no photo, no completion.
 */
@Service
@RequiredArgsConstructor
public class AssignmentService {

    private final AssignmentRepository assignmentRepository;
    private final GarbageReportRepository reportRepository;
    private final UserRepository userRepository;
    private final ReportService reportService;
    private final NotificationService notificationService;
    private final FileStorageService fileStorageService;
    private final ReportMapper reportMapper;

    // ------------------------------------------------------------------ ADMIN

    @Transactional
    public TaskResponse assign(Long reportId, Long workerId, Long adminId) {

        GarbageReport report = reportService.requireReport(reportId);

        User worker = userRepository.findById(workerId)
                .orElseThrow(() -> new ResourceNotFoundException("Worker", workerId));

        if (worker.getRole() != Role.WORKER) {
            throw new BadRequestException("User " + workerId + " is not a worker");
        }
        if (!Boolean.TRUE.equals(worker.getActive())) {
            throw new BadRequestException("This worker account is deactivated");
        }

        // Legal from REPORTED, UNDER_REVIEW or REOPENED. Anything else throws 409.
        reportService.transition(report, ReportStatus.ASSIGNED);
        reportRepository.save(report);

        User admin = adminId == null ? null : userRepository.findById(adminId).orElse(null);

        Assignment assignment = assignmentRepository.save(Assignment.builder()
                .report(report)
                .worker(worker)
                .assignedBy(admin)
                .status(AssignmentStatus.ASSIGNED)
                .assignedAt(LocalDateTime.now())
                .build());

        notificationService.create(worker, "New task assigned",
                "You have been assigned report #" + report.getId() + " ("
                        + report.getGarbageType() + ", priority " + report.getPriority() + ").", report.getId());

        notificationService.create(report.getReporter(), "Worker assigned",
                "Report #" + report.getId() + " has been assigned to a cleanup worker.", report.getId());

        return reportMapper.toTaskResponse(assignment);
    }

    // ------------------------------------------------------------------ WORKER

    @Transactional(readOnly = true)
    public List<TaskResponse> myTasks(Long workerId, boolean activeOnly) {
        List<Assignment> assignments = activeOnly
                ? assignmentRepository.findByWorkerIdAndStatusInOrderByAssignedAtDesc(workerId,
                List.of(AssignmentStatus.ASSIGNED, AssignmentStatus.ACCEPTED, AssignmentStatus.IN_PROGRESS))
                : assignmentRepository.findByWorkerIdOrderByAssignedAtDesc(workerId);

        return assignments.stream().map(reportMapper::toTaskResponse).toList();
    }

    @Transactional(readOnly = true)
    public TaskResponse getTask(Long taskId, Long workerId, boolean isAdmin) {
        return reportMapper.toTaskResponse(requireTask(taskId, workerId, isAdmin));
    }

    @Transactional
    public TaskResponse accept(Long taskId, Long workerId) {
        Assignment task = requireTask(taskId, workerId, false);
        requireTaskStatus(task, AssignmentStatus.ASSIGNED);

        task.setStatus(AssignmentStatus.ACCEPTED);
        task.setAcceptedAt(LocalDateTime.now());

        GarbageReport report = task.getReport();
        reportService.transition(report, ReportStatus.WORKER_ACCEPTED);
        reportRepository.save(report);

        notificationService.create(report.getReporter(), "Worker accepted",
                "A worker has accepted report #" + report.getId() + ".", report.getId());

        return reportMapper.toTaskResponse(assignmentRepository.save(task));
    }

    @Transactional
    public TaskResponse reject(Long taskId, Long workerId, RejectRequest request) {
        Assignment task = requireTask(taskId, workerId, false);
        requireTaskStatus(task, AssignmentStatus.ASSIGNED);

        task.setStatus(AssignmentStatus.REJECTED);
        task.setRejectReason(request.getReason());

        // The report goes back into the admin's queue so it can be reassigned.
        GarbageReport report = task.getReport();
        reportService.transition(report, ReportStatus.UNDER_REVIEW);
        reportRepository.save(report);

        notifyAdmins("Task rejected",
                "Worker " + task.getWorker().getName() + " rejected report #" + report.getId()
                        + ": " + request.getReason(), report.getId());

        return reportMapper.toTaskResponse(assignmentRepository.save(task));
    }

    @Transactional
    public TaskResponse start(Long taskId, Long workerId) {
        Assignment task = requireTask(taskId, workerId, false);
        requireTaskStatus(task, AssignmentStatus.ACCEPTED);

        task.setStatus(AssignmentStatus.IN_PROGRESS);
        task.setStartedAt(LocalDateTime.now());

        GarbageReport report = task.getReport();
        reportService.transition(report, ReportStatus.CLEANING_IN_PROGRESS);
        reportRepository.save(report);

        notificationService.create(report.getReporter(), "Cleaning started",
                "Cleaning has started for report #" + report.getId() + ".", report.getId());

        return reportMapper.toTaskResponse(assignmentRepository.save(task));
    }

    /** Upload the AFTER photo. Can be called before complete(), or as part of it. */
    @Transactional
    public TaskResponse uploadAfterPhoto(Long taskId, Long workerId, MultipartFile image) {
        Assignment task = requireTask(taskId, workerId, false);

        if (task.getStatus() != AssignmentStatus.IN_PROGRESS && task.getStatus() != AssignmentStatus.ACCEPTED) {
            throw new BadRequestException("You can only upload a photo for a task you have started");
        }

        task.setAfterImage(fileStorageService.store(image));
        return reportMapper.toTaskResponse(assignmentRepository.save(task));
    }

    /**
     * Finish the job. The BEFORE photo (citizen's) is untouched — the AFTER photo is a
     * separate column on the assignment, so both survive and can be shown side by side.
     */
    @Transactional
    public TaskResponse complete(Long taskId, Long workerId, MultipartFile afterImage, String notes) {

        Assignment task = requireTask(taskId, workerId, false);
        requireTaskStatus(task, AssignmentStatus.IN_PROGRESS);

        if (afterImage != null && !afterImage.isEmpty()) {
            task.setAfterImage(fileStorageService.store(afterImage));
        }
        if (task.getAfterImage() == null) {
            throw new BadRequestException("An after-cleaning photo is required before completing the task");
        }

        task.setStatus(AssignmentStatus.COMPLETED);
        task.setCompletedAt(LocalDateTime.now());
        task.setWorkerNotes(notes);

        GarbageReport report = task.getReport();
        reportService.transition(report, ReportStatus.CLEANING_COMPLETED);
        reportService.transition(report, ReportStatus.VERIFICATION_PENDING);
        reportRepository.save(report);

        notificationService.create(report.getReporter(), "Please verify the cleanup",
                "Your report #" + report.getId()
                        + " has been marked as cleaned. Please verify the cleanup.", report.getId());

        notifyAdmins("Cleanup completed",
                "Report #" + report.getId() + " was cleaned and is awaiting citizen verification.", report.getId());

        return reportMapper.toTaskResponse(assignmentRepository.save(task));
    }

    // ------------------------------------------------------------------ helpers

    private Assignment requireTask(Long taskId, Long workerId, boolean isAdmin) {
        Assignment task = assignmentRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task", taskId));

        if (!isAdmin && !task.getWorker().getId().equals(workerId)) {
            // Do not reveal that the task exists but belongs to someone else.
            throw new ResourceNotFoundException("Task", taskId);
        }
        return task;
    }

    private void requireTaskStatus(Assignment task, AssignmentStatus expected) {
        if (task.getStatus() != expected) {
            throw new BadRequestException(
                    "This action requires the task to be " + expected + ", but it is " + task.getStatus());
        }
    }

    private void notifyAdmins(String title, String message, Long reportId) {
        for (User admin : userRepository.findByRole(Role.ADMIN)) {
            notificationService.create(admin, title, message, reportId);
        }
    }
}
