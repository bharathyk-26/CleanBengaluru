package com.cleanbengaluru.exception;

import com.cleanbengaluru.entity.ReportStatus;

/** Thrown when code tries an illegal move in the report lifecycle. Mapped to HTTP 409. */
public class InvalidStatusTransitionException extends RuntimeException {
    public InvalidStatusTransitionException(ReportStatus from, ReportStatus to) {
        super("Invalid status transition: " + from + " -> " + to);
    }
}
