# 인증 DB 구조 회의 초안

이 문서는 실행용 마이그레이션이 아니다. 공유 DB의 현재 DDL과 기존 데이터, 팀의 인증 정책을 확인한 뒤 확정해야 한다. SQL을 공유 DB에 실행하지 않았다.

이전에 전달받은 users DDL에는 user_id, login_id, password_hash, email, email_verified_at, created_at과 uq_users_login_id, uq_users_email이 있다. 현재 Entity는 id, user_name, password, updated_at을 사용하므로 그대로는 일치하지 않는다. 현재 실제 DDL은 확인하지 못했다.

회의에서 결정할 사항:
- login_id와 별도 nickname을 모두 저장할지, 기존 user_name을 어느 필드에 대응할지
- 기존 사용자의 이메일 인증 여부와 인증 전 계정의 로그인 정책
- refresh_tokens 실제 존재 여부, 동시 기기 로그인 허용 및 회전 정책
- 이메일 인증 상태의 저장·만료·경합 처리, 기존 데이터 보존 및 전환 방법

아래는 새 설치를 가정한 논의용 예시다. users도 새 데이터베이스라면 필요하다. 기존 공유 DB에는 users가 이미 있다고 전달받았으므로 그 DB에서 CREATE TABLE을 실행하지 않는다. 아래 users는 현재 Entity 기준이며 loginId와 nickname 분리 및 인증 상태 저장은 팀과 협의 후 확정해야 한다. 기존 테이블을 변경하는 SQL이 아니며, 타입과 이름은 확정되지 않았다.

```sql
CREATE TABLE users (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    email VARCHAR(254) NOT NULL,
    password VARCHAR(255) NOT NULL,
    user_name VARCHAR(50) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT email UNIQUE (email),
    CONSTRAINT user_id UNIQUE (user_name)
);

CREATE TABLE email_verifications (
    email VARCHAR(254) NOT NULL PRIMARY KEY,
    code_hash CHAR(64) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    last_sent_at DATETIME(6) NOT NULL,
    verified_at DATETIME(6) NULL
);

CREATE TABLE refresh_tokens (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT UNSIGNED NOT NULL,
    token_hash CHAR(64) NOT NULL,
    session_id VARCHAR(36) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    UNIQUE KEY uq_refresh_tokens_user_id (user_id)
);
```

이 초안은 인증코드 발송 제한의 기간별 횟수, 동시 요청 잠금, 인증 증명 소진, 토큰 회전의 원자성, 외래키 및 만료 데이터 정리를 완결하지 않는다. 구현과 운영 정책을 검증한 후 수정해야 한다.

## 현재 엔티티 기준 검토 파일

신규 테이블의 현재 엔티티 기준 DDL은 `docs/sql/auth-tables-final.sql`이다. 기존 `email-verification-draft.sql`과 `email-verification-send.sql`은 이전 초안이므로 함께 적용하지 않는다. 이 파일도 팀 검토 전에는 공유 DB에 실행하지 않는다. `users`는 생성하거나 변경하지 않는다.

기존에 전달된 `users.user_id`는 BIGINT UNSIGNED이지만 Java `Long`과 RefreshToken 엔티티의 `user_id`는 부호 있는 BIGINT 범위다. 외래키는 실제 DB의 타입·기존 데이터 검토 전까지 초안에 추가하지 않았다. 이메일 인증 테이블과 Refresh Token 테이블의 기존 존재 여부, 문자 집합, 인증서 및 접근 권한을 실제 연결 전에 확인해야 한다.
