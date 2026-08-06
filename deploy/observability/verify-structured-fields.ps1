param(
    [string]$ElasticsearchUrl = "http://localhost:9200",
    [string]$IndexPattern = "match-vault-logs-*"
)

$ErrorActionPreference = "Stop"

# API-Football 실행 단위 집계 로그가 Elasticsearch에 구조화되어 저장됐는지 확인할 핵심 필드다.
$requiredFields = @(
    "event.code",
    "event.outcome",
    "external_api.provider",
    "api_football.retry_batch_id",
    "api_football.retry_total_units",
    "api_football.retry_failed_units"
)

try {
    $null = Invoke-RestMethod -Uri "$ElasticsearchUrl/_cluster/health"
} catch {
    Write-Error "Elasticsearch에 연결할 수 없습니다: $ElasticsearchUrl"
    exit 1
}

$indices = @(
    Invoke-RestMethod -Uri "$ElasticsearchUrl/_cat/indices/$IndexPattern`?format=json&h=index,docs.count"
)

if ($indices.Count -eq 0) {
    Write-Error "'$IndexPattern' 인덱스가 없습니다. 애플리케이션과 Logstash가 실행 중인지 확인하세요."
    exit 1
}

Write-Host "수집 인덱스"
$indices | Format-Table -AutoSize

$fieldList = [Uri]::EscapeDataString(($requiredFields -join ","))
$fieldCaps = Invoke-RestMethod -Uri "$ElasticsearchUrl/$IndexPattern/_field_caps?fields=$fieldList"
$mappedFields = @($fieldCaps.fields.PSObject.Properties.Name)
$missingFields = @($requiredFields | Where-Object { $_ -notin $mappedFields })

Write-Host "구조화 필드 매핑"
foreach ($field in $requiredFields) {
    $mapping = $fieldCaps.fields.PSObject.Properties[$field]
    if ($null -eq $mapping) {
        Write-Host "[누락] $field"
        continue
    }

    $types = @($mapping.Value.PSObject.Properties.Name) -join ", "
    Write-Host "[확인] $field ($types)"
}

if ($missingFields.Count -gt 0) {
    Write-Error ("필드가 아직 색인되지 않았습니다: " + ($missingFields -join ", ") +
        ". API-Football 재시도 Batch 완료 로그를 발생시킨 뒤 다시 실행하세요.")
    exit 2
}

$searchBody = @{
    size = 1
    sort = @(
        @{
            "@timestamp" = @{
                order = "desc"
            }
        }
    )
    query = @{
        match = @{
            "event.code" = "API_FOOTBALL_SYNC_RETRY_BATCH_COMPLETED"
        }
    }
} | ConvertTo-Json -Depth 8

$searchResult = Invoke-RestMethod `
    -Method Post `
    -Uri "$ElasticsearchUrl/$IndexPattern/_search" `
    -ContentType "application/json" `
    -Body $searchBody

if ($searchResult.hits.total.value -eq 0) {
    Write-Error "필드 매핑은 존재하지만 API-Football Batch 완료 이벤트 문서를 찾지 못했습니다."
    exit 3
}

Write-Host "최근 API-Football Batch 완료 이벤트"
$searchResult.hits.hits[0]._source | ConvertTo-Json -Depth 8
Write-Host "구조화 로그 수집 검증을 통과했습니다."
