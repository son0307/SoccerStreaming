import { useEffect, useMemo, useRef, useState } from "react";
import { ChevronDown, ChevronLeft, ChevronRight, RefreshCw } from "lucide-react";
import { Link, useSearchParams } from "react-router-dom";
import {
  fetchFixtureMeta,
  fetchFixtures,
  fetchStandings,
  fetchSyncStatuses,
  requestAdminSync,
  type CurrentUser,
  type FixtureMeta,
  type FixtureSummary,
  type SyncStatus,
  type TeamStanding,
} from "../api";
import { formatFixtureDateKey, parseKoreaDateTime } from "../dateUtils";
import { displayLocalizedName } from "../teamNames";
import { fixtureStatusLabel } from "../fixtureStatus";
import { SyncToast } from "../components/SyncToast";
import { useManualSyncCooldown } from "../useManualSyncCooldown";

const TEAM_PAGE_SIZE = 10;
const TEAM_FETCH_SIZE = 100;

type FixtureMode = "date" | "round" | "team";

type FixtureTeamOption = {
  teamId: number;
  teamName: string;
  logoUrl: string | null;
};

const fixtureModes: Array<{ label: string; value: FixtureMode }> = [
  { label: "날짜별", value: "date" },
  { label: "라운드별", value: "round" },
  { label: "팀별", value: "team" },
];

export function LeagueFixturesPage({ currentUser, season }: { currentUser: CurrentUser | null; season: number }) {
  const [searchParams, setSearchParams] = useSearchParams();
  const mode = fixtureMode(searchParams.get("mode")) ?? "date";
  const urlSeason = positiveInteger(searchParams.get("season"));
  const isSeasonUrlCurrent = urlSeason === season;
  const [meta, setMeta] = useState<FixtureMeta | null>(null);
  const [metaSeason, setMetaSeason] = useState<number | null>(null);
  const activeMeta = metaSeason === season ? meta : null;
  const requestedWeek = isSeasonUrlCurrent ? validDateKey(searchParams.get("week")) : null;
  const weekStart = activeMeta
    ? clampWeekStart(requestedWeek ?? defaultWeekStart(activeMeta), activeMeta)
    : requestedWeek ?? startOfKoreaWeek(todayKoreaDateKey());
  const isDateWeekReady = isSeasonUrlCurrent && (requestedWeek !== null || activeMeta !== null);
  const round = positiveInteger(searchParams.get("round")) ?? 1;
  const [isRoundMenuOpen, setIsRoundMenuOpen] = useState(false);
  const [teams, setTeams] = useState<FixtureTeamOption[]>([]);
  const selectedTeamId = positiveInteger(searchParams.get("teamId"));
  const [fixtures, setFixtures] = useState<FixtureSummary[]>([]);
  const [syncStatus, setSyncStatus] = useState<SyncStatus | null>(null);
  const [syncToast, setSyncToast] = useState<{ message: string; type: "success" | "error" } | null>(null);
  const { cooldownUntil: syncCooldownUntil, cooldownSeconds, startCooldown } = useManualSyncCooldown("fixtures", season);
  const [isSyncing, setIsSyncing] = useState(false);
  const teamPage = Math.max(0, (positiveInteger(searchParams.get("page")) ?? 1) - 1);
  const [isLoading, setIsLoading] = useState(true);
  const [isLoadingTeams, setIsLoadingTeams] = useState(true);
  const [errorMessage, setErrorMessage] = useState("");
  const [teamErrorMessage, setTeamErrorMessage] = useState("");
  const fixtureRequestId = useRef(0);
  const metaRequestId = useRef(0);
  const teamRequestId = useRef(0);

  const minRound = 1;
  const maxRound = activeMeta?.maxRound ?? 38;
  const weekEnd = addDaysToDateKey(weekStart, 6);
  const canMovePreviousWeek = !activeMeta?.minDate || weekStart > startOfKoreaWeek(activeMeta.minDate);
  const canMoveNextWeek = !activeMeta?.maxDate || weekStart < startOfKoreaWeek(activeMeta.maxDate);
  const selectedTeam = teams.find((team) => team.teamId === selectedTeamId) ?? null;
  const orderedFixtures = useMemo(
    () => (mode === "team" ? sortTeamFixtures(fixtures) : fixtures),
    [fixtures, mode],
  );
  const visibleFixtures =
    mode === "team"
      ? orderedFixtures.slice(teamPage * TEAM_PAGE_SIZE, (teamPage + 1) * TEAM_PAGE_SIZE)
      : orderedFixtures;
  const groupedFixtures = useMemo(() => groupFixturesByDate(visibleFixtures), [visibleFixtures]);
  const totalTeamPages = Math.max(1, Math.ceil(fixtures.length / TEAM_PAGE_SIZE));
  const teamRangeStart = fixtures.length ? teamPage * TEAM_PAGE_SIZE + 1 : 0;
  const teamRangeEnd = Math.min((teamPage + 1) * TEAM_PAGE_SIZE, fixtures.length);
  const roundOptions = useMemo(
    () => Array.from({ length: maxRound - minRound + 1 }, (_, index) => minRound + index),
    [minRound, maxRound],
  );

  useEffect(() => {
    setMeta(null);
    setMetaSeason(null);
    void loadFixtureMeta();
    void loadTeamOptions();
    void loadSyncStatus(season);
  }, [season]);

  useEffect(() => {
    setSearchParams((current) => {
      const next = new URLSearchParams(current);
      let changed = false;
      if (next.get("season") !== String(season)) {
        next.set("season", String(season));
        if (mode === "date") {
          next.delete("week");
        }
        changed = true;
        return next;
      }
      if (next.get("mode") !== mode) {
        next.set("mode", mode);
        changed = true;
      }
      if (mode === "date" && isDateWeekReady && next.get("week") !== weekStart) {
        next.set("week", weekStart);
        changed = true;
      }
      if (mode === "date") {
        changed = deleteParams(next, ["round", "teamId", "page"]) || changed;
      }
      if (mode === "round" && next.get("round") !== String(Math.min(round, maxRound))) {
        next.set("round", String(Math.min(round, maxRound)));
        changed = true;
      }
      if (mode === "round") {
        changed = deleteParams(next, ["week", "teamId", "page"]) || changed;
      }
      if (mode === "team" && teams.length && !teams.some((team) => team.teamId === selectedTeamId)) {
        next.set("teamId", String(teams[0].teamId));
        next.set("page", "1");
        changed = true;
      }
      if (mode === "team" && next.get("page") !== String(teamPage + 1)) {
        next.set("page", String(teamPage + 1));
        changed = true;
      }
      if (mode === "team") {
        changed = deleteParams(next, ["week", "round"]) || changed;
      }
      return changed ? next : current;
    }, { replace: true });
  }, [isDateWeekReady, maxRound, mode, round, searchParams, season, selectedTeamId, setSearchParams, teamPage, teams, weekStart]);

  useEffect(() => {
    if (mode === "date" && !isDateWeekReady) {
      setFixtures([]);
      setIsLoading(true);
      return;
    }
    if (mode === "team" && !selectedTeamId) {
      setFixtures([]);
      setIsLoading(false);
      return;
    }
    void loadFixtures();
  }, [isDateWeekReady, mode, weekStart, weekEnd, round, selectedTeamId, season]);

  useEffect(() => {
    if (mode === "team" && !isLoading && teamPage >= totalTeamPages) {
      updateQuery({ page: totalTeamPages }, true);
    }
  }, [isLoading, mode, teamPage, totalTeamPages]);

  async function loadFixtureMeta() {
    const requestId = metaRequestId.current + 1;
    metaRequestId.current = requestId;
    try {
      const fixtureMeta = await fetchFixtureMeta(season);
      if (requestId !== metaRequestId.current) {
        return;
      }
      setMeta(fixtureMeta);
      setMetaSeason(season);
    } catch (error) {
      if (requestId !== metaRequestId.current) {
        return;
      }
      setErrorMessage(error instanceof Error ? error.message : "경기 범위를 불러오지 못했습니다.");
    }
  }

  async function loadTeamOptions() {
    const requestId = teamRequestId.current + 1;
    teamRequestId.current = requestId;
    setIsLoadingTeams(true);
    setTeamErrorMessage("");
    try {
      const standings = await fetchStandings(season);
      if (requestId !== teamRequestId.current) {
        return;
      }
      const teamOptions = standingsToTeamOptions(standings);
      setTeams(teamOptions);
    } catch (error) {
      if (requestId !== teamRequestId.current) {
        return;
      }
      setTeams([]);
      setTeamErrorMessage(error instanceof Error ? error.message : "팀 목록을 불러오지 못했습니다.");
    } finally {
      if (requestId === teamRequestId.current) {
        setIsLoadingTeams(false);
      }
    }
  }

  async function loadFixtures() {
    const query = queryForMode(mode, weekStart, weekEnd, round, selectedTeamId, season);
    if (!query) {
      return;
    }

    const requestId = fixtureRequestId.current + 1;
    fixtureRequestId.current = requestId;
    setIsLoading(true);
    setErrorMessage("");
    setFixtures([]);

    try {
      const response = await fetchFixtures(query);
      if (requestId !== fixtureRequestId.current) {
        return;
      }
      setFixtures(response.content ?? []);
    } catch (error) {
      if (requestId !== fixtureRequestId.current) {
        return;
      }
      setErrorMessage(error instanceof Error ? error.message : "경기 일정을 불러오지 못했습니다.");
    } finally {
      if (requestId === fixtureRequestId.current) {
        setIsLoading(false);
      }
    }
  }

  async function loadSyncStatus(targetSeason = season) {
    try {
      const response = await fetchSyncStatuses(targetSeason);
      setSyncStatus(response.statuses.find((status) => status.task === "fixtures") ?? null);
    } catch {
      setSyncStatus(null);
    }
  }

  async function refreshFixtureSync() {
    if (currentUser?.role !== "ADMIN" || isSyncing || Date.now() < syncCooldownUntil) {
      return;
    }
    setSyncToast(null);
    startCooldown();
    setIsSyncing(true);
    try {
      await requestAdminSync("fixtures", season);
      setSyncToast({ message: "최신 경기 데이터로 갱신했습니다.", type: "success" });
      await Promise.all([loadFixtures(), loadFixtureMeta(), loadSyncStatus(season)]);
    } catch (error) {
      setSyncToast({
        message: error instanceof Error ? error.message : "경기 데이터 갱신 요청에 실패했습니다.",
        type: "error",
      });
      await loadSyncStatus(season);
    } finally {
      setIsSyncing(false);
    }
  }

  function moveWeek(dayDelta: number) {
    updateQuery({ week: addDaysToDateKey(weekStart, dayDelta) });
  }

  function selectRound(nextRound: number) {
    updateQuery({ round: nextRound });
    setIsRoundMenuOpen(false);
  }

  function selectTeam(teamId: number) {
    updateQuery({ teamId, page: 1 });
  }

  function selectMode(nextMode: FixtureMode) {
    updateQuery({ mode: nextMode });
  }

  function selectTeamPage(nextPage: number) {
    updateQuery({ page: nextPage + 1 });
  }

  function updateQuery(values: Record<string, string | number>, replace = false) {
    setSearchParams((current) => {
      const next = new URLSearchParams(current);
      next.set("season", String(season));
      Object.entries(values).forEach(([key, value]) => next.set(key, String(value)));
      return next;
    }, { replace });
  }

  const isStale = isSyncStatusStale(syncStatus);

  return (
    <section className="league-content fixtures-page">
      {syncToast ? (
        <SyncToast
          message={syncToast.message}
          type={syncToast.type}
          onClose={() => setSyncToast(null)}
        />
      ) : null}
      <div className="fixtures-toolbar">
        <div className="segmented-control" aria-label="경기 조회 방식">
          {fixtureModes.map((item) => (
            <button
              className={mode === item.value ? "active" : ""}
              key={item.value}
              type="button"
              onClick={() => selectMode(item.value)}
            >
              {item.label}
            </button>
          ))}
          {currentUser?.role === "ADMIN" ? (
            <button
              className="sync-refresh-button"
              type="button"
              onClick={() => void refreshFixtureSync()}
              disabled={isSyncing || cooldownSeconds > 0}
              aria-label={cooldownSeconds > 0 ? `${cooldownSeconds}초 후 데이터 갱신 가능` : "경기 데이터 갱신"}
              title={cooldownSeconds > 0 ? `${cooldownSeconds}초 후 다시 요청할 수 있습니다.` : "경기 데이터 갱신"}
            >
              <RefreshCw className={`sync-refresh-icon${isSyncing ? " spinning" : ""}`} size={18} aria-hidden="true" />
            </button>
          ) : null}
        </div>

        {mode === "date" ? (
          <div className="fixture-control-group">
            <button type="button" onClick={() => moveWeek(-7)} disabled={!canMovePreviousWeek} aria-label="이전 주">
              <ChevronLeft size={18} aria-hidden="true" />
            </button>
            <strong>{formatRange(weekStart, weekEnd)}</strong>
            <button type="button" onClick={() => moveWeek(7)} disabled={!canMoveNextWeek} aria-label="다음 주">
              <ChevronRight size={18} aria-hidden="true" />
            </button>
          </div>
        ) : null}

        {mode === "round" ? (
          <div className="fixture-control-group round-control">
            <button type="button" onClick={() => selectRound(Math.max(minRound, round - 1))} disabled={round <= minRound} aria-label="이전 라운드">
              <ChevronLeft size={18} aria-hidden="true" />
            </button>
            <div className="round-menu-wrap">
              <button className="round-trigger" type="button" onClick={() => setIsRoundMenuOpen((open) => !open)}>
                {round}라운드
                <ChevronDown size={16} aria-hidden="true" />
              </button>
              {isRoundMenuOpen ? (
                <div className="round-menu">
                  {roundOptions.map((roundOption) => (
                    <button
                      className={roundOption === round ? "active" : ""}
                      key={roundOption}
                      type="button"
                      onClick={() => selectRound(roundOption)}
                    >
                      {roundOption}라운드
                    </button>
                  ))}
                </div>
              ) : null}
            </div>
            <button type="button" onClick={() => selectRound(Math.min(maxRound, round + 1))} disabled={round >= maxRound} aria-label="다음 라운드">
              <ChevronRight size={18} aria-hidden="true" />
            </button>
          </div>
        ) : null}

        {mode === "team" ? (
          <label className="team-select-field">
            <span>팀 선택</span>
            <select
              value={selectedTeamId ?? ""}
              onChange={(event) => selectTeam(Number(event.target.value))}
              disabled={isLoadingTeams || teams.length === 0}
            >
              {teams.map((team) => (
                <option value={team.teamId} key={team.teamId}>
                  {team.teamName}
                </option>
              ))}
            </select>
          </label>
        ) : null}
      </div>

      <div className={`data-freshness ${isStale ? "stale" : ""}`}>
        <span>{syncFreshnessText(syncStatus)}</span>
      </div>
      {mode === "team" ? (
        <TeamFixtureHeader
          selectedTeam={selectedTeam}
          teamRangeStart={teamRangeStart}
          teamRangeEnd={teamRangeEnd}
          totalFixtures={fixtures.length}
          teamPage={teamPage}
          totalTeamPages={totalTeamPages}
        />
      ) : null}

      {errorMessage ? <div className="notice error">{errorMessage}</div> : null}
      {mode === "team" && teamErrorMessage ? <div className="notice error">{teamErrorMessage}</div> : null}

      <article className="panel fixtures-panel">
        {isLoading ? (
          <div className="empty-state">경기 일정을 불러오는 중입니다.</div>
        ) : isLoadingTeams && mode === "team" ? (
          <div className="empty-state">팀 목록을 불러오는 중입니다.</div>
        ) : teams.length === 0 && mode === "team" ? (
          <div className="empty-state">선택 가능한 팀이 없습니다.</div>
        ) : (
          <FixtureGroups groupedFixtures={groupedFixtures} />
        )}
      </article>

      {mode === "team" && fixtures.length > TEAM_PAGE_SIZE ? (
        <div className="team-pager">
          <button type="button" onClick={() => selectTeamPage(Math.max(0, teamPage - 1))} disabled={teamPage === 0}>
            <ChevronLeft size={18} aria-hidden="true" />
            이전
          </button>
          <strong>{teamPage + 1} / {totalTeamPages}</strong>
          <button type="button" onClick={() => selectTeamPage(Math.min(totalTeamPages - 1, teamPage + 1))} disabled={teamPage >= totalTeamPages - 1}>
            다음
            <ChevronRight size={18} aria-hidden="true" />
          </button>
        </div>
      ) : null}
    </section>
  );
}

function isSyncStatusStale(status: SyncStatus | null) {
  if (!status) {
    return false;
  }
  if (status.status === "FAILED" || status.status === "RETRY_PENDING") {
    return true;
  }
  const lastSuccess = lastSuccessfulSyncTime(status);
  return lastSuccess ? Date.now() - Date.parse(lastSuccess) > 24 * 60 * 60 * 1000 : false;
}

function syncFreshnessText(status: SyncStatus | null) {
  const lastSuccess = lastSuccessfulSyncTime(status);
  if (!lastSuccess) {
    return "최근 업데이트 시간: 확인할 수 없음";
  }
  return `최근 업데이트 시간: ${formatDateTime(lastSuccess)}`;
}

function formatDateTime(value: string) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }
  return new Intl.DateTimeFormat("ko-KR", {
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
  }).format(date);
}

function lastSuccessfulSyncTime(status: SyncStatus | null) {
  if (!status) {
    return null;
  }
  if (status.lastSuccessAt) {
    return status.lastSuccessAt;
  }
  return status.status === "OK" || status.status === "STALE" ? status.lastSyncedAt : null;
}

function TeamFixtureHeader({
  selectedTeam,
  teamRangeStart,
  teamRangeEnd,
  totalFixtures,
  teamPage,
  totalTeamPages,
}: {
  selectedTeam: FixtureTeamOption | null;
  teamRangeStart: number;
  teamRangeEnd: number;
  totalFixtures: number;
  teamPage: number;
  totalTeamPages: number;
}) {
  if (!selectedTeam) {
    return null;
  }

  return (
    <div className="team-fixture-header">
      <div>
        {selectedTeam.logoUrl ? (
          <img src={selectedTeam.logoUrl} alt="" className="team-logo" />
        ) : (
          <span className="team-logo placeholder" aria-hidden="true" />
        )}
        <strong>{selectedTeam.teamName}</strong>
      </div>
      <span>
        경기 {teamRangeStart}-{teamRangeEnd} / 총 {totalFixtures} · {teamPage + 1} / {totalTeamPages}
      </span>
    </div>
  );
}

function FixtureGroups({ groupedFixtures }: { groupedFixtures: Array<[string, FixtureSummary[]]> }) {
  if (!groupedFixtures.length) {
    return <div className="empty-state">표시할 경기가 없습니다.</div>;
  }

  return (
    <div className="fixture-group-list">
      {groupedFixtures.map(([dateKey, fixtures]) => (
        <section className="fixture-date-group" key={dateKey}>
          <h2>
            {dateGroupTitle(dateKey)}
            <span>{roundLabel(fixtures)}</span>
          </h2>
          <div className="fixture-card-list">
            {fixtures.map((fixture) => (
              <FixtureCard fixture={fixture} key={fixture.fixtureId} />
            ))}
          </div>
        </section>
      ))}
    </div>
  );
}

function FixtureCard({ fixture }: { fixture: FixtureSummary }) {
  return (
    <Link className="fixture-match-card" to={`/fixtures/${fixture.fixtureId}`}>
      <time>{formatTime(fixture.fixtureDate)}</time>
      <div className="fixture-match-team home">
        <strong>{displayLocalizedName(fixture.homeTeamNameKo, fixture.homeTeamName)}</strong>
        {fixture.homeTeamLogoUrl ? (
          <img src={fixture.homeTeamLogoUrl} alt="" className="team-logo" />
        ) : (
          <span className="team-logo placeholder" aria-hidden="true" />
        )}
      </div>
      <span className="fixture-match-score">{scoreText(fixture)}</span>
      <div className="fixture-match-team away">
        {fixture.awayTeamLogoUrl ? (
          <img src={fixture.awayTeamLogoUrl} alt="" className="team-logo" />
        ) : (
          <span className="team-logo placeholder" aria-hidden="true" />
        )}
        <strong>{displayLocalizedName(fixture.awayTeamNameKo, fixture.awayTeamName)}</strong>
      </div>
      <span className="status-pill">{fixtureStatusLabel(fixture)}</span>
    </Link>
  );
}

function standingsToTeamOptions(standings: TeamStanding[]): FixtureTeamOption[] {
  return standings
    .slice()
    .sort((a, b) => valueOf(a.rank) - valueOf(b.rank))
    .map((standing) => ({
      teamId: standing.team?.id ?? 0,
      teamName: displayLocalizedName(standing.team?.nameKo, standing.team?.name),
      logoUrl: standing.team?.logo ?? null,
    }))
    .filter((team) => team.teamId > 0);
}

function roundLabel(fixtures: FixtureSummary[]) {
  const rounds = Array.from(
    new Set(
      fixtures
        .map((fixture) => fixture.round)
        .filter((round): round is number => typeof round === "number"),
    ),
  ).sort((a, b) => a - b);

  if (!rounds.length) {
    return "";
  }
  if (rounds.length === 1) {
    return `${rounds[0]}라운드`;
  }
  return `${rounds[0]}-${rounds[rounds.length - 1]}라운드`;
}

function queryForMode(
  mode: FixtureMode,
  weekStart: string,
  weekEnd: string,
  round: number,
  selectedTeamId: number | null,
  season: number,
) {
  if (mode === "date") {
    return { season, dateFrom: weekStart, dateTo: weekEnd, size: 100 };
  }
  if (mode === "round") {
    return { season, round, size: 100 };
  }
  if (selectedTeamId) {
    return { season, teamId: selectedTeamId, size: TEAM_FETCH_SIZE };
  }
  return null;
}

function groupFixturesByDate(fixtures: FixtureSummary[]) {
  const sorted = fixtures
    .slice()
    .sort((a, b) => fixtureTime(a.fixtureDate) - fixtureTime(b.fixtureDate));
  const groups = new Map<string, FixtureSummary[]>();
  sorted.forEach((fixture) => {
    const key = dateKey(fixture.fixtureDate);
    groups.set(key, [...(groups.get(key) ?? []), fixture]);
  });
  return Array.from(groups.entries());
}

function sortTeamFixtures(fixtures: FixtureSummary[]) {
  return fixtures.slice().sort((a, b) => {
    const roundA = a.round ?? Number.MAX_SAFE_INTEGER;
    const roundB = b.round ?? Number.MAX_SAFE_INTEGER;
    return roundA - roundB || fixtureTime(a.fixtureDate) - fixtureTime(b.fixtureDate);
  });
}

function todayKoreaDateKey() {
  return new Intl.DateTimeFormat("en-CA", {
    timeZone: "Asia/Seoul",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).format(new Date());
}

function fixtureMode(value: string | null): FixtureMode | null {
  return value === "date" || value === "round" || value === "team" ? value : null;
}

function positiveInteger(value: string | null) {
  if (!value || !/^\d+$/.test(value)) {
    return null;
  }
  const number = Number(value);
  return Number.isSafeInteger(number) && number > 0 ? number : null;
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

function clampWeekStart(weekStart: string, meta: FixtureMeta) {
  const minWeek = meta.minDate ? startOfKoreaWeek(meta.minDate) : null;
  const maxWeek = meta.maxDate ? startOfKoreaWeek(meta.maxDate) : null;
  if (minWeek && weekStart < minWeek) {
    return minWeek;
  }
  if (maxWeek && weekStart > maxWeek) {
    return maxWeek;
  }
  return weekStart;
}

function defaultWeekStart(meta: FixtureMeta) {
  const defaultDate = meta.latestStartedDate ?? meta.minDate ?? todayKoreaDateKey();
  return startOfKoreaWeek(defaultDate);
}

function deleteParams(params: URLSearchParams, keys: string[]) {
  let changed = false;
  keys.forEach((key) => {
    if (params.has(key)) {
      params.delete(key);
      changed = true;
    }
  });
  return changed;
}

function startOfKoreaWeek(dateKeyValue: string) {
  const date = parseDateKeyAsUtcDate(dateKeyValue);
  const day = date.getUTCDay();
  date.setUTCDate(date.getUTCDate() - day);
  return formatKoreaDate(date);
}

function addDaysToDateKey(dateKeyValue: string, dayDelta: number) {
  const date = parseDateKeyAsUtcDate(dateKeyValue);
  date.setUTCDate(date.getUTCDate() + dayDelta);
  return formatKoreaDate(date);
}

function parseDateKeyAsUtcDate(dateKeyValue: string) {
  const [year, month, day] = dateKeyValue.split("-").map(Number);
  return new Date(Date.UTC(year, month - 1, day));
}

function formatKoreaDate(date: Date) {
  return new Intl.DateTimeFormat("en-CA", {
    timeZone: "Asia/Seoul",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).format(date);
}

function formatRange(start: string, end: string) {
  return `${shortDate(start)} - ${shortDate(end)}`;
}

function shortDate(value: string) {
  const date = parseDateKeyAsUtcDate(value);
  return new Intl.DateTimeFormat("ko-KR", {
    timeZone: "Asia/Seoul",
    month: "short",
    day: "numeric",
  }).format(date);
}

function dateGroupTitle(value: string) {
  return formatFixtureDateKey(value, value);
}

function dateKey(value: string | null) {
  if (!value) {
    return "unknown";
  }
  const date = parseFixtureDate(value);
  return date ? formatKoreaDate(date) : value.slice(0, 10);
}

function fixtureTime(value: string | null) {
  return parseFixtureDate(value)?.getTime() ?? 0;
}

function parseFixtureDate(value: string | null) {
  return parseKoreaDateTime(value);
}

function formatTime(value: string | null) {
  const date = parseFixtureDate(value);
  if (!date) {
    return value?.slice(11, 16) || "-";
  }
  return new Intl.DateTimeFormat("ko-KR", {
    timeZone: "Asia/Seoul",
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
  }).format(date);
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
