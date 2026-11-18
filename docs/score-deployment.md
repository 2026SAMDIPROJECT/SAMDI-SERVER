# 점수 API 배포

## 인증

`POST /scores`는 로그인한 사용자의 `users.id`를 인증 정보에서 읽는다. [인증 PR #3](https://github.com/2026SAMDIPROJECT/SAMDI-SERVER/pull/3)을 `develop`에 먼저 합치고, 두 변경이 들어간 버전을 배포한다. 두 브랜치를 합칠 때 `.gitignore`와 `application.yaml`에 충돌이 나면 인증 PR의 환경 설정을 사용한다.

`POST /auth/login` 응답의 `Authorization` 헤더 값(`Bearer …`)을 `POST /scores` 요청 헤더에 그대로 보내면 된다. 본문은 `{"game1Score":80,"game2Score":100}` 형식이다. 인증된 요청은 `204`, 토큰이 없는 요청은 `401`이 나오는지 확인한다.

## 기존 최고 점수 이관

대상 MariaDB를 백업하고, 기존 버전의 점수 쓰기를 중단한 상태에서 진행한다.

1. [score-table.sql](sql/score-table.sql)을 실행한다. 이미 테이블이 있으면 `SHOW CREATE TABLE score`로 기본키, 외래키, 컬럼 타입과 기본값이 SQL과 일치하는지 확인한다.
2. `SHOW COLUMNS FROM users LIKE 'highest_score'`로 기존 컬럼이 있는지 확인한다. 컬럼이 있으면 [score-backfill.sql](sql/score-backfill.sql)을 실행한다. 마지막 검증 쿼리의 결과는 0행이어야 한다. 기존 총점과 새 총점 중 큰 값을 남기고, 이미 있는 게임별 기록은 그대로 둔다. 새로 옮기는 행은 게임별 점수가 0에서 시작한다.
3. 이미 기존 컬럼을 삭제했다면 `score`에 기록이 옮겨졌는지 확인한다. 옮기기 전에 컬럼을 삭제했다면 백업에서 기존 기록을 복구해야 한다.
4. 새 버전에서 점수 저장을 확인한 뒤, 기존 컬럼을 참조하는 코드가 모두 제거됐을 때 `users.highest_score`를 삭제한다.
