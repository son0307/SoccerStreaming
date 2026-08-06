package com.son.soccerStreaming.news.service;

import com.son.soccerStreaming.news.client.SerpApiNewsClient;
import com.son.soccerStreaming.global.externalapi.ExternalApiErrorCategory;
import com.son.soccerStreaming.global.externalapi.ExternalApiException;
import com.son.soccerStreaming.global.externalapi.ExternalApiProvider;
import com.son.soccerStreaming.team.entity.Team;
import com.son.soccerStreaming.team.repository.TeamRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NewsCollectionServiceTest {

    @Mock TeamRepository teamRepository;
    @Mock SerpApiNewsClient serpApiNewsClient;
    @Mock NewsPersistenceService newsPersistenceService;
    @InjectMocks NewsCollectionService service;

    @Test
    void continuesWithNextTeamWhenOneSearchFails() {
        Team first = Team.builder().teamId(1L).name("Arsenal").build();
        Team second = Team.builder().teamId(2L).name("Chelsea").build();
        var article = new SerpApiNewsClient.SearchArticle(
                "Chelsea title", "https://bbc.com/sport/football/articles/1", "BBC Sport", Instant.now());
        when(teamRepository.findAllByOrderByNameAsc()).thenReturn(List.of(first, second));
        when(serpApiNewsClient.searchTeamNews("Arsenal")).thenThrow(new ExternalApiException(
                ExternalApiProvider.SERP_API,
                "search-team-news",
                ExternalApiErrorCategory.TIMEOUT,
                null,
                true,
                null,
                "failed",
                null
        ));
        when(serpApiNewsClient.searchTeamNews("Chelsea")).thenReturn(List.of(article));
        when(newsPersistenceService.saveTeamArticles(eq(2L), any(), any())).thenReturn(1);

        var result = service.collectAllTeams();

        assertThat(result.totalTeams()).isEqualTo(2);
        assertThat(result.succeededTeams()).isEqualTo(1);
        assertThat(result.failedTeams()).isEqualTo(1);
        assertThat(result.savedArticles()).isEqualTo(1);
        assertThat(result.failures()).containsExactly(new NewsCollectionService.FailedTeam(
                1L,
                "Arsenal",
                ExternalApiErrorCategory.TIMEOUT,
                true,
                null
        ));
        verify(newsPersistenceService).saveTeamArticles(eq(2L), eq(List.of(article)), any());
    }
}
