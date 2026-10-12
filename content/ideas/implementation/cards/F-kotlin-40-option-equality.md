---
id: F-kotlin-40
title: 장바구니 상품 옵션 비교
kind: fix
track: bug-fix
stage: option-equality
status: 후보
wave: 4
env: 없음
prereq: 
---

# F-kotlin-40 장바구니 상품 옵션 비교

- 맥락: 장바구니에 같은 상품을 같은 옵션으로 다시 담으면 수량을 합친다. 옵션은 색상, 크기 같은 이름과 선택값 목록이다. 장바구니는 상품 ID 와 옵션을 키로 항목을 찾는다.
- 버그 후보: 옵션을 data class 로 두면서 선택값을 `Array` 로 담아 `equals` 가 배열을 참조로 비교한다(먼저 고른다. 같은 옵션이 다른 항목으로 쌓인다), `equals` 만 직접 구현하고 `hashCode` 는 두지 않은 클래스, 해시 집합에 넣은 뒤 키 객체의 필드를 바꿔 찾지 못한다.
- 버그 신고: "같은 상품을 같은 옵션으로 두 번 담으면 수량 2인 한 줄이 아니라 수량 1인 두 줄이 생긴다."
- 형식: 180~260줄, 파일 3~4개.
- 난도: 증상은 바로 보이지만 원인이 언어 규칙에 있어 한 번 더 추론해야 한다.
- 검증: 숨은 테스트는 옵션이 같은 경우와 선택값 하나만 다른 경우를 둔다. `List` 로 바꾸거나 `equals` 와 `hashCode` 를 함께 구현한 수정이 모두 통과해야 한다.
- 출처: Kotlin 문서의 Data classes 와 Arrays(배열 내용 비교는 `contentEquals`). Java SE 21 API 문서의 `Object.hashCode` 계약.
- 피할 것: F-kotlin-20 의 BigDecimal 동등성 후보와 겹치는 원인.
