package com.son.soccerStreaming.team.service;

import com.son.soccerStreaming.apifootball.service.ApiFootballStandingLocalUpdateService;
import com.son.soccerStreaming.apifootball.service.ApiFootballStandingLocalUpdateService.LiveStandingImpact;
import com.son.soccerStreaming.fixture.entity.Fixture;
import com.son.soccerStreaming.fixture.repository.FixtureRepository;
import com.son.soccerStreaming.global.util.DateTimeUtils;
import com.son.soccerStreaming.media.service.MediaUrlService;
import com.son.soccerStreaming.team.dto.TeamStandingResponseDto;
import com.son.soccerStreaming.team.entity.Team;
import com.son.soccerStreaming.team.entity.TeamStanding;
import com.son.soccerStreaming.team.repository.TeamStandingRepository;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class TeamStandingService {

    private static final int PREMIER_LEAGUE_ID = 39;
    private static final List<String> FINISHED_FIXTURE_STATUSES = List.of("FINISHED", "FT", "AET", "PEN");

    private final TeamStandingRepository teamStandingRepository;
    private final ApiFootballStandingLocalUpdateService apiFootballStandingLocalUpdateService;
    private final FixtureRepository fixtureRepository;
    private final MediaUrlService mediaUrlService;

    public List<TeamStandingResponseDto> getStandings(Integer season) {
        List<StandingProjection> projections = teamStandingRepository
                .findAllByLeagueIdAndSeason(PREMIER_LEAGUE_ID, season)
                .stream()
                .map(standing -> StandingProjection.from(standing, mediaUrlService))
                .toList();

        Map<Long, TeamStandingResponseDto.LiveMatch> liveMatches = applyLiveImpacts(
                projections,
                apiFootballStandingLocalUpdateService.findImpacts(season)
        );
        refreshRanks(projections);
        Map<Long, RecentFormProjection> recentForms = findRecentForms(season, projections);
        Map<Long, TeamStandingResponseDto.NextMatch> nextMatches = findNextMatches(season, projections);

        return projections.stream()
                .sorted(Comparator.comparing(StandingProjection::getRank, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(standing -> toResponse(
                        standing,
                        recentForms.get(standing.getTeamId()),
                        liveMatches.get(standing.getTeamId()),
                        nextMatches.get(standing.getTeamId())
                ))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private TeamStandingResponseDto toResponse(StandingProjection standing, RecentFormProjection recentForm,
                                               TeamStandingResponseDto.LiveMatch liveMatch,
                                               TeamStandingResponseDto.NextMatch nextMatch) {
        return TeamStandingResponseDto.builder()
                .season(standing.getSeason())
                .rank(standing.getRank())
                .team(TeamStandingResponseDto.TeamInfo.builder()
                        .id(standing.getTeamId())
                        .name(standing.getTeamName())
                        .nameKo(standing.getTeamNameKo())
                        .logo(standing.getTeamLogoUrl())
                        .build())
                .points(standing.getPoints())
                .goalsDiff(standing.getGoalsDiff())
                .group(standing.getGroup())
                .form(standing.getForm())
                .status(standing.getStatus())
                .description(standing.getDescription())
                .all(toRecord(
                        standing.getPlayed(),
                        standing.getWin(),
                        standing.getDraw(),
                        standing.getLose(),
                        standing.getGoalsFor(),
                        standing.getGoalsAgainst()
                ))
                .home(toRecord(
                        standing.getHomePlayed(),
                        standing.getHomeWin(),
                        standing.getHomeDraw(),
                        standing.getHomeLose(),
                        standing.getHomeGoalsFor(),
                        standing.getHomeGoalsAgainst()
                ))
                .away(toRecord(
                        standing.getAwayPlayed(),
                        standing.getAwayWin(),
                        standing.getAwayDraw(),
                        standing.getAwayLose(),
                        standing.getAwayGoalsFor(),
                        standing.getAwayGoalsAgainst()
                ))
                .recentForm(toRecentForm(recentForm))
                .liveMatch(liveMatch)
                .nextMatch(nextMatch)
                .updatedAt(standing.getUpdatedAt())
                .build();
    }

    private Map<Long, TeamStandingResponseDto.NextMatch> findNextMatches(
            Integer season,
            List<StandingProjection> standings
    ) {
        Set<Long> standingTeamIds = standings.stream()
                .map(StandingProjection::getTeamId)
                .collect(Collectors.toCollection(HashSet::new));
        Map<Long, TeamStandingResponseDto.NextMatch> nextMatches = new HashMap<>();

        if (standingTeamIds.isEmpty()) {
            return nextMatches;
        }

        List<Fixture> upcomingFixtures = fixtureRepository.findUpcomingScheduledByLeagueAndSeason(
                PREMIER_LEAGUE_ID,
                season,
                LocalDateTime.now(ZoneOffset.UTC)
        );

        for (Fixture fixture : upcomingFixtures) {
            putNextMatchIfAbsent(nextMatches, standingTeamIds, fixture, fixture.getHomeTeam(), fixture.getAwayTeam(), "HOME");
            putNextMatchIfAbsent(nextMatches, standingTeamIds, fixture, fixture.getAwayTeam(), fixture.getHomeTeam(), "AWAY");

            if (nextMatches.size() == standingTeamIds.size()) {
                break;
            }
        }

        return nextMatches;
    }

    private void putNextMatchIfAbsent(
            Map<Long, TeamStandingResponseDto.NextMatch> nextMatches,
            Set<Long> standingTeamIds,
            Fixture fixture,
            Team team,
            Team opponent,
            String venue
    ) {
        if (!standingTeamIds.contains(team.getTeamId())) {
            return;
        }

        nextMatches.putIfAbsent(team.getTeamId(), TeamStandingResponseDto.NextMatch.builder()
                .fixtureId(fixture.getFixtureId())
                .fixtureDate(DateTimeUtils.utcToKorea(fixture.getFixtureDate()))
                .opponent(TeamStandingResponseDto.TeamInfo.builder()
                        .id(opponent.getTeamId())
                        .name(opponent.getName())
                        .nameKo(opponent.getKoreanName())
                        .logo(mediaUrlService.teamLogoUrl(opponent))
                        .build())
                .venue(venue)
                .build());
    }

    private Map<Long, RecentFormProjection> findRecentForms(Integer season, List<StandingProjection> standings) {
        Map<Long, RecentFormProjection> recentForms = new HashMap<>();
        standings.forEach(standing -> recentForms.put(standing.getTeamId(), new RecentFormProjection()));

        fixtureRepository.findFinishedWithScoresBySeasonOrderByFixtureDateDesc(season, FINISHED_FIXTURE_STATUSES)
                .stream()
                .forEach(fixture -> applyRecentFixture(recentForms, fixture));

        return recentForms;
    }

    private void applyRecentFixture(Map<Long, RecentFormProjection> recentForms, Fixture fixture) {
        Long homeTeamId = fixture.getHomeTeam().getTeamId();
        Long awayTeamId = fixture.getAwayTeam().getTeamId();

        RecentFormProjection homeForm = recentForms.get(homeTeamId);
        if (homeForm != null && homeForm.canApply()) {
            homeForm.apply(
                    fixture.getHomeScore(),
                    fixture.getAwayScore(),
                    toRecentMatch(fixture, fixture.getAwayTeam(), "HOME",
                            fixture.getHomeScore(), fixture.getAwayScore())
            );
        }

        RecentFormProjection awayForm = recentForms.get(awayTeamId);
        if (awayForm != null && awayForm.canApply()) {
            awayForm.apply(
                    fixture.getAwayScore(),
                    fixture.getHomeScore(),
                    toRecentMatch(fixture, fixture.getHomeTeam(), "AWAY",
                            fixture.getAwayScore(), fixture.getHomeScore())
            );
        }
    }

    private TeamStandingResponseDto.RecentMatch toRecentMatch(
            Fixture fixture,
            Team opponent,
            String venue,
            Integer scoreFor,
            Integer scoreAgainst
    ) {
        return TeamStandingResponseDto.RecentMatch.builder()
                .fixtureId(fixture.getFixtureId())
                .fixtureDate(DateTimeUtils.utcToKorea(fixture.getFixtureDate()))
                .opponent(TeamStandingResponseDto.TeamInfo.builder()
                        .id(opponent.getTeamId())
                        .name(opponent.getName())
                        .nameKo(opponent.getKoreanName())
                        .logo(mediaUrlService.teamLogoUrl(opponent))
                        .build())
                .venue(venue)
                .scoreFor(scoreFor)
                .scoreAgainst(scoreAgainst)
                .result(recentResultOf(scoreFor, scoreAgainst))
                .build();
    }

    private String recentResultOf(Integer scoreFor, Integer scoreAgainst) {
        if (scoreFor > scoreAgainst) {
            return "W";
        }
        if (scoreFor < scoreAgainst) {
            return "L";
        }
        return "D";
    }

    private Map<Long, TeamStandingResponseDto.LiveMatch> applyLiveImpacts(
            List<StandingProjection> standings,
            List<LiveStandingImpact> impacts
    ) {
        Map<Long, StandingProjection> standingsByTeamId = standings.stream()
                .collect(Collectors.toMap(StandingProjection::getTeamId, Function.identity()));
        Map<Long, Integer> authoritativePlayedByTeamId = standings.stream()
                .collect(Collectors.toMap(
                        StandingProjection::getTeamId,
                        standing -> standing.getPlayed() == null ? 0 : standing.getPlayed()
                ));
        Map<Long, TeamStandingResponseDto.LiveMatch> liveMatches = new HashMap<>();

        for (LiveStandingImpact impact : impacts) {
            StandingProjection home = standingsByTeamId.get(impact.getHomeTeamId());
            StandingProjection away = standingsByTeamId.get(impact.getAwayTeamId());
            if (home == null || away == null) {
                continue;
            }

            if (apiFootballStandingLocalUpdateService.isLiveImpact(impact)) {
                liveMatches.put(impact.getHomeTeamId(), toLiveMatch(
                        impact,
                        impact.getHomeScore(),
                        impact.getAwayScore()
                ));
                liveMatches.put(impact.getAwayTeamId(), toLiveMatch(
                        impact,
                        impact.getAwayScore(),
                        impact.getHomeScore()
                ));
            }

            if (apiFootballStandingLocalUpdateService.isReflected(
                    impact,
                    authoritativePlayedByTeamId.get(impact.getHomeTeamId()),
                    authoritativePlayedByTeamId.get(impact.getAwayTeamId())
            )) {
                continue;
            }

            home.applyMatchResult(true, impact.getHomeScore(), impact.getAwayScore(), impact.getAppliedAt());
            away.applyMatchResult(false, impact.getAwayScore(), impact.getHomeScore(), impact.getAppliedAt());
        }

        return liveMatches;
    }

    private TeamStandingResponseDto.LiveMatch toLiveMatch(LiveStandingImpact impact,
                                                          Integer scoreFor, Integer scoreAgainst) {
        return TeamStandingResponseDto.LiveMatch.builder()
                .fixtureId(impact.getFixtureId())
                .scoreFor(scoreFor)
                .scoreAgainst(scoreAgainst)
                .statusShort(impact.getStatusShort())
                .elapsed(impact.getElapsed())
                .extra(impact.getExtra())
                .result(resultOf(scoreFor, scoreAgainst))
                .build();
    }

    private String resultOf(Integer scoreFor, Integer scoreAgainst) {
        if (scoreFor > scoreAgainst) {
            return "WINNING";
        }
        if (scoreFor < scoreAgainst) {
            return "LOSING";
        }
        return "DRAWING";
    }

    private void refreshRanks(List<StandingProjection> standings) {
        List<StandingProjection> sorted = standings.stream()
                .sorted(Comparator
                        .comparing(StandingProjection::getPoints, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(StandingProjection::getGoalsDiff, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(StandingProjection::getGoalsFor, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(StandingProjection::getTeamName))
                .toList();

        for (int index = 0; index < sorted.size(); index++) {
            sorted.get(index).updateRank(index + 1);
        }
    }

    private TeamStandingResponseDto.Record toRecord(Integer played, Integer win, Integer draw, Integer lose,
                                                    Integer goalsFor, Integer goalsAgainst) {
        return TeamStandingResponseDto.Record.builder()
                .played(played)
                .win(win)
                .draw(draw)
                .lose(lose)
                .goals(TeamStandingResponseDto.Goals.builder()
                        .goalsFor(goalsFor)
                        .goalsAgainst(goalsAgainst)
                        .build())
                .build();
    }

    private TeamStandingResponseDto.RecentForm toRecentForm(RecentFormProjection recentForm) {
        RecentFormProjection form = recentForm != null ? recentForm : new RecentFormProjection();
        return TeamStandingResponseDto.RecentForm.builder()
                .played(form.getPlayed())
                .win(form.getWin())
                .draw(form.getDraw())
                .lose(form.getLose())
                .goals(TeamStandingResponseDto.Goals.builder()
                        .goalsFor(form.getGoalsFor())
                        .goalsAgainst(form.getGoalsAgainst())
                        .build())
                .points(form.getPoints())
                .goalsDiff(form.getGoalsDiff())
                .results(form.getResults())
                .matches(form.getMatches())
                .build();
    }

    @Getter
    private static class StandingProjection {
        private final Integer season;
        private Integer rank;
        private final Long teamId;
        private final String teamName;
        private final String teamNameKo;
        private final String teamLogoUrl;
        private Integer points;
        private Integer goalsDiff;
        private final String group;
        private final String form;
        private final String status;
        private final String description;
        private Integer played;
        private Integer win;
        private Integer draw;
        private Integer lose;
        private Integer goalsFor;
        private Integer goalsAgainst;
        private Integer homePlayed;
        private Integer homeWin;
        private Integer homeDraw;
        private Integer homeLose;
        private Integer homeGoalsFor;
        private Integer homeGoalsAgainst;
        private Integer awayPlayed;
        private Integer awayWin;
        private Integer awayDraw;
        private Integer awayLose;
        private Integer awayGoalsFor;
        private Integer awayGoalsAgainst;
        private LocalDateTime updatedAt;

        private StandingProjection(TeamStanding standing, MediaUrlService mediaUrlService) {
            Team team = standing.getTeam();
            this.season = standing.getSeason();
            this.rank = standing.getRank();
            this.teamId = team.getTeamId();
            this.teamName = team.getName();
            this.teamNameKo = team.getKoreanName();
            this.teamLogoUrl = mediaUrlService.teamLogoUrl(team);
            this.points = standing.getPoints();
            this.goalsDiff = standing.getGoalsDiff();
            this.group = standing.getGroup();
            this.form = standing.getForm();
            this.status = standing.getStatus();
            this.description = standing.getDescription();
            this.played = standing.getPlayed();
            this.win = standing.getWin();
            this.draw = standing.getDraw();
            this.lose = standing.getLose();
            this.goalsFor = standing.getGoalsFor();
            this.goalsAgainst = standing.getGoalsAgainst();
            this.homePlayed = standing.getHomePlayed();
            this.homeWin = standing.getHomeWin();
            this.homeDraw = standing.getHomeDraw();
            this.homeLose = standing.getHomeLose();
            this.homeGoalsFor = standing.getHomeGoalsFor();
            this.homeGoalsAgainst = standing.getHomeGoalsAgainst();
            this.awayPlayed = standing.getAwayPlayed();
            this.awayWin = standing.getAwayWin();
            this.awayDraw = standing.getAwayDraw();
            this.awayLose = standing.getAwayLose();
            this.awayGoalsFor = standing.getAwayGoalsFor();
            this.awayGoalsAgainst = standing.getAwayGoalsAgainst();
            this.updatedAt = standing.getApiUpdatedAt();
        }

        static StandingProjection from(TeamStanding standing, MediaUrlService mediaUrlService) {
            return new StandingProjection(standing, mediaUrlService);
        }

        void applyMatchResult(boolean homeSide, int goalsFor, int goalsAgainst, LocalDateTime updatedAt) {
            int pointDelta = pointsFor(goalsFor, goalsAgainst);
            boolean winResult = goalsFor > goalsAgainst;
            boolean drawResult = goalsFor == goalsAgainst;

            this.points = valueOf(this.points) + pointDelta;
            this.goalsDiff = valueOf(this.goalsDiff) + goalsFor - goalsAgainst;
            this.played = valueOf(this.played) + 1;
            this.win = valueOf(this.win) + (winResult ? 1 : 0);
            this.draw = valueOf(this.draw) + (drawResult ? 1 : 0);
            this.lose = valueOf(this.lose) + (!winResult && !drawResult ? 1 : 0);
            this.goalsFor = valueOf(this.goalsFor) + goalsFor;
            this.goalsAgainst = valueOf(this.goalsAgainst) + goalsAgainst;

            if (homeSide) {
                this.homePlayed = valueOf(this.homePlayed) + 1;
                this.homeWin = valueOf(this.homeWin) + (winResult ? 1 : 0);
                this.homeDraw = valueOf(this.homeDraw) + (drawResult ? 1 : 0);
                this.homeLose = valueOf(this.homeLose) + (!winResult && !drawResult ? 1 : 0);
                this.homeGoalsFor = valueOf(this.homeGoalsFor) + goalsFor;
                this.homeGoalsAgainst = valueOf(this.homeGoalsAgainst) + goalsAgainst;
            } else {
                this.awayPlayed = valueOf(this.awayPlayed) + 1;
                this.awayWin = valueOf(this.awayWin) + (winResult ? 1 : 0);
                this.awayDraw = valueOf(this.awayDraw) + (drawResult ? 1 : 0);
                this.awayLose = valueOf(this.awayLose) + (!winResult && !drawResult ? 1 : 0);
                this.awayGoalsFor = valueOf(this.awayGoalsFor) + goalsFor;
                this.awayGoalsAgainst = valueOf(this.awayGoalsAgainst) + goalsAgainst;
            }

            this.updatedAt = updatedAt;
        }

        private int pointsFor(int goalsFor, int goalsAgainst) {
            if (goalsFor > goalsAgainst) {
                return 3;
            }
            if (goalsFor == goalsAgainst) {
                return 1;
            }
            return 0;
        }

        private int valueOf(Integer value) {
            return value == null ? 0 : value;
        }

        private void updateRank(Integer rank) {
            this.rank = rank;
        }
    }

    @Getter
    private static class RecentFormProjection {
        private static final int MAX_RECENT_MATCHES = 5;

        private int played;
        private int win;
        private int draw;
        private int lose;
        private int goalsFor;
        private int goalsAgainst;
        private int points;
        private final List<String> results = new ArrayList<>();
        private final List<TeamStandingResponseDto.RecentMatch> matches = new ArrayList<>();

        boolean canApply() {
            return played < MAX_RECENT_MATCHES;
        }

        void apply(int goalsFor, int goalsAgainst, TeamStandingResponseDto.RecentMatch match) {
            if (!canApply()) {
                return;
            }

            this.played++;
            this.goalsFor += goalsFor;
            this.goalsAgainst += goalsAgainst;

            if (goalsFor > goalsAgainst) {
                this.win++;
                this.points += 3;
                this.results.add("W");
            } else if (goalsFor == goalsAgainst) {
                this.draw++;
                this.points += 1;
                this.results.add("D");
            } else {
                this.lose++;
                this.results.add("L");
            }
            this.matches.add(match);
        }

        int getGoalsDiff() {
            return goalsFor - goalsAgainst;
        }
    }
}
