# SAMDI-SERVER

## Cloudtype 데이터베이스 설정

`cloudtype` 프로필은 기존 MariaDB 드라이버를 사용합니다. 연결 정보는 실행 환경에 다음 환경변수로 설정합니다.

- `DB_HOST`: `svc.sel3.cloudtype.app`
- `DB_PORT`: `31983`
- `DB_NAME`: Cloudtype에서 확인한 데이터베이스 이름
- `DB_USERNAME`: Cloudtype에서 확인한 데이터베이스 사용자 이름
- `DB_PASSWORD`: 해당 사용자의 비밀번호
- `JWT_SECRET`, `EMAIL_VERIFICATION_SECRET`: 각각 독립적으로 생성한 비밀키
- `MAIL_USERNAME`, `MAIL_PASSWORD`: 이메일 발송 계정 정보

예를 들어 환경변수를 안전하게 설정한 후 `./gradlew bootRun --args='--spring.profiles.active=cloudtype'`으로 실행합니다. 이 명령은 실제 DB 연결을 시도하므로 접속 정보와 서버 설정을 확인한 후에만 실행하세요.

DB 연결은 `sslMode=verify-full`로 인증서와 호스트명을 검증합니다. 서버의 TLS 지원 여부와 신뢰할 수 있는 인증서 설정을 먼저 확인해야 하며, 확인되지 않으면 연결을 진행하지 않습니다. 스키마 설정은 `validate`입니다. 별도의 환경변수 파일은 자동으로 읽지 않습니다.

## 인증 API

이메일 인증과 회원가입은 다음 순서로 진행합니다.

1. `POST /auth/email/send`: `{"email":"user@example.com"}`으로 인증번호를 요청합니다.
2. `POST /auth/email/verify`: `{"email":"user@example.com","code":"123456"}`을 보내고 응답의 `data.verificationToken`을 받습니다.
3. `POST /auth/signup`: `loginId`, `email`, `password`, `passwordConfirm`, `verificationToken`을 JSON으로 보냅니다. 성공 응답은 `data.email`과 `data.userName`을 포함합니다.

`POST /auth/login`은 `{"loginId":"player01","password":"..."}`을 받습니다. 성공하면 Access Token을 `Authorization: Bearer <token>` 응답 헤더로, Refresh Token을 `__Secure-refreshToken` 쿠키로 반환합니다. 토큰은 응답 JSON에 포함되지 않습니다.

인증이 필요한 요청에는 `Authorization: Bearer <accessToken>` 헤더를 사용합니다. `POST /auth/refresh`에는 Refresh Token 쿠키와 `X-Requested-With: XMLHttpRequest` 헤더를 보내며 본문은 필요하지 않습니다. 재발급 응답의 Access Token 헤더와 Refresh Token 쿠키를 사용하고 이전 Refresh Token은 폐기해야 합니다. `POST /auth/logout`에는 Access Token 헤더가 필요하며, 성공 시 Refresh Token과 쿠키가 삭제됩니다. Access Token은 로그아웃 후에도 만료 전까지 유효합니다.

쿠키는 `Secure`, `HttpOnly`, `SameSite=Strict`, `Path=/auth/refresh` 속성을 사용합니다. Unity에서는 HTTPS 연결과 쿠키의 저장·재전송을 지원하는 HTTP 클라이언트가 필요합니다. 브라우저의 교차 출처 호출은 별도 CORS 정책이 필요하며, 쿠키 인증과 재발급 요청 헤더를 고려해 허용 출처를 제한해야 합니다.

기본 Access Token 만료는 60분, Refresh Token 만료는 14일입니다. 각각 `JWT_ACCESS_TOKEN_EXPIRATION_MINUTES`, `JWT_REFRESH_TOKEN_EXPIRATION_MINUTES`로 설정합니다. 로그인 실패 제한은 `LOGIN_MAX_FAILURES`, `LOGIN_WINDOW_MINUTES`, `LOGIN_BLOCK_MINUTES`로 설정하며 기본값은 5회, 5분, 5분입니다. 만료된 로그인 실패 기록은 `LOGIN_CLEANUP_INTERVAL`과 `LOGIN_CLEANUP_BATCH_SIZE` 설정에 따라 정리합니다.

운영 DB 준비 사항은 [인증 DB 검토](docs/auth-db-review.md)와 `docs/sql`의 DDL을 확인하세요. 운영 DB에 DDL이 자동 적용되지는 않습니다.
