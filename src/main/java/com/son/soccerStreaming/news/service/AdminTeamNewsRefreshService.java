package com.son.soccerStreaming.news.service;

import com.son.soccerStreaming.global.exception.CustomException;
import com.son.soccerStreaming.global.exception.ErrorCode;
import com.son.soccerStreaming.global.externalapi.ExternalApiErrorCategory;
import com.son.soccerStreaming.global.externalapi.ExternalApiException;
import com.son.soccerStreaming.global.externalapi.ExternalApiInvocationContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class AdminTeamNewsRefreshService {

    private final NewsCollectionService collectionService;
    private final NewsTitleTranslationService translationService;
    private final Set<Long> refreshingTeamIds = ConcurrentHashMap.newKeySet();

    public RefreshResult refresh(Long teamId) {
        return refresh(null, teamId);
    }

    public RefreshResult refresh(Long adminUserId, Long teamId) {
        if (!refreshingTeamIds.add(teamId)) {
            throw new CustomException(ErrorCode.ADMIN_SYNC_TOO_FREQUENT);
        }
        try {
            int collectedArticles = adminUserId == null
                    ? collectionService.collectTeam(teamId)
                    : collectionService.collectTeam(teamId,
                    ExternalApiInvocationContext.admin(adminUserId, teamId, null, null));
            NewsTitleTranslationService.TranslationRunResult translation = adminUserId == null
                    ? translationService.translatePendingForTeam(teamId)
                    : translationService.translatePendingForTeam(teamId, adminUserId);
            return new RefreshResult(
                    collectedArticles,
                    translation.candidates(),
                    translation.translated(),
                    translation.failed()
            );
        } catch (ExternalApiException e) {
            throw new CustomException(errorCodeFor(e), e);
        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            // Do not propagate a RestClient exception because the SerpApi URL contains the API key.
            throw new CustomException(ErrorCode.NEWS_REFRESH_FAILED);
        } finally {
            refreshingTeamIds.remove(teamId);
        }
    }

    private ErrorCode errorCodeFor(ExternalApiException exception) {
        if (exception.getCategory() == ExternalApiErrorCategory.QUOTA_EXHAUSTED) {
            return ErrorCode.EXTERNAL_API_QUOTA_EXHAUSTED;
        }
        if (exception.getCategory() == ExternalApiErrorCategory.RATE_LIMITED) {
            return ErrorCode.EXTERNAL_API_RATE_LIMITED;
        }
        return ErrorCode.NEWS_REFRESH_FAILED;
    }

    public record RefreshResult(
            int collectedArticles,
            int translationCandidates,
            int translatedArticles,
            int failedTranslations
    ) {
    }
}
