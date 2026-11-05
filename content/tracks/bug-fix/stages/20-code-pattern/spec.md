# 상품 코드 검증

[1] 쇼핑 카탈로그는 판매자가 입력한 상품 코드를 상품 ID와 함께 등록한다. 정산 작업은 코드에서 추출한 분류와 일련번호를 사용한다. 이 단계는 상품 코드 등록, 조회, 분류별 목록, 삭제와 판매자의 일괄 업로드 검수를 처리한다. 실행할 때마다 카탈로그는 빈 상태에서 시작한다.

[2] 표준 입력에서 명령을 한 줄씩 EOF까지 읽고 명령마다 결과 한 줄을 표준 출력에 쓴다. 명령과 인자는 탭 문자 하나로 구분한다. 명령 표기의 `\t`는 탭을 뜻하며 아래 입력 블록의 구분자는 실제 탭이다. 인자 안에는 탭이나 줄바꿈이 없으며 상품 코드에는 공백이나 빈 문자열이 들어올 수 있다. 입력값의 공백을 지우거나 대소문자를 바꾸지 않는다.

[3] 상품 코드는 ASCII 대문자 세 자, 하이픈 하나, ASCII 숫자 네 자로만 이루어져야 한다. 예를 들어 `TOP-0042`는 유효하다. 이 형식 이외의 문자가 하나라도 있거나 글자 수가 다르면 등록을 거절한다. 분류는 앞의 대문자 세 자이며 허용 분류 목록은 따로 없다. 일련번호는 뒤의 숫자 네 자를 십진수 정수로 해석한 값이다. `0000`부터 `9999`까지 허용하며 저장하는 코드 원문에는 앞자리 0을 그대로 남긴다.

[4] `REGISTER\t상품ID\t코드`는 상품을 등록하거나 같은 ID의 기존 코드를 교체한다. 상품 ID는 영문 소문자로 시작하고 이후 영문 소문자, ASCII 숫자, 하이픈을 쓰는 1~32자 문자열이다. 입력되는 상품 ID는 이 조건을 만족한다. 코드 형식이 유효하면 다른 상품이 같은 코드 원문을 사용하는지 검사한다. 형식 오류는 `REJECTED 상품ID INVALID_CODE`, 다른 상품의 코드와 중복되면 `REJECTED 상품ID DUPLICATE_CODE`를 출력한다. 형식 오류를 중복보다 먼저 판정한다.

[5] 등록이나 교체에 성공하면 `OK 상품ID 분류 일련번호`를 출력한다. 같은 ID에 같은 코드를 다시 등록해도 성공이다. 거절한 명령은 기존 상품과 코드 점유 상태를 전혀 바꾸지 않는다. 교체에 성공하면 이전 코드는 다른 상품이 사용할 수 있다. 일련번호를 출력할 때에는 앞자리 0을 붙이지 않는다.

[6] `GET\t상품ID`는 등록된 상품을 `PRODUCT 상품ID 코드원문 분류 일련번호`로 출력한다. 해당 상품이 없으면 `MISSING 상품ID`를 출력한다.

[7] `LIST\t분류`는 해당 분류의 상품을 상품 ID의 사전순으로 나열한다. 각 상품을 `상품ID:코드원문`으로 표현하고 쉼표로 연결해 한 줄로 출력한다. 상품이 없으면 `(none)`을 출력한다. 분류 인자는 ASCII 대문자 세 자로 주어진다.

[8] `REMOVE\t상품ID`는 상품을 지우고 `REMOVED 상품ID`를 출력한다. 해당 상품이 없으면 `MISSING 상품ID`를 출력한다. 삭제된 상품의 코드는 다른 상품이 사용할 수 있다.

[9] 명령은 대소문자를 구분한다. 빈 줄, 알 수 없는 명령, 인자 수가 맞지 않는 명령에는 `ERROR COMMAND`를 출력하고 상태를 유지한다. 탭으로 구분한 마지막 인자가 비어 있어도 인자 하나로 센다. 상품 ID, 묶음 ID와 분류 인자는 정해진 조건을 만족하므로 별도의 내용 오류는 다루지 않는다.

## 일괄 업로드

[10] `STAGE\t묶음ID\t상품ID\t코드`는 등록 후보를 묶음에 보관하고 `STAGED 묶음ID 상품ID`를 출력한다. 묶음 ID는 상품 ID와 같은 형식이다. 이 명령은 코드 형식을 검사하지 않으며 카탈로그도 바꾸지 않는다. 같은 묶음에서 같은 상품을 다시 제출하면 마지막 코드만 남기되 상품을 처음 제출한 순서는 유지한다. 묶음끼리는 후보를 공유하지 않는다.

[11] `CHECK\t묶음ID`는 현재 카탈로그의 사본에서 상품을 처음 제출한 순서대로 후보를 등록해 보고 `REVIEW 묶음ID 성공수 거절수`를 출력한다. 각 후보에는 [3]~[5]의 등록 규칙을 적용한다. 사본에서 앞 후보의 등록이 성공하면 뒤 후보는 그 변경을 기준으로 중복을 판정한다. 거절된 후보는 사본을 바꾸지 않는다. 성공수와 거절수는 마지막 코드만 남긴 상품별로 센다. 검수는 실제 카탈로그와 보관 중인 후보를 바꾸지 않는다. 묶음이 없으면 `MISSING_BATCH 묶음ID`를 출력한다.

[12] `APPLY\t묶음ID`는 검수 때와 같은 순서와 규칙으로 실제 카탈로그에 후보를 등록하고 `APPLIED 묶음ID 성공수 거절수`를 출력한다. 앞서 실행한 검수 결과를 저장해 사용하지 않고 반영 시점의 카탈로그를 기준으로 다시 판정한다. 성공한 후보만 반영하며 거절한 후보 때문에 다른 후보의 성공을 취소하지 않는다. 처리한 묶음은 거절한 후보까지 모두 지운다. 묶음이 없으면 `MISSING_BATCH 묶음ID`를 출력한다.

[12.1] `SCAN\t문의내용`은 판매자가 보낸 문의에서 코드 정보를 찾는다. 문의내용의 왼쪽부터 ASCII 대문자 세 자, 하이픈 하나, ASCII 숫자 네 자가 연속된 부분을 처음 찾으면 `FOUND 분류 일련번호`를 출력한다. 그 부분의 앞뒤에 다른 문자가 있어도 찾는다. 일련번호는 앞자리 0 없이 출력한다. 해당 부분이 없으면 `NOT_FOUND`를 출력한다. 빈 문의내용도 허용하며 카탈로그와 업로드 후보는 바꾸지 않는다.

## 버그 신고

[13] 다음은 이 훈련을 위해 구성한 판매자 업로드 신고다. “처음 검수한 뒤 몇 행을 다시 제출했다. 그사이 다른 상품도 등록했다. 검수와 반영의 집계가 예상과 달랐고, 반영 후 TOP 목록에는 원래 상품 대신 새 상품이 있었다. 검수 중에는 원래 상품이 그대로 조회됐다.”

[14] 빈 카탈로그에서 재현한 요청 전체다. 개별 후보의 처리 결과는 업로드 화면에 표시되지 않고 집계만 표시된다.

```text
REGISTER	tee	TOP-0042
REGISTER	tote	BAG-0100
REGISTER	cap	HAT-0007
STAGE	autumn	tee	BAG-0100
STAGE	autumn	tote	BAG-0100
STAGE	autumn	coat	TOP-0042
CHECK	autumn
STAGE	autumn	tee	BAG-01001
STAGE	autumn	cap	HAT-0008
STAGE	autumn	tote	BAG-0100
STAGE	autumn	spare	bad
CHECK	autumn
GET	tee
REGISTER	hold	HAT-0008
CHECK	autumn
APPLY	autumn
LIST	TOP
GET	cap
CHECK	autumn
```

[15] 신고자가 등록 규칙과 최종 제출 후보를 기준으로 계산한 기대 결과다.

```text
OK tee TOP 42
OK tote BAG 100
OK cap HAT 7
STAGED autumn tee
STAGED autumn tote
STAGED autumn coat
REVIEW autumn 1 2
STAGED autumn tee
STAGED autumn cap
STAGED autumn tote
STAGED autumn spare
REVIEW autumn 2 3
PRODUCT tee TOP-0042 TOP 42
OK hold HAT 8
REVIEW autumn 1 4
APPLIED autumn 1 4
tee:TOP-0042
PRODUCT cap HAT-0007 HAT 7
MISSING_BATCH autumn
```

[16] 같은 요청으로 시작 코드를 실행한 실제 결과다.

```text
OK tee TOP 42
OK tote BAG 100
OK cap HAT 7
STAGED autumn tee
STAGED autumn tote
STAGED autumn coat
REVIEW autumn 1 2
STAGED autumn tee
STAGED autumn cap
STAGED autumn tote
STAGED autumn spare
REVIEW autumn 4 1
PRODUCT tee TOP-0042 TOP 42
OK hold HAT 8
REVIEW autumn 3 2
APPLIED autumn 3 2
coat:TOP-0042
PRODUCT cap HAT-0007 HAT 7
MISSING_BATCH autumn
```
