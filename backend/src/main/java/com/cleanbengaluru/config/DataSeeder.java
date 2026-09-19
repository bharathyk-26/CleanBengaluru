package com.cleanbengaluru.config;

import com.cleanbengaluru.entity.*;
import com.cleanbengaluru.repository.GarbageBinRepository;
import com.cleanbengaluru.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Inserts demo accounts and a few real Bengaluru bin locations on first startup,
 * so the app is usable the moment you run it. Controlled by app.seed.enabled.
 *
 * Demo logins (change the passwords before you deploy anywhere real):
 *   admin@cleanbengaluru.com   / admin123
 *   worker1@cleanbengaluru.com / worker123
 *   worker2@cleanbengaluru.com / worker123
 *   citizen@cleanbengaluru.com / citizen123
 */
@Component
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true", matchIfMissing = false)
@RequiredArgsConstructor
@Slf4j
public class DataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final GarbageBinRepository binRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        seedUsers();
        seedBins();
    }

    private void seedUsers() {
        if (userRepository.count() > 0) {
            log.info("Users already exist, skipping user seed");
            return;
        }

        userRepository.saveAll(List.of(
                user("System Admin", "admin@cleanbengaluru.com", "admin123", Role.ADMIN, "Central"),
                user("Ravi Kumar", "worker1@cleanbengaluru.com", "worker123", Role.WORKER, "Rajajinagar"),
                user("Lakshmi Devi", "worker2@cleanbengaluru.com", "worker123", Role.WORKER, "Indiranagar"),
                user("Demo Citizen", "citizen@cleanbengaluru.com", "citizen123", Role.CITIZEN, "Rajajinagar")
        ));
        log.info("Seeded 4 demo users");
    }

    private User user(String name, String email, String rawPassword, Role role, String area) {
        return User.builder()
                .name(name)
                .email(email)
                .password(passwordEncoder.encode(rawPassword))
                .role(role)
                .areaName(area)
                .active(true)
                .build();
    }

    private void seedBins() {
        if (binRepository.count() > 0) {
            log.info("Bins already exist, skipping bin seed");
            return;
        }

        binRepository.saveAll(List.of(
                bin("BLR-RJN-001", "Rajajinagar 1st Block Park", 12.9910, 77.5520, BinType.MIXED, 240, BinStatus.NORMAL, "Rajajinagar"),
                bin("BLR-RJN-002", "Navrang Circle", 12.9968, 77.5535, BinType.DRY_WASTE, 120, BinStatus.NEAR_FULL, "Rajajinagar"),
                bin("BLR-RJN-003", "Rajajinagar Metro Station", 12.9895, 77.5551, BinType.WET_WASTE, 240, BinStatus.OVERFLOWING, "Rajajinagar"),
                bin("BLR-IND-001", "100 Feet Road, Indiranagar", 12.9719, 77.6412, BinType.MIXED, 360, BinStatus.NORMAL, "Indiranagar"),
                bin("BLR-IND-002", "Indiranagar Metro Station", 12.9784, 77.6408, BinType.RECYCLABLE, 120, BinStatus.EMPTY, "Indiranagar"),
                bin("BLR-JAY-001", "Jayanagar 4th Block Complex", 12.9250, 77.5938, BinType.MIXED, 240, BinStatus.FULL, "Jayanagar"),
                bin("BLR-JAY-002", "Jayanagar Bus Stand", 12.9279, 77.5826, BinType.DRY_WASTE, 240, BinStatus.DAMAGED, "Jayanagar"),
                bin("BLR-KOR-001", "Koramangala 5th Block", 12.9345, 77.6264, BinType.MIXED, 360, BinStatus.NORMAL, "Koramangala"),
                bin("BLR-KOR-002", "Forum Mall Road", 12.9345, 77.6110, BinType.WET_WASTE, 240, BinStatus.NEAR_FULL, "Koramangala"),
                bin("BLR-MGR-001", "MG Road Metro", 12.9757, 77.6068, BinType.MIXED, 480, BinStatus.NORMAL, "MG Road")
        ));
        log.info("Seeded 10 demo garbage bins");
    }

    private GarbageBin bin(String code, String location, double lat, double lon,
                           BinType type, int capacity, BinStatus status, String area) {
        return GarbageBin.builder()
                .code(code)
                .locationName(location)
                .latitude(lat)
                .longitude(lon)
                .binType(type)
                .capacityLitres(capacity)
                .status(status)
                .areaName(area)
                .lastCollectionAt(LocalDateTime.now().minusHours(6))
                .build();
    }
}
