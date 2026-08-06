package com.son.soccerStreaming.news.service;

import com.son.soccerStreaming.news.client.SerpApiNewsClient;
import com.son.soccerStreaming.global.exception.CustomException;
import com.son.soccerStreaming.global.exception.ErrorCode;
import com.son.soccerStreaming.global.externalapi.ExternalApiErrorCategory;
import com.son.soccerStreaming.global.externalapi.ExternalApiException;
import com.son.soccerStreaming.team.entity.Team;
import com.son.soccerStreaming.team.repository.TeamRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import com.son.soccerStreaming.global.externalapi.ExternalApiInvocationContext;

@Slf4j
@Service
@RequiredArgsConstructor
public class NewsCollectionService {

    private final TeamRepository teamRepository;
    private final SerpApiNewsClient serpApiNewsClient;
    private final NewsPersistenceService newsPersistenceService;

    public CollectionResult collectAllTeams() {
        List<Team> teams = teamRepository.findAllByOrderByNameAsc();
        int succeededTeams = 0;
        int failedTeams = 0;
        int savedArticles = 0;
        List<FailedTeam> failures = new ArrayList<>();

        for (Team team : teams) {
            try {
                List<SerpApiNewsClient.SearchArticle> articles = serpApiNewsClient.searchTeamNews(team.getName());
                savedArticles += newsPersistenceService.saveTeamArticles(team.getTeamId(), articles, Instant.now());
                succeededTeams++;
            } catch (Exception e) {
                failedTeams++;
                failures.add(failedTeam(team, e));
                // SerpApi authenticates with a query parameter, so never log the exception URL.
                log.warn("Team news collection failed. teamId={}, teamName={}, errorType={}",
                        team.getTeamId(), team.getName(), e.getClass().getSimpleName());
            }
        }

        return new CollectionResult(teams.size(), succeededTeams, failedTeams, savedArticles, List.copyOf(failures));
    }

    public int collectTeam(Long teamId) {
        return collectTeam(teamId, ExternalApiInvocationContext.system());
    }

    public int collectTeam(Long teamId, ExternalApiInvocationContext context) {
        Team team = teamRepository.findByTeamId(teamId)
                .orElseThrow(() -> new CustomException(ErrorCode.TEAM_NOT_FOUND));
        List<SerpApiNewsClient.SearchArticle> articles = serpApiNewsClient.searchTeamNews(team.getName(), context);
        return newsPersistenceService.saveTeamArticles(team.getTeamId(), articles, Instant.now());
    }

    private FailedTeam failedTeam(Team team, Exception exception) {
        ExternalApiException externalFailure = externalApiException(exception);
        return new FailedTeam(
                team.getTeamId(),
                team.getName(),
                externalFailure != null ? externalFailure.getCategory() : ExternalApiErrorCategory.UNKNOWN,
                externalFailure != null && externalFailure.isRetryable(),
                externalFailure != null ? externalFailure.getRetryAfter() : null
        );
    }

    private ExternalApiException externalApiException(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof ExternalApiException externalApiException) {
                return externalApiException;
            }
            current = current.getCause();
        }
        return null;
    }

    public record CollectionResult(
            int totalTeams,
            int succeededTeams,
            int failedTeams,
            int savedArticles,
            List<FailedTeam> failures
    ) {
        public CollectionResult {
            failures = failures == null ? List.of() : List.copyOf(failures);
        }
    }

    public record FailedTeam(
            Long teamId,
            String teamName,
            ExternalApiErrorCategory category,
            boolean retryable,
            Duration retryAfter
    ) {
    }
}
