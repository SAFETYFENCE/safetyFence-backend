package com.project.safetyFence.common.util.kakaoApi.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class KakaoAddressResponseDto {

    @JsonProperty("documents")
    private List<DocumentDto> documents;

    public DocumentDto getFirstDocument() {
        if (documents == null || documents.isEmpty()) {
            return null;
        }
        return documents.get(0);
    }

    public boolean hasDocuments() {
        return documents != null && !documents.isEmpty();
    }

    @Getter
    @NoArgsConstructor
    public static class DocumentDto {

        @JsonProperty("x")
        private String longitude; // 경도

        @JsonProperty("y")
        private String latitude;  // 위도
    }
}
