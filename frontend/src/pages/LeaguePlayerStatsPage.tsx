import { useEffect, useMemo, useRef, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import {
  fetchLeaguePlayerRankings,
  type LeaguePlayerRankingRow,
  type LeaguePlayerRankings,
} from "../api";
import { RankingInfoButton } from "../components/RankingInfoButton";
import { displayLocalizedName } from "../teamNames";

type RankingKey = keyof Pick<
  LeaguePlayerRankings,
  | "goals"
  | "assists"
  | "attackPoints"
  | "ratings"
  | "minutes"
  | "yellowCards"
  | "redCards"
  | "saves"
  | "cleanSheets"
  | "savePercentages"
>;

type Metric = {
  label: string;
  value: (row: LeaguePlayerRankingRow) => string;
};

type RankingCategory = {
  key: RankingKey;
  label: string;
  description: string;
  primary: Metric;
  secondary: Metric[];
};

const categories: RankingCategory[] = [
  {
    key: "goals",
    label: "득점",
    description: "페널티 득점이 적은 선수를 우선하는 득점 순위입니다.",
    primary: metric("득점", (row) => `${row.goals}`),
    secondary: [
      metric("도움", (row) => `${row.assists}`),
      metric("PK", (row) => `${row.penaltyGoals}`),
    ],
  },
  {
    key: "assists",
    label: "도움",
    description: "시즌 도움 수 순위이며, 도움 수가 같으면 공동 순위로 표시합니다.",
    primary: metric("도움", (row) => `${row.assists}`),
    secondary: [
      metric("득점", (row) => `${row.goals}`),
      metric("기록 경기", (row) => `${row.assistMatches}`),
    ],
  },
  {
    key: "attackPoints",
    label: "공격포인트",
    description: "득점과 도움을 합산한 공격포인트 순위입니다.",
    primary: metric("공격P", (row) => `${row.attackPoints}`),
    secondary: [
      metric("득점", (row) => `${row.goals}`),
      metric("도움", (row) => `${row.assists}`),
    ],
  },
  {
    key: "ratings",
    label: "평점",
    description: "시즌 평균 평점 순위입니다. 시즌 초반에는 진행 경기 수에 따라 최소 표본 기준이 조정됩니다. 정규 기준은 5경기 출전 / 피유효슈팅 10회입니다.",
    primary: metric("평점", (row) => row.rating.toFixed(2)),
    secondary: [
      metric("경기", (row) => `${row.appearances}`),
      metric("시간", (row) => formatMinutes(row.minutes)),
    ],
  },
  {
    key: "minutes",
    label: "출전시간",
    description: "시즌 누적 출전시간 순위입니다.",
    primary: metric("출전시간", (row) => formatMinutes(row.minutes)),
    secondary: [
      metric("경기", (row) => `${row.appearances}`),
      metric("평점", (row) => row.rating.toFixed(2)),
    ],
  },
  {
    key: "yellowCards",
    label: "옐로카드",
    description: "옐로카드가 많은 선수부터 표시합니다.",
    primary: metric("옐로", (row) => `${row.yellowCards}`),
    secondary: [
      metric("레드", (row) => `${row.redCards}`),
      metric("경기", (row) => `${row.appearances}`),
    ],
  },
  {
    key: "redCards",
    label: "레드카드",
    description: "레드카드가 많은 선수부터 표시합니다.",
    primary: metric("레드", (row) => `${row.redCards}`),
    secondary: [
      metric("옐로", (row) => `${row.yellowCards}`),
      metric("경기", (row) => `${row.appearances}`),
    ],
  },
  {
    key: "saves",
    label: "세이브",
    description: "골키퍼의 시즌 누적 세이브 순위입니다.",
    primary: metric("세이브", (row) => `${row.saves}`),
    secondary: [
      metric("실점", (row) => `${row.conceded}`),
      metric("시간", (row) => formatMinutes(row.minutes)),
    ],
  },
  {
    key: "cleanSheets",
    label: "클린시트",
    description: "1분 이상 출전한 무실점 경기 수입니다.",
    primary: metric("클린시트", (row) => `${row.cleanSheets}`),
    secondary: [
      metric("세이브", (row) => `${row.saves}`),
      metric("시간", (row) => formatMinutes(row.minutes)),
    ],
  },
  {
    key: "savePercentages",
    label: "선방률",
    description: "골키퍼의 세이브 ÷ (세이브 + 실점) 값입니다. 시즌 초반에는 진행 경기 수에 따라 최소 표본 기준이 조정됩니다. 정규 기준은 5경기 출전 / 피유효슈팅 10회입니다.",
    primary: metric("선방률", (row) => formatPercentage(row.savePercentage)),
    secondary: [
      metric("세이브", (row) => `${row.saves}`),
      metric("실점", (row) => `${row.conceded}`),
    ],
  },
];

export function LeaguePlayerStatsPage({ season }: { season: number }) {
  const [searchParams, setSearchParams] = useSearchParams();
  const activeKey = rankingKey(searchParams.get("stat")) ?? "goals";
  const [rankings, setRankings] = useState<LeaguePlayerRankings | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState("");
  const requestIdRef = useRef(0);

  const category = useMemo(
    () => categories.find((item) => item.key === activeKey) ?? categories[0],
    [activeKey],
  );
  const rows = rankings?.[activeKey] ?? [];

  async function loadRankings(targetSeason = season) {
    const requestId = requestIdRef.current + 1;
    requestIdRef.current = requestId;
    setRankings(null);
    setIsLoading(true);
    setErrorMessage("");

    try {
      const response = await fetchLeaguePlayerRankings(targetSeason);
      if (requestId === requestIdRef.current) {
        setRankings(response);
      }
    } catch (error) {
      if (requestId !== requestIdRef.current) {
        return;
      }
      setErrorMessage(error instanceof Error ? error.message : "플레이어 통계 순위를 불러오지 못했습니다.");
    } finally {
      if (requestId === requestIdRef.current) {
        setIsLoading(false);
      }
    }
  }

  useEffect(() => {
    void loadRankings(season);
  }, [season]);

  useEffect(() => {
    if (searchParams.get("stat") !== activeKey) {
      setSearchParams((current) => {
        const next = new URLSearchParams(current);
        next.set("stat", activeKey);
        return next;
      }, { replace: true });
    }
  }, [activeKey, searchParams, setSearchParams]);

  function selectRanking(nextKey: RankingKey) {
    setSearchParams((current) => {
      const next = new URLSearchParams(current);
      next.set("stat", nextKey);
      return next;
    });
  }

  return (
    <section className="league-content player-rankings-page">
      <div className="player-rankings-heading">
        <div>
          <p className="eyebrow">{season} 시즌</p>
          <div className="ranking-title-row">
            <h2>플레이어 통계 순위</h2>
            <RankingInfoButton description={category.description} />
          </div>
        </div>
      </div>

      <div className="segmented-control player-ranking-tabs" aria-label="플레이어 통계 종류">
        {categories.map((item) => (
          <button
            className={activeKey === item.key ? "active" : ""}
            key={item.key}
            type="button"
            onClick={() => selectRanking(item.key)}
          >
            {item.label}
          </button>
        ))}
      </div>

      <article className="panel player-ranking-panel">
        {isLoading ? <div className="empty-state">플레이어 순위를 불러오는 중입니다.</div> : null}
        {errorMessage ? (
          <div className="section-error inline-retry">
            <span>{errorMessage}</span>
            <button type="button" onClick={() => void loadRankings()}>
              다시 불러오기
            </button>
          </div>
        ) : null}
        {!isLoading && !errorMessage && !rows.length ? (
          <div className="empty-state">이 시즌에 표시할 {category.label} 기록이 없습니다.</div>
        ) : null}
        {!isLoading && !errorMessage && rows.length ? (
          <RankingTable category={category} rows={rows} />
        ) : null}
      </article>
    </section>
  );
}

function rankingKey(value: string | null): RankingKey | null {
  return categories.some((category) => category.key === value) ? value as RankingKey : null;
}

function RankingTable({
  category,
  rows,
}: {
  category: RankingCategory;
  rows: LeaguePlayerRankingRow[];
}) {
  return (
    <div className="player-ranking-table-wrap">
      <table className="player-ranking-table">
        <thead>
          <tr>
            <th>순위</th>
            <th className="player-ranking-player-column">선수</th>
            <th className="player-ranking-team-column">팀</th>
            <th className="player-ranking-position-column">포지션</th>
            <th className="player-ranking-primary-column">{category.primary.label}</th>
            {category.secondary.map((item) => (
              <th className="player-ranking-secondary-column" key={item.label}>
                {item.label}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((row) => (
            <tr key={row.playerId}>
              <td className="player-ranking-rank">{row.rank}</td>
              <td>
                <PlayerIdentity row={row} />
              </td>
              <td className="player-ranking-team-column">
                <TeamIdentity row={row} />
              </td>
              <td className="player-ranking-position-column">{positionLabel(row.position)}</td>
              <td className="player-ranking-primary">{category.primary.value(row)}</td>
              {category.secondary.map((item) => (
                <td className="player-ranking-secondary-column" key={item.label}>
                  {item.value(row)}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function PlayerIdentity({ row }: { row: LeaguePlayerRankingRow }) {
  const [imageFailed, setImageFailed] = useState(false);

  return (
    <div className="player-ranking-player">
      {!imageFailed && row.photoUrl ? (
        <img src={row.photoUrl} alt="" onError={() => setImageFailed(true)} />
      ) : (
        <span className="player-ranking-photo-placeholder" aria-hidden="true" />
      )}
      <div>
        <span className="player-ranking-player-name">
          <Link to={`/players/${row.playerId}`}>{displayLocalizedName(row.playerNameKo, row.playerName)}</Link>
          {row.provisional ? <span className="player-ranking-provisional">잠정</span> : null}
        </span>
        <span className="player-ranking-mobile-meta">
          {displayLocalizedName(row.teamNameKo, row.teamName)} · {positionLabel(row.position)}
        </span>
      </div>
    </div>
  );
}

function TeamIdentity({ row }: { row: LeaguePlayerRankingRow }) {
  const [imageFailed, setImageFailed] = useState(false);

  return (
    <Link className="player-ranking-team" to={`/teams/${row.teamId}`}>
      {!imageFailed && row.teamLogoUrl ? (
        <img src={row.teamLogoUrl} alt="" onError={() => setImageFailed(true)} />
      ) : (
        <span className="player-ranking-logo-placeholder" aria-hidden="true" />
      )}
      <span>{displayLocalizedName(row.teamNameKo, row.teamName)}</span>
    </Link>
  );
}

function metric(label: string, value: (row: LeaguePlayerRankingRow) => string): Metric {
  return { label, value };
}

function formatMinutes(value: number) {
  return `${value.toLocaleString("ko-KR")}분`;
}

function formatPercentage(value: number | null) {
  return value === null ? "-" : `${value.toFixed(1)}%`;
}

function positionLabel(value: string | null) {
  if (!value) {
    return "-";
  }
  const normalized = value.trim().toLowerCase();
  if (normalized === "goalkeeper" || normalized === "g" || normalized === "gk") {
    return "GK";
  }
  if (normalized === "defender" || normalized === "d" || normalized === "df") {
    return "DF";
  }
  if (normalized === "midfielder" || normalized === "m" || normalized === "mf") {
    return "MF";
  }
  if (normalized === "attacker" || normalized === "f" || normalized === "fw") {
    return "FW";
  }
  return value;
}
