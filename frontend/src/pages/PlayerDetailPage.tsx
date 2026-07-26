import { useEffect, useRef, useState } from "react";
import { Activity, CalendarDays, Pencil, Shirt, Star, UserRound } from "lucide-react";
import { Link, useParams } from "react-router-dom";
import {
  ApiError,
  addFavoritePlayer,
  fetchFavoriteDashboard,
  fetchPlayerDetails,
  fetchPlayerRecentMatches,
  fetchPlayerSeasonSummary,
  removeFavoritePlayer,
  type CurrentUser,
  type PlayerMatchStat,
  type PlayerProfile,
  type PlayerSeasonSummary,
  type PlayerTeamSeasonSummary,
} from "../api";
import type { AuthStatus } from "../App";
import { formatFixtureDate, parseKoreaDateTime } from "../dateUtils";
import { displayLocalizedName } from "../teamNames";

type LoadState<T> = {
  data: T | null;
  error: string;
  isLoading: boolean;
};

const RECENT_MATCH_SIZE = 8;

const loadingState = <T,>(): LoadState<T> => ({
  data: null,
  error: "",
  isLoading: true,
});

export function PlayerDetailPage({ authStatus, currentUser, season }: { authStatus: AuthStatus; currentUser: CurrentUser | null; season: number }) {
  const { playerId } = useParams();
  const numericPlayerId = Number(playerId);
  const isValidPlayerId = Number.isFinite(numericPlayerId) && numericPlayerId > 0;
  const profileRequestId = useRef(0);
  const seasonRequestId = useRef(0);
  const matchesRequestId = useRef(0);
  const [profileState, setProfileState] = useState<LoadState<PlayerProfile>>(loadingState);
  const [seasonState, setSeasonState] = useState<LoadState<PlayerSeasonSummary>>(loadingState);
  const [matchesState, setMatchesState] = useState<LoadState<PlayerMatchStat[]>>(loadingState);
  const [isFavorite, setIsFavorite] = useState(false);
  const [isFavoriteLoading, setIsFavoriteLoading] = useState(false);
  const [favoriteError, setFavoriteError] = useState("");

  useEffect(() => {
    if (!isValidPlayerId) {
      setProfileState({ data: null, error: "올바른 선수 ID가 아닙니다.", isLoading: false });
      setSeasonState({ data: null, error: "", isLoading: false });
      setMatchesState({ data: null, error: "", isLoading: false });
      return;
    }
    loadProfile();
  }, [isValidPlayerId, numericPlayerId]);

  useEffect(() => {
    if (!isValidPlayerId) {
      return;
    }
    loadSeasonSummary();
    loadRecentMatches();
  }, [isValidPlayerId, numericPlayerId, season]);

  useEffect(() => {
    if (!isValidPlayerId) {
      setIsFavorite(false);
      return;
    }
    if (authStatus !== "authenticated") {
      setIsFavorite(false);
      setFavoriteError("");
      return;
    }

    let isCurrent = true;
    setFavoriteError("");

    fetchFavoriteDashboard(season)
      .then((dashboard) => {
        if (isCurrent) {
          setIsFavorite(dashboard.players.some((player) => player.playerId === numericPlayerId));
        }
      })
      .catch((error) => {
        if (isCurrent) {
          setIsFavorite(false);
          if (!(error instanceof ApiError && error.status === 401)) {
            setFavoriteError("즐겨찾기 상태를 확인하지 못했습니다.");
          }
        }
      });

    return () => {
      isCurrent = false;
    };
  }, [authStatus, isValidPlayerId, numericPlayerId, season]);

  function loadProfile() {
    if (!isValidPlayerId) {
      setProfileState({ data: null, error: "올바른 선수 ID가 아닙니다.", isLoading: false });
      return;
    }

    const requestId = profileRequestId.current + 1;
    profileRequestId.current = requestId;
    setProfileState(loadingState);
    fetchPlayerDetails(numericPlayerId)
      .then((profile) => {
        if (requestId === profileRequestId.current) {
          setProfileState({ data: profile, error: "", isLoading: false });
        }
      })
      .catch((error) => {
        if (requestId === profileRequestId.current) {
          setProfileState({
            data: null,
            error: error instanceof Error ? error.message : "선수 프로필을 불러오지 못했습니다.",
            isLoading: false,
          });
        }
      });
  }

  function loadSeasonSummary() {
    if (!isValidPlayerId) {
      setSeasonState({ data: null, error: "올바른 선수 ID가 아닙니다.", isLoading: false });
      return;
    }

    const requestId = seasonRequestId.current + 1;
    seasonRequestId.current = requestId;
    setSeasonState(loadingState);
    fetchPlayerSeasonSummary(numericPlayerId, season)
      .then((summary) => {
        if (requestId === seasonRequestId.current) {
          setSeasonState({ data: summary, error: "", isLoading: false });
        }
      })
      .catch((error) => {
        if (requestId === seasonRequestId.current) {
          setSeasonState({
            data: null,
            error: error instanceof Error ? error.message : "선수 시즌 스탯을 불러오지 못했습니다.",
            isLoading: false,
          });
        }
      });
  }

  function loadRecentMatches() {
    if (!isValidPlayerId) {
      setMatchesState({ data: null, error: "올바른 선수 ID가 아닙니다.", isLoading: false });
      return;
    }

    const requestId = matchesRequestId.current + 1;
    matchesRequestId.current = requestId;
    setMatchesState(loadingState);
    fetchPlayerRecentMatches(numericPlayerId, season, RECENT_MATCH_SIZE)
      .then((matches) => {
        if (requestId === matchesRequestId.current) {
          setMatchesState({ data: matches, error: "", isLoading: false });
        }
      })
      .catch((error) => {
        if (requestId === matchesRequestId.current) {
          setMatchesState({
            data: null,
            error: error instanceof Error ? error.message : "선수 최근 경기를 불러오지 못했습니다.",
            isLoading: false,
          });
        }
      });
  }

  async function toggleFavorite() {
    if (!isValidPlayerId) {
      return;
    }
    if (authStatus !== "authenticated") {
      setFavoriteError("로그인이 필요합니다.");
      return;
    }

    setIsFavoriteLoading(true);
    setFavoriteError("");
    try {
      const dashboard = isFavorite
        ? await removeFavoritePlayer(numericPlayerId, season)
        : await addFavoritePlayer(numericPlayerId, season);
      setIsFavorite(dashboard.players.some((player) => player.playerId === numericPlayerId));
    } catch (error) {
      if (error instanceof ApiError && error.status === 401) {
        setFavoriteError("로그인이 필요합니다.");
      } else {
        setFavoriteError("즐겨찾기 변경에 실패했습니다.");
      }
    } finally {
      setIsFavoriteLoading(false);
    }
  }

  if (!isValidPlayerId) {
    return (
      <section className="league-content player-detail-page">
        <article className="panel placeholder-panel">
          <p className="eyebrow">Player Detail</p>
          <h2>올바른 선수 ID가 아닙니다.</h2>
          <Link className="primary-link fixture-back-link" to="/league/overview">
            리그 홈으로
          </Link>
        </article>
      </section>
    );
  }

  if (profileState.isLoading) {
    return (
      <section className="league-content player-detail-page">
        <article className="panel placeholder-panel">선수 프로필을 불러오는 중입니다.</article>
      </section>
    );
  }

  if (profileState.error || !profileState.data) {
    return (
      <section className="league-content player-detail-page">
        <article className="panel placeholder-panel">
          <p className="eyebrow">Player Detail</p>
          <h2>선수 프로필을 불러오지 못했습니다.</h2>
          <p className="muted">{profileState.error || "잠시 후 다시 시도해 주세요."}</p>
          <button className="section-retry-button" type="button" onClick={loadProfile}>
            다시 불러오기
          </button>
          <Link className="primary-link fixture-back-link" to="/league/overview">
            리그 홈으로
          </Link>
        </article>
      </section>
    );
  }

  return (
    <section className="league-content player-detail-page">
      <PlayerHero
        canUseFavorite={authStatus === "authenticated"}
        favoriteError={favoriteError}
        isFavorite={isFavorite}
        isFavoriteLoading={isFavoriteLoading}
        onToggleFavorite={toggleFavorite}
        profile={profileState.data}
        season={season}
        seasonState={seasonState}
        isAdmin={currentUser?.role === "ADMIN"}
      />
      <SeasonSummaryPanel
        isGoalkeeper={isGoalkeeperPosition(profileState.data.position)}
        onRetry={loadSeasonSummary}
        season={season}
        state={seasonState}
      />
      <RecentMatchesPanel onRetry={loadRecentMatches} state={matchesState} />
    </section>
  );
}

function PlayerHero({
  profile,
  season,
  seasonState,
  canUseFavorite,
  isFavorite,
  isFavoriteLoading,
  favoriteError,
  onToggleFavorite,
  isAdmin,
}: {
  profile: PlayerProfile;
  season: number;
  seasonState: LoadState<PlayerSeasonSummary>;
  canUseFavorite: boolean;
  isFavorite: boolean;
  isFavoriteLoading: boolean;
  favoriteError: string;
  onToggleFavorite: () => void;
  isAdmin: boolean;
}) {
  return (
    <article className="panel player-hero">
      <div className="player-photo-wrap">
        {profile.photoUrl ? (
          <img src={profile.photoUrl} alt="" className="player-photo" />
        ) : (
          <span className="player-photo placeholder" aria-hidden="true">
            <UserRound size={44} />
          </span>
        )}
      </div>
      <div className="player-hero-main">
        <p className="eyebrow">{profile.position ?? "Player"}</p>
        <div className="detail-title-row">
          <h2>{displayLocalizedName(profile.playerNameKo, profile.playerName)}</h2>
          {canUseFavorite ? (
            <FavoriteToggleButton
              isActive={isFavorite}
              isLoading={isFavoriteLoading}
              onClick={onToggleFavorite}
              typeLabel="선수"
            />
          ) : null}
          {isAdmin ? (
            <Link aria-label="관리자 수정" className="admin-edit-link icon" title="관리자 수정" to={`/admin/editor?tab=player&id=${profile.playerId}`}>
              <Pencil size={16} aria-hidden="true" />
            </Link>
          ) : null}
        </div>
        {favoriteError ? <p className="favorite-inline-error">{favoriteError}</p> : null}
        <div className="player-meta-list">
          <span>{profile.nationality ?? "국적 정보 없음"}</span>
          <span>{profile.age ? `${profile.age}세` : "나이 정보 없음"}</span>
          <span>{profile.height ? `${profile.height}cm` : "신장 정보 없음"}</span>
          <span>{profile.weight ? `${profile.weight}kg` : "체중 정보 없음"}</span>
        </div>
      </div>
      <SeasonTeamSummary season={season} state={seasonState} />
    </article>
  );
}

function SeasonTeamSummary({ season, state }: { season: number; state: LoadState<PlayerSeasonSummary> }) {
  if (state.isLoading) {
    return (
      <div className="player-team-card">
        <strong className="player-team-card-title">{season} 시즌 소속팀</strong>
        <div>
          <strong>소속팀 확인 중</strong>
          <p>선택 시즌 기준으로 불러오고 있습니다.</p>
        </div>
      </div>
    );
  }

  if (state.error) {
    return (
      <div className="player-team-card">
        <strong className="player-team-card-title">{season} 시즌 소속팀</strong>
        <div>
          <strong>소속팀 정보를 불러오지 못했습니다.</strong>
          <p>{state.error}</p>
        </div>
      </div>
    );
  }

  const teams = state.data?.teams ?? [];
  if (!teams.length) {
    return (
      <div className="player-team-card">
        <strong className="player-team-card-title">{season} 시즌 소속팀</strong>
        <div>
          <strong>해당 시즌 소속팀 정보 없음</strong>
          <p>선택 시즌의 EPL 기록이 없습니다.</p>
        </div>
      </div>
    );
  }

  return (
    <div className="player-team-card">
      <strong className="player-team-card-title">{season} 시즌 소속팀</strong>
      <div className="player-season-team-list">
        {teams.map((team) => (
          <Link className="player-season-team-row" to={`/teams/${team.teamId}`} key={team.teamId}>
            {team.teamLogoUrl ? (
              <img src={team.teamLogoUrl} alt="" className="team-logo large" />
            ) : (
              <span className="team-logo large placeholder" aria-hidden="true" />
            )}
            <span>{displayLocalizedName(team.teamNameKo, team.teamName)}</span>
          </Link>
        ))}
      </div>
    </div>
  );
}

function FavoriteToggleButton({
  isActive,
  isLoading,
  onClick,
  typeLabel,
}: {
  isActive: boolean;
  isLoading: boolean;
  onClick: () => void;
  typeLabel: string;
}) {
  return (
    <button
      aria-label={`${typeLabel} 즐겨찾기 ${isActive ? "해제" : "등록"}`}
      className={`favorite-toggle-button${isActive ? " active" : ""}`}
      disabled={isLoading}
      onClick={onClick}
      title={`${typeLabel} 즐겨찾기 ${isActive ? "해제" : "등록"}`}
      type="button"
    >
      <Star size={17} aria-hidden="true" fill={isActive ? "currentColor" : "none"} />
    </button>
  );
}

function SeasonSummaryPanel({
  isGoalkeeper,
  season,
  state,
  onRetry,
}: {
  isGoalkeeper: boolean;
  season: number;
  state: LoadState<PlayerSeasonSummary>;
  onRetry: () => void;
}) {
  if (state.isLoading) {
    return (
      <article className="panel detail-panel">
        <div className="empty-state">시즌 스탯을 불러오는 중입니다.</div>
      </article>
    );
  }

  if (state.error) {
    return <SectionError title="시즌 스탯을 불러오지 못했습니다." message={state.error} onRetry={onRetry} />;
  }

  const summary = state.data;
  if (!summary || (!summary.totalFixtures && !(summary.teams ?? []).length)) {
    return (
      <article className="panel detail-panel">
        <div className="detail-panel-heading player-panel-title">
          <Shirt size={19} aria-hidden="true" />
          <h2>{season} 시즌 스탯</h2>
        </div>
        <div className="empty-state">선택 시즌의 EPL 기록이 없습니다.</div>
      </article>
    );
  }

  return (
    <article className="panel detail-panel">
      <div className="detail-panel-heading player-panel-title">
        <Shirt size={19} aria-hidden="true" />
        <h2>{summary.season ?? season} 시즌 스탯</h2>
      </div>
      <div className="player-stat-chips">
        <StatChip label="경기" value={summary.totalFixtures} />
        <StatChip label="출전 시간" value={summary.minutesPlayed} />
        {isGoalkeeper ? (
          <>
            <StatChip label="클린시트" value={summary.cleanSheets} />
            <StatChip label="실점" value={summary.conceded} />
            <StatChip label="선방" value={summary.saves} />
          </>
        ) : (
          <>
            <StatChip label="골" value={summary.goals} />
            <StatChip label="도움" value={summary.assists} />
          </>
        )}
        <StatChip label="평점" value={ratingText(summary.averageRating)} />
        {!isGoalkeeper ? (
          <>
            <StatChip label="슈팅" value={summary.shots} />
            <StatChip label="유효 슈팅" value={summary.shotsOnTarget} />
            <StatChip label="키패스" value={summary.keyPasses} />
          </>
        ) : null}
        <StatChip label="경고" value={summary.yellowCards} />
        <StatChip label="퇴장" value={summary.redCards} />
      </div>
      <SeasonTeamStats isGoalkeeper={isGoalkeeper} teams={summary.teams ?? []} />
    </article>
  );
}

function StatChip({ label, value }: { label: string; value: number | string }) {
  return (
    <div>
      <span>{label}</span>
      <strong>{typeof value === "number" ? numberText(value) : value}</strong>
    </div>
  );
}

function SeasonTeamStats({
  isGoalkeeper,
  teams,
}: {
  isGoalkeeper: boolean;
  teams: PlayerTeamSeasonSummary[];
}) {
  if (!teams.length) {
    return null;
  }

  return (
    <div className="player-season-team-groups">
      {teams.map((team) => (
        <section className="player-season-team-group" key={team.teamId}>
          <div className="player-team-row season-team-heading">
            {team.teamLogoUrl ? (
              <img src={team.teamLogoUrl} alt="" className="team-logo" />
            ) : (
              <span className="team-logo placeholder" aria-hidden="true" />
            )}
            <Link className="team-name-link" to={`/teams/${team.teamId}`}>
              {displayLocalizedName(team.teamNameKo, team.teamName)}
            </Link>
            <span>
              {isGoalkeeper
                ? `${numberText(team.totalFixtures)}경기 · ${numberText(team.cleanSheets)} 클린시트 · ${numberText(team.conceded)}실점`
                : `${numberText(team.totalFixtures)}경기 · ${numberText(team.goals)}G ${numberText(team.assists)}A`}
            </span>
          </div>
          <div className="player-stat-chips">
            <StatChip label="출전 시간" value={team.minutesPlayed} />
            <StatChip label="평점" value={ratingText(team.averageRating)} />
            {isGoalkeeper ? (
              <>
                <StatChip label="클린시트" value={team.cleanSheets} />
                <StatChip label="실점" value={team.conceded} />
                <StatChip label="선방" value={team.saves} />
              </>
            ) : (
              <>
                <StatChip label="슈팅" value={team.shots} />
                <StatChip label="유효 슈팅" value={team.shotsOnTarget} />
                <StatChip label="키패스" value={team.keyPasses} />
              </>
            )}
            <StatChip label="경고" value={team.yellowCards} />
            <StatChip label="퇴장" value={team.redCards} />
          </div>
        </section>
      ))}
    </div>
  );
}

function isGoalkeeperPosition(position: string | null) {
  const normalized = position?.trim().toUpperCase() ?? "";
  return normalized === "GK" || normalized === "G" || normalized.includes("GOALKEEPER");
}

function RecentMatchesPanel({ state, onRetry }: { state: LoadState<PlayerMatchStat[]>; onRetry: () => void }) {
  if (state.isLoading) {
    return (
      <article className="panel detail-panel">
        <div className="empty-state">최근 경기를 불러오는 중입니다.</div>
      </article>
    );
  }

  if (state.error) {
    return <SectionError title="최근 경기를 불러오지 못했습니다." message={state.error} onRetry={onRetry} />;
  }

  const matches = state.data ?? [];
  if (!matches.length) {
    return (
      <article className="panel detail-panel">
        <div className="empty-state">선택 시즌의 경기 기록이 없습니다.</div>
      </article>
    );
  }

  return (
    <article className="panel detail-panel">
      <div className="detail-panel-heading player-panel-title">
        <CalendarDays size={19} aria-hidden="true" />
        <h2>최근 경기</h2>
      </div>
      <div className="player-match-list">
        {matches.map((match) => (
          <PlayerMatchRow match={match} key={match.fixtureId} />
        ))}
      </div>
    </article>
  );
}

function SectionError({ title, message, onRetry }: { title: string; message: string; onRetry: () => void }) {
  return (
    <article className="panel detail-panel">
      <div className="section-error section-retry-error">
        <strong>{title}</strong>
        <p>{message}</p>
        <button className="section-retry-button" type="button" onClick={onRetry}>
          다시 불러오기
        </button>
      </div>
    </article>
  );
}

function PlayerMatchRow({ match }: { match: PlayerMatchStat }) {
  const homeTeamName = displayLocalizedName(match.homeTeamNameKo, match.homeTeamName ?? inferHomeTeam(match));
  const awayTeamName = displayLocalizedName(match.awayTeamNameKo, match.awayTeamName ?? inferAwayTeam(match));
  const homeScore = match.homeScore ?? inferHomeScore(match);
  const awayScore = match.awayScore ?? inferAwayScore(match);

  return (
    <Link className="player-match-row" to={`/fixtures/${match.fixtureId}`}>
      <time>{formatDate(match.fixtureDate)}</time>
      <div className="player-match-teams" aria-label="홈팀과 원정팀">
        <strong>
          <span>홈</span>
          {homeTeamName}
        </strong>
        <span>
          {numberText(homeScore)}:{numberText(awayScore)}
        </span>
        <strong>
          <span>원정</span>
          {awayTeamName}
        </strong>
      </div>
      <div className="player-match-stats">
        <span>{numberText(match.minutesPlayed)}분</span>
        <span>{numberText(match.goals)}G</span>
        <span>{numberText(match.assists)}A</span>
        <span>
          <Activity size={14} aria-hidden="true" />
          {ratingText(match.rating)}
        </span>
      </div>
    </Link>
  );
}

function inferHomeTeam(match: PlayerMatchStat) {
  return match.teamName;
}

function inferAwayTeam(match: PlayerMatchStat) {
  return match.opponentTeamName;
}

function inferHomeScore(match: PlayerMatchStat) {
  return match.teamScore;
}

function inferAwayScore(match: PlayerMatchStat) {
  return match.opponentScore;
}

function formatDate(value: string | null) {
  return formatFixtureDate(value, value?.slice(0, 10) || "-");
}

function formatDateOnly(value: string) {
  const date = new Date(`${value}T00:00:00+09:00`);
  if (Number.isNaN(date.getTime())) {
    return value;
  }

  return new Intl.DateTimeFormat("ko-KR", {
    timeZone: "Asia/Seoul",
    year: "numeric",
    month: "long",
    day: "numeric",
  }).format(date);
}

function ratingText(value: number | null | undefined) {
  if (value === null || value === undefined || Number.isNaN(value)) {
    return "-";
  }
  return value.toFixed(1);
}

function numberText(value: number | null | undefined) {
  if (value === null || value === undefined || Number.isNaN(value)) {
    return "-";
  }
  return String(value);
}

function matchTime(match: PlayerMatchStat) {
  const date = parseKoreaDateTime(match.fixtureDate);
  return date?.getTime() ?? Number.MAX_SAFE_INTEGER;
}

export function sortMatchesByDateAsc(matches: PlayerMatchStat[]) {
  return matches.slice().sort((left, right) => matchTime(left) - matchTime(right) || left.fixtureId - right.fixtureId);
}
