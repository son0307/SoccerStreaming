package com.son.soccerStreaming.apifootball.scheduler;

import java.util.Objects;

/**
 * 하나의 Retry Batch 안에서 독립적으로 성공하거나 실패할 수 있는 최소 재시도 단위를 나타낸다.
 *
 * @param retryKey 재시도 단위를 식별하는 안정적인 키
 * @param description 운영 로그에서 재시도 대상을 설명하는 문구
 * @param retryAction 실제 동기화를 다시 실행하는 작업
 */
public record ApiFootballRetryUnit(
        String retryKey,
        String description,
        Runnable retryAction
) {

    /**
     * 필수 값이 빠진 재시도 단위가 스케줄러에 들어오지 않도록 생성 시점에 검증한다.
     */
    public ApiFootballRetryUnit {
        if (retryKey == null || retryKey.isBlank()) {
            throw new IllegalArgumentException("retryKey must not be blank.");
        }
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("description must not be blank.");
        }
        Objects.requireNonNull(retryAction, "retryAction must not be null.");
    }
}
