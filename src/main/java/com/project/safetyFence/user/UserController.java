package com.project.safetyFence.user;

import com.project.safetyFence.user.domain.User;
import com.project.safetyFence.user.dto.DeleteAccountRequestDto;
import com.project.safetyFence.user.dto.SignInRequestDto;
import com.project.safetyFence.user.dto.SignUpRequestDto;
import com.project.safetyFence.common.exception.CustomException;
import com.project.safetyFence.common.exception.ErrorResult;
import com.project.safetyFence.user.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping("/user/signIn")
    public ResponseEntity<Map<String, Object>> signIn(@RequestBody SignInRequestDto signInRequestDto) {
        String number = signInRequestDto.getNumber();
        String password = signInRequestDto.getPassword();

        if (!userService.checkExistNumber(number)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("message", "로그인 실패"));
        }

        User user = userService.findByNumber(number);
        String userPassword = user.getPassword();

        if (!userPassword.equals(password)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("message", "로그인 실패"));
        }

        // API Key 발급 (이미 있으면 재사용, 없으면 새로 생성)
        String apiKey = user.getApiKey();
        if (apiKey == null || apiKey.isEmpty()) {
            apiKey = userService.generateAndSaveApiKey(user.getNumber());
        }

        Map<String, Object> response = new HashMap<>();
        response.put("message", "login success");
        response.put("apiKey", apiKey);
        response.put("name", user.getName());
        response.put("number", user.getNumber());

        return ResponseEntity.ok(response);
    }

    @PostMapping("/user/signup")
    public ResponseEntity<Map<String, Object>> signUp(@RequestBody SignUpRequestDto signUpRequestDto) {

        log.info("사용자 신규 회원가입 요청");

        if (userService.checkDuplicateNumber(signUpRequestDto.getNumber())) {
            throw new CustomException(ErrorResult.USER_NUMBER_DUPLICATE);
        }

        User user = userService.registerUser(signUpRequestDto);

        Map<String, Object> response = new HashMap<>();
        response.put("name", user.getName());
        response.put("number", user.getNumber());

        log.info("사용자 이름: " + user.getName() + ", 성공적으로 회원가입 완료되었습니다.");

        return ResponseEntity.ok(response);
    }

    /**
     * 사용자 본인 계정 삭제
     * DELETE /api/user/delete-account
     * X-API-KEY 헤더를 통해 인증된 사용자만 자신의 계정 삭제 가능
     * 추가 보안을 위해 비밀번호 재확인 필요
     */
    @DeleteMapping("/api/user/delete-account")
    public ResponseEntity<String> deleteOwnAccount(
            HttpServletRequest request,
            @RequestBody DeleteAccountRequestDto deleteAccountRequestDto) {

        String userNumber = (String) request.getAttribute("userNumber");

        if (userNumber == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body("인증이 필요합니다");
        }

        String password = deleteAccountRequestDto.getPassword();
        if (password == null || password.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body("비밀번호를 입력해주세요");
        }

        log.info("🗑️ 계정 삭제 요청: {}", userNumber);

        try {
            userService.deleteOwnAccount(userNumber, password);
            return ResponseEntity.ok("계정이 삭제되었습니다");
        } catch (IllegalArgumentException e) {
            log.warn("⚠️ 계정 삭제 실패: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(e.getMessage());
        } catch (Exception e) {
            log.error("계정 삭제 실패: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("계정 삭제 중 오류가 발생했습니다");
        }
    }

}
