package com.son.soccerStreaming.apifootball.event;

import com.son.soccerStreaming.apifootball.service.ApiFootballStandingLocalUpdateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class StandingSyncCompletedListener {

    private final ApiFootballStandingLocalUpdateService localUpdateService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void reconcileFinishedImpacts(StandingSyncCompleted event) {
        try {
            localUpdateService.reconcileFinishedImpacts(event.league(), event.season());
        } catch (RuntimeException exception) {
            log.error("Finished standing impact reconciliation failed. league={}, season={}",
                    event.league(), event.season(), exception);
        }
    }
}
