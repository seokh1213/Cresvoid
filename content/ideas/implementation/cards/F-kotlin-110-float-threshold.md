---
id: F-kotlin-110
title: 무료 배송 기준 금액
kind: fix
track: bug-fix
stage: float-threshold
status: 후보
wave: 5
env: 없음
prereq: 
---

# F-kotlin-110 무료 배송 기준 금액

- 맥락: 해외 판매 몰의 장바구니 합계가 50.00 달러 이상이면 배송비를 받지 않는다. 금액은 센트까지 있는 달러이고 항목별로 더해 합계를 낸다. 화면에는 합계를 소수 둘째 자리까지 보여 준다.
- 버그 후보: 금액을 Double 로 누적해 합계가 기준과 같아야 할 때 조금 작아져 배송비가 부과된다(먼저 고른다. 13.04 + 19.81 + 17.15 를 Double 로 더하면 49.99999999999999 다), 비교를 `>` 로 써서 기준과 같은 금액을 제외한다.
- 버그 신고: "합계가 정확히 50.00 달러로 보이는 장바구니에 배송비가 붙었다." 재현 입력과 기대한 결과, 실제 결과를 적는다.
- 형식: 150~220줄, 파일 2~3개.
- 난도: 화면의 합계는 `"%.2f"` 로 반올림되어 50.00 으로 보이므로 출력만으로는 원인이 보이지 않는다.
- 검증: 숨은 테스트는 Double 로 더해도 맞는 금액과 어긋나는 금액을 함께 둔다. 어긋나는 금액은 실행으로 찾아 싣는다. 센트 단위 Long 이나 BigDecimal 로 고친 수정이 모두 통과해야 한다.
- 출처: Goldberg, "What Every Computer Scientist Should Know About Floating-Point Arithmetic", ACM Computing Surveys 1991. Java SE 21 API 문서의 `BigDecimal`.
- 피할 것: F-kotlin-20 과 같은 곱셈 뒤 내림 원인. C-kotlin-10 의 BigDecimal 비교 함정.
