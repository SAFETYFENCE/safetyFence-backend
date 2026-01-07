package com.project.safetyFence.user;

import com.project.safetyFence.geofence.domain.Geofence;
import com.project.safetyFence.geofence.service.InitialGeofenceCreator;
import com.project.safetyFence.user.domain.User;
import com.project.safetyFence.user.domain.UserAddress;
import com.project.safetyFence.user.dto.SignUpRequestDto;
import com.project.safetyFence.link.generator.LinkCodeGenerator;
import com.project.safetyFence.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final LinkCodeGenerator linkCodeGenerator;
    private final UserRepository userRepository;
    private final UserAddressService userAddressService;
    private final InitialGeofenceCreator initialGeofenceCreator; // 인터페이스로 변경

    public boolean checkExistNumber(String number) {
        return userRepository.existsByNumber(number);
    }

    public User findByNumber(String number) {
        return userRepository.findByNumber(number);
    }

    public boolean checkDuplicateNumber (String number) {
        return userRepository.existsByNumber(number);
    }

    @Transactional
    public User registerUser(SignUpRequestDto signUpRequestDto) {
        // user 저장
        User user = makeUserEntity(signUpRequestDto);
        // userAddress 저장
        UserAddress userAddress = userAddressService.makeUserAddressEntity(signUpRequestDto, user);
        user.addUserAddress(userAddress);

        // user 주소로 geofence 생성
        Geofence homeGeofence = initialGeofenceCreator.createHomeGeofence(userAddress);
        user.addGeofence(homeGeofence);

        // center 주소가 있을 경우에만 geofence 생성
        if (signUpRequestDto.getCenterStreetAddress() != null && !signUpRequestDto.getCenterStreetAddress().isEmpty()) {
            Geofence centerGeofence = initialGeofenceCreator.createCenterGeofence(userAddress);
            user.addGeofence(centerGeofence);
        }

        // User 저장 시 cascade로 UserAddress, Geofence도 자동 저장됨
        userRepository.save(user);

        return user;
    }


    public User makeUserEntity(SignUpRequestDto signUpRequestDto) {
        // linkCode 생성
        String linkCode = linkCodeGenerator.generate();

        // linkCode 중복 검사
        String uniqueLinkCode = checkLinkCodeDuplicate(linkCode);

        String number = signUpRequestDto.getNumber();
        String name = signUpRequestDto.getName();
        String password = signUpRequestDto.getPassword();
        String birthString = signUpRequestDto.getBirth();

        // DTO의 String -> Java의 LocalDate로 변환
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        LocalDate birthDate = LocalDate.parse(birthString, formatter); // 여기서 변환

        User user = new User(number, name, password, birthDate, uniqueLinkCode);

        return user;
    }

    private String checkLinkCodeDuplicate(String firstLinkCode){
        String linkCode = firstLinkCode;
        while (userRepository.existsByLinkCode(linkCode)){
            linkCode = linkCodeGenerator.generate();
        }
        return linkCode;
    }

    // API Key 생성 및 저장
    @Transactional
    public String generateAndSaveApiKey(String userNumber) {
        User user = userRepository.findByNumber(userNumber);

        // 안전한 API Key 생성
        String apiKey = generateSecureApiKey();

        // 중복 체크
        while (userRepository.existsByApiKey(apiKey)) {
            apiKey = generateSecureApiKey();
        }

        user.updateApiKey(apiKey);
        userRepository.save(user);

        return apiKey;
    }

    // API Key로 사용자 번호 조회
    public String findUserNumberByApiKey(String apiKey) {
        User user = userRepository.findByApiKey(apiKey);
        return user != null ? user.getNumber() : null;
    }

    // 안전한 API Key 생성 (UUID 사용)
    private String generateSecureApiKey() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    // 사용자 본인 계정 삭제
    @Transactional
    public void deleteOwnAccount(String userNumber, String password) {
        User user = userRepository.findByNumber(userNumber);
        if (user == null) {
            throw new IllegalArgumentException("사용자를 찾을 수 없습니다");
        }

        // 비밀번호 검증
        if (!user.getPassword().equals(password)) {
            log.warn("⚠️ 계정 삭제 실패 - 비밀번호 불일치: {}", userNumber);
            throw new IllegalArgumentException("비밀번호가 일치하지 않습니다");
        }

        log.info("🗑️ 사용자 본인 계정 삭제: {} ({})", user.getName(), userNumber);

        // CASCADE 설정으로 모든 연관 데이터 자동 삭제
        // UserAddress, UserLocation, Log, Link, Geofence, UserEvent, Medication (+ MedicationLog)
        userRepository.delete(user);

        log.info("✅ 계정 및 연관 데이터 삭제 완료: {}", userNumber);
    }

}
