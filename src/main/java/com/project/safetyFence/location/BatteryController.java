package com.project.safetyFence.location;

import com.project.safetyFence.location.dto.BatteryUpdateDto;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Slf4j
public class BatteryController {

    private final LocationCacheService cacheService;

    @PostMapping("/battery")
    public ResponseEntity<Void> updateBattery(
            @RequestBody BatteryUpdateDto battery,
            HttpServletRequest request) {

        String userNumber = (String) request.getAttribute("userNumber");

        if (userNumber == null || userNumber.isBlank()) {
            log.error("Battery update failed: missing userNumber");
            return ResponseEntity.badRequest().build();
        }

        // Validate battery level
        if (battery.getBatteryLevel() == null ||
            battery.getBatteryLevel() < 0 ||
            battery.getBatteryLevel() > 100) {
            log.error("Invalid battery level: {}", battery.getBatteryLevel());
            return ResponseEntity.badRequest().build();
        }

        // Set server-side fields
        battery.setUserNumber(userNumber);
        battery.setTimestamp(System.currentTimeMillis());

        // Store in cache
        cacheService.updateBattery(userNumber, battery);

        log.debug("Battery updated: userNumber={}, level={}",
                  userNumber, battery.getBatteryLevel());

        return ResponseEntity.ok().build();
    }
}
