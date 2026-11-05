# 02. 멱등한 결제 호출

## 1. 개요

[1.1] 앞 단계의 원장에 결제·환불 요청과 외부 결제 대행사(PSP) 모형을 더한다. 요청 접수, 원격 처리, 원장 반영을 분리하고 중간에 응답을 잃어도 돈을 두 번 옮기지 않아야 한다. 예상 구현 시간은 2~3시간이다. 앞 단계 명령과 출력은 그대로 유지한다.

[1.2] 실제 네트워크와 데이터베이스 대신 입력 명령으로 각 경계를 진행한다. `CRASH`는 프로세스를 종료하지 않고 휘발성 정보만 버리는 장애 모형이다. 여기서 영속 상태란 이 명령을 거쳐도 남는 상태를 뜻하며 파일 저장은 요구하지 않는다. 명령 사이에 다른 요청이나 앞 단계 명령을 실행할 수 있다.

## 2. 배경 개념

[2.1] 멱등성은 같은 요청을 반복해도 돈 이동 같은 효과가 한 번만 생기는 성질이다. 응답을 받지 못했다는 사실만으로 결제가 실패했다고 판단할 수는 없다. 대행사가 성공한 뒤 응답만 유실될 수 있기 때문이다. 성공 여부를 조회하거나 같은 멱등 키로 결과를 확인해야 중복 결제를 피할 수 있다.

[2.2] Airbnb의 [Orpheus 글](https://medium.com/airbnb-engineering/avoiding-double-payments-in-a-distributed-payments-system-2981f6b070bb)은 요청 사전 기록, 원격 호출, 결과 사후 기록을 구분한다. 사전·사후 기록에서는 네트워크를 호출하지 않고 원격 호출 중에는 데이터베이스를 변경하지 않는다. 이 과제는 세 경계를 각각 요청 명령, `CALL`·`QUERY`, `COMMIT`으로 드러낸다.

[2.3] [Stripe의 멱등성 글](https://stripe.com/blog/idempotency)과 [API v1 계약](https://docs.stripe.com/api/idempotent_requests)은 재시도에 같은 키를 쓰고 요청 매개변수를 유지하는 이유를 설명한다. 이 과제의 키는 실행 내내 만료되지 않으며 아래의 PSP 응답·재시도 규칙을 따른다. 실제 Stripe API 전체를 재현하는 과제가 아니다.

[2.4] 결제의 외부 성공과 로컬 원장의 성공 기록은 서로 다른 사실이다. 원격 호출이 성공해도 사후 기록 전에는 로컬 상태가 미완료일 수 있다. 다시 시도할 수 있는 장애와 최종 거절을 구분해야 한다. 환불 역시 미확정 요청을 한도 계산에서 빼면 다른 요청이 같은 한도를 중복 사용하게 된다.

## 3. 입출력 규칙

[3.1] 앞 단계의 입력·출력 문법과 수치 범위를 따른다. 결제·환불 키는 계정 ID와 같은 문법이며 실행 전체에서 하나의 이름 공간을 공유한다. 계정·사용자 분개 ID와는 별개다. 모든 명령은 한 줄이고 이 단계에서 추가한 명령은 한 줄을 출력한다.

[3.2] 이 단계의 내부 분개 ID는 `@` 뒤에 요청 키를 붙인 값이다. `JOURNAL @<키>`로 조회할 수 있다. 사용자는 `POST`의 분개 ID로 `@`로 시작하는 값을 줄 수 없다. 기존 사용자 분개와 내부 분개는 같은 조회 대상이며 `TRIAL`에도 모두 포함한다.

## 4. 명령 규칙

### 요청 사전 기록

[4.1] `PAY <키> <차변계정> <대변계정> <통화> <금액>`은 결제 요청을 영속 상태 `READY`로 접수한다. 금액은 양수여야 하며 두 계정은 서로 달라야 하고 모두 존재해야 한다. 두 계정의 통화는 요청 통화와 같아야 하고 정책은 모두 `ANY`여야 한다. 정상방향은 제한하지 않는다. 계정 잔액이나 분개는 아직 바꾸지 않는다. 이 과제는 외부 성공 뒤 잔액 제한 때문에 사후 기록이 막히는 문제를 제외하기 위해 결제·환불에 `NONNEG` 계정을 사용하지 않는다.

[4.2] `REFUND <키> <원결제키> <통화> <금액>`은 환불 요청을 `READY`로 접수한다. 원결제는 로컬 상태가 `SUCCEEDED`인 `PAY` 요청이어야 한다. 금액은 양수이고 통화는 원결제와 같아야 한다. 성공 시 원결제의 대변계정에 차변을, 차변계정에 대변을 기록한다. 환불의 환불과 계정 변경은 지원하지 않는다.

[4.3] 원결제별로 `FAILED`가 아닌 모든 환불 요청 금액의 합을 한도 사용액으로 본다. 새 환불을 포함한 한도 사용액은 원결제 금액 이하여야 한다. `READY`, `RETRYABLE`, `UNKNOWN`은 아직 한도를 차지하고 `SUCCEEDED`는 환불된 금액으로 계속 차지한다. `FAILED`로 확정되면 해당 요청의 금액만 한도에서 빠진다. 요청 취소나 키 삭제는 없다.

[4.4] 성공적으로 접수한 `PAY`·`REFUND`는 `STATE <키> <현재상태>`를 출력한다. 이미 있는 키로 요청하면 요청 종류와 그 명령의 모든 매개변수를 먼저 비교한다. 모두 같으면 현재 상태만 반환하며 원격 호출, 한도 추가 점유, 분개 반영을 하지 않는다. 하나라도 다르면 `ERR KEY_CONFLICT`다. 이 비교는 새 요청의 업무 검증보다 먼저 한다. 접수 전에 거절한 요청은 키를 차지하지 않는다.

[4.5] 영속 요청 상태는 `READY`, `RETRYABLE`, `UNKNOWN`, `SUCCEEDED`, `FAILED` 다섯 가지다. 요청 접수 뒤 결제·환불의 상태 전이와 내부 분개 반영은 `COMMIT`에서만 한다. `SUCCEEDED`와 `FAILED`는 최종 상태이며 바뀌지 않는다. `STATUS <키>`는 `STATUS <키> <종류> <상태> <통화> <금액> <분개ID> <원결제키>`를 출력한다. 종류는 `PAY` 또는 `REFUND`다. 금액은 환불도 양수이고 분개 ID는 `SUCCEEDED`일 때만 있으며 나머지 상태에서는 `-`다. `PAY`의 원결제키는 `-`다.

### 원격 호출과 휘발성 관찰

[4.6] `PSP <키> <결과>`는 그 키의 PSP 응답 대기열 끝에 결과 하나를 추가하고 `OK PSP <키>`를 출력한다. 키를 접수하기 전에도 추가할 수 있다. 결과는 `OK`, `DECLINE`, `RETRY`, `TIMEOUT_OK`, `TIMEOUT_NONE` 중 하나다. 이 명령 자체는 외부 돈 이동을 일으키지 않으며 로컬 요청을 만들거나 바꾸지 않는다. 대기열과 PSP 상태는 `CRASH`에도 남는다.

[4.7] `CALL <키>`는 로컬 상태가 `READY` 또는 `RETRYABLE`인 요청만 원격 처리한다. PSP가 이미 그 키의 최종 성공·거절을 기록했다면 각각 `OK`·`DECLINE`을 관찰하고 대기열을 소비하지 않는다. PSP 최종 기록이 없다면 대기열의 맨 앞 결과를 하나 소비한다. 대기열마저 비어 있으면 `ERR NO_SCRIPT`이며 호출 횟수도 늘지 않는다. 처리 가능한 호출이면 PSP 호출 횟수를 1 늘리고 아래 규칙의 관찰 하나를 만든 뒤 `OBSERVED <키> <관찰>`을 출력한다. 요청 매개변수는 접수한 값 그대로이며 새로운 원격 키를 만들지 않는다.

[4.8] 대기열 결과 `OK`는 PSP에 최종 성공을 기록하고 돈 이동 효과를 한 번 만들며 관찰 `OK`를 반환한다. `DECLINE`은 PSP에 최종 거절을 기록하고 효과 없이 관찰 `DECLINE`을 반환한다. `RETRY`는 PSP 최종 기록과 효과 없이 관찰 `RETRY`를 반환한다. `TIMEOUT_OK`는 `OK`와 같은 외부 성공·효과를 만들지만 관찰은 `UNKNOWN`이다. `TIMEOUT_NONE`은 PSP 최종 기록과 효과 없이 관찰 `UNKNOWN`을 반환한다. PSP는 키별로 최종 결과를 유지하므로 같은 키의 돈 이동 효과는 최대 한 번이다. 대기열 결과는 이 과제의 결정적 장애 모형이며 실제 HTTP 상태코드가 아니다.

[4.9] `QUERY <키>`는 로컬 상태가 `READY`, `RETRYABLE`, `UNKNOWN`인 요청의 PSP 상태를 조회한다. 대기열은 소비하지 않으며 PSP 조회 횟수를 1 늘린다. PSP 최종 성공이면 `OK`, 최종 거절이면 `DECLINE`, 최종 기록이 없으면 `ABSENT`를 관찰한다. `OBSERVED <키> <관찰>`을 출력한다. `UNKNOWN` 요청은 바로 `CALL`할 수 없고 반드시 조회 결과를 `COMMIT`한 뒤 다음 행동을 정한다.

[4.10] 관찰은 키별 휘발성 정보이며 아직 영속 요청 상태와 분개에 영향을 주지 않는다. 한 키에 관찰은 하나만 둘 수 있다. `COMMIT`하거나 `CRASH`하기 전에는 같은 키에 다시 `CALL`·`QUERY`할 수 없다. 다른 키의 관찰은 동시에 여러 개 남아 있을 수 있다. 관찰이 있는 키에 같은 내용의 `PAY`·`REFUND`를 다시 보내거나 `STATUS`를 실행하는 것은 허용하며 관찰을 지우지 않는다.

### 결과 사후 기록과 장애

[4.11] `COMMIT <키>`는 해당 키의 관찰을 영속 반영한 뒤 지운다. 관찰 `OK`는 `SUCCEEDED`, `DECLINE`은 `FAILED`, `RETRY`는 `RETRYABLE`, `UNKNOWN`은 `UNKNOWN`, `ABSENT`는 `READY`로 전이한다. 응답은 `STATE <키> <새상태>`다. 관찰이 없으면 최종 상태인 요청도 `ERR NO_OBSERVATION`이다.

[4.12] `OK`를 사후 기록할 때 상태 확정과 내부 분개 반영은 원자적이다. 분개 통화는 요청 통화이고 항목은 두 개다. `PAY`는 접수한 차변계정 `D` 금액, 대변계정 `C` 금액 순서다. `REFUND`는 원결제의 대변계정 `D` 금액, 차변계정 `C` 금액 순서다. 내부 분개 ID는 `@<키>`다. 성공한 요청마다 이 분개가 정확히 하나 생기고, 다른 관찰을 기록할 때는 분개를 만들지 않는다. 같은 최종 성공을 조회하거나 요청을 반복해도 내부 분개를 추가하지 않는다.

[4.13] `CRASH`는 모든 키의 미반영 관찰을 버리고 `OK CRASH`를 출력한다. 계정, 분개, 접수한 요청과 그 상태, 환불 한도 사용액, PSP의 대기열·최종 기록·횟수는 유지한다. 특히 `CALL`이 외부 성공한 뒤 `COMMIT` 전에 장애가 나면 로컬은 이전 상태인 채로 PSP만 성공해 있다. 이때 같은 키의 재호출이나 조회로 결과를 다시 관찰할 수 있다.

[4.14] `REMOTE <키>`는 로컬 요청의 유무와 관계없이 `REMOTE <키> <PSP상태> <호출횟수> <조회횟수> <효과횟수>`를 출력한다. PSP상태는 `NONE`, `SUCCEEDED`, `FAILED` 중 하나다. 처음 본 키는 `NONE 0 0 0`이고 대기열 추가만으로도 이 값은 바뀌지 않는다. `CALL`로 이미 기록된 결과를 돌려받아도 호출횟수는 늘지만 효과횟수는 늘지 않는다. 효과횟수는 최종 성공이면 1, 나머지는 0이다. `REMOTE` 자체는 조회횟수를 늘리지 않는다.

## 5. 오류 규칙

[5.1] 앞 단계 오류 규칙을 유지한다. 이 단계 명령도 오류 하나를 출력하고 아무 상태도 바꾸지 않는다. `CALL`에서 정상적으로 소비한 `RETRY`·`TIMEOUT_NONE` 같은 결과는 명령 오류가 아니므로 응답 소비와 횟수 증가는 유지한다.

[5.2] 기존 키의 `PAY`·`REFUND`는 4.4의 동일 요청 판정을 가장 먼저 한다. 새 `PAY`는 0 이하 금액 `AMOUNT`, 없는 계정 `NOT_FOUND`, 같은 두 계정 `SAME_ACCOUNT`, 계정 통화 불일치 `CURRENCY`, `NONNEG` 계정 사용 `ACCOUNT_POLICY` 순서로 검사한다.

[5.3] 새 `REFUND`는 0 이하 금액 `AMOUNT`, 없는 원결제키 `NOT_FOUND`, 원요청이 `PAY`가 아니거나 로컬 `SUCCEEDED`가 아닌 경우 `PAYMENT_STATE`, 원결제와 통화 불일치 `CURRENCY`, 한도 초과 `REFUND_LIMIT` 순서로 검사한다. 원결제보다 큰 단일 환불도 한도 초과다.

[5.4] `STATUS`, `CALL`, `QUERY`, `COMMIT`은 로컬 요청이 없으면 `NOT_FOUND`다. `CALL`·`QUERY`는 그다음 미반영 관찰 유무 `OBSERVATION_PENDING`, 허용하지 않는 로컬 상태 `STATE` 순서로 검사한다. `CALL`은 이 검사를 통과한 뒤에만 4.7의 `NO_SCRIPT`를 검사한다. `COMMIT`은 요청 존재 다음으로 관찰 유무를 검사한다. `PSP`, `CRASH`, `REMOTE`는 문법이 맞는 입력에서 오류가 없다.

## 6. 예시

[6.1] PSP는 결제에 성공하지만 응답이 유실된다. 로컬 상태는 조회 결과를 사후 기록하기 전까지 성공이 아니다. 같은 키의 다른 금액은 거절하고, 다시 받은 성공 결과로 분개 하나만 만든다.

입력:
```text
OPEN psp KRW D ANY
OPEN merchant KRW C ANY
PAY order1 psp merchant KRW 100
PSP order1 TIMEOUT_OK
CALL order1
STATUS order1
COMMIT order1
PAY order1 psp merchant KRW 101
CALL order1
QUERY order1
COMMIT order1
PAY order1 psp merchant KRW 100
REMOTE order1
JOURNAL @order1
TRIAL KRW
```

출력:
```text
OK OPEN psp
OK OPEN merchant
STATE order1 READY
OK PSP order1
OBSERVED order1 UNKNOWN
STATUS order1 PAY READY KRW 100 - -
STATE order1 UNKNOWN
ERR KEY_CONFLICT
ERR STATE
OBSERVED order1 OK
STATE order1 SUCCEEDED
STATE order1 SUCCEEDED
REMOTE order1 SUCCEEDED 1 1 1
JOURNAL @order1 KRW 2
ENTRY psp D 100
ENTRY merchant C 100
TRIAL KRW 100 100 0
```

[6.2] 성공 관찰을 잃어도 PSP는 같은 키를 다시 청구하지 않는다. 미확정 환불은 한도를 차지하며 최종 거절된 환불은 사용한 한도를 해제한다.

입력:
```text
OPEN psp USD D ANY
OPEN merchant USD C ANY
PAY p psp merchant USD 100
PSP p OK
CALL p
CRASH
CALL p
COMMIT p
REFUND r1 p USD 70
REFUND r2 p USD 40
PSP r1 RETRY
CALL r1
COMMIT r1
REFUND r2 p USD 40
PSP r1 DECLINE
CALL r1
COMMIT r1
REFUND r2 p USD 40
PSP r2 OK
CALL r2
COMMIT r2
STATUS r1
STATUS r2
REMOTE p
BALANCE merchant
TRIAL USD
```

출력:
```text
OK OPEN psp
OK OPEN merchant
STATE p READY
OK PSP p
OBSERVED p OK
OK CRASH
OBSERVED p OK
STATE p SUCCEEDED
STATE r1 READY
ERR REFUND_LIMIT
OK PSP r1
OBSERVED r1 RETRY
STATE r1 RETRYABLE
ERR REFUND_LIMIT
OK PSP r1
OBSERVED r1 DECLINE
STATE r1 FAILED
STATE r2 READY
OK PSP r2
OBSERVED r2 OK
STATE r2 SUCCEEDED
STATUS r1 REFUND FAILED USD 70 - p
STATUS r2 REFUND SUCCEEDED USD 40 @r2 p
REMOTE p SUCCEEDED 2 0 1
BALANCE merchant USD 60
TRIAL USD 60 60 0
```

## 7. 검사 방식

[7.1] 표준 입출력으로 검사하며 프로세스 초기화와 출력 비교 방식은 01단계와 같다. 테스트 하나의 제한 시간은 빌드 시간을 제외하고 30초다. 한 테스트의 최대 규모는 새 명령과 기존 명령을 합쳐 10,000개, 접수 요청 2,000개, 계정 2,000개다. 별도 성능 전용 대규모 테스트는 두지 않는다. 명령 사이의 장애와 재시도 순서를 조합하는 데 난도를 둔다.

[7.2] 같은 키의 매개변수 충돌, 사전 기록 뒤 장애, 원격 성공 뒤 장애, 미확정 결제 조회, 응답 재시도, 환불 한도 선점을 확인한다. `STATUS`·`REMOTE`·`JOURNAL`로 로컬 확정, 외부 효과와 분개 개수를 구별한다. 모든 테스트는 사용한 통화의 `TRIAL` 차이가 0인지 함께 확인한다. 앞 단계 테스트도 누적 실행한다.
