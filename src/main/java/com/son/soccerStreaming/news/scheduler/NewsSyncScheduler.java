package com.son.soccerStreaming.news.scheduler;

import com.son.soccerStreaming.news.service.NewsCleanupService;
import com.son.soccerStreaming.news.service.NewsCollectionService;
import com.son.soccerStreaming.news.service.NewsTitleTranslationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "news.sync.enabled", havingValue = "true")
public class NewsSyncScheduler {

    private final NewsCollectionService collectionService;
    private final NewsCollectionFailureRetryScheduler collectionRetryScheduler;
    private final NewsTitleTranslationService translationService;
    private final NewsCleanupService cleanupService;

    @Scheduled(cron = "${news.sync.cron:0 0 6 * * *}", zone = "${news.sync.zone:Asia/Seoul}")
    public void syncNews() {
        try {
            NewsCollectionService.CollectionResult result = collectionService.collectAllTeams();
            collectionRetryScheduler.replacePendingRetries(result.failures());
            log.info("Team news collection completed. totalTeams={}, succeededTeams={}, failedTeams={}, savedArticles={}",
                    result.totalTeams(), result.succeededTeams(), result.failedTeams(), result.savedArticles());
        } catch (Exception e) {
            log.error("Team news collection run failed.", e);
        }

        try {
            NewsTitleTranslationService.TranslationRunResult result = translationService.translatePending();
            log.info("News title translation completed. candidates={}, translated={}, failed={}",
                    result.candidates(), result.translated(), result.failed());
        } catch (Exception e) {
            log.error("News title translation run failed.", e);
        }

        try {
            NewsCleanupService.CleanupResult result = cleanupService.cleanupExpired();
            log.info("News cleanup completed. deletedRelations={}, deletedArticles={}",
                    result.deletedRelations(), result.deletedArticles());
        } catch (Exception e) {
            log.error("News cleanup run failed.", e);
        }
    }
}
