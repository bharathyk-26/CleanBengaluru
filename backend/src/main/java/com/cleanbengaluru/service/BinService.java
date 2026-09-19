package com.cleanbengaluru.service;

import com.cleanbengaluru.dto.BinRequest;
import com.cleanbengaluru.dto.BinResponse;
import com.cleanbengaluru.dto.BinStatusUpdateRequest;
import com.cleanbengaluru.entity.BinStatus;
import com.cleanbengaluru.entity.GarbageBin;
import com.cleanbengaluru.exception.BadRequestException;
import com.cleanbengaluru.exception.DuplicateResourceException;
import com.cleanbengaluru.exception.ResourceNotFoundException;
import com.cleanbengaluru.repository.GarbageBinRepository;
import com.cleanbengaluru.util.DistanceCalculator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BinService {

    private static final double MAX_RADIUS_KM = 20d;

    private final GarbageBinRepository binRepository;

    @Transactional(readOnly = true)
    public List<BinResponse> findAll() {
        return binRepository.findAll().stream()
                .map(b -> BinResponse.from(b, null))
                .toList();
    }

    @Transactional(readOnly = true)
    public BinResponse findById(Long id) {
        return BinResponse.from(requireBin(id), null);
    }

    /**
     * Bounding box in SQL, exact Haversine in Java, sorted nearest-first.
     * radiusKm is capped so a client cannot ask for "the whole planet" and pull every row.
     */
    @Transactional(readOnly = true)
    public List<BinResponse> findNearby(double latitude, double longitude, double radiusKm) {

        if (radiusKm <= 0 || radiusKm > MAX_RADIUS_KM) {
            throw new BadRequestException("Radius must be between 0 and " + MAX_RADIUS_KM + " km");
        }

        double radiusMetres = radiusKm * 1000;
        double[] box = DistanceCalculator.boundingBox(latitude, longitude, radiusMetres);

        return binRepository.findInBoundingBox(box[0], box[1], box[2], box[3]).stream()
                .map(b -> BinResponse.from(b,
                        DistanceCalculator.distanceMetres(latitude, longitude, b.getLatitude(), b.getLongitude())))
                .filter(b -> b.distanceMetres() <= radiusMetres)
                .sorted(Comparator.comparingDouble(BinResponse::distanceMetres))
                .toList();
    }

    @Transactional
    public BinResponse create(BinRequest request) {
        if (binRepository.existsByCode(request.getCode())) {
            throw new DuplicateResourceException("A bin with code " + request.getCode() + " already exists");
        }

        GarbageBin bin = GarbageBin.builder()
                .code(request.getCode().trim())
                .locationName(request.getLocationName().trim())
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .binType(request.getBinType())
                .capacityLitres(request.getCapacityLitres())
                .status(request.getStatus() == null ? BinStatus.NORMAL : request.getStatus())
                .areaName(request.getAreaName())
                .build();

        return BinResponse.from(binRepository.save(bin), null);
    }

    @Transactional
    public BinResponse update(Long id, BinRequest request) {
        GarbageBin bin = requireBin(id);

        if (!bin.getCode().equals(request.getCode()) && binRepository.existsByCode(request.getCode())) {
            throw new DuplicateResourceException("A bin with code " + request.getCode() + " already exists");
        }

        bin.setCode(request.getCode().trim());
        bin.setLocationName(request.getLocationName().trim());
        bin.setLatitude(request.getLatitude());
        bin.setLongitude(request.getLongitude());
        bin.setBinType(request.getBinType());
        bin.setCapacityLitres(request.getCapacityLitres());
        bin.setAreaName(request.getAreaName());
        if (request.getStatus() != null) {
            bin.setStatus(request.getStatus());
        }
        return BinResponse.from(binRepository.save(bin), null);
    }

    /** Used by workers in the field after emptying or finding a damaged bin. */
    @Transactional
    public BinResponse updateStatus(Long id, BinStatusUpdateRequest request) {
        GarbageBin bin = requireBin(id);
        bin.setStatus(request.getStatus());

        if (Boolean.TRUE.equals(request.getCollected()) || request.getStatus() == BinStatus.EMPTY) {
            bin.setLastCollectionAt(LocalDateTime.now());
        }
        return BinResponse.from(binRepository.save(bin), null);
    }

    @Transactional
    public void delete(Long id) {
        binRepository.delete(requireBin(id));
    }

    private GarbageBin requireBin(Long id) {
        return binRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Garbage bin", id));
    }
}
