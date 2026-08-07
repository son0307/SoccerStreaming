package com.son.soccerStreaming.team.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public class Team {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(nullable = false, unique = true)
    private Long teamId;

    @Column(nullable = false)
    private String name;
    private String koreanName;
    private String code;
    private String country;
    private Integer founded;
    private String logoUrl;
    private String logoObjectKey;
    private String adminLogoObjectKey;
    private LocalDateTime logoCachedAt;
    private LocalDateTime logoCacheFailedAt;
    private String logoCacheFailureReason;

    @OneToOne(fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "venue_id")
    private Venue venue;

    public void updateTeam(String name, String code, String country, Integer founded, String logoUrl) {
        this.name = name;
        this.code = code;
        this.country = country;
        this.founded = founded;
        updateLogoUrl(logoUrl);
    }

    public void updateKoreanName(String koreanName) {
        this.koreanName = koreanName;
    }

    public void updateVenue(Venue venue) {
        this.venue = venue;
    }

    public void updateLogoUrl(String logoUrl) {
        if (!Objects.equals(this.logoUrl, logoUrl)) {
            this.logoObjectKey = null;
            this.logoCachedAt = null;
            this.logoCacheFailedAt = null;
            this.logoCacheFailureReason = null;
        }
        this.logoUrl = logoUrl;
    }

    public void markLogoCached(String objectKey, LocalDateTime cachedAt) {
        this.logoObjectKey = objectKey;
        this.logoCachedAt = cachedAt;
        this.logoCacheFailedAt = null;
        this.logoCacheFailureReason = null;
    }

    public void updateAdminLogoObjectKey(String objectKey) {
        this.adminLogoObjectKey = objectKey;
    }

    public void clearAdminLogoObjectKey() {
        this.adminLogoObjectKey = null;
    }

    public void markLogoCacheFailed(LocalDateTime failedAt, String failureReason) {
        this.logoCacheFailedAt = failedAt;
        this.logoCacheFailureReason = failureReason;
    }
}
