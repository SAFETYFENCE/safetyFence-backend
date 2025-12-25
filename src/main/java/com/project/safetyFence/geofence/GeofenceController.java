package com.project.safetyFence.geofence;

import com.project.safetyFence.geofence.domain.Geofence;
import com.project.safetyFence.geofence.dto.FenceInRequestDto;
import com.project.safetyFence.geofence.dto.GeofenceDeleteRequestDto;
import com.project.safetyFence.geofence.dto.GeofenceRequestDto;
import com.project.safetyFence.geofence.dto.GeofenceResponseDto;
import com.project.safetyFence.geofence.GeofenceService;
import com.project.safetyFence.mypage.dto.NumberRequestDto;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
public class GeofenceController {

    private final GeofenceService geofenceService;

    @PostMapping("/geofence/list")
    public ResponseEntity<List<GeofenceResponseDto>> findGeofenceList(HttpServletRequest request, @RequestBody(required = false)NumberRequestDto numberRequestDto) {

        String userNumber = (numberRequestDto != null && numberRequestDto.getNumber() != null)
                ? numberRequestDto.getNumber()
                : (String) request.getAttribute("userNumber");
        List<GeofenceResponseDto> responseDtos = new ArrayList<>();
        List<Geofence> geofences = geofenceService.findGeofenceByNumber(userNumber);

        for (Geofence geofence : geofences) {
            Long id = geofence.getId();
            String name = geofence.getName();
            String address = geofence.getAddress();
            BigDecimal latitude = BigDecimal.valueOf(geofence.getLatitude());
            BigDecimal longitude = BigDecimal.valueOf(geofence.getLongitude());
            int type = geofence.getType();
            LocalDateTime startTime = geofence.getStartTime();
            LocalDateTime endTime = geofence.getEndTime();
            int maxSequence = geofence.getMaxSequence();

            GeofenceResponseDto responseDto = new GeofenceResponseDto(id, name, address, latitude, longitude, type, startTime, endTime, maxSequence);
            responseDtos.add(responseDto);
        }

        return ResponseEntity.ok(responseDtos);
    }

    // TODO: 지오펜스 진입 처리 시 응답 관련 기능 추가 필요
    // -> 이러면 지속적으로 알림이 간다 + ?
    @PostMapping("/geofence/userFenceIn")
    public ResponseEntity<String> userFenceIn(@RequestBody FenceInRequestDto fenceInRequestDto,
                                             HttpServletRequest request) {
        String userNumber = (String) request.getAttribute("userNumber");
        geofenceService.userFenceIn(userNumber, fenceInRequestDto.getGeofenceId());
        return ResponseEntity.ok("사용자의 진입이 성공적으로 감지되었습니다.");
    }

    @PostMapping("/geofence/newFence")
    public ResponseEntity<String> createNewFence(@Valid @RequestBody GeofenceRequestDto geofenceRequestDto,
                                                 HttpServletRequest request) {
        String userNumber = (geofenceRequestDto.getNumber() != null)
                ? geofenceRequestDto.getNumber()
                : (String) request.getAttribute("userNumber");

        geofenceService.createNewFence(userNumber, geofenceRequestDto);
        return ResponseEntity.ok("새로운 지오펜스가 성공적으로 생성되었습니다.");
    }

    @DeleteMapping("/geofence/deleteFence")
    public ResponseEntity<String> deleteFence(@RequestBody GeofenceDeleteRequestDto geofenceRequestDto
            , HttpServletRequest request) {

        String userNumber = (geofenceRequestDto.getNumber() != null)
                ? geofenceRequestDto.getNumber()
                : (String) request.getAttribute("userNumber");

        geofenceService.deleteFence(userNumber, geofenceRequestDto.getId());
        return ResponseEntity.ok("지오펜스가 성공적으로 삭제되었습니다.");
    }

}
