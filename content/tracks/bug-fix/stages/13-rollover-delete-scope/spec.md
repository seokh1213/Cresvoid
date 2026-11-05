# 정리 후 비어 버린 상품 상세

## 카탈로그 이력과 보관 계약

[1] 쇼핑 카탈로그는 상품 키마다 필드 변경 이력을 보관한다. 정리 작업은 이번 창에 변경된 상품의 전체 상태를 새 기준 상태로 적재하고 적재를 확인한 상품의 옛 이력을 제거한다. 이번 창에 변경되지 않은 상품은 이전 기준 상태와 이력으로 계속 조회한다. 저장소, 상태 재구성과 적재 결과를 모의하는 실행 환경은 제공한다.

[2] 프로그램은 표준 입력에서 명령을 한 줄씩 EOF까지 읽고 명령마다 결과 한 줄을 표준 출력에 쓴다. 빈 줄은 무시한다. 명령은 공백으로 구분한다. 키·필드·값·작업 ID는 영문자로 시작하는 영문자·숫자·하이픈 문자열이다. 시각은 0 이상 1,000,000 이하 정수다. 명령은 최대 3,000개다. 실제 시계나 외부 저장소를 사용하지 않는다.

[3] `PUT <키> <시각> <필드> <값>`은 필드 변경을, `DEL <키> <시각> <필드>`는 필드 삭제를 등록하고 `OK`를 출력한다. 없는 필드를 삭제해도 이력은 남는다. 변경에는 프로그램 전체에서 1부터 증가하는 입력 순번을 부여한다. 변경 시각은 입력 순서와 달라도 된다. 같은 시각의 변경은 입력 순번 순으로 반영한다. 이미 기준 상태가 있는 키에는 그 기준 시각보다 이른 변경을 등록하지 않는다.

[4] `GET <키> <시각>`은 해당 시각까지의 상태를 출력한다. 기준 상태가 있으면 그 상태에서 시작하고 이후 변경을 시각·입력 순번 순으로 반영한다. 필드는 이름의 사전순으로 `필드=값`을 쉼표로 연결하며 필드가 없으면 `EMPTY`를 출력한다. 키의 기준 시각보다 이른 조회에는 `EXPIRED`를 출력한다. 기준 상태도 이력도 없는 키는 어느 시각에 조회해도 `EMPTY`다.

[5] `HISTORY <키>`는 저장된 기준 상태와 변경 이력을 출력한다. 기준 상태는 `B@시각{상태}`, 값 변경은 `P@시각#순번{필드=값}`, 삭제는 `D@시각#순번{필드}`로 표시한다. 상태 표기는 GET과 같고 빈 기준 상태는 `B@시각{EMPTY}`다. 기준 상태를 먼저 놓고 변경을 시각·입력 순번 순으로 나열하며 세미콜론으로 연결한다. 저장된 항목이 없으면 `NONE`을 출력한다.

## 정리 작업

[6] `PLAN <작업ID> <창시작> <기준시각>`은 창시작 이상, 기준시각 미만에 변경 이력이 있는 키들을 대상으로 계획을 만들고 `PLANNED <키수>`를 출력한다. 각 대상 키의 기준시각 미만 전체 상태를 계산한다. 창시작 이전에만 변경된 필드도 포함하고 삭제된 필드는 제외한다. 계획은 저장소의 입력 버전과 계산한 상태를 고정한다. 계획 자체는 저장소를 바꾸지 않는다. 작업 ID는 새 ID이며 창시작은 기준시각보다 작고 저장된 모든 기준 상태의 시각 이상이다.

[7] `LOAD <작업ID> <키> <결과>`는 계획에 속한 키의 전체 기준 상태를 적재하는 모의 명령이다. 결과는 `OK` 또는 `FAIL`이다. `OK`이면 `LOADED`, `FAIL`이면 `FAILED`를 출력한다. 같은 키를 다시 적재하면 마지막 결과를 사용한다. FAIL이거나 LOAD하지 않은 키는 적재가 확인되지 않은 상태다. 이 명령은 아직 원래 저장소를 바꾸지 않는다. 적용 전 계획에만 LOAD할 수 있다.

[8] `APPLY <작업ID>`는 적재가 확인된 키의 기준 상태를 설치하고 그 키의 기준시각 미만 이력만 제거한다. 새 기준 상태에는 기준시각 미만의 모든 변경을 반영하며 기준시각 이상의 변경은 원래 시각·순번과 함께 남긴다. 적재가 확인되지 않은 키의 기준 상태와 이력은 그대로 보존한다. 빈 상태도 적재 확인 대상이다. 성공하면 `APPLIED <확인된키수>`를 출력하고 작업을 적용 완료로 표시한다. 확인된 키가 없어도 성공한다.

[9] PUT 또는 DEL마다 저장소의 입력 버전이 증가한다. 계획 후 다른 정리 작업이 처음 적용되어도 버전이 증가한다. APPLY 시 계획의 입력 버전과 현재 버전이 다르면 `STALE`을 출력하고 기준 상태·이력·작업의 적용 여부를 바꾸지 않는다. 이미 적용한 작업의 APPLY는 버전을 검사하기 전에 `ALREADY_APPLIED`를 출력하고 아무것도 바꾸지 않는다. 따라서 재실행은 이후에 등록된 변경도 보존한다.

[10] 입력은 위 계약을 만족한다. 존재하지 않는 작업 ID, 계획에 없는 키의 LOAD, 잘못된 토큰 수나 범위는 주어지지 않는다. 검사는 조회 상태뿐 아니라 보존해야 할 기준 상태와 이력도 확인한다. 삭제 호출 횟수나 내부 자료구조는 검사하지 않는다.

[11] 배경 자료는 [Apache Kafka의 Log Compaction](https://kafka.apache.org/42/design/design/#log-compaction)이다. 키의 최종 상태를 보존한다는 원리를 참고했다. 이 단계의 창, 필드별 상태 계산, 적재 확인과 삭제 범위는 카탈로그의 별도 보관 계약이며 Kafka의 API를 구현하는 과제가 아니다.

## 버그 신고

[12] 카탈로그 점검 중 상품 상세가 비어 있는 항목을 발견했다. 오전 정리와 저녁 정리는 모두 성공으로 끝났다. 저녁에는 적재 결과를 정정한 상품과 적재하지 않은 상품이 있었고, 기준시각에 가격이 바뀐 상품도 있었다. 아래는 두 작업과 점검 조회를 모은 재현 로그다. 정리 후에도 조회 가능한 상품의 상세와 보관 대상 이력이 남아 있을 것으로 기대했다.

재현 입력:

```text
PUT boots 2 title Boots
PUT boots 6 color Black
PUT mug 3 title Mug
PUT poster 4 title Poster
PLAN morning 0 10
LOAD morning boots OK
LOAD morning mug OK
LOAD morning poster OK
APPLY morning
PUT boots 12 price Cheap
PUT mug 14 color Blue
PUT poster 16 size Wide
PUT boots 20 price High
GET boots 18
PLAN evening 10 20
LOAD evening boots OK
LOAD evening mug OK
LOAD evening mug FAIL
APPLY evening
GET boots 19
GET boots 25
GET mug 25
GET poster 25
HISTORY boots
HISTORY mug
HISTORY poster
APPLY evening
GET boots 25
```

기대한 출력:

```text
OK
OK
OK
OK
PLANNED 3
LOADED
LOADED
LOADED
APPLIED 3
OK
OK
OK
OK
color=Black,price=Cheap,title=Boots
PLANNED 3
LOADED
LOADED
FAILED
APPLIED 1
EXPIRED
color=Black,price=High,title=Boots
color=Blue,title=Mug
size=Wide,title=Poster
B@20{color=Black,price=Cheap,title=Boots};P@20#8{price=High}
B@10{title=Mug};P@14#6{color=Blue}
B@10{title=Poster};P@16#7{size=Wide}
ALREADY_APPLIED
color=Black,price=High,title=Boots
```

실제 출력:

```text
OK
OK
OK
OK
PLANNED 3
LOADED
LOADED
LOADED
APPLIED 3
OK
OK
OK
OK
color=Black,price=Cheap,title=Boots
PLANNED 3
LOADED
LOADED
FAILED
APPLIED 1
EXPIRED
color=Black,price=High,title=Boots
EMPTY
EMPTY
B@20{color=Black,price=Cheap,title=Boots};P@20#8{price=High}
NONE
NONE
ALREADY_APPLIED
color=Black,price=High,title=Boots
```
