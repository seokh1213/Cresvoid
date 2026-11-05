# 무료 배송 기준 금액

## 장바구니 견적

[1] 해외 판매 몰의 카탈로그 가격과 장바구니를 관리한다. 고객은 상품을 담거나 빼면서 배송비가 포함된 견적을 확인한다. 모든 금액의 통화는 미국 달러다. 상품 수량은 따로 두지 않으며 같은 상품을 여러 개 담으려면 서로 다른 항목 ID로 담는다.

[2] 프로그램은 표준 입력에서 명령을 한 줄씩 EOF까지 읽고 결과를 표준 출력에 쓴다. 빈 줄은 무시한다. 명령과 인자는 공백으로 구분하며 대소문자를 구분한다. 상품·장바구니·항목 ID는 영문자, 숫자, 밑줄, 하이픈으로 이루어진 1~24자 문자열이다. 모든 ID의 형식과 정의된 명령의 인자 수는 올바르게 주어진다. 한 실행의 명령은 1,000줄 이하다.

[3] `PRICE <상품 ID> <금액>`은 카탈로그 가격을 등록하거나 바꾸고 `OK`를 출력한다. 금액은 `0.00`부터 `999999.99`까지이며 소수점 아래 두 자리를 반드시 쓴다. 정수부는 `0`이거나 0으로 시작하지 않는 1~6자리 숫자다. 다른 형식이나 범위의 금액은 `ERROR amount`를 출력하고 기존 가격을 유지한다.

[4] `PUT <장바구니 ID> <항목 ID> <상품 ID>`는 카탈로그의 현재 가격으로 항목을 담고 `OK`를 출력한다. 같은 장바구니에 같은 항목 ID가 있으면 상품과 가격을 모두 교체한다. 항목을 담을 때의 가격은 이후 카탈로그 가격이 바뀌어도 유지한다. 상품이 없으면 `ERROR product`를 출력하고 장바구니를 바꾸지 않는다.

[5] `DROP <장바구니 ID> <항목 ID>`는 해당 항목을 빼고 `OK`를 출력한다. 장바구니나 항목이 없으면 `ERROR line`을 출력한다. 다른 장바구니와 카탈로그에는 영향을 주지 않는다.

[6] `ITEMS <장바구니 ID>`는 항목을 항목 ID의 문자열 오름차순으로 나열한다. 출력은 `ITEMS <장바구니 ID> <항목 ID>:<상품 ID>:<담은 가격> ...` 형식이며 항목 사이는 공백 하나로 구분한다. 항목이 없으면 `ITEMS <장바구니 ID> (empty)`를 출력한다.

[7] `QUOTE <장바구니 ID>`는 현재 장바구니에 담긴 각 항목의 가격을 더해 상품 합계를 구한다. 상품 합계가 50.00달러 이상이면 배송비는 0.00달러이며 그보다 작으면 4.99달러다. 항목이 없는 장바구니의 배송비는 0.00달러다. 청구액은 상품 합계와 배송비를 더한 값이다. 조회는 장바구니를 바꾸지 않는다.

[8] 견적 출력은 `QUOTE <장바구니 ID> subtotal=<상품 합계> shipping=<배송비> total=<청구액>` 형식이다. 모든 출력 금액은 천 단위 구분 없이 점을 소수점으로 쓰고 소수점 아래 두 자리를 표시한다. 위에서 정한 명령 외의 명령은 `ERROR command`를 출력하고 상태를 바꾸지 않는다.

## 버그 신고

[9] 여행용 상품을 담고 견적을 확인했는데 배송비가 붙어 청구액이 예상보다 커졌다. 책상용 장바구니는 합계가 같았는데 배송비가 붙지 않았다. 담은 상품 목록도 확인하고 여행용 상품 하나를 빼 보았다. 그때 실행한 기록을 함께 보낸다.

[9.1] 재현 입력은 다음과 같다.

```text
PRICE brush 8.25
PUT spare z brush
QUOTE spare
PRICE lamp 25.00
PUT desk a lamp
PUT desk b lamp
QUOTE desk
PRICE mug 13.04
PRICE pouch 19.81
PRICE flask 17.15
PUT trip a mug
PUT trip b pouch
PUT trip c flask
ITEMS trip
QUOTE trip
DROP trip b
QUOTE trip
QUOTE spare
QUOTE desk
```

[9.2] 기대한 결과는 다음과 같다.

```text
OK
OK
QUOTE spare subtotal=8.25 shipping=4.99 total=13.24
OK
OK
OK
QUOTE desk subtotal=50.00 shipping=0.00 total=50.00
OK
OK
OK
OK
OK
OK
ITEMS trip a:mug:13.04 b:pouch:19.81 c:flask:17.15
QUOTE trip subtotal=50.00 shipping=0.00 total=50.00
OK
QUOTE trip subtotal=30.19 shipping=4.99 total=35.18
QUOTE spare subtotal=8.25 shipping=4.99 total=13.24
QUOTE desk subtotal=50.00 shipping=0.00 total=50.00
```

[9.3] 실제 결과는 다음과 같다.

```text
OK
OK
QUOTE spare subtotal=8.25 shipping=4.99 total=13.24
OK
OK
OK
QUOTE desk subtotal=50.00 shipping=0.00 total=50.00
OK
OK
OK
OK
OK
OK
ITEMS trip a:mug:13.04 b:pouch:19.81 c:flask:17.15
QUOTE trip subtotal=50.00 shipping=4.99 total=54.99
OK
QUOTE trip subtotal=30.19 shipping=4.99 total=35.18
QUOTE spare subtotal=8.25 shipping=4.99 total=13.24
QUOTE desk subtotal=50.00 shipping=0.00 total=50.00
```
