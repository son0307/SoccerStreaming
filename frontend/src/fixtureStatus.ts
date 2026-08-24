import type { FixtureSummary } from "./api";

type FixtureStatusData = Pick<FixtureSummary, "fixtureStatus" | "statusShort" | "statusLong" | "elapsed" | "extra">;

const SPECIAL_STATUS_LABELS: Record<string, string> = {
  HT: "HT",
  BT: "연장 휴식",
  P: "승부차기",
  SUSP: "중단",
  INT: "중단",
  PST: "연기",
  CANC: "취소",
  ABD: "중단",
  AWD: "몰수",
  WO: "부전승",
};

export function isLiveFixture(fixture: Pick<FixtureSummary, "fixtureStatus">) {
  return fixture.fixtureStatus?.toUpperCase() === "LIVE";
}

export function fixtureStatusLabel(fixture: FixtureStatusData) {
  const macroStatus = fixture.fixtureStatus?.toUpperCase() ?? "";
  const shortStatus = fixture.statusShort?.toUpperCase() ?? "";
  const specialLabel = SPECIAL_STATUS_LABELS[shortStatus];

  if (specialLabel) {
    return specialLabel;
  }

  if (macroStatus === "LIVE") {
    if (fixture.elapsed !== null && fixture.elapsed !== undefined) {
      const extra = fixture.extra && fixture.extra > 0 ? `+${fixture.extra}` : "";
      return `${fixture.elapsed}${extra}'`;
    }
    return fixture.statusLong || "LIVE";
  }

  if (macroStatus === "SCHEDULED") {
    return "예정";
  }
  if (macroStatus === "FINISHED") {
    return shortStatus === "AET" ? "연장 종료" : shortStatus === "PEN" ? "승부차기 종료" : "종료";
  }

  return fixture.statusLong || fixture.fixtureStatus || "예정";
}
