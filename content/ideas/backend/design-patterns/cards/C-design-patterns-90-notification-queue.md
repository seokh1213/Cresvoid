---
id: C-design-patterns-90
title: 알림 발송 작업 큐
kind: code
track: code-reading
stage: notification-queue
status: 후보
wave: 5
env: 없음
prereq:
---

# C-design-patterns-90 알림 발송 작업 큐

- 도메인: 알림 요청을 `ArrayBlockingQueue`에 넣고 작업자 스레드 여럿이 꺼내 보내는 발송기. 종료할 때는 작업자 수만큼 독약 객체를 넣는다. 작업자는 `ExecutorService`로 실행하고 종료 때 처리하지 못한 요청을 보고한다. 문항이 묻는 출력이 실행마다 같도록 시나리오마다 작업자 하나를 쓰거나 래치로 진행을 통제한다. 큐, 작업자, 종료 절차, 시나리오를 파일로 나눈다.
- 겨냥: `offer`(가득 차면 바로 `false`)와 `put`(빈자리를 기다린다)의 차이와 버려진 요청 수, 독약 객체 수가 작업자 수보다 적을 때 끝나지 않는 작업자, `shutdown`은 기다리지 않고 `awaitTermination`이 기다린다는 점, `shutdownNow`가 돌려주는 시작하지 않은 작업과 실행 중인 작업이 받는 인터럽트, FIFO 큐에서 독약 객체 뒤에 들어간 요청이 처리되지 않는 경우.
- 형식: 220~320줄, 파일 3~4개, 5문항(실행 결과 예측 short 2, choice 3), 목표 25분.
- 난도: 래치 조건에서 스레드 사이의 순서를 추론해야 한다.
- 검증: 실행으로 확인한다. 문항이 묻는 출력은 50회 반복 실행에서 모두 같아야 한다.
- 출처: JDK 21 API 문서(`BlockingQueue`, `ExecutorService`), Goetz 외 "Java Concurrency in Practice" 7장(취소와 종료, 독약 객체).
- 피할 것: 코루틴 Channel(coroutines 도메인의 아이디어 'Channel 과 생산자-소비자'), 실행마다 출력이 달라지는 문항.
