export type FixtureSummary = {
  fixtureId: number;
  fixtureDate: string | null;
  season: number | null;
  round: number | null;
  referee: string | null;
  venueName: string | null;
  venueNameKo: string | null;
  homeTeamId: number | null;
  awayTeamId: number | null;
  homeTeamName: string | null;
  homeTeamNameKo: string | null;
  awayTeamName: string | null;
  awayTeamNameKo: string | null;
  homeTeamLogoUrl: string | null;
  awayTeamLogoUrl: string | null;
  homeScore: number | null;
  awayScore: number | null;
  fixtureStatus: string | null;
  statusShort: string | null;
  statusLong: string | null;
  elapsed: number | null;
  extra: number | null;
};

export type LiveFixtureSnapshot = {
  fixtureId: number;
  statusShort: string | null;
  statusLong: string | null;
  fixtureStatus: string | null;
  elapsed: number | null;
  extra: number | null;
  homeWinner: boolean | null;
  awayWinner: boolean | null;
  halftimeHomeScore: number | null;
  halftimeAwayScore: number | null;
  fulltimeHomeScore: number | null;
  fulltimeAwayScore: number | null;
  extratimeHomeScore: number | null;
  extratimeAwayScore: number | null;
  penaltyHomeScore: number | null;
  penaltyAwayScore: number | null;
  homeTeamStat: FixtureTeamStat | null;
  awayTeamStat: FixtureTeamStat | null;
};

export type FixtureMeta = {
  minDate: string | null;
  maxDate: string | null;
  latestStartedDate: string | null;
  minRound: number | null;
  maxRound: number | null;
};

export type LeagueSeasonCoverage = {
  leagueId: number;
  leagueName: string | null;
  seasonYear: number;
  label: string;
  startDate: string | null;
  endDate: string | null;
  currentSeason: boolean;
  events: boolean;
  lineups: boolean;
  fixtureStats: boolean;
  playerStats: boolean;
  standings: boolean;
  players: boolean;
  topScorers: boolean;
  topAssists: boolean;
  topCards: boolean;
  injuries: boolean;
  predictions: boolean;
  odds: boolean;
};

export type LeagueSeasonCoverageResponse = {
  currentSeason: number;
  seasons: LeagueSeasonCoverage[];
};

export type SyncStatusState = "NEVER_SYNCED" | "OK" | "STALE" | "FAILED" | "RETRY_PENDING";

export type SyncStatus = {
  task: string;
  label: string;
  lastSyncedAt: string | null;
  lastAttemptAt: string | null;
  lastSuccessAt: string | null;
  lastFailureAt: string | null;
  failureCount: number | null;
  lastErrorMessage: string | null;
  status: SyncStatusState | string | null;
};

export type SyncStatusResponse = {
  statuses: SyncStatus[];
};

export type LeaguePlayerRankings = {
  leagueId: number;
  season: number;
  goals: LeaguePlayerRankingRow[];
  assists: LeaguePlayerRankingRow[];
  attackPoints: LeaguePlayerRankingRow[];
  ratings: LeaguePlayerRankingRow[];
  minutes: LeaguePlayerRankingRow[];
  yellowCards: LeaguePlayerRankingRow[];
  redCards: LeaguePlayerRankingRow[];
  saves: LeaguePlayerRankingRow[];
  cleanSheets: LeaguePlayerRankingRow[];
  savePercentages: LeaguePlayerRankingRow[];
};

export type LeaguePlayerRankingRow = {
  rank: number;
  playerId: number;
  playerName: string | null;
  playerNameKo: string | null;
  photoUrl: string | null;
  position: string | null;
  teamId: number;
  teamName: string | null;
  teamNameKo: string | null;
  teamLogoUrl: string | null;
  teamRank: number | null;
  appearances: number;
  minutes: number;
  rating: number;
  goals: number;
  penaltyGoals: number;
  assists: number;
  attackPoints: number;
  goalMatches: number;
  assistMatches: number;
  attackPointMatches: number;
  yellowCards: number;
  redCards: number;
  saves: number;
  conceded: number;
  cleanSheets: number;
  savePercentage: number | null;
};

export type LeagueTeamRankings = {
  leagueId: number;
  season: number;
  goalsFor: LeagueTeamRankingRow[];
  goalsAgainst: LeagueTeamRankingRow[];
  possession: LeagueTeamRankingRow[];
  yellowCards: LeagueTeamRankingRow[];
  redCards: LeagueTeamRankingRow[];
};

export type LeagueTeamRankingRow = {
  rank: number;
  teamId: number;
  teamName: string | null;
  teamNameKo: string | null;
  teamLogoUrl: string | null;
  teamRank: number | null;
  played: number;
  goalsFor: number;
  goalsAgainst: number;
  goalsForPerMatch: number;
  goalsAgainstPerMatch: number;
  averagePossession: number | null;
  yellowCards: number;
  redCards: number;
};

export type FixtureEventResponse = {
  fixtureId: number;
  events: FixtureEvent[];
};

export type FixtureEvent = {
  sequence: number | null;
  time: {
    elapsed: number | null;
    extra: number | null;
  } | null;
  team: {
    id: number;
    name: string | null;
    logo: string | null;
  } | null;
  player: FixtureEventPlayer | null;
  assist: FixtureEventPlayer | null;
  type: string | null;
  detail: string | null;
  comments: string | null;
};

export type FixtureEventPlayer = {
  id: number;
  name: string | null;
  nameKo: string | null;
};

export type FixtureLineupResponse = {
  fixtureId: number;
  homeTeam: FixtureTeamLineup | null;
  awayTeam: FixtureTeamLineup | null;
};

export type FixtureTeamLineup = {
  teamId: number;
  teamName: string | null;
  teamNameKo: string | null;
  formation: string | null;
  coachName: string | null;
  colors: FixtureUniformColors | null;
  starters: FixtureLineupPlayer[];
  substitutes: FixtureLineupPlayer[];
  absences: FixtureLineupAbsence[];
};

export type FixtureUniformColors = {
  player: FixtureColorInfo | null;
  goalkeeper: FixtureColorInfo | null;
};

export type FixtureColorInfo = {
  primary: string | null;
  number: string | null;
  border: string | null;
};

export type FixtureLineupPlayer = {
  playerId: number;
  playerName: string | null;
  playerNameKo: string | null;
  photoUrl: string | null;
  backNumber: number | null;
  position: string | null;
  grid: string | null;
  starter: boolean;
};

export type FixtureLineupAbsence = {
  playerId: number;
  playerName: string | null;
  playerNameKo: string | null;
  teamId: number;
  teamName: string | null;
  teamNameKo: string | null;
  absenceType: string | null;
  reason: string | null;
};

export type FixtureStatResponse = {
  fixtureId: number;
  homeTeamStat: FixtureTeamStat | null;
  awayTeamStat: FixtureTeamStat | null;
};

export type FixtureTeamStat = {
  teamId: number;
  teamName: string | null;
  teamNameKo: string | null;
  score: number;
  shotsOnGoal: number;
  shotsOffGoal: number;
  totalShots: number;
  shotsOnTarget: number;
  blockedShots: number;
  shotsInsideBox: number;
  shotsOutsideBox: number;
  totalPasses: number;
  passesAccurate: number;
  passAccuracy: number;
  fouls: number;
  cornerKicks: number;
  offsides: number;
  ballPossession: number;
  goalkeeperSaves: number;
  yellowCards: number;
  redCards: number;
  expectedGoals: number | null;
};

export type FixturePlayerStatResponse = {
  fixtureId: number;
  homeTeam: FixtureTeamPlayerStats | null;
  awayTeam: FixtureTeamPlayerStats | null;
};

export type FixtureTeamPlayerStats = {
  teamId: number;
  teamName: string | null;
  teamNameKo: string | null;
  players: FixturePlayerStat[];
};

export type FixturePlayerStat = {
  playerId: number;
  playerName: string | null;
  playerNameKo: string | null;
  jerseyNumber: number | null;
  position: string | null;
  minutesPlayed: number | null;
  rating: number | null;
  captain: boolean | null;
  substitute: boolean | null;
  goals: number | null;
  assists: number | null;
  shotsTotal: number | null;
  shotsOnTarget: number | null;
  passesTotal: number | null;
  passesKey: number | null;
  tacklesTotal: number | null;
  foulsCommitted: number | null;
  yellowCards: number | null;
  redCards: number | null;
};

export type FixtureHeadToHead = {
  summary: FixtureHeadToHeadSummary;
  recentMatches: FixtureHeadToHeadMatch[];
};

export type FixtureHeadToHeadSummary = {
  homeTeamId: number | null;
  homeTeamName: string | null;
  homeTeamNameKo: string | null;
  awayTeamId: number | null;
  awayTeamName: string | null;
  awayTeamNameKo: string | null;
  matches: number;
  homeWins: number;
  draws: number;
  awayWins: number;
  homeGoals: number;
  awayGoals: number;
};

export type FixtureHeadToHeadMatch = {
  fixtureId: number;
  fixtureDate: string | null;
  season: number | null;
  round: number | null;
  homeTeamId: number | null;
  homeTeamName: string | null;
  homeTeamNameKo: string | null;
  awayTeamId: number | null;
  awayTeamName: string | null;
  awayTeamNameKo: string | null;
  homeScore: number | null;
  awayScore: number | null;
  fixtureStatus: string | null;
};

export type TeamSummary = {
  teamId: number;
  teamName: string | null;
  teamNameKo: string | null;
  code: string | null;
  logoUrl: string | null;
};

export type SearchScope = "all" | "team" | "player" | "fixture";

export type SearchResponse = {
  teams: TeamSearchResult[];
  players: PlayerSearchResult[];
  fixtures: FixtureSearchResult[];
};

export type TeamSearchResult = {
  teamId: number;
  teamName: string | null;
  teamNameKo: string | null;
  code: string | null;
  logoUrl: string | null;
};

export type PlayerSearchResult = {
  playerId: number;
  playerName: string | null;
  playerNameKo: string | null;
  position: string | null;
  photoUrl: string | null;
};

export type FixtureSearchResult = {
  fixtureId: number;
  fixtureDate: string | null;
  homeTeamName: string | null;
  homeTeamNameKo: string | null;
  awayTeamName: string | null;
  awayTeamNameKo: string | null;
  homeScore: number | null;
  awayScore: number | null;
  fixtureStatus: string | null;
};

export type TeamDetails = TeamSummary & {
  country: string | null;
  founded: number | null;
  venue: TeamVenue | null;
};

export type TeamNewsArticle = {
  articleId: number;
  originalTitle: string;
  translatedTitle: string | null;
  publisherName: string;
  originalUrl: string;
  publishedAt: string | null;
};

export type TeamNewsResponse = {
  lastCollectedAt: string | null;
  articles: TeamNewsArticle[];
};

export type TeamNewsRefreshResult = {
  collectedArticles: number;
  translationCandidates: number;
  translatedArticles: number;
  failedTranslations: number;
};

export type TeamNewsTranslationResult = {
  articleId: number;
  translatedTitle: string;
};

export type TeamVenue = {
  venueId: number | null;
  venueName: string | null;
  venueNameKo: string | null;
  venueAddress: string | null;
  venueCity: string | null;
  capacity: number | null;
  surface: string | null;
  venueImageUrl: string | null;
};

export type PlayerSummary = {
  playerId: number;
  playerName: string | null;
  playerNameKo: string | null;
  backNumber: number | null;
  position: string | null;
  photoUrl: string | null;
};

export type TeamPlayerRankings = {
  rows: TeamPlayerRanking[];
};

export type TeamPlayerRanking = {
  playerId: number;
  playerName: string | null;
  playerNameKo: string | null;
  photoUrl: string | null;
  position: string | null;
  goals: number;
  assists: number;
  rating: number;
  minutes: number;
};

export type FixtureQuery = {
  season?: number;
  round?: number;
  date?: string;
  dateFrom?: string;
  dateTo?: string;
  teamId?: number;
  cursorId?: number | null;
  size?: number;
};

export type CursorResponse<T> = {
  content: T[];
  nextCursor: number | null;
  hasNext: boolean;
};

export type StandingGoals = {
  goalsFor: number | null;
  goalsAgainst: number | null;
};

export type StandingRecord = {
  played: number | null;
  win: number | null;
  draw: number | null;
  lose: number | null;
  goals: StandingGoals | null;
};

export type RecentForm = StandingRecord & {
  points: number | null;
  goalsDiff: number | null;
  results: string[];
  matches: StandingRecentMatch[];
};

export type StandingRecentMatch = {
  fixtureId: number;
  fixtureDate: string | null;
  opponent: {
    id: number;
    name: string | null;
    nameKo: string | null;
    logo: string | null;
  } | null;
  venue: "HOME" | "AWAY";
  scoreFor: number;
  scoreAgainst: number;
  result: "W" | "D" | "L";
};

export type StandingNextMatch = {
  fixtureId: number;
  fixtureDate: string | null;
  opponent: {
    id: number;
    name: string | null;
    nameKo: string | null;
    logo: string | null;
  } | null;
  venue: "HOME" | "AWAY";
};

export type TeamStanding = {
  season: number | null;
  rank: number | null;
  team: {
    id: number;
    name: string | null;
    nameKo: string | null;
    logo: string | null;
  } | null;
  points: number | null;
  goalsDiff: number | null;
  form: string | null;
  description: string | null;
  all: StandingRecord | null;
  home: StandingRecord | null;
  away: StandingRecord | null;
  recentForm: RecentForm | null;
  liveMatch: StandingLiveMatch | null;
  nextMatch: StandingNextMatch | null;
};

export type StandingLiveMatch = {
  fixtureId: number;
  scoreFor: number;
  scoreAgainst: number;
  statusShort: string | null;
  elapsed: number | null;
  extra: number | null;
  result: "WINNING" | "DRAWING" | "LOSING";
};

export type HomeSummary = {
  todayFixtures: FixtureSummary[];
  standings: TeamStanding[];
};

export type FavoriteDashboard = {
  teams: FavoriteTeamCard[];
  players: FavoritePlayerCard[];
};

export type FavoriteTeamCard = {
  teamId: number;
  teamName: string | null;
  teamNameKo: string | null;
  logoUrl: string | null;
  rank: number | null;
  points: number | null;
  form: string | null;
  recentFixtures: FavoriteTeamFixture[];
  nextFixture: FavoriteTeamFixture | null;
  liveFixture: FavoriteLiveFixture | null;
};

export type FavoriteTeamFixture = {
  fixtureId: number;
  fixtureDate: string | null;
  homeTeamName: string | null;
  homeTeamNameKo: string | null;
  awayTeamName: string | null;
  awayTeamNameKo: string | null;
  homeScore: number | null;
  awayScore: number | null;
  fixtureStatus: string | null;
  result: string | null;
};

export type FavoriteLiveFixture = FavoriteTeamFixture & {
  statusShort: string | null;
  statusLong: string | null;
  elapsed: number | null;
};

export type FavoritePlayerCard = {
  playerId: number;
  playerName: string | null;
  playerNameKo: string | null;
  photoUrl: string | null;
  position: string | null;
  recentMatch: FavoriteRecentPlayerMatch | null;
  seasonStat: FavoritePlayerSeasonStat | null;
};

export type FavoriteRecentPlayerMatch = {
  fixtureId: number;
  fixtureDate: string | null;
  teamName: string | null;
  teamNameKo: string | null;
  opponentTeamName: string | null;
  opponentTeamNameKo: string | null;
  teamScore: number | null;
  opponentScore: number | null;
  minutesPlayed: number | null;
  rating: number | null;
  goals: number | null;
  assists: number | null;
};

export type FavoritePlayerSeasonStat = {
  season: number | null;
  teamName: string | null;
  teamNameKo: string | null;
  teamLogoUrl: string | null;
  teamCount: number | null;
  aggregated: boolean | null;
  appearances: number | null;
  minutes: number | null;
  rating: number | null;
  goals: number | null;
  assists: number | null;
  yellowCards: number | null;
  redCards: number | null;
};

export type PlayerPanel = {
  profile: PlayerProfile | null;
  seasons: PlayerSeasonSummary[];
  matches: PlayerMatchStat[];
};

export type PlayerProfile = {
  playerId: number;
  playerName: string | null;
  playerNameKo: string | null;
  firstname: string | null;
  lastname: string | null;
  backNumber: number | null;
  age: number | null;
  birthDate: string | null;
  birthPlace: string | null;
  birthCountry: string | null;
  nationality: string | null;
  height: string | null;
  weight: string | null;
  position: string | null;
  photoUrl: string | null;
  teamId: number | null;
  teamName: string | null;
  teamNameKo: string | null;
  teamLogoUrl: string | null;
};

export type PlayerSeasonSummary = {
  season: number | null;
  totalFixtures: number;
  minutesPlayed: number;
  averageRating: number;
  cleanSheets: number;
  conceded: number;
  saves: number;
  goals: number;
  assists: number;
  shots: number;
  shotsOnTarget: number;
  keyPasses: number;
  yellowCards: number;
  redCards: number;
  teams: PlayerTeamSeasonSummary[];
};

export type PlayerTeamSeasonSummary = {
  teamId: number;
  teamName: string | null;
  teamNameKo: string | null;
  teamLogoUrl: string | null;
  totalFixtures: number;
  minutesPlayed: number;
  averageRating: number;
  cleanSheets: number;
  conceded: number;
  saves: number;
  goals: number;
  assists: number;
  shots: number;
  shotsOnTarget: number;
  keyPasses: number;
  yellowCards: number;
  redCards: number;
};

export type PlayerMatchStat = {
  fixtureId: number;
  fixtureDate: string | null;
  season: number | null;
  round: number | null;
  teamId: number | null;
  teamName: string | null;
  teamNameKo: string | null;
  opponentTeamId: number | null;
  opponentTeamName: string | null;
  opponentTeamNameKo: string | null;
  teamScore: number | null;
  opponentScore: number | null;
  homeTeamId: number | null;
  homeTeamName: string | null;
  homeTeamNameKo: string | null;
  awayTeamId: number | null;
  awayTeamName: string | null;
  awayTeamNameKo: string | null;
  homeScore: number | null;
  awayScore: number | null;
  minutesPlayed: number | null;
  rating: number | null;
  goals: number | null;
  assists: number | null;
};

export type CurrentUser = {
  id: number;
  email: string;
  nickname: string | null;
  role: string;
};

export class ApiError extends Error {
  status: number;

  constructor(message: string, status: number) {
    super(message);
    this.status = status;
  }
}

const SHORT_CACHE_TTL_MS = 60_000;
const DETAIL_CACHE_TTL_MS = 5 * 60_000;
const DEFAULT_API_SEASON = 2025;

type CacheEntry<T> = {
  expiresAt: number;
  value: T;
};

const memoryCache = new Map<string, CacheEntry<unknown>>();
const pendingRequests = new Map<string, Promise<unknown>>();

export function clearApiMemoryCache() {
  memoryCache.clear();
  pendingRequests.clear();
}

export async function fetchHomeSummary(season: number): Promise<HomeSummary> {
  const response = await fetch(`/api/v1/home/summary?season=${normalizeSeason(season)}`, {
    headers: {
      Accept: "application/json",
    },
    credentials: "same-origin",
  });

  if (!response.ok) {
    throw new ApiError(`홈 정보를 불러오지 못했습니다. (${response.status})`, response.status);
  }

  return response.json();
}

export async function fetchStandings(
  season: number,
  options: { fresh?: boolean } = {},
): Promise<TeamStanding[]> {
  const url = `/api/v1/teams/standings?season=${normalizeSeason(season)}`;
  return options.fresh
    ? fetchFreshJsonWithCacheFallback<TeamStanding[]>(
        url,
        "순위 정보를 불러오지 못했습니다.",
        DETAIL_CACHE_TTL_MS,
      )
    : cachedGetJson<TeamStanding[]>(url, "순위 정보를 불러오지 못했습니다.", DETAIL_CACHE_TTL_MS);

}

export async function fetchFixturesByDate(
  season: number,
  dateKey: string,
): Promise<CursorResponse<FixtureSummary>> {
  return fetchFixtures({ season, date: dateKey, size: 100 });
}

export async function fetchFixtures(query: FixtureQuery): Promise<CursorResponse<FixtureSummary>> {
  const params = new URLSearchParams();
  appendParam(params, "season", query.season);
  appendParam(params, "round", query.round);
  appendParam(params, "date", query.date);
  appendParam(params, "dateFrom", query.dateFrom);
  appendParam(params, "dateTo", query.dateTo);
  appendParam(params, "teamId", query.teamId);
  appendParam(params, "cursorId", query.cursorId ?? undefined);
  appendParam(params, "size", query.size);

  return cachedGetJson<CursorResponse<FixtureSummary>>(
    `/api/v1/fixtures?${params.toString()}`,
    "경기 일정을 불러오지 못했습니다.",
    SHORT_CACHE_TTL_MS,
  );

}

export async function fetchFixture(fixtureId: number, options: { fresh?: boolean } = {}): Promise<FixtureSummary> {
  const url = `/api/v1/fixtures/${fixtureId}`;
  return options.fresh
    ? fetchJson<FixtureSummary>(url, "경기 정보를 불러오지 못했습니다.")
    : cachedGetJson<FixtureSummary>(url, "경기 정보를 불러오지 못했습니다.", DETAIL_CACHE_TTL_MS);

}

export async function fetchFixtureHeadToHead(fixtureId: number): Promise<FixtureHeadToHead> {
  return cachedGetJson<FixtureHeadToHead>(
    `/api/v1/fixtures/${fixtureId}/head-to-head`,
    "최근 전적을 불러오지 못했습니다.",
    DETAIL_CACHE_TTL_MS,
  );
}

export async function fetchFixtureMeta(season: number): Promise<FixtureMeta> {
  return cachedGetJson<FixtureMeta>(
    `/api/v1/fixtures/meta?season=${normalizeSeason(season)}`,
    "경기 범위를 불러오지 못했습니다.",
    DETAIL_CACHE_TTL_MS,
  );

}

export async function fetchFixtureEvents(fixtureId: number): Promise<FixtureEventResponse> {
  return fetchJson<FixtureEventResponse>(
    `/api/v1/fixtures/${fixtureId}/events`,
    "경기 이벤트를 불러오지 못했습니다.",
  );

}

export async function fetchFixtureLineups(fixtureId: number): Promise<FixtureLineupResponse> {
  return fetchJson<FixtureLineupResponse>(
    `/api/v1/fixtures/${fixtureId}/lineups`,
    "라인업을 불러오지 못했습니다.",
  );

}

export async function fetchFixtureStats(fixtureId: number, options: { fresh?: boolean } = {}): Promise<FixtureStatResponse> {
  const url = `/api/v1/fixtures/${fixtureId}/stats`;
  return options.fresh
    ? fetchJson<FixtureStatResponse>(url, "팀 통계를 불러오지 못했습니다.")
    : cachedGetJson<FixtureStatResponse>(url, "팀 통계를 불러오지 못했습니다.", SHORT_CACHE_TTL_MS);
}

export async function fetchFixturePlayerStats(fixtureId: number): Promise<FixturePlayerStatResponse> {
  return fetchJson<FixturePlayerStatResponse>(
    `/api/v1/fixtures/${fixtureId}/player-stats`,
    "선수별 경기 통계를 불러오지 못했습니다.",
  );
}

export async function fetchTeams(): Promise<TeamSummary[]> {
  return cachedGetJson<TeamSummary[]>("/api/v1/teams", "팀 목록을 불러오지 못했습니다.", DETAIL_CACHE_TTL_MS);

}

export async function searchGlobal(keyword: string, type: SearchScope): Promise<SearchResponse> {
  const params = new URLSearchParams();
  params.set("q", keyword);
  params.set("type", type);

  return fetchJson<SearchResponse>(`/api/v1/search?${params.toString()}`, "검색 결과를 불러오지 못했습니다.");
}

export async function fetchTeamDetails(teamId: number): Promise<TeamDetails> {
  return cachedGetJson<TeamDetails>(
    `/api/v1/teams/${teamId}`,
    "팀 정보를 불러오지 못했습니다.",
    DETAIL_CACHE_TTL_MS,
  );
}

export async function fetchTeamNews(teamId: number): Promise<TeamNewsResponse> {
  return cachedGetJson<TeamNewsResponse>(
    `/api/v1/teams/${teamId}/news`,
    "팀 뉴스를 불러오지 못했습니다.",
    DETAIL_CACHE_TTL_MS,
  );
}

export async function refreshTeamNews(teamId: number): Promise<TeamNewsRefreshResult> {
  const result = await postJson(`/api/v1/admin/teams/${teamId}/news/refresh`, {}) as TeamNewsRefreshResult;
  clearApiMemoryCache();
  return result;
}

export async function translateTeamNewsArticle(teamId: number, articleId: number): Promise<TeamNewsTranslationResult> {
  return await postJson(`/api/v1/admin/teams/${teamId}/news/${articleId}/translate`, {}) as TeamNewsTranslationResult;
}

export async function fetchTeamPlayers(teamId: number, season: number): Promise<PlayerSummary[]> {
  return cachedGetJson<PlayerSummary[]>(
    `/api/v1/teams/${teamId}/players?season=${normalizeSeason(season)}`,
    "팀 선수 목록을 불러오지 못했습니다.",
    DETAIL_CACHE_TTL_MS,
  );
}

export async function fetchTeamPlayerRankings(teamId: number, season: number): Promise<TeamPlayerRankings> {
  return cachedGetJson<TeamPlayerRankings>(
    `/api/v1/teams/${teamId}/player-rankings?season=${normalizeSeason(season)}`,
    "팀 선수 통계를 불러오지 못했습니다.",
    DETAIL_CACHE_TTL_MS,
  );
}

export async function login(email: string, password: string): Promise<CurrentUser> {
  const user = parseCurrentUser(await postJson("/api/v1/auth/login", { email, password }));
  clearApiMemoryCache();
  return user;
}

export async function signup(email: string, password: string, nickname: string): Promise<CurrentUser> {
  const user = parseCurrentUser(await postJson("/api/v1/auth/signup", { email, password, nickname }));
  clearApiMemoryCache();
  return user;
}

export async function logout(): Promise<void> {
  const response = await fetch("/api/v1/auth/logout", {
    method: "POST",
    headers: {
      Accept: "application/json",
    },
    credentials: "same-origin",
  });

  if (!response.ok) {
    throw await responseError(response, `로그아웃에 실패했습니다. (${response.status})`);
  }
  clearApiMemoryCache();
}

export async function fetchCurrentUser(): Promise<CurrentUser> {
  const response = await fetch("/api/v1/auth/me", {
    headers: {
      Accept: "application/json",
    },
    credentials: "same-origin",
  });

  if (!response.ok) {
    throw await responseError(response, `로그인이 필요합니다. (${response.status})`);
  }

  return parseCurrentUser(await responseJson(response, "로그인 사용자 정보가 올바르지 않습니다."));
}

export async function fetchPlayerPanel(playerId: number): Promise<PlayerPanel> {
  return cachedGetJson<PlayerPanel>(
    `/api/v1/players/${playerId}/panel`,
    "선수 정보를 불러오지 못했습니다.",
    DETAIL_CACHE_TTL_MS,
  );
}


export async function fetchPlayerDetails(playerId: number): Promise<PlayerProfile> {
  return cachedGetJson<PlayerProfile>(
    `/api/v1/players/${playerId}`,
    "선수 프로필을 불러오지 못했습니다.",
    DETAIL_CACHE_TTL_MS,
  );
}

export async function fetchPlayerSeasonSummary(playerId: number, season: number): Promise<PlayerSeasonSummary> {
  return cachedGetJson<PlayerSeasonSummary>(
    `/api/v1/players/${playerId}/season-summary?season=${normalizeSeason(season)}`,
    "선수 시즌 스탯을 불러오지 못했습니다.",
    DETAIL_CACHE_TTL_MS,
  );
}

export async function fetchPlayerRecentMatches(playerId: number, season: number, size = 8): Promise<PlayerMatchStat[]> {
  const normalizedSize = normalizePageSize(size, 8, 1, 30);
  return cachedGetJson<PlayerMatchStat[]>(
    `/api/v1/players/${playerId}/recent-matches?season=${normalizeSeason(season)}&size=${normalizedSize}`,
    "선수 최근 경기를 불러오지 못했습니다.",
    SHORT_CACHE_TTL_MS,
  );
}

async function cachedGetJson<T>(url: string, fallbackMessage: string, ttlMs: number): Promise<T> {
  const now = Date.now();
  const cached = memoryCache.get(url) as CacheEntry<T> | undefined;
  if (cached && cached.expiresAt > now) {
    return cached.value;
  }

  const pending = pendingRequests.get(url) as Promise<T> | undefined;
  if (pending) {
    return pending;
  }

  const request = fetchJson<T>(url, fallbackMessage)
    .then((value) => {
      memoryCache.set(url, {
        expiresAt: Date.now() + ttlMs,
        value,
      });
      return value;
    })
    .finally(() => {
      pendingRequests.delete(url);
    });

  pendingRequests.set(url, request);
  return request;
}

async function fetchJson<T>(url: string, fallbackMessage: string): Promise<T> {
  const response = await fetch(url, {
    headers: {
      Accept: "application/json",
    },
    credentials: "same-origin",
  });

  if (!response.ok) {
    throw new ApiError(`${fallbackMessage} (${response.status})`, response.status);
  }

  return response.json();
}

async function fetchFreshJson<T>(url: string, fallbackMessage: string): Promise<T> {
  const response = await fetch(url, {
    headers: {
      Accept: "application/json",
    },
    credentials: "same-origin",
    cache: "no-store",
  });

  if (!response.ok) {
    throw new ApiError(`${fallbackMessage} (${response.status})`, response.status);
  }

  return response.json();
}

async function fetchFreshJsonWithCacheFallback<T>(
  url: string,
  fallbackMessage: string,
  ttlMs: number,
): Promise<T> {
  const cached = memoryCache.get(url) as CacheEntry<T> | undefined;

  try {
    const value = await fetchFreshJson<T>(url, fallbackMessage);
    memoryCache.set(url, {
      expiresAt: Date.now() + ttlMs,
      value,
    });
    return value;
  } catch (error) {
    if (cached) {
      return cached.value;
    }
    throw error;
  }
}
    
export async function updateNickname(nickname: string): Promise<CurrentUser> {
  return parseCurrentUser(await patchJson("/api/v1/auth/me/nickname", { nickname }));
}

export async function changePassword(currentPassword: string, newPassword: string): Promise<void> {
  await patchJson("/api/v1/auth/me/password", { currentPassword, newPassword }, true);
}

export async function deleteAccount(currentPassword: string): Promise<void> {
  await deleteJson("/api/v1/auth/me", { currentPassword });
}

export async function fetchFavoriteDashboard(season: number): Promise<FavoriteDashboard> {
  const response = await fetch(`/api/v1/favorites/dashboard?season=${normalizeSeason(season)}`, {
    headers: {
      Accept: "application/json",
    },
    credentials: "same-origin",
  });

  if (!response.ok) {
    throw await responseError(response, `즐겨찾기 요청에 실패했습니다. (${response.status})`);
  }

  return response.json();
}

export async function fetchLeagueSeasons(leagueId = 39): Promise<LeagueSeasonCoverageResponse> {
  return cachedGetJson<LeagueSeasonCoverageResponse>(
    `/api/v1/leagues/${leagueId}/seasons`,
    "시즌 정보를 불러오지 못했습니다.",
    DETAIL_CACHE_TTL_MS,
  );
}

export async function fetchSyncStatuses(season: number): Promise<SyncStatusResponse> {
  return fetchJson<SyncStatusResponse>(
    `/api/v1/sync/statuses?season=${normalizeSeason(season)}`,
    "Failed to load sync status.",
  );
}

export async function requestAdminSync(task: "standings" | "fixtures", season: number): Promise<void> {
  const response = await postJson(
    `/api/v1/admin/sync/${task}?league=39&season=${normalizeSeason(season)}`,
    {},
  ) as { success?: boolean; message?: string };
  if (!response.success) {
    throw new Error(response.message || "데이터 동기화에 실패했습니다.");
  }
}

export async function fetchLeaguePlayerRankings(
  season: number,
  leagueId = 39,
): Promise<LeaguePlayerRankings> {
  return cachedGetJson<LeaguePlayerRankings>(
    `/api/v1/leagues/${leagueId}/player-rankings?season=${normalizeSeason(season)}`,
    "플레이어 통계 순위를 불러오지 못했습니다.",
    SHORT_CACHE_TTL_MS,
  );
}

export async function fetchLeagueTeamRankings(
  season: number,
  leagueId = 39,
): Promise<LeagueTeamRankings> {
  return cachedGetJson<LeagueTeamRankings>(
    `/api/v1/leagues/${leagueId}/team-rankings?season=${normalizeSeason(season)}`,
    "팀 통계 순위를 불러오지 못했습니다.",
    SHORT_CACHE_TTL_MS,
  );
}

export async function addFavoriteTeam(teamId: number, season: number): Promise<FavoriteDashboard> {
  return requestFavoriteDashboard(`/api/v1/favorites/teams/${teamId}?season=${normalizeSeason(season)}`, "POST");
}

export async function removeFavoriteTeam(teamId: number, season: number): Promise<FavoriteDashboard> {
  return requestFavoriteDashboard(`/api/v1/favorites/teams/${teamId}?season=${normalizeSeason(season)}`, "DELETE");
}

export async function addFavoritePlayer(playerId: number, season: number): Promise<FavoriteDashboard> {
  return requestFavoriteDashboard(`/api/v1/favorites/players/${playerId}?season=${normalizeSeason(season)}`, "POST");
}

export async function removeFavoritePlayer(playerId: number, season: number): Promise<FavoriteDashboard> {
  return requestFavoriteDashboard(`/api/v1/favorites/players/${playerId}?season=${normalizeSeason(season)}`, "DELETE");
}

async function requestFavoriteDashboard(url: string, method: "POST" | "DELETE"): Promise<FavoriteDashboard> {
  const response = await fetch(url, {
    method,
    headers: {
      Accept: "application/json",
    },
    credentials: "same-origin",
  });

  if (!response.ok) {
    throw await responseError(response, `즐겨찾기 요청에 실패했습니다. (${response.status})`);
  }

  const dashboard = await response.json();
  clearApiMemoryCache();
  return dashboard;
}

function appendParam(params: URLSearchParams, key: string, value: string | number | undefined) {
  if (value !== undefined && value !== null && value !== "") {
    params.set(key, String(key === "season" && typeof value === "number" ? normalizeSeason(value) : value));
  }
}

function normalizeSeason(value: number) {
  return Number.isInteger(value) && value >= 2000 && value <= 2100 ? value : DEFAULT_API_SEASON;
}

function normalizePageSize(value: number, fallback: number, min: number, max: number) {
  if (!Number.isFinite(value)) {
    return fallback;
  }
  return Math.min(max, Math.max(min, Math.trunc(value)));
}

async function postJson(url: string, body: unknown): Promise<unknown> {
  const response = await fetch(url, {
    method: "POST",
    headers: {
      Accept: "application/json",
      "Content-Type": "application/json",
    },
    credentials: "same-origin",
    body: JSON.stringify(body),
  });

  if (!response.ok) {
    throw await responseError(response, `${url} 요청에 실패했습니다. (${response.status})`);
  }

  return responseJson(response, `${url} 응답이 비어 있습니다.`);
}

async function patchJson(url: string, body: unknown, allowEmpty = false): Promise<unknown> {
  const response = await fetch(url, {
    method: "PATCH",
    headers: {
      Accept: "application/json",
      "Content-Type": "application/json",
    },
    credentials: "same-origin",
    body: JSON.stringify(body),
  });

  if (!response.ok) {
    throw await responseError(response, `${url} 요청에 실패했습니다. (${response.status})`);
  }

  if (allowEmpty && response.status === 204) {
    return null;
  }

  return responseJson(response, `${url} 응답이 비어 있습니다.`);
}

async function deleteJson(url: string, body?: unknown): Promise<unknown> {
  const response = await fetch(url, {
    method: "DELETE",
    headers: {
      Accept: "application/json",
      ...(body === undefined ? {} : { "Content-Type": "application/json" }),
    },
    credentials: "same-origin",
    body: body === undefined ? undefined : JSON.stringify(body),
  });

  if (!response.ok) {
    throw await responseError(response, `${url} 요청에 실패했습니다. (${response.status})`);
  }

  if (response.status === 204) {
    return null;
  }

  return responseJson(response, `${url} 응답이 비어 있습니다.`);
}

async function responseError(response: Response, fallbackMessage: string) {
  try {
    const errorBody = (await response.json()) as { message?: string };
    return new ApiError(errorBody.message || fallbackMessage, response.status);
  } catch {
    return new ApiError(fallbackMessage, response.status);
  }
}

async function responseJson(response: Response, emptyMessage: string): Promise<unknown> {
  if (response.status === 204) {
    throw new ApiError(emptyMessage, response.status);
  }

  try {
    return await response.json();
  } catch {
    throw new ApiError(emptyMessage, response.status);
  }
}

function parseCurrentUser(value: unknown): CurrentUser {
  if (
    !isRecord(value)
    || typeof value.id !== "number"
    || typeof value.email !== "string"
    || typeof value.role !== "string"
  ) {
    throw new ApiError("로그인 사용자 정보가 올바르지 않습니다.", 500);
  }

  return {
    id: value.id,
    email: value.email,
    nickname: typeof value.nickname === "string" ? value.nickname : null,
    role: value.role,
  };
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null;
}
