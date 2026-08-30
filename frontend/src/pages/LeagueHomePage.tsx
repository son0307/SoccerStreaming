import { useEffect, useMemo, useRef, useState } from "react";
import { CalendarDays, ChevronLeft, ChevronRight, Star, Trophy } from "lucide-react";
import { Link, useSearchParams } from "react-router-dom";
import type { AuthStatus } from "../App";
import {
  ApiError,
  fetchFavoriteDashboard,
  fetchFixturesByDate,
  fetchStandings,
  type FavoriteDashboard,
  type FavoritePlayerCard,
  type FavoriteTeamCard,
  type FixtureSummary,
  type TeamStanding,
} from "../api";
import { formatFixtureDateKey, parseKoreaDateTime } from "../dateUtils";
import { displayLocalizedName } from "../teamNames";
import { fixtureStatusLabel } from "../fixtureStatus";

export function LeagueHomePage({ authStatus, season }: { authStatus: AuthStatus; season: number }) {
  const [searchParams, setSearchParams] = useSearchParams();
  const selectedDate = validDateKey(searchParams.get("date")) ?? todayKoreaDateKey();
  const [standings, setStandings] = useState<TeamStanding[]>([]);
  const [fixtures, setFixtures] = useState<FixtureSummary[]>([]);
  const [favorites, setFavorites] = useState<FavoriteDashboard | null>(null);
  const [favoritesNeedLogin, setFavoritesNeedLogin] = useState(false);
  const [isLoadingStandings, setIsLoadingStandings] = useState(true);
  const [isLoadingFixtures, setIsLoadingFixtures] = useState(true);
  const [isLoadingFavorites, setIsLoadingFavorites] = useState(true);
  const [standingsError, setStandingsError] = useState("");
  const [fixturesError, setFixturesError] = useState("");
  const [favoritesError, setFavoritesError] = useState("");
  const standingsRequestIdRef = useRef(0);
  const fixturesRequestIdRef = useRef(0);
  const favoritesRequestIdRef = useRef(0);

  const rankingRows = useMemo(
    () =>
      standings
        .slice()
        .sort((a, b) => valueOf(a.rank) - valueOf(b.rank))
        .slice(0, 20),
    [standings],
  );

  useEffect(() => {
    void loadStandings();
  }, [season]);

  useEffect(() => {
    if (searchParams.get("date") !== selectedDate) {
      setSearchParams((current) => {
        const next = new URLSearchParams(current);
        next.set("date", selectedDate);
        return next;
      }, { replace: true });
    }
  }, [searchParams, selectedDate, setSearchParams]);

  useEffect(() => {
    if (authStatus === "checking") {
      favoritesRequestIdRef.current += 1;
      setIsLoadingFavorites(true);
      setFavoritesError("");
      return;
    }

    if (authStatus === "guest") {
      favoritesRequestIdRef.current += 1;
      setFavorites(null);
      setFavoritesNeedLogin(true);
      setFavoritesError("");
      setIsLoadingFavorites(false);
      return;
    }

    void loadFavorites();
  }, [authStatus, season]);

  useEffect(() => {
    void loadFixtures(selectedDate);
  }, [selectedDate, season]);

  async function loadStandings() {
    const requestId = standingsRequestIdRef.current + 1;
    standingsRequestIdRef.current = requestId;
    setIsLoadingStandings(true);
    setStandingsError("");
    try {
      const nextStandings = await fetchStandings(season);
      if (requestId === standingsRequestIdRef.current) {
        setStandings(nextStandings);
      }
    } catch (error) {
      if (requestId !== standingsRequestIdRef.current) {
        return;
      }
      setStandings([]);
      setStandingsError(error instanceof Error ? error.message : "팀 랭킹을 불러오지 못했습니다.");
    } finally {
      if (requestId === standingsRequestIdRef.current) {
        setIsLoadingStandings(false);
      }
    }
  }

  async function loadFixtures(dateKey: string) {
    const requestId = fixturesRequestIdRef.current + 1;
    fixturesRequestIdRef.current = requestId;
    setIsLoadingFixtures(true);
    setFixturesError("");
    try {
      const response = await fetchFixturesByDate(season, dateKey);
      if (requestId === fixturesRequestIdRef.current) {
        setFixtures(response.content ?? []);
      }
    } catch (error) {
      if (requestId !== fixturesRequestIdRef.current) {
        return;
      }
      setFixtures([]);
      setFixturesError(error instanceof Error ? error.message : "경기 일정을 불러오지 못했습니다.");
    } finally {
      if (requestId === fixturesRequestIdRef.current) {
        setIsLoadingFixtures(false);
      }
    }
  }

  async function loadFavorites() {
    const requestId = favoritesRequestIdRef.current + 1;
    favoritesRequestIdRef.current = requestId;
    setIsLoadingFavorites(true);
    setFavoritesError("");
    setFavoritesNeedLogin(false);
    try {
      const nextFavorites = await fetchFavoriteDashboard(season);
      if (requestId === favoritesRequestIdRef.current) {
        setFavorites(nextFavorites);
      }
    } catch (error) {
      if (requestId !== favoritesRequestIdRef.current) {
        return;
      }
      setFavorites(null);
      if (error instanceof ApiError && error.status === 401) {
        setFavoritesNeedLogin(true);
      } else {
        setFavoritesError(error instanceof Error ? error.message : "즐겨찾기를 불러오지 못했습니다.");
      }
    } finally {
      if (requestId === favoritesRequestIdRef.current) {
        setIsLoadingFavorites(false);
      }
    }
  }

  function moveDate(dayDelta: number) {
    updateSelectedDate(addDaysToDateKey(selectedDate, dayDelta));
  }

  function updateSelectedDate(nextDate: string) {
    if (!validDateKey(nextDate)) {
      return;
    }
    setSearchParams((current) => {
      const next = new URLSearchParams(current);
      next.set("date", nextDate);
      return next;
    });
  }

  return (
    <section className="league-home">
      <div className="league-home-main">
        <article className="panel schedule-panel">
          <div className="panel-heading schedule-heading">
            <div>
              <div className="panel-title-with-icon">
                <CalendarDays size={20} aria-hidden="true" />
                <h2>경기 일정</h2>
              </div>
              <label className="home-date-picker">
                <span>{dateGroupTitle(selectedDate)}</span>
                <CalendarDays size={17} aria-hidden="true" />
                <input
                  aria-label="경기 날짜 선택"
                  type="date"
                  value={selectedDate}
                  onChange={(event) => updateSelectedDate(event.currentTarget.value)}
                />
              </label>
            </div>
            <div className="date-controls" aria-label="경기 날짜 이동">
              <button type="button" onClick={() => moveDate(-1)} aria-label="이전 날짜" title="이전 날짜">
                <ChevronLeft size={18} aria-hidden="true" />
              </button>
              <button type="button" onClick={() => updateSelectedDate(todayKoreaDateKey())}>
                오늘
              </button>
              <button type="button" onClick={() => moveDate(1)} aria-label="다음 날짜" title="다음 날짜">
                <ChevronRight size={18} aria-hidden="true" />
              </button>
            </div>
          </div>
          {fixturesError ? <div className="section-error">{fixturesError}</div> : null}
          {isLoadingFixtures ? (
            <div className="empty-state">경기 일정을 불러오는 중입니다.</div>
          ) : (
            <FixtureSchedule fixtures={fixtures} />
          )}
        </article>

        <article className="panel favorites-panel">
          <div className="panel-heading">
            <div className="panel-title-with-icon">
              <Star size={20} aria-hidden="true" />
              <h2>즐겨찾기</h2>
            </div>
          </div>
          {favoritesError ? (
            <div className="section-error inline-retry">
              <span>{favoritesError}</span>
              <button type="button" onClick={() => void loadFavorites()}>
                다시 불러오기
              </button>
            </div>
          ) : null}
          {isLoadingFavorites ? (
            <div className="empty-state">즐겨찾기를 확인하는 중입니다.</div>
          ) : favoritesNeedLogin ? (
            <FavoriteLoginStateWithLink />
          ) : (
            <FavoritesPreview favorites={favorites} />
          )}
        </article>
      </div>

      <aside className="league-home-side">
        <article className="panel ranking-panel">
          <div className="panel-heading">
            <div className="panel-title-with-icon">
              <Trophy size={20} aria-hidden="true" />
              <h2>팀 랭킹</h2>
            </div>
          </div>
          {standingsError ? <div className="section-error">{standingsError}</div> : null}
          {isLoadingStandings ? (
            <div className="empty-state">팀 랭킹을 불러오는 중입니다.</div>
          ) : (
            <CompactRanking standings={rankingRows} />
          )}
        </article>
      </aside>
    </section>
  );
}

function CompactRanking({ standings }: { standings: TeamStanding[] }) {
  if (!standings.length) {
    return <div className="empty-state">랭킹 데이터가 없습니다.</div>;
  }

  return (
    <div className="compact-ranking">
      {standings.map((standing) => (
        <div className="compact-ranking-row" key={standing.team?.id ?? standing.rank}>
          <span className="compact-rank">{standing.rank ?? "-"}</span>
          {standing.team?.logo ? (
            <img src={standing.team.logo} alt="" className="team-logo" />
          ) : (
            <span className="team-logo placeholder" aria-hidden="true" />
          )}
          {standing.team?.id ? (
            <Link className="team-name-link" to={`/teams/${standing.team.id}`}>
              {displayLocalizedName(standing.team.nameKo, standing.team.name)}
            </Link>
          ) : (
            <strong>{displayLocalizedName(standing.team?.nameKo, standing.team?.name)}</strong>
          )}
          <b>{standing.points ?? 0}</b>
        </div>
      ))}
    </div>
  );
}

function FixtureSchedule({ fixtures }: { fixtures: FixtureSummary[] }) {
  if (!fixtures.length) {
    return <div className="empty-state">선택한 날짜에 등록된 경기가 없습니다.</div>;
  }

  const orderedFixtures = fixtures.slice().sort((left, right) =>
    fixtureTime(left.fixtureDate) - fixtureTime(right.fixtureDate)
      || left.fixtureId - right.fixtureId,
  );

  return (
    <div className="home-fixture-list">
      {orderedFixtures.map((fixture) => (
        <Link className="home-fixture-card" key={fixture.fixtureId} to={`/fixtures/${fixture.fixtureId}`}>
          <time>{formatTime(fixture.fixtureDate)}</time>
          <div className="home-fixture-teams">
            <strong>{displayLocalizedName(fixture.homeTeamNameKo, fixture.homeTeamName)}</strong>
            {fixture.homeTeamLogoUrl ? (
              <img src={fixture.homeTeamLogoUrl} alt="" className="team-logo" />
            ) : (
              <span className="team-logo placeholder" aria-hidden="true" />
            )}
            <span>{scoreText(fixture)}</span>
            {fixture.awayTeamLogoUrl ? (
              <img src={fixture.awayTeamLogoUrl} alt="" className="team-logo" />
            ) : (
              <span className="team-logo placeholder" aria-hidden="true" />
            )}
            <strong>{displayLocalizedName(fixture.awayTeamNameKo, fixture.awayTeamName)}</strong>
          </div>
          <span className="status-pill">{fixtureStatusLabel(fixture)}</span>
        </Link>
      ))}
    </div>
  );
}

function FavoriteLoginState() {
  return (
    <div className="favorite-empty">
      <strong>로그인 후 즐겨찾기를 확인할 수 있습니다.</strong>
      <p>좋아하는 팀과 선수를 저장하면 최근 경기, 다음 경기, 시즌 기록이 이곳에 표시됩니다.</p>
    </div>
  );
}

function FavoriteLoginStateWithLink() {
  return (
    <div className="favorite-empty">
      <strong>로그인 후 즐겨찾기를 확인할 수 있습니다.</strong>
      <p>좋아하는 팀과 선수를 저장하면 최근 경기, 다음 경기, 시즌 기록을 한곳에서 확인할 수 있습니다.</p>
      <Link className="favorite-login-link" to="/login">
        로그인
      </Link>
    </div>
  );
}

function FavoritesPreview({ favorites }: { favorites: FavoriteDashboard | null }) {
  const teams = favorites?.teams ?? [];
  const players = favorites?.players ?? [];

  if (!teams.length && !players.length) {
    return (
      <div className="favorite-empty">
        <strong>아직 즐겨찾기한 팀이나 선수가 없습니다.</strong>
        <p>팀 상세 정보 페이지나 선수 상세 정보 페이지에서 즐겨찾기 버튼을 통해 즐겨찾기를 추가할 수 있습니다.</p>
      </div>
    );
  }

  return (
    <div className="favorite-preview-grid">
      <section>
        <h3>팀</h3>
        <div className="favorite-mini-list">
          {teams.map((team) => (
            <FavoriteTeamItem team={team} key={team.teamId} />
          ))}
        </div>
      </section>
      <section>
        <h3>선수</h3>
        <div className="favorite-mini-list">
          {players.map((player) => (
            <FavoritePlayerItemV2 player={player} key={player.playerId} />
          ))}
        </div>
      </section>
    </div>
  );
}

function FavoriteTeamItem({ team }: { team: FavoriteTeamCard }) {
  return (
    <article className="favorite-mini-card">
      <div className="favorite-mini-head">
        {team.logoUrl ? (
          <img src={team.logoUrl} alt="" className="team-logo" />
        ) : (
          <span className="team-logo placeholder" aria-hidden="true" />
        )}
        <div>
          <Link className="team-name-link" to={`/teams/${team.teamId}`}>
            {displayLocalizedName(team.teamNameKo, team.teamName)}
          </Link>
          <p>{numberText(team.rank)}위 · {numberText(team.points)}점 · 최근 {team.form ?? "-"}</p>
        </div>
      </div>
      {team.liveFixture ? (
        <p className="favorite-line live">LIVE {team.liveFixture.elapsed ?? "-"}' · {displayLocalizedName(team.liveFixture.homeTeamNameKo, team.liveFixture.homeTeamName)} {team.liveFixture.homeScore}:{team.liveFixture.awayScore} {displayLocalizedName(team.liveFixture.awayTeamNameKo, team.liveFixture.awayTeamName)}</p>
      ) : team.nextFixture ? (
        <p className="favorite-line">다음 경기 · {displayLocalizedName(team.nextFixture.homeTeamNameKo, team.nextFixture.homeTeamName)} vs {displayLocalizedName(team.nextFixture.awayTeamNameKo, team.nextFixture.awayTeamName)}</p>
      ) : (
        <p className="favorite-line muted">예정된 경기 정보가 없습니다.</p>
      )}
    </article>
  );
}

function FavoritePlayerItem({ player }: { player: FavoritePlayerCard }) {
  return (
    <article className="favorite-mini-card">
      <div className="favorite-mini-head">
        {player.photoUrl ? (
          <img src={player.photoUrl} alt="" className="player-thumb" />
        ) : (
          <span className="player-thumb placeholder" aria-hidden="true" />
        )}
        <div>
          <Link className="favorite-player-link" to={`/players/${player.playerId}`}>
            {displayLocalizedName(player.playerNameKo, player.playerName)}
          </Link>
          <p>{player.position ?? "Player"} · {displayLocalizedName(player.seasonStat?.teamNameKo, player.seasonStat?.teamName)}</p>
        </div>
      </div>
      <p className="favorite-line">
        시즌 {numberText(player.seasonStat?.goals)}골 {numberText(player.seasonStat?.assists)}도움 · 평점 {numberText(player.seasonStat?.rating)}
      </p>
    </article>
  );
}

function FavoritePlayerItemV2({ player }: { player: FavoritePlayerCard }) {
  const seasonStat = player.seasonStat;

  return (
    <article className="favorite-mini-card">
      <div className="favorite-mini-head">
        {player.photoUrl ? (
          <img src={player.photoUrl} alt="" className="player-thumb" />
        ) : (
          <span className="player-thumb placeholder" aria-hidden="true" />
        )}
        <div>
          <Link className="favorite-player-link" to={`/players/${player.playerId}`}>
            {displayLocalizedName(player.playerNameKo, player.playerName)}
          </Link>
          <p>{player.position ?? "Player"} · {displayLocalizedName(seasonStat?.teamNameKo, seasonStat?.teamName)}</p>
        </div>
      </div>
      {seasonStat ? (
        <>
          <p className="favorite-line">
            시즌 {numberText(seasonStat.goals)}골 {numberText(seasonStat.assists)}도움 · 평점 {numberText(seasonStat.rating)}
          </p>
        </>
      ) : (
        <p className="favorite-line muted">해당 선수는 이번 시즌에 EPL 기록이 없습니다.</p>
      )}
    </article>
  );
}

function todayKoreaDateKey() {
  return new Intl.DateTimeFormat("en-CA", {
    timeZone: "Asia/Seoul",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).format(new Date());
}

function validDateKey(value: string | null) {
  if (!value || !/^\d{4}-\d{2}-\d{2}$/.test(value)) {
    return null;
  }
  const [year, month, day] = value.split("-").map(Number);
  const date = new Date(Date.UTC(year, month - 1, day));
  return date.getUTCFullYear() === year
    && date.getUTCMonth() === month - 1
    && date.getUTCDate() === day
    ? value
    : null;
}

function addDaysToDateKey(dateKey: string, dayDelta: number) {
  const date = new Date(`${dateKey}T00:00:00+09:00`);
  date.setUTCDate(date.getUTCDate() + dayDelta);

  return new Intl.DateTimeFormat("en-CA", {
    timeZone: "Asia/Seoul",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).format(date);
}

function dateGroupTitle(dateKey: string) {
  const date = new Date(`${dateKey}T00:00:00+09:00`);
  const today = new Date(`${todayKoreaDateKey()}T00:00:00+09:00`);
  const diffDays = Math.round((date.getTime() - today.getTime()) / 86400000);
  const formatted = new Intl.DateTimeFormat("ko-KR", {
    timeZone: "Asia/Seoul",
    month: "long",
    day: "numeric",
    weekday: "short",
  }).format(date);
  const adaptiveFormatted = formatFixtureDateKey(dateKey, formatted);

  if (diffDays === 0) {
    return `오늘, ${adaptiveFormatted}`;
  }
  if (diffDays === 1) {
    return `내일, ${adaptiveFormatted}`;
  }
  if (diffDays === -1) {
    return `어제, ${adaptiveFormatted}`;
  }
  return adaptiveFormatted;
}

function formatTime(value: string | null) {
  if (!value) {
    return "-";
  }

  const date = parseKoreaDateTime(value);
  if (!date || Number.isNaN(date.getTime())) {
    return value.slice(11, 16) || "-";
  }

  return new Intl.DateTimeFormat("ko-KR", {
    timeZone: "Asia/Seoul",
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
  }).format(date);
}

function fixtureTime(value: string | null) {
  return parseKoreaDateTime(value)?.getTime() ?? Number.MAX_SAFE_INTEGER;
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

function valueOf(value: number | null | undefined) {
  return value ?? 0;
}

function numberText(value: number | null | undefined) {
  return value === null || value === undefined ? "-" : String(value);
}
