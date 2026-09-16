# Wallet API 기능 목록

## 적용 범위

- `wallet` 기능은 입금 등록, 지출 등록, 잔액 조회만 제공한다.
- 거래 목록·상세·수정·삭제와 `page`/`size` 페이지 기능은 제거했다.
- 로그인/회원가입, `SecurityConfig`, 의존성, `application.yaml`은 변경하지 않았다.
- DB 접속, SQL 실행, 마이그레이션을 수행하지 않았으며 기존 테이블·컬럼·인덱스·enum을 유지했다.
- 인증은 기존 `@AuthenticationPrincipal Long userId` 연동을 사용한다.

## API 목록

공통 경로는 `/api/users/me/wallet`이며 사용자 ID는 로그인 정보에서 가져온다.

| 기능 | 요청 | 구현 파일 |
| --- | --- | --- |
| 잔액·누적 합계 조회 | `GET /api/users/me/wallet` | `WalletController`, `WalletService` |
| 입금 등록 | `POST /api/users/me/wallet/transactions/income` | `WalletController`, `WalletService` |
| 지출 등록 | `POST /api/users/me/wallet/transactions/outcome` | `WalletController`, `WalletService` |

## 입금·지출 등록

```json
{
  "amount": 5000,
  "transactionDate": "2026-09-16",
  "type": "DELIVERY_REWARD",
  "referenceId": 202609160001
}
```

- `amount`는 소수점 없는 원 단위 양의 정수다.
- 입금 type은 `DELIVERY_REWARD`다.
- 지출 type은 `ITEM_PURCHASE`, `ROBBERY_PAYMENT`, `MOTORCYCLE_PURCHASE`다.
- 지출 요청도 양수 금액을 보내며 서버가 음수 거래 금액으로 저장한다. 지출액이 잔액보다 커도 음수 잔액을 허용한다.
- `type`과 `referenceId`를 중복 요청 식별값으로 사용한다. 같은 요청을 다시 보내면 잔액에 두 번 반영하지 않는다.
- 별도 거래일 컬럼을 추가하지 않기 위해 기존 `createdAt`에 거래일의 00:00을 저장한다.

## 잔액 조회

```json
{
  "balance": 38000,
  "totalIncome": 50000,
  "totalOutcome": 12000,
  "walletVersion": 4
}
```

`balance`는 전체 입금 합계에서 전체 지출 합계를 뺀 값이다. `totalIncome`과 `totalOutcome`은 각각 양수 합계로 반환한다.

## 주요 파일

- `WalletController.java`: 외부에서 호출하는 세 가지 HTTP API 주소와 요청 연결
- `WalletService.java`: 입금·지출 검증, 중복 방지, 잔액 재계산, 잔액 합계 조회
- `TransactionRequest.java`: 등록 요청 값과 원 단위 정수 검증
- `TransactionResponse.java`: 등록 결과 형식
- `WalletResponse.java`: 잔액과 입금·지출 누적 합계 응답 형식
- `TransactionRepository.java`: 중복 확인과 잔액 재계산에 필요한 거래 조회
- `WalletExceptionHandler.java`: 잘못된 입력, 중복 요청, 지갑 없음 등의 API 오류 응답

## DB 연결 없는 검증

```sh
./gradlew test --tests 'com.example.samdi.wallet.*'
```

Wallet 단위 테스트와 MockMvc API 테스트만 실행하며 서버나 DB를 시작하지 않는다.
