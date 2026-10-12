---
id: C-coroutines-30
title: 코루틴 실행 순서와 디스패처
kind: code
track: code-reading
stage: 02-coroutine-order
status: 완료(2026-10-12)
wave: 3
env: kotlinx-coroutines
prereq: B-coroutines-20
---

# C-coroutines-30 코루틴 실행 순서와 디스패처

- 도메인: 상품 상세 화면에 필요한 가격, 재고, 리뷰를 여러 하위 서비스에서 동시에 가져와 합치는 집계 서비스. 하위 서비스는 지연과 실패를 주입할 수 있는 가짜 구현이다.
- 겨냥: launch 와 async 의 시작 시점, coroutineScope 와 supervisorScope 의 차이, 한 하위 호출의 실패가 나머지를 취소하는지, withTimeout 과 정리 블록, Dispatchers.Default 와 IO 의 스레드, 예외 처리기가 불리는 위치.
- 형식: 250~350줄, 파일 3~5개, 6문항(실행 결과 예측 short 3). 목표 25분.
- 검증: 문항 정답은 실행으로 확인한다. 스레드 이름처럼 실행마다 달라지는 출력은 문항에 쓰지 않는다.
- 출처: 이 도메인 README 의 '개념 지도' 절.
