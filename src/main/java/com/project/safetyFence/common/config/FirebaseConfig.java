package com.project.safetyFence.common.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;

@Slf4j
@Configuration
public class FirebaseConfig {

    @PostConstruct
    public void initialize() {
        try {
            if (FirebaseApp.getApps().isEmpty()) {
                FirebaseOptions options = FirebaseOptions.builder()
                        .setCredentials(GoogleCredentials.fromStream(
                                new ClassPathResource("firebase-service-account.json").getInputStream()))
                        .build();

                FirebaseApp.initializeApp(options);
                log.info("✅ Firebase Admin SDK 초기화 완료");
            }
        } catch (IOException e) {
            log.error("❌ Firebase Admin SDK 초기화 실패", e);
            throw new RuntimeException("Firebase 초기화 실패", e);
        }
    }
}