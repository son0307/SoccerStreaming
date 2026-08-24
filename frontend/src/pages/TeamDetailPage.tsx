import { useEffect, useMemo, useRef, useState } from "react";
import type { Dispatch, SetStateAction } from "react";
import { CalendarDays, Clock, ExternalLink, Goal, Languages, Newspaper, Pencil, RefreshCw, Star, Users } from "lucide-react";
import type { LucideIcon } from "lucide-react";
import { Link, useParams, useSearchParams } from "react-router-dom";
import {
  ApiError,
  addFavoriteTeam,
  fetchFavoriteDashboard,
  fetchFixtures,
  fetchTeamDetails,
  fetchTeamNews,
  fetchTeamPlayerRankings,
  fetchTeamPlayers,
  removeFavoriteTeam,
  refreshTeamNews,
  translateTeamNewsArticle,
  type CurrentUser,
  type FixtureSummary,
  type PlayerSummary,
  type TeamDetails,
  type TeamNewsResponse,
  type TeamNewsRefreshResult,
  type TeamPlayerRanking,
} from "../api";
import type { AuthStatus } from "../App";
import { formatFixtureDateKey, parseKoreaDateTime } from "../dateUtils";
import { displayLocalizedName } from "../teamNames";
import { fixtureStatusLabel } from "../fixtureStatus";

type LoadState<T> = {
  data: T | null;
  error: string;
  isLoading: boolean;
};

type TeamDetailTab = "fixtures" | "squad" | "stats" | "news";

const FIXTURE_FETCH_SIZE = 100;
const TEAM_FIXTURE_PAGE_SIZE = 10;

function teamDetailTab(value: string | null): TeamDetailTab {
  if (value === "squad" || value === "stats" || value === "news") {
    return value;
  }
  return "fixtures";
}

export function TeamDetailPage({ authStatus, currentUser, season }: { authStatus: AuthStatus; currentUser: CurrentUser | null; season: number }) {
  const { teamId } = useParams();
  const [searchParams] = useSearchParams();
  const numericTeamId = Number(teamId);
  const activeTab = teamDetailTab(searchParams.get("tab"));
  const teamRequestId = useRef(0);
  const fixturesRequestId = useRef(0);
  const playersRequestId = useRef(0);
  const rankingsRequestId = useRef(0);
  const newsRequestId = useRef(0);
  const activeNewsTeamId = useRef(numericTeamId);
  activeNewsTeamId.current = numericTeamId;
  const [teamState, setTeamState] = useState<LoadState<TeamDetails>>({
    data: null,
    error: "",
    isLoading: true,
  });
  const [playersState, setPlayersState] = useState<LoadState<PlayerSummary[]>>({
    data: null,
    error: "",
    isLoading: false,
  });
  const [fixturesState, setFixturesState] = useState<LoadState<FixtureSummary[]>>({
    data: null,
    error: "",
    isLoading: false,
  });
  const [rankState, setRankState] = useState<LoadState<TeamPlayerRanking[]>>({
    data: null,
    error: "",
    isLoading: false,
  });
  const [newsState, setNewsState] = useState<LoadState<TeamNewsResponse>>({
    data: null,
    error: "",
    isLoading: false,
  });
  const [newsLanguage, setNewsLanguage] = useState<"ko" | "en">("ko");
  const [isNewsRefreshing, setIsNewsRefreshing] = useState(false);
  const [newsRefreshMessage, setNewsRefreshMessage] = useState("");
  const [newsRefreshError, setNewsRefreshError] = useState("");
  const [translatingArticleIds, setTranslatingArticleIds] = useState<Set<number>>(() => new Set());
  const [newsTranslationErrors, setNewsTranslationErrors] = useState<Record<number, string>>({});
  const [fixturePage, setFixturePage] = useState(0);
  const [isFavorite, setIsFavorite] = useState(false);
  const [isFavoriteLoading, setIsFavoriteLoading] = useState(false);
  const [favoriteError, setFavoriteError] = useState("");

  useEffect(() => {
    setTranslatingArticleIds(new Set());
    setNewsTranslationErrors({});
  }, [numericTeamId]);

  useEffect(() => {
    if (!Number.isFinite(numericTeamId) || numericTeamId <= 0) {
      const error = "올바른 팀 ID가 아닙니다.";
      setTeamState({ data: null, error, isLoading: false });
      return;
    }

    const requestId = teamRequestId.current + 1;
    teamRequestId.current = requestId;
    let isCurrent = true;
    const isLatest = () => isCurrent && teamRequestId.current === requestId;
    setTeamState({ data: null, error: "", isLoading: true });

    fetchTeamDetails(numericTeamId)
      .then((data) => {
        if (isLatest()) {
          setTeamState({ data, error: "", isLoading: false });
        }
      })
      .catch((error) => {
        if (isLatest()) {
          setTeamState({
            data: null,
            error: error instanceof Error ? error.message : "팀 정보를 불러오지 못했습니다.",
            isLoading: false,
          });
        }
      });

    return () => {
      isCurrent = false;
    };
  }, [numericTeamId]);

  useEffect(() => {
    fixturesRequestId.current += 1;
    playersRequestId.current += 1;
    rankingsRequestId.current += 1;
    setFixturePage(0);
    setFixturesState({ data: null, error: "", isLoading: false });
    setPlayersState({ data: null, error: "", isLoading: false });
    setRankState({ data: null, error: "", isLoading: false });
  }, [numericTeamId, season]);

  useEffect(() => {
    if (activeTab !== "fixtures" || !Number.isFinite(numericTeamId) || numericTeamId <= 0) {
      return;
    }
    const requestId = fixturesRequestId.current + 1;
    fixturesRequestId.current = requestId;
    let isCurrent = true;
    setFixturePage(0);
    setFixturesState({ data: null, error: "", isLoading: true });
    fetchFixtures({ season, teamId: numericTeamId, size: FIXTURE_FETCH_SIZE })
      .then((response) => {
        if (isCurrent && fixturesRequestId.current === requestId) {
          setFixturesState({ data: response.content ?? [], error: "", isLoading: false });
        }
      })
      .catch((error) => {
        if (isCurrent && fixturesRequestId.current === requestId) {
          setFixturesState({
            data: null,
            error: error instanceof Error ? error.message : "팀 경기 일정을 불러오지 못했습니다.",
            isLoading: false,
          });
        }
      });
    return () => {
      isCurrent = false;
    };
  }, [activeTab, numericTeamId, season]);

  useEffect(() => {
    if (activeTab !== "squad" || !Number.isFinite(numericTeamId) || numericTeamId <= 0) {
      return;
    }
    const requestId = playersRequestId.current + 1;
    playersRequestId.current = requestId;
    let isCurrent = true;
    setPlayersState({ data: null, error: "", isLoading: true });
    fetchTeamPlayers(numericTeamId, season)
      .then((players) => {
        if (isCurrent && playersRequestId.current === requestId) {
          setPlayersState({ data: players, error: "", isLoading: false });
        }
      })
      .catch((error) => {
        if (isCurrent && playersRequestId.current === requestId) {
          setPlayersState({
            data: null,
            error: error instanceof Error ? error.message : "팀 선수 목록을 불러오지 못했습니다.",
            isLoading: false,
          });
        }
      });
    return () => {
      isCurrent = false;
    };
  }, [activeTab, numericTeamId, season]);

  useEffect(() => {
    if (activeTab !== "stats" || !Number.isFinite(numericTeamId) || numericTeamId <= 0) {
      return;
    }
    const requestId = rankingsRequestId.current + 1;
    rankingsRequestId.current = requestId;
    let isCurrent = true;
    setRankState({ data: null, error: "", isLoading: true });
    fetchTeamPlayerRankings(numericTeamId, season)
      .then((response) => {
        if (isCurrent && rankingsRequestId.current === requestId) {
          setRankState({ data: response.rows ?? [], error: "", isLoading: false });
        }
      })
      .catch((error) => {
        if (isCurrent && rankingsRequestId.current === requestId) {
          setRankState({
            data: null,
            error: error instanceof Error ? error.message : "팀 선수 통계를 불러오지 못했습니다.",
            isLoading: false,
          });
        }
      });
    return () => {
      isCurrent = false;
    };
  }, [activeTab, numericTeamId, season]);

  useEffect(() => {
    setNewsLanguage("ko");
    setNewsState({ data: null, error: "", isLoading: false });
    setIsNewsRefreshing(false);
    setNewsRefreshMessage("");
    setNewsRefreshError("");
    newsRequestId.current += 1;
  }, [numericTeamId]);

  useEffect(() => {
    if (activeTab !== "news") {
      newsRequestId.current += 1;
      return;
    }
    if (!Number.isFinite(numericTeamId) || numericTeamId <= 0) {
      setNewsState({ data: null, error: "올바른 팀 ID가 아닙니다.", isLoading: false });
      return;
    }

    const requestId = newsRequestId.current + 1;
    newsRequestId.current = requestId;
    let isCurrent = true;
    setNewsState({ data: null, error: "", isLoading: true });

    fetchTeamNews(numericTeamId)
      .then((articles) => {
        if (isCurrent && newsRequestId.current === requestId) {
          setNewsState({ data: articles, error: "", isLoading: false });
        }
      })
      .catch((error) => {
        if (isCurrent && newsRequestId.current === requestId) {
          setNewsState({
            data: null,
            error: error instanceof Error ? error.message : "팀 뉴스를 불러오지 못했습니다.",
            isLoading: false,
          });
        }
      });

    return () => {
      isCurrent = false;
    };
  }, [activeTab, numericTeamId]);

  useEffect(() => {
    if (!Number.isFinite(numericTeamId) || numericTeamId <= 0) {
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
          setIsFavorite(dashboard.teams.some((team) => team.teamId === numericTeamId));
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
  }, [authStatus, numericTeamId, season]);

  async function toggleFavorite() {
    if (!Number.isFinite(numericTeamId) || numericTeamId <= 0) {
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
        ? await removeFavoriteTeam(numericTeamId, season)
        : await addFavoriteTeam(numericTeamId, season);
      setIsFavorite(dashboard.teams.some((team) => team.teamId === numericTeamId));
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

  function retryTeamPage() {
    if (!Number.isFinite(numericTeamId) || numericTeamId <= 0) {
      return;
    }

    const requestId = teamRequestId.current + 1;
    teamRequestId.current = requestId;
    const isLatest = () => teamRequestId.current === requestId;

    setTeamState({ data: null, error: "", isLoading: true });

    fetchTeamDetails(numericTeamId)
      .then((data) => {
        if (isLatest()) {
          setTeamState({ data, error: "", isLoading: false });
        }
      })
      .catch((error) => {
        if (isLatest()) {
          setTeamState({
            data: null,
            error: error instanceof Error ? error.message : "팀 정보를 불러오지 못했습니다.",
            isLoading: false,
          });
        }
      });

  }

  function retryFixtures() {
    if (!Number.isFinite(numericTeamId) || numericTeamId <= 0) {
      return;
    }
    const requestId = fixturesRequestId.current + 1;
    fixturesRequestId.current = requestId;
    setFixturePage(0);
    setFixturesState({ data: null, error: "", isLoading: true });
    fetchFixtures({ season, teamId: numericTeamId, size: FIXTURE_FETCH_SIZE })
      .then((response) => {
        if (fixturesRequestId.current === requestId) {
          setFixturesState({ data: response.content ?? [], error: "", isLoading: false });
        }
      })
      .catch((error) => {
        if (fixturesRequestId.current === requestId) {
          setFixturesState({
            data: null,
            error: error instanceof Error ? error.message : "팀 경기 일정을 불러오지 못했습니다.",
            isLoading: false,
          });
        }
      });
  }

  function retryPlayers() {
    if (!Number.isFinite(numericTeamId) || numericTeamId <= 0) {
      return;
    }
    const requestId = playersRequestId.current + 1;
    playersRequestId.current = requestId;
    setPlayersState({ data: null, error: "", isLoading: true });
    fetchTeamPlayers(numericTeamId, season)
      .then((players) => {
        if (playersRequestId.current === requestId) {
          setPlayersState({ data: players, error: "", isLoading: false });
        }
      })
      .catch((error) => {
        if (playersRequestId.current === requestId) {
          setPlayersState({
            data: null,
            error: error instanceof Error ? error.message : "팀 선수 목록을 불러오지 못했습니다.",
            isLoading: false,
          });
        }
      });
  }

  function retryRankings() {
    if (!Number.isFinite(numericTeamId) || numericTeamId <= 0) {
      return;
    }
    const requestId = rankingsRequestId.current + 1;
    rankingsRequestId.current = requestId;
    setRankState({ data: null, error: "", isLoading: true });
    fetchTeamPlayerRankings(numericTeamId, season)
      .then((response) => {
        if (rankingsRequestId.current === requestId) {
          setRankState({ data: response.rows ?? [], error: "", isLoading: false });
        }
      })
      .catch((error) => {
        if (rankingsRequestId.current === requestId) {
          setRankState({
            data: null,
            error: error instanceof Error ? error.message : "팀 선수 통계를 불러오지 못했습니다.",
            isLoading: false,
          });
        }
      });
  }

  function retryNews() {
    if (!Number.isFinite(numericTeamId) || numericTeamId <= 0) {
      return;
    }

    const requestId = newsRequestId.current + 1;
    newsRequestId.current = requestId;
    setNewsState({ data: null, error: "", isLoading: true });
    fetchTeamNews(numericTeamId)
      .then((articles) => {
        if (newsRequestId.current === requestId) {
          setNewsState({ data: articles, error: "", isLoading: false });
        }
      })
      .catch((error) => {
        if (newsRequestId.current === requestId) {
          setNewsState({
            data: null,
            error: error instanceof Error ? error.message : "팀 뉴스를 불러오지 못했습니다.",
            isLoading: false,
          });
        }
      });
  }

  async function refreshNewsFromProviders() {
    if (!Number.isFinite(numericTeamId) || numericTeamId <= 0 || isNewsRefreshing) {
      return;
    }

    const requestId = newsRequestId.current + 1;
    newsRequestId.current = requestId;
    setIsNewsRefreshing(true);
    setNewsRefreshMessage("");
    setNewsRefreshError("");
    try {
      const result = await refreshTeamNews(numericTeamId);
      if (newsRequestId.current !== requestId) {
        return;
      }
      const articles = await fetchTeamNews(numericTeamId);
      if (newsRequestId.current === requestId) {
        setNewsState({ data: articles, error: "", isLoading: false });
        setNewsRefreshMessage(newsRefreshResultMessage(result));
      }
    } catch (error) {
      if (newsRequestId.current === requestId) {
        setNewsRefreshError(error instanceof Error ? error.message : "뉴스 새로고침에 실패했습니다.");
      }
    } finally {
      if (newsRequestId.current === requestId) {
        setIsNewsRefreshing(false);
      }
    }
  }

  async function translateNewsArticle(articleId: number) {
    if (!Number.isFinite(numericTeamId) || numericTeamId <= 0 || translatingArticleIds.has(articleId)) {
      return;
    }

    const requestedTeamId = numericTeamId;
    setTranslatingArticleIds((current) => new Set(current).add(articleId));
    setNewsTranslationErrors((current) => {
      const next = { ...current };
      delete next[articleId];
      return next;
    });
    try {
      const result = await translateTeamNewsArticle(requestedTeamId, articleId);
      if (activeNewsTeamId.current !== requestedTeamId) {
        return;
      }
      setNewsState((current) => ({
        ...current,
        data: current.data ? {
          ...current.data,
          articles: current.data.articles.map((article) => (
            article.articleId === result.articleId
              ? { ...article, translatedTitle: result.translatedTitle }
              : article
          )),
        } : null,
      }));
    } catch (error) {
      if (activeNewsTeamId.current === requestedTeamId) {
        setNewsTranslationErrors((current) => ({
          ...current,
          [articleId]: error instanceof Error ? error.message : "뉴스 제목을 번역하지 못했습니다.",
        }));
      }
    } finally {
      if (activeNewsTeamId.current === requestedTeamId) {
        setTranslatingArticleIds((current) => {
          const next = new Set(current);
          next.delete(articleId);
          return next;
        });
      }
    }
  }

  if (teamState.isLoading) {
    return (
      <section className="league-content team-detail-page">
        <article className="panel placeholder-panel">팀 정보를 불러오는 중입니다.</article>
      </section>
    );
  }

  if (teamState.error || !teamState.data) {
    return (
      <section className="league-content team-detail-page">
        <article className="panel placeholder-panel">
          <p className="eyebrow">Team Detail</p>
          <h2>팀 정보를 불러오지 못했습니다.</h2>
          <p className="muted">{teamState.error || "잠시 후 다시 시도해 주세요."}</p>
          <button className="section-retry-button" type="button" onClick={retryTeamPage}>
            새로 고침
          </button>
          <Link className="primary-link fixture-back-link" to="/league/standings">
            순위표로
          </Link>
        </article>
      </section>
    );
  }

  return (
    <section className="league-content team-detail-page">
      <div className="team-overview-grid">
        <TeamHero
          canUseFavorite={authStatus === "authenticated"}
          favoriteError={favoriteError}
          isFavorite={isFavorite}
          isFavoriteLoading={isFavoriteLoading}
          onToggleFavorite={toggleFavorite}
          team={teamState.data}
          isAdmin={currentUser?.role === "ADMIN"}
        />
        <TeamVenueCard venue={teamState.data.venue} />
      </div>
      <nav className="team-detail-tabs" aria-label="팀 상세 메뉴">
        <Link className={activeTab === "fixtures" ? "active" : ""} to={`/teams/${numericTeamId}`}>
          경기 일정
        </Link>
        <Link className={activeTab === "squad" ? "active" : ""} to={`/teams/${numericTeamId}?tab=squad`}>
          선수단
        </Link>
        <Link className={activeTab === "stats" ? "active" : ""} to={`/teams/${numericTeamId}?tab=stats`}>
          선수 통계
        </Link>
        <Link className={activeTab === "news" ? "active" : ""} to={`/teams/${numericTeamId}?tab=news`}>
          뉴스
        </Link>
      </nav>
      {activeTab === "fixtures" ? (
        <TeamFixturePanel
          fixturePage={fixturePage}
          fixturesState={fixturesState}
          onRetry={retryFixtures}
          setFixturePage={setFixturePage}
        />
      ) : null}
      {activeTab === "squad" ? (
        <TeamPlayersPanel onRetry={retryPlayers} playersState={playersState} />
      ) : null}
      {activeTab === "stats" ? (
        <TeamPlayerRanksPanel onRetry={retryRankings} rankState={rankState} />
      ) : null}
      {activeTab === "news" ? (
        <TeamNewsPanel
          canRefresh={currentUser?.role === "ADMIN"}
          isRefreshing={isNewsRefreshing}
          language={newsLanguage}
          newsState={newsState}
          onLanguageChange={setNewsLanguage}
          onRefresh={refreshNewsFromProviders}
          onRetry={retryNews}
          refreshError={newsRefreshError}
          refreshMessage={newsRefreshMessage}
          translatingArticleIds={translatingArticleIds}
          translationErrors={newsTranslationErrors}
          onTranslate={translateNewsArticle}
        />
      ) : null}
    </section>
  );
}

function TeamNewsPanel({
  canRefresh,
  isRefreshing,
  language,
  newsState,
  onLanguageChange,
  onRefresh,
  onRetry,
  onTranslate,
  refreshError,
  refreshMessage,
  translatingArticleIds,
  translationErrors,
}: {
  canRefresh: boolean;
  isRefreshing: boolean;
  language: "ko" | "en";
  newsState: LoadState<TeamNewsResponse>;
  onLanguageChange: (language: "ko" | "en") => void;
  onRefresh: () => void;
  onRetry: () => void;
  onTranslate: (articleId: number) => void;
  refreshError: string;
  refreshMessage: string;
  translatingArticleIds: Set<number>;
  translationErrors: Record<number, string>;
}) {
  const articles = newsState.data?.articles ?? [];

  return (
    <article className="panel team-news-panel">
      <div className="team-news-heading">
        <div className="detail-panel-heading player-panel-title">
          <Newspaper size={19} aria-hidden="true" />
          <h2>관련 뉴스</h2>
        </div>
        <div className="team-news-actions">
          {canRefresh ? (
            <button className="team-news-refresh" type="button" disabled={isRefreshing} onClick={onRefresh}>
              <RefreshCw className={isRefreshing ? "spinning" : ""} size={15} aria-hidden="true" />
              {isRefreshing ? "새로고침 중" : "새로고침"}
            </button>
          ) : null}
          <div className="team-news-language" role="group" aria-label="뉴스 제목 언어">
            <button
              className={language === "ko" ? "active" : ""}
              type="button"
              aria-pressed={language === "ko"}
              onClick={() => onLanguageChange("ko")}
            >
              한국어
            </button>
            <button
              className={language === "en" ? "active" : ""}
              type="button"
              aria-pressed={language === "en"}
              onClick={() => onLanguageChange("en")}
            >
              English
            </button>
          </div>
        </div>
      </div>
      <p className="team-news-collected-at">
        <Clock size={14} aria-hidden="true" />
        마지막 수집: {newsState.data?.lastCollectedAt ? formatNewsDate(newsState.data.lastCollectedAt) : "수집 기록 없음"}
      </p>
      {refreshMessage ? <p className="team-news-refresh-status success">{refreshMessage}</p> : null}
      {refreshError ? <p className="team-news-refresh-status error">{refreshError}</p> : null}
      {newsState.isLoading ? <div className="empty-state">팀 뉴스를 불러오는 중입니다.</div> : null}
      {newsState.error ? <SectionRetryError message={newsState.error} onRetry={onRetry} /> : null}
      {!newsState.isLoading && !newsState.error && !articles.length ? (
        <div className="empty-state">표시할 팀 뉴스가 없습니다.</div>
      ) : null}
      {!newsState.isLoading && !newsState.error && articles.length ? (
        <div className="team-news-list">
          {articles.map((article) => {
            const title = language === "ko" ? article.translatedTitle ?? article.originalTitle : article.originalTitle;
            const isTranslating = translatingArticleIds.has(article.articleId);
            return (
              <div className="team-news-row" key={article.articleId}>
                <a
                  className="team-news-link"
                  href={article.originalUrl}
                  target="_blank"
                  rel="noopener noreferrer"
                >
                  <div>
                    <strong>{title}</strong>
                    <p>
                      <span>{article.publisherName}</span>
                      {article.publishedAt ? <time dateTime={article.publishedAt}>{formatNewsDate(article.publishedAt)}</time> : null}
                    </p>
                  </div>
                  <ExternalLink size={18} aria-hidden="true" />
                </a>
                {canRefresh && !article.translatedTitle ? (
                  <div className="team-news-translation-action">
                    <button type="button" disabled={isTranslating} onClick={() => onTranslate(article.articleId)}>
                      <Languages size={15} aria-hidden="true" />
                      {isTranslating ? "번역 중" : "번역"}
                    </button>
                    {translationErrors[article.articleId] ? (
                      <span role="alert">{translationErrors[article.articleId]}</span>
                    ) : null}
                  </div>
                ) : null}
              </div>
            );
          })}
        </div>
      ) : null}
    </article>
  );
}

function TeamVenueCard({ venue }: { venue: TeamDetails["venue"] }) {
  const [imageFailed, setImageFailed] = useState(false);

  useEffect(() => {
    setImageFailed(false);
  }, [venue?.venueImageUrl]);

  if (!venue) {
    return (
      <article className="panel team-venue-card empty">
        <p className="eyebrow">Stadium</p>
        <h3>경기장 정보가 없습니다.</h3>
      </article>
    );
  }

  const fullAddress = [venue.venueCity, venue.venueAddress].filter(Boolean).join(" · ");
  const addressPopoverId = `team-venue-address-${venue.venueId ?? "current"}`;
  const displayVenueName = venue.venueNameKo?.trim() || venue.venueName;

  return (
    <article className="panel team-venue-card">
      <div className="team-venue-image">
        {venue.venueImageUrl && !imageFailed ? (
          <img
            src={venue.venueImageUrl}
            alt={displayVenueName ? `${displayVenueName} 경기장` : "팀 경기장"}
            onError={() => setImageFailed(true)}
          />
        ) : (
          <span>경기장 이미지가 없습니다.</span>
        )}
      </div>
      <div className="team-venue-info">
        <p className="eyebrow">Stadium</p>
        <h3>{displayVenueName ?? "경기장 이름 정보 없음"}</h3>
        <dl>
          {fullAddress ? (
            <div className="team-venue-location">
              <dt>위치</dt>
              <dd>
                {venue.venueCity && venue.venueAddress ? (
                  <>
                    <button
                      type="button"
                      className="team-venue-address-button"
                      popoverTarget={addressPopoverId}
                      aria-haspopup="dialog"
                      aria-label={`${venue.venueCity} 전체 주소 보기`}
                      title={venue.venueCity}
                    >
                      {venue.venueCity}
                    </button>
                    <div
                      id={addressPopoverId}
                      className="team-venue-address-popover"
                      popover="auto"
                      role="dialog"
                      aria-label="경기장 전체 주소"
                    >
                      <strong>{displayVenueName ?? "경기장 주소"}</strong>
                      <span>{fullAddress}</span>
                    </div>
                  </>
                ) : (
                  venue.venueCity ?? venue.venueAddress
                )}
              </dd>
            </div>
          ) : null}
          {venue.capacity ? (
            <div>
              <dt>수용 인원</dt>
              <dd>{new Intl.NumberFormat("ko-KR").format(venue.capacity)}명</dd>
            </div>
          ) : null}
          {venue.surface ? (
            <div>
              <dt>경기장 표면</dt>
              <dd>{venue.surface}</dd>
            </div>
          ) : null}
        </dl>
      </div>
    </article>
  );
}

function TeamHero({
  team,
  canUseFavorite,
  isFavorite,
  isFavoriteLoading,
  favoriteError,
  onToggleFavorite,
  isAdmin,
}: {
  team: TeamDetails;
  canUseFavorite: boolean;
  isFavorite: boolean;
  isFavoriteLoading: boolean;
  favoriteError: string;
  onToggleFavorite: () => void;
  isAdmin: boolean;
}) {
  return (
    <article className="panel team-detail-hero">
      {team.logoUrl ? <img src={team.logoUrl} alt="" className="team-detail-logo" /> : <span className="team-detail-logo placeholder" aria-hidden="true" />}
      <div>
        <p className="eyebrow">{team.country ?? "Team"}</p>
        <div className="detail-title-row">
          <h2>{displayLocalizedName(team.teamNameKo, team.teamName)}</h2>
          {canUseFavorite ? (
            <FavoriteToggleButton
              isActive={isFavorite}
              isLoading={isFavoriteLoading}
              onClick={onToggleFavorite}
              typeLabel="팀"
            />
          ) : null}
          {isAdmin ? (
            <Link aria-label="관리자 수정" className="admin-edit-link icon" title="관리자 수정" to={`/admin/editor?tab=team&id=${team.teamId}`}>
              <Pencil size={16} aria-hidden="true" />
            </Link>
          ) : null}
        </div>
        {favoriteError ? <p className="favorite-inline-error">{favoriteError}</p> : null}
        <div className="team-detail-meta">
          <span>{team.country ?? "국가 정보 없음"}</span>
          <span>{team.founded ? `${team.founded} 창단` : "창단 정보 없음"}</span>
        </div>
      </div>
    </article>
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

function TeamFixturePanel({
  fixturePage,
  fixturesState,
  onRetry,
  setFixturePage,
}: {
  fixturePage: number;
  fixturesState: LoadState<FixtureSummary[]>;
  onRetry: () => void;
  setFixturePage: Dispatch<SetStateAction<number>>;
}) {
  const orderedFixtures = useMemo(
    () => (fixturesState.data ?? []).slice().sort((a, b) => fixtureTime(a.fixtureDate) - fixtureTime(b.fixtureDate)),
    [fixturesState.data],
  );
  const totalPages = Math.max(1, Math.ceil(orderedFixtures.length / TEAM_FIXTURE_PAGE_SIZE));
  const currentPage = Math.min(fixturePage, totalPages - 1);
  const visibleFixtures = orderedFixtures.slice(
    currentPage * TEAM_FIXTURE_PAGE_SIZE,
    (currentPage + 1) * TEAM_FIXTURE_PAGE_SIZE,
  );
  const groupedFixtures = useMemo(() => groupFixturesByDate(visibleFixtures), [visibleFixtures]);

  return (
    <article className="panel team-fixtures-panel">
      <div className="detail-panel-heading player-panel-title">
        <CalendarDays size={19} aria-hidden="true" />
        <h2>경기 일정</h2>
      </div>
      {fixturesState.isLoading ? <div className="empty-state">경기 일정을 불러오는 중입니다.</div> : null}
      {fixturesState.error ? <SectionRetryError message={fixturesState.error} onRetry={onRetry} /> : null}
      {!fixturesState.isLoading && !fixturesState.error ? <TeamFixtureGroups groupedFixtures={groupedFixtures} /> : null}
      {!fixturesState.isLoading && !fixturesState.error && orderedFixtures.length > TEAM_FIXTURE_PAGE_SIZE ? (
        <div className="team-pager team-detail-pager">
          <button
            type="button"
            onClick={() => setFixturePage((page) => Math.max(0, page - 1))}
            disabled={currentPage === 0}
          >
            이전
          </button>
          <strong>{currentPage + 1} / {totalPages}</strong>
          <button
            type="button"
            onClick={() => setFixturePage((page) => Math.min(totalPages - 1, page + 1))}
            disabled={currentPage >= totalPages - 1}
          >
            다음
          </button>
        </div>
      ) : null}
    </article>
  );
}

function TeamFixtureGroups({ groupedFixtures }: { groupedFixtures: Array<[string, FixtureSummary[]]> }) {
  if (!groupedFixtures.length) {
    return <div className="empty-state">표시할 경기 일정이 없습니다.</div>;
  }

  return (
    <div className="team-detail-fixture-list">
      {groupedFixtures.map(([dateKey, fixtures]) => (
        <section className="team-detail-fixture-group" key={dateKey}>
          <h3>{dateGroupTitle(dateKey)}</h3>
          {fixtures.map((fixture) => (
            <Link className="team-detail-fixture-row" key={fixture.fixtureId} to={`/fixtures/${fixture.fixtureId}`}>
              <time>{formatTime(fixture.fixtureDate)}</time>
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
              <em>{fixtureStatusLabel(fixture)}</em>
            </Link>
          ))}
        </section>
      ))}
    </div>
  );
}

function TeamPlayersPanel({ playersState, onRetry }: { playersState: LoadState<PlayerSummary[]>; onRetry: () => void }) {
  const players = (playersState.data ?? []).slice().sort(comparePlayers);

  return (
    <article className="panel team-players-panel">
      <div className="detail-panel-heading player-panel-title">
        <Users size={19} aria-hidden="true" />
        <h2>등록 선수</h2>
      </div>
      {playersState.isLoading ? <div className="empty-state">선수 목록을 불러오는 중입니다.</div> : null}
      {playersState.error ? <SectionRetryError message={playersState.error} onRetry={onRetry} /> : null}
      {!playersState.isLoading && !playersState.error ? (
        players.length ? (
          <div className="team-player-grid">
            {players.map((player) => (
              <Link className="team-player-card" key={player.playerId} to={`/players/${player.playerId}`}>
                {player.photoUrl ? <img src={player.photoUrl} alt="" className="player-thumb" /> : <span className="player-thumb placeholder" aria-hidden="true" />}
                <div>
                  <strong>{displayLocalizedName(player.playerNameKo, player.playerName)}</strong>
                  <p>
                    {player.backNumber ? `No. ${player.backNumber}` : "등번호 없음"} · {player.position ?? "Player"}
                  </p>
                </div>
              </Link>
            ))}
          </div>
        ) : (
          <div className="empty-state">등록된 선수가 없습니다.</div>
        )
      ) : null}
    </article>
  );
}

function TeamPlayerRanksPanel({ rankState, onRetry }: { rankState: LoadState<TeamPlayerRanking[]>; onRetry: () => void }) {
  const rows = rankState.data ?? [];
  const isLoading = rankState.isLoading;
  const error = rankState.error;
  const rankGroups = [
    { label: "득점 순위", icon: Goal, rows: topRank(rows, "goals"), value: (row: TeamPlayerRanking) => `${row.goals}골` },
    { label: "도움 순위", icon: Star, rows: topRank(rows, "assists"), value: (row: TeamPlayerRanking) => `${row.assists}도움` },
    { label: "평점 순위", icon: Star, rows: topRank(rows, "rating"), value: (row: TeamPlayerRanking) => ratingText(row.rating) },
    { label: "출전 시간 순위", icon: Clock, rows: topRank(rows, "minutes"), value: (row: TeamPlayerRanking) => `${row.minutes}분` },
  ] satisfies Array<{
    label: string;
    icon: LucideIcon;
    rows: TeamPlayerRanking[];
    value: (row: TeamPlayerRanking) => string;
  }>;

  return (
    <article className="panel team-ranks-panel">
      <div className="detail-panel-heading player-panel-title">
        <Star size={19} aria-hidden="true" />
        <h2>선수 통계</h2>
      </div>
      {isLoading ? <div className="empty-state">선수 통계를 불러오는 중입니다.</div> : null}
      {rankState.error ? <SectionRetryError message={rankState.error} onRetry={onRetry} /> : null}
      {!rankState.isLoading && !rankState.error && rows.length ? (
        <div className="team-rank-grid">
          {rankGroups.map((group) => (
            <section className="team-rank-card" key={group.label}>
              <h3>
                <group.icon size={17} aria-hidden={true} />
                {group.label}
              </h3>
              {group.rows.map((row, index) => (
                <Link className="team-rank-row" key={row.playerId} to={`/players/${row.playerId}`}>
                  <span>{index + 1}</span>
                  {row.photoUrl ? (
                    <img src={row.photoUrl} alt="" className="player-thumb" />
                  ) : (
                    <span className="player-thumb placeholder" aria-hidden="true" />
                  )}
                  <strong>{displayLocalizedName(row.playerNameKo, row.playerName)}</strong>
                  <em>{group.value(row)}</em>
                </Link>
              ))}
            </section>
          ))}
        </div>
      ) : null}
      {!isLoading && !rows.length && !error ? <div className="empty-state">표시할 선수 통계가 없습니다.</div> : null}
    </article>
  );
}

function SectionRetryError({ message, onRetry }: { message: string; onRetry: () => void }) {
  return (
    <div className="section-error section-retry-error">
      <span>{message}</span>
      <button type="button" onClick={onRetry}>
        새로 고침
      </button>
    </div>
  );
}

function topRank(rows: TeamPlayerRanking[], field: "goals" | "assists" | "rating" | "minutes") {
  return rows
    .slice()
    .sort((a, b) => b[field] - a[field] || compareNullableStringLast(a.playerName, b.playerName))
    .slice(0, 5);
}

function comparePlayers(left: PlayerSummary, right: PlayerSummary) {
  return positionRank(left.position) - positionRank(right.position)
    || compareNullableNumber(left.backNumber, right.backNumber)
    || compareNullableStringLast(left.playerName, right.playerName);
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

function compareNullableStringLast(left: string | null | undefined, right: string | null | undefined) {
  if (left && right) {
    return left.localeCompare(right);
  }
  if (left) {
    return -1;
  }
  if (right) {
    return 1;
  }
  return 0;
}

function formatNewsDate(value: string) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }
  return new Intl.DateTimeFormat("ko-KR", {
    dateStyle: "medium",
    timeStyle: "short",
    timeZone: "Asia/Seoul",
  }).format(date);
}

function newsRefreshResultMessage(result: TeamNewsRefreshResult) {
  const failed = result.failedTranslations > 0 ? `, ${result.failedTranslations}건 실패` : "";
  return `${result.collectedArticles}건 확인, 번역 ${result.translatedArticles}/${result.translationCandidates}건 완료${failed}`;
}

function groupFixturesByDate(fixtures: FixtureSummary[]) {
  const groups = new Map<string, FixtureSummary[]>();
  fixtures.forEach((fixture) => {
    const key = dateKey(fixture.fixtureDate);
    groups.set(key, [...(groups.get(key) ?? []), fixture]);
  });
  return Array.from(groups.entries());
}

function dateKey(value: string | null) {
  if (!value) {
    return "unknown";
  }
  const date = parseFixtureDate(value);
  return date ? formatKoreaDate(date) : value.slice(0, 10);
}

function fixtureTime(value: string | null) {
  return parseFixtureDate(value)?.getTime() ?? Number.MAX_SAFE_INTEGER;
}

function parseFixtureDate(value: string | null) {
  return parseKoreaDateTime(value);
}

function formatKoreaDate(date: Date) {
  return new Intl.DateTimeFormat("en-CA", {
    timeZone: "Asia/Seoul",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).format(date);
}

function dateGroupTitle(value: string) {
  if (value === "unknown") {
    return "날짜 미정";
  }
  return formatFixtureDateKey(value, value);
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

function ratingText(value: number) {
  return value > 0 ? value.toFixed(1) : "-";
}
