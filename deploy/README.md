# Match Vault 운영 배포

이 디렉터리는 Match Vault의 1차 운영 배포 구성을 담고 있습니다.
GitHub Actions가 Spring Boot jar와 React 프론트엔드를 빌드한 뒤 하나의 release bundle로 묶어 서버에 업로드하고, 서버의 `match-vault` systemd 서비스를 재시작한 다음 Spring Boot 헬스체크를 수행합니다.

## GitHub Secrets

배포를 활성화하기 전에 repository secret 또는 production environment secret으로 아래 값을 등록합니다.

- `SSH_HOST`: 운영 서버 호스트 또는 IP.
- `SSH_USER`: 배포 경로에 쓸 수 있고 `match-vault` 서비스를 sudo로 재시작할 수 있는 SSH 사용자.
- `SSH_PRIVATE_KEY`: 배포용 SSH private key.
- `SSH_PORT`: SSH 포트. 선택 값이며 없으면 `22`를 사용합니다.
- `DEPLOY_PATH`: 배포 루트 경로. 선택 값이며 없으면 `/opt/match-vault`를 사용합니다.

## 서버 디렉터리 구조

배포 스크립트는 아래 경로를 기준으로 동작합니다.

- `/opt/match-vault`: 배포 루트.
- `/opt/match-vault/releases/<github-sha>`: GitHub commit SHA별 릴리즈 디렉터리.
- `/opt/match-vault/current`: 현재 활성화된 릴리즈를 가리키는 symlink.
- `/etc/match-vault/match-vault.env`: 운영 환경 변수 파일.
- `match-vault`: systemd 서비스 이름.

`/opt/match-vault/current`는 배포 스크립트가 자동으로 만드는 symlink입니다. 서버 최초 설정 때 `current` 디렉터리를 직접 만들지 않습니다.

## 서버 최초 설정

런타임 사용자와 배포 디렉터리를 생성합니다. 아래 명령의 `deploy`는 GitHub Secret `SSH_USER`에 등록한 실제 SSH 사용자로 바꿔서 실행합니다.

```bash
sudo useradd --system --home /opt/match-vault --shell /usr/sbin/nologin match-vault
sudo usermod -aG match-vault deploy
sudo mkdir -p /opt/match-vault/releases /opt/match-vault/incoming /etc/match-vault
sudo chown -R deploy:match-vault /opt/match-vault
sudo chmod -R 2775 /opt/match-vault
```

`/etc/match-vault/match-vault.env` 파일을 생성합니다.

```dotenv
SPRING_PROFILES_ACTIVE=prod
SERVER_PORT=8080

DB_URL=jdbc:mysql://127.0.0.1:3306/match_vault?serverTimezone=Asia/Seoul&characterEncoding=UTF-8
DB_USERNAME=match_vault
DB_PASSWORD=change-me

SPRING_DATA_REDIS_HOST=127.0.0.1
SPRING_DATA_REDIS_PORT=6379

API_FOOTBALL_KEY=change-me
API_FOOTBALL_HOST=
API_FOOTBALL_LEAGUE_SEASONS_SYNC_RUN_ON_STARTUP=false
API_FOOTBALL_TEAMS_SYNC_RUN_ON_STARTUP=false
API_FOOTBALL_STANDINGS_SYNC_RUN_ON_STARTUP=false
API_FOOTBALL_FIXTURES_SYNC_RUN_ON_STARTUP=false
API_FOOTBALL_FIXTURE_DETAILS_SYNC_RUN_ON_STARTUP=false
API_FOOTBALL_PLAYERS_REGISTERED_SYNC_RUN_ON_STARTUP=false
API_FOOTBALL_INJURIES_SYNC_RUN_ON_STARTUP=false
```

운영 배포 안정성을 위해 startup sync는 기본적으로 꺼두는 것을 권장합니다. 외부 API가 503을 반환하거나 응답이 느린 경우, 앱 재시작과 GitHub Actions 헬스체크가 외부 API 상태에 영향을 받을 수 있습니다. 초기 데이터 적재가 필요하면 배포가 끝난 뒤 관리자 기능이나 별도 수동 작업으로 실행합니다.

환경 변수 파일을 작성한 뒤 권한을 제한합니다.

```bash
sudo chown root:match-vault /etc/match-vault/match-vault.env
sudo chmod 640 /etc/match-vault/match-vault.env
```

systemd unit을 설치합니다.

```bash
sudo cp deploy/systemd/match-vault.service.example /etc/systemd/system/match-vault.service
sudo systemctl daemon-reload
sudo systemctl enable match-vault
```

Nginx site 설정을 설치합니다.

```bash
sudo cp deploy/nginx/match-vault.conf.example /etc/nginx/sites-available/match-vault.conf
sudo ln -s /etc/nginx/sites-available/match-vault.conf /etc/nginx/sites-enabled/match-vault.conf
sudo nginx -t
sudo systemctl reload nginx
```

Nginx 예시 파일에는 `server_name example.com;`이 들어 있습니다. 활성화하기 전에 실제 도메인 또는 서버 IP에 맞게 `server_name`을 변경합니다.

## 배포 사용자 sudo 권한

GitHub Actions가 SSH로 접속하는 사용자는 비밀번호 입력 없이 아래 명령을 실행할 수 있어야 합니다.

```text
systemctl daemon-reload
systemctl restart match-vault
```

먼저 서버에서 `command -v systemctl`로 `systemctl` 경로를 확인합니다. 예를 들어 경로가 `/usr/bin/systemctl`이라면 sudoers를 아래처럼 좁게 설정할 수 있습니다.

```text
deploy ALL=(root) NOPASSWD: /usr/bin/systemctl daemon-reload, /usr/bin/systemctl restart match-vault
```

위 예시의 `deploy` 사용자명과 `systemctl` 경로는 서버 환경에 맞게 조정합니다.

## 헬스체크 대기 시간 조정

배포 스크립트는 `systemctl restart` 직후 앱이 바로 준비되었다고 가정하지 않습니다. 기본적으로 10초 대기한 뒤 최대 10분 동안 `/actuator/health`를 확인합니다.

필요하면 GitHub Actions의 remote deployment 단계나 서버 환경에서 아래 환경 변수로 조정할 수 있습니다.

- `HEALTH_URL`: 헬스체크 URL. 기본값은 `http://127.0.0.1:8080/actuator/health`.
- `HEALTH_INITIAL_DELAY_SECONDS`: 첫 헬스체크 전 대기 시간. 기본값은 `10`.
- `HEALTH_TIMEOUT_SECONDS`: 전체 헬스체크 최대 대기 시간. 기본값은 `600`.
- `HEALTH_INTERVAL_SECONDS`: 헬스체크 반복 간격. 기본값은 `5`.

## 배포 후 검증

배포가 끝난 뒤 서버에서 아래 명령으로 상태를 확인합니다.

```bash
systemctl status match-vault
curl http://127.0.0.1:8080/actuator/health
curl http://127.0.0.1/api/v1/home/summary?season=2025
```

그 다음 Nginx를 거치는 공개 경로도 확인합니다.

- `/`
- `/league/overview`
- `/api/v1/home/summary?season=2025`
