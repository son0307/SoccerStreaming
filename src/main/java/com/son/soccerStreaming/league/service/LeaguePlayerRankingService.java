package com.son.soccerStreaming.league.service;

import com.son.soccerStreaming.fixture.repository.PlayerFixtureStatRepository;
import com.son.soccerStreaming.global.config.RedisCacheConfig;
import com.son.soccerStreaming.league.dto.LeaguePlayerRankingResponseDto;
import com.son.soccerStreaming.media.service.MediaUrlService;
import com.son.soccerStreaming.player.entity.Player;
import com.son.soccerStreaming.player.entity.PlayerTeamSeasonStat;
import com.son.soccerStreaming.player.repository.PlayerTeamSeasonStatRepository;
import com.son.soccerStreaming.team.entity.Team;
import com.son.soccerStreaming.team.entity.TeamStanding;
import com.son.soccerStreaming.team.repository.TeamStandingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class LeaguePlayerRankingService {

    private static final int TOP_LIMIT = 20;
    private static final int MIN_RATING_APPEARANCES = 5;
    private static final int MIN_SAVE_ATTEMPTS = 10;

    private final PlayerTeamSeasonStatRepository playerTeamSeasonStatRepository;
    private final PlayerFixtureStatRepository playerFixtureStatRepository;
    private final TeamStandingRepository teamStandingRepository;
    private final MediaUrlService mediaUrlService;

    @Cacheable(
            cacheManager = RedisCacheConfig.RANKINGS_CACHE_MANAGER,
            cacheNames = RedisCacheConfig.LEAGUE_PLAYER_RANKINGS_CACHE,
            key = "'v5:league:' + #leagueId + ':season:' + #season",
            sync = true
    )
    public LeaguePlayerRankingResponseDto getRankings(Integer leagueId, Integer season) {
        List<PlayerTeamSeasonStat> stats = playerTeamSeasonStatRepository.findAllForLeagueRankings(
                leagueId.longValue(),
                season
        );
        if (stats.isEmpty()) {
            return emptyResponse(leagueId, season);
        }

        List<Long> playerIds = stats.stream()
                .map(stat -> stat.getPlayer().getPlayerId())
                .distinct()
                .toList();
        Map<Long, PlayerFixtureStatRepository.PlayerRankingMatchAggregate> matchAggregates =
                playerFixtureStatRepository.findRankingMatchAggregates(playerIds, season).stream()
                        .collect(Collectors.toMap(
                                PlayerFixtureStatRepository.PlayerRankingMatchAggregate::getPlayerId,
                                item -> item
                        ));
        Map<Long, Long> latestTeamByPlayer = latestTeamByPlayer(playerIds, season);
        List<TeamStanding> standings = teamStandingRepository.findAllByLeagueIdAndSeason(leagueId, season);
        Map<Long, Integer> teamMatchesPlayed = standings.stream()
                .collect(Collectors.toMap(
                        standing -> standing.getTeam().getTeamId(),
                        standing -> valueOf(standing.getPlayed()),
                        Math::max
                ));

        List<LeaguePlayerRankingResponseDto.Row> rows = aggregateRows(
                stats,
                matchAggregates,
                latestTeamByPlayer
        );

        return LeaguePlayerRankingResponseDto.builder()
                .leagueId(leagueId)
                .season(season)
                .goals(rank(rows, row -> row.getGoals() > 0, goalsComparator()))
                .assists(rank(rows, row -> row.getAssists() > 0, assistsComparator()))
                .attackPoints(rank(rows, row -> row.getAttackPoints() > 0, attackPointsComparator()))
                .ratings(rank(
                        rows,
                        row -> row.getAppearances() >= minimumRatingAppearances(teamMatchesPlayed, row.getTeamId()),
                        ratingComparator(),
                        row -> row.getAppearances() < MIN_RATING_APPEARANCES
                ))
                .minutes(rank(rows, row -> row.getMinutes() > 0, minutesComparator()))
                .yellowCards(rank(rows, row -> row.getYellowCards() > 0, yellowCardsComparator()))
                .redCards(rank(rows, row -> row.getRedCards() > 0, redCardsComparator()))
                .saves(rank(rows, row -> row.getSaves() > 0, savesComparator()))
                .cleanSheets(rank(
                        rows,
                        row -> isGoalkeeper(row.getPosition()) && row.getCleanSheets() > 0,
                        cleanSheetsComparator()
                ))
                .savePercentages(rank(
                        rows,
                        row -> isGoalkeeper(row.getPosition())
                                && row.getSavePercentage() != null
                                && row.getSaves() + row.getConceded()
                                >= minimumSaveAttempts(teamMatchesPlayed, row.getTeamId()),
                        savePercentageComparator(),
                        row -> row.getSaves() + row.getConceded() < MIN_SAVE_ATTEMPTS
                ))
                .build();
    }

    private List<LeaguePlayerRankingResponseDto.Row> aggregateRows(
            List<PlayerTeamSeasonStat> stats,
            Map<Long, PlayerFixtureStatRepository.PlayerRankingMatchAggregate> matchAggregates,
            Map<Long, Long> latestTeamByPlayer
    ) {
        Map<Long, List<PlayerTeamSeasonStat>> statsByPlayer = stats.stream()
                .collect(Collectors.groupingBy(
                        stat -> stat.getPlayer().getPlayerId(),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        return statsByPlayer.entrySet().stream()
                .map(entry -> toRow(
                        entry.getValue(),
                        matchAggregates.get(entry.getKey()),
                        latestTeamByPlayer.get(entry.getKey())
                ))
                .toList();
    }

    private LeaguePlayerRankingResponseDto.Row toRow(
            List<PlayerTeamSeasonStat> stats,
            PlayerFixtureStatRepository.PlayerRankingMatchAggregate matchAggregate,
            Long latestTeamId
    ) {
        Player player = stats.get(0).getPlayer();
        PlayerTeamSeasonStat displayStat = displayStat(stats, latestTeamId);
        Team team = displayStat.getTeam();
        int appearances = stats.stream().mapToInt(stat -> valueOf(stat.getAppearances())).sum();
        int ratingAppearances = stats.stream()
                .filter(stat -> stat.getRating() != null)
                .mapToInt(stat -> valueOf(stat.getAppearances()))
                .sum();
        double weightedRating = stats.stream()
                .filter(stat -> stat.getRating() != null)
                .mapToDouble(stat -> stat.getRating() * valueOf(stat.getAppearances()))
                .sum();
        int goals = sum(stats, PlayerTeamSeasonStat::getGoals);
        int assists = sum(stats, PlayerTeamSeasonStat::getAssists);
        int saves = sum(stats, PlayerTeamSeasonStat::getSaves);
        int conceded = sum(stats, PlayerTeamSeasonStat::getConceded);
        String position = displayStat.getPosition() != null ? displayStat.getPosition() : player.getPosition();
        boolean goalkeeper = isGoalkeeper(position);

        return LeaguePlayerRankingResponseDto.Row.builder()
                .playerId(player.getPlayerId())
                .playerName(player.getName())
                .playerNameKo(player.getKoreanName())
                .photoUrl(mediaUrlService.playerPhotoUrl(player))
                .position(position)
                .teamId(team.getTeamId())
                .teamName(team.getName())
                .teamNameKo(team.getKoreanName())
                .teamLogoUrl(mediaUrlService.teamLogoUrl(team))
                .appearances(appearances)
                .minutes(sum(stats, PlayerTeamSeasonStat::getMinutes))
                .rating(ratingAppearances > 0 ? roundToTwoDecimals(weightedRating / ratingAppearances) : 0)
                .goals(goals)
                .penaltyGoals(sum(stats, PlayerTeamSeasonStat::getPenaltyScored))
                .assists(assists)
                .attackPoints(goals + assists)
                .goalMatches(longValue(matchAggregate != null ? matchAggregate.getGoalMatches() : null))
                .assistMatches(longValue(matchAggregate != null ? matchAggregate.getAssistMatches() : null))
                .attackPointMatches(longValue(matchAggregate != null ? matchAggregate.getAttackPointMatches() : null))
                .yellowCards(sum(stats, PlayerTeamSeasonStat::getYellowCards))
                .redCards(sum(stats, PlayerTeamSeasonStat::getRedCards))
                .saves(saves)
                .conceded(conceded)
                .cleanSheets(goalkeeper
                        ? longValue(matchAggregate != null ? matchAggregate.getCleanSheets() : null)
                        : 0)
                .savePercentage(goalkeeper ? savePercentage(saves, conceded) : null)
                .build();
    }

    private PlayerTeamSeasonStat displayStat(List<PlayerTeamSeasonStat> stats, Long latestTeamId) {
        if (latestTeamId != null) {
            return stats.stream()
                    .filter(stat -> latestTeamId.equals(stat.getTeam().getTeamId()))
                    .findFirst()
                    .orElse(stats.get(0));
        }
        return stats.stream()
                .max(Comparator.comparingInt(stat -> valueOf(stat.getAppearances())))
                .orElse(stats.get(0));
    }

    private Map<Long, Long> latestTeamByPlayer(List<Long> playerIds, Integer season) {
        return playerFixtureStatRepository.findLatestTeamByPlayerIdsAndSeason(playerIds, season).stream()
                .collect(Collectors.toMap(
                        PlayerFixtureStatRepository.LatestPlayerTeam::getPlayerId,
                        PlayerFixtureStatRepository.LatestPlayerTeam::getTeamId
                ));
    }

    private List<LeaguePlayerRankingResponseDto.Row> rank(
            List<LeaguePlayerRankingResponseDto.Row> rows,
            Predicate<LeaguePlayerRankingResponseDto.Row> filter,
            Comparator<LeaguePlayerRankingResponseDto.Row> comparator
    ) {
        return rank(rows, filter, comparator, row -> false);
    }

    private List<LeaguePlayerRankingResponseDto.Row> rank(
            List<LeaguePlayerRankingResponseDto.Row> rows,
            Predicate<LeaguePlayerRankingResponseDto.Row> filter,
            Comparator<LeaguePlayerRankingResponseDto.Row> comparator,
            Predicate<LeaguePlayerRankingResponseDto.Row> provisional
    ) {
        List<LeaguePlayerRankingResponseDto.Row> ranked = rows.stream()
                .filter(filter)
                .sorted(comparator.thenComparingLong(LeaguePlayerRankingResponseDto.Row::getPlayerId))
                .toList();
        List<LeaguePlayerRankingResponseDto.Row> result = new ArrayList<>(ranked.size());
        LeaguePlayerRankingResponseDto.Row previous = null;
        int currentRank = 0;
        for (int index = 0; index < ranked.size(); index++) {
            LeaguePlayerRankingResponseDto.Row row = ranked.get(index);
            if (previous == null || comparator.compare(previous, row) != 0) {
                currentRank = index + 1;
            }
            if (currentRank > TOP_LIMIT) {
                break;
            }
            result.add(row.toBuilder()
                    .rank(currentRank)
                    .provisional(provisional.test(row))
                    .build());
            previous = row;
        }
        return result;
    }

    private int minimumRatingAppearances(Map<Long, Integer> teamMatchesPlayed, Long teamId) {
        int played = teamMatchesPlayed.getOrDefault(teamId, 0);
        return Math.min(MIN_RATING_APPEARANCES, Math.max(1, (int) Math.ceil(played * 0.6)));
    }

    private int minimumSaveAttempts(Map<Long, Integer> teamMatchesPlayed, Long teamId) {
        int played = teamMatchesPlayed.getOrDefault(teamId, 0);
        return Math.min(MIN_SAVE_ATTEMPTS, Math.max(3, played * 2));
    }

    private Comparator<LeaguePlayerRankingResponseDto.Row> goalsComparator() {
        return descendingInt(LeaguePlayerRankingResponseDto.Row::getGoals)
                .thenComparingInt(LeaguePlayerRankingResponseDto.Row::getPenaltyGoals);
    }

    private Comparator<LeaguePlayerRankingResponseDto.Row> assistsComparator() {
        return descendingInt(LeaguePlayerRankingResponseDto.Row::getAssists);
    }

    private Comparator<LeaguePlayerRankingResponseDto.Row> attackPointsComparator() {
        return descendingInt(LeaguePlayerRankingResponseDto.Row::getAttackPoints);
    }

    private Comparator<LeaguePlayerRankingResponseDto.Row> ratingComparator() {
        return Comparator.comparingDouble(LeaguePlayerRankingResponseDto.Row::getRating).reversed();
    }

    private Comparator<LeaguePlayerRankingResponseDto.Row> minutesComparator() {
        return descendingInt(LeaguePlayerRankingResponseDto.Row::getMinutes);
    }

    private Comparator<LeaguePlayerRankingResponseDto.Row> yellowCardsComparator() {
        return descendingInt(LeaguePlayerRankingResponseDto.Row::getYellowCards);
    }

    private Comparator<LeaguePlayerRankingResponseDto.Row> redCardsComparator() {
        return descendingInt(LeaguePlayerRankingResponseDto.Row::getRedCards);
    }

    private Comparator<LeaguePlayerRankingResponseDto.Row> savesComparator() {
        return descendingInt(LeaguePlayerRankingResponseDto.Row::getSaves);
    }

    private Comparator<LeaguePlayerRankingResponseDto.Row> cleanSheetsComparator() {
        return descendingInt(LeaguePlayerRankingResponseDto.Row::getCleanSheets);
    }

    private Comparator<LeaguePlayerRankingResponseDto.Row> savePercentageComparator() {
        return Comparator.comparing(
                        LeaguePlayerRankingResponseDto.Row::getSavePercentage,
                        Comparator.nullsLast(Comparator.reverseOrder())
                );
    }

    private Comparator<LeaguePlayerRankingResponseDto.Row> descendingInt(
            java.util.function.ToIntFunction<LeaguePlayerRankingResponseDto.Row> extractor
    ) {
        return Comparator.comparingInt(extractor).reversed();
    }

    private int sum(
            List<PlayerTeamSeasonStat> stats,
            java.util.function.Function<PlayerTeamSeasonStat, Integer> extractor
    ) {
        return stats.stream().map(extractor).mapToInt(this::valueOf).sum();
    }

    private Double savePercentage(int saves, int conceded) {
        int attempts = saves + conceded;
        if (attempts <= 0) {
            return null;
        }
        return roundToTwoDecimals((saves * 100.0) / attempts);
    }

    private boolean isGoalkeeper(String position) {
        if (position == null) {
            return false;
        }
        String normalized = position.trim().toUpperCase(java.util.Locale.ROOT);
        return normalized.equals("G")
                || normalized.equals("GK")
                || normalized.equals("GOALKEEPER");
    }

    private double roundToTwoDecimals(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private int valueOf(Integer value) {
        return value != null ? value : 0;
    }

    private int longValue(Long value) {
        return value != null ? Math.toIntExact(value) : 0;
    }

    private LeaguePlayerRankingResponseDto emptyResponse(Integer leagueId, Integer season) {
        return LeaguePlayerRankingResponseDto.builder()
                .leagueId(leagueId)
                .season(season)
                .goals(List.of())
                .assists(List.of())
                .attackPoints(List.of())
                .ratings(List.of())
                .minutes(List.of())
                .yellowCards(List.of())
                .redCards(List.of())
                .saves(List.of())
                .cleanSheets(List.of())
                .savePercentages(List.of())
                .build();
    }
}
