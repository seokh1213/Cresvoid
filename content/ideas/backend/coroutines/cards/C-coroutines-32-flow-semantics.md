---
id: C-coroutines-32
title: StateFlow 와 SharedFlow 의 의미
kind: code
track: code-reading
stage: 03-flow-semantics
status: 완료(2026-10-12)
wave: 4
env: kotlinx-coroutines
prereq: C-coroutines-30
---

# C-coroutines-32 StateFlow 와 SharedFlow 의 의미

- 도메인: 상품 가격 변경을 화면과 알림 처리기에 나눠 보내는 상태 저장소.
- 겨냥: StateFlow 의 값 합치기와 equals 비교, 가변 객체를 고친 뒤 다시 넣은 값, SharedFlow 의 replay 와 버퍼, 늦게 시작한 수집자가 받는 값, 콜드 Flow 를 두 번 수집할 때의 부수효과, flowOn 과 catch 의 위치.
- 형식: 250~350줄, 파일 3~5개, 6문항(실행 결과 예측 short 3). 목표 25분.
- 검증: 문항 정답은 실행으로 확인한다. 타이밍에 기대는 결과는 테스트 디스패처로 결정적으로 만든다.
- 출처: kotlinx.coroutines API 의 StateFlow, SharedFlow 문서.
