package com.son.soccerStreaming.apifootball.scheduler;

import java.util.List;
import java.util.Objects;

/**
 * 한 번의 동기화 실행에서 발생한 모든 실패 단위를 하나의 UUID Batch로 등록하기 위한 요청이다.
 *
 * @param executionKey 같은 종류의 동기화가 동시에 실행되지 않도록 묶는 논리 키
 * @param description Batch 전체를 설명하는 운영 로그 문구
 * @param scope 전체 작업 재시도인지 일부 단위 재시도인지 나타내는 범위
 * @param initialFailure Batch를 만들게 된 최초 동기화 예외
 * @param units Batch에 포함할 팀, Chunk 또는 전체 작업 재시도 단위
 */
public record ApiFootballRetryBatchRequest(
        String executionKey,
        String description,
        ApiFootballRetryScope scope,
        Exception initialFailure,
        List<ApiFootballRetryUnit> units
) {

    /**
     * Batch 메타데이터와 재시도 단위를 불변 복사하여 실행 중 외부 변경을 막는다.
     */
    public ApiFootballRetryBatchRequest {
        if (executionKey == null || executionKey.isBlank()) {
            throw new IllegalArgumentException("executionKey must not be blank.");
        }
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("description must not be blank.");
        }
        Objects.requireNonNull(scope, "scope must not be null.");
        Objects.requireNonNull(initialFailure, "initialFailure must not be null.");
        units = List.copyOf(units);
        if (units.isEmpty()) {
            throw new IllegalArgumentException("units must not be empty.");
        }
    }

    /**
     * 전체 동기화를 다시 실행하는 단일 Unit Batch 요청을 만든다.
     */
    public static ApiFootballRetryBatchRequest wholeTask(
            String executionKey,
            String description,
            Exception initialFailure,
            ApiFootballRetryUnit unit
    ) {
        return new ApiFootballRetryBatchRequest(
                executionKey,
                description,
                ApiFootballRetryScope.WHOLE_TASK,
                initialFailure,
                List.of(unit)
        );
    }

    /**
     * 최초 동기화에서 실패한 팀이나 Chunk만 묶은 부분 재시도 Batch 요청을 만든다.
     */
    public static ApiFootballRetryBatchRequest partialUnits(
            String executionKey,
            String description,
            Exception initialFailure,
            List<ApiFootballRetryUnit> units
    ) {
        return new ApiFootballRetryBatchRequest(
                executionKey,
                description,
                ApiFootballRetryScope.PARTIAL_UNITS,
                initialFailure,
                units
        );
    }
}
