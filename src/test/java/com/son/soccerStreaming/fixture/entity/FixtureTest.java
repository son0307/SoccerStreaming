package com.son.soccerStreaming.fixture.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FixtureTest {

    @Test
    void parseRoundNumberExtractsTrailingNumber() {
        assertThat(Fixture.parseRoundNumber("Regular Season - 38")).isEqualTo(38);
        assertThat(Fixture.parseRoundNumber("Regular Season - 01")).isEqualTo(1);
    }

    @Test
    void parseRoundNumberReturnsNullWhenRoundHasNoTrailingNumber() {
        assertThat(Fixture.parseRoundNumber(null)).isNull();
        assertThat(Fixture.parseRoundNumber("   ")).isNull();
        assertThat(Fixture.parseRoundNumber("Playoffs")).isNull();
    }

    @Test
    void updateRoundKeepsExistingRoundWhenParsingFails() {
        Fixture fixture = Fixture.builder()
                .round(38)
                .build();

        fixture.updateRound("Quarter-finals");

        assertThat(fixture.getRound()).isEqualTo(38);
    }

    @Test
    void recognizesFinishedFixtureFromCanonicalOrApiStatus() {
        assertThat(Fixture.builder().fixtureStatus("FINISHED").build().isFinished()).isTrue();
        assertThat(Fixture.builder().statusShort("FT").build().isFinished()).isTrue();
        assertThat(Fixture.builder().statusShort("AET").build().isFinished()).isTrue();
        assertThat(Fixture.builder().statusShort("PEN").build().isFinished()).isTrue();
    }

    @Test
    void rejectsNonFinishedFixtureStatus() {
        assertThat(Fixture.builder().fixtureStatus("SCHEDULED").statusShort("NS").build().isFinished()).isFalse();
        assertThat(Fixture.builder().fixtureStatus("LIVE").statusShort("2H").build().isFinished()).isFalse();
        assertThat(Fixture.builder().build().isFinished()).isFalse();
    }

    @Test
    void normalizesVenueKoreanName() {
        Fixture fixture = Fixture.builder()
                .fixtureId(1L)
                .build();

        fixture.updateVenueKoreanName("  에미레이츠 스타디움  ");
        assertThat(fixture.getVenueNameKo()).isEqualTo("에미레이츠 스타디움");

        fixture.updateVenueKoreanName("");
        assertThat(fixture.getVenueNameKo()).isNull();
    }
}
