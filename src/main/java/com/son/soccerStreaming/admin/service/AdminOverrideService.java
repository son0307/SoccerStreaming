package com.son.soccerStreaming.admin.service;

import com.son.soccerStreaming.admin.entity.AdminFieldOverride;
import com.son.soccerStreaming.admin.entity.AdminOverrideTargetType;
import com.son.soccerStreaming.admin.repository.AdminFieldOverrideRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminOverrideService {

    private final AdminFieldOverrideRepository adminFieldOverrideRepository;

    @Transactional
    public void markOverrides(AdminOverrideTargetType targetType, Long targetId, Collection<String> fieldNames) {
        for (String fieldName : fieldNames) {
            adminFieldOverrideRepository
                    .findByTargetTypeAndTargetIdAndFieldName(targetType, targetId, fieldName)
                    .ifPresentOrElse(
                            AdminFieldOverride::touch,
                            () -> adminFieldOverrideRepository.save(AdminFieldOverride.of(targetType, targetId, fieldName))
                    );
        }
    }

    @Transactional(readOnly = true)
    public Set<String> overriddenFields(AdminOverrideTargetType targetType, Long targetId, Collection<String> fieldNames) {
        return adminFieldOverrideRepository
                .findAllByTargetTypeAndTargetIdAndFieldNameIn(targetType, targetId, fieldNames)
                .stream()
                .map(AdminFieldOverride::getFieldName)
                .collect(Collectors.toSet());
    }

    @Transactional(readOnly = true)
    public boolean isOverriddenForEventSync(
            AdminOverrideTargetType targetType,
            Long targetId,
            String fieldName
    ) {
        return adminFieldOverrideRepository.findForEventSync(targetType, targetId, fieldName).isPresent();
    }

    @Transactional(readOnly = true)
    public List<OverrideInfo> overrideInfos(AdminOverrideTargetType targetType, Long targetId) {
        return adminFieldOverrideRepository
                .findAllByTargetTypeAndTargetIdOrderByFieldNameAsc(targetType, targetId)
                .stream()
                .map(override -> new OverrideInfo(override.getFieldName(), override.getUpdatedAt()))
                .toList();
    }

    @Transactional
    public long clearOverrides(AdminOverrideTargetType targetType, Long targetId) {
        return adminFieldOverrideRepository.deleteByTargetTypeAndTargetId(targetType, targetId);
    }

    @Transactional
    public long clearOverride(AdminOverrideTargetType targetType, Long targetId, String fieldName) {
        return adminFieldOverrideRepository.deleteByTargetTypeAndTargetIdAndFieldName(targetType, targetId, fieldName);
    }

    public <T> T apiValueUnlessOverridden(Set<String> overrides, String fieldName, T currentValue, T apiValue) {
        return overrides.contains(fieldName) ? currentValue : apiValue;
    }

    public record OverrideInfo(String fieldName, LocalDateTime updatedAt) {
    }
}
