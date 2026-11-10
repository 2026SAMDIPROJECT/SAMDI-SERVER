# 로그인한 계정의 업적 조회

`GET /api/achievements/me`

1. 기존 계정으로 `POST /auth/login`을 호출한다.
2. 로그인 성공 응답의 `Authorization` 헤더에서 Access Token을 받는다.
3. 업적 조회 요청에 `Authorization: Bearer <Access Token>` 헤더를 보낸다.

Postman에서는 Authorization 탭의 Bearer Token에 토큰 값만 넣는다.
사용자 ID는 URL이나 요청 본문에 넣지 않는다. JWT 필터가 인증 정보에 넣은 계정 ID만 사용한다.
토큰이 없거나 유효하지 않으면 401이다. 개발용 인증 우회 설정은 없다.
이전 `/user/achievement/{id}/getAchievement` 경로는 사용하지 않는다.

공개 업적의 이름, 설명, 목표 수치와 개인 진행도를 배열로 반환한다.
개인 기록이 없으면 progress는 0이고, 공개 업적이 없으면 빈 배열이다.
숨김 업적은 달성 여부와 관계없이 제외한다.
게임 결과를 받아 진행도를 올리는 기능은 아직 없다.

## DB 준비

`docs/sql/achievements.sql`은 기존 users 테이블을 참조하는 업적 테이블 두 개의 생성문이다.
기존 users.id가 BIGINT UNSIGNED인지 확인한 뒤 필요한 환경에서 수동 적용한다.
공유 DB에는 자동 실행하지 않는다.

로그인 기능은 최신 develop에 반영된 PR #3을 사용한다. README와 docs/auth-db-review.md의 환경 변수,
인증 테이블 및 TLS 준비 사항을 따라야 한다. 준비되지 않은 공유 DB에 앱을 실행하지 않는다.
