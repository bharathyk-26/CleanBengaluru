package com.cleanbengaluru.dto;

/**
 * Returned by POST /reports.
 *
 * If duplicate detection found a match and the citizen did not pass forceCreate=true,
 * `created` is false and `possibleDuplicate` holds the existing report so the UI can show
 * "Possible existing report found".
 */
public record CreateReportResult(boolean created,
                                 ReportResponse report,
                                 ReportResponse possibleDuplicate,
                                 Double duplicateDistanceMetres) {
}
