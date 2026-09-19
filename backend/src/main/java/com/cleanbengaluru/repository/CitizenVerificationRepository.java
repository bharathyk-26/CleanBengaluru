package com.cleanbengaluru.repository;

import com.cleanbengaluru.entity.CitizenVerification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CitizenVerificationRepository extends JpaRepository<CitizenVerification, Long> {
    List<CitizenVerification> findByReportIdOrderByCreatedAtAsc(Long reportId);
}
