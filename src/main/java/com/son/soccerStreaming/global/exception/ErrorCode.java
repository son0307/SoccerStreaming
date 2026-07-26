package com.son.soccerStreaming.global.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum ErrorCode {

    // 400 BAD_REQUEST
    INVALID_AUTH_REQUEST(HttpStatus.BAD_REQUEST, "이메일, 비밀번호, 닉네임을 확인해 주세요. 비밀번호는 8자 이상이어야 합니다."),
    INVALID_CURRENT_PASSWORD(HttpStatus.BAD_REQUEST, "현재 비밀번호가 일치하지 않습니다."),
    INVALID_ADMIN_OVERRIDE_FIELD(HttpStatus.BAD_REQUEST, "Invalid admin override field."),
    INVALID_ADMIN_EVENT_FIELD(HttpStatus.BAD_REQUEST, "Invalid admin event field."),
    INVALID_ADMIN_EDIT_FIELD(HttpStatus.BAD_REQUEST, "필수 입력 항목은 비워둘 수 없습니다."),
    INVALID_ADMIN_SEARCH_KEYWORD(HttpStatus.BAD_REQUEST, "Admin search keyword is too long."),
    INVALID_ADMIN_SYNC_COVERAGE(HttpStatus.BAD_REQUEST, "This season does not support the requested API-Football data."),
    ADMIN_SYNC_STANDINGS_REQUIRED(HttpStatus.BAD_REQUEST, "선수 동기화 전에 해당 시즌의 팀과 순위를 먼저 동기화해 주세요."),
    INVALID_ADMIN_MEDIA_REQUEST(HttpStatus.BAD_REQUEST, "이미지는 PNG, JPEG, WebP 형식의 2MB 이하 파일만 업로드할 수 있습니다."),
    INVALID_ADMIN_MEDIA_OBJECT(HttpStatus.BAD_REQUEST, "업로드된 이미지가 요청한 대상 또는 파일 조건과 일치하지 않습니다."),
    ADMIN_SYNC_TOO_FREQUENT(HttpStatus.TOO_MANY_REQUESTS, "Manual sync was requested too frequently. Please wait before retrying."),

    // 429 TOO_MANY_REQUESTS
    EXTERNAL_API_RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "외부 API 호출 제한에 도달했습니다. 잠시 후 다시 시도해 주세요."),
    EXTERNAL_API_QUOTA_EXHAUSTED(HttpStatus.TOO_MANY_REQUESTS, "외부 API 사용량이 소진되었습니다. 사용량 또는 요금제를 확인해 주세요."),

    // 401 UNAUTHORIZED
    LOGIN_FAILED(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다."),
    AUTHENTICATION_REQUIRED(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."),

    // 404 NOT_FOUND
    PLAYER_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 선수를 찾을 수 없습니다."),
    TEAM_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 팀을 찾을 수 없습니다."),
    VENUE_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 경기장을 찾을 수 없습니다."),
    FIXTURE_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 경기를 찾을 수 없습니다."),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."),
    ADMIN_SYNC_JOB_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 관리자 동기화 작업을 찾을 수 없습니다."),
    ADMIN_MEDIA_OBJECT_NOT_FOUND(HttpStatus.NOT_FOUND, "업로드된 이미지 객체를 찾을 수 없습니다."),
    NEWS_ARTICLE_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 팀의 뉴스 기사를 찾을 수 없습니다."),

    // 503 SERVICE_UNAVAILABLE
    ADMIN_MEDIA_STORAGE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "이미지 저장소를 사용할 수 없습니다."),
    NEWS_REFRESH_FAILED(HttpStatus.BAD_GATEWAY, "뉴스를 새로고침하지 못했습니다. 잠시 후 다시 시도해 주세요."),
    NEWS_TRANSLATION_FAILED(HttpStatus.BAD_GATEWAY, "뉴스 제목을 번역하지 못했습니다. 잠시 후 다시 시도해 주세요."),

    // 500 INTERNAL_SERVER_ERROR
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "예기치 못한 서버 오류가 발생했습니다."),

    // 409 CONFLICT
    ADMIN_SYNC_ALREADY_RUNNING(HttpStatus.CONFLICT, "동일한 동기화 작업이 이미 대기 중이거나 실행 중입니다."),
    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 가입된 이메일입니다.");

    private final HttpStatus status;
    private final String message;
}
