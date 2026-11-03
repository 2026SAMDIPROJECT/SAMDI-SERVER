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
