package com.son.soccerStreaming.admin.repository;

import com.son.soccerStreaming.admin.entity.AdminFieldOverride;
import com.son.soccerStreaming.admin.entity.AdminOverrideTargetType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AdminFieldOverrideRepository extends JpaRepository<AdminFieldOverride, Long> {

    Optional<AdminFieldOverride> findByTargetTypeAndTargetIdAndFieldName(
            AdminOverrideTargetType targetType,
            Long targetId,
            String fieldName
    );

    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("SELECT o FROM AdminFieldOverride o "
            + "WHERE o.targetType = :targetType AND o.targetId = :targetId AND o.fieldName = :fieldName")
    Optional<AdminFieldOverride> findForEventSync(
            @Param("targetType") AdminOverrideTargetType targetType,
            @Param("targetId") Long targetId,
            @Param("fieldName") String fieldName
    );

    List<AdminFieldOverride> findAllByTargetTypeAndTargetIdAndFieldNameIn(
            AdminOverrideTargetType targetType,
            Long targetId,
            Collection<String> fieldNames
    );

    List<AdminFieldOverride> findAllByTargetTypeAndTargetIdOrderByFieldNameAsc(
            AdminOverrideTargetType targetType,
            Long targetId
    );

    long deleteByTargetTypeAndTargetId(AdminOverrideTargetType targetType, Long targetId);

    long deleteByTargetTypeAndTargetIdAndFieldName(
            AdminOverrideTargetType targetType,
            Long targetId,
            String fieldName
    );
}
