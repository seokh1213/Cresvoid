---
id: F-kotlin-50
title: 추천 점수 정렬
kind: fix
track: bug-fix
stage: comparator-contract
status: 후보
wave: 4
env: 없음
prereq: 
---

# F-kotlin-50 추천 점수 정렬

- 맥락: 추천 상품 목록을 점수 내림차순으로 정렬해 보여 준다. 점수는 0~1 사이 Double 이고 동점이면 상품 ID 오름차순이다. 정렬 비교자는 직접 구현한 Comparator 다.
- 버그 후보: 점수 차이를 `((b - a) * 10).toInt()` 처럼 정수로 잘라 비교해 가까운 점수를 같다고 판단하고 추이성이 깨진다(먼저 고른다. 원소가 많으면 `IllegalArgumentException: Comparison method violates its general contract!` 가 날 수 있고 적으면 예외 없이 순서만 틀린다), 동점 처리에서 a 와 b 를 바꿔 쓴 비교자.
- 버그 신고: "추천 목록이 점수 순서가 아니다. 상품이 많은 카테고리에서는 추천 화면이 오류로 열리지 않는다."
- 형식: 180~260줄, 파일 3~4개.
- 난도: 두 증상이 같은 원인임을 알아내야 한다. 예외가 원소 수에 따라 나거나 나지 않아 재현 범위를 좁혀야 한다.
- 검증: 예외를 재현하는 입력은 시드를 고정한 무작위 탐색으로 찾아 고정한다(원소 64개, `java.util.Random(4)` 에서 재현을 확인했다). `compareByDescending` 과 `thenBy` 로 고친 수정과 `Double.compare` 로 고친 수정이 모두 통과해야 한다.
- 출처: Java SE 21 API 문서의 `Comparator` 계약과 `List.sort`(계약 위반을 발견하면 IllegalArgumentException 을 던질 수 있다).
- 피할 것: 정렬 알고리즘 내부 규칙을 알아야 고칠 수 있게 만드는 것.
