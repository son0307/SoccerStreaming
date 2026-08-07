package com.son.soccerStreaming.admin.repository;

import com.son.soccerStreaming.admin.entity.AdminFieldOverride;
import com.son.soccerStreaming.admin.entity.AdminOverrideTargetType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AdminFieldOverrideRepository extends JpaRepository<AdminFieldOverride, Long> {

    Optional<AdminFieldOverride> findByTargetTypeAndTargetIdAndFieldName(
            AdminOverrideTargetType targetType,
            Long targetId,
            String fieldName
    );

    boolean existsByTargetTypeAndTargetIdAndFieldName(
            AdminOverrideTargetType targetType,
            Long targetId,
            String fieldName
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
