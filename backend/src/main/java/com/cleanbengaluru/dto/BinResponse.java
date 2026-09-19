package com.cleanbengaluru.dto;

import com.cleanbengaluru.entity.BinStatus;
import com.cleanbengaluru.entity.BinType;
import com.cleanbengaluru.entity.GarbageBin;

import java.time.LocalDateTime;

public record BinResponse(Long id, String code, String locationName,
                          Double latitude, Double longitude,
                          BinType binType, Integer capacityLitres, BinStatus status,
                          LocalDateTime lastCollectionAt, String areaName,
                          Double distanceMetres) {

    public static BinResponse from(GarbageBin b, Double distanceMetres) {
        return new BinResponse(b.getId(), b.getCode(), b.getLocationName(),
                b.getLatitude(), b.getLongitude(), b.getBinType(), b.getCapacityLitres(),
                b.getStatus(), b.getLastCollectionAt(), b.getAreaName(), distanceMetres);
    }
}
