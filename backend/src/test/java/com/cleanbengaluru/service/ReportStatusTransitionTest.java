package com.cleanbengaluru.service;

import com.cleanbengaluru.entity.ReportStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReportStatusTransitionTest {

    @Test
    @DisplayName("the happy path is fully walkable")
    void happyPath() {
        assertThat(ReportStatus.REPORTED.canMoveTo(ReportStatus.UNDER_REVIEW)).isTrue();
        assertThat(ReportStatus.UNDER_REVIEW.canMoveTo(ReportStatus.ASSIGNED)).isTrue();
        assertThat(ReportStatus.ASSIGNED.canMoveTo(ReportStatus.WORKER_ACCEPTED)).isTrue();
        assertThat(ReportStatus.WORKER_ACCEPTED.canMoveTo(ReportStatus.CLEANING_IN_PROGRESS)).isTrue();
        assertThat(ReportStatus.CLEANING_IN_PROGRESS.canMoveTo(ReportStatus.CLEANING_COMPLETED)).isTrue();
        assertThat(ReportStatus.CLEANING_COMPLETED.canMoveTo(ReportStatus.VERIFICATION_PENDING)).isTrue();
        assertThat(ReportStatus.VERIFICATION_PENDING.canMoveTo(ReportStatus.VERIFIED)).isTrue();
        assertThat(ReportStatus.VERIFIED.canMoveTo(ReportStatus.CLOSED)).isTrue();
    }

    @Test
    @DisplayName("a citizen can reopen from VERIFICATION_PENDING")
    void reopenPath() {
        assertThat(ReportStatus.VERIFICATION_PENDING.canMoveTo(ReportStatus.REOPENED)).isTrue();
        assertThat(ReportStatus.REOPENED.canMoveTo(ReportStatus.ASSIGNED)).isTrue();
    }

    @Test
    @DisplayName("shortcuts and moves out of terminal states are rejected")
    void illegalMoves() {
        assertThat(ReportStatus.REPORTED.canMoveTo(ReportStatus.CLOSED)).isFalse();
        assertThat(ReportStatus.REPORTED.canMoveTo(ReportStatus.CLEANING_COMPLETED)).isFalse();
        assertThat(ReportStatus.CLOSED.canMoveTo(ReportStatus.ASSIGNED)).isFalse();
        assertThat(ReportStatus.REJECTED.canMoveTo(ReportStatus.ASSIGNED)).isFalse();
    }

    @Test
    @DisplayName("isOpen() marks only unfinished reports")
    void openStates() {
        assertThat(ReportStatus.REPORTED.isOpen()).isTrue();
        assertThat(ReportStatus.CLEANING_IN_PROGRESS.isOpen()).isTrue();
        assertThat(ReportStatus.CLOSED.isOpen()).isFalse();
        assertThat(ReportStatus.REJECTED.isOpen()).isFalse();
    }
}
