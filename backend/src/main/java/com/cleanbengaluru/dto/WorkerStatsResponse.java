package com.cleanbengaluru.dto;

public record WorkerStatsResponse(Long workerId, String workerName, String areaName,
                                  long totalTasks, long completedTasks, long activeTasks) {
}
