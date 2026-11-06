# 잠금 방식별 동시 요청

## 1. 개요

[1.1] 예약 요청의 읽기와 확정을 입력이 지정한 순서로 섞는 실행 모형을 만든다. 같은 재고를 읽은 두 요청이 나중에 확정될 때 비관적 잠금, 버전 기반 낙관적 잠금, 재고 제약 조건 방식의 결과와 재시도 수를 비교한다. 목표 풀이 시간은 숙련 개발자 기준 1~3시간이다.

[1.2] 1·2단계 명령과 출력은 배치 밖에서 그대로 유지한다. 새 배치 안에서는 BOOK에 해당하는 예약만 실행한다. 실제 스레드나 DB를 쓰지 않고 입력의 STEP을 가상 실행 차례로 사용한다. 운영 DB의 격리 수준·스케줄러 전체를 재현하지 않으며 아래 모형의 관찰 가능한 상태와 출력을 구현한다. 배치 밖의 취소·재고 변경·멱등 요청과 배치의 성공 예약은 같은 호텔 상태를 사용한다.

## 2. 배경 개념

[2.1] 읽기와 쓰기가 떨어져 있으면, 두 요청이 모두 잔여 재고 1을 읽고 각각 1개를 예약할 수 있다고 판단할 수 있다. 두 판단을 그대로 확정하면 판매 한도를 넘긴다. 예약 날짜가 여러 개면 한 날짜의 충돌 때문에 다른 날짜까지 함께 되돌릴 수 있어야 한다.

[2.2] 비관적 잠금은 충돌할 수 있는 자원을 사용하는 동안 다른 요청이 그 자원에 진입하지 못하게 한다. 충돌한 요청은 기다린다. 여러 날짜 중 일부만 잠근 채 나머지를 기다리면 서로 상대의 잠금을 기다릴 수 있다. 이 과제에서는 필요한 날짜 전부의 잠금을 한꺼번에 얻거나 하나도 얻지 않는 규칙으로 그 상황을 제외한다. 행 잠금의 개념은 [PostgreSQL의 명시적 잠금 문서](https://www.postgresql.org/docs/18/explicit-locking.html)에서 확인할 수 있다.

[2.3] 낙관적 잠금은 먼저 읽고 나중에 그 읽기가 여전히 유효한지 검사한다. 버전은 값이 바뀔 때 증가하는 표식이다. 읽었던 날짜 중 하나라도 버전이 달라지면 읽은 상태를 버리고 다시 시도한다. 현재 여유가 충분하더라도 버전 검사가 실패할 수 있다. 이 과제는 날짜별 버전을 비교하며 다른 날짜나 다른 유형의 변경은 충돌이 아니다.

[2.4] 제약 조건 방식은 최종 변경 결과가 허용 범위 안에 드는지를 확정 시점에 검사한다. 이 과제의 제약은 모든 날짜에서 예약 수량이 0 이상이고 판매 한도 이하라는 것이다. 현재 값에 예약 수량을 더한 결과를 검사하므로 과거에 읽은 버전이 달라도 한도가 남으면 확정할 수 있다. 제약 위반이 난 시도는 전체를 되돌린다. 검사 제약의 일반적인 의미는 [PostgreSQL의 제약 조건 문서](https://www.postgresql.org/docs/18/ddl-constraints.html)에서 확인할 수 있다. 아래의 다박 원자성과 재시도 규칙은 이 과제의 계약이다.

[2.5] 대기는 아직 새 상태를 읽지 못한 것이고 재시도는 이미 읽은 시도가 폐기된 것이다. 둘을 같은 횟수로 세면 방식별 차이가 사라진다. 이 과제의 대기 수는 막힌 STEP 호출 횟수이며 실제 시간이나 잠금의 성능을 나타내지 않는다.

## 3. 입출력 규칙

[3.1] 새 명령은 `BEGIN <mode>`, `TX <txId> <reservationId> <type> <from> <to> <quantity>`, `STEP <txId>`, END이다. mode는 정확히 PESSIMISTIC, OPTIMISTIC, CONSTRAINT 중 하나다. txId는 1단계의 식별자 문법을 따른다. 나머지 식별자·날짜·정수·기본 범위와 공백 규칙도 1단계와 같다.

[3.2] BEGIN과 TX는 성공하면 각각 `OK BEGIN <mode>`와 `OK TX <txId>` 한 줄을 출력한다. STEP은 `TX <txId> <state> <attempts> <retries> <waits>` 한 줄을 출력한다. state는 READY, PREPARED, BOOKED, FULL 중 하나다. 각 카운터는 처음에 0이며 STEP 응답에는 해당 호출을 처리한 뒤의 상태와 누적 값을 넣는다.

[3.3] 성공한 END는 TX 등록 순서대로 `RESULT <txId> <state> <attempts> <retries> <waits>`를 한 줄씩 출력한 뒤 `SUMMARY <booked> <full> <attempts> <retries> <waits>`를 출력한다. RESULT의 state는 BOOKED 또는 FULL이다. SUMMARY의 booked와 full은 각각 해당 최종 상태인 요청 수이고 나머지 세 값은 모든 요청의 카운터 합이다. 등록된 요청이 없으면 SUMMARY 0 0 0 0 0만 출력한다.

## 4. 명령 규칙

### 4.1 배치와 요청 등록

[4.1.1] BEGIN은 지정한 방식의 배치를 연다. 동시에 열린 배치는 최대 하나다. 배치는 현재의 객실 유형과 재고, 성공한 예약, 요청 ID 기록을 그대로 사용한다. BEGIN 자체는 재고나 예약을 바꾸지 않는다. 요청별 상태·카운터·잠금과 날짜별 버전은 배치마다 새로 시작하며 모든 날짜의 초기 버전은 0이다.

[4.1.2] 배치 안에서는 TX, STEP, END만 사용할 수 있다. TYPE, STOCK, AVAIL, BOOK, CANCEL, STATUS, REQUEST와 또 다른 BEGIN은 ERROR BATCH_ACTIVE이다. 배치 밖에서 TX, STEP, END를 사용하면 ERROR NO_BATCH이다. 오류 시 배치와 호텔 상태를 바꾸지 않는다. 배치 밖 명령은 기존 응답을 그대로 유지한다.

[4.1.3] TX는 예약할 내용을 등록하고 READY 상태로 만든다. 등록할 때 재고를 읽거나 잠그지 않는다. 등록 즉시 호텔의 예약 수량이 늘어나지도 않는다. 유형이 존재하는지, txId가 이번 배치에서 쓰였는지, reservationId가 이미 성공한 예약에 쓰였거나 이번 배치의 다른 TX가 등록한 이름인지 차례로 검사한다. 오류는 각각 ERROR TYPE_NOT_FOUND, ERROR TX_EXISTS, ERROR RESERVATION_EXISTS이다.

[4.1.4] 배치에서 등록한 txId와 reservationId는 그 요청이 FULL로 끝나도 END까지 다시 등록할 수 없다. 배치가 끝나면 txId는 다시 쓸 수 있다. FULL인 요청의 reservationId는 호텔에 성공 이력을 만들지 않으므로 END 뒤에는 다시 사용할 수 있다. BOOKED인 예약 ID는 1단계와 같이 계속 보존한다. TX는 요청 ID를 받지 않으며 2단계의 요청 기록을 읽거나 쓰지 않는다.

[4.1.5] TX는 다른 요청의 STEP 사이에도 추가할 수 있다. STEP은 지정된 요청만 진행시키며 다른 요청을 자동으로 실행하지 않는다. 잠금이 풀려도 기다리던 요청을 자동으로 진행시키지 않는다. BOOKED 또는 FULL 요청에 다시 STEP을 주면 상태와 모든 카운터를 유지한 채 같은 STEP 응답을 출력한다.

### 4.2 공통 시도와 확정

[4.2.1] 한 시도는 읽기 차례와 확정 차례로 이루어진다. READY인 요청이 읽기에 성공하면 attempts를 1 늘리고 자기 구간의 모든 날짜에 대한 현재 예약 수량·한도·버전을 한 번에 읽어 보관하고 PREPARED가 된다. 이 차례에는 예약을 확정하지 않는다. 잠금으로 막힌 경우를 제외하면 재고가 부족해도 PREPARED가 된다.

[4.2.2] 읽기에서 보관한 모든 날짜에 대해 보관한 예약 수량 + quantity <= 보관한 한도이면 그 시도는 수용 가능하다고 판단한다. 하나라도 만족하지 않으면 수용 불가능하다고 판단한다. 한 날짜의 결과로만 다른 날짜의 수용 여부를 추정하지 않는다.

[4.2.3] PREPARED 요청의 다음 STEP은 아래 방식별 확정 규칙을 적용한다. BOOKED로 끝나면 모든 대상 날짜의 현재 예약 수량에 quantity를 더하고 해당 날짜의 버전을 각각 1 늘린다. 동시에 1단계 BOOK 성공과 같은 예약 이력을 만들어 이후 STATUS와 CANCEL이 사용할 수 있게 한다. FULL로 끝나면 호텔 상태와 모든 버전을 유지한다. 재시도 시에도 호텔 상태와 모든 버전을 유지한다.

[4.2.4] 재시도는 retries를 1 늘리고 보관한 읽기 결과를 폐기하여 READY로 돌아가는 것이다. 같은 STEP에서 다시 읽지 않는다. 다음에 그 요청에 STEP을 주어야 새 시도가 시작된다. 시도 횟수나 재시도 횟수에 별도 상한은 없다. 입력에 주어진 STEP 수만큼만 실행한다.

### 4.3 비관적 잠금

[4.3.1] PESSIMISTIC에서 READY 요청은 자신의 객실 유형과 구간에 속하는 모든 날짜의 배타 잠금을 요구한다. 다른 요청이 대상 날짜 하나라도 잠그고 있으면 어떤 잠금도 얻지 않고 읽지도 않는다. 이때 waits만 1 늘리고 READY를 유지한다. attempts와 retries는 늘리지 않는다. 날짜가 겹쳐도 유형이 다르면 잠금 충돌이 아니다.

[4.3.2] 대상 날짜가 모두 잠겨 있지 않으면 전부 잠근 뒤 [4.2.1]의 읽기를 수행한다. PREPARED 요청의 확정 차례에는 보관한 수용 가능 여부가 참이면 BOOKED, 거짓이면 FULL로 끝나고 모든 잠금을 놓는다. 수용 불가능한 읽기도 확정 차례 전까지 잠금을 유지한다. 이 방식에서 retries는 항상 0이다.

### 4.4 낙관적 잠금

[4.4.1] OPTIMISTIC의 읽기는 잠금을 사용하지 않는다. 확정 차례에는 먼저 대상 날짜 전부의 현재 버전과 보관한 버전을 비교한다. 하나라도 다르면 수용 가능 여부를 확인하기 전에 재시도한다. 읽을 때 이미 수용 불가능하다고 판단했어도 버전이 달라졌으면 바로 FULL로 끝내지 않고 재시도한다.

[4.4.2] 모든 버전이 같으면 보관한 수용 가능 여부에 따라 BOOKED 또는 FULL로 끝난다. BOOKED일 때 현재 값에 quantity를 더하며 보관한 예약 수량으로 현재 값을 덮어쓰지 않는다. 이 방식에서 waits는 항상 0이다.

### 4.5 제약 조건

[4.5.1] CONSTRAINT의 읽기도 잠금을 사용하지 않는다. 확정 차례에 보관한 수용 가능 여부가 거짓이면 FULL로 끝난다. 참이면 모든 대상 날짜에 대해 현재 예약 수량 + quantity <= 현재 한도인지 검사한다. 모두 만족하면 BOOKED로 끝난다. 이때 버전이 달라져도 재시도하지 않는다.

[4.5.2] 보관한 수용 가능 여부는 참이지만 현재 제약을 만족하지 못하는 날짜가 있으면 전체 시도를 되돌리고 재시도한다. 앞 날짜가 제약을 만족하더라도 그 날짜의 수량이나 버전을 바꾸지 않는다. 제약 위반은 곧바로 FULL이라는 결과를 만들지 않는다. 이후 읽기에서 부족함을 확인하고 확정할 때 FULL이 될 수 있다. 이 방식에서 waits는 항상 0이다.

### 4.6 종료

[4.6.1] END는 등록된 요청 전부가 BOOKED 또는 FULL일 때만 성공한다. READY나 PREPARED가 하나라도 있으면 ERROR UNFINISHED 한 줄을 출력하고 배치를 열어 둔다. END가 대신 요청을 진행시키지 않는다. 이 오류 전에 확정한 예약도 그대로 유지한다. 오류 뒤에 STEP을 더 주고 다시 END할 수 있다.

[4.6.2] 성공한 END는 [3.3]의 결과를 출력하고 배치의 요청별 상태·잠금·버전·이름 사용 기록만 없앤다. 호텔의 성공 예약과 재고, 2단계 요청 ID 기록은 유지한다. 모든 요청이 끝난 배치의 총 attempts는 등록 요청 수 + 총 retries와 같다. 배치를 닫은 뒤 앞 단계의 조회·취소를 실행하거나 새 배치를 열 수 있다.

## 5. 오류 규칙

[5.1] 새 명령과 앞 단계 명령 모두 형식 검사, 기본 범위 검사, 배치 안팎 검사, 명령별 상태 검사 순서로 판정한다. 잘못된 mode는 ERROR INPUT이다. 배치 안의 BOOK이라도 수량 0이면 ERROR RANGE이며 기본 검사를 통과한 BOOK은 ERROR BATCH_ACTIVE이다. 배치 안에서 거절된 REQUEST는 요청 기록을 만들거나 비교하지 않는다.

[5.2] 열린 배치에서 STEP의 txId가 없으면 ERROR TX_NOT_FOUND이다. TX의 상태 검사 순서는 [4.1.3]을 따른다. 실패한 등록은 txId와 reservationId를 차지하지 않는다. READY의 대기와 확정 시 재시도는 오류 응답이 아니라 정상 STEP 응답이다.

[5.3] 입력은 배치를 연 상태로 끝나지 않는다. 오류 응답 뒤에도 끝내 모든 요청을 마치고 END하는 입력만 검사한다. 프로세스 간의 요청 복구나 미완료 배치의 자동 종료는 구현 대상이 아니다.

## 6. 예시

[6.1] 다음 비관적 잠금 예시에서 b의 첫 STEP은 a가 보유한 날짜의 잠금 때문에 읽지 못한다. 기다리는 동안 시도 수는 늘지 않는다. b를 다시 진행시키려면 a의 확정 뒤에 STEP b를 명시해야 한다.

```text
입력
TYPE Twin 0
STOCK Twin 2032-01-01 2032-01-02 2
BEGIN PESSIMISTIC
TX a ra Twin 2032-01-01 2032-01-02 1
TX b rb Twin 2032-01-01 2032-01-02 1
STEP a
STEP b
STEP a
STEP b
STEP b
END
AVAIL Twin 2032-01-01
출력
OK TYPE Twin
OK STOCK Twin
OK BEGIN PESSIMISTIC
OK TX a
OK TX b
TX a PREPARED 1 0 0
TX b READY 0 0 1
TX a BOOKED 1 0 0
TX b PREPARED 1 0 1
TX b BOOKED 1 0 1
RESULT a BOOKED 1 0 0
RESULT b BOOKED 1 0 1
SUMMARY 2 0 2 0 1
STOCK Twin 2032-01-01 2 2 2 0
```

[6.2] 같은 재고 2개를 사용하는 낙관적 잠금 예시다. b가 읽은 뒤 a가 예약을 확정하면 날짜 버전이 달라진다. 여유가 1 남아도 b는 읽기를 폐기하고 다시 시도한다.

```text
입력
TYPE Twin 0
STOCK Twin 2032-01-01 2032-01-02 2
BEGIN OPTIMISTIC
TX a ra Twin 2032-01-01 2032-01-02 1
TX b rb Twin 2032-01-01 2032-01-02 1
STEP a
STEP b
STEP a
STEP b
STEP b
STEP b
END
출력
OK TYPE Twin
OK STOCK Twin
OK BEGIN OPTIMISTIC
OK TX a
OK TX b
TX a PREPARED 1 0 0
TX b PREPARED 1 0 0
TX a BOOKED 1 0 0
TX b READY 1 1 0
TX b PREPARED 2 1 0
TX b BOOKED 2 1 0
RESULT a BOOKED 1 0 0
RESULT b BOOKED 2 1 0
SUMMARY 2 0 3 1 0
```

[6.3] 같은 재고에서 제약 조건 방식은 a의 확정으로 버전이 바뀌어도 b를 바로 확정한다. 두 예약이 현재 한도 2 안에 함께 들어가기 때문이다.

```text
입력
TYPE Twin 0
STOCK Twin 2032-01-01 2032-01-02 2
BEGIN CONSTRAINT
TX a ra Twin 2032-01-01 2032-01-02 1
TX b rb Twin 2032-01-01 2032-01-02 1
STEP a
STEP b
STEP a
STEP b
END
출력
OK TYPE Twin
OK STOCK Twin
OK BEGIN CONSTRAINT
OK TX a
OK TX b
TX a PREPARED 1 0 0
TX b PREPARED 1 0 0
TX a BOOKED 1 0 0
TX b BOOKED 1 0 0
RESULT a BOOKED 1 0 0
RESULT b BOOKED 1 0 0
SUMMARY 2 0 2 0 0
```

[6.4] 다음 예시에서 b는 두 날짜를 읽고 그 뒤 a가 두 번째 날짜의 예약을 먼저 확정한다. 제약 위반으로 재시도한 b는 다음 읽기에서 부족함을 확인하고 FULL로 끝난다. 실패한 시도가 첫 날짜의 재고를 차지하지 않는다.

```text
입력
TYPE Twin 0
STOCK Twin 2033-01-01 2033-01-03 2
STOCK Twin 2033-01-02 2033-01-03 1
BEGIN CONSTRAINT
TX a ra Twin 2033-01-02 2033-01-03 1
TX b rb Twin 2033-01-01 2033-01-03 1
STEP b
STEP a
STEP a
STEP b
END
STEP b
STEP b
END
AVAIL Twin 2033-01-01
STATUS ra
STATUS rb
출력
OK TYPE Twin
OK STOCK Twin
OK STOCK Twin
OK BEGIN CONSTRAINT
OK TX a
OK TX b
TX b PREPARED 1 0 0
TX a PREPARED 1 0 0
TX a BOOKED 1 0 0
TX b READY 1 1 0
ERROR UNFINISHED
TX b PREPARED 2 1 0
TX b FULL 2 1 0
RESULT a BOOKED 1 0 0
RESULT b FULL 2 1 0
SUMMARY 1 1 3 1 0
STOCK Twin 2033-01-01 2 2 0 2
STATUS ra Twin 2033-01-02 2033-01-03 1
ERROR RESERVATION_NOT_FOUND
```

## 7. 검사 방식

[7.1] 1·2단계의 테스트를 누적 실행한다. 새 입력도 표준 입력과 EOF로 주고 종료 코드 0과 출력 줄을 비교한다. 비교 방식과 제한 시간은 앞 단계와 같으며 컴파일을 제외하고 프로세스 한 번당 20초다. 매 테스트는 빈 호텔 상태로 시작한다. 같은 실행 순서를 주면 항상 같은 출력이어야 한다.

[7.2] 한 입력의 명령 수는 앞 단계와 같이 최대 3,000줄이고 유형은 최대 100개다. 한 배치에는 TX를 최대 200개 등록한다. 구간을 받는 명령의 날짜 차이 합은 TX 등록과 REQUEST의 감싼 명령까지 포함해 최대 100,000일이다. STEP은 새 구간을 받지 않으므로 이 합에 더하지 않는다. 별도의 성능 강제용 대규모 테스트는 두지 않는다. 가장 큰 기능 검사도 이 범위 안에서 여러 날짜와 유형의 요청을 섞는다.

[7.3] 날짜 일부만 겹치는 요청, 다른 유형의 같은 날짜, 수용 가능한 현재 재고와 오래된 버전의 차이, 수용 불가능한 읽기의 버전 충돌, 전체 롤백, 반복 대기, 종료한 요청에 대한 STEP, 배치 종료 전후의 예약·취소·멱등 재전송을 검사한다. 검사는 이 스펙의 결정적인 실행 모형과 카운터를 확인하며 실제 스레드의 처리 시간이나 DB 잠금 API 사용 여부는 확인하지 않는다.
