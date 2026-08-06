package com.son.soccerStreaming.apifootball.scheduler;

/**
 * 재시도 Batch가 전체 동기화를 대신하는지, 일부 실패 단위만 보완하는지를 구분한다.
 */
public enum ApiFootballRetryScope {
    /**
     * 최초 동기화가 전부 실패하여 전체 작업을 다시 실행한다.
     */
    WHOLE_TASK,

    /**
     * 최초 동기화에서 실패한 팀이나 Fixture Chunk만 다시 실행한다.
     */
    PARTIAL_UNITS
}
