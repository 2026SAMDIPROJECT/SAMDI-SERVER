# Cloudtype Spring Boot 배포

## Cloudtype 서비스 설정

Spring Boot 서비스와 MariaDB 서비스를 같은 Cloudtype 프로젝트 및 내부 네트워크에 배치합니다. Cloudtype DB 상세 화면에서 서비스 간 내부 연결 주소와 포트를 확인한 후 사용합니다. 공개 접속 주소 `svc.sel3.cloudtype.app:31983`는 사용하지 않습니다. `DB_HOST`에는 내부 호스트, `DB_PORT`에는 해당 내부 포트를 입력합니다. 내부 주소를 확인하지 못했다면 임의의 호스트나 포트를 넣지 말고 DB 서비스 담당자에게 확인합니다.

Cloudtype은 배포 서비스와 데이터베이스를 프라이빗 네트워크로 연결할 수 있다고 안내합니다. 실제 내부 주소와 포트는 해당 DB 서비스 설정에서 확인해야 합니다: [Cloudtype 데이터베이스 안내](https://docs.cloudtype.io/guide/integrations/database).

Cloudtype의 서비스 설정에서 다음을 지정합니다.

- Java 런타임: Java 21
- 빌드 명령: `./gradlew --no-daemon clean bootJar`
- 시작 명령: `java -jar build/libs/SAMDI-0.0.1-SNAPSHOT.jar`
- 공개 포트: `8080/http` (또는 아래 `SERVER_PORT`와 같은 포트)
- 환경변수: 아래 표 참조

프로필은 `SPRING_PROFILES_ACTIVE=cloudtype`으로 설정합니다. Cloudtype 프로필은 `server.port` 기본값 8080을 사용하며 `SERVER_PORT`, 이어서 `PORT`로 덮어쓸 수 있습니다. 공개 포트와 애플리케이션 포트가 같아야 합니다.

## 필수 환경변수

실제 비밀값은 Cloudtype의 환경변수/Secret 설정 화면에 직접 입력합니다. 저장소, README, 빌드 명령, 코드 또는 로그에 비밀값을 넣지 않습니다.

| 이름 | 필수 | 설명 |
| --- | --- | --- |
| `SPRING_PROFILES_ACTIVE` | 예 | `cloudtype` |
| `DB_HOST` | 예 | MariaDB의 Cloudtype 내부 호스트명. 공개 주소 사용 금지 |
| `DB_PORT` | 예 | MariaDB의 내부 연결 포트 |
| `DB_NAME` | 예 | `game_db` |
| `DB_USERNAME` | 예 | MariaDB 접속 사용자 |
| `DB_PASSWORD` | 예 | 접속 사용자 비밀번호; Secret으로 저장 |
| `MAIL_USERNAME` | 예 | 인증 메일 발송 Gmail 주소 |
| `MAIL_PASSWORD` | 예 | Gmail 앱 비밀번호; 새 앱 비밀번호를 Secret으로 저장 |
| `EMAIL_VERIFICATION_SECRET` | 예 | 이메일 인증 HMAC 비밀키 |
| `JWT_SECRET` | 예 | JWT 서명 키; 최소 256비트(32바이트) 이상 |

`EMAIL_VERIFICATION_SECRET`과 `JWT_SECRET`은 서로 다른 무작위 값으로 생성하고, 하나의 같은 DB를 공유하는 애플리케이션 인스턴스에는 각각 동일한 값을 설정합니다. 예를 들어 각 키를 만들 때 `openssl rand -base64 32`를 실행하고 결과를 해당 Secret 칸에 직접 붙여넣을 수 있습니다. 생성된 값을 채팅, 로그, 저장소에 복사하지 않습니다.

## 선택 환경변수와 기본값

| 이름 | 기본값 | 설명 |
| --- | --- | --- |
| `MAIL_HOST` | `smtp.gmail.com` | SMTP 서버 |
| `MAIL_PORT` | `587` | SMTP STARTTLS 포트 |
| `SERVER_PORT` | `PORT`, 없으면 `8080` | Spring Boot HTTP 포트 |
| `JWT_ACCESS_TOKEN_EXPIRATION_MINUTES` | `60` | Access Token 만료(분) |
| `JWT_REFRESH_TOKEN_EXPIRATION_MINUTES` | `20160` | Refresh Token 만료(분, 14일) |
| `LOGIN_MAX_FAILURES` | `5` | 로그인 실패 제한 횟수 |
| `LOGIN_WINDOW_MINUTES` | `5` | 실패 집계 시간(분) |
| `LOGIN_BLOCK_MINUTES` | `5` | 제한 시간(분) |
| `LOGIN_CLEANUP_INTERVAL` | `PT10M` | 로그인 실패 기록 정리 주기 |
| `LOGIN_CLEANUP_BATCH_SIZE` | `100` | 정리 작업 배치 크기 |

## DB 및 TLS 동작

Cloudtype 프로필은 `jdbc:mariadb://${DB_HOST}:${DB_PORT}/${DB_NAME}?sslMode=disable`을 사용합니다. TLS가 비활성화된 MariaDB와 내부 네트워크로 통신한다는 팀의 결정에 따른 예외입니다. 이 연결은 DB 전송 구간을 암호화하거나 인증서/호스트명을 검증하지 않습니다. 따라서 반드시 Cloudtype이 제공하는 내부 연결 주소를 사용하고, DB 접근을 해당 내부 네트워크로 제한해야 합니다. 알려진 공개 주소 `svc.sel3.cloudtype.app`를 `DB_HOST`로 넣으면 애플리케이션이 시작 전에 거부합니다.

다른 프로필은 기존 TLS `verify-full` 정책을 유지합니다. 모든 비테스트 환경에서 JPA 설정은 `ddl-auto: validate`입니다. 서버 시작 시 테이블을 생성, 변경, 삭제하지 않으며 실제 DB 스키마가 Entity 및 기능별 테이블 정의와 다르면 실행이 실패합니다. 배포 전에 DB 담당자가 다음 DDL 초안을 실제 테이블과 비교하고 필요한 변경을 별도 승인·적용해야 합니다.

- `docs/sql/auth-tables-final.sql`
- `docs/sql/login-attempts.sql`
- `docs/sql/score-table.sql`
- `docs/sql/achievements.sql`

SQL 파일은 애플리케이션 시작 과정에서 자동 실행되지 않습니다. 기존 데이터베이스의 백업 및 마이그레이션 여부는 DB 담당자가 확인합니다.

## 배포 확인 순서

1. 위 필수 환경변수를 Cloudtype 서비스의 Environment Variables/Secrets에 입력합니다.
2. DB 서비스 상세 화면에서 내부 호스트와 포트를 확인해 `DB_HOST`, `DB_PORT`에 설정합니다. 공개 주소 및 공개 포트는 사용하지 않습니다.
3. 서비스 공개 포트를 `8080/http`로 설정하거나 `SERVER_PORT`와 일치시킵니다.
4. DB 담당자가 실제 `game_db` 테이블이 현재 애플리케이션 Entity와 DDL 정의를 만족하는지 확인합니다. `validate` 오류가 나면 앱 시작 실패 로그의 누락/불일치 테이블을 검토하고 승인된 스키마 작업 후 재배포합니다.
5. 빌드/시작 로그에서 Spring 프로필이 `cloudtype`인지 확인합니다. DB 연결 및 Hibernate schema validation이 성공해야 애플리케이션 시작이 완료됩니다. 비밀번호나 전체 접속 URL이 로그에 출력되지 않는지 확인합니다.
6. 배포 도메인에서 `GET /v3/api-docs`가 JSON을 반환하는지 확인합니다. 이후 테스트 계정으로 이메일 발송·검증, 회원가입, 로그인, 토큰 갱신, 인증된 API를 순서대로 확인합니다.

현재 Actuator health endpoint는 의존성/설정에 포함되어 있지 않으므로 `/actuator/health`의 응답을 배포 확인 기준으로 사용하지 않습니다.

## 자주 발생하는 오류

- **필수 환경변수 누락:** `Could not resolve placeholder` 등으로 시작이 실패합니다. Cloudtype 변수 이름을 표와 정확히 맞추고 서비스 재배포를 실행합니다.
- **공개 DB 호스트를 입력함:** 알려진 공개 DB 호스트는 애플리케이션이 시작 전에 거부합니다. DB 서비스 화면에서 내부 호스트와 포트를 다시 확인합니다.
- **연결 거부/시간 초과:** Spring Boot와 MariaDB가 같은 프로젝트/내부 네트워크인지, DB가 실행 중인지, 내부 포트를 사용했는지 확인합니다. `@@port` 값만으로 Cloudtype의 서비스 연결 포트가 확정되지는 않습니다.
- **Access denied:** `DB_USERNAME`, `DB_PASSWORD`, 해당 DB 계정의 `game_db` 권한을 DB 담당자가 확인합니다.
- **TLS 설정 오류:** Cloudtype 프로필 URL에 `sslMode=disable`이 포함됐는지 확인합니다. 이 옵션을 다른 프로필이나 외부 DB 주소에 사용하지 않습니다.
- **스키마 검증 실패:** 기존 테이블을 자동으로 고치지 않습니다. Entity, 실제 DDL, `docs/sql`을 비교해 DB 담당자가 마이그레이션 여부를 결정합니다.
- **Gmail 인증/발송 실패:** Gmail 계정의 2단계 인증과 앱 비밀번호, `MAIL_USERNAME`, `MAIL_PASSWORD`, SMTP 587/STARTTLS, Cloudtype의 외부 SMTP 연결 가능 여부를 확인합니다. 기존에 노출된 앱 비밀번호는 폐기하고 새 앱 비밀번호를 사용합니다.
- **JWT 키 오류:** `JWT_SECRET`이 최소 32바이트 이상의 키인지 확인하고, 이전과 같은 DB를 공유하는 서버는 같은 키를 사용합니다. 키 교체 시 기존 JWT는 검증되지 않습니다.
- **API 접속 실패:** Cloudtype 공개 포트와 `SERVER_PORT`/`PORT`가 일치하는지, 앱 상태가 Running인지 확인합니다.
