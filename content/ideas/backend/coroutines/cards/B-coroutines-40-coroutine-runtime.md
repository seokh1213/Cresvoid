---
id: B-coroutines-40
title: 작은 코루틴 실행기 만들기
kind: build
track: coroutine-runtime
stage: 01-event-loop, 02-structured, 03-cancellation, 04-exceptions, 05-channels, 06-flow, 07-sequence, 08-manual-state-machine
status: 완료(2026-10-14)
wave: 4
env: 없음
prereq: B-coroutines-20
---

# B-coroutines-40 작은 코루틴 실행기 만들기

- 목표: 표준 라이브러리의 코루틴 기본 요소(`Continuation`, `startCoroutine`, `suspendCoroutine`)만으로 가상 시계 위의 단일 스레드 실행기를 만들고 구조적 동시성, 취소, 예외 전파를 구현한다. B-coroutines-20 에서 써 본 kotlinx.coroutines 의 의미론을 직접 구현하며 이해하는 것이 목표다.
- 입출력 모양: 코루틴 시나리오를 작은 명령 언어로 받는다. 예: 코루틴 정의(이름, 부모, 감독자 여부), 본문 명령(출력, 지연, 자식 시작, 결과 기다리기, 실패, 취소 확인, 계산 반복). 실행기는 사건(시작, 출력, 재개, 취소, 실패, 완료)을 가상 시각과 함께 정해진 순서로 출력한다. 같은 시각의 재개 순서는 명세가 정한다.
- 1단계 `01-event-loop`: 중단과 재개, 가상 시계의 지연, 준비 큐와 디스패치, async 와 await.
- 2단계 `02-structured`: 부모-자식 완료, join 과 scope, 문맥 상속과 일부 교체, 지연 시작과 Job 상태.
- 3단계 `03-cancellation`: 협력적 취소, yield 와 명시적 확인, 정리와 취소 불가 구간, 시간 제한과 완료 처리기.
- 4단계 `04-exceptions`: 부모·형제 실패 전파, supervisor, await 예외, 루트 처리기와 취소 예외를 삼킨 결과.
- 5단계 `05-channels`: 랑데부·버퍼 Channel 의 send/receive/close 와 Mutex 대기 순서.
- 6단계 `06-flow`: 수집할 때마다 시작하는 cold Flow, map·filter·take 와 상류 취소.
- 7단계 `07-sequence`: 요청할 때마다 `Continuation`을 재개하는 pull 시퀀스, 반복자별 상태와 `hasNext` 값 보관.
- 8단계 `08-manual-state-machine`: 두 중단 지점의 재개 위치와 첫 결과를 직접 보존하는 상태 기계, 동기·지연 재개와 실패 전달.
- 난도: 시작 코드는 공개 API의 뼈대만 제공하며, 1단계부터 파서와 실행기를 직접 만들어야 한다. 5단계에는 대기자 취소와 소유권 이전 경합이 있다. 단계당 소요 시간은 아직 측정하지 않았으므로 2~3시간 완수를 보장하지 않는다.
- 함정: 같은 시각 재개 순서, 이미 끝난 자식의 취소, 정리 블록에서 다시 던진 예외, 감독자 안에서 다시 일반 스코프를 연 경우, 첫 예외와 이후 예외의 처리.
- 검증: 독립적으로 작성한 문항·숨은 테스트와 참조 풀이를 교차 검증한다. 공개 API 직접 호출, 가상 시간의 경합·취소·닫힘·상류 정지, 이전 단계 회귀를 검사한다. 표준 라이브러리와 JDK만으로 실행하며 kotlinx.coroutines 와 의미가 다르게 단순화된 부분은 스펙에 밝힌다.
- 출처: Kotlin 코루틴 설계 문서(KEEP), Kotlin 문서(Cancellation and timeouts, Coroutine exceptions handling). 사용자가 추천한 영상 시리즈 "코루틴 launch를 직접 만들어? #1"(https://youtu.be/qr8tfjV_U48). 사용자가 함께 추천한 재생목록 "코틀린 코루틴 #1~#8" 과의 대조와 단계 추가 제안은 이 도메인 README 의 '실행기 구현 영상 조사' 절에 있다. 영상은 주제만 참고한다(D15).
- 피할 것: kotlinx.coroutines 의 내부 구현(작업 훔치기 스케줄러, 원자 상태 기계)을 요구하는 것.
