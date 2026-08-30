import { useEffect, useMemo, useRef, useState } from "react";
import { RefreshCw } from "lucide-react";
import { Link } from "react-router-dom";
import {
  fetchStandings,
  fetchSyncStatuses,
  requestAdminSync,
  type CurrentUser,
  type RecentForm,
  type StandingRecord,
  type StandingLiveMatch,
  type StandingNextMatch,
  type StandingRecentMatch,
  type SyncStatus,
  type TeamStanding,
} from "../api";
import { displayLocalizedName } from "../teamNames";
import { fixtureStatusLabel } from "../fixtureStatus";
import { formatFixtureDate, formatFixtureDateTime } from "../dateUtils";
import { SyncToast } from "../components/SyncToast";
import { useManualSyncCooldown } from "../useManualSyncCooldown";

type StandingMode = "all" | "home" | "away" | "recent";

const standingModes: Array<{ label: string; value: StandingMode }> = [
  { label: "모두", value: "all" },
  { label: "홈", value: "home" },
  { label: "원정", value: "away" },
  { label: "최근 5경기", value: "recent" },
];

export function LeagueStandingsPage({ currentUser, season }: { currentUser: CurrentUser | null; season: number }) {
  const [standings, setStandings] = useState<TeamStanding[]>([]);
  const [syncStatus, setSyncStatus] = useState<SyncStatus | null>(null);
  const [syncToast, setSyncToast] = useState<{ message: string; type: "success" | "error" } | null>(null);
  const { cooldownUntil: syncCooldownUntil, cooldownSeconds, startCooldown } = useManualSyncCooldown("standings", season);
  const [isSyncing, setIsSyncing] = useState(false);
  const [mode, setMode] = useState<StandingMode>("all");
  const [isLoading, setIsLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState("");
  const standingsRequestIdRef = useRef(0);

  const rows = useMemo(
    () =>
      standings
        .map((standing) => toStandingRow(standing, mode))
        .sort((a, b) => compareStandingRows(a, b, mode))
        .map((row, index) => ({
          ...row,
          displayRank: mode === "all" && row.sourceRank > 0 ? row.sourceRank : index + 1,
        })),
    [standings, mode],
  );

  async function loadStandings(targetSeason = season) {
    const requestId = standingsRequestIdRef.current + 1;
    standingsRequestIdRef.current = requestId;
    setIsLoading(true);
    setErrorMessage("");

    try {
      const nextStandings = await fetchStandings(targetSeason, { fresh: true });
      if (requestId === standingsRequestIdRef.current) {
        setStandings(nextStandings);
      }
    } catch (error) {
      if (requestId !== standingsRequestIdRef.current) {
        return;
      }
      setStandings([]);
      setErrorMessage(
        error instanceof Error ? error.message : "순위 정보를 불러오지 못했습니다.",
      );
    } finally {
      if (requestId === standingsRequestIdRef.current) {
        setIsLoading(false);
      }
    }
  }

  async function loadSyncStatus(targetSeason = season) {
    try {
      const response = await fetchSyncStatuses(targetSeason);
      setSyncStatus(response.statuses.find((status) => status.task === "standings") ?? null);
    } catch {
      setSyncStatus(null);
    }
  }

  async function refreshStandingsSync() {
    if (currentUser?.role !== "ADMIN" || isSyncing || Date.now() < syncCooldownUntil) {
      return;
    }
    setSyncToast(null);
    startCooldown();
    setIsSyncing(true);
    try {
      await requestAdminSync("standings", season);
      setSyncToast({ message: "최신 순위 데이터로 갱신했습니다.", type: "success" });
      await Promise.all([loadStandings(season), loadSyncStatus(season)]);
    } catch (error) {
      setSyncToast({
        message: error instanceof Error ? error.message : "순위 데이터 갱신 요청에 실패했습니다.",
        type: "error",
      });
      await loadSyncStatus(season);
    } finally {
      setIsSyncing(false);
    }
  }

  useEffect(() => {
    void loadStandings(season);
    void loadSyncStatus(season);
  }, [season]);

  const isStale = isSyncStatusStale(syncStatus);

  return (
    <section className="league-content">
      {syncToast ? (
        <SyncToast
          message={syncToast.message}
          type={syncToast.type}
          onClose={() => setSyncToast(null)}
        />
      ) : null}
      <div className="standings-toolbar">
        <div className="segmented-control" aria-label="순위 범위">
          {standingModes.map((item) => (
            <button
              className={mode === item.value ? "active" : ""}
              key={item.value}
              type="button"
              onClick={() => setMode(item.value)}
            >
              {item.label}
            </button>
          ))}
          {currentUser?.role === "ADMIN" ? (
            <button
              className="sync-refresh-button"
              type="button"
              onClick={() => void refreshStandingsSync()}
              disabled={isSyncing || cooldownSeconds > 0}
              aria-label={cooldownSeconds > 0 ? `${cooldownSeconds}초 후 데이터 갱신 가능` : "순위 데이터 갱신"}
              title={cooldownSeconds > 0 ? `${cooldownSeconds}초 후 다시 요청할 수 있습니다.` : "순위 데이터 갱신"}
            >
              <RefreshCw className={`sync-refresh-icon${isSyncing ? " spinning" : ""}`} size={18} aria-hidden="true" />
            </button>
          ) : null}
        </div>
      </div>

      <div className={`data-freshness ${isStale ? "stale" : ""}`}>
        <span>{syncFreshnessText(syncStatus)}</span>
      </div>
      {errorMessage ? <div className="notice error">{errorMessage}</div> : null}

      <article className="panel standings-panel">
        {isLoading ? (
          <div className="empty-state">순위 정보를 불러오는 중입니다.</div>
        ) : (
          <>
            <StandingsTable rows={rows} />
            <QualificationLegend />
          </>
        )}
      </article>
    </section>
  );
}

function StandingsTable({ rows }: { rows: StandingRow[] }) {
  if (!rows.length) {
    return <div className="empty-state">순위 데이터가 없습니다.</div>;
  }

  return (
    <div className="standings-table-wrap">
      <table className="standings-table">
        <thead>
          <tr>
            <th>#</th>
            <th className="team-column">팀</th>
            <th>경기</th>
            <th>승</th>
            <th>무</th>
            <th>패</th>
            <th>득실</th>
            <th>+/-</th>
            <th>승점</th>
            <th>기록</th>
            <th>다음</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((row) => (
            <tr key={row.teamId}>
              <td className={`rank-cell ${row.qualificationClass}`}>
                <span>{row.displayRank}</span>
              </td>
              <td className="team-cell">
                {row.logo ? (
                  <img src={row.logo} alt="" className="team-logo" />
                ) : (
                  <span className="team-logo placeholder" aria-hidden="true" />
                )}
                <Link className="team-name-link" to={`/teams/${row.teamId}`}>
                  {row.teamName}
                </Link>
                {row.liveMatch ? <LiveScoreBadge liveMatch={row.liveMatch} /> : null}
              </td>
              <td>{row.played}</td>
              <td>{row.win}</td>
              <td>{row.draw}</td>
              <td>{row.lose}</td>
              <td>{row.goalsText}</td>
              <td>{row.goalsDiffText}</td>
              <td className="points-cell">{row.points}</td>
              <td>
                <ResultChips
                  matches={row.recentMatches}
                  results={row.results}
                  teamName={row.teamName}
                />
              </td>
              <td>
                {row.nextMatch ? (
                  <NextMatchLink nextMatch={row.nextMatch} teamName={row.teamName} />
                ) : (
                  <span className="muted">-</span>
                )}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function LiveScoreBadge({ liveMatch }: { liveMatch: StandingLiveMatch }) {
  const score = `${liveMatch.scoreFor}-${liveMatch.scoreAgainst}`;
  const status = fixtureStatusLabel({
    fixtureStatus: "LIVE",
    statusShort: liveMatch.statusShort,
    statusLong: null,
    elapsed: liveMatch.elapsed,
    extra: liveMatch.extra,
  });
  const resultLabel = liveMatch.result === "WINNING"
    ? "이기는 중"
    : liveMatch.result === "LOSING"
      ? "지는 중"
      : "동점";

  return (
    <Link
      className={`standing-live-score ${liveMatch.result.toLowerCase()}`}
      to={`/fixtures/${liveMatch.fixtureId}`}
      title={`${status} · ${score} ${resultLabel}`}
      aria-label={`진행 중인 경기 ${status}, ${score}, ${resultLabel}`}
    >
      {score}
    </Link>
  );
}

function ResultChips({
  matches,
  results,
  teamName,
}: {
  matches: StandingRecentMatch[];
  results: string[];
  teamName: string;
}) {
  if (!results.length) {
    return <span className="muted">-</span>;
  }

  return (
    <div className="result-chips" aria-label={`최근 기록 ${results.join("")}`}>
      {matches.length
        ? matches.map((match) => <RecentMatchChip key={match.fixtureId} match={match} teamName={teamName} />)
        : results.map((result, index) => (
            <span className={`result-chip ${result.toLowerCase()}`} key={`${result}-${index}`}>
              {translateResult(result)}
            </span>
          ))}
    </div>
  );
}

function NextMatchLink({ nextMatch, teamName }: { nextMatch: StandingNextMatch; teamName: string }) {
  const opponentName = displayLocalizedName(nextMatch.opponent?.nameKo, nextMatch.opponent?.name);
  const date = formatFixtureDateTime(nextMatch.fixtureDate, "일정 미정");
  const matchup = nextMatch.venue === "HOME"
    ? `${teamName} vs ${opponentName}`
    : `${opponentName} vs ${teamName}`;
  const description = `${date} · ${matchup}`;

  return (
    <Link
      className="standing-next-match"
      to={`/fixtures/${nextMatch.fixtureId}`}
      aria-label={`${description}, 경기 상세 보기`}
    >
      {nextMatch.opponent?.logo ? (
        <img src={nextMatch.opponent.logo} alt="" />
      ) : (
        <span className="standing-next-match-placeholder" aria-hidden="true" />
      )}
      <span className="result-chip-tooltip" role="tooltip">
        {description}
      </span>
    </Link>
  );
}

function RecentMatchChip({ match, teamName }: { match: StandingRecentMatch; teamName: string }) {
  const opponentName = displayLocalizedName(match.opponent?.nameKo, match.opponent?.name);
  const date = formatFixtureDate(match.fixtureDate, "날짜 미정");
  const matchup = match.venue === "HOME"
    ? `${teamName} ${match.scoreFor}-${match.scoreAgainst} ${opponentName}`
    : `${opponentName} ${match.scoreAgainst}-${match.scoreFor} ${teamName}`;
  const description = `${date} · ${matchup}`;

  return (
    <Link
      className={`result-chip ${match.result.toLowerCase()}`}
      to={`/fixtures/${match.fixtureId}`}
      aria-label={`${description}, 경기 상세 보기`}
    >
      {translateResult(match.result)}
      <span className="result-chip-tooltip" role="tooltip">
        {description}
      </span>
    </Link>
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

function lastSuccessfulSyncTime(status: SyncStatus | null) {
  if (!status) {
    return null;
  }
  if (status.lastSuccessAt) {
    return status.lastSuccessAt;
  }
  return status.status === "OK" || status.status === "STALE" ? status.lastSyncedAt : null;
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

type StandingRow = {
  teamId: number;
  sourceRank: number;
  displayRank: number;
  teamName: string;
  logo: string | null;
  played: number;
  win: number;
  draw: number;
  lose: number;
  goalsFor: number;
  goalsAgainst: number;
  goalsDiff: number;
  goalsText: string;
  goalsDiffText: string;
  points: number;
  results: string[];
  recentMatches: StandingRecentMatch[];
  qualificationClass: string;
  liveMatch: StandingLiveMatch | null;
  nextMatch: StandingNextMatch | null;
};

function toStandingRow(standing: TeamStanding, mode: StandingMode): StandingRow {
  const source = standingRecordForMode(standing, mode);
  const recentMatches = standing.recentForm?.matches?.slice(0, 5) ?? [];
  const formResults = recentMatches.length
    ? recentMatches.map((match) => match.result)
    : standing.recentForm?.results?.length
      ? standing.recentForm.results.slice(0, 5)
    : parseFormResults(standing.form);
  const recentSummary = summarizeResults(formResults);
  const goalsFor = valueOf(source?.goals?.goalsFor);
  const goalsAgainst = valueOf(source?.goals?.goalsAgainst);
  const goalsDiff =
    mode === "recent"
      ? valueOf(standing.recentForm?.goalsDiff)
      : goalsFor - goalsAgainst;

  return {
    teamId: standing.team?.id ?? standing.rank ?? 0,
    sourceRank: standing.rank ?? 0,
    displayRank: standing.rank ?? 0,
    teamName: displayLocalizedName(standing.team?.nameKo, standing.team?.name),
    logo: standing.team?.logo ?? null,
    played: mode === "recent" ? recentSummary.played : valueOf(source?.played),
    win: mode === "recent" ? recentSummary.win : valueOf(source?.win),
    draw: mode === "recent" ? recentSummary.draw : valueOf(source?.draw),
    lose: mode === "recent" ? recentSummary.lose : valueOf(source?.lose),
    goalsFor,
    goalsAgainst,
    goalsDiff,
    goalsText: `${goalsFor}-${goalsAgainst}`,
    goalsDiffText: formatDiff(goalsDiff),
    points:
      mode === "recent"
        ? recentSummary.points
        : standingPointsForMode(standing, mode),
    results: formResults,
    recentMatches,
    qualificationClass: qualificationClassOf(standing.description),
    liveMatch: mode === "all" ? standing.liveMatch : null,
    nextMatch: standing.nextMatch,
  };
}

function standingRecordForMode(
  standing: TeamStanding,
  mode: StandingMode,
): StandingRecord | RecentForm | null {
  if (mode === "home") {
    return standing.home;
  }
  if (mode === "away") {
    return standing.away;
  }
  if (mode === "recent") {
    return standing.recentForm;
  }
  return standing.all;
}

function standingPointsForMode(standing: TeamStanding, mode: StandingMode) {
  const record = standingRecordForMode(standing, mode);
  if (mode === "all") {
    return valueOf(standing.points);
  }
  return valueOf(record?.win) * 3 + valueOf(record?.draw);
}

function formatDiff(value: number) {
  return value > 0 ? `+${value}` : String(value);
}

function compareStandingRows(a: StandingRow, b: StandingRow, mode: StandingMode) {
  const sourceRankDifference = compareSourceRanks(a.sourceRank, b.sourceRank);
  if (mode === "all" && sourceRankDifference !== 0) {
    return sourceRankDifference;
  }
  return (
    b.points - a.points ||
    b.goalsDiff - a.goalsDiff ||
    b.goalsFor - a.goalsFor ||
    sourceRankDifference ||
    String(a.teamName).localeCompare(String(b.teamName))
  );
}

function compareSourceRanks(a: number, b: number) {
  if (a === b) {
    return 0;
  }
  if (a <= 0) {
    return 1;
  }
  if (b <= 0) {
    return -1;
  }
  return a - b;
}

function parseFormResults(form: string | null) {
  return String(form ?? "")
    .toUpperCase()
    .split("")
    .filter((result) => result === "W" || result === "D" || result === "L");
}

function summarizeResults(results: string[]) {
  return results.reduce(
    (summary, result) => {
      summary.played++;
      if (result === "W") {
        summary.win++;
        summary.points += 3;
      } else if (result === "D") {
        summary.draw++;
        summary.points += 1;
      } else if (result === "L") {
        summary.lose++;
      }
      return summary;
    },
    { played: 0, win: 0, draw: 0, lose: 0, points: 0 },
  );
}

function valueOf(value: number | null | undefined) {
  return value ?? 0;
}

function translateResult(result: string) {
  if (result === "W") {
    return "승";
  }
  if (result === "D") {
    return "무";
  }
  if (result === "L") {
    return "패";
  }
  return result;
}

function qualificationClassOf(description: string | null) {
  const text = String(description ?? "").toLowerCase();
  if (text.includes("champions league")) {
    return "champions";
  }
  if (text.includes("europa league")) {
    return "europa";
  }
  if (text.includes("conference league")) {
    return "conference";
  }
  if (text.includes("relegation")) {
    return "relegation";
  }
  return "";
}

function QualificationLegend() {
  return (
    <div className="qualification-legend" aria-label="순위 색상 안내">
      <span><i className="champions" />챔피언스리그 진출</span>
      <span><i className="europa" />유로파리그 진출</span>
      <span><i className="conference" />컨퍼런스리그 진출</span>
      <span><i className="relegation" />강등</span>
    </div>
  );
}
