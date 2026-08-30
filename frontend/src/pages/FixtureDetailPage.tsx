import { useEffect, useMemo, useRef, useState } from "react";
import type { CSSProperties, Dispatch, SetStateAction } from "react";
import { Pencil } from "lucide-react";
import { Link, useParams, useSearchParams } from "react-router-dom";
import {
  fetchFixture,
  fetchFixtureEvents,
  fetchFixtureHeadToHead,
  fetchFixtureLineups,
  fetchFixturePlayerStats,
  fetchFixtureStats,
  fetchLeagueSeasons,
  type FixtureColorInfo,
  type FixtureEvent,
  type FixtureEventResponse,
  type FixtureHeadToHead,
  type FixtureHeadToHeadMatch,
  type FixtureLineupPlayer,
  type FixtureLineupResponse,
  type LiveFixtureSnapshot,
  type FixturePlayerStat,
  type FixturePlayerStatResponse,
  type FixtureStatResponse,
  type FixtureSummary,
  type FixtureTeamLineup,
  type FixtureTeamPlayerStats,
  type FixtureTeamStat,
  type LeagueSeasonCoverage,
  type CurrentUser,
} from "../api";
import { parseKoreaDateTime } from "../dateUtils";
import { displayLocalizedName } from "../teamNames";
import { fixtureStatusLabel, isLiveFixture } from "../fixtureStatus";

type DetailTab = "events" | "lineups" | "stats" | "headToHead";
type LoadState<T> = {
  data: T | null;
  error: string;
  isLoading: boolean;
};
type CoverageStatus = "loading" | "ready" | "error";
type LiveConnectionStatus = "connecting" | "connected" | "reconnecting" | null;
type Side = "home" | "away";
type EventSide = Side | "neutral";
type EventCategory =
  | "goal"
  | "own-goal"
  | "missed-penalty"
  | "yellow-card"
  | "red-card"
  | "substitution"
  | "var"
  | "event";
type EventTimelineEventItem = {
  kind: "event";
  event: FixtureEvent;
  category: EventCategory;
  side: EventSide;
  score: { home: number; away: number } | null;
};
type EventTimelineSectionItem = {
  kind: "section";
  id: "half-time" | "full-time";
  label: string;
};
type EventTimelineItem = EventTimelineEventItem | EventTimelineSectionItem;
type PlayerStatSortKey = "minutes" | "rating" | "goals" | "assists" | "shots" | "passes" | "tackles";
type SortDirection = "asc" | "desc";

const detailTabs: Array<{ label: string; value: DetailTab }> = [
  { label: "이벤트", value: "events" },
  { label: "라인업", value: "lineups" },
  { label: "통계", value: "stats" },
  { label: "최근 전적", value: "headToHead" },
];
const UNSUPPORTED_MESSAGE = "해당 시즌에는 지원하지 않습니다.";

const initialLoadState = <T,>(): LoadState<T> => ({
  data: null,
  error: "",
  isLoading: true,
});

export function FixtureDetailPage({ currentUser }: { currentUser: CurrentUser | null }) {
  const { fixtureId } = useParams();
  const [searchParams, setSearchParams] = useSearchParams();
  const numericFixtureId = Number(fixtureId);
  const activeFixtureIdRef = useRef(numericFixtureId);
  activeFixtureIdRef.current = numericFixtureId;
  const activeTab = detailTab(searchParams.get("tab")) ?? "events";
  const [fixtureState, setFixtureState] = useState<LoadState<FixtureSummary>>(initialLoadState);
  const [eventsState, setEventsState] = useState<LoadState<FixtureEventResponse>>(initialLoadState);
  const [headToHeadState, setHeadToHeadState] = useState<LoadState<FixtureHeadToHead>>(initialLoadState);
  const [lineupsState, setLineupsState] = useState<LoadState<FixtureLineupResponse>>(initialLoadState);
  const [statsState, setStatsState] = useState<LoadState<FixtureStatResponse>>(initialLoadState);
  const [playerStatsState, setPlayerStatsState] = useState<LoadState<FixturePlayerStatResponse>>(initialLoadState);
  const [seasonCoverages, setSeasonCoverages] = useState<LeagueSeasonCoverage[]>([]);
  const [coverageStatus, setCoverageStatus] = useState<CoverageStatus>("loading");
  const [liveConnectionStatus, setLiveConnectionStatus] = useState<LiveConnectionStatus>(null);

  useEffect(() => {
    let isCurrent = true;
    loadSeasonCoverages(() => isCurrent);

    return () => {
      isCurrent = false;
    };
  }, []);

  function loadSeasonCoverages(isCurrent: () => boolean = () => true) {
    setCoverageStatus("loading");
    fetchLeagueSeasons()
      .then((response) => {
        if (isCurrent()) {
          setSeasonCoverages(response.seasons ?? []);
          setCoverageStatus("ready");
        }
      })
      .catch(() => {
        if (isCurrent()) {
          setSeasonCoverages([]);
          setCoverageStatus("error");
        }
      });
  }

  useEffect(() => {
    if (!Number.isFinite(numericFixtureId) || numericFixtureId <= 0) {
      setFixtureState({
        data: null,
        error: "올바른 경기 ID가 아닙니다.",
        isLoading: false,
      });
      return;
    }

    let isCurrent = true;
    setFixtureState(initialLoadState);
    setEventsState(initialLoadState);
    setHeadToHeadState(initialLoadState);
    setLineupsState(initialLoadState);
    setStatsState(initialLoadState);
    setPlayerStatsState(initialLoadState);

    fetchFixture(numericFixtureId, { fresh: true })
      .then((data) => {
        if (isCurrent) {
          setFixtureState({ data, error: "", isLoading: false });
        }
      })
      .catch((error) => {
        if (isCurrent) {
          setFixtureState({
            data: null,
            error: error instanceof Error ? error.message : "경기 정보를 불러오지 못했습니다.",
            isLoading: false,
          });
        }
      });
    void loadSection(
      fetchFixtureHeadToHead(numericFixtureId),
      setHeadToHeadState,
      "최근 전적을 불러오지 못했습니다.",
      () => isCurrent,
    );

    return () => {
      isCurrent = false;
    };
  }, [numericFixtureId]);

  useEffect(() => {
    if (searchParams.get("tab") !== activeTab) {
      setSearchParams((current) => {
        const next = new URLSearchParams(current);
        next.set("tab", activeTab);
        return next;
      }, { replace: true });
    }
  }, [activeTab, searchParams, setSearchParams]);

  useEffect(() => {
    const fixture = fixtureState.data;
    if (!fixture || coverageStatus === "loading" || !Number.isFinite(numericFixtureId) || numericFixtureId <= 0) {
      return;
    }

    let isCurrent = true;
    const coverage = seasonCoverages.find((item) => item.seasonYear === fixture.season);
    const isSupported = (value: boolean | undefined) => !coverage || value !== false;

    if (isSupported(coverage?.events)) {
      setEventsState(initialLoadState);
      void loadSection(fetchFixtureEvents(numericFixtureId), setEventsState, "경기 이벤트를 불러오지 못했습니다.", () => isCurrent);
    } else {
      setEventsState({ data: null, error: UNSUPPORTED_MESSAGE, isLoading: false });
    }

    if (isSupported(coverage?.lineups)) {
      setLineupsState(initialLoadState);
      void loadSection(fetchFixtureLineups(numericFixtureId), setLineupsState, "라인업을 불러오지 못했습니다.", () => isCurrent);
    } else {
      setLineupsState({ data: null, error: UNSUPPORTED_MESSAGE, isLoading: false });
    }

    if (isSupported(coverage?.fixtureStats)) {
      setStatsState(initialLoadState);
      void loadSection(
        fetchFixtureStats(numericFixtureId, { fresh: isLiveFixture(fixture) }),
        setStatsState,
        "팀 통계를 불러오지 못했습니다.",
        () => isCurrent,
      );
    } else {
      setStatsState({ data: null, error: UNSUPPORTED_MESSAGE, isLoading: false });
    }

    if (isSupported(coverage?.playerStats)) {
      setPlayerStatsState(initialLoadState);
      void loadSection(
        fetchFixturePlayerStats(numericFixtureId),
        setPlayerStatsState,
        "선수별 경기 통계를 불러오지 못했습니다.",
        () => isCurrent,
      );
    } else {
      setPlayerStatsState({ data: null, error: UNSUPPORTED_MESSAGE, isLoading: false });
    }

    return () => {
      isCurrent = false;
    };
  }, [coverageStatus, fixtureState.data?.season, numericFixtureId, seasonCoverages]);

  useEffect(() => {
    const fixture = fixtureState.data;
    if (!fixture || !isLiveFixture(fixture) || !Number.isFinite(numericFixtureId) || numericFixtureId <= 0) {
      setLiveConnectionStatus(null);
      return;
    }

    let isCurrent = true;
    let fallbackTimerId: number | null = null;
    const eventSource = new EventSource(`/api/v1/live/stream/fixtures/${numericFixtureId}`);
    setLiveConnectionStatus("connecting");

    const refreshLiveState = async (finalRefresh = false) => {
      const [fixtureResult, eventsResult, statsResult, playerStatsResult] = await Promise.allSettled([
        fetchFixture(numericFixtureId, { fresh: true }),
        fetchFixtureEvents(numericFixtureId),
        fetchFixtureStats(numericFixtureId, { fresh: true }),
        fetchFixturePlayerStats(numericFixtureId),
      ]);
      if ((!isCurrent && !finalRefresh) || activeFixtureIdRef.current !== numericFixtureId) {
        return;
      }
      if (fixtureResult.status === "fulfilled") {
        setFixtureState({ data: fixtureResult.value, error: "", isLoading: false });
      }
      if (eventsResult.status === "fulfilled") {
        setEventsState({ data: eventsResult.value, error: "", isLoading: false });
      }
      if (statsResult.status === "fulfilled") {
        setStatsState({ data: statsResult.value, error: "", isLoading: false });
      }
      if (playerStatsResult.status === "fulfilled") {
        setPlayerStatsState({ data: playerStatsResult.value, error: "", isLoading: false });
      }
    };

    const stopFallbackPolling = () => {
      if (fallbackTimerId !== null) {
        window.clearInterval(fallbackTimerId);
        fallbackTimerId = null;
      }
    };

    eventSource.onopen = () => {
      if (!isCurrent) {
        return;
      }
      setLiveConnectionStatus("connected");
      stopFallbackPolling();
      void refreshLiveState();
    };

    eventSource.onerror = () => {
      if (!isCurrent) {
        return;
      }
      setLiveConnectionStatus("reconnecting");
      if (fallbackTimerId === null) {
        void refreshLiveState();
        fallbackTimerId = window.setInterval(() => void refreshLiveState(), 60_000);
      }
    };

    eventSource.addEventListener("LIVE_SNAPSHOT", (rawEvent) => {
      const snapshot = parseSseData<LiveFixtureSnapshot>(rawEvent);
      if (!snapshot || snapshot.fixtureId !== numericFixtureId || !isCurrent) {
        return;
      }
      setFixtureState((current) => current.data ? {
        data: {
          ...current.data,
          fixtureStatus: snapshot.fixtureStatus,
          statusShort: snapshot.statusShort,
          statusLong: snapshot.statusLong,
          elapsed: snapshot.elapsed,
          extra: snapshot.extra,
          homeScore: snapshot.homeTeamStat?.score ?? current.data.homeScore,
          awayScore: snapshot.awayTeamStat?.score ?? current.data.awayScore,
        },
        error: "",
        isLoading: false,
      } : current);
      setStatsState({
        data: {
          fixtureId: snapshot.fixtureId,
          homeTeamStat: snapshot.homeTeamStat,
          awayTeamStat: snapshot.awayTeamStat,
        },
        error: "",
        isLoading: false,
      });
      if (snapshot.fixtureStatus?.toUpperCase() !== "LIVE") {
        eventSource.close();
        stopFallbackPolling();
        setLiveConnectionStatus(null);
        void refreshLiveState(true);
      }
    });

    eventSource.addEventListener("FIXTURE_EVENTS", (rawEvent) => {
      const response = parseSseData<FixtureEventResponse>(rawEvent);
      if (response?.fixtureId === numericFixtureId && isCurrent) {
        setEventsState({ data: response, error: "", isLoading: false });
      }
    });

    eventSource.addEventListener("PLAYER_STATS", (rawEvent) => {
      const response = parseSseData<FixturePlayerStatResponse>(rawEvent);
      if (response?.fixtureId === numericFixtureId && isCurrent) {
        setPlayerStatsState({ data: response, error: "", isLoading: false });
      }
    });

    return () => {
      isCurrent = false;
      stopFallbackPolling();
      eventSource.close();
    };
  }, [fixtureState.data?.fixtureStatus, numericFixtureId]);

  if (fixtureState.isLoading) {
    return (
      <section className="league-content">
        <article className="panel placeholder-panel">경기 정보를 불러오는 중입니다.</article>
      </section>
    );
  }

  if (fixtureState.error || !fixtureState.data) {
    return (
      <section className="league-content">
        <article className="panel placeholder-panel">
          <p className="eyebrow">Fixture Detail</p>
          <h2>경기 정보를 불러오지 못했습니다.</h2>
          <p className="muted">{fixtureState.error || "잠시 후 다시 시도해 주세요."}</p>
          <Link className="primary-link fixture-back-link" to="/league/fixtures">
            경기 목록으로
          </Link>
        </article>
      </section>
    );
  }

  const fixture = fixtureState.data;
  const homeTeamId =
    fixture.homeTeamId
    ?? lineupsState.data?.homeTeam?.teamId
    ?? statsState.data?.homeTeamStat?.teamId
    ?? null;
  const awayTeamId =
    fixture.awayTeamId
    ?? lineupsState.data?.awayTeam?.teamId
    ?? statsState.data?.awayTeamStat?.teamId
    ?? null;

  return (
    <section className="league-content fixture-detail-page">
      <FixtureDetailHero
        awayTeamId={awayTeamId}
        events={eventsState.data?.events ?? []}
        fixture={fixture}
        homeTeamId={homeTeamId}
        isAdmin={currentUser?.role === "ADMIN"}
        lineups={lineupsState.data}
        liveConnectionStatus={liveConnectionStatus}
      />

      <nav className="detail-tabs" aria-label="경기 상세 메뉴">
        {detailTabs.map((tab) => (
          <button
            className={activeTab === tab.value ? "active" : ""}
            key={tab.value}
            onClick={() => {
              setSearchParams((current) => {
                const next = new URLSearchParams(current);
                next.set("tab", tab.value);
                return next;
              });
            }}
            type="button"
          >
            {tab.label}
          </button>
        ))}
      </nav>

      {coverageStatus === "error" && activeTab !== "headToHead" ? (
        <div className="notice warning inline-retry">
          <span>시즌 지원 정보를 확인하지 못했습니다. 기본 상세 정보 조회를 시도합니다.</span>
          <button type="button" onClick={() => loadSeasonCoverages()}>
            다시 확인
          </button>
        </div>
      ) : null}

      {activeTab === "events" ? <EventsPanel state={eventsState} fixture={fixture} /> : null}
      {activeTab === "lineups" ? (
        <LineupsPanel
          events={eventsState.data?.events ?? []}
          playerStats={playerStatsState.data}
          state={lineupsState}
        />
      ) : null}
      {activeTab === "stats" ? (
        <StatsPanel
          fixture={fixture}
          playerStatsState={playerStatsState}
          statsState={statsState}
        />
      ) : null}
      {activeTab === "headToHead" ? <HeadToHeadPanel state={headToHeadState} /> : null}
    </section>
  );
}

async function loadSection<T>(
  promise: Promise<T>,
  setter: Dispatch<SetStateAction<LoadState<T>>>,
  fallbackMessage: string,
  isCurrent: () => boolean,
) {
  try {
    const data = await promise;
    if (isCurrent()) {
      setter({ data, error: "", isLoading: false });
    }
  } catch (error) {
    if (isCurrent()) {
      setter({
        data: null,
        error: error instanceof Error ? error.message : fallbackMessage,
        isLoading: false,
      });
    }
  }
}

function parseSseData<T>(event: Event): T | null {
  if (!(event instanceof MessageEvent) || typeof event.data !== "string") {
    return null;
  }
  try {
    return JSON.parse(event.data) as T;
  } catch {
    return null;
  }
}

function FixtureDetailHero({
  awayTeamId,
  events,
  fixture,
  homeTeamId,
  isAdmin,
  lineups,
  liveConnectionStatus,
}: {
  awayTeamId: number | null;
  events: FixtureEvent[];
  fixture: FixtureSummary;
  homeTeamId: number | null;
  isAdmin: boolean;
  lineups: FixtureLineupResponse | null;
  liveConnectionStatus: LiveConnectionStatus;
}) {
  const dateParts = fixtureDateParts(fixture.fixtureDate);
  const playerTeamIds = lineupPlayerTeamIds(lineups);
  const homeScorers = fixtureScorers(events, homeTeamId, fixture.homeTeamName, playerTeamIds);
  const awayScorers = fixtureScorers(events, awayTeamId, fixture.awayTeamName, playerTeamIds);
  const homeRedCards = fixtureRedCards(events, homeTeamId, fixture.homeTeamName);
  const awayRedCards = fixtureRedCards(events, awayTeamId, fixture.awayTeamName);
  const homeTeamName = displayLocalizedName(fixture.homeTeamNameKo, fixture.homeTeamName);
  const awayTeamName = displayLocalizedName(fixture.awayTeamNameKo, fixture.awayTeamName);

  return (
    <article className="panel fixture-detail-hero">
      <div className="detail-team home">
        <div className="detail-team-identity">
          {fixture.homeTeamLogoUrl ? <img src={fixture.homeTeamLogoUrl} alt="" className="team-logo large" /> : null}
          {homeTeamId ? (
            <Link className="team-name-link" to={`/teams/${homeTeamId}`}>
              {homeTeamName}
            </Link>
          ) : (
            <strong>{homeTeamName}</strong>
          )}
        </div>
        <div className="detail-team-summary">
          <FixtureScorerList scorers={homeScorers} />
          <FixtureRedCardList redCards={homeRedCards} />
        </div>
      </div>
      <div className="detail-scoreboard">
        <p className="fixture-detail-meta">
          <span>{dateParts.date}</span>
          {dateParts.time ? <span>{dateParts.time}</span> : null}
          {fixture.round ? <span>{fixture.round}라운드</span> : null}
          {fixture.season ? <span>{formatSeasonLabel(fixture.season)}</span> : null}
        </p>
        <strong>{scoreText(fixture)}</strong>
        {false && isAdmin ? (
          <Link aria-label="관리자 수정" className="admin-edit-link icon" title="관리자 수정" to={`/admin/editor?tab=fixture&id=${fixture.fixtureId}`}>
            <Pencil size={16} aria-hidden="true" />
            관리자 수정
          </Link>
        ) : null}
        <span className="status-pill">{fixtureStatusLabel(fixture)}</span>
        {liveConnectionStatus ? (
          <small className={`live-connection-status ${liveConnectionStatus}`}>
            {liveConnectionStatus === "connected" ? "실시간 연결됨" : liveConnectionStatus === "connecting" ? "실시간 연결 중" : "재연결 중 · 1분 폴백"}
          </small>
        ) : null}
      </div>
        {isAdmin ? (
          <Link aria-label="관리자 수정" className="admin-edit-link icon fixture-admin-edit-link" title="관리자 수정" to={`/admin/editor?tab=fixture&id=${fixture.fixtureId}`}>
            <Pencil size={16} aria-hidden="true" />
            관리자 수정
          </Link>
        ) : null}
      <div className="detail-team away">
        <div className="detail-team-summary">
          <FixtureScorerList scorers={awayScorers} />
          <FixtureRedCardList redCards={awayRedCards} />
        </div>
        <div className="detail-team-identity">
          {fixture.awayTeamLogoUrl ? <img src={fixture.awayTeamLogoUrl} alt="" className="team-logo large" /> : null}
          {awayTeamId ? (
            <Link className="team-name-link" to={`/teams/${awayTeamId}`}>
              {awayTeamName}
            </Link>
          ) : (
            <strong>{awayTeamName}</strong>
          )}
        </div>
      </div>
      <dl className="fixture-detail-additional-meta">
        <div>
          <dt>심판</dt>
          <dd>{fixtureInfo(fixture.referee, "미정")}</dd>
        </div>
        <div>
          <dt>경기장</dt>
          <dd>{fixtureInfo(fixture.venueNameKo?.trim() || fixture.venueName, "미정")}</dd>
        </div>
      </dl>
    </article>
  );
}

type FixtureScorer = {
  playerId: number | null;
  playerName: string;
  ownGoal: boolean;
  minutes: string[];
  firstMinute: number;
};

function HeadToHeadPanel({ state }: { state: LoadState<FixtureHeadToHead> }) {
  if (state.isLoading) {
    return (
      <article className="panel detail-panel h2h-panel">
        <div className="detail-panel-heading">
          <p className="eyebrow">Head to Head</p>
          <h2>최근 10경기 전적</h2>
        </div>
        <p className="muted h2h-empty">최근 맞대결을 불러오는 중입니다.</p>
      </article>
    );
  }

  if (state.error || !state.data) {
    return (
      <article className="panel detail-panel h2h-panel">
        <div className="detail-panel-heading">
          <p className="eyebrow">Head to Head</p>
          <h2>최근 10경기 전적</h2>
        </div>
        <p className="muted h2h-empty">{state.error || "최근 맞대결 정보를 표시할 수 없습니다."}</p>
      </article>
    );
  }

  const { summary, recentMatches } = state.data;
  const homeTeamName = summary.homeTeamNameKo ?? summary.homeTeamName ?? "-";
  const awayTeamName = summary.awayTeamNameKo ?? summary.awayTeamName ?? "-";

  return (
    <article className="panel detail-panel h2h-panel">
      <div className="detail-panel-heading">
        <p className="eyebrow">Head to Head</p>
        <h2>최근 10경기 전적</h2>
      </div>
      <div className="h2h-summary-grid">
        <div className="h2h-team-summary">
          <strong>{homeTeamName}</strong>
          <span>{summary.homeWins}승</span>
          <small>{summary.homeGoals}득점</small>
        </div>
        <div className="h2h-draw-summary">
          <small>{summary.matches}경기</small>
          <span>{summary.draws}무</span>
        </div>
        <div className="h2h-team-summary">
          <strong>{awayTeamName}</strong>
          <span>{summary.awayWins}승</span>
          <small>{summary.awayGoals}득점</small>
        </div>
      </div>
      {recentMatches.length > 0 ? (
        <div className="h2h-match-list">
          {recentMatches.map((match) => (
            <HeadToHeadMatchRow key={match.fixtureId} match={match} />
          ))}
        </div>
      ) : (
        <p className="muted h2h-empty">최근 맞대결 기록이 없습니다.</p>
      )}
    </article>
  );
}

function HeadToHeadMatchRow({ match }: { match: FixtureHeadToHeadMatch }) {
  const dateParts = fixtureDateParts(match.fixtureDate);
  const homeTeamName = match.homeTeamNameKo ?? match.homeTeamName ?? "-";
  const awayTeamName = match.awayTeamNameKo ?? match.awayTeamName ?? "-";

  return (
    <Link className="h2h-match-row" to={`/fixtures/${match.fixtureId}`}>
      <time dateTime={match.fixtureDate ?? undefined}>
        <span>{dateParts.date}</span>
        <small>{match.season ? formatSeasonLabel(match.season) : dateParts.time}</small>
      </time>
      <strong>{homeTeamName}</strong>
      <span className="h2h-score">{headToHeadScoreText(match)}</span>
      <strong>{awayTeamName}</strong>
    </Link>
  );
}

function headToHeadScoreText(match: FixtureHeadToHeadMatch) {
  if (match.homeScore === null || match.homeScore === undefined || match.awayScore === null || match.awayScore === undefined) {
    return "-";
  }
  return `${match.homeScore}:${match.awayScore}`;
}

function fixtureScorers(
  events: FixtureEvent[],
  teamId: number | null,
  teamName: string | null,
  playerTeamIds: Map<number, number>,
) {
  const scorers = new Map<string, FixtureScorer>();

  events
    .filter((event) => isGoalEvent(event) || isOwnGoalEvent(event))
    .filter((event) => {
      if (isOwnGoalEvent(event)) {
        const playerTeamId = event.player?.id ? playerTeamIds.get(event.player.id) : null;
        if (teamId && playerTeamId) {
          return playerTeamId !== teamId;
        }
        if (teamId && event.team?.id) {
          return event.team.id !== teamId;
        }
        return Boolean(teamName && event.team?.name !== teamName);
      }
      if (teamId && event.team?.id) {
        return event.team.id === teamId;
      }
      return Boolean(teamName && event.team?.name === teamName);
    })
    .forEach((event) => {
      const ownGoal = isOwnGoalEvent(event);
      const playerId = event.player?.id ?? null;
      const playerName = displayEventPlayerName(event.player, "선수 정보 없음");
      const key = `${playerId ?? playerName}-${ownGoal ? "own" : "goal"}`;
      const current = scorers.get(key);
      const minute = eventMinute(event);
      const elapsed = event.time?.elapsed ?? Number.MAX_SAFE_INTEGER;

      if (current) {
        current.minutes.push(minute);
        current.firstMinute = Math.min(current.firstMinute, elapsed);
      } else {
        scorers.set(key, {
          playerId,
          playerName,
          ownGoal,
          minutes: [minute],
          firstMinute: elapsed,
        });
      }
    });

  return [...scorers.values()].sort((left, right) => left.firstMinute - right.firstMinute);
}

function lineupPlayerTeamIds(lineups: FixtureLineupResponse | null) {
  const playerTeamIds = new Map<number, number>();
  [lineups?.homeTeam, lineups?.awayTeam].forEach((team) => {
    [...(team?.starters ?? []), ...(team?.substitutes ?? [])].forEach((player) => {
      if (team?.teamId) {
        playerTeamIds.set(player.playerId, team.teamId);
      }
    });
  });
  return playerTeamIds;
}

function FixtureScorerList({ scorers }: { scorers: FixtureScorer[] }) {
  if (!scorers.length) {
    return null;
  }

  return (
    <div className="fixture-scorer-list">
      {scorers.map((scorer) => (
        <div
          className={scorer.ownGoal ? "fixture-scorer own-goal" : "fixture-scorer"}
          key={`${scorer.playerId ?? scorer.playerName}-${scorer.ownGoal}`}
        >
          <span className="fixture-scorer-name">
            <span aria-label="골" className="fixture-goal-icon" role="img">⚽</span>
            {scorer.playerId ? (
              <Link to={`/players/${scorer.playerId}`}>{scorer.playerName}</Link>
            ) : (
              scorer.playerName
            )}
            {scorer.ownGoal ? " (자책골)" : ""}
          </span>
          <span>{scorer.minutes.join(", ")}</span>
        </div>
      ))}
    </div>
  );
}

type FixtureRedCard = {
  playerId: number | null;
  playerName: string;
  minutes: string[];
  firstMinute: number;
};

function fixtureRedCards(
  events: FixtureEvent[],
  teamId: number | null,
  teamName: string | null,
) {
  const redCards = new Map<string, FixtureRedCard>();

  events
    .filter(isRedCardEvent)
    .filter((event) => {
      if (teamId && event.team?.id) {
        return event.team.id === teamId;
      }
      return Boolean(teamName && event.team?.name === teamName);
    })
    .forEach((event) => {
      const playerId = event.player?.id ?? null;
      const playerName = displayEventPlayerName(event.player, "선수 정보 없음");
      const key = `${playerId ?? playerName}`;
      const current = redCards.get(key);
      const minute = eventMinute(event);
      const elapsed = event.time?.elapsed ?? Number.MAX_SAFE_INTEGER;

      if (current) {
        current.minutes.push(minute);
        current.firstMinute = Math.min(current.firstMinute, elapsed);
      } else {
        redCards.set(key, {
          playerId,
          playerName,
          minutes: [minute],
          firstMinute: elapsed,
        });
      }
    });

  return [...redCards.values()].sort((left, right) => left.firstMinute - right.firstMinute);
}

function FixtureRedCardList({ redCards }: { redCards: FixtureRedCard[] }) {
  if (!redCards.length) {
    return null;
  }

  return (
    <div className="fixture-red-card-list">
      {redCards.map((redCard) => (
        <div className="fixture-red-card" key={redCard.playerId ?? redCard.playerName}>
          <span className="fixture-red-card-icon" aria-label="레드카드" />
          <span>
            {redCard.playerId ? (
              <Link to={`/players/${redCard.playerId}`}>{redCard.playerName}</Link>
            ) : (
              redCard.playerName
            )}
          </span>
          <span>{redCard.minutes.join(", ")}</span>
        </div>
      ))}
    </div>
  );
}

function EventsPanel({ state, fixture }: { state: LoadState<FixtureEventResponse>; fixture: FixtureSummary }) {
  if (state.isLoading) {
    return <SectionLoading label="이벤트를 불러오는 중입니다." />;
  }

  if (state.error) {
    return <SectionError message={state.error} />;
  }

  const events = state.data?.events ?? [];
  if (!events.length) {
    return <EmptyPanel message="저장된 경기 이벤트가 없습니다." />;
  }
  const timeline = buildEventTimeline(events, fixture);

  return (
    <article className="panel detail-panel">
      <div className="detail-panel-heading">
        <h2>이벤트</h2>
      </div>
      <div className="event-timeline">
        {timeline.map((item, index) => (
          <EventRow item={item} key={item.kind === "section" ? `${item.id}-${index}` : `${item.event.sequence ?? index}-${index}`} />
        ))}
      </div>
    </article>
  );
}

function EventRow({ item }: { item: EventTimelineItem }) {
  if (item.kind === "section") {
    return (
      <div className="event-timeline-section" role="separator">
        <span>{item.label}</span>
      </div>
    );
  }

  const icon = eventIcon(item.category);
  const minute = eventMinuteParts(item.event);

  return (
    <article className={`event-timeline-row ${item.side}`}>
      <div className="event-body">
        {eventMarkup(item)}
        {item.event.comments ? <p className="muted">{item.event.comments}</p> : null}
      </div>
      <div className="event-center">
        <span className="event-minute" aria-label={minute.label} title={minute.label}>
          <span className="event-minute-main">{minute.elapsed}</span>
          {minute.extra ? <span className="event-minute-extra">+{minute.extra}</span> : null}
        </span>
        <img className="event-icon" src={icon.src} alt={icon.label} title={icon.label} />
      </div>
    </article>
  );
}

function eventMarkup(item: EventTimelineEventItem) {
  const { category, event, score } = item;
  if (category === "substitution") {
    return (
      <div className="event-substitution">
        {hasEventPlayerName(event.assist) ? (
          <p className="event-in">
            <span>IN</span> <EventPlayerLink player={event.assist} />
          </p>
        ) : null}
        <p className="event-out">
          <span>OUT</span> <EventPlayerLink player={event.player} />
        </p>
      </div>
    );
  }

  if (category === "var") {
    return (
      <p className="event-primary">
        <strong>{varEventLabel(event)}</strong>
      </p>
    );
  }

  const player = hasEventPlayerName(event.player)
    ? <EventPlayerLink player={event.player} />
    : <>{event.team?.name ?? "이벤트 정보 없음"}</>;

  return (
    <>
      <p className="event-primary">
        <strong>{player}</strong>
        {score ? <span className="event-score">({score.home} - {score.away})</span> : null}
      </p>
      {category === "goal" && hasEventPlayerName(event.assist) ? (
        <p className="event-assist">
          <EventPlayerLink player={event.assist} />의 도움
        </p>
      ) : null}
      {category === "goal" && normalizedEventDetail(event) === "penalty" ? <p className="muted">페널티</p> : null}
      {category === "own-goal" ? <p className="muted">자책골</p> : null}
      {category === "missed-penalty" ? <p className="muted">페널티 실축</p> : null}
      {category === "event" && event.detail ? <p className="muted">{event.detail}</p> : null}
    </>
  );
}

function displayEventPlayerName(
  player: { name: string | null; nameKo?: string | null } | null | undefined,
  fallback = "-",
) {
  return displayLocalizedName(player?.nameKo, player?.name) || fallback;
}

function hasEventPlayerName(player: { name: string | null; nameKo?: string | null } | null | undefined) {
  return Boolean(player?.nameKo?.trim() || player?.name?.trim());
}

function EventPlayerLink({ player }: { player: { id: number; name: string | null; nameKo?: string | null } | null }) {
  const playerName = displayEventPlayerName(player);
  if (!player?.id) {
    return <>{playerName}</>;
  }

  return (
    <Link className="event-player-link" to={`/players/${player.id}`}>
      {playerName}
    </Link>
  );
}

function LineupsPanel({
  events,
  playerStats,
  state,
}: {
  events: FixtureEvent[];
  playerStats: FixturePlayerStatResponse | null;
  state: LoadState<FixtureLineupResponse>;
}) {
  if (state.isLoading) {
    return <SectionLoading label="라인업을 불러오는 중입니다." />;
  }

  if (state.error) {
    return <SectionError message={state.error} />;
  }

  if (!state.data?.homeTeam && !state.data?.awayTeam) {
    return <EmptyPanel message="저장된 라인업이 없습니다." />;
  }

  const playerDetails = buildLineupPlayerDetails(events, playerStats);
  const lineupPlayerIds = new Set(
    [state.data.homeTeam, state.data.awayTeam]
      .flatMap((team) => [...(team?.starters ?? []), ...(team?.substitutes ?? [])])
      .map((player) => player.playerId),
  );
  const topRatedPlayerId = selectTopRatedPlayerId(playerStats, lineupPlayerIds);

  return (
    <>
      <article className="panel lineup-pitch-panel">
        <FootballPitch
          homeTeam={state.data.homeTeam}
          awayTeam={state.data.awayTeam}
          playerDetails={playerDetails}
          topRatedPlayerId={topRatedPlayerId}
        />
      </article>
      <div className="lineup-detail-grid">
        <LineupTeamCard
          playerDetails={playerDetails}
          side="home"
          team={state.data.homeTeam}
          topRatedPlayerId={topRatedPlayerId}
        />
        <LineupTeamCard
          playerDetails={playerDetails}
          side="away"
          team={state.data.awayTeam}
          topRatedPlayerId={topRatedPlayerId}
        />
      </div>
    </>
  );
}

function FootballPitch({
  homeTeam,
  awayTeam,
  playerDetails,
  topRatedPlayerId,
}: {
  homeTeam: FixtureTeamLineup | null;
  awayTeam: FixtureTeamLineup | null;
  playerDetails: Map<number, LineupPlayerDetail>;
  topRatedPlayerId: number | null;
}) {
  const homePlayers = useMemo(() => positionedPlayers(homeTeam, "home"), [homeTeam]);
  const awayPlayers = useMemo(() => positionedPlayers(awayTeam, "away"), [awayTeam]);

  return (
    <>
      <div className="football-pitch" aria-label="라인업 포메이션">
        <div className="pitch-line halfway" />
        <div className="pitch-circle" />
        <div className="pitch-box left" />
        <div className="pitch-box right" />
        <div className="pitch-goal left" />
        <div className="pitch-goal right" />
        <FormationLabel side="home" team={homeTeam} />
        <FormationLabel side="away" team={awayTeam} />
        {[...homePlayers, ...awayPlayers].map((player) => (
          <PitchPlayer
            detail={playerDetails.get(player.player.playerId)}
            isTopRated={player.player.playerId === topRatedPlayerId}
            player={player}
            key={`${player.side}-${player.player.playerId}`}
          />
        ))}
      </div>
      <div className="mobile-lineup-board" aria-label="모바일 라인업 포메이션">
        <MobileLineupPitch
          awayTeam={awayTeam}
          homeTeam={homeTeam}
          playerDetails={playerDetails}
          topRatedPlayerId={topRatedPlayerId}
        />
      </div>
    </>
  );
}

function MobileLineupPitch({
  awayTeam,
  homeTeam,
  playerDetails,
  topRatedPlayerId,
}: {
  awayTeam: FixtureTeamLineup | null;
  homeTeam: FixtureTeamLineup | null;
  playerDetails: Map<number, LineupPlayerDetail>;
  topRatedPlayerId: number | null;
}) {
  const homePlayers = useMemo(() => positionedMobilePlayers(homeTeam, "home"), [homeTeam]);
  const awayPlayers = useMemo(() => positionedMobilePlayers(awayTeam, "away"), [awayTeam]);
  const players = [...homePlayers, ...awayPlayers];

  return (
    <section className="mobile-football-pitch" aria-label="모바일 라인업">
      <div className="mobile-pitch-line halfway" />
      <div className="mobile-pitch-circle center" />
      <div className="mobile-pitch-box top" />
      <div className="mobile-pitch-box bottom" />
      <div className="mobile-formation-label home">
        <strong>{displayLocalizedName(homeTeam?.teamNameKo, homeTeam?.teamName ?? "홈팀")}</strong>
        <span>{homeTeam?.formation ?? "-"}</span>
      </div>
      <div className="mobile-formation-label away">
        <strong>{displayLocalizedName(awayTeam?.teamNameKo, awayTeam?.teamName ?? "원정팀")}</strong>
        <span>{awayTeam?.formation ?? "-"}</span>
      </div>
      {players.map((player) => (
        <PitchPlayer
          detail={playerDetails.get(player.player.playerId)}
          isTopRated={player.player.playerId === topRatedPlayerId}
          player={player}
          key={`${player.side}-mobile-${player.player.playerId}`}
        />
      ))}
    </section>
  );
}

function FormationLabel({ side, team }: { side: Side; team: FixtureTeamLineup | null }) {
  return (
    <div className={`formation-label ${side}`}>
      <strong>{displayLocalizedName(team?.teamNameKo, team?.teamName ?? (side === "home" ? "홈팀" : "원정팀"))}</strong>
      <span>{team?.formation ?? "-"}</span>
    </div>
  );
}

function displayLineupPlayerName(player: FixtureLineupPlayer) {
  return displayLocalizedName(player.playerNameKo, player.playerName);
}

function PitchPlayer({
  detail,
  isTopRated,
  player,
}: {
  detail?: LineupPlayerDetail;
  isTopRated: boolean;
  player: PositionedPlayer;
}) {
  const colors = player.color;
  const style = {
    left: `${player.left}%`,
    top: `${player.top}%`,
    "--kit-bg": normalizeKitColor(colors.primary, player.side === "home" ? "#b4d455" : "#b9d7f4"),
    "--kit-fg": normalizeKitColor(colors.number, "#111827"),
    "--kit-border": normalizeKitColor(colors.border, "rgba(17, 24, 39, 0.5)"),
  } as CSSProperties;

  return (
    <Link
      className="pitch-player player-name-link"
      style={style}
      title={displayLineupPlayerName(player.player) || undefined}
      to={`/players/${player.player.playerId}`}
    >
      <div className="pitch-player-visual">
        <PlayerPhoto player={player.player} />
        {!player.player.photoUrl ? <span className="pitch-player-number">{player.player.backNumber ?? "-"}</span> : null}
        {detail?.rating ? (
          <b className={ratingClassName(detail.rating, isTopRated)}>
            {isTopRated ? "★ " : ""}{detail.rating.toFixed(1)}
          </b>
        ) : null}
        {detail?.subbedOutMinute ? (
          <small className="pitch-substitution-minute out">OUT {detail.subbedOutMinute}</small>
        ) : null}
      </div>
      <strong>{shortName(displayLineupPlayerName(player.player))}</strong>
      <LineupEventBadges detail={detail} compact />
    </Link>
  );
}

function LineupTeamCard({
  playerDetails,
  side,
  team,
  topRatedPlayerId,
}: {
  playerDetails: Map<number, LineupPlayerDetail>;
  side: Side;
  team: FixtureTeamLineup | null;
  topRatedPlayerId: number | null;
}) {
  if (!team) {
    return (
      <article className="panel lineup-team-card">
        <div className="empty-state">라인업 정보가 없습니다.</div>
      </article>
    );
  }

  return (
    <article className={`panel lineup-team-card ${side}`}>
      <div className="lineup-team-heading">
        <div>
          <h2>{displayLocalizedName(team.teamNameKo, team.teamName ?? "팀")}</h2>
          <p className="muted">포메이션 {team.formation ?? "-"}</p>
        </div>
      </div>
      <div className="lineup-coach-row">
        <span>감독</span>
        <strong>{team.coachName ?? "-"}</strong>
      </div>
      <LineupList
        playerDetails={playerDetails}
        title="교체 명단"
        players={team.substitutes ?? []}
        topRatedPlayerId={topRatedPlayerId}
      />
      <AbsenceList team={team} />
    </article>
  );
}

function LineupList({
  playerDetails,
  title,
  players,
  topRatedPlayerId,
}: {
  playerDetails: Map<number, LineupPlayerDetail>;
  title: string;
  players: FixtureLineupPlayer[];
  topRatedPlayerId: number | null;
}) {
  const sortedPlayers = sortLineupPlayers(players);

  return (
    <div className="lineup-list-block">
      <h3>{title}</h3>
      <div className="lineup-list">
        {sortedPlayers.length ? (
          sortedPlayers.map((player) => {
            const detail = playerDetails.get(player.playerId);
            return (
              <div className="lineup-list-row" key={`${title}-${player.playerId}`}>
                <PlayerPhoto player={player} />
                <span className="lineup-shirt-number">{player.backNumber ?? "-"}</span>
                <div className="lineup-player-summary">
                  <div className="lineup-player-name-row">
                    <Link className="lineup-player-link" to={`/players/${player.playerId}`}>
                      {displayLineupPlayerName(player)}
                    </Link>
                    <LineupEventBadges detail={detail} />
                  </div>
                  <small>{player.position ?? "-"}</small>
                </div>
                {detail?.rating ? (
                  <b className={ratingClassName(detail.rating, player.playerId === topRatedPlayerId)}>
                    {player.playerId === topRatedPlayerId ? "★ " : ""}{detail.rating.toFixed(1)}
                  </b>
                ) : null}
                <LineupSubstitutionMinutes detail={detail} />
              </div>
            );
          })
        ) : (
          <div className="lineup-list-row empty">정보 없음</div>
        )}
      </div>
    </div>
  );
}

function LineupSubstitutionMinutes({ detail }: { detail?: LineupPlayerDetail }) {
  if (!detail?.subbedInMinute && !detail?.subbedOutMinute) {
    return <em>-</em>;
  }

  return (
    <em className="lineup-substitution-minutes">
      {detail.subbedInMinute ? <span className="lineup-substitution-minute in">IN {detail.subbedInMinute}</span> : null}
      {detail.subbedOutMinute ? <span className="lineup-substitution-minute out">OUT {detail.subbedOutMinute}</span> : null}
    </em>
  );
}

type LineupPlayerDetail = {
  rating: number | null;
  goals: number;
  ownGoals: number;
  assists: number;
  yellowCards: number;
  redCards: number;
  goalMinutes: string[];
  ownGoalMinutes: string[];
  assistMinutes: string[];
  yellowCardMinutes: string[];
  redCardMinutes: string[];
  subbedInMinute: string | null;
  subbedOutMinute: string | null;
};

function buildLineupPlayerDetails(
  events: FixtureEvent[],
  playerStats: FixturePlayerStatResponse | null,
) {
  const details = new Map<number, LineupPlayerDetail>();
  const ensureDetail = (playerId: number) => {
    const current = details.get(playerId);
    if (current) {
      return current;
    }
    const created: LineupPlayerDetail = {
      rating: null,
      goals: 0,
      ownGoals: 0,
      assists: 0,
      yellowCards: 0,
      redCards: 0,
      goalMinutes: [],
      ownGoalMinutes: [],
      assistMinutes: [],
      yellowCardMinutes: [],
      redCardMinutes: [],
      subbedInMinute: null,
      subbedOutMinute: null,
    };
    details.set(playerId, created);
    return created;
  };

  [playerStats?.homeTeam, playerStats?.awayTeam].forEach((team) => {
    (team?.players ?? []).forEach((player) => {
      const detail = ensureDetail(player.playerId);
      detail.rating = player.rating && player.rating > 0 ? player.rating : null;
      detail.goals = player.goals ?? 0;
      detail.assists = player.assists ?? 0;
      detail.yellowCards = player.yellowCards ?? 0;
      detail.redCards = player.redCards ?? 0;
    });
  });

  events.forEach((event) => {
    const minute = eventMinute(event);
    if (isSubstitutionEvent(event)) {
      if (event.player?.id) {
        ensureDetail(event.player.id).subbedOutMinute = minute;
      }
      if (event.assist?.id) {
        ensureDetail(event.assist.id).subbedInMinute = minute;
      }
      return;
    }

    if (isOwnGoalEvent(event)) {
      if (event.player?.id) {
        ensureDetail(event.player.id).ownGoalMinutes.push(minute);
      }
      return;
    }

    if (isGoalEvent(event)) {
      if (event.player?.id) {
        ensureDetail(event.player.id).goalMinutes.push(minute);
      }
      if (event.assist?.id) {
        ensureDetail(event.assist.id).assistMinutes.push(minute);
      }
      return;
    }

    if (isYellowCardEvent(event) && event.player?.id) {
      ensureDetail(event.player.id).yellowCardMinutes.push(minute);
    }
    if (isRedCardEvent(event) && event.player?.id) {
      ensureDetail(event.player.id).redCardMinutes.push(minute);
    }
  });

  details.forEach((detail) => {
    detail.goals = Math.max(detail.goals, detail.goalMinutes.length);
    detail.ownGoals = detail.ownGoalMinutes.length;
    detail.assists = Math.max(detail.assists, detail.assistMinutes.length);
    detail.yellowCards = Math.max(detail.yellowCards, detail.yellowCardMinutes.length);
    detail.redCards = Math.max(detail.redCards, detail.redCardMinutes.length);
  });

  return details;
}

function selectTopRatedPlayerId(
  playerStats: FixturePlayerStatResponse | null,
  lineupPlayerIds: Set<number>,
) {
  const players = [playerStats?.homeTeam, playerStats?.awayTeam]
    .flatMap((team) => team?.players ?? [])
    .filter((player) => lineupPlayerIds.has(player.playerId) && player.rating !== null && player.rating > 0)
    .sort(compareTopRatedPlayers);
  return players[0]?.playerId ?? null;
}

function compareTopRatedPlayers(left: FixturePlayerStat, right: FixturePlayerStat) {
  return (
    compareNumberDescending(left.rating, right.rating) ||
    compareNumberDescending(left.goals, right.goals) ||
    compareNumberDescending(left.assists, right.assists) ||
    compareNumberDescending(left.passesKey, right.passesKey) ||
    compareNumberDescending(left.passesTotal, right.passesTotal) ||
    compareNumberDescending(left.shotsTotal, right.shotsTotal) ||
    compareNumberAscending(left.foulsCommitted, right.foulsCommitted) ||
    compareNumberAscending(left.yellowCards, right.yellowCards) ||
    left.playerId - right.playerId
  );
}

function compareNumberDescending(left: number | null, right: number | null) {
  return (right ?? 0) - (left ?? 0);
}

function compareNumberAscending(left: number | null, right: number | null) {
  return (left ?? 0) - (right ?? 0);
}

function PlayerPhoto({ player }: { player: FixtureLineupPlayer }) {
  const [imageFailed, setImageFailed] = useState(false);
  if (!player.photoUrl || imageFailed) {
    return <span className="lineup-player-photo placeholder" aria-hidden="true">{shortName(displayLineupPlayerName(player)).slice(0, 1)}</span>;
  }
  return (
    <img
      alt=""
      className="lineup-player-photo"
      onError={() => setImageFailed(true)}
      src={player.photoUrl}
    />
  );
}

function LineupEventBadges({ compact = false, detail }: { compact?: boolean; detail?: LineupPlayerDetail }) {
  if (!detail) {
    return null;
  }

  const badges = [
    detail.goals > 0 ? { className: "goal", label: `⚽${detail.goals > 1 ? ` ${detail.goals}` : ""}`, title: eventTitle("골", detail.goalMinutes) } : null,
    detail.ownGoals > 0 ? { className: "own-goal", label: detail.ownGoals > 1 ? `${detail.ownGoals}` : "", title: eventTitle("자책골", detail.ownGoalMinutes), icon: "own-goal" } : null,
    detail.assists > 0 ? { className: "assist", label: `👟${detail.assists > 1 ? ` ${detail.assists}` : ""}`, title: eventTitle("도움", detail.assistMinutes) } : null,
    detail.yellowCards > 0 ? { className: "yellow", label: detail.yellowCards > 1 ? `${detail.yellowCards}` : "", title: eventTitle("경고", detail.yellowCardMinutes) } : null,
    detail.redCards > 0 ? { className: "red", label: detail.redCards > 1 ? `${detail.redCards}` : "", title: eventTitle("퇴장", detail.redCardMinutes) } : null,
  ].filter(Boolean) as Array<{ className: string; icon?: "own-goal"; label: string; title: string }>;

  if (!badges.length) {
    return null;
  }

  return (
    <span className={`lineup-event-badges${compact ? " compact" : ""}`}>
      {badges.map((badge) => (
        <span className={`lineup-event-badge ${badge.className}`} key={badge.className} title={badge.title}>
          {badge.icon === "own-goal" ? <OwnGoalBallIcon /> : null}
          {badge.label}
        </span>
      ))}
    </span>
  );
}

function OwnGoalBallIcon() {
  return (
    <svg
      aria-hidden="true"
      className="own-goal-ball-icon"
      viewBox="0 0 24 24"
    >
      <circle cx="12" cy="12" r="9.25" />
      <path d="m12 7.2 3 2.2-1.15 3.5h-3.7L9 9.4l3-2.2Z" />
      <path d="m12 2.75 0 4.45M3.3 9.2 9 9.4M5.55 18.7l4.6-5.8M18.45 18.7l-4.6-5.8M20.7 9.2 15 9.4" />
      <path d="m6.7 4.55 2.3 4.85M17.3 4.55 15 9.4M2.95 14.75l7.2-1.85M21.05 14.75l-7.2-1.85M9.35 21l.8-8.1M14.65 21l-.8-8.1" />
    </svg>
  );
}

function eventTitle(label: string, minutes: string[]) {
  return minutes.length ? `${label} ${minutes.join(", ")}` : label;
}

function ratingClassName(rating: number, isTopRated = false) {
  if (isTopRated) {
    return "lineup-rating top-rated";
  }
  if (rating >= 7.5) {
    return "lineup-rating excellent";
  }
  if (rating >= 7) {
    return "lineup-rating good";
  }
  return "lineup-rating";
}

function AbsenceList({ team }: { team: FixtureTeamLineup }) {
  return (
    <div className="lineup-list-block">
      <h3>결장</h3>
      <div className="lineup-list">
        {team.absences?.length ? (
          team.absences.map((absence) => (
            <div className="lineup-list-row absence" key={`absence-${absence.playerId}`}>
              <span>-</span>
              <Link className="lineup-player-link" to={`/players/${absence.playerId}`}>
                {displayLocalizedName(absence.playerNameKo, absence.playerName)}
              </Link>
              <em>{absence.reason ?? absence.absenceType ?? "-"}</em>
            </div>
          ))
        ) : (
          <div className="lineup-list-row empty">결장 정보 없음</div>
        )}
      </div>
    </div>
  );
}

function StatsPanel({
  fixture,
  statsState,
  playerStatsState,
}: {
  fixture: FixtureSummary;
  statsState: LoadState<FixtureStatResponse>;
  playerStatsState: LoadState<FixturePlayerStatResponse>;
}) {
  return (
    <div className="detail-stats-stack">
      {isLiveFixture(fixture) ? (
        <p className="live-stats-delay-notice" role="note">
          진행 중인 경기의 팀·선수 통계는 데이터 제공처의 갱신 상황에 따라 실제 경기보다 늦게 반영될 수 있습니다.
        </p>
      ) : null}
      <TeamStatsPanel fixture={fixture} state={statsState} />
      <PlayerStatsPanel state={playerStatsState} />
    </div>
  );
}

function TeamStatsPanel({ fixture, state }: { fixture: FixtureSummary; state: LoadState<FixtureStatResponse> }) {
  if (state.isLoading) {
    return <SectionLoading label="팀 통계를 불러오는 중입니다." />;
  }

  if (state.error) {
    return <SectionError message={state.error} />;
  }

  if (!state.data?.homeTeamStat && !state.data?.awayTeamStat) {
    return <EmptyPanel message="저장된 팀 통계가 없습니다." />;
  }

  const rows = teamStatRows(state.data.homeTeamStat, state.data.awayTeamStat);

  return (
    <article className="panel detail-panel">
      <div className="detail-panel-heading">
        <h2>팀 통계</h2>
      </div>
      <div className="team-stat-comparison">
        <div className="team-stat-head">
          <strong>{displayLocalizedName(state.data.homeTeamStat?.teamNameKo ?? fixture.homeTeamNameKo, state.data.homeTeamStat?.teamName ?? fixture.homeTeamName ?? "홈팀")}</strong>
          <strong>{displayLocalizedName(state.data.awayTeamStat?.teamNameKo ?? fixture.awayTeamNameKo, state.data.awayTeamStat?.teamName ?? fixture.awayTeamName ?? "원정팀")}</strong>
        </div>
        {rows.map((row) => (
          <StatCompareRow key={row.label} row={row} />
        ))}
      </div>
    </article>
  );
}

function StatCompareRow({ row }: { row: TeamStatRow }) {
  const total = Math.max(Number(row.home) + Number(row.away), 1);
  const homeWidth = `${(Number(row.home) / total) * 100}%`;
  const awayWidth = `${(Number(row.away) / total) * 100}%`;

  return (
    <div className="team-stat-row">
      <div className="team-stat-values">
        <strong>{formatStat(row.home, row.format)}</strong>
        <span>{row.label}</span>
        <strong>{formatStat(row.away, row.format)}</strong>
      </div>
      <div className="team-stat-bars">
        <span className="home" style={{ width: homeWidth }} />
        <span className="away" style={{ width: awayWidth }} />
      </div>
    </div>
  );
}

function PlayerStatsPanel({ state }: { state: LoadState<FixturePlayerStatResponse> }) {
  if (state.isLoading) {
    return <SectionLoading label="선수별 경기 통계를 불러오는 중입니다." />;
  }

  if (state.error) {
    return <SectionError message={state.error} />;
  }

  const groups = [state.data?.homeTeam, state.data?.awayTeam].filter(Boolean) as FixtureTeamPlayerStats[];
  if (!groups.length) {
    return <EmptyPanel message="저장된 선수별 경기 통계가 없습니다." />;
  }

  return (
    <article className="panel detail-panel">
      <div className="detail-panel-heading">
        <h2>선수별 경기 통계</h2>
      </div>
      <div className="player-stat-team-grid">
        {groups.map((group) => (
          <PlayerStatTable group={group} key={group.teamId} />
        ))}
      </div>
    </article>
  );
}

function PlayerStatTable({ group }: { group: FixtureTeamPlayerStats }) {
  const [sort, setSort] = useState<{ key: PlayerStatSortKey | null; direction: SortDirection }>({
    key: null,
    direction: "desc",
  });
  const players = sortPlayerStats(group.players ?? [], sort.key, sort.direction);

  function updateSort(key: PlayerStatSortKey) {
    setSort((current) => ({
      key,
      direction: current.key === key && current.direction === "desc" ? "asc" : "desc",
    }));
  }

  return (
    <section className="player-stat-team">
      <h3>{displayLocalizedName(group.teamNameKo, group.teamName ?? "팀")}</h3>
      <div className="player-stat-table-wrap">
        <table className="player-stat-table">
          <thead>
            <tr>
              <th>선수</th>
              <th>포지션</th>
              <SortableStatHead activeKey={sort.key} direction={sort.direction} label="분" sortKey="minutes" onSort={updateSort} />
              <SortableStatHead activeKey={sort.key} direction={sort.direction} label="평점" sortKey="rating" onSort={updateSort} />
              <SortableStatHead activeKey={sort.key} direction={sort.direction} label="골" sortKey="goals" onSort={updateSort} />
              <SortableStatHead activeKey={sort.key} direction={sort.direction} label="도움" sortKey="assists" onSort={updateSort} />
              <SortableStatHead activeKey={sort.key} direction={sort.direction} label="슈팅" sortKey="shots" onSort={updateSort} />
              <SortableStatHead activeKey={sort.key} direction={sort.direction} label="패스" sortKey="passes" onSort={updateSort} />
              <SortableStatHead activeKey={sort.key} direction={sort.direction} label="태클" sortKey="tackles" onSort={updateSort} />
              <th>카드</th>
            </tr>
          </thead>
          <tbody>
            {players.length ? (
              players.map((player) => (
                <PlayerStatRow key={player.playerId} player={player} />
              ))
            ) : (
              <tr>
                <td colSpan={10}>선수 통계가 없습니다.</td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
    </section>
  );
}

function SortableStatHead({
  activeKey,
  direction,
  label,
  onSort,
  sortKey,
}: {
  activeKey: PlayerStatSortKey | null;
  direction: SortDirection;
  label: string;
  onSort: (key: PlayerStatSortKey) => void;
  sortKey: PlayerStatSortKey;
}) {
  const isActive = activeKey === sortKey;
  return (
    <th>
      <button
        aria-label={`${label} 기준 정렬`}
        className={`player-stat-sort-button${isActive ? " active" : ""}`}
        onClick={() => onSort(sortKey)}
        type="button"
      >
        {label}
        {isActive ? <span aria-hidden="true">{direction === "desc" ? "↓" : "↑"}</span> : null}
      </button>
    </th>
  );
}

function PlayerStatRow({ player }: { player: FixturePlayerStat }) {
  return (
    <tr>
      <td>
        <Link className="player-stat-link" to={`/players/${player.playerId}`}>
          {player.jerseyNumber ? `${player.jerseyNumber}. ` : ""}
          {displayLocalizedName(player.playerNameKo, player.playerName)}
        </Link>
      </td>
      <td>{compactPosition(player.position)}</td>
      <td>{numberText(player.minutesPlayed)}</td>
      <td>{player.rating ? <span className={ratingClassName(player.rating)}>{player.rating.toFixed(1)}</span> : "-"}</td>
      <td>{numberText(player.goals)}</td>
      <td>{numberText(player.assists)}</td>
      <td>
        {numberText(player.shotsOnTarget)} / {numberText(player.shotsTotal)}
      </td>
      <td>{numberText(player.passesTotal)}</td>
      <td>{numberText(player.tacklesTotal)}</td>
      <td>
        <CardStatIcons yellowCards={player.yellowCards} redCards={player.redCards} />
      </td>
    </tr>
  );
}

function CardStatIcons({ yellowCards, redCards }: { yellowCards: number | null; redCards: number | null }) {
  const yellow = yellowCards ?? 0;
  const red = redCards ?? 0;
  if (yellow === 0 && red === 0) {
    return <span className="muted">-</span>;
  }
  return (
    <span className="player-card-icons">
      {yellow > 0 ? (
        <span className="player-card-icon" title={`옐로카드 ${yellow}`}>
          <img src="/events/yellow-card.svg" alt="옐로카드" />
          {yellow > 1 ? <span>{yellow}</span> : null}
        </span>
      ) : null}
      {red > 0 ? (
        <span className="player-card-icon" title={`레드카드 ${red}`}>
          <img src="/events/red-card.svg" alt="레드카드" />
          {red > 1 ? <span>{red}</span> : null}
        </span>
      ) : null}
    </span>
  );
}

function sortPlayerStats(players: FixturePlayerStat[], sortKey: PlayerStatSortKey | null, direction: SortDirection) {
  const sorted = players.slice();
  if (!sortKey) {
    return sorted.sort(compareDefaultPlayerStats);
  }
  return sorted.sort((left, right) => {
    const order = comparePlayerStatValue(left, right, sortKey)
      || compareDefaultPlayerStats(left, right);
    return direction === "desc" ? order : -order;
  });
}

function comparePlayerStatValue(left: FixturePlayerStat, right: FixturePlayerStat, key: PlayerStatSortKey) {
  if (key === "shots") {
    return compareNumberDescending(left.shotsOnTarget ?? 0, right.shotsOnTarget ?? 0)
      || compareNumberDescending(left.shotsTotal ?? 0, right.shotsTotal ?? 0);
  }
  return compareNumberDescending(playerStatValue(left, key), playerStatValue(right, key));
}

function compareDefaultPlayerStats(left: FixturePlayerStat, right: FixturePlayerStat) {
  const minuteOrder = zeroMinuteRank(left.minutesPlayed) - zeroMinuteRank(right.minutesPlayed);
  if (minuteOrder !== 0) {
    return minuteOrder;
  }
  return comparePlayerOrder(
    { position: left.position, number: left.jerseyNumber, name: left.playerName },
    { position: right.position, number: right.jerseyNumber, name: right.playerName },
  );
}

function playerStatValue(player: FixturePlayerStat, key: PlayerStatSortKey) {
  switch (key) {
    case "minutes":
      return player.minutesPlayed ?? 0;
    case "rating":
      return player.rating ?? 0;
    case "goals":
      return player.goals ?? 0;
    case "assists":
      return player.assists ?? 0;
    case "shots":
      return player.shotsOnTarget ?? 0;
    case "passes":
      return player.passesTotal ?? 0;
    case "tackles":
      return player.tacklesTotal ?? 0;
    default:
      return 0;
  }
}

function compactPosition(position: string | null) {
  const normalized = position?.trim().toUpperCase() ?? "";
  if (normalized === "G" || normalized === "GK" || normalized.includes("GOAL")) {
    return "G";
  }
  if (normalized === "D" || normalized.includes("DEF")) {
    return "D";
  }
  if (normalized === "M" || normalized.includes("MID")) {
    return "M";
  }
  if (normalized === "F" || normalized.includes("ATT") || normalized.includes("FOR")) {
    return "F";
  }
  return normalized.slice(0, 1) || "-";
}

function SectionLoading({ label }: { label: string }) {
  return (
    <article className="panel detail-panel">
      <div className="empty-state">{label}</div>
    </article>
  );
}

function SectionError({ message }: { message: string }) {
  return (
    <article className="panel detail-panel">
      <div className="section-error">{message}</div>
    </article>
  );
}

function EmptyPanel({ message }: { message: string }) {
  return (
    <article className="panel detail-panel">
      <div className="empty-state">{message}</div>
    </article>
  );
}

type PositionedPlayer = {
  player: FixtureLineupPlayer;
  side: Side;
  left: number;
  top: number;
  color: FixtureColorInfo;
};

function positionedPlayers(team: FixtureTeamLineup | null, side: Side): PositionedPlayer[] {
  if (!team?.starters?.length) {
    return [];
  }

  const parsedPlayers = team.starters
    .map((player) => ({ player, grid: parseGrid(player.grid) }))
    .filter((item): item is { player: FixtureLineupPlayer; grid: { row: number; column: number } } => Boolean(item.grid));
  const columnsByRow = new Map<number, number[]>();

  parsedPlayers.forEach(({ grid }) => {
    const columns = columnsByRow.get(grid.row) ?? [];
    columnsByRow.set(grid.row, [...columns, grid.column].sort((a, b) => a - b));
  });

  return parsedPlayers.map(({ player, grid }) => {
      const isGoalkeeper = isGoalkeeperPosition(player.position);
      const color = (isGoalkeeper ? team.colors?.goalkeeper : team.colors?.player) ?? {
        primary: side === "home" ? "#b4d455" : "#b9d7f4",
        number: "#102015",
        border: "#ffffff",
      };

      const rowColumns = columnsByRow.get(grid.row) ?? [grid.column];

      return {
        player,
        side,
        left: playerLeft(grid.row, side),
        top: playerTop(grid.column, rowColumns),
        color,
      };
    });
}

function positionedMobilePlayers(team: FixtureTeamLineup | null, side: Side): PositionedPlayer[] {
  if (!team?.starters?.length) {
    return [];
  }

  const parsedPlayers = team.starters
    .map((player) => ({ player, grid: parseGrid(player.grid) }))
    .filter((item): item is { player: FixtureLineupPlayer; grid: { row: number; column: number } } => Boolean(item.grid));
  const columnsByRow = new Map<number, number[]>();

  parsedPlayers.forEach(({ grid }) => {
    const columns = columnsByRow.get(grid.row) ?? [];
    columnsByRow.set(grid.row, [...columns, grid.column].sort((a, b) => a - b));
  });

  return parsedPlayers.map(({ player, grid }) => {
    const isGoalkeeper = isGoalkeeperPosition(player.position);
    const color = (isGoalkeeper ? team.colors?.goalkeeper : team.colors?.player) ?? {
      primary: side === "home" ? "#b4d455" : "#b9d7f4",
      number: "#102015",
      border: "#ffffff",
    };
    const rowColumns = columnsByRow.get(grid.row) ?? [grid.column];

    return {
      player,
      side,
      left: playerTop(grid.column, rowColumns),
      top: mobilePlayerTop(grid.row, side),
      color,
    };
  });
}

function parseGrid(value: string | null) {
  if (!value) {
    return null;
  }

  const [rowValue, columnValue] = value.split(":").map((item) => Number(item));
  if (!Number.isFinite(rowValue) || !Number.isFinite(columnValue)) {
    return null;
  }

  return { row: rowValue, column: columnValue };
}

function playerLeft(row: number, side: Side) {
  const clampedRow = Math.max(1, Math.min(row, 5));
  const withinHalf = 8 + ((clampedRow - 1) / 4) * 34;
  return side === "home" ? withinHalf : 100 - withinHalf;
}

function playerTop(column: number, rowColumns: number[]) {
  const orderedColumns = rowColumns.slice().sort((a, b) => a - b);
  const columnIndex = Math.max(0, orderedColumns.indexOf(column));
  const rowSize = Math.max(orderedColumns.length, 1);
  const step = rowSize === 1 ? 0 : Math.min(32, 76 / (rowSize - 1));
  return 50 + (columnIndex - (rowSize - 1) / 2) * step;
}

function mobilePlayerTop(row: number, side: Side) {
  const clampedRow = Math.max(1, Math.min(row, 5));
  const topHalf = 7 + ((clampedRow - 1) / 4) * 40;
  return side === "home" ? topHalf : 100 - topHalf;
}

function sortLineupPlayers(players: FixtureLineupPlayer[]) {
  return players.slice().sort((a, b) => {
    return comparePlayerOrder(
      { position: a.position, number: a.backNumber, name: a.playerName },
      { position: b.position, number: b.backNumber, name: b.playerName },
    );
  });
}

function comparePlayerOrder(
  left: { position: string | null; number: number | null; name: string | null },
  right: { position: string | null; number: number | null; name: string | null },
) {
  return (
    positionRank(left.position) - positionRank(right.position) ||
    compareNullableNumber(left.number, right.number) ||
    (left.name ?? "").localeCompare(right.name ?? "")
  );
}

function positionRank(position: string | null) {
  const normalized = (position ?? "").toUpperCase();
  if (normalized === "G" || normalized === "GK" || normalized.includes("GOAL")) {
    return 0;
  }
  if (normalized === "D" || normalized.includes("DEF")) {
    return 1;
  }
  if (normalized === "M" || normalized.includes("MID")) {
    return 2;
  }
  if (normalized === "F" || normalized === "A" || normalized.includes("ATT") || normalized.includes("FOR")) {
    return 3;
  }
  return 4;
}

function zeroMinuteRank(minutesPlayed: number | null | undefined) {
  return minutesPlayed === 0 ? 1 : 0;
}

function compareNullableNumber(left: number | null | undefined, right: number | null | undefined) {
  if (left === right) {
    return 0;
  }
  if (left === null || left === undefined) {
    return 1;
  }
  if (right === null || right === undefined) {
    return -1;
  }
  return left - right;
}

function normalizeKitColor(value: string | null | undefined, fallback: string) {
  if (!value) {
    return fallback;
  }

  const trimmed = value.trim();
  if (!trimmed || trimmed.toLowerCase() === "transparent" || trimmed === "rgba(0,0,0,0)") {
    return fallback;
  }
  if (/^[0-9a-f]{3}$/i.test(trimmed) || /^[0-9a-f]{6}$/i.test(trimmed)) {
    return `#${trimmed}`;
  }

  return trimmed;
}

type TeamStatRow = {
  label: string;
  home: number;
  away: number;
  format?: "percent" | "decimal";
};

function teamStatRows(home: FixtureTeamStat | null, away: FixtureTeamStat | null): TeamStatRow[] {
  return [
    { label: "점유율", home: home?.ballPossession ?? 0, away: away?.ballPossession ?? 0, format: "percent" },
    { label: "슈팅", home: home?.totalShots ?? 0, away: away?.totalShots ?? 0 },
    { label: "유효슈팅", home: home?.shotsOnTarget ?? home?.shotsOnGoal ?? 0, away: away?.shotsOnTarget ?? away?.shotsOnGoal ?? 0 },
    { label: "패스", home: home?.totalPasses ?? 0, away: away?.totalPasses ?? 0 },
    { label: "패스 성공률", home: home?.passAccuracy ?? 0, away: away?.passAccuracy ?? 0, format: "percent" },
    { label: "코너킥", home: home?.cornerKicks ?? 0, away: away?.cornerKicks ?? 0 },
    { label: "오프사이드", home: home?.offsides ?? 0, away: away?.offsides ?? 0 },
    { label: "파울", home: home?.fouls ?? 0, away: away?.fouls ?? 0 },
    { label: "세이브", home: home?.goalkeeperSaves ?? 0, away: away?.goalkeeperSaves ?? 0 },
    { label: "카드", home: (home?.yellowCards ?? 0) + (home?.redCards ?? 0), away: (away?.yellowCards ?? 0) + (away?.redCards ?? 0) },
    { label: "xG", home: home?.expectedGoals ?? 0, away: away?.expectedGoals ?? 0, format: "decimal" },
  ];
}

function buildEventTimeline(events: FixtureEvent[], fixture: FixtureSummary): EventTimelineItem[] {
  let homeScore = 0;
  let awayScore = 0;
  const goals: Array<{ teamId: number; playerId: number | null; minute: number; active: boolean }> = [];
  const timeline: EventTimelineItem[] = [];
  const hasFirstHalfEvents = events.some((event) => (event.time?.elapsed ?? 0) <= 45);
  const hasSecondHalfEvents = events.some((event) => (event.time?.elapsed ?? 0) > 45);
  let insertedHalfTime = false;

  events.forEach((event) => {
    if (!insertedHalfTime && hasFirstHalfEvents && hasSecondHalfEvents && (event.time?.elapsed ?? 0) > 45) {
      timeline.push({
        kind: "section",
        id: "half-time",
        label: `HT ${homeScore} - ${awayScore}`,
      });
      insertedHalfTime = true;
    }

    const category = eventCategory(event);
    const side = eventSide(event, fixture);
    let score: EventTimelineEventItem["score"] = null;

    if (category === "goal" || category === "own-goal") {
      const scoringTeamId = event.team?.id ?? null;
      if (scoringTeamId !== null && scoringTeamId === fixture.homeTeamId) {
        homeScore += 1;
        goals.push({
          teamId: scoringTeamId,
          playerId: event.player?.id ?? null,
          minute: eventMinuteValue(event),
          active: true,
        });
        score = { home: homeScore, away: awayScore };
      } else if (scoringTeamId !== null && scoringTeamId === fixture.awayTeamId) {
        awayScore += 1;
        goals.push({
          teamId: scoringTeamId,
          playerId: event.player?.id ?? null,
          minute: eventMinuteValue(event),
          active: true,
        });
        score = { home: homeScore, away: awayScore };
      }
    } else if (isVarGoalCancelledEvent(event) && event.team?.id) {
      const cancellationMinute = eventMinuteValue(event);
      const cancelledGoal = goals
        .slice()
        .reverse()
        .find((goal) => (
          goal.active
          && goal.teamId === event.team?.id
          && cancellationMinute - goal.minute >= 0
          && cancellationMinute - goal.minute <= 5
          && (!event.player?.id || !goal.playerId || goal.playerId === event.player.id)
        ));
      if (cancelledGoal) {
        cancelledGoal.active = false;
        if (cancelledGoal.teamId === fixture.homeTeamId) {
          homeScore = Math.max(0, homeScore - 1);
        } else if (cancelledGoal.teamId === fixture.awayTeamId) {
          awayScore = Math.max(0, awayScore - 1);
        }
      }
    }

    timeline.push({ kind: "event", event, category, side, score });
  });
  if (isFullTimeFixture(fixture)) {
    timeline.push({
      kind: "section",
      id: "full-time",
      label: `FT ${fixture.homeScore ?? homeScore} - ${fixture.awayScore ?? awayScore}`,
    });
  }

  return timeline;
}

function eventSide(event: FixtureEvent, fixture: FixtureSummary): EventSide {
  if (event.team?.id === fixture.homeTeamId) {
    return "home";
  }
  if (event.team?.id === fixture.awayTeamId) {
    return "away";
  }
  return "neutral";
}

function isFullTimeFixture(fixture: FixtureSummary) {
  return ["FINISHED", "FT", "AET", "PEN"].includes(fixture.fixtureStatus?.toUpperCase() ?? "");
}

function eventCategory(event: FixtureEvent): EventCategory {
  if (isMissedPenaltyEvent(event)) {
    return "missed-penalty";
  }
  if (isOwnGoalEvent(event)) {
    return "own-goal";
  }
  if (isGoalEvent(event)) {
    return "goal";
  }
  if (isSubstitutionEvent(event)) {
    return "substitution";
  }
  if (normalizedEventType(event) === "var") {
    return "var";
  }
  if (isRedCardEvent(event)) {
    return "red-card";
  }
  if (isYellowCardEvent(event)) {
    return "yellow-card";
  }
  return "event";
}

function eventIcon(category: EventCategory) {
  const icons: Record<EventCategory, { src: string; label: string }> = {
    goal: { src: "/events/goal.svg", label: "골" },
    "own-goal": { src: "/events/own-goal.svg", label: "자책골" },
    "missed-penalty": { src: "/events/penalty-missed.svg", label: "페널티 실축" },
    "yellow-card": { src: "/events/yellow-card.svg", label: "옐로카드" },
    "red-card": { src: "/events/red-card.svg", label: "레드카드" },
    substitution: { src: "/events/substitute.svg", label: "선수 교체" },
    var: { src: "/events/var.svg", label: "VAR" },
    event: { src: "/events/unknown-event.svg", label: "기타 이벤트" },
  };
  return icons[category];
}

function isGoalEvent(event: FixtureEvent) {
  if (normalizedEventType(event) !== "goal") {
    return false;
  }
  const detail = normalizedEventDetail(event);
  return detail === "normal goal" || detail === "penalty";
}

function isOwnGoalEvent(event: FixtureEvent) {
  return normalizedEventType(event) === "goal" && normalizedEventDetail(event) === "own goal";
}

function isMissedPenaltyEvent(event: FixtureEvent) {
  return normalizedEventType(event) === "goal" && normalizedEventDetail(event) === "missed penalty";
}

function isYellowCardEvent(event: FixtureEvent) {
  return normalizedEventType(event) === "card" && normalizedEventDetail(event) === "yellow card";
}

function isRedCardEvent(event: FixtureEvent) {
  return normalizedEventType(event) === "card" && normalizedEventDetail(event) === "red card";
}

function isSubstitutionEvent(event: FixtureEvent) {
  const text = normalizedText(event.type, event.detail);
  return text.includes("subst") || text.includes("substitution");
}

function isVarGoalCancelledEvent(event: FixtureEvent) {
  return normalizedEventType(event) === "var" && normalizedEventDetail(event) === "goal cancelled";
}

function varEventLabel(event: FixtureEvent) {
  if (normalizedEventDetail(event) === "goal cancelled" && hasEventPlayerName(event.player)) {
    return `${displayEventPlayerName(event.player)}의 골 취소`;
  }
  if (normalizedEventDetail(event) === "goal confirmed" && hasEventPlayerName(event.player)) {
    return `${displayEventPlayerName(event.player)}의 골 확정`;
  }
  const labels: Record<string, string> = {
    "goal cancelled": "골 취소",
    "goal confirmed": "골 확정",
    "penalty confirmed": "페널티 확정",
    "penalty cancelled": "페널티 취소",
    "card upgrade": "카드 판정 변경",
  };
  return labels[normalizedEventDetail(event)] ?? event.detail ?? "VAR 판정";
}

function normalizedEventType(event: FixtureEvent) {
  return event.type?.trim().toLowerCase() ?? "";
}

function normalizedEventDetail(event: FixtureEvent) {
  return event.detail?.trim().toLowerCase() ?? "";
}

function normalizedText(...values: Array<string | null>) {
  return values.filter(Boolean).join(" ").toLowerCase();
}

function eventMinuteParts(event: FixtureEvent) {
  const elapsed = event.time?.elapsed;
  const extra = event.time?.extra;
  if (elapsed === null || elapsed === undefined) {
    return { elapsed: "-", extra: null, label: "시간 정보 없음" };
  }
  return {
    elapsed: `${elapsed}'`,
    extra: extra ? `${extra}` : null,
    label: extra ? `${elapsed}분 추가 시간 ${extra}분` : `${elapsed}분`,
  };
}

function eventMinute(event: FixtureEvent) {
  const minute = eventMinuteParts(event);
  return minute.extra ? `${minute.elapsed.replace("'", "")}+${minute.extra}'` : minute.elapsed;
}

function eventMinuteValue(event: FixtureEvent) {
  return (event.time?.elapsed ?? 0) + (event.time?.extra ?? 0);
}

function isGoalkeeperPosition(position: string | null) {
  const normalized = position?.toUpperCase() ?? "";
  return normalized === "G" || normalized === "GK" || normalized.includes("GOAL");
}

function scoreText(fixture: FixtureSummary) {
  if (fixture.fixtureStatus === "SCHEDULED") {
    return "vs";
  }
  if (fixture.homeScore === null || fixture.homeScore === undefined || fixture.awayScore === null || fixture.awayScore === undefined) {
    return "-";
  }
  return `${fixture.homeScore}:${fixture.awayScore}`;
}

function detailTab(value: string | null): DetailTab | null {
  return value === "events" || value === "lineups" || value === "stats" || value === "headToHead" ? value : null;
}

function formatSeasonLabel(season: number) {
  const startYear = String(season).slice(-2).padStart(2, "0");
  const endYear = String(season + 1).slice(-2).padStart(2, "0");
  return `${startYear}/${endYear} 시즌`;
}

function fixtureInfo(value: string | null, fallback: string) {
  return value?.trim() || fallback;
}

function fixtureDateParts(value: string | null) {
  const date = parseKoreaDateTime(value);
  if (!date) {
    return { date: "날짜 미정", time: "" };
  }
  return {
    date: new Intl.DateTimeFormat("ko-KR", {
      timeZone: "Asia/Seoul",
      month: "long",
      day: "numeric",
      weekday: "short",
    }).format(date),
    time: new Intl.DateTimeFormat("ko-KR", {
      timeZone: "Asia/Seoul",
      hour: "2-digit",
      minute: "2-digit",
      hour12: false,
    }).format(date),
  };
}

function formatStat(value: number, format?: "percent" | "decimal") {
  if (format === "percent") {
    return `${value}%`;
  }
  if (format === "decimal") {
    return value.toFixed(2);
  }
  return String(value);
}

function numberText(value: number | null | undefined) {
  return value === null || value === undefined ? "-" : String(value);
}

function shortName(value: string | null) {
  if (!value) {
    return "-";
  }

  const parts = value.split(" ").filter(Boolean);
  return parts.length > 1 ? parts[parts.length - 1] : value;
}
