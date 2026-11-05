# 타임세일 시작과 끝

## 상품과 주문

[1] 쇼핑 카탈로그의 주문 가격을 계산하는 프로그램이다. 상품마다 정가와 타임세일 일정을 보관한다. 주문을 접수하면 주문 시각에 적용할 단가를 정하고 영수증을 저장한다. 재고, 결제 승인, 쿠폰은 다루지 않는다.

[2] 표준 입력에서 명령을 한 줄씩 EOF까지 읽고 명령마다 결과 한 줄을 표준 출력에 쓴다. 토큰은 공백으로 구분한다. 빈 줄은 무시한다. 명령의 이름, 토큰 수, 정수와 시각의 표기는 항상 올바르다. 식별자는 영문자·숫자·하이픈으로 된 공백 없는 문자열이며 `-` 하나는 식별자로 쓰지 않는다. 시각은 UTC의 ISO-8601 형식이며 끝에 `Z`가 붙는다. 소수 초는 아홉 자리까지 쓸 수 있다. 시스템 시계는 사용하지 않는다.

[3] 금액은 원 단위 정수다. 정가는 1 이상 1,000,000,000 이하, 세일 단가는 0 이상 1,000,000,000 이하, 수량은 1 이상 1,000 이하다. 이 범위를 벗어나는 입력은 없다. 단가에 수량을 곱한 금액은 생략하거나 반올림하지 않고 정수로 출력한다. 명령은 입력 순서대로 처리하되 주문 시각과 세일 등록 순서는 시간순일 필요가 없다.

## 명령과 가격 규칙

[4] `PRODUCT <상품> <정가>`는 상품을 등록하고 `OK PRODUCT <상품>`을 출력한다. 같은 상품 식별자를 다시 등록하면 `ERROR DUPLICATE_PRODUCT`를 출력한다. 등록된 정가는 바꾸지 않는다.

[5] `SALE <세일> <상품> <시작> <종료> <단가>`는 해당 상품의 세일을 등록하고 `OK SALE <세일>`을 출력한다. 검사는 상품 존재 여부, 세일 식별자 중복, 시작과 종료의 순서, 단가, 기존 일정과의 겹침 순으로 진행한다. 각각 실패하면 `ERROR MISSING_PRODUCT`, `ERROR DUPLICATE_SALE`, `ERROR INVALID_WINDOW`, `ERROR INVALID_PRICE`, `ERROR SALE_CONFLICT`를 출력한다. 세일 식별자는 모든 상품에서 고유해야 한다. 시작은 종료보다 빨라야 하며, 세일 단가는 상품 정가 이하여야 한다. 실패한 명령은 상품·일정·영수증을 바꾸거나 식별자를 차지하지 않는다.

[6] 세일은 시작 시각부터 종료 시각 직전까지 적용한다. 시작 시각과 같은 주문은 세일 대상이며 종료 시각과 같은 주문은 그 세일의 대상이 아니다. 같은 상품의 세일은 적용 구간이 겹칠 수 없지만 앞 세일의 종료와 뒤 세일의 시작이 같은 일정은 등록할 수 있다. 서로 다른 상품의 일정은 독립적이다.

[7] `ORDER <주문> <상품> <수량> <시각>`은 현재 등록된 일정 중 그 주문 시각에 적용되는 세일 단가를 사용한다. 해당하는 세일이 없으면 정가를 사용한다. 같은 주문 식별자가 있으면 먼저 `ERROR DUPLICATE_ORDER`를 출력한다. 중복 주문이 아니고 상품이 없으면 `ERROR MISSING_PRODUCT`를 출력한다. 성공하면 `ORDER <주문> <상품> <수량> <단가> <합계> <세일>`을 출력한다. 합계는 단가와 수량의 곱이며 정가를 썼으면 세일 자리에는 `-`를 출력한다. 세일 단가가 정가와 같더라도 세일이 적용되었다면 그 세일 식별자를 출력한다.

[8] `RECEIPT <주문>`은 주문 접수 때 저장한 영수증을 `ORDER` 성공 결과와 같은 형식으로 출력한다. 주문이 없으면 `ERROR MISSING_ORDER`를 출력한다. 나중에 세일을 등록해도 기존 영수증은 바뀌지 않는다. 조회는 주문이나 일정을 변경하지 않는다.

## 버그 신고

[9] 아래는 운영 문의를 본떠 만든 재현 자료다. 머그 행사 주문을 정산하던 중 일부 주문의 단가와 세일 식별자가 등록한 일정과 맞지 않았다. 같은 자료의 다른 머그 주문과 필터 주문은 예상한 금액으로 처리됐다. 행사 등록과 주문 접수 내역, 영수증 조회 결과를 함께 첨부한다. 영수증을 다시 조회해도 처음 받은 단가가 그대로 나왔다.

[10] 신고자가 첨부한 입력은 다음과 같다.

```text
PRODUCT mug 12000
PRODUCT filter 3000
SALE evening mug 2026-11-05T14:00:00Z 2026-11-05T16:00:00Z 8000
SALE morning mug 2026-11-05T08:00:00Z 2026-11-05T10:00:00Z 9500
ORDER r1 mug 2 2026-11-05T09:20:00Z
ORDER r2 mug 1 2026-11-05T10:00:00Z
ORDER f1 filter 3 2026-11-05T12:00:00Z
SALE lunch mug 2026-11-05T11:00:00Z 2026-11-05T13:00:00Z 9000
ORDER r3 mug 1 2026-11-05T11:00:00Z
ORDER f2 filter 1 2026-11-05T12:00:00Z
ORDER r4 mug 2 2026-11-05T13:30:00Z
ORDER r5 mug 1 2026-11-05T14:10:00Z
SALE bridge mug 2026-11-05T10:20:00Z 2026-11-05T11:00:00Z 11000
ORDER r6 mug 1 2026-11-05T12:20:00Z
RECEIPT r3
SALE bonus mug 2026-11-05T13:00:00Z 2026-11-05T14:00:00Z 10000
ORDER r7 mug 1 2026-11-05T13:30:00Z
ORDER r8 mug 1 2026-11-05T10:20:00Z
ORDER r9 mug 1 2026-11-05T11:00:00Z
RECEIPT r2
```

[11] 일정과 주문을 대조한 기대 결과는 다음과 같다.

```text
OK PRODUCT mug
OK PRODUCT filter
OK SALE evening
OK SALE morning
ORDER r1 mug 2 9500 19000 morning
ORDER r2 mug 1 12000 12000 -
ORDER f1 filter 3 3000 9000 -
OK SALE lunch
ORDER r3 mug 1 9000 9000 lunch
ORDER f2 filter 1 3000 3000 -
ORDER r4 mug 2 12000 24000 -
ORDER r5 mug 1 8000 8000 evening
OK SALE bridge
ORDER r6 mug 1 9000 9000 lunch
ORDER r3 mug 1 9000 9000 lunch
OK SALE bonus
ORDER r7 mug 1 10000 10000 bonus
ORDER r8 mug 1 11000 11000 bridge
ORDER r9 mug 1 9000 9000 lunch
ORDER r2 mug 1 12000 12000 -
```

[12] 현재 프로그램에서 얻은 실제 결과는 다음과 같다.

```text
OK PRODUCT mug
OK PRODUCT filter
OK SALE evening
OK SALE morning
ORDER r1 mug 2 9500 19000 morning
ORDER r2 mug 1 9500 9500 morning
ORDER f1 filter 3 3000 9000 -
OK SALE lunch
ORDER r3 mug 1 12000 12000 -
ORDER f2 filter 1 3000 3000 -
ORDER r4 mug 2 12000 24000 -
ORDER r5 mug 1 8000 8000 evening
OK SALE bridge
ORDER r6 mug 1 9000 9000 lunch
ORDER r3 mug 1 12000 12000 -
OK SALE bonus
ORDER r7 mug 1 10000 10000 bonus
ORDER r8 mug 1 12000 12000 -
ORDER r9 mug 1 11000 11000 bridge
ORDER r2 mug 1 9500 9500 morning
```
