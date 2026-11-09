# 인증 DB 구조 검토

이 문서는 팀 검토용이며 공유 DB에 실행한 마이그레이션이 아니다. 실제 DB에 연결하지 않고 전달받은 DDL과 현재 엔티티를 비교했다. 운영 적용 전 현재 DB의 테이블과 제약 조건을 확인해야 한다.

2026-11-09 IntelliJ 로컬 스키마 캐시에는 `users`의 `id`, `nickname`, `login_id`, `email`, `password`, `created_at`, `updated_at`이 기록되어 있다. `nickname`은 NOT NULL, 최대 50자이며 `user_id`라는 이름의 UNIQUE 인덱스가 있다. `email`과 `login_id`의 UNIQUE 인덱스 이름은 각각 `email`, `uq_users_login_id`다. 현재 `User` 엔티티는 이 컬럼 이름에 맞춰 매핑한다. 캐시와 현재 공유 DB의 일치 여부는 직접 확인하지 못했다. 기존 `users` 테이블은 생성하거나 변경하지 않는다.

신규 테이블은 현재 존재 여부를 확인한 후 다음 DDL을 검토한다.

- `docs/sql/auth-tables-final.sql`: `email_verifications`, `refresh_tokens` 생성 초안.
- `docs/sql/login-attempts.sql`: 인덱스를 포함한 `login_attempts` 생성 초안.
- `docs/sql/login-attempts-cleanup-index.sql`: `login_attempts`가 이미 있고 해당 인덱스가 없는 경우에만 적용할 인덱스 초안. 위 생성 DDL과 중복 적용하지 않는다.

`email-verification-draft.sql`과 `email-verification-send.sql`은 이전 초안이므로 최종 DDL과 함께 적용하지 않는다. `ddl-auto: validate`는 스키마를 자동 변경하지 않으며, 모든 UNIQUE 제약 조건이나 인덱스가 의도대로 존재하는지 대신 확인해 주지는 않는다.

캐시의 `users.id`는 BIGINT UNSIGNED이지만 Java `Long`과 Refresh Token의 `user_id`는 부호 있는 BIGINT 범위다. 외래키는 실제 DB의 타입과 기존 데이터 검토 전까지 추가하지 않았다. 캐시에는 별도의 `score.nickname` 컬럼도 있으므로 닉네임 변경 시 해당 컬럼과의 동기화 정책을 팀에서 확인해야 한다. DB와 애플리케이션의 시간대, TLS 인증서, 기존 사용자에 대한 이메일 인증 정책도 배포 전에 확인해야 한다.
