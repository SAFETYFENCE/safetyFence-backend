package com.project.safetyFence.admin;

import com.project.safetyFence.admin.dto.StatisticsResponseDto;
import com.project.safetyFence.admin.dto.UserResponseDto;
import com.project.safetyFence.admin.dto.AdminLinkResponseDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    /**
     * 전체 사용자 목록 조회
     * GET /admin/users
     */
    @GetMapping("/users")
    public ResponseEntity<List<UserResponseDto>> getAllUsers() {
        log.info("📋 관리자 요청: 전체 사용자 목록 조회");
        List<UserResponseDto> users = adminService.getAllUsers();
        return ResponseEntity.ok(users);
    }

    /**
     * 사용자 삭제
     * DELETE /admin/users/{userNumber}
     */
    @DeleteMapping("/users/{userNumber}")
    public ResponseEntity<String> deleteUser(@PathVariable String userNumber) {
        log.info("🗑️ 관리자 요청: 사용자 삭제 - {}", userNumber);
        adminService.deleteUser(userNumber);
        return ResponseEntity.ok("사용자가 삭제되었습니다");
    }

    /**
     * 비밀번호 초기화
     * POST /admin/users/{userNumber}/reset-password
     */
    @PostMapping("/users/{userNumber}/reset-password")
    public ResponseEntity<String> resetPassword(@PathVariable String userNumber) {
        log.info("🔑 관리자 요청: 비밀번호 초기화 - {}", userNumber);
        String newPassword = adminService.resetPassword(userNumber);
        return ResponseEntity.ok("새 비밀번호: " + newPassword);
    }

    /**
     * 전체 링크 목록 조회
     * GET /admin/links
     */
    @GetMapping("/links")
    public ResponseEntity<List<AdminLinkResponseDto>> getAllLinks() {
        log.info("🔗 관리자 요청: 전체 링크 목록 조회");
        List<AdminLinkResponseDto> links = adminService.getAllLinks();
        return ResponseEntity.ok(links);
    }

    /**
     * 링크 삭제
     * DELETE /admin/links/{linkId}
     */
    @DeleteMapping("/links/{linkId}")
    public ResponseEntity<String> deleteLink(@PathVariable Long linkId) {
        log.info("🗑️ 관리자 요청: 링크 삭제 - {}", linkId);
        adminService.deleteLink(linkId);
        return ResponseEntity.ok("링크가 삭제되었습니다");
    }

    /**
     * 시스템 통계 조회
     * GET /admin/statistics
     */
    @GetMapping("/statistics")
    public ResponseEntity<StatisticsResponseDto> getStatistics() {
        log.info("📊 관리자 요청: 시스템 통계 조회");
        StatisticsResponseDto statistics = adminService.getStatistics();
        return ResponseEntity.ok(statistics);
    }

    /**
     * 만료된 지오펜스 삭제
     * POST /admin/cleanup/expired-geofences
     */
    @PostMapping("/cleanup/expired-geofences")
    public ResponseEntity<String> deleteExpiredGeofences() {
        log.info("🧹 관리자 요청: 만료된 지오펜스 삭제");
        int count = adminService.deleteExpiredGeofences();
        return ResponseEntity.ok(count + "개의 만료된 지오펜스가 삭제되었습니다");
    }

    /**
     * 오래된 로그 정리
     * POST /admin/cleanup/old-logs
     */
    @PostMapping("/cleanup/old-logs")
    public ResponseEntity<String> deleteOldLogs(@RequestParam(defaultValue = "3") int monthsOld) {
        log.info("🧹 관리자 요청: {}개월 이상 오래된 로그 정리", monthsOld);
        int count = adminService.deleteOldLogs(monthsOld);
        return ResponseEntity.ok(count + "개의 오래된 로그가 삭제되었습니다");
    }
}
