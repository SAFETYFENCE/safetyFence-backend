package com.project.safetyFence.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorResult {

    USER_NUMBER_DUPLICATE(HttpStatus.BAD_REQUEST, "중복된 전화번호입니다."),
    LINK_USER_AND_SUPPORTER_DUPLICATE(HttpStatus.BAD_REQUEST, "이미 등록된 이용자와 보호자입니다."),
    NOT_EXIST_NUMBER(HttpStatus.BAD_REQUEST, "존재하지 않는 전화번호 입니다."),
    USER_NOT_MATCH_PASSWORD(HttpStatus.BAD_REQUEST, "비밀번호가 일치하지 않습니다."),
    LINK_CODE_NOT_EXIST(HttpStatus.BAD_REQUEST, "일치하는 유저 코드가 존재하지 않습니다."),
    NOT_EXIST_ZIPCODE(HttpStatus.BAD_REQUEST, "존재하지 않는 우편번호 입니다."),
    CANNOT_ADD_SELF_AS_LINK(HttpStatus.BAD_REQUEST, "자기 자신을 링크로 추가할 수 없습니다."),
    LINK_ALREADY_EXISTS(HttpStatus.BAD_REQUEST, "이미 링크에 추가된 사용자입니다."),
    LINK_NOT_FOUND(HttpStatus.BAD_REQUEST, "링크 삭제 중 문제가 발생했습니다."),
    GEOFENCE_ADDRESS_CONVERSION_FAILED(HttpStatus.BAD_REQUEST, "주소를 좌표로 변환하는데 실패했습니다.");

    private final HttpStatus httpStatus;
    private final String message;
}
