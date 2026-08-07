import { FormEvent, useEffect, useRef, useState } from "react";
import { ChevronDown, LoaderCircle, X } from "lucide-react";
import { Navigate, NavLink, useLocation, useSearchParams } from "react-router-dom";
import {
  clearApiMemoryCache,
  fetchFixtures,
  fetchLeagueSeasons,
  fetchTeamPlayers,
  type LeagueSeasonCoverage,
  type PlayerSummary,
} from "../api";
import { type LeagueAuthState } from "../App";

type AdminPageProps = {
  authState: LeagueAuthState;
};

type AdminTab = "team" | "player" | "fixture";
type AdminSection = "editor" | "sync" | "logs";
type AdminMediaTargetType = "PLAYER_PHOTO" | "TEAM_LOGO" | "VENUE_IMAGE";
type AdminMediaToast = {
  message: string;
  type: "success" | "error";
};

type AdminMediaPresignResponse = {
  objectKey: string;
  uploadUrl: string;
  requiredHeaders: Record<string, string>;
  expiresAt: string;
};

type AdminMediaResponse = {
  targetType: AdminMediaTargetType;
  targetId: number;
  objectKey: string | null;
  publicUrl: string | null;
  adminImage: boolean;
};

function adminTab(value: string | null): AdminTab | null {
  return value === "team" || value === "player" || value === "fixture" ? value : null;
}

function adminSection(pathname: string): AdminSection | null {
  const normalizedPath = pathname.replace(/\/+$/, "") || "/";
  if (normalizedPath === "/admin/editor") return "editor";
  if (normalizedPath === "/admin/sync") return "sync";
  if (normalizedPath === "/admin/logs") return "logs";
  return null;
}

type FieldKind = "text" | "number" | "datetime" | "date" | "boolean" | "select";

type FieldOption = {
  label: string;
  value: string | number;
};

type FieldConfig = {
  name: string;
  label: string;
  kind?: FieldKind;
  help?: string;
  min?: number;
  max?: number;
  options?: FieldOption[];
  preview?: "color";
};

type AdminOverride = {
  fieldName: string;
  updatedAt: string | null;
};

type TeamAdmin = Record<string, unknown> & {
  teamId: number;
  version: number;
  name: string | null;
  koreanName: string | null;
  logoDisplayUrl: string | null;
  adminLogo: boolean;
  venueId: number | null;
  venueImageDisplayUrl: string | null;
  adminVenueImage: boolean;
  manualOverrides?: AdminOverride[];
};

type TeamSelectionState = {
  teamId: number | null;
  status: "idle" | "loading" | "ready" | "error";
  detail: TeamAdmin | null;
};

type PlayerAdmin = Record<string, unknown> & {
  playerId: number;
  version: number;
  name: string | null;
  koreanName: string | null;
  photoDisplayUrl: string | null;
  adminPhoto: boolean;
  manualOverrides?: AdminOverride[];
};

type PlayerSelectionState = {
  playerId: number | null;
  status: "idle" | "loading" | "ready" | "error";
  detail: PlayerAdmin | null;
};

type FixtureSummaryAdmin = {
  fixtureId: number;
  fixtureDate: string | null;
  season: number | null;
  round: number | null;
  homeTeamName: string | null;
  awayTeamName: string | null;
  homeScore: number | null;
  awayScore: number | null;
  fixtureStatus: string | null;
};

type FixtureDetailAdmin = {
  fixture: FixtureAdmin;
  eventOverrides?: AdminOverride[];
  events: FixtureEventAdmin[];
  lineups: FixtureLineupAdmin[];
  teamStats: FixtureTeamStatAdmin[];
  playerStats: FixturePlayerStatAdmin[];
};

type FixtureSelectionState = {
  fixtureId: number | null;
  status: "idle" | "loading" | "ready" | "error";
  detail: FixtureDetailAdmin | null;
};

type FixtureListStatus = "idle" | "loading" | "ready" | "error";

type FixtureTeamOption = {
  teamId: number;
  name: string | null;
  koreanName: string | null;
};

type FixtureAdmin = Record<string, unknown> & {
  fixtureId: number;
  version: number;
  fixtureStatus: string | null;
  statusShort: string | null;
  homeTeamId: number | null;
  homeTeamName: string | null;
  awayTeamId: number | null;
  awayTeamName: string | null;
  manualOverrides?: AdminOverride[];
};

type FixtureEventAdmin = Record<string, unknown> & {
  eventSequence: number;
  playerName: string | null;
  playerNameKo: string | null;
  eventType: string | null;
  eventDetail: string | null;
};

type FixtureLineupAdmin = Record<string, unknown> & {
  lineupId: number;
  version: number;
  teamId: number;
  teamName: string | null;
  teamNameKo: string | null;
  playerId: number;
  playerName: string | null;
  playerNameKo: string | null;
  manualOverrides?: AdminOverride[];
};

type FixtureTeamStatAdmin = Record<string, unknown> & {
  teamId: number;
  version: number;
  teamName: string | null;
  manualOverrides?: AdminOverride[];
};

type FixturePlayerStatAdmin = Record<string, unknown> & {
  playerId: number;
  version: number;
  teamId: number;
  playerName: string | null;
  playerNameKo: string | null;
  teamName: string | null;
  teamNameKo: string | null;
  manualOverrides?: AdminOverride[];
};

type AuditLog = {
  id: number;
  adminEmail: string | null;
  type: string;
  syncCategory: string | null;
  targetType: string | null;
  targetId: number | null;
  message: string;
  details: string | null;
  provider: string | null;
  success: boolean;
  createdAt: string | null;
};

type SyncStatus = {
  task: string;
  label: string;
  lastSyncedAt: string | null;
  lastAttemptAt: string | null;
  lastSuccessAt: string | null;
  lastFailureAt: string | null;
  failureCount: number | null;
  lastErrorMessage: string | null;
  status: string | null;
  provider: string | null;
  lastOperation: string | null;
  lastErrorCategory: string | null;
  lastHttpStatus: number | null;
  lastAttemptCount: number | null;
};

type CoverageStatus = "loading" | "ready" | "error";
type LoadStatus = "idle" | "loading" | "ready" | "error";
type SyncJobStatus = "QUEUED" | "RUNNING" | "CANCEL_REQUESTED" | "CANCELLED" | "SUCCEEDED" | "PARTIAL_FAILED" | "FAILED";

type SyncJobError = {
  unitType: string;
  unitId: string | null;
  message: string;
  createdAt: string | null;
};

type SyncJob = {
  id: number;
  task: string;
  adminEmail: string | null;
  targetType: string | null;
  targetId: number | null;
  season: number | null;
  details: string | null;
  status: SyncJobStatus;
  active: boolean;
  totalUnits: number;
  processedUnits: number;
  successfulUnits: number;
  failedUnits: number;
  savedCount: number;
  phase: string | null;
  unitLabel: string | null;
  message: string;
  createdAt: string | null;
  startedAt: string | null;
  completedAt: string | null;
  errors: SyncJobError[];
};

type AdminSyncResponse = {
  jobId?: number;
  task?: string;
  success?: boolean;
  queued?: boolean;
  message: string;
};

type AuditLogPage = {
  logs: AuditLog[];
  page: number;
  size: number;
  totalPages: number;
  totalElements: number;
};

const AUDIT_LOG_PAGE_SIZE = 20;
const SYNC_JOB_POLL_INTERVAL_MS = 2_000;
const SYNC_JOB_RETRY_INTERVAL_MS = 5_000;
const ADMIN_SEARCH_KEYWORD_MAX_LENGTH = 80;
const MANUAL_SYNC_COOLDOWN_MS = 30_000;
const SYNC_STATUS_REQUEST_TIMEOUT_MS = 30_000;
const MANUAL_SYNC_COOLDOWN_STORAGE_PREFIX = "admin-sync-cooldown";
const ADMIN_IMAGE_MAX_BYTES = 2 * 1024 * 1024;
const ADMIN_IMAGE_CONTENT_TYPES = new Set(["image/png", "image/jpeg", "image/webp"]);

function syncCooldownStorageKey(cooldownKey: string) {
  return `${MANUAL_SYNC_COOLDOWN_STORAGE_PREFIX}:${cooldownKey}`;
}

function readStoredSyncCooldowns(): Record<string, number> {
  const cooldowns: Record<string, number> = {};
  const now = Date.now();
  const storagePrefix = `${MANUAL_SYNC_COOLDOWN_STORAGE_PREFIX}:`;
  try {
    for (let index = window.localStorage.length - 1; index >= 0; index -= 1) {
      const storageKey = window.localStorage.key(index);
      if (!storageKey?.startsWith(storagePrefix)) {
        continue;
      }
      const cooldownUntil = Number(window.localStorage.getItem(storageKey));
      if (Number.isFinite(cooldownUntil) && cooldownUntil > now) {
        cooldowns[storageKey.slice(storagePrefix.length)] = cooldownUntil;
      } else {
        window.localStorage.removeItem(storageKey);
      }
    }
  } catch {
    // Keep the page usable when localStorage is unavailable.
  }
  return cooldowns;
}

function storeSyncCooldown(cooldownKey: string, cooldownUntil: number) {
  try {
    window.localStorage.setItem(syncCooldownStorageKey(cooldownKey), String(cooldownUntil));
  } catch {
    // The in-memory cooldown still applies while this page remains open.
  }
}

function removeStoredSyncCooldown(cooldownKey: string) {
  try {
    window.localStorage.removeItem(syncCooldownStorageKey(cooldownKey));
  } catch {
    // Expired in-memory cooldowns can still be removed when storage is unavailable.
  }
}

const teamFields: FieldConfig[] = [
  { name: "name", label: "이름" },
  { name: "koreanName", label: "한글 이름" },
  { name: "code", label: "팀 코드" },
  { name: "country", label: "국가" },
  { name: "founded", label: "창단 연도", kind: "number" },
  { name: "logoUrl", label: "로고 URL" },
  { name: "venueName", label: "경기장 이름" },
  { name: "venueNameKo", label: "경기장 한글 이름" },
  { name: "venueAddress", label: "경기장 주소" },
  { name: "venueCity", label: "경기장 도시" },
  { name: "capacity", label: "수용 인원", kind: "number" },
  { name: "surface", label: "경기장 표면" },
  { name: "venueImageUrl", label: "경기장 이미지 URL" },
];

const playerFields: FieldConfig[] = [
  { name: "name", label: "이름" },
  { name: "koreanName", label: "한글 이름" },
  { name: "firstname", label: "이름 부분" },
  { name: "lastname", label: "성" },
  { name: "age", label: "나이", kind: "number" },
  { name: "birthDate", label: "생년월일", kind: "date" },
  { name: "birthPlace", label: "출생지" },
  { name: "birthCountry", label: "출생 국가" },
  { name: "nationality", label: "국적" },
  { name: "height", label: "키", kind: "number" },
  { name: "weight", label: "몸무게", kind: "number" },
  { name: "position", label: "포지션" },
  { name: "number", label: "등번호", kind: "number" },
  { name: "photoUrl", label: "사진 URL" },
];

const fixtureFields: FieldConfig[] = [
  { name: "fixtureDate", label: "경기 일시", kind: "datetime", help: "한국 표준시 기준으로 입력됩니다." },
  { name: "referee", label: "주심" },
  { name: "venueId", label: "경기장 ID", kind: "number" },
  { name: "venueName", label: "경기장 이름" },
  { name: "venueNameKo", label: "경기장 한글 이름" },
  { name: "venueCity", label: "경기장 도시" },
  { name: "homeFormation", label: "홈팀 포메이션" },
  { name: "awayFormation", label: "원정팀 포메이션" },
  { name: "homeCoachName", label: "홈팀 감독" },
  { name: "awayCoachName", label: "원정팀 감독" },
  { name: "homePlayerColorPrimary", label: "홈팀 선수 유니폼", preview: "color" },
  { name: "homePlayerColorNumber", label: "홈팀 선수 등번호", preview: "color" },
  { name: "homePlayerColorBorder", label: "홈팀 선수 테두리", preview: "color" },
  { name: "homeGoalkeeperColorPrimary", label: "홈팀 골키퍼 유니폼", preview: "color" },
  { name: "homeGoalkeeperColorNumber", label: "홈팀 골키퍼 등번호", preview: "color" },
  { name: "homeGoalkeeperColorBorder", label: "홈팀 골키퍼 테두리", preview: "color" },
  { name: "awayPlayerColorPrimary", label: "원정팀 선수 유니폼", preview: "color" },
  { name: "awayPlayerColorNumber", label: "원정팀 선수 등번호", preview: "color" },
  { name: "awayPlayerColorBorder", label: "원정팀 선수 테두리", preview: "color" },
  { name: "awayGoalkeeperColorPrimary", label: "원정팀 골키퍼 유니폼", preview: "color" },
  { name: "awayGoalkeeperColorNumber", label: "원정팀 골키퍼 등번호", preview: "color" },
  { name: "awayGoalkeeperColorBorder", label: "원정팀 골키퍼 테두리", preview: "color" },
];

const eventFields: FieldConfig[] = [
  { name: "teamId", label: "팀", kind: "select" },
  { name: "playerId", label: "선수", kind: "select" },
  { name: "assistPlayerId", label: "도움 선수", kind: "select" },
  { name: "elapsed", label: "경기 시간", kind: "number", min: 0, max: 90 },
  { name: "extra", label: "추가 시간", kind: "number", min: 0, max: 20 },
  { name: "eventType", label: "유형", kind: "select" },
  { name: "eventDetail", label: "상세 유형", kind: "select" },
  { name: "comments", label: "설명" },
];

const eventOverrideFields: FieldConfig[] = [
  { name: "events", label: "경기 이벤트 전체" },
];

const eventTypeOptions: FieldOption[] = [
  { value: "Goal", label: "득점" },
  { value: "Card", label: "카드" },
  { value: "Subst", label: "교체" },
  { value: "Var", label: "VAR" },
];

const eventDetailOptionsByType: Record<string, FieldOption[]> = {
  Goal: [
    { value: "Normal Goal", label: "일반 득점" },
    { value: "Own Goal", label: "자책골" },
    { value: "Penalty", label: "페널티골" },
    { value: "Missed Penalty", label: "페널티 실축" },
  ],
  Card: [
    { value: "Yellow Card", label: "옐로카드" },
    { value: "Red card", label: "레드카드" },
  ],
  Subst: Array.from({ length: 10 }, (_, index) => {
    const value = `Substitution ${index + 1}`;
    return { value, label: `${index + 1}번째 교체` };
  }),
  Var: [
    { value: "Goal cancelled", label: "득점 취소" },
    { value: "Penalty confirmed", label: "페널티 확정" },
  ],
};

const lineupFields: FieldConfig[] = [
  { name: "jerseyNumber", label: "등번호", kind: "number", min: 0, max: 99 },
  { name: "position", label: "포지션" },
  { name: "grid", label: "배치 위치" },
  { name: "starter", label: "선발 여부", kind: "boolean" },
];

const teamStatFields: FieldConfig[] = [
  { name: "shotsOnGoal", label: "유효 슈팅", kind: "number" },
  { name: "shotsOffGoal", label: "빗나간 슈팅", kind: "number" },
  { name: "totalShots", label: "전체 슈팅", kind: "number" },
  { name: "blockedShots", label: "막힌 슈팅", kind: "number" },
  { name: "shotsInsideBox", label: "박스 안 슈팅", kind: "number" },
  { name: "shotsOutsideBox", label: "박스 밖 슈팅", kind: "number" },
  { name: "fouls", label: "파울", kind: "number" },
  { name: "cornerKicks", label: "코너킥", kind: "number" },
  { name: "offsides", label: "오프사이드", kind: "number" },
  { name: "ballPossession", label: "점유율", kind: "number" },
  { name: "yellowCards", label: "옐로카드", kind: "number" },
  { name: "redCards", label: "레드카드", kind: "number" },
  { name: "goalkeeperSaves", label: "골키퍼 선방", kind: "number" },
  { name: "totalPasses", label: "전체 패스", kind: "number" },
  { name: "passesAccurate", label: "정확한 패스", kind: "number" },
  { name: "expectedGoals", label: "기대 득점(xG)", kind: "number" },
];

const playerStatFields: FieldConfig[] = [
  { name: "minutesPlayed", label: "출전 시간", kind: "number" },
  { name: "rating", label: "평점", kind: "number" },
  { name: "captain", label: "주장 여부", kind: "boolean" },
  { name: "substitute", label: "교체 선수 여부", kind: "boolean" },
  { name: "goals", label: "득점", kind: "number" },
  { name: "assists", label: "도움", kind: "number" },
  { name: "conceded", label: "실점", kind: "number" },
  { name: "saves", label: "선방", kind: "number" },
  { name: "shotsTotal", label: "전체 슈팅", kind: "number" },
  { name: "shotsOnTarget", label: "유효 슈팅", kind: "number" },
  { name: "passesTotal", label: "전체 패스", kind: "number" },
  { name: "passesKey", label: "키 패스", kind: "number" },
  { name: "passesAccurate", label: "정확한 패스", kind: "number" },
  { name: "tacklesTotal", label: "태클", kind: "number" },
  { name: "blocks", label: "블록", kind: "number" },
  { name: "interceptions", label: "인터셉트", kind: "number" },
  { name: "duelsTotal", label: "전체 경합", kind: "number" },
  { name: "duelsWon", label: "경합 승리", kind: "number" },
  { name: "dribblesAttempts", label: "드리블 시도", kind: "number" },
  { name: "dribblesSuccess", label: "드리블 성공", kind: "number" },
  { name: "dribblesPast", label: "드리블 돌파 허용", kind: "number" },
  { name: "foulsDrawn", label: "얻은 파울", kind: "number" },
  { name: "foulsCommitted", label: "범한 파울", kind: "number" },
  { name: "yellowCards", label: "옐로카드", kind: "number" },
  { name: "redCards", label: "레드카드", kind: "number" },
  { name: "offsides", label: "오프사이드", kind: "number" },
  { name: "penaltyWon", label: "페널티 획득", kind: "number" },
  { name: "penaltyCommitted", label: "페널티 허용", kind: "number" },
  { name: "penaltyScored", label: "페널티 성공", kind: "number" },
  { name: "penaltyMissed", label: "페널티 실축", kind: "number" },
  { name: "penaltySaved", label: "페널티 선방", kind: "number" },
];

const syncTasks = [
  { task: "seasons", label: "지원 시즌" },
  { task: "teams", label: "팀" },
  { task: "standings", label: "순위" },
  { task: "fixtures", label: "경기" },
  { task: "fixture-details", label: "시즌 경기 상세" },
  { task: "players", label: "선수" },
  { task: "injuries", label: "부상자" },
];

export function AdminPage({ authState }: AdminPageProps) {
  const location = useLocation();
  const [searchParams] = useSearchParams();
  const activeSection = adminSection(location.pathname);
  const [activeTab, setActiveTab] = useState<AdminTab>("team");
  const [teamKeyword, setTeamKeyword] = useState("");
  const [teams, setTeams] = useState<TeamAdmin[]>([]);
  const [teamSelection, setTeamSelection] = useState<TeamSelectionState>({
    teamId: null,
    status: "idle",
    detail: null,
  });
  const [playerKeyword, setPlayerKeyword] = useState("");
  const [players, setPlayers] = useState<PlayerAdmin[]>([]);
  const [selectedPlayerTeamId, setSelectedPlayerTeamId] = useState<number | null>(null);
  const [teamPlayers, setTeamPlayers] = useState<PlayerSummary[]>([]);
  const [teamPlayerStatus, setTeamPlayerStatus] = useState<FixtureListStatus>("idle");
  const [playerSelection, setPlayerSelection] = useState<PlayerSelectionState>({
    playerId: null,
    status: "idle",
    detail: null,
  });
  const [fixtureTeams, setFixtureTeams] = useState<FixtureTeamOption[]>([]);
  const [fixtureTeamStatus, setFixtureTeamStatus] = useState<FixtureListStatus>("idle");
  const [selectedFixtureTeamId, setSelectedFixtureTeamId] = useState<number | null>(null);
  const [fixtures, setFixtures] = useState<FixtureSummaryAdmin[]>([]);
  const [fixtureListStatus, setFixtureListStatus] = useState<FixtureListStatus>("idle");
  const [fixtureSelection, setFixtureSelection] = useState<FixtureSelectionState>({
    fixtureId: null,
    status: "idle",
    detail: null,
  });
  const [savingKey, setSavingKey] = useState<string | null>(null);
  const [syncingTask, setSyncingTask] = useState<string | null>(null);
  const [syncCooldownUntil, setSyncCooldownUntil] = useState<Record<string, number>>(readStoredSyncCooldowns);
  const [syncClock, setSyncClock] = useState(Date.now());
  const [syncStatuses, setSyncStatuses] = useState<SyncStatus[]>([]);
  const [seasonCoverages, setSeasonCoverages] = useState<LeagueSeasonCoverage[]>([]);
  const [coverageStatus, setCoverageStatus] = useState<CoverageStatus>("loading");
  const [syncJobs, setSyncJobs] = useState<SyncJob[]>([]);
  const [syncJobsStatus, setSyncJobsStatus] = useState<LoadStatus>("idle");
  const [cancellingJobId, setCancellingJobId] = useState<number | null>(null);
  const [logs, setLogs] = useState<AuditLog[]>([]);
  const [auditStatus, setAuditStatus] = useState<LoadStatus>("idle");
  const [auditError, setAuditError] = useState("");
  const [auditPage, setAuditPage] = useState(0);
  const [auditTotalPages, setAuditTotalPages] = useState(0);
  const [auditTotalElements, setAuditTotalElements] = useState(0);
  const [error, setError] = useState("");
  const [mediaToast, setMediaToast] = useState<AdminMediaToast | null>(null);
  const teamRequestIdRef = useRef(0);
  const playerRequestIdRef = useRef(0);
  const teamPlayerRequestIdRef = useRef(0);
  const fixtureTeamRequestIdRef = useRef(0);
  const fixtureListRequestIdRef = useRef(0);
  const fixtureRequestIdRef = useRef(0);
  const auditRequestIdRef = useRef(0);
  const syncStatusRequestIdRef = useRef(0);
  const syncJobsRequestIdRef = useRef(0);
  const syncJobsInitializedRef = useRef(false);
  const seenTerminalJobIdsRef = useRef<Set<number>>(new Set());
  const appliedAdminTargetRef = useRef("");
  const pendingAdminTargetRef = useRef<{ tab: AdminTab; id: number } | null>(null);
  const fixtureEditorRef = useRef<HTMLDivElement>(null);
  const selectedTeam = teamSelection.status === "ready" ? teamSelection.detail : null;
  const loadingTeamId = teamSelection.status === "loading" ? teamSelection.teamId : null;
  const selectedPlayer = playerSelection.status === "ready" ? playerSelection.detail : null;
  const loadingPlayerId = playerSelection.status === "loading" ? playerSelection.playerId : null;
  const selectedFixture = fixtureSelection.status === "ready" ? fixtureSelection.detail : null;
  const loadingFixtureId = fixtureSelection.status === "loading" ? fixtureSelection.fixtureId : null;

  useEffect(() => {
    if (authState.authStatus === "authenticated" && authState.currentUser?.role === "ADMIN") {
      void reloadSyncJobs(false);
      if (activeSection === "sync") {
        void reloadSyncStatusesSafely();
        void loadSeasonCoverages(setSeasonCoverages, setCoverageStatus);
      }
      if (activeSection === "logs") {
        void reloadAuditLogs(0);
      }
    }
  }, [activeSection, authState.authStatus, authState.currentUser?.role, authState.season]);

  const hasActiveSyncJobs = syncJobs.some((job) => job.active);

  useEffect(() => {
    if (!hasActiveSyncJobs) {
      return;
    }
    const timerId = window.setInterval(() => void reloadSyncJobs(true), SYNC_JOB_POLL_INTERVAL_MS);
    return () => window.clearInterval(timerId);
  }, [hasActiveSyncJobs, authState.season]);

  useEffect(() => {
    if (syncJobsStatus !== "error" || hasActiveSyncJobs) {
      return;
    }
    const timerId = window.setTimeout(
      () => void reloadSyncJobs(false),
      SYNC_JOB_RETRY_INTERVAL_MS,
    );
    return () => window.clearTimeout(timerId);
  }, [syncJobsStatus, hasActiveSyncJobs, authState.season]);

  useEffect(() => {
    if (!Object.values(syncCooldownUntil).some((until) => until > syncClock)) {
      return;
    }
    const timerId = window.setInterval(() => setSyncClock(Date.now()), 1000);
    return () => window.clearInterval(timerId);
  }, [syncCooldownUntil, syncClock]);

  useEffect(() => {
    const expiredKeys = Object.entries(syncCooldownUntil)
      .filter(([, until]) => until <= syncClock)
      .map(([key]) => key);
    if (expiredKeys.length === 0) {
      return;
    }
    setSyncCooldownUntil((current) => {
      const next = { ...current };
      expiredKeys.forEach((key) => delete next[key]);
      return next;
    });
    expiredKeys.forEach(removeStoredSyncCooldown);
  }, [syncCooldownUntil, syncClock]);

  useEffect(() => {
    function handleStorage(event: StorageEvent) {
      if (event.key !== null && !event.key.startsWith(`${MANUAL_SYNC_COOLDOWN_STORAGE_PREFIX}:`)) {
        return;
      }
      setSyncClock(Date.now());
      setSyncCooldownUntil(readStoredSyncCooldowns());
    }

    window.addEventListener("storage", handleStorage);
    return () => window.removeEventListener("storage", handleStorage);
  }, []);

  useEffect(() => {
    if (!mediaToast) {
      return;
    }
    const timerId = window.setTimeout(() => setMediaToast(null), 4000);
    return () => window.clearTimeout(timerId);
  }, [mediaToast]);

  useEffect(() => {
    if (
      authState.authStatus === "authenticated"
      && authState.currentUser?.role === "ADMIN"
      && activeSection === "editor"
    ) {
      void loadFixtureTeams(authState.season);
    }
  }, [activeSection, authState.authStatus, authState.currentUser?.role, authState.season]);

  useEffect(() => {
    if (
      activeSection !== "editor"
      || authState.authStatus !== "authenticated"
      || authState.currentUser?.role !== "ADMIN"
    ) {
      return;
    }
    const tab = adminTab(searchParams.get("tab"));
    const id = Number(searchParams.get("id"));
    if (!tab || !Number.isFinite(id) || id <= 0) {
      return;
    }
    const targetKey = `${tab}:${id}`;
    if (appliedAdminTargetRef.current === targetKey) {
      return;
    }
    appliedAdminTargetRef.current = targetKey;
    pendingAdminTargetRef.current = { tab, id };
    setActiveTab(tab);
    if (tab === "team") {
      void selectTeam(id);
      return;
    }
    if (tab === "player") {
      void selectPlayer(id);
      return;
    }
    void selectFixture(id);
  }, [activeSection, authState.authStatus, authState.currentUser?.role, searchParams]);

  useEffect(() => {
    const target = pendingAdminTargetRef.current;
    if (!target) {
      return;
    }
    if (target.tab === "team" && teamSelection.status === "ready" && teamSelection.teamId === target.id) {
      pendingAdminTargetRef.current = null;
      return;
    }
    if (target.tab === "player" && playerSelection.status === "ready" && playerSelection.playerId === target.id) {
      pendingAdminTargetRef.current = null;
      return;
    }
    if (target.tab === "fixture" && fixtureSelection.status === "ready" && fixtureSelection.fixtureId === target.id) {
      pendingAdminTargetRef.current = null;
      return;
    }
    if (fixtureTeamStatus !== "ready") {
      return;
    }
    if (target.tab === "team" && teamSelection.status === "idle") {
      void selectTeam(target.id);
    } else if (target.tab === "player" && playerSelection.status === "idle") {
      void selectPlayer(target.id);
    } else if (target.tab === "fixture" && fixtureSelection.status === "idle") {
      void selectFixture(target.id);
    }
  }, [
    fixtureSelection.fixtureId,
    fixtureSelection.status,
    fixtureTeamStatus,
    playerSelection.playerId,
    playerSelection.status,
    teamSelection.status,
    teamSelection.teamId,
  ]);

  useEffect(() => {
    if (fixtureSelection.status !== "ready") {
      return;
    }
    const frameId = window.requestAnimationFrame(() => {
      fixtureEditorRef.current?.scrollIntoView({ behavior: "smooth", block: "start" });
    });
    return () => window.cancelAnimationFrame(frameId);
  }, [fixtureSelection.status, fixtureSelection.fixtureId]);

  if (authState.authStatus === "checking") {
    return <section className="panel admin-page-panel">권한을 확인하는 중입니다.</section>;
  }

  if (authState.currentUser?.role !== "ADMIN") {
    return <Navigate to="/league/overview" replace />;
  }

  if (activeSection === null) {
    return <Navigate to={`/admin/editor${location.search}`} replace />;
  }

  async function searchTeams(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    await runRequest(async () => {
      const result = await adminGet<TeamAdmin[]>(`/api/v1/admin/teams?keyword=${encodeURIComponent(teamKeyword)}`);
      setTeams(result);
    });
  }

  async function selectTeam(teamId: number) {
    if (savingKey !== null) {
      return;
    }
    const requestId = teamRequestIdRef.current + 1;
    teamRequestIdRef.current = requestId;
    setTeamSelection({ teamId, status: "loading", detail: null });
    setError("");
    try {
      const detail = await adminGet<TeamAdmin>(`/api/v1/admin/teams/${teamId}`);
      if (teamRequestIdRef.current === requestId && detail.teamId === teamId) {
        setTeamSelection({ teamId, status: "ready", detail });
      }
    } catch (nextError) {
      if (teamRequestIdRef.current === requestId) {
        setTeamSelection({ teamId, status: "error", detail: null });
        setError(nextError instanceof Error ? nextError.message : "팀 정보를 불러오지 못했습니다.");
      }
    }
  }

  async function searchPlayers(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    await runRequest(async () => {
      const result = await adminGet<PlayerAdmin[]>(`/api/v1/admin/players?keyword=${encodeURIComponent(playerKeyword)}`);
      setPlayers(result);
    });
  }

  async function selectPlayer(playerId: number) {
    if (savingKey !== null) {
      return;
    }
    const requestId = playerRequestIdRef.current + 1;
    playerRequestIdRef.current = requestId;
    setPlayerSelection({ playerId, status: "loading", detail: null });
    setError("");
    try {
      const detail = await adminGet<PlayerAdmin>(`/api/v1/admin/players/${playerId}`);
      if (playerRequestIdRef.current === requestId && detail.playerId === playerId) {
        setPlayerSelection({ playerId, status: "ready", detail });
      }
    } catch (nextError) {
      if (playerRequestIdRef.current === requestId) {
        setPlayerSelection({ playerId, status: "error", detail: null });
        setError(nextError instanceof Error ? nextError.message : "선수 정보를 불러오지 못했습니다.");
      }
    }
  }

  async function loadFixtureTeams(season: number) {
    const requestId = fixtureTeamRequestIdRef.current + 1;
    fixtureTeamRequestIdRef.current = requestId;
    fixtureListRequestIdRef.current += 1;
    fixtureRequestIdRef.current += 1;
    teamPlayerRequestIdRef.current += 1;
    setFixtureTeamStatus("loading");
    setTeamSelection({ teamId: null, status: "idle", detail: null });
    setSelectedPlayerTeamId(null);
    setTeamPlayers([]);
    setTeamPlayerStatus("idle");
    setPlayerSelection({ playerId: null, status: "idle", detail: null });
    setSelectedFixtureTeamId(null);
    setFixtures([]);
    setFixtureListStatus("idle");
    setFixtureSelection({ fixtureId: null, status: "idle", detail: null });
    try {
      const result = await adminGet<FixtureTeamOption[]>(`/api/v1/admin/fixture-teams?season=${season}`);
      if (fixtureTeamRequestIdRef.current === requestId) {
        setFixtureTeams(result);
        setFixtureTeamStatus("ready");
      }
    } catch (nextError) {
      if (fixtureTeamRequestIdRef.current === requestId) {
        setFixtureTeams([]);
        setFixtureTeamStatus("error");
        setError(nextError instanceof Error ? nextError.message : "시즌 참가 팀을 불러오지 못했습니다.");
      }
    }
  }

  async function selectPlayerTeam(teamId: number) {
    if (savingKey !== null) {
      return;
    }
    const requestId = teamPlayerRequestIdRef.current + 1;
    teamPlayerRequestIdRef.current = requestId;
    playerRequestIdRef.current += 1;
    setSelectedPlayerTeamId(teamId);
    setTeamPlayers([]);
    setTeamPlayerStatus("loading");
    setPlayerSelection({ playerId: null, status: "idle", detail: null });
    setError("");
    try {
      const result = await fetchTeamPlayers(teamId, authState.season);
      if (teamPlayerRequestIdRef.current === requestId) {
        setTeamPlayers(result);
        setTeamPlayerStatus("ready");
      }
    } catch (nextError) {
      if (teamPlayerRequestIdRef.current === requestId) {
        setTeamPlayerStatus("error");
        setError(nextError instanceof Error ? nextError.message : "팀 선수 목록을 불러오지 못했습니다.");
      }
    }
  }

  async function selectFixtureTeam(teamId: number) {
    if (savingKey !== null) {
      return;
    }
    const requestId = fixtureListRequestIdRef.current + 1;
    fixtureListRequestIdRef.current = requestId;
    fixtureRequestIdRef.current += 1;
    setSelectedFixtureTeamId(teamId);
    setFixtures([]);
    setFixtureListStatus("loading");
    setFixtureSelection({ fixtureId: null, status: "idle", detail: null });
    setError("");
    try {
      const response = await fetchFixtures({ season: authState.season, teamId, size: 100 });
      if (fixtureListRequestIdRef.current === requestId) {
        setFixtures(sortAdminFixturesByRound(response.content ?? []));
        setFixtureListStatus("ready");
      }
    } catch (nextError) {
      if (fixtureListRequestIdRef.current === requestId) {
        setFixtureListStatus("error");
        setError(nextError instanceof Error ? nextError.message : "팀 경기 목록을 불러오지 못했습니다.");
      }
    }
  }

  async function selectFixture(fixtureId: number) {
    if (savingKey !== null) {
      return;
    }
    const requestId = fixtureRequestIdRef.current + 1;
    fixtureRequestIdRef.current = requestId;
    setFixtureSelection({ fixtureId, status: "loading", detail: null });
    setError("");
    try {
      const detail = await adminGet<FixtureDetailAdmin>(`/api/v1/admin/fixtures/${fixtureId}`);
      if (fixtureRequestIdRef.current === requestId && detail.fixture.fixtureId === fixtureId) {
        setFixtureSelection({ fixtureId, status: "ready", detail });
      }
    } catch (nextError) {
      if (fixtureRequestIdRef.current === requestId) {
        setFixtureSelection({ fixtureId, status: "error", detail: null });
        setError(nextError instanceof Error ? nextError.message : "경기 정보를 불러오지 못했습니다.");
      }
    }
  }

  async function reloadAuditLogs(page = auditPage) {
    const requestId = auditRequestIdRef.current + 1;
    auditRequestIdRef.current = requestId;
    setAuditStatus("loading");
    setAuditError("");
    try {
      const response = await adminGet<AuditLogPage>(`/api/v1/admin/audit-logs?page=${page}&size=${AUDIT_LOG_PAGE_SIZE}`);
      if (auditRequestIdRef.current !== requestId) {
        return;
      }
      setLogs(response.logs ?? []);
      setAuditPage(response.page ?? page);
      setAuditTotalPages(response.totalPages ?? 0);
      setAuditTotalElements(response.totalElements ?? 0);
      setAuditStatus("ready");
    } catch (nextError) {
      if (auditRequestIdRef.current !== requestId) {
        return;
      }
      setAuditStatus("error");
      setAuditError(nextError instanceof Error ? nextError.message : "관리자 로그를 불러오지 못했습니다.");
    }
  }

  async function reloadSyncJobs(notify: boolean) {
    const requestId = syncJobsRequestIdRef.current + 1;
    syncJobsRequestIdRef.current = requestId;
    if (!syncJobsInitializedRef.current) {
      setSyncJobsStatus("loading");
    }
    try {
      const response = await adminGet<{ jobs: SyncJob[] }>("/api/v1/admin/sync/jobs?limit=10");
      if (syncJobsRequestIdRef.current !== requestId) {
        return;
      }
      const jobs = response.jobs ?? [];
      const terminalJobs = jobs.filter((job) => !job.active);
      if (!syncJobsInitializedRef.current) {
        terminalJobs.forEach((job) => seenTerminalJobIdsRef.current.add(job.id));
        syncJobsInitializedRef.current = true;
      } else if (notify) {
        const newlyCompleted = terminalJobs.filter((job) => !seenTerminalJobIdsRef.current.has(job.id));
        newlyCompleted.forEach((job) => {
          seenTerminalJobIdsRef.current.add(job.id);
          setMediaToast({
            type: job.status === "SUCCEEDED" ? "success" : "error",
            message: syncJobCompletionMessage(job),
          });
        });
        if (newlyCompleted.length > 0) {
          await Promise.all([
            reloadAuditLogs(0),
            reloadSyncStatusesSafely(),
          ]);
        }
      }
      setSyncJobs(jobs);
      setSyncJobsStatus("ready");
    } catch {
      if (syncJobsRequestIdRef.current === requestId) {
        setSyncJobsStatus("error");
      }
    }
  }

  async function cancelSyncJob(job: SyncJob) {
    if (job.status !== "QUEUED" && job.status !== "RUNNING") {
      return;
    }
    if (!window.confirm(`${syncTaskLabel(job.task)} 작업을 취소할까요? 현재 처리 중인 단위는 안전하게 마친 뒤 중단됩니다.`)) {
      return;
    }
    setCancellingJobId(job.id);
    try {
      const response = await adminJson<{ message: string }>(
        `/api/v1/admin/sync/jobs/${job.id}/cancel`,
        "POST",
      );
      setMediaToast({ type: "success", message: response.message });
      await Promise.all([reloadSyncJobs(false), reloadAuditLogs(0)]);
    } catch (nextError) {
      setMediaToast({
        type: "error",
        message: nextError instanceof Error ? nextError.message : "작업 취소 요청에 실패했습니다.",
      });
    } finally {
      setCancellingJobId(null);
    }
  }

  async function saveTeam(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!selectedTeam || savingKey !== null) {
      return;
    }
    const teamId = selectedTeam.teamId;
    const body = {
      ...formBody(event.currentTarget, teamFields),
      version: selectedTeam.version,
      venueId: selectedTeam.venueId,
    };
    await runToastSave(`team:${teamId}`, async () => {
      const updated = await adminJson<TeamAdmin>(`/api/v1/admin/teams/${teamId}`, "PUT", body);
      clearApiMemoryCache();
      setTeamSelection({ teamId, status: "ready", detail: updated });
      await reloadAuditLogs();
    }, "팀 정보를 수정했습니다.", "팀 정보를 수정하지 못했습니다.");
  }

  async function savePlayer(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!selectedPlayer || savingKey !== null) {
      return;
    }
    const playerId = selectedPlayer.playerId;
    const body = {
      ...formBody(event.currentTarget, playerFields),
      version: selectedPlayer.version,
    };
    await runToastSave(`player:${playerId}`, async () => {
      const updated = await adminJson<PlayerAdmin>(`/api/v1/admin/players/${playerId}`, "PUT", body);
      clearApiMemoryCache();
      setPlayerSelection({ playerId, status: "ready", detail: updated });
      await reloadAuditLogs();
    }, "선수 정보를 수정했습니다.", "선수 정보를 수정하지 못했습니다.");
  }

  async function clearTeamOverrides() {
    if (!selectedTeam || savingKey !== null) {
      return;
    }
    if (!window.confirm("팀의 관리자 수정 상태를 모두 초기화할까요? 다음 동기화부터 API 값이 다시 반영됩니다.")) {
      return;
    }
    const teamId = selectedTeam.teamId;
    await runToastSave(`team-overrides:${teamId}`, async () => {
      const updated = await adminJson<TeamAdmin>(`/api/v1/admin/teams/${teamId}/overrides`, "DELETE");
      clearApiMemoryCache();
      setTeamSelection({ teamId, status: "ready", detail: updated });
      await reloadAuditLogs();
    }, "팀의 관리자 수정 상태를 초기화했습니다.", "팀의 관리자 수정 상태를 초기화하지 못했습니다.");
  }

  async function clearPlayerOverrides() {
    if (!selectedPlayer || savingKey !== null) {
      return;
    }
    if (!window.confirm("선수의 관리자 수정 상태를 모두 초기화할까요? 다음 동기화부터 API 값이 다시 반영됩니다.")) {
      return;
    }
    const playerId = selectedPlayer.playerId;
    await runToastSave(`player-overrides:${playerId}`, async () => {
      const updated = await adminJson<PlayerAdmin>(`/api/v1/admin/players/${playerId}/overrides`, "DELETE");
      clearApiMemoryCache();
      setPlayerSelection({ playerId, status: "ready", detail: updated });
      await reloadAuditLogs();
    }, "선수의 관리자 수정 상태를 초기화했습니다.", "선수의 관리자 수정 상태를 초기화하지 못했습니다.");
  }

  async function clearTeamOverride(fieldName: string) {
    if (!selectedTeam || savingKey !== null) {
      return;
    }
    const label = fieldLabel(teamFields, fieldName);
    if (!window.confirm(`${label}의 관리자 수정 상태를 초기화할까요? 다음 동기화부터 API 값이 다시 반영됩니다.`)) {
      return;
    }
    const teamId = selectedTeam.teamId;
    await runToastSave(`team-override:${teamId}:${fieldName}`, async () => {
      const updated = await adminJson<TeamAdmin>(
        `/api/v1/admin/teams/${teamId}/overrides/${encodeURIComponent(fieldName)}`,
        "DELETE",
      );
      clearApiMemoryCache();
      setTeamSelection({ teamId, status: "ready", detail: updated });
      await reloadAuditLogs();
    }, `${label}의 관리자 수정 상태를 초기화했습니다.`, `${label}의 관리자 수정 상태를 초기화하지 못했습니다.`);
  }

  async function clearPlayerOverride(fieldName: string) {
    if (!selectedPlayer || savingKey !== null) {
      return;
    }
    const label = fieldLabel(playerFields, fieldName);
    if (!window.confirm(`${label}의 관리자 수정 상태를 초기화할까요? 다음 동기화부터 API 값이 다시 반영됩니다.`)) {
      return;
    }
    const playerId = selectedPlayer.playerId;
    await runToastSave(`player-override:${playerId}:${fieldName}`, async () => {
      const updated = await adminJson<PlayerAdmin>(
        `/api/v1/admin/players/${playerId}/overrides/${encodeURIComponent(fieldName)}`,
        "DELETE",
      );
      clearApiMemoryCache();
      setPlayerSelection({ playerId, status: "ready", detail: updated });
      await reloadAuditLogs();
    }, `${label}의 관리자 수정 상태를 초기화했습니다.`, `${label}의 관리자 수정 상태를 초기화하지 못했습니다.`);
  }

  async function uploadAdminMedia(
    targetType: AdminMediaTargetType,
    targetId: number,
    file: File,
  ): Promise<string | null> {
    if (!ADMIN_IMAGE_CONTENT_TYPES.has(file.type) || file.size <= 0 || file.size > ADMIN_IMAGE_MAX_BYTES) {
      const validationError = "PNG, JPEG, WebP 형식의 2MB 이하 이미지만 업로드할 수 있습니다.";
      setMediaToast({ message: validationError, type: "error" });
      return validationError;
    }

    return runMediaSave(`media:${targetType}:${targetId}`, async () => {
      let presign: AdminMediaPresignResponse;
      try {
        presign = await adminJson<AdminMediaPresignResponse>("/api/v1/admin/media/uploads/presign", "POST", {
          targetType,
          targetId,
          contentType: file.type,
          sizeBytes: file.size,
        });
      } catch (nextError) {
        throw new Error(`업로드 URL 발급 실패: ${requestErrorMessage(nextError)}`);
      }

      let uploadResponse: Response;
      try {
        uploadResponse = await fetch(presign.uploadUrl, {
          method: "PUT",
          headers: presign.requiredHeaders,
          body: file,
        });
      } catch {
        throw new Error("R2 이미지 전송에 실패했습니다. 네트워크 상태와 R2 CORS 설정을 확인해주세요.");
      }
      if (!uploadResponse.ok) {
        throw new Error(`R2 이미지 전송에 실패했습니다. (${uploadResponse.status})`);
      }

      let completed: AdminMediaResponse;
      try {
        completed = await adminJson<AdminMediaResponse>("/api/v1/admin/media/uploads/complete", "POST", {
          targetType,
          targetId,
          objectKey: presign.objectKey,
        });
      } catch (nextError) {
        throw new Error(`업로드 완료 확인 실패: ${requestErrorMessage(nextError)}`);
      }

      applyAdminMediaResponse(completed);
      await refreshVersionedMediaTarget(completed);
      clearApiMemoryCache();
      await reloadAuditLogs();
    }, "관리자 이미지를 적용했습니다.");
  }

  async function restoreAdminMedia(targetType: AdminMediaTargetType, targetId: number) {
    await runMediaSave(`media:${targetType}:${targetId}`, async () => {
      const restored = await adminJson<AdminMediaResponse>(
        `/api/v1/admin/media/${targetType}/${targetId}`,
        "DELETE",
      );
      applyAdminMediaResponse(restored);
      await refreshVersionedMediaTarget(restored);
      clearApiMemoryCache();
      await reloadAuditLogs();
    }, "관리자 이미지를 제거하고 원본 이미지로 복원했습니다.");
  }

  function applyAdminMediaResponse(response: AdminMediaResponse) {
    if (response.targetType === "PLAYER_PHOTO") {
      setPlayerSelection((current) => current.status === "ready" && current.playerId === response.targetId && current.detail
        ? {
            ...current,
            detail: { ...current.detail, photoDisplayUrl: response.publicUrl, adminPhoto: response.adminImage },
          }
        : current);
      return;
    }

    setTeamSelection((current) => {
      if (current.status !== "ready" || !current.detail) {
        return current;
      }
      const matchesTarget = response.targetType === "TEAM_LOGO"
        ? current.teamId === response.targetId
        : current.detail.venueId === response.targetId;
      if (!matchesTarget) {
        return current;
      }
      return {
        ...current,
        detail: response.targetType === "TEAM_LOGO"
          ? { ...current.detail, logoDisplayUrl: response.publicUrl, adminLogo: response.adminImage }
          : { ...current.detail, venueImageDisplayUrl: response.publicUrl, adminVenueImage: response.adminImage },
      };
    });
  }

  async function refreshVersionedMediaTarget(response: AdminMediaResponse) {
    if (response.targetType === "PLAYER_PHOTO") {
      const detail = await adminGet<PlayerAdmin>(`/api/v1/admin/players/${response.targetId}`);
      setPlayerSelection({ playerId: response.targetId, status: "ready", detail });
      return;
    }
    if (response.targetType === "TEAM_LOGO") {
      const detail = await adminGet<TeamAdmin>(`/api/v1/admin/teams/${response.targetId}`);
      setTeamSelection({ teamId: response.targetId, status: "ready", detail });
    }
  }

  async function saveFixture(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!selectedFixture) {
      return;
    }
    const fixtureId = selectedFixture.fixture.fixtureId;
    await saveFixturePayload(
      fixtureId,
      `/api/v1/admin/fixtures/${fixtureId}`,
      { ...formBody(event.currentTarget, fixtureFields), version: selectedFixture.fixture.version },
      "경기 기본 정보를 저장했습니다.",
    );
  }

  async function saveFixturePayload(fixtureId: number, url: string, body: Record<string, unknown>, successMessage: string, method = "PUT") {
    if (!canSaveFixture(fixtureId) || savingKey !== null) {
      setMediaToast({
        type: "error",
        message: savingKey !== null
          ? "다른 관리자 요청을 처리하고 있습니다. 잠시 후 다시 시도해주세요."
          : "현재 열려 있는 경기와 저장 대상이 일치하지 않습니다. 경기를 다시 선택해주세요.",
      });
      return;
    }
    await runToastSave(`fixture:${fixtureId}`, async () => {
      if (!canSaveFixture(fixtureId)) {
        throw new Error("현재 열려 있는 경기와 저장 대상이 일치하지 않습니다. 경기를 다시 선택해주세요.");
      }
      const updated = await adminJson<FixtureDetailAdmin>(url, method, body);
      clearApiMemoryCache();
      if (canSaveFixture(fixtureId) && updated.fixture.fixtureId === fixtureId) {
        setFixtureSelection({ fixtureId, status: "ready", detail: updated });
      }
      await reloadAuditLogs();
    }, successMessage, "경기 정보를 저장하지 못했습니다.");
  }

  function canSaveFixture(fixtureId: number) {
    return fixtureSelection.status === "ready"
      && fixtureSelection.fixtureId === fixtureId
      && fixtureSelection.detail?.fixture.fixtureId === fixtureId;
  }

  async function runToastSave(
    key: string,
    action: () => Promise<void>,
    successMessage: string,
    fallbackErrorMessage: string,
  ): Promise<string | null> {
    if (savingKey !== null) {
      const busyMessage = "다른 관리자 요청을 처리하고 있습니다. 잠시 후 다시 시도해주세요.";
      setMediaToast({ message: busyMessage, type: "error" });
      return busyMessage;
    }
    setSavingKey(key);
    setError("");
    try {
      await action();
      setMediaToast({ message: successMessage, type: "success" });
      return null;
    } catch (nextError) {
      const requestError = nextError instanceof Error ? nextError.message : fallbackErrorMessage;
      setMediaToast({ message: requestError, type: "error" });
      return requestError;
    } finally {
      setSavingKey(null);
    }
  }

  async function runMediaSave(key: string, action: () => Promise<void>, successMessage: string): Promise<string | null> {
    if (savingKey !== null) {
      const busyMessage = "다른 관리자 요청을 처리하고 있습니다. 잠시 후 다시 시도해주세요.";
      setMediaToast({ message: busyMessage, type: "error" });
      return busyMessage;
    }
    setSavingKey(key);
    try {
      await action();
      setMediaToast({ message: successMessage, type: "success" });
      return null;
    } catch (nextError) {
      const requestError = nextError instanceof Error ? nextError.message : "이미지 요청을 처리하지 못했습니다.";
      setMediaToast({ message: requestError, type: "error" });
      return requestError;
    } finally {
      setSavingKey(null);
    }
  }

  function manualSyncCooldownKey(task: string) {
    if (task === "seasons") {
      return task;
    }
    return `${task}:${authState.season}`;
  }

  function syncCooldownSeconds(key: string) {
    return Math.max(0, Math.ceil(((syncCooldownUntil[key] ?? 0) - syncClock) / 1000));
  }

  function markSyncCooldown(key: string) {
    const now = Date.now();
    const cooldownUntil = now + MANUAL_SYNC_COOLDOWN_MS;
    storeSyncCooldown(key, cooldownUntil);
    setSyncClock(now);
    setSyncCooldownUntil((current) => ({
      ...current,
      [key]: cooldownUntil,
    }));
  }

  async function runSync(task: string) {
    const availability = syncAvailability(task, authState.season, seasonCoverages, coverageStatus);
    if (!availability.enabled) {
      setMediaToast({ message: availability.message, type: "error" });
      return;
    }
    const cooldownKey = manualSyncCooldownKey(task);
    const cooldownSeconds = syncCooldownSeconds(cooldownKey);
    if (cooldownSeconds > 0) {
      const cooldownMessage = `${cooldownSeconds}초 후 다시 요청할 수 있습니다.`;
      setMediaToast({ message: cooldownMessage, type: "error" });
      return;
    }
    const league = 39;
    const season = authState.season;
    const urls: Record<string, string | null> = {
      seasons: `/api/v1/admin/sync/seasons?league=${league}`,
      teams: `/api/v1/admin/sync/teams?league=${league}&season=${season}`,
      standings: `/api/v1/admin/sync/standings?league=${league}&season=${season}`,
      fixtures: `/api/v1/admin/sync/fixtures?league=${league}&season=${season}`,
      "fixture-details": `/api/v1/admin/sync/fixture-details?season=${season}`,
      players: `/api/v1/admin/sync/players?league=${league}&season=${season}&delayMs=7000`,
      injuries: `/api/v1/admin/sync/injuries?league=${league}&season=${season}`,
    };
    const url = urls[task];
    if (!url) {
      const missingTargetMessage = "경기 상세 동기화는 먼저 경기를 선택해야 합니다.";
      setMediaToast({ message: missingTargetMessage, type: "error" });
      return;
    }
    markSyncCooldown(cooldownKey);
    setSyncingTask(task);
    setError("");
    let result: AdminSyncResponse;
    try {
      result = await adminJson<AdminSyncResponse>(url, "POST");
      if (result.success === false) {
        throw new Error(result.message);
      }
    } catch (nextError) {
      setSyncingTask(null);
      const requestError = requestErrorMessage(nextError);
      setMediaToast({ message: requestError, type: "error" });
      void reloadSyncStatusesSafely();
      return;
    }

    setSyncingTask(null);
    clearApiMemoryCache();
    if (result.jobId || result.queued) {
      setMediaToast({ message: `${syncRequestDisplayKey(task, season)} 동기화 요청됨`, type: "success" });
    } else {
      setMediaToast({ message: `${syncRequestDisplayKey(task, season)} 동기화 완료`, type: "success" });
    }

    try {
      if (task === "seasons") {
        await loadSeasonCoverages(setSeasonCoverages, setCoverageStatus);
      }
      await reloadAuditLogs();
      if (result.jobId) {
        await reloadSyncJobs(true);
      }
    } catch {
      setMediaToast({
        message: "동기화 요청은 처리됐지만 최신 상태를 불러오지 못했습니다.",
        type: "error",
      });
    } finally {
      void reloadSyncStatusesSafely();
    }
  }

  async function reloadSyncStatusesSafely() {
    const requestId = syncStatusRequestIdRef.current + 1;
    syncStatusRequestIdRef.current = requestId;
    const controller = new AbortController();
    const timeoutId = window.setTimeout(() => controller.abort(), SYNC_STATUS_REQUEST_TIMEOUT_MS);
    try {
      const statuses = await loadSyncStatuses(authState.season, controller.signal);
      if (syncStatusRequestIdRef.current === requestId) {
        setSyncStatuses(statuses);
      }
    } catch {
      // 상태 조회 실패가 이미 완료된 동기화 요청의 성공·실패 결과를 바꾸지 않도록 분리한다.
    } finally {
      window.clearTimeout(timeoutId);
    }
  }

  const syncStatusByTask = new Map(syncStatuses.map((status) => [status.task, status]));
  const apiFootballStatus = syncStatusByTask.get("api-football");
  const newsProviderStatuses = [syncStatusByTask.get("serp-api"), syncStatusByTask.get("openai")].filter(
    (status): status is SyncStatus => status !== undefined,
  );
  const activeSyncTasks = new Set(syncJobs.filter((job) => job.active).map((job) => job.task));
  const runningSyncJobs = syncJobs.filter((job) => job.status === "RUNNING" || job.status === "CANCEL_REQUESTED");
  const queuedSyncJobs = syncJobs.filter((job) => job.status === "QUEUED");
  const completedSyncJobs = syncJobs.filter((job) => !job.active);
  const selectedCoverage = seasonCoverages.find((coverage) => coverage.seasonYear === authState.season) ?? null;
  async function runRequest(action: () => Promise<void>): Promise<string | null> {
    setError("");
    try {
      await action();
      return null;
    } catch (nextError) {
      const requestError = nextError instanceof Error ? nextError.message : "요청을 처리하지 못했습니다.";
      setError(requestError);
      return requestError;
    }
  }

  return (
    <section className="admin-page">
      {mediaToast ? (
        <div
          className={`admin-media-toast ${mediaToast.type}`}
          role={mediaToast.type === "error" ? "alert" : "status"}
          aria-live={mediaToast.type === "error" ? "assertive" : "polite"}
        >
          <span>{mediaToast.message}</span>
          <button type="button" aria-label="알림 닫기" onClick={() => setMediaToast(null)}>
            <X size={18} aria-hidden="true" />
          </button>
        </div>
      ) : null}
      <div className="admin-page-heading">
        <div>
          <p className="eyebrow">관리자</p>
          <h2>
            {activeSection === "editor"
              ? "데이터 편집"
              : activeSection === "sync"
                ? "외부 API 동기화"
                : "관리자 로그"}
          </h2>
        </div>
        <span className="status-pill">{authState.currentUser.email}</span>
      </div>

      <nav className="admin-tabs admin-section-tabs" aria-label="관리자 페이지">
        <NavLink className={({ isActive }) => `admin-tab${isActive ? " active" : ""}`} to="/admin/editor">
          데이터 편집
        </NavLink>
        <NavLink className={({ isActive }) => `admin-tab${isActive ? " active" : ""}`} to="/admin/sync">
          외부 API 동기화
        </NavLink>
        <NavLink className={({ isActive }) => `admin-tab${isActive ? " active" : ""}`} to="/admin/logs">
          관리자 로그
        </NavLink>
      </nav>

      {activeSection === "editor" ? (
        <>
      {error ? <div className="notice error">{error}</div> : null}

      <nav className="admin-tabs admin-editor-tabs" aria-label="편집 대상">
        <button
          type="button"
          className={`admin-tab${activeTab === "team" ? " active" : ""}`}
          onClick={() => setActiveTab("team")}
        >
          팀
        </button>
        <button
          type="button"
          className={`admin-tab${activeTab === "player" ? " active" : ""}`}
          onClick={() => setActiveTab("player")}
        >
          선수
        </button>
        <button
          type="button"
          className={`admin-tab${activeTab === "fixture" ? " active" : ""}`}
          onClick={() => setActiveTab("fixture")}
        >
          경기
        </button>
      </nav>

      {activeTab === "team" ? (
        <EditorPanel title="팀 정보 편집" eyebrow="팀">
          <label className="admin-fixture-team-select">
            <span>{authState.season} 시즌 팀</span>
            <select
              value={teamSelection.teamId ?? ""}
              onChange={(event) => {
                const teamId = Number(event.currentTarget.value);
                if (Number.isFinite(teamId) && teamId > 0) {
                  void selectTeam(teamId);
                }
              }}
              disabled={fixtureTeamStatus !== "ready" || savingKey !== null}
            >
              <option value="">{fixtureTeamStatus === "loading" ? "팀 목록 불러오는 중..." : "팀을 선택하세요"}</option>
              {fixtureTeams.map((team) => (
                <option key={team.teamId} value={team.teamId}>{adminDisplayName(team.koreanName, team.name, team.teamId)}</option>
              ))}
            </select>
          </label>
          <p className="muted admin-sync-message">목록에 없는 팀은 아래 검색으로 찾을 수 있습니다.</p>
          <SearchRow value={teamKeyword} placeholder="팀 검색" onChange={setTeamKeyword} onSubmit={searchTeams} />
          <ResultList
            items={teams}
            getKey={(team) => team.teamId}
            render={(team) => adminDisplayName(team.koreanName, team.name, team.teamId)}
            onSelect={(team) => void selectTeam(team.teamId)}
            disabled={savingKey !== null}
          />
          {loadingTeamId !== null ? <p className="muted admin-sync-message">팀 정보를 불러오는 중입니다.</p> : null}
          {selectedTeam ? (
            <>
              <div className="admin-media-grid">
                <AdminMediaUpload
                  label="팀 로고"
                  imageUrl={selectedTeam.logoDisplayUrl}
                  adminImage={selectedTeam.adminLogo}
                  targetType="TEAM_LOGO"
                  targetId={selectedTeam.teamId}
                  disabled={savingKey !== null}
                  onUpload={uploadAdminMedia}
                  onRestore={restoreAdminMedia}
                />
                <AdminMediaUpload
                  label="경기장 이미지"
                  imageUrl={selectedTeam.venueImageDisplayUrl}
                  adminImage={selectedTeam.adminVenueImage}
                  targetType="VENUE_IMAGE"
                  targetId={selectedTeam.venueId}
                  disabled={savingKey !== null}
                  unavailableMessage="경기장 정보를 먼저 저장해주세요."
                  onUpload={uploadAdminMedia}
                  onRestore={restoreAdminMedia}
                />
              </div>
              <AdminForm
                title={selectedTeam.name ?? "팀"}
                fields={teamFields}
                value={selectedTeam}
                overrides={selectedTeam.manualOverrides}
                onClearOverrides={() => void clearTeamOverrides()}
                onClearOverride={(fieldName) => void clearTeamOverride(fieldName)}
                submitLabel="팀 정보 저장"
                onSubmit={saveTeam}
                disabled={savingKey !== null}
              />
            </>
          ) : null}
        </EditorPanel>
      ) : null}

      {activeTab === "player" ? (
        <EditorPanel title="선수 정보 편집" eyebrow="선수">
          <div className="admin-fixture-browser">
            <label className="admin-fixture-team-select">
              <span>{authState.season} 시즌 팀</span>
              <select
                value={selectedPlayerTeamId ?? ""}
                onChange={(event) => {
                  const teamId = Number(event.currentTarget.value);
                  if (Number.isFinite(teamId) && teamId > 0) {
                    void selectPlayerTeam(teamId);
                  }
                }}
                disabled={fixtureTeamStatus !== "ready" || savingKey !== null}
              >
                <option value="">{fixtureTeamStatus === "loading" ? "팀 목록 불러오는 중..." : "팀을 선택하세요"}</option>
                {fixtureTeams.map((team) => (
                  <option key={team.teamId} value={team.teamId}>{adminDisplayName(team.koreanName, team.name, team.teamId)}</option>
                ))}
              </select>
            </label>
            {teamPlayerStatus === "loading" ? <p className="muted admin-sync-message">선수 목록을 불러오는 중입니다.</p> : null}
            {teamPlayerStatus === "error" ? <p className="muted admin-sync-message">선수 목록을 불러오지 못했습니다.</p> : null}
            {teamPlayerStatus === "ready" && teamPlayers.length === 0 ? <p className="muted admin-sync-message">선택한 팀의 선수가 없습니다.</p> : null}
            {teamPlayers.length > 0 ? (
              <ResultList
                items={teamPlayers}
                getKey={(player) => player.playerId}
                render={(player) => adminDisplayName(player.playerNameKo, player.playerName, player.playerId)}
                onSelect={(player) => void selectPlayer(player.playerId)}
                disabled={savingKey !== null}
              />
            ) : null}
          </div>
          <p className="muted admin-sync-message">현재 시즌 팀 목록에 없는 선수는 아래 검색으로 찾을 수 있습니다.</p>
          <SearchRow value={playerKeyword} placeholder="선수 검색" onChange={setPlayerKeyword} onSubmit={searchPlayers} />
          <ResultList
            items={players}
            getKey={(player) => player.playerId}
            render={(player) => adminDisplayName(player.koreanName, player.name, player.playerId)}
            onSelect={(player) => void selectPlayer(player.playerId)}
            disabled={savingKey !== null}
          />
          {loadingPlayerId !== null ? <p className="muted admin-sync-message">선수 정보를 불러오는 중입니다.</p> : null}
          {selectedPlayer ? (
            <>
              <div className="admin-media-grid single">
                <AdminMediaUpload
                  label="선수 프로필 이미지"
                  imageUrl={selectedPlayer.photoDisplayUrl}
                  adminImage={selectedPlayer.adminPhoto}
                  targetType="PLAYER_PHOTO"
                  targetId={selectedPlayer.playerId}
                  disabled={savingKey !== null}
                  onUpload={uploadAdminMedia}
                  onRestore={restoreAdminMedia}
                />
              </div>
              <AdminForm
                title={selectedPlayer.name ?? "선수"}
                fields={playerFields}
                value={selectedPlayer}
                overrides={selectedPlayer.manualOverrides}
                onClearOverrides={() => void clearPlayerOverrides()}
                onClearOverride={(fieldName) => void clearPlayerOverride(fieldName)}
                submitLabel="선수 정보 저장"
                onSubmit={savePlayer}
                disabled={savingKey !== null}
              />
            </>
          ) : null}
        </EditorPanel>
      ) : null}

      {activeTab === "fixture" ? (
      <EditorPanel title="경기 정보 편집" eyebrow="경기">
        <div className="admin-fixture-browser">
          <label className="admin-fixture-team-select">
            <span>{authState.season} 시즌 팀</span>
            <select
              value={selectedFixtureTeamId ?? ""}
              onChange={(event) => {
                const teamId = Number(event.currentTarget.value);
                if (Number.isFinite(teamId) && teamId > 0) {
                  void selectFixtureTeam(teamId);
                }
              }}
              disabled={fixtureTeamStatus !== "ready" || savingKey !== null}
            >
              <option value="">{fixtureTeamStatus === "loading" ? "팀 목록 불러오는 중..." : "팀을 선택하세요"}</option>
              {fixtureTeams.map((team) => (
              <option key={team.teamId} value={team.teamId}>{adminDisplayName(team.koreanName, team.name, team.teamId)}</option>
              ))}
            </select>
          </label>
          {fixtureTeamStatus === "error" ? <p className="muted admin-sync-message">팀 목록을 불러오지 못했습니다.</p> : null}
          {fixtureListStatus === "loading" ? <p className="muted admin-sync-message">경기 목록을 불러오는 중입니다.</p> : null}
          {fixtureListStatus === "ready" && fixtures.length === 0 ? <p className="muted admin-sync-message">선택한 팀의 경기가 없습니다.</p> : null}
          {fixtures.length > 0 ? (
            <div className="admin-fixture-list" aria-label="선택한 팀의 경기 목록">
              {fixtures.map((fixture) => (
                <button
                  type="button"
                  key={fixture.fixtureId}
                  className={fixtureSelection.fixtureId === fixture.fixtureId ? "active" : ""}
                  onClick={() => void selectFixture(fixture.fixtureId)}
                  disabled={savingKey !== null}
                >
                  <span className="admin-fixture-list-meta">
                    <time>{formatFixtureDate(fixture.fixtureDate)}</time>
                    <span>{fixture.round ? `${fixture.round}라운드` : "라운드 미정"}</span>
                    <span>{fixture.fixtureStatus ?? "-"}</span>
                  </span>
                  <strong>{fixture.homeTeamName ?? "-"} <em>{formatFixtureScore(fixture)}</em> {fixture.awayTeamName ?? "-"}</strong>
                  <small>경기 #{fixture.fixtureId}</small>
                </button>
              ))}
            </div>
          ) : null}
        </div>
        {loadingFixtureId !== null ? <p className="muted admin-sync-message">경기 정보를 불러오는 중입니다.</p> : null}
        {selectedFixture ? (
          <div ref={fixtureEditorRef}>
            <FixtureEditor
              key={selectedFixture.fixture.fixtureId}
              detail={selectedFixture}
              onSaveFixture={saveFixture}
              onSavePayload={saveFixturePayload}
              disabled={savingKey !== null}
            />
          </div>
        ) : null}
      </EditorPanel>
      ) : null}
        </>
      ) : null}

      {activeSection === "sync" ? (
      <section className="panel admin-page-panel admin-utility-section">
        <div className="admin-page-section-heading">
          <span>
            <span className="eyebrow">외부 API</span>
            <strong>제공자 상태 및 API-Football 동기화</strong>
          </span>
        </div>
        <p className="muted admin-sync-message">
          새로운 시즌은 팀 → 순위 순서로 동기화한 뒤 나머지 동기화를 진행해 주세요.
        </p>
        <div className={`admin-api-health admin-api-health-${(apiFootballStatus?.status ?? "NEVER_SYNCED").toLowerCase()}`}>
          <div className="admin-api-health-heading">
            <strong>API-Football 전체 상태</strong>
            <span className={`status-pill sync-status-${(apiFootballStatus?.status ?? "NEVER_SYNCED").toLowerCase()}`}>
              {apiFootballStatusLabel(apiFootballStatus?.status)}
            </span>
          </div>
          <div className="admin-api-health-details">
            <span>마지막 성공: {formatDateTime(lastSuccessfulSyncTime(apiFootballStatus))}</span>
            <span>마지막 시도: {formatDateTime(apiFootballStatus?.lastAttemptAt ?? null)}</span>
            {apiFootballStatus?.lastFailureAt ? <span>마지막 실패: {formatDateTime(apiFootballStatus.lastFailureAt)}</span> : null}
            {(apiFootballStatus?.failureCount ?? 0) > 0 ? <span>기록된 장애: {apiFootballStatus?.failureCount}회</span> : null}
            {apiFootballStatus?.lastOperation ? <span>작업: {externalApiOperationLabel(apiFootballStatus.lastOperation)}</span> : null}
            {apiFootballStatus?.lastAttemptCount ? <span>시도 횟수: {apiFootballStatus.lastAttemptCount}</span> : null}
            {apiFootballStatus?.lastErrorCategory ? <span>오류: {externalApiErrorCategoryLabel(apiFootballStatus.lastErrorCategory)}{apiFootballStatus.lastHttpStatus ? ` (${apiFootballStatus.lastHttpStatus})` : ""}</span> : null}
          </div>
        </div>
        {newsProviderStatuses.map((status) => (
          <div className={`admin-api-health admin-api-health-${(status.status ?? "NEVER_SYNCED").toLowerCase()}`} key={status.task}>
            <div className="admin-api-health-heading">
              <strong>{status.label} 상태</strong>
              <span className={`status-pill sync-status-${(status.status ?? "NEVER_SYNCED").toLowerCase()}`}>
                {apiFootballStatusLabel(status.status)}
              </span>
            </div>
            <div className="admin-api-health-details">
              <span>마지막 성공: {formatDateTime(status.lastSuccessAt)}</span>
              <span>마지막 시도: {formatDateTime(status.lastAttemptAt)}</span>
              {status.lastFailureAt ? <span>마지막 실패: {formatDateTime(status.lastFailureAt)}</span> : null}
              <span>연속 실패: {status.failureCount ?? 0}회</span>
              {status.lastOperation ? <span>작업: {externalApiOperationLabel(status.lastOperation)}</span> : null}
              {status.lastAttemptCount ? <span>시도 횟수: {status.lastAttemptCount}</span> : null}
              {status.lastErrorCategory ? <span>오류: {externalApiErrorCategoryLabel(status.lastErrorCategory)}{status.lastHttpStatus ? ` (${status.lastHttpStatus})` : ""}</span> : null}
            </div>
          </div>
        ))}
        <div className="admin-sync-actions">
          {syncTasks.map((item) => {
            const status = syncStatusByTask.get(item.task);
            const isSyncing = syncingTask === item.task;
            const availability = syncAvailability(item.task, authState.season, seasonCoverages, coverageStatus);
            const cooldownSeconds = syncCooldownSeconds(manualSyncCooldownKey(item.task));
            return (
              <div className="admin-sync-action" key={item.task}>
                <button
                  type="button"
                  onClick={() => void runSync(item.task)}
                  disabled={syncingTask !== null || activeSyncTasks.has(item.task) || cooldownSeconds > 0 || !availability.enabled}
                  aria-disabled={cooldownSeconds > 0}
                >
                  {isSyncing ? <LoaderCircle className="admin-loading-icon" aria-hidden="true" /> : null}
                  {item.label}
                </button>
                <span className={`status-pill sync-status-${(status?.status ?? "NEVER_SYNCED").toLowerCase()}`}>
                  {syncStatusLabel(status?.status)}
                </span>
                <span>마지막 성공: {formatDateTime(lastSuccessfulSyncTime(status))}</span>
                <span>마지막 시도: {formatDateTime(status?.lastAttemptAt ?? null)}</span>
                {status?.lastFailureAt ? <span>마지막 실패: {formatDateTime(status.lastFailureAt)}</span> : null}
                {(status?.failureCount ?? 0) > 0 ? <span className="admin-sync-warning">연속 실패 수: {status?.failureCount}</span> : null}
                {cooldownSeconds > 0 ? <span className="admin-sync-warning">{cooldownSeconds}초 후 재요청 가능</span> : null}
                {!availability.enabled ? <span className="admin-sync-warning">{availability.message}</span> : null}
              </div>
            );
          })}
        </div>
        <div className="admin-sync-jobs" aria-live="polite">
          <div className="admin-sync-jobs-heading">
            <strong>백그라운드 동기화 작업</strong>
            <button type="button" className="section-retry-button" disabled={syncJobsStatus === "loading"} onClick={() => void reloadSyncJobs(false)}>
              {syncJobsStatus === "loading" ? <LoaderCircle className="admin-loading-icon" aria-hidden="true" /> : null}
              새로고침
            </button>
          </div>
          {syncJobsStatus === "loading" && syncJobs.length === 0 ? <p className="muted admin-sync-message">작업 내역을 불러오는 중입니다.</p> : null}
          {syncJobsStatus === "error" ? <p className="admin-inline-error">작업 내역을 불러오지 못했습니다.</p> : null}
          {syncJobsStatus === "ready" && syncJobs.length === 0 ? <p className="muted admin-sync-message">최근 비동기 작업이 없습니다.</p> : null}
          <SyncJobSection
            title="현재 진행 중인 작업"
            jobs={runningSyncJobs}
            emptyMessage="현재 실행 중인 작업이 없습니다."
            cancellingJobId={cancellingJobId}
            onCancel={cancelSyncJob}
          />
          <SyncJobSection
            title="대기 중인 작업"
            jobs={queuedSyncJobs}
            emptyMessage="대기 중인 작업이 없습니다."
            cancellingJobId={cancellingJobId}
            onCancel={cancelSyncJob}
          />
          <SyncJobSection
            jobs={completedSyncJobs}
            title="최근 완료된 작업"
            emptyMessage="최근 완료된 작업이 없습니다."
            cancellingJobId={cancellingJobId}
            onCancel={cancelSyncJob}
            collapsible
          />
        </div>
        {coverageStatus === "error" ? (
          <p className="muted admin-sync-message">시즌 지원 범위를 불러오지 못해 수동 동기화 요청을 잠시 막았습니다.</p>
        ) : null}
        {coverageStatus === "ready" && !selectedCoverage ? (
          <p className="muted admin-sync-message">선택한 시즌은 API-Football 지원 범위 정보가 없어 수동 동기화를 요청할 수 없습니다.</p>
        ) : null}
      </section>
      ) : null}

      {activeSection === "logs" ? (
      <section className="panel admin-page-panel admin-utility-section">
        <div className="admin-page-section-heading">
          <span>
            <span className="eyebrow">감사 기록</span>
            <strong>최근 관리자 로그</strong>
          </span>
          <button type="button" className="section-retry-button" disabled={auditStatus === "loading"} onClick={() => void reloadAuditLogs()}>
            {auditStatus === "loading" ? <LoaderCircle className="admin-loading-icon" aria-hidden="true" /> : null}
            {auditStatus === "loading" ? "새로고침 중" : "새로고침"}
          </button>
        </div>
        {auditStatus === "loading" && logs.length === 0 ? <p className="muted admin-sync-message">관리자 로그를 불러오는 중입니다.</p> : null}
        {auditStatus === "loading" && logs.length > 0 ? <p className="muted admin-sync-message" role="status">기존 로그를 표시한 채 새 내역을 확인하고 있습니다.</p> : null}
        {auditStatus === "error" ? <p className="admin-inline-error" role="alert">{auditError}</p> : null}
        <div className="admin-log-list">
          {logs.map((log) => (
            <article className={`admin-log-item ${log.success ? "success" : "failed"}`} key={log.id}>
              <div className="admin-log-badges">
                <span className="status-pill">{adminAuditTypeLabel(log.type)}</span>
                {log.provider ? <span className="status-pill admin-log-category">{externalApiProviderLabel(log.provider)}</span> : null}
                {log.syncCategory ? <span className="status-pill admin-log-category">{syncCategoryLabel(log.syncCategory)}</span> : null}
              </div>
              <div>
                <strong>{log.message}</strong>
                {log.details ? <p className="muted">{adminAuditDetailsLabel(log.details)}</p> : null}
                <p className="muted">
                  {log.adminEmail ?? "-"} · {formatDateTime(log.createdAt)}
                </p>
              </div>
              <span className="status-pill admin-log-result">{log.success ? "성공" : "실패"}</span>
            </article>
          ))}
        </div>
        <div className="admin-pagination">
          <button type="button" disabled={auditStatus === "loading" || auditPage <= 0} onClick={() => void reloadAuditLogs(auditPage - 1)}>
            이전
          </button>
          <span>
            {auditTotalPages === 0 ? 0 : auditPage + 1} / {auditTotalPages} 페이지 · 총 {auditTotalElements}건
          </span>
          <button type="button" disabled={auditStatus === "loading" || auditPage + 1 >= auditTotalPages} onClick={() => void reloadAuditLogs(auditPage + 1)}>
            다음
          </button>
        </div>
      </section>
      ) : null}
    </section>
  );
}

function SyncJobSection({
  title,
  jobs,
  emptyMessage,
  cancellingJobId,
  onCancel,
  collapsible = false,
}: {
  title: string;
  jobs: SyncJob[];
  emptyMessage: string;
  cancellingJobId: number | null;
  onCancel: (job: SyncJob) => Promise<void>;
  collapsible?: boolean;
}) {
  const completedCounts = jobs.reduce(
    (counts, job) => {
      if (job.status === "SUCCEEDED") {
        counts.succeeded += 1;
      } else if (job.status === "FAILED" || job.status === "PARTIAL_FAILED") {
        counts.failed += 1;
      } else if (job.status === "CANCELLED") {
        counts.cancelled += 1;
      }
      return counts;
    },
    { succeeded: 0, failed: 0, cancelled: 0 },
  );
  const content = (
    <>
      {jobs.length === 0 ? <p className="muted admin-sync-job-empty">{emptyMessage}</p> : null}
      {jobs.map((job) => (
        <SyncJobCard
          key={job.id}
          job={job}
          cancelling={cancellingJobId === job.id}
          onCancel={onCancel}
        />
      ))}
    </>
  );

  if (collapsible) {
    return (
      <details className="admin-sync-job-section admin-sync-job-section-collapsible">
        <summary>
          <div className="admin-sync-job-summary-heading">
            <h4>{title} <span>{jobs.length}</span></h4>
            <div className="admin-sync-job-summary-counts" aria-label={`성공 ${completedCounts.succeeded}, 실패 ${completedCounts.failed}, 취소 ${completedCounts.cancelled}`}>
              <span className="succeeded">성공 {completedCounts.succeeded}</span>
              <i aria-hidden="true">/</i>
              <span className="failed">실패 {completedCounts.failed}</span>
              <i aria-hidden="true">/</i>
              <span className="cancelled">취소 {completedCounts.cancelled}</span>
            </div>
          </div>
          <ChevronDown size={18} aria-hidden="true" />
        </summary>
        {content}
      </details>
    );
  }

  return (
    <section className="admin-sync-job-section">
      <h4>{title} <span>{jobs.length}</span></h4>
      {content}
    </section>
  );
}

function SyncJobCard({
  job,
  cancelling,
  onCancel,
}: {
  job: SyncJob;
  cancelling: boolean;
  onCancel: (job: SyncJob) => Promise<void>;
}) {
  const percent = job.totalUnits > 0
    ? Math.min(100, Math.round((job.processedUnits / job.totalUnits) * 100))
    : null;
  const cancellable = job.status === "QUEUED" || job.status === "RUNNING";
  return (
    <article className={`admin-sync-job ${job.status.toLowerCase()}`}>
      <div className="admin-sync-job-heading">
        <div>
          <strong>{syncTaskLabel(job.task)}</strong>
          <span>{job.details ? adminAuditDetailsLabel(job.details) : `작업 #${job.id}`}</span>
        </div>
        <span className="status-pill">{syncJobStatusLabel(job.status)}</span>
      </div>
      {job.active ? (
        percent === null ? (
          <div className="admin-sync-indeterminate"><span /></div>
        ) : (
          <div className="admin-sync-progress" role="progressbar" aria-valuemin={0} aria-valuemax={100} aria-valuenow={percent}>
            <span style={{ width: `${percent}%` }} />
          </div>
        )
      ) : null}
      <p className="admin-sync-phase">{syncJobPhaseLabel(job.phase, job.status)}</p>
      <p className="muted admin-sync-job-counts">
        처리 {job.processedUnits}/{job.totalUnits || "?"} {syncUnitLabel(job.unitLabel)} · 성공 {job.successfulUnits} · 실패 {job.failedUnits} · 저장 {job.savedCount}
      </p>
      <p className="muted">{job.adminEmail ?? "-"} · 요청 {formatDateTime(job.createdAt)}{job.startedAt ? ` · 시작 ${formatDateTime(job.startedAt)}` : ""}{job.completedAt ? ` · 완료 ${formatDateTime(job.completedAt)}` : ""}</p>
      {cancellable || job.status === "CANCEL_REQUESTED" ? (
        <button
          type="button"
          className="section-retry-button"
          disabled={!cancellable || cancelling}
          onClick={() => void onCancel(job)}
        >
          {cancelling ? <LoaderCircle className="admin-loading-icon" aria-hidden="true" /> : null}
          {job.status === "CANCEL_REQUESTED" ? "현재 처리 단위 완료 후 취소 중" : "작업 취소"}
        </button>
      ) : null}
      {job.errors.length > 0 ? (
        <details className="admin-sync-errors">
          <summary>오류 내역 {job.errors.length}건</summary>
          <ul>
            {job.errors.map((jobError, index) => (
              <li key={`${jobError.unitType}:${jobError.unitId ?? index}`}>
                <strong>{syncUnitLabel(jobError.unitType)}{jobError.unitId ? ` ${jobError.unitId}` : ""}</strong>
                <span>{jobError.message}</span>
              </li>
            ))}
          </ul>
        </details>
      ) : null}
    </article>
  );
}

function EditorPanel({
  title,
  eyebrow,
  children,
}: {
  title: string;
  eyebrow: string;
  children: React.ReactNode;
}) {
  return (
    <section className="panel admin-page-panel">
      <div className="panel-heading">
        <div>
          <p className="eyebrow">{eyebrow}</p>
          <h2>{title}</h2>
        </div>
      </div>
      <div className="admin-search-form">{children}</div>
    </section>
  );
}

function SearchRow({
  value,
  placeholder,
  onChange,
  onSubmit,
}: {
  value: string;
  placeholder: string;
  onChange: (value: string) => void;
  onSubmit: (event: FormEvent<HTMLFormElement>) => void;
}) {
  return (
    <form className="admin-search-row" onSubmit={onSubmit}>
      <input
        type="search"
        value={value}
        placeholder={placeholder}
        maxLength={ADMIN_SEARCH_KEYWORD_MAX_LENGTH}
        onChange={(event) => onChange(event.target.value)}
      />
      <button type="submit">검색</button>
    </form>
  );
}

function ResultList<T>({
  items,
  getKey,
  render,
  onSelect,
  disabled,
}: {
  items: T[];
  getKey: (item: T) => string | number;
  render: (item: T) => string;
  onSelect: (item: T) => void;
  disabled?: boolean;
}) {
  if (!items.length) {
    return null;
  }

  return (
    <div className="admin-result-list">
      {items.map((item) => (
        <button type="button" key={getKey(item)} onClick={() => onSelect(item)} disabled={disabled}>
          {render(item)}
        </button>
      ))}
    </div>
  );
}

function AdminMediaUpload({
  label,
  imageUrl,
  adminImage,
  targetType,
  targetId,
  disabled,
  unavailableMessage,
  onUpload,
  onRestore,
}: {
  label: string;
  imageUrl: string | null;
  adminImage: boolean;
  targetType: AdminMediaTargetType;
  targetId: number | null;
  disabled?: boolean;
  unavailableMessage?: string;
  onUpload: (targetType: AdminMediaTargetType, targetId: number, file: File) => Promise<string | null>;
  onRestore: (targetType: AdminMediaTargetType, targetId: number) => Promise<void>;
}) {
  const inputRef = useRef<HTMLInputElement>(null);
  const [imageFailed, setImageFailed] = useState(false);
  const [failedUpload, setFailedUpload] = useState<{ file: File; message: string } | null>(null);
  const available = targetId !== null && Number.isFinite(targetId) && targetId > 0;

  useEffect(() => {
    setImageFailed(false);
  }, [imageUrl]);

  useEffect(() => {
    setFailedUpload(null);
  }, [targetType, targetId]);

  async function tryUpload(file: File) {
    if (!available || targetId === null) {
      return;
    }
    setFailedUpload(null);
    const failureMessage = await onUpload(targetType, targetId, file);
    if (failureMessage) {
      setFailedUpload({ file, message: failureMessage });
    }
  }

  async function selectFile(event: React.ChangeEvent<HTMLInputElement>) {
    const file = event.currentTarget.files?.[0];
    event.currentTarget.value = "";
    if (!file || !available || targetId === null) {
      return;
    }
    await tryUpload(file);
  }

  async function restore() {
    if (!available || targetId === null || !adminImage) {
      return;
    }
    if (!window.confirm(`${label}를 원본 이미지로 복원할까요?`)) {
      return;
    }
    await onRestore(targetType, targetId);
  }

  return (
    <section className="admin-media-card">
      <div className="admin-media-heading">
        <strong>{label}</strong>
        <span className={adminImage ? "status-pill" : "muted"}>{adminImage ? "관리자 이미지" : "원본 이미지"}</span>
      </div>
      <div className="admin-media-preview">
        {imageUrl && !imageFailed ? (
          <img src={imageUrl} alt={`${label} 미리보기`} onError={() => setImageFailed(true)} />
        ) : (
          <span>이미지 없음</span>
        )}
      </div>
      {!available && unavailableMessage ? <p className="admin-media-help">{unavailableMessage}</p> : null}
      {failedUpload ? (
        <div className="admin-media-upload-error" role="alert">
          <div>
            <strong>{failedUpload.file.name}</strong>
            <span>{failedUpload.message}</span>
          </div>
          <button type="button" onClick={() => void tryUpload(failedUpload.file)} disabled={disabled || !available}>
            다시 시도
          </button>
        </div>
      ) : null}
      <div className="admin-media-actions">
        <input
          ref={inputRef}
          type="file"
          accept="image/png,image/jpeg,image/webp"
          onChange={selectFile}
          disabled={disabled || !available}
          hidden
        />
        <button type="button" onClick={() => inputRef.current?.click()} disabled={disabled || !available}>
          {disabled ? <LoaderCircle className="admin-loading-icon" aria-hidden="true" /> : null}
          이미지 업로드
        </button>
        <button type="button" onClick={restore} disabled={disabled || !available || !adminImage}>
          원본으로 복원
        </button>
      </div>
      <small>PNG, JPEG, WebP · 최대 2MB</small>
    </section>
  );
}

function AdminForm({
  title,
  fields,
  value,
  overrides,
  onClearOverrides,
  onClearOverride,
  submitLabel,
  onSubmit,
  disabled,
}: {
  title: string;
  fields: FieldConfig[];
  value: Record<string, unknown>;
  overrides?: AdminOverride[];
  onClearOverrides?: () => void;
  onClearOverride?: (fieldName: string) => void;
  submitLabel: string;
  onSubmit: (event: FormEvent<HTMLFormElement>) => void;
  disabled?: boolean;
}) {
  const overrideSet = new Set((overrides ?? []).map((override) => override.fieldName));
  return (
    <form className="admin-edit-form" onSubmit={onSubmit}>
      <h3>{title}</h3>
      <AdminOverrideStatus
        overrides={overrides}
        fields={fields}
        onClear={onClearOverrides}
        onClearField={onClearOverride}
        disabled={disabled}
      />
      <div className="admin-form-grid">
        {fields.map((field) => (
          <AdminField key={field.name} field={field} value={value[field.name]} overridden={overrideSet.has(field.name)} />
        ))}
      </div>
      <button type="submit" disabled={disabled}>{submitLabel}</button>
    </form>
  );
}

function AdminOverrideStatus({
  overrides,
  fields,
  onClear,
  onClearField,
  disabled,
}: {
  overrides?: AdminOverride[];
  fields: FieldConfig[];
  onClear?: () => void;
  onClearField?: (fieldName: string) => void;
  disabled?: boolean;
}) {
  if (!overrides || overrides.length === 0) {
    return null;
  }
  const labels = new Map(fields.map((field) => [field.name, field.label]));
  return (
    <details className="admin-override-status">
      <summary>
        <strong>관리자 수정 적용 중</strong>
        <span>{overrides.length}개 항목</span>
      </summary>
      <div className="admin-override-status-body">
        <div className="admin-override-status-heading">
          <p>아래 항목은 외부 API 동기화에서 제외됩니다.</p>
          {onClear ? (
            <button type="button" onClick={onClear} disabled={disabled}>
              전체 초기화
            </button>
          ) : null}
        </div>
        <ul>
          {overrides.map((override) => (
            <li key={override.fieldName}>
              <span>{labels.get(override.fieldName) ?? override.fieldName}</span>
              <time dateTime={override.updatedAt ?? undefined}>{formatDateTime(override.updatedAt)}</time>
              {onClearField ? (
                <button
                  type="button"
                  onClick={() => onClearField(override.fieldName)}
                  disabled={disabled}
                >
                  초기화
                </button>
              ) : null}
            </li>
          ))}
        </ul>
      </div>
    </details>
  );
}

function fieldLabel(fields: FieldConfig[], fieldName: string) {
  return fields.find((field) => field.name === fieldName)?.label ?? fieldName;
}

function FixtureEditor({
  detail,
  onSaveFixture,
  onSavePayload,
  disabled,
}: {
  detail: FixtureDetailAdmin;
  onSaveFixture: (event: FormEvent<HTMLFormElement>) => void;
  onSavePayload: (fixtureId: number, url: string, body: Record<string, unknown>, successMessage: string, method?: string) => Promise<void>;
  disabled?: boolean;
}) {
  const fixtureId = detail.fixture.fixtureId;
  const finishedDataEditingDisabled = disabled || !isFinishedFixture(detail.fixture);
  const [addingEvent, setAddingEvent] = useState(false);
  const [newEventValue, setNewEventValue] = useState(() => newFixtureEventValue(detail));

  useEffect(() => {
    if (!addingEvent) {
      setNewEventValue(newFixtureEventValue(detail));
    }
  }, [addingEvent, detail]);

  function clearFixtureOverrides(url: string, label: string, fieldName?: string) {
    const targetLabel = fieldName ? fieldLabel(
      fieldName === "events"
        ? eventOverrideFields
        : [...fixtureFields, ...lineupFields, ...teamStatFields, ...playerStatFields],
      fieldName,
    ) : label;
    const scopeText = fieldName ? targetLabel : `${label} 전체`;
    if (!window.confirm(`${scopeText}의 관리자 수정 상태를 초기화할까요? 다음 동기화부터 API 값이 다시 반영됩니다.`)) {
      return;
    }
    const endpoint = fieldName ? `${url}/${encodeURIComponent(fieldName)}` : url;
    void onSavePayload(
      fixtureId,
      endpoint,
      {},
      `${scopeText}의 관리자 수정 상태를 초기화했습니다.`,
      "DELETE",
    );
  }

  function deleteFixtureEvent(event: FixtureEventAdmin) {
    const eventLabel = `#${event.eventSequence} ${eventTypeLabel(event.eventType)} ${adminName(event.playerNameKo, event.playerName)}`;
    if (!window.confirm(`${eventLabel} 이벤트를 삭제할까요? 삭제한 이벤트는 관리자 수정 적용 중 상태가 해제되기 전까지 동기화에서 제외됩니다.`)) {
      return;
    }
    void onSavePayload(
      fixtureId,
      `/api/v1/admin/fixtures/${fixtureId}/events/${event.eventSequence}`,
      {},
      `${eventLabel} 이벤트를 삭제했습니다.`,
      "DELETE",
    );
  }

  return (
    <div className="fixture-admin-editor">
      <NestedAdminSection title="경기 기본 정보" count={1}>
        <AdminForm
          title={`${detail.fixture.homeTeamName ?? "-"} 대 ${detail.fixture.awayTeamName ?? "-"}`}
          fields={fixtureFields}
          value={detail.fixture}
          overrides={detail.fixture.manualOverrides}
          onClearOverrides={() => clearFixtureOverrides(
            `/api/v1/admin/fixtures/${fixtureId}/overrides`,
            "경기 기본 정보",
          )}
          onClearOverride={(fieldName) => clearFixtureOverrides(
            `/api/v1/admin/fixtures/${fixtureId}/overrides`,
            "경기 기본 정보",
            fieldName,
          )}
          submitLabel="경기 정보 저장"
          onSubmit={onSaveFixture}
          disabled={disabled}
        />
      </NestedAdminSection>
      <NestedAdminSection title="경기 이벤트" count={detail.events.length}>
        {finishedDataEditingDisabled ? <p className="muted">종료된 경기의 이벤트만 수정할 수 있습니다.</p> : null}
        <AdminOverrideStatus
          overrides={detail.eventOverrides}
          fields={eventOverrideFields}
          onClear={() => clearFixtureOverrides(
            `/api/v1/admin/fixtures/${fixtureId}/events/overrides`,
            "경기 이벤트",
          )}
          onClearField={(fieldName) => clearFixtureOverrides(
            `/api/v1/admin/fixtures/${fixtureId}/events/overrides`,
            "경기 이벤트",
            fieldName,
          )}
          disabled={disabled}
        />
        <button type="button" className="admin-add-button" onClick={() => setAddingEvent((current) => !current)} disabled={finishedDataEditingDisabled}>
          {addingEvent ? "이벤트 추가 취소" : "이벤트 추가"}
        </button>
        {addingEvent ? (
          <details className="nested-admin-item" open>
            <summary>새 이벤트</summary>
            <EventAdminForm
              title="새 이벤트"
              detail={detail}
              value={newEventValue}
              submitLabel="이벤트 생성"
              onSubmit={(body) => {
                void onSavePayload(
                  fixtureId,
                  `/api/v1/admin/fixtures/${fixtureId}/events`,
                  body,
                  "이벤트를 추가했습니다.",
                  "POST",
                ).then(() => setAddingEvent(false));
              }}
              disabled={finishedDataEditingDisabled}
            />
          </details>
        ) : null}
        {detail.events.map((event) => (
          <details className="nested-admin-item" key={event.eventSequence}>
            <summary>#{event.eventSequence} {eventTypeLabel(event.eventType)} {adminName(event.playerNameKo, event.playerName)}</summary>
            <EventAdminForm
              title={`#${event.eventSequence} ${eventTypeLabel(event.eventType)} ${adminName(event.playerNameKo, event.playerName)}`}
              detail={detail}
              value={event}
              submitLabel="이벤트 저장"
              onSubmit={(body) => {
                void onSavePayload(
                  fixtureId,
                  `/api/v1/admin/fixtures/${fixtureId}/events/${event.eventSequence}`,
                  body,
                  "이벤트를 저장했습니다.",
                );
              }}
              onDelete={() => deleteFixtureEvent(event)}
              disabled={finishedDataEditingDisabled}
            />
          </details>
        ))}
      </NestedAdminSection>
      <NestedAdminSection title="라인업" count={detail.lineups.length}>
        {detail.lineups.map((lineup) => (
          <details className="nested-admin-item" key={`${lineup.teamId}-${lineup.playerId}`}>
            <summary>{adminName(lineup.teamNameKo, lineup.teamName)} · {adminName(lineup.playerNameKo, lineup.playerName)}</summary>
            <AdminForm
            title={`${adminName(lineup.teamNameKo, lineup.teamName)} · ${adminName(lineup.playerNameKo, lineup.playerName)}`}
            fields={lineupFields}
            value={lineup}
            overrides={lineup.manualOverrides}
            onClearOverrides={() => clearFixtureOverrides(
              `/api/v1/admin/fixtures/${fixtureId}/lineups/${lineup.teamId}/${lineup.playerId}/overrides`,
              "라인업",
            )}
            onClearOverride={(fieldName) => clearFixtureOverrides(
              `/api/v1/admin/fixtures/${fixtureId}/lineups/${lineup.teamId}/${lineup.playerId}/overrides`,
              "라인업",
              fieldName,
            )}
            submitLabel="라인업 저장"
            onSubmit={(submitEvent) => {
              submitEvent.preventDefault();
              void onSavePayload(
                fixtureId,
                `/api/v1/admin/fixtures/${fixtureId}/lineups/${lineup.teamId}/${lineup.playerId}`,
                {
                  ...formBody(submitEvent.currentTarget, lineupFields),
                  version: lineup.version,
                },
                "라인업을 저장했습니다.",
              );
            }}
            disabled={disabled}
            />
          </details>
        ))}
      </NestedAdminSection>
      <NestedAdminSection title="팀 경기 통계" count={detail.teamStats.length}>
        {finishedDataEditingDisabled ? <p className="muted">종료된 경기의 팀별 스탯만 수정할 수 있습니다.</p> : null}
        {detail.teamStats.map((stat) => (
          <details className="nested-admin-item" key={stat.teamId}>
            <summary>{stat.teamName ?? "팀"}</summary>
            <AdminForm
            title={stat.teamName ?? "팀"}
            fields={teamStatFields}
            value={stat}
            overrides={stat.manualOverrides}
            onClearOverrides={() => clearFixtureOverrides(
              `/api/v1/admin/fixtures/${fixtureId}/team-stats/${stat.teamId}/overrides`,
              `${stat.teamName ?? "팀"} 경기 스탯`,
            )}
            onClearOverride={(fieldName) => clearFixtureOverrides(
              `/api/v1/admin/fixtures/${fixtureId}/team-stats/${stat.teamId}/overrides`,
              `${stat.teamName ?? "팀"} 경기 스탯`,
              fieldName,
            )}
            submitLabel="팀 통계 저장"
            onSubmit={(submitEvent) => {
              submitEvent.preventDefault();
              void onSavePayload(
                fixtureId,
                `/api/v1/admin/fixtures/${fixtureId}/team-stats/${stat.teamId}`,
                {
                  ...formBody(submitEvent.currentTarget, teamStatFields),
                  version: stat.version,
                },
                "팀 경기 통계를 저장했습니다.",
              );
            }}
            disabled={finishedDataEditingDisabled}
            />
          </details>
        ))}
      </NestedAdminSection>
      <NestedAdminSection title="선수 경기 통계" count={detail.playerStats.length}>
        {finishedDataEditingDisabled ? <p className="muted">종료된 경기의 선수별 스탯만 수정할 수 있습니다.</p> : null}
        {detail.playerStats.map((stat) => (
          <details className="nested-admin-item" key={stat.playerId}>
            <summary>{adminName(stat.playerNameKo, stat.playerName)} · {adminName(stat.teamNameKo, stat.teamName)}</summary>
            <AdminForm
            title={`${adminName(stat.playerNameKo, stat.playerName)} · ${adminName(stat.teamNameKo, stat.teamName)}`}
            fields={playerStatFields}
            value={stat}
            overrides={stat.manualOverrides}
            onClearOverrides={() => clearFixtureOverrides(
              `/api/v1/admin/fixtures/${fixtureId}/player-stats/${stat.playerId}/overrides`,
              `${adminName(stat.playerNameKo, stat.playerName)} 경기 스탯`,
            )}
            onClearOverride={(fieldName) => clearFixtureOverrides(
              `/api/v1/admin/fixtures/${fixtureId}/player-stats/${stat.playerId}/overrides`,
              `${adminName(stat.playerNameKo, stat.playerName)} 경기 스탯`,
              fieldName,
            )}
            submitLabel="선수 통계 저장"
            onSubmit={(submitEvent) => {
              submitEvent.preventDefault();
              void onSavePayload(
                fixtureId,
                `/api/v1/admin/fixtures/${fixtureId}/player-stats/${stat.playerId}`,
                {
                  ...formBody(submitEvent.currentTarget, playerStatFields),
                  version: stat.version,
                },
                "선수 경기 통계를 저장했습니다.",
              );
            }}
            disabled={finishedDataEditingDisabled}
            />
          </details>
        ))}
      </NestedAdminSection>
    </div>
  );
}

function isFinishedFixture(fixture: FixtureAdmin) {
  return fixture.fixtureStatus?.toUpperCase() === "FINISHED"
    || ["FT", "AET", "PEN"].includes(fixture.statusShort?.toUpperCase() ?? "");
}

function NestedAdminSection({ title, count, children }: { title: string; count?: number; children: React.ReactNode }) {
  return (
    <details className="nested-admin-section">
      <summary>
        <span>{title}</span>
        {typeof count === "number" ? <span className="small-count">{count}</span> : null}
      </summary>
      <div className="nested-admin-grid">{children}</div>
    </details>
  );
}

function newFixtureEventValue(detail: FixtureDetailAdmin): Record<string, unknown> {
  return {
    teamId: detail.fixture.homeTeamId,
    playerId: null,
    assistPlayerId: null,
    elapsed: 0,
    extra: null,
    eventType: "Goal",
    eventDetail: "Normal Goal",
    comments: "",
  };
}

function EventAdminForm({
  title,
  detail,
  value,
  submitLabel,
  onSubmit,
  onDelete,
  disabled,
}: {
  title: string;
  detail: FixtureDetailAdmin;
  value: Record<string, unknown>;
  submitLabel: string;
  onSubmit: (body: Record<string, unknown>) => void;
  onDelete?: () => void;
  disabled?: boolean;
}) {
  const [draft, setDraft] = useState(() => eventDraftValue(value));

  useEffect(() => {
    setDraft(eventDraftValue(value));
  }, [value]);

  const selectedType = normalizeEventType(draft.eventType);
  const selectedTeamId = numericId(draft.teamId);
  const fields = eventFieldsWithOptions(detail, selectedType, selectedTeamId);
  const formValue: Record<string, unknown> = {
    ...draft,
    eventType: selectedType,
    eventDetail: validEventDetail(selectedType, draft.eventDetail),
  };

  function updateDraft(field: FieldConfig, nextValue: string) {
    setDraft((current) => {
      const next = { ...current, [field.name]: nextValue };
      if (field.name === "teamId") {
        return {
          ...next,
          playerId: null,
          assistPlayerId: null,
        };
      }
      if (field.name !== "eventType") {
        return next;
      }
      const nextType = normalizeEventType(nextValue);
      return {
        ...next,
        eventType: nextType,
        eventDetail: validEventDetail(nextType, current.eventDetail),
      };
    });
  }

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    onSubmit(payloadFromDraft(formValue, fields));
  }

  return (
    <form className="admin-edit-form" onSubmit={submit}>
      <h3>{title}</h3>
      <div className="admin-form-grid">
        {fields.map((field) => (
          <AdminField
            key={field.name === "eventDetail" ? `${field.name}-${selectedType}` : field.name}
            field={field}
            value={formValue[field.name]}
            onChange={(nextValue) => updateDraft(field, nextValue)}
          />
        ))}
      </div>
      <button type="submit" disabled={disabled}>{submitLabel}</button>
      {onDelete ? (
        <button type="button" className="admin-delete-button" onClick={onDelete} disabled={disabled}>
          이벤트 삭제
        </button>
      ) : null}
    </form>
  );
}

function eventDraftValue(value: Record<string, unknown>): Record<string, unknown> {
  const eventType = normalizeEventType(value.eventType);
  return {
    ...value,
    eventType,
    eventDetail: validEventDetail(eventType, value.eventDetail),
  };
}

function validEventDetail(eventType: string, value: unknown) {
  const options = eventDetailOptions(eventType);
  return typeof value === "string" && options.some((option) => option.value === value) ? value : options[0]?.value ?? "";
}

function payloadFromDraft(draft: Record<string, unknown>, fields: FieldConfig[]) {
  const body: Record<string, unknown> = {};
  fields.forEach((field) => {
    body[field.name] = coerceValue(draft[field.name], field.kind);
  });
  return body;
}

function eventFieldsWithOptions(
  detail: FixtureDetailAdmin,
  selectedType: string,
  selectedTeamId: number | null,
): FieldConfig[] {
  const teamOptions: FieldOption[] = [
    {
      value: Number(detail.fixture.homeTeamId),
      label: detail.fixture.homeTeamName ?? "홈팀",
    },
    {
      value: Number(detail.fixture.awayTeamId),
      label: detail.fixture.awayTeamName ?? "원정팀",
    },
  ].filter((option) => Number.isFinite(option.value));
  const playerOptions = fixturePlayerOptions(detail, selectedTeamId);
  const substitution = selectedType === "Subst";

  return eventFields.map((field) => {
    if (field.name === "teamId") {
      return { ...field, options: teamOptions };
    }
    if (field.name === "playerId" || field.name === "assistPlayerId") {
      const label = substitution
        ? field.name === "playerId" ? "교체 아웃" : "교체 인"
        : field.label;
      return { ...field, label, options: playerOptions };
    }
    if (field.name === "eventType") {
      return { ...field, options: eventTypeOptions };
    }
    if (field.name === "eventDetail") {
      return { ...field, options: eventDetailOptions(selectedType) };
    }
    return field;
  });
}

function eventDetailOptions(selectedType: string) {
  return eventDetailOptionsByType[selectedType] ?? [];
}

function normalizeEventType(value: unknown) {
  if (typeof value !== "string") {
    return "Goal";
  }
  const normalized = value.trim().toLowerCase();
  if (normalized === "goal") {
    return "Goal";
  }
  if (normalized === "card") {
    return "Card";
  }
  if (normalized === "subst" || normalized === "substitution") {
    return "Subst";
  }
  if (normalized === "var") {
    return "Var";
  }
  return "Goal";
}

function fixturePlayerOptions(detail: FixtureDetailAdmin, selectedTeamId: number | null): FieldOption[] {
  if (selectedTeamId === null) {
    return [];
  }
  const players = new Map<number, string>();
  detail.lineups.forEach((lineup) => {
    if (lineup.teamId === selectedTeamId && Number.isFinite(lineup.playerId)) {
      players.set(lineup.playerId, `${adminName(lineup.playerNameKo, lineup.playerName)} · ${adminName(lineup.teamNameKo, lineup.teamName)}`);
    }
  });
  detail.playerStats.forEach((stat) => {
    if (stat.teamId === selectedTeamId && Number.isFinite(stat.playerId) && !players.has(stat.playerId)) {
      players.set(stat.playerId, `${adminName(stat.playerNameKo, stat.playerName)} · ${adminName(stat.teamNameKo, stat.teamName)}`);
    }
  });

  return Array.from(players.entries())
    .sort((left, right) => left[1].localeCompare(right[1]))
    .map(([value, label]) => ({ value, label }));
}

function numericId(value: unknown): number | null {
  if (value === null || value === undefined || value === "") {
    return null;
  }
  const parsed = typeof value === "number" ? value : Number(value);
  return Number.isFinite(parsed) ? parsed : null;
}

function AdminField({
  field,
  value,
  overridden,
  onChange,
}: {
  field: FieldConfig;
  value: unknown;
  overridden?: boolean;
  onChange?: (value: string) => void;
}) {
  if (field.kind === "select") {
    const selectValue = value === null || value === undefined ? "" : String(value);
    return (
      <label>
        <span>{field.label}{overridden ? " · 수동 수정" : ""}</span>
        <select
          name={field.name}
          value={onChange ? selectValue : undefined}
          defaultValue={onChange ? undefined : selectValue}
          onChange={(event) => onChange?.(event.currentTarget.value)}
        >
          <option value="">-</option>
          {(field.options ?? []).map((option) => (
            <option key={option.value} value={option.value}>
              {option.label}
            </option>
          ))}
        </select>
        {field.help ? <small className="admin-field-help">{field.help}</small> : null}
      </label>
    );
  }

  if (field.kind === "boolean") {
    const booleanValue = value === null || value === undefined ? "" : String(value);
    return (
      <label>
        <span>{field.label}{overridden ? " · 수동 수정" : ""}</span>
        <select
          name={field.name}
          value={onChange ? booleanValue : undefined}
          defaultValue={onChange ? undefined : booleanValue}
          onChange={(event) => onChange?.(event.currentTarget.value)}
        >
          <option value="">-</option>
          <option value="true">예</option>
          <option value="false">아니요</option>
        </select>
        {field.help ? <small className="admin-field-help">{field.help}</small> : null}
      </label>
    );
  }

  if (field.preview === "color") {
    return <ColorPreviewField field={field} value={value} overridden={overridden} />;
  }

  const textValue = inputValue(value, field.kind);
  return (
    <label>
      <span>{field.label}{overridden ? " · 수동 수정" : ""}</span>
      <input
        name={field.name}
        type={field.kind === "number" ? "number" : field.kind === "date" ? "date" : field.kind === "datetime" ? "datetime-local" : "text"}
        step={field.kind === "number" ? "any" : undefined}
        min={field.min}
        max={field.max}
        value={onChange ? textValue : undefined}
        defaultValue={onChange ? undefined : textValue}
        onChange={(event) => onChange?.(event.currentTarget.value)}
      />
      {field.help ? <small className="admin-field-help warning">{field.help}</small> : null}
    </label>
  );
}

function ColorPreviewField({ field, value, overridden }: { field: FieldConfig; value: unknown; overridden?: boolean }) {
  const [colorCode, setColorCode] = useState(normalizeHexCode(inputValue(value, field.kind)));
  const pickerColor = colorInputValue(colorCode);

  useEffect(() => {
    setColorCode(normalizeHexCode(inputValue(value, field.kind)));
  }, [field.kind, value]);

  return (
    <label>
      <span>{field.label}{overridden ? " · 수동 수정" : ""}</span>
      <div className="admin-color-field">
        <input
          name={field.name}
          type="text"
          value={colorCode}
          maxLength={6}
          onChange={(event) => setColorCode(normalizeHexCode(event.target.value))}
          placeholder="ffffff"
        />
        <input
          className={`admin-color-preview${pickerColor ? "" : " empty"}`}
          type="color"
          value={pickerColor || "#ffffff"}
          onChange={(event) => setColorCode(event.target.value.replace("#", "").toUpperCase())}
          aria-label={`${field.label} 미리보기`}
        />
      </div>
      {field.help ? <small className="admin-field-help">{field.help}</small> : null}
    </label>
  );
}

function formBody(form: HTMLFormElement, fields: FieldConfig[]) {
  const body: Record<string, unknown> = {};
  const formData = new FormData(form);
  fields.forEach((field) => {
    const value = formData.get(field.name);
    body[field.name] = coerceValue(value, field.kind);
  });
  return body;
}

function adminDisplayName(koreanName: string | null | undefined, name: string | null | undefined, id: number) {
  const primary = koreanName?.trim() || name?.trim();
  if (!primary) {
    return `#${id}`;
  }
  return koreanName?.trim() && name?.trim() && koreanName.trim() !== name.trim()
    ? `${koreanName.trim()} (${name.trim()}) #${id}`
    : `${primary} #${id}`;
}

function adminName(koreanName: string | null | undefined, name: string | null | undefined) {
  return koreanName?.trim() || name?.trim() || "-";
}

function coerceValue(value: unknown, kind: FieldKind = "text") {
  if (value === null || value === undefined) {
    return null;
  }
  const text = String(value).trim();
  if (text === "") {
    return null;
  }
  if (kind === "number") {
    return Number(text);
  }
  if (kind === "select") {
    return /^-?\d+$/.test(text) ? Number(text) : text;
  }
  if (kind === "boolean") {
    return text === "true" ? true : text === "false" ? false : null;
  }
  if (kind === "datetime") {
    return `${text}:00+09:00`;
  }
  return text;
}

function inputValue(value: unknown, kind: FieldKind = "text") {
  if (value === null || value === undefined) {
    return "";
  }
  if (kind === "datetime" && typeof value === "string") {
    return value.slice(0, 16);
  }
  return String(value);
}

function normalizeHexCode(value: string) {
  return value.replace("#", "").replace(/[^0-9a-f]/gi, "").slice(0, 6).toUpperCase();
}

function colorInputValue(value: string) {
  return /^[0-9A-F]{6}$/i.test(value) ? `#${value}` : "";
}

async function loadSyncStatuses(
  season: number,
  signal?: AbortSignal,
) {
  const response = await adminGet<{ statuses: SyncStatus[] }>(
    `/api/v1/admin/sync/statuses?season=${season}`,
    { signal },
  );
  return response.statuses ?? [];
}

function syncTaskLabel(task: string) {
  if (task === "fixture-detail") {
    return "경기 상세";
  }
  return syncTasks.find((item) => item.task === task)?.label ?? task;
}

function syncRequestDisplayKey(task: string, season: number | null) {
  const label = syncTaskLabel(task);
  return season === null || task === "seasons" ? label : `${label}:${season}`;
}

function syncUnitLabel(unitLabel: string | null) {
  const labels: Record<string, string> = {
    request: "요청",
    injuries: "부상 정보",
    teams: "팀",
    images: "이미지",
    season: "시즌",
    fixtures: "경기",
  };
  return unitLabel ? labels[unitLabel] ?? unitLabel : "단위";
}

function adminAuditTypeLabel(type: string) {
  const labels: Record<string, string> = {
    TEAM_UPDATE: "팀 수정",
    PLAYER_UPDATE: "선수 수정",
    FIXTURE_UPDATE: "경기 수정",
    MEDIA_UPLOAD: "이미지 등록",
    MEDIA_RESTORE: "이미지 복원",
    OVERRIDE_CLEAR: "수동 수정 해제",
    SYNC: "동기화",
    EXTERNAL_API_CALL: "외부 API 호출",
  };
  return labels[type] ?? type;
}

function syncCategoryLabel(category: string) {
  const labels: Record<string, string> = {
    Seasons: "지원 시즌",
    Teams: "팀",
    Standings: "순위",
    Fixtures: "경기",
    "Season Details": "시즌 경기 상세",
    "Fixture Detail": "경기 상세",
    Players: "선수",
    Injuries: "부상자",
  };
  return labels[category] ?? category;
}

function adminAuditDetailsLabel(details: string) {
  const labels: Record<string, string> = {
    leagueId: "리그 ID",
    teamId: "팀 ID",
    playerId: "선수 ID",
    fixtureId: "경기 ID",
    articleId: "기사 ID",
    season: "시즌",
    delayMs: "지연 시간(ms)",
    field: "필드",
    batchSize: "묶음 크기",
    resultCount: "결과 수",
    attempts: "시도 횟수",
    durationMs: "소요 시간(ms)",
    httpStatus: "HTTP 상태",
    errorCategory: "오류 분류",
    operation: "작업",
  };
  return details
    .split(";")
    .map((part) => {
      const [key, ...valueParts] = part.trim().split("=");
      if (valueParts.length === 0) {
        return part.trim();
      }
      const value = valueParts.join("=");
      const displayValue = key === "operation"
        ? externalApiOperationLabel(value)
        : key === "errorCategory"
          ? externalApiErrorCategoryLabel(value)
          : value;
      return `${labels[key] ?? key}=${displayValue}`;
    })
    .join("; ");
}

function eventTypeLabel(eventType: string | null) {
  if (!eventType?.trim()) {
    return "";
  }
  const normalizedType = normalizeEventType(eventType);
  return eventTypeOptions.find((option) => option.value === normalizedType)?.label ?? eventType;
}

function externalApiOperationLabel(operation: string) {
  const labels: Record<string, string> = {
    getFixture: "경기 조회",
    getFixturesByIds: "경기 일괄 조회",
    getFixtures: "시즌 경기 조회",
    getLeagueSeasons: "지원 시즌 조회",
    getLiveFixtures: "실시간 경기 조회",
    getEvents: "경기 이벤트 조회",
    getLineups: "라인업 조회",
    getPlayerProfiles: "선수 프로필 조회",
    getRegisteredPlayers: "등록 선수 조회",
    getRegisteredPlayersByTeam: "팀별 등록 선수 조회",
    getInjuries: "부상자 조회",
    getPlayerStats: "선수 경기 통계 조회",
    getFixtureStatistics: "팀 경기 통계 조회",
    getTeams: "팀 조회",
    getStandings: "순위 조회",
    searchTeamNews: "팀 뉴스 검색",
    translateNewsTitles: "뉴스 제목 번역",
  };
  return labels[operation] ?? operation;
}

function externalApiErrorCategoryLabel(category: string) {
  const labels: Record<string, string> = {
    CONFIGURATION: "설정 오류",
    BAD_REQUEST: "잘못된 요청",
    AUTHENTICATION: "인증 오류",
    PERMISSION: "권한 오류",
    NOT_FOUND: "대상 없음",
    RATE_LIMITED: "호출 제한",
    QUOTA_EXHAUSTED: "사용량 소진",
    TIMEOUT: "시간 초과",
    NETWORK: "네트워크 오류",
    UPSTREAM_SERVER: "외부 서버 오류",
    INVALID_RESPONSE: "잘못된 응답",
    CIRCUIT_OPEN: "호출 차단",
    UNKNOWN: "알 수 없는 오류",
  };
  return labels[category] ?? category;
}

function syncStatusLabel(status?: string | null) {
  const labels: Record<string, string> = {
    OK: "정상",
    STALE: "지연",
    FAILED: "실패",
    RETRY_PENDING: "재시도 대기중",
    NEVER_SYNCED: "기록 없음",
  };
  return labels[status ?? "NEVER_SYNCED"] ?? status ?? "기록 없음";
}

function apiFootballStatusLabel(status?: string | null) {
  if (status === "FAILED") return "전체 API 장애";
  if (status === "RETRY_PENDING") return "재시도 대기중";
  if (status === "STALE") return "상태 확인 지연";
  if (status === "OK") return "정상";
  return "확인 전";
}

function lastSuccessfulSyncTime(status?: SyncStatus | null) {
  if (!status) {
    return null;
  }
  if (status.lastSuccessAt) {
    return status.lastSuccessAt;
  }
  return status.status === "OK" || status.status === "STALE" ? status.lastSyncedAt : null;
}

function syncJobStatusLabel(status: SyncJobStatus) {
  const labels: Record<SyncJobStatus, string> = {
    QUEUED: "대기 중",
    RUNNING: "진행 중",
    CANCEL_REQUESTED: "취소 요청됨",
    CANCELLED: "취소됨",
    SUCCEEDED: "성공",
    PARTIAL_FAILED: "부분 실패",
    FAILED: "실패",
  };
  return labels[status];
}

function syncJobPhaseLabel(phase: string | null, status: SyncJobStatus) {
  if (status === "SUCCEEDED") return "성공적으로 동기화되었습니다.";
  if (status === "PARTIAL_FAILED") return "일부 데이터는 동기화하지 못했습니다.";
  if (status === "FAILED") return "동기화에 실패했습니다.";
  if (status === "QUEUED") return "실행 순서를 기다리고 있습니다.";
  if (status === "CANCEL_REQUESTED") return "현재 처리 단위를 마친 뒤 취소합니다.";
  if (status === "CANCELLED") return "관리자 요청으로 취소되었습니다.";
  const labels: Record<string, string> = {
    SYNCING_FIXTURES: "경기 상세 정보를 동기화하고 있습니다.",
    REBUILDING_SEASON_STATS: "선수 팀별 시즌 스탯을 재계산하고 있습니다.",
    SYNCING_PLAYERS: "시즌 참가 팀의 선수 정보를 동기화하고 있습니다.",
    CACHING_IMAGES: "선수 이미지를 캐싱하고 있습니다.",
    FETCHING_INJURIES: "API-Football에서 부상 정보를 가져오고 있습니다.",
    SYNCING_INJURIES: "부상 정보를 저장하고 있습니다.",
  };
  return phase ? labels[phase] ?? phase : "작업 상태를 준비하고 있습니다.";
}

function syncJobCompletionMessage(job: SyncJob) {
  const syncKey = syncRequestDisplayKey(job.task, job.season);
  if (job.status === "SUCCEEDED") {
    return `${syncKey} 동기화 완료`;
  }
  const errorMessage = job.errors?.find((error) => error.message?.trim())?.message?.trim()
    || job.message?.trim()
    || "오류 내역을 확인해 주세요.";
  if (job.status === "PARTIAL_FAILED") {
    return `${syncKey} 동기화 일부 실패: ${errorMessage}`;
  }
  if (job.status === "CANCELLED") {
    return `${syncKey} 동기화 취소`;
  }
  return `${syncKey} 동기화 실패: ${errorMessage}`;
}

async function loadSeasonCoverages(
  setSeasonCoverages: (coverages: LeagueSeasonCoverage[]) => void,
  setCoverageStatus: (status: CoverageStatus) => void,
) {
  setCoverageStatus("loading");
  try {
    const response = await fetchLeagueSeasons();
    setSeasonCoverages(response.seasons ?? []);
    setCoverageStatus("ready");
  } catch {
    setSeasonCoverages([]);
    setCoverageStatus("error");
  }
}

function syncAvailability(
  task: string,
  season: number,
  coverages: LeagueSeasonCoverage[],
  coverageStatus: CoverageStatus,
) {
  if (task === "seasons") {
    return { enabled: true, message: "" };
  }
  if (coverageStatus === "loading") {
    return { enabled: false, message: "시즌 지원 범위를 확인 중입니다." };
  }
  if (coverageStatus === "error") {
    return { enabled: false, message: "시즌 지원 범위를 확인할 수 없습니다." };
  }

  const coverage = coverages.find((item) => item.seasonYear === season);
  if (!coverage) {
    return { enabled: false, message: "이 시즌의 API-Football 지원 정보가 없습니다." };
  }

  if (task === "standings" && coverage.standings === false) {
    return { enabled: false, message: "이 시즌은 순위 데이터를 제공하지 않습니다." };
  }
  if (task === "players" && coverage.players === false) {
    return { enabled: false, message: "이 시즌은 선수 데이터를 제공하지 않습니다." };
  }
  if (task === "injuries" && coverage.injuries === false) {
    return { enabled: false, message: "이 시즌은 부상자 데이터를 제공하지 않습니다." };
  }
  if (task === "fixture-details" && !supportsFixtureDetails(coverage)) {
    return { enabled: false, message: "이 시즌은 경기 상세 데이터를 제공하지 않습니다." };
  }

  return { enabled: true, message: "" };
}

function supportsFixtureDetails(coverage: LeagueSeasonCoverage) {
  return [coverage.events, coverage.lineups, coverage.fixtureStats, coverage.playerStats].some((value) => value !== false);
}

async function adminGet<T>(url: string, init: RequestInit = {}): Promise<T> {
  return adminRequest<T>(url, { ...init, method: "GET" });
}

async function adminJson<T>(url: string, method: string, body?: unknown): Promise<T> {
  return adminRequest<T>(url, {
    method,
    headers: body === undefined ? undefined : { "Content-Type": "application/json" },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
}

async function adminRequest<T>(url: string, init: RequestInit): Promise<T> {
  const response = await fetch(url, {
    ...init,
    credentials: "same-origin",
    headers: {
      Accept: "application/json",
      ...(init.headers ?? {}),
    },
  });
  if (!response.ok) {
    throw new Error(await errorMessage(response, `요청을 처리하지 못했습니다. (${response.status})`));
  }
  return response.status === 204 ? (null as T) : response.json();
}

async function errorMessage(response: Response, fallback: string) {
  try {
    const body = (await response.json()) as { message?: string };
    return body.message ?? fallback;
  } catch {
    return fallback;
  }
}

function requestErrorMessage(error: unknown) {
  return error instanceof Error ? error.message : "요청을 처리하지 못했습니다.";
}

function formatDateTime(value: string | null) {
  if (!value) {
    return "-";
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }
  return new Intl.DateTimeFormat("ko-KR", {
    dateStyle: "short",
    timeStyle: "short",
  }).format(date);
}

function sortAdminFixturesByRound(fixtures: FixtureSummaryAdmin[]) {
  return [...fixtures].sort((left, right) => {
    const roundDifference = (left.round ?? Number.MAX_SAFE_INTEGER) - (right.round ?? Number.MAX_SAFE_INTEGER);
    if (roundDifference !== 0) {
      return roundDifference;
    }
    const leftTime = left.fixtureDate ? new Date(left.fixtureDate).getTime() : Number.MAX_SAFE_INTEGER;
    const rightTime = right.fixtureDate ? new Date(right.fixtureDate).getTime() : Number.MAX_SAFE_INTEGER;
    const dateDifference = leftTime - rightTime;
    if (Number.isFinite(dateDifference) && dateDifference !== 0) {
      return dateDifference;
    }
    return left.fixtureId - right.fixtureId;
  });
}

function formatFixtureDate(value: string | null) {
  if (!value) {
    return "일시 미정";
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }
  return new Intl.DateTimeFormat("ko-KR", {
    timeZone: "Asia/Seoul",
    month: "short",
    day: "numeric",
    weekday: "short",
    hour: "2-digit",
    minute: "2-digit",
  }).format(date);
}

function formatFixtureScore(fixture: FixtureSummaryAdmin) {
  if (fixture.homeScore === null || fixture.awayScore === null) {
    return "대";
  }
  return `${fixture.homeScore} : ${fixture.awayScore}`;
}

function externalApiProviderLabel(provider: string) {
  if (provider === "SERP_API") return "SerpAPI";
  if (provider === "OPENAI") return "OpenAI";
  if (provider === "API_FOOTBALL") return "API-Football";
  return provider;
}
