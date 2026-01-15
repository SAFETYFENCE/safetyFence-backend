package com.project.safetyFence.location;

import com.project.safetyFence.location.dto.DailyDistanceResponseDto;
import com.project.safetyFence.mypage.dto.NumberRequestDto;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/location")
public class LocationController {

    private final LocationService locationService;

    /**
     * 일일 이동거리 조회 (도보만)
     * - 이용자: 본인의 오늘 이동거리
     * - 보호자: 요청 body에 number를 넣으면 해당 이용자의 이동거리 조회
     *
     * @param request HTTP 요청 (userNumber 추출)
     * @param numberRequestDto 선택적 - 조회할 이용자 번호 (보호자용)
     * @return 일일 이동거리 정보
     */
    @PostMapping("/daily-distance")
    public ResponseEntity<DailyDistanceResponseDto> getDailyDistance(
            HttpServletRequest request,
            @RequestBody(required = false) NumberRequestDto numberRequestDto) {

        // 조회할 사용자 번호 결정 (보호자가 이용자 번호를 넣으면 해당 이용자, 아니면 본인)
        String userNumber = (numberRequestDto != null && numberRequestDto.getNumber() != null)
                ? numberRequestDto.getNumber()
                : (String) request.getAttribute("userNumber");

        if (userNumber == null || userNumber.isBlank()) {
            log.error("일일 이동거리 조회 실패: userNumber 없음");
            return ResponseEntity.badRequest().build();
        }

        // 오늘 날짜 기준 조회
        LocalDate today = LocalDate.now();
        DailyDistanceResponseDto response = locationService.calculateDailyDistance(userNumber, today);

        return ResponseEntity.ok(response);
    }

    /**
     * 특정 날짜의 이동거리 조회
     *
     * @param request HTTP 요청
     * @param date 조회할 날짜 (yyyy-MM-dd)
     * @param numberRequestDto 선택적 - 조회할 이용자 번호 (보호자용)
     * @return 해당 날짜 이동거리 정보
     */
    @PostMapping("/daily-distance/{date}")
    public ResponseEntity<DailyDistanceResponseDto> getDailyDistanceByDate(
            HttpServletRequest request,
            @PathVariable String date,
            @RequestBody(required = false) NumberRequestDto numberRequestDto) {

        String userNumber = (numberRequestDto != null && numberRequestDto.getNumber() != null)
                ? numberRequestDto.getNumber()
                : (String) request.getAttribute("userNumber");

        if (userNumber == null || userNumber.isBlank()) {
            log.error("일일 이동거리 조회 실패: userNumber 없음");
            return ResponseEntity.badRequest().build();
        }

        LocalDate targetDate;
        try {
            targetDate = LocalDate.parse(date);
        } catch (Exception e) {
            log.error("잘못된 날짜 형식: {}", date);
            return ResponseEntity.badRequest().build();
        }

        DailyDistanceResponseDto response = locationService.calculateDailyDistance(userNumber, targetDate);

        return ResponseEntity.ok(response);
    }
}
