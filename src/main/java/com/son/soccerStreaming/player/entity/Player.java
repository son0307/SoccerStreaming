package com.son.soccerStreaming.player.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public class Player {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(nullable = false, unique = true)
    private Long playerId;

    @Column(nullable = false)
    private String name;
    private String koreanName;
    private String firstname;
    private String lastname;

    // 생년월일 및 출신 정보
    private Integer age;
    private LocalDate birthDate;
    private String birthPlace;
    private String birthCountry;
    private String nationality;

    // 신체 정보
    private Integer height;

    private Integer weight;

    // 포지션 및 기본 등번호
    private String position;
    private Integer number;

    // 이미지 정보
    private String photoUrl;
    private String photoObjectKey;
    private String adminPhotoObjectKey;
    private LocalDateTime photoCachedAt;
    private LocalDateTime photoCacheFailedAt;
    private String photoCacheFailureReason;

    // 선수 정보 업데이트(Sync)를 위한 편의 메서드
    public void updateProfile(String name, String firstname, String lastname, Integer age,
                              LocalDate birthDate, String birthPlace, String birthCountry,
                              String nationality, Integer height, Integer weight,
                              String position, Integer number, String photoUrl) {
        this.name = name;
        this.firstname = firstname;
        this.lastname = lastname;
        this.age = age;
        this.birthDate = birthDate;
        this.birthPlace = birthPlace;
        this.birthCountry = birthCountry;
        this.nationality = nationality;
        this.height = height;
        this.weight = weight;
        this.position = position;
        this.number = number;
        updatePhotoUrl(photoUrl);
    }

    public void updateNumber(Integer number) {
        if (number != null) {
            this.number = number;
        }
    }

    public void updateKoreanName(String koreanName) {
        this.koreanName = koreanName;
    }

    public void updatePhotoUrl(String photoUrl) {
        if (!Objects.equals(this.photoUrl, photoUrl)) {
            this.photoObjectKey = null;
            this.photoCachedAt = null;
            this.photoCacheFailedAt = null;
            this.photoCacheFailureReason = null;
        }
        this.photoUrl = photoUrl;
    }

    public void markPhotoCached(String objectKey, LocalDateTime cachedAt) {
        this.photoObjectKey = objectKey;
        this.photoCachedAt = cachedAt;
        this.photoCacheFailedAt = null;
        this.photoCacheFailureReason = null;
    }

    public void updateAdminPhotoObjectKey(String objectKey) {
        this.adminPhotoObjectKey = objectKey;
    }

    public void clearAdminPhotoObjectKey() {
        this.adminPhotoObjectKey = null;
    }

    public void markPhotoCacheFailed(LocalDateTime failedAt, String failureReason) {
        this.photoCacheFailedAt = failedAt;
        this.photoCacheFailureReason = failureReason;
    }
}
