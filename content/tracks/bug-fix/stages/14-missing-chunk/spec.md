# 확인 전에 나온 전체 완료 안내

## 상품 카탈로그 분할 배치

[1] 쇼핑 카탈로그 서비스는 상품 스냅숏을 여러 조각으로 나누어 검색용 데이터를 만든다. 실행 전에 예상 조각 목록을 등록하고 각 작업자는 조각 처리가 끝나면 결과를 남긴다. 이 프로그램은 계획과 작업자 결과를 메모리에 보관하고 배치의 전체 완료 여부를 판정한다. 프로세스가 끝나면 모든 상태가 사라진다.

[2] 실행은 배치 ID, 세대 번호, 입력 ID의 조합으로 구분한다. 입력 ID는 상품 스냅숏을 식별한다. 같은 배치라도 세대나 입력이 다르면 별개의 실행이다. 조각 결과는 실행과 조각 ID로 식별하며, 처리한 상품이 0건인 성공도 명시적인 성공 결과로 남긴다.

## 입력과 출력

[3] 표준 입력에서 명령을 한 줄씩 EOF까지 읽고 명령마다 결과 한 줄을 표준 출력에 쓴다. 빈 줄은 무시한다. 토큰은 하나 이상의 공백으로 구분한다. ID는 영문 소문자로 시작하며 영문 소문자, 숫자, 하이픈으로 이루어진 길이 1~32의 문자열이다. 세대 번호는 1~1,000,000,000의 정수다. 건수는 0~1,000,000,000의 정수다. 입력은 아래 형식을 지키며 조각 목록에 같은 ID가 두 번 나오지 않는다. 한 프로세스에 명령은 최대 1,000개, 계획 하나에 조각은 최대 100개다.

[4] `PLAN <배치> <세대> <입력> <조각목록>`은 해당 배치의 현재 계획을 등록하거나 교체한다. 조각목록은 `east,west,north`처럼 쉼표로 잇고 빈 목록은 `-`로 쓴다. 출력은 `PLANNED <배치> <예상조각수>`다. 계획을 교체해도 저장된 조각 결과는 지우지 않는다. 같은 실행을 다시 등록하면 그 실행의 기존 결과를 사용할 수 있다.

[5] `RESULT <배치> <세대> <입력> <조각> <상태> <건수>`는 조각 결과를 저장한다. 상태는 `SUCCESS` 또는 `FAILURE`다. `FAILURE`의 건수는 항상 0이다. 같은 실행의 같은 조각에 결과가 여러 번 들어오면 마지막 명령의 결과만 남긴다. 현재 계획이 없거나 현재 계획에 없는 조각의 결과도 저장할 수 있다. 출력은 `RECORDED <배치> <조각>`이다.

[6] `INSPECT <배치> <조각>`은 현재 계획과 같은 실행에 저장된 해당 조각 결과를 조회한다. 결과가 있으면 `<배치> <조각> <상태> <건수>`, 없으면 `<배치> <조각> MISSING`을 출력한다. 예상 목록에 없는 조각도 조회할 수 있다. 해당 배치의 현재 계획이 없으면 `<배치> UNPLANNED`를 출력한다.

[7] `CHECK <배치>`는 현재 계획의 완료 여부를 조회한다. 현재 계획이 없으면 `<배치> UNPLANNED`를 출력한다. 계획이 있으면 완료 여부에 따라 `<배치> COMPLETE` 또는 `<배치> PENDING`을 출력한다. 조회는 계획이나 결과를 바꾸지 않는다. 이전 조회가 `COMPLETE`였어도 다음 조회는 현재 결과를 기준으로 다시 판정한다.

## 전체 완료 규칙

[8] 현재 계획의 예상 목록에 있는 모든 조각에 대해 현재 계획과 배치 ID·세대 번호·입력 ID가 모두 같은 실행의 `SUCCESS` 결과가 있어야 전체 완료다. 결과가 없거나 `FAILURE`인 예상 조각이 하나라도 있으면 `PENDING`이다. `SUCCESS 0`은 다른 성공과 동일하게 인정한다.

[9] 예상 목록에 없는 조각의 결과는 전체 완료 판정에 영향을 주지 않는다. 그 결과가 성공이어도 예상 조각의 결과를 대신할 수 없으며, 실패여도 예상 조각들의 성공을 무효로 만들지 않는다. 예상 목록이 빈 계획은 처리할 조각이 없으므로 `COMPLETE`다.

[10] 현재 계획과 세대 번호 또는 입력 ID가 다른 결과는 현재 실행의 증거로 사용하지 않는다. 서로 다른 배치의 결과도 섞지 않는다. 같은 실행의 결과를 나중에 덮어쓰면 다음 `CHECK`부터 새 결과로 판단한다.

## 배경 자료

[11] [AWS Step Functions Distributed Map](https://docs.aws.amazon.com/step-functions/latest/dg/state-map-distributed.html)은 입력 항목을 개별 하위 워크플로 실행으로 처리하고 Map Run에서 실행을 추적한다. 이 과제의 명령과 저장 형식은 별도로 정한 모형이다. AWS의 실패 허용 설정을 재현하지 않으며, 이 과제에서는 예상 조각 전체의 성공을 요구한다.

## 버그 신고

[12] 카탈로그 운영 담당자의 신고다. “오전 반영 작업에서 상품 노출 확인이 끝나기 전에 전체 완료 안내가 나왔다. 같은 시간에 점검한 저녁 작업은 정상으로 보인다. 점검 때 수집한 명령을 아래에 붙였다. morning의 첫 완료 조회는 아직 대기 중이어야 하는데 완료로 표시됐다.”

[13] 재현 입력은 다음과 같다. 두 배치를 함께 점검한 기록이며 맨 끝에는 후속 처리 결과도 포함한다.

```text
CHECK clearance
PLAN morning 12 snap-q bags,coats,shoes
RESULT morning 11 snap-q coats SUCCESS 29
RESULT morning 12 snap-q bags SUCCESS 7
PLAN evening 3 snap-r hats
RESULT evening 3 snap-r hats SUCCESS 2
CHECK evening
RESULT morning 12 snap-q shoes SUCCESS 0
INSPECT morning shoes
CHECK morning
RESULT morning 12 snap-q coats SUCCESS 29
CHECK morning
```

[14] 기대한 전체 출력은 다음과 같다.

```text
clearance UNPLANNED
PLANNED morning 3
RECORDED morning coats
RECORDED morning bags
PLANNED evening 1
RECORDED evening hats
evening COMPLETE
RECORDED morning shoes
morning shoes SUCCESS 0
morning PENDING
RECORDED morning coats
morning COMPLETE
```

[15] 실제 전체 출력은 다음과 같다.

```text
clearance UNPLANNED
PLANNED morning 3
RECORDED morning coats
RECORDED morning bags
PLANNED evening 1
RECORDED evening hats
evening COMPLETE
RECORDED morning shoes
morning shoes SUCCESS 0
morning COMPLETE
RECORDED morning coats
morning COMPLETE
```
