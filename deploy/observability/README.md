# Match Vault 로컬 로그 수집

이 구성은 로컬 개발 환경에서 다음 흐름을 검증하기 위한 것이다.

```text
Logback -> logs/application.json -> Logstash -> Elasticsearch -> Kibana
```

Elasticsearch 보안 기능을 비활성화했으므로 운영 환경에서 그대로 사용하면 안 된다.
호스트 포트는 외부에 노출되지 않도록 `127.0.0.1`에만 바인딩한다.
같은 호스트의 다른 프로세스는 인증 없이 로그 데이터에 접근할 수 있으므로 민감한
운영 로그를 저장하거나 외부에 포트를 공개하지 않는다.

## 1. 애플리케이션 로그 생성

프로젝트 루트에서 운영 프로필로 실행한다. 운영 로그 경로는 배포 디렉터리 구조에
의존하지 않도록 반드시 환경 변수로 명시한다.

애플리케이션 실행 전에 로그 디렉터리를 만들고 애플리케이션과 Logstash가 각각
쓰기와 읽기를 할 수 있는지 확인한다.

```powershell
New-Item -ItemType Directory -Force logs
$env:LOG_FILE_NAME="logs/application.json"
$env:LOG_ARCHIVE_PATTERN="logs/application.%d{yyyy-MM-dd}.%i.json.gz"
$env:LOG_HOST_PATH=(Resolve-Path logs).Path
```

Linux 서버에서 `/opt/match-vault/logs`를 공유한다면 다음처럼 준비한다.

```bash
sudo install -d -o match-vault -g match-vault -m 755 /opt/match-vault/logs
chmod 755 deploy/observability/logstash \
  deploy/observability/logstash/config \
  deploy/observability/logstash/pipeline
chmod 644 deploy/observability/logstash/config/*.yml \
  deploy/observability/logstash/pipeline/*.conf
```

운영 애플리케이션 환경 파일에는 절대경로를 지정한다.

```bash
LOG_FILE_NAME=/opt/match-vault/logs/application.json
LOG_ARCHIVE_PATTERN=/opt/match-vault/logs/application.%d{yyyy-MM-dd}.%i.json.gz
```

Compose 실행 셸에도 같은 호스트 로그 디렉터리를 지정한다.

```bash
export LOG_HOST_PATH=/opt/match-vault/logs
```

```powershell
$env:SPRING_PROFILES_ACTIVE="prod"
.\gradlew.bat bootRun
```

## 2. Elasticsearch, Logstash, Kibana 실행

로컬 설정 파일을 만든 뒤 `LOG_HOST_PATH`를 실제 로그 디렉터리의 절대 경로로 수정한다.

```powershell
Copy-Item deploy/observability/.env.example deploy/observability/.env
```

`.env`는 Git에서 제외되어 있다. 운영 환경에서는 배포 환경 변수로 같은 로그 경로를 주입한다.

다른 터미널에서 다음 명령을 실행한다.

```powershell
docker compose `
  --env-file deploy/observability/.env `
  -f deploy/observability/compose.yml up -d
docker compose `
  --env-file deploy/observability/.env `
  -f deploy/observability/compose.yml ps
```

Logstash 컨테이너가 로그 파일을 읽을 수 있는지 확인한다.

```powershell
docker compose `
  --env-file deploy/observability/.env `
  -f deploy/observability/compose.yml exec logstash `
  test -r /var/log/match-vault/application.json
```

Logstash는 첫 실행 시 `application.json`을 처음부터 읽는다. 이후 읽은 위치는
`logstash-data` 볼륨의 sincedb 파일에 저장하므로 컨테이너를 재시작해도 이미 읽은
로그를 처음부터 다시 적재하지 않는다.

## 3. 수집 결과 확인

Elasticsearch 상태와 생성된 인덱스를 확인한다.

```powershell
Invoke-RestMethod http://localhost:9200/_cluster/health
Invoke-RestMethod http://localhost:9200/_cat/indices/match-vault-logs-*?v
```

최근 로그 문서를 확인한다.

```powershell
$body = '{"size":5,"sort":[{"@timestamp":{"order":"desc"}}]}'
Invoke-RestMethod -Method Post `
  -Uri http://localhost:9200/match-vault-logs-*/_search `
  -ContentType application/json `
  -Body $body
```

API-Football 실행 단위 집계 로그에 필요한 핵심 필드와 실제 Batch 완료 문서를
한 번에 확인하려면 다음 검증 스크립트를 실행한다.

```powershell
deploy/observability/verify-structured-fields.ps1
```

다음 필드를 모두 확인하고 `API_FOOTBALL_SYNC_RETRY_BATCH_COMPLETED` 이벤트가
실제로 색인된 경우에만 성공한다.

- `event.code`
- `event.outcome`
- `external_api.provider`
- `api_football.retry_batch_id`
- `api_football.retry_total_units`
- `api_football.retry_failed_units`

필드 누락으로 종료되면 아직 해당 종류의 로그가 Elasticsearch에 들어오지 않은
상태다. API-Football 재시도 Batch가 완료되는 흐름을 한 번 실행한 뒤 다시 검증한다.

Logstash 자체 상태나 오류는 다음 명령으로 확인한다.

```powershell
Invoke-RestMethod http://localhost:9600/_node/stats/pipelines
docker compose `
  --env-file deploy/observability/.env `
  -f deploy/observability/compose.yml logs logstash
```

## 4. Kibana에서 로그 확인

브라우저에서 `http://localhost:5601`에 접속한다.

1. **Management > Stack Management > Data Views**로 이동한다.
2. `Create data view`를 선택한다.
3. 이름과 인덱스 패턴에 `match-vault-logs-*`를 입력한다.
4. Timestamp field로 `@timestamp`를 선택해 생성한다.
5. **Analytics > Discover**에서 생성한 Data View를 선택한다.

필요에 따라 `log.level`, `service.name`, `http.request.id`,
`http.request.method`, `url.path`, `event.outcome` 필드를 열로 추가하거나 필터로 사용할 수 있다.

## 종료

데이터 볼륨을 유지하면서 컨테이너만 종료한다.

```powershell
docker compose `
  --env-file deploy/observability/.env `
  -f deploy/observability/compose.yml down
```
