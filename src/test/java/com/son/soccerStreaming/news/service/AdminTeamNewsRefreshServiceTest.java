package com.son.soccerStreaming.news.service;

import com.son.soccerStreaming.global.exception.CustomException;
import com.son.soccerStreaming.global.exception.ErrorCode;
import com.son.soccerStreaming.global.externalapi.ExternalApiErrorCategory;
import com.son.soccerStreaming.global.externalapi.ExternalApiException;
import com.son.soccerStreaming.global.externalapi.ExternalApiProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminTeamNewsRefreshServiceTest {

    @Mock NewsCollectionService collectionService;
    @Mock NewsTitleTranslationService translationService;

    @Test
    void collectsAndTranslatesOnlyRequestedTeam() {
        when(collectionService.collectTeam(42L)).thenReturn(8);
        when(translationService.translatePendingForTeam(42L))
                .thenReturn(new NewsTitleTranslationService.TranslationRunResult(3, 2, 1));

        var result = new AdminTeamNewsRefreshService(collectionService, translationService).refresh(42L);

        assertThat(result).isEqualTo(new AdminTeamNewsRefreshService.RefreshResult(8, 3, 2, 1));
    }

    @Test
    void replacesExternalClientFailureWithSanitizedError() {
        when(collectionService.collectTeam(42L))
                .thenThrow(new IllegalStateException("https://serpapi.example?api_key=secret"));

        assertThatThrownBy(() -> new AdminTeamNewsRefreshService(collectionService, translationService).refresh(42L))
                .isInstanceOfSatisfying(CustomException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.NEWS_REFRESH_FAILED))
                .hasMessageNotContaining("secret");
    }

    @Test
    void reportsExternalApiQuotaExhaustionAsTooManyRequests() {
        when(collectionService.collectTeam(42L))
                .thenThrow(externalApiFailure(ExternalApiErrorCategory.QUOTA_EXHAUSTED, false));

        assertThatThrownBy(() -> new AdminTeamNewsRefreshService(collectionService, translationService).refresh(42L))
                .isInstanceOfSatisfying(CustomException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.EXTERNAL_API_QUOTA_EXHAUSTED);
                    assertThat(exception.getErrorCode().getStatus().value()).isEqualTo(429);
                })
                .hasMessageNotContaining("secret");
    }

    @Test
    void reportsExternalApiRateLimitAsTooManyRequests() {
        when(collectionService.collectTeam(42L))
                .thenThrow(externalApiFailure(ExternalApiErrorCategory.RATE_LIMITED, true));

        assertThatThrownBy(() -> new AdminTeamNewsRefreshService(collectionService, translationService).refresh(42L))
                .isInstanceOfSatisfying(CustomException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.EXTERNAL_API_RATE_LIMITED);
                    assertThat(exception.getErrorCode().getStatus().value()).isEqualTo(429);
                })
                .hasMessageNotContaining("secret");
    }

    @Test
    void keepsOtherExternalApiFailuresAsGenericNewsRefreshFailure() {
        when(collectionService.collectTeam(42L))
                .thenThrow(externalApiFailure(ExternalApiErrorCategory.UPSTREAM_SERVER, true));

        assertThatThrownBy(() -> new AdminTeamNewsRefreshService(collectionService, translationService).refresh(42L))
                .isInstanceOfSatisfying(CustomException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.NEWS_REFRESH_FAILED))
                .hasMessageNotContaining("secret");
    }

    private ExternalApiException externalApiFailure(ExternalApiErrorCategory category, boolean retryable) {
        return new ExternalApiException(
                ExternalApiProvider.SERP_API,
                "searchTeamNews",
                category,
                429,
                retryable,
                Duration.ofSeconds(30),
                "External API request failed",
                new IllegalStateException("https://serpapi.example?api_key=secret")
        );
    }
}
