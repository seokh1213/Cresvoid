---
id: F-coroutines-31
title: 주문 확정 제한 시간
kind: fix
track: bug-fix
stage: 02-order-deadline
status: 완료(2026-10-12)
wave: 3
env: kotlinx-coroutines
prereq: B-coroutines-20
---

# F-coroutines-31 주문 확정 제한 시간

- 맥락: 주문마다 하위 서비스 여러 개를 호출하고 제한 시간 안에 끝나지 않으면 포기하는 처리기.
- 버그 후보: `catch (e: Exception)` 나 `runCatching` 이 CancellationException 을 삼켜 제한 시간이 지나도 작업이 계속됨, 정리 블록 안의 중단 호출이 취소 때문에 실행되지 않음, 계산 반복이 취소를 확인하지 않음.
- 형식: 200~300줄, 파일 3~4개. 버그 신고에는 "제한 시간이 지났다는 응답을 받았는데 하위 서비스 호출 기록이 계속 늘어남" 같은 증상을 적는다.
- 검증: 버그 신고의 재현은 가상 시간을 쓰는 테스트로 결정적으로 만든다.
- 출처: Kotlin 문서(Cancellation and timeouts).
- 피할 것: 실행마다 결과가 달라지는 실제 시간 기반 테스트.
