# 주문 상태 변경 기록

## 맥락과 입력

[1] 쇼핑몰 운영 도구는 주문 상태를 바꾸고 외부 이력 저장소에 변경 내역을 남긴다. 주문 상태 저장과 이력 저장은 별개다. 이력 응답을 받지 못해도 이미 바꾼 주문 상태를 되돌리지 않는다. 이 단계는 가짜 저장소로 호출 결과를 재현한다. 실제 네트워크, 대기 시간, 시계는 사용하지 않는다.

[2] 프로그램은 표준 입력에서 명령을 한 줄씩 EOF까지 읽고 명령마다 결과 한 줄을 표준 출력에 쓴다. 빈 줄은 무시한다. 토큰은 하나 이상의 공백으로 구분하고 대소문자를 구별한다. 주문 ID는 영문자·숫자·하이픈으로 된 1~32자 문자열이다. 상태는 `NEW`, `PAID`, `PACKED`, `SHIPPED`, `CANCELLED` 중 하나다. 운영자의 정정도 허용하므로 서로 다른 두 상태 사이의 전환은 모두 가능하다. 상태와 이력은 실행을 끝낼 때까지 유지한다.

## 주문 명령

[3] `OPEN <주문 ID> <상태>`는 주문을 등록하고 `OPENED <주문 ID> <상태>`를 출력한다. 등록 자체는 상태 변경 횟수에 넣지 않고 이력 저장소도 호출하지 않는다. 이미 등록한 ID라면 기존 상태를 유지하고 `EXISTS <주문 ID> <기존 상태>`를 출력한다.

[4] `CHANGE <주문 ID> <상태>`는 주문을 지정한 상태로 바꾼다. 등록되지 않은 주문이면 `NOT_FOUND <주문 ID>`를 출력한다. 기존 상태와 같으면 아무것도 바꾸지 않고 `UNCHANGED <주문 ID> <상태>`를 출력한다. 이 두 경우에는 저장소를 호출하지 않으며 예정된 호출 결과도 소비하지 않는다.

[5] 실제 상태 변경을 주문 상태와 변경 횟수에 먼저 반영하고 이전 상태와 새 상태를 담은 이력 한 건을 전송한다. 같은 변경을 여러 번 전송해도 이력은 한 건만 남아야 한다. 다른 명령에서 발생한 별개의 변경은 주문 ID와 이전·새 상태가 같더라도 각각 기록해야 한다.

[6] 이력 전송은 첫 시도를 포함해 최대 세 번 호출한다. 시간 초과일 때만 즉시 다시 시도한다. 정상 응답을 받으면 `CONFIRMED`, 세 번 모두 시간 초과면 `UNKNOWN`, 거절 응답을 받으면 `REJECTED`로 끝낸다. 정상 응답이나 거절 뒤에는 남은 시도를 하지 않는다. 출력은 `CHANGED <주문 ID> <이전 상태>><새 상태> delivery=<결과> attempts=<실제 호출 횟수>`다. 어느 결과든 주문 상태와 변경 횟수는 유지한다. `UNKNOWN`은 저장 여부를 확정하지 못했다는 뜻이며 이 단계에서는 이후 명령에서 해당 변경을 다시 전송하지 않는다.

[7] `SHOW <주문 ID>`는 `ORDER <주문 ID> <현재 상태> changes=<변경 횟수>`를 출력한다. 등록되지 않은 주문이면 `NOT_FOUND <주문 ID>`를 출력한다.

## 가짜 이력 저장소와 조회

[8] `PLAN <결과> [<결과> ...]`은 저장소 호출 결과를 공용 대기열 끝에 덧붙이고 `PLANNED <대기 중인 결과 수>`를 출력한다. 결과는 `OK`, `BEFORE`, `AFTER`, `DENY` 중 하나다. 각 저장소 호출은 주문에 관계없이 맨 앞 결과 하나를 소비한다. 대기열이 비었으면 `OK`로 처리한다. 조회와 주문 등록은 이 대기열을 소비하지 않는다.

[9] `BEFORE`는 저장하지 않고 시간 초과를 낸다. `AFTER`는 저장을 마친 뒤 시간 초과를 낸다. `OK`는 저장을 마친 뒤 정상 응답을 준다. `DENY`는 저장하지 않고 거절 응답을 준다. 저장소는 요청에 함께 전달된 멱등 키를 기억하고 이미 저장한 키로 온 요청은 새 이력을 남기지 않는다. 이때도 지정한 호출 결과는 그대로 적용한다. 시간 초과만으로는 저장 여부를 판단할 수 없다.

[10] `HISTORY <주문 ID>`는 실제 저장된 이력을 저장 순서대로 `HISTORY <주문 ID> <이전 상태>><새 상태>|...` 형식으로 출력한다. 이력이 없으면 마지막 부분은 `-`다. 등록되지 않은 ID도 이력 조회는 가능하며 같은 형식으로 출력한다. 이력에는 주문 등록이나 변경 없는 명령을 포함하지 않는다.

[11] `METRICS`는 `STORE calls=<전체 호출 수> records=<전체 저장 건수> queued=<대기 중인 결과 수>`를 출력한다. 시간 초과·거절·멱등 키 중복인 호출도 호출 수에 포함하고, 저장 건수는 실제 추가된 이력만 센다.

[12] 알 수 없는 명령, 인자 개수 오류, 잘못된 주문 ID·상태·호출 결과는 `ERROR input`을 출력한다. 오류가 난 명령은 주문, 이력, 호출 횟수, 예정된 호출 결과를 바꾸지 않는다. 이후 줄은 계속 처리한다.

## 버그 신고

[13] 운영 점검 중 두 주문의 상태를 바꾼 뒤 이력을 대조했다. 주문 화면의 변경 횟수와 이력 줄 수가 맞지 않는 주문이 있었다. 같은 점검에서 처리한 다른 주문은 두 수치가 맞았다. 아래는 점검에 사용한 입력이다.

```text
OPEN cart-17 NEW
OPEN cart-28 PAID
PLAN OK BEFORE OK AFTER OK
CHANGE cart-17 PAID
SHOW cart-28
CHANGE cart-28 PACKED
CHANGE cart-17 PACKED
CHANGE cart-28 PACKED
HISTORY cart-17
HISTORY cart-28
METRICS
SHOW cart-17
```

[14] 기대한 결과는 다음과 같다. 이 재현 입력에서는 모든 변경이 `CONFIRMED`이므로 주문별 변경 횟수와 저장된 이력 건수가 같아야 한다.

```text
OPENED cart-17 NEW
OPENED cart-28 PAID
PLANNED 5
CHANGED cart-17 NEW>PAID delivery=CONFIRMED attempts=1
ORDER cart-28 PAID changes=0
CHANGED cart-28 PAID>PACKED delivery=CONFIRMED attempts=2
CHANGED cart-17 PAID>PACKED delivery=CONFIRMED attempts=2
UNCHANGED cart-28 PACKED
HISTORY cart-17 NEW>PAID|PAID>PACKED
HISTORY cart-28 PAID>PACKED
STORE calls=5 records=3 queued=0
ORDER cart-17 PACKED changes=2
```

[15] 실제 결과는 다음과 같다. cart-17의 변경 횟수는 2인데 이력에는 세 항목이 나왔다. cart-28에서는 이런 차이를 보지 못했다.

```text
OPENED cart-17 NEW
OPENED cart-28 PAID
PLANNED 5
CHANGED cart-17 NEW>PAID delivery=CONFIRMED attempts=1
ORDER cart-28 PAID changes=0
CHANGED cart-28 PAID>PACKED delivery=CONFIRMED attempts=2
CHANGED cart-17 PAID>PACKED delivery=CONFIRMED attempts=2
UNCHANGED cart-28 PACKED
HISTORY cart-17 NEW>PAID|PAID>PACKED|PAID>PACKED
HISTORY cart-28 PAID>PACKED
STORE calls=5 records=4 queued=0
ORDER cart-17 PACKED changes=2
```
