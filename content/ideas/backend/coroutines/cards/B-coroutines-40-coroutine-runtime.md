---
id: B-coroutines-40
title: 작은 코루틴 실행기 만들기
kind: build
track: coroutine-runtime
stage: 01-event-loop, 02-structured, 03-cancellation, 04-exceptions
status: 후보
wave: 4
env: 없음
prereq: B-coroutines-20
---

# B-coroutines-40 작은 코루틴 실행기 만들기

- 목표: 표준 라이브러리의 코루틴 기본 요소(`Continuation`, `startCoroutine`, `suspendCoroutine`)만으로 가상 시계 위의 단일 스레드 실행기를 만들고 구조적 동시성, 취소, 예외 전파를 구현한다. B-coroutines-20 에서 써 본 kotlinx.coroutines 의 의미론을 직접 구현하며 이해하는 것이 목표다.
- 입출력 모양: 코루틴 시나리오를 작은 명령 언어로 받는다. 예: 코루틴 정의(이름, 부모, 감독자 여부), 본문 명령(출력, 지연, 자식 시작, 결과 기다리기, 실패, 취소 확인, 계산 반복). 실행기는 사건(시작, 출력, 재개, 취소, 실패, 완료)을 가상 시각과 함께 정해진 순서로 출력한다. 같은 시각의 재개 순서는 명세가 정한다.
- 1단계 `01-event-loop`: 중단과 재개, 가상 시계의 지연, 준비된 코루틴 큐의 순서, async 와 await.
- 2단계 `02-structured`: 부모-자식 관계, 부모는 자식이 다 끝나야 완료, join, 스코프가 끝날 때 남은 자식 기다리기.
- 3단계 `03-cancellation`: 취소 요청, 중단 지점마다 하는 취소 확인, 계산 반복의 명시적 확인, 정리 블록, 정리 중 중단(취소 불가 문맥), 시간 제한.
- 4단계 `04-exceptions`: 실패가 부모와 형제를 취소하는 규칙, 감독자 아래의 독립 실패, await 에서 드러나는 예외, 루트 예외 처리기, 취소 예외를 삼키는 본문의 결과.
- 난도: 단계마다 2~3시간. 표준 라이브러리 코루틴 기본 요소를 처음 쓰는 사람에게 1단계가 가장 어렵다.
- 함정: 같은 시각 재개 순서, 이미 끝난 자식의 취소, 정리 블록에서 다시 던진 예외, 감독자 안에서 다시 일반 스코프를 연 경우, 첫 예외와 이후 예외의 처리.
- 검증: build 스킬의 교차 검증. 의존성이 허용되면 같은 시나리오를 kotlinx.coroutines 로 실행해 사건 순서를 대조한다. 의미가 다르게 단순화된 부분은 스펙에 밝힌다.
- 출처: Kotlin 코루틴 설계 문서(KEEP), Kotlin 문서(Cancellation and timeouts, Coroutine exceptions handling). 사용자가 추천한 영상 시리즈 "코루틴 launch를 직접 만들어? #1"(https://youtu.be/qr8tfjV_U48). 사용자가 함께 추천한 재생목록 "코틀린 코루틴 #1~#8" 과의 대조와 단계 추가 제안은 이 도메인 README 의 '실행기 구현 영상 조사' 절에 있다. 영상은 주제만 참고한다(D15).
- 피할 것: kotlinx.coroutines 의 내부 구현(작업 훔치기 스케줄러, 원자 상태 기계)을 요구하는 것.
