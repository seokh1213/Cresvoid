---
id: F-design-patterns-110
title: 빈 대기열에서 꺼내는 작업자
kind: fix
track: bug-fix
stage: 26-unchecked-wakeup
status: 제작 중
wave: 5
env: 없음
prereq:
---

# F-design-patterns-110 빈 대기열에서 꺼내는 작업자

- 맥락: 작업자 스레드 둘이 공유 대기열에서 작업을 꺼낸다. 대기열이 비면 `wait`로 기다린다. 작업을 넣는 쪽은 작업을 넣은 뒤 `notifyAll`로 깨운다(보호된 대기).
- 버그 후보: 대기 조건을 `if`로 한 번만 확인해 깨어난 뒤 다시 확인하지 않는다. 작업 하나에 작업자 둘이 깨어나면 늦게 깨어난 쪽이 빈 대기열에서 꺼낸다(먼저 고른다). `notify`로 하나만 깨워 신호를 잃는 경우.
- 버그 신고: "작업자 기록에 가끔 `NoSuchElementException`이 찍히고 그 작업자는 멈춘다. 작업이 몰리지 않는 한가한 시간대에 주로 생긴다."
- 형식: 160~240줄, 파일 3개.
- 난도: 깨어났다는 사실이 조건이 참이라는 뜻이 아님을 알아야 한다.
- 검증: 숨은 테스트는 두 작업자가 모두 대기 상태에 들어간 것을 확인한 뒤 작업 하나만 넣어 결정적으로 재현한다. `while`로 다시 확인하는 수정과 `BlockingQueue`로 바꾼 수정이 모두 통과해야 한다.
- 출처: JDK 21 API 문서의 `Object.wait`(깨어난 뒤 조건을 반복해서 확인해야 한다), Goetz 외 "Java Concurrency in Practice" 14장(조건 큐).
- 피할 것: 실행 시점에 기대는 재현, 코루틴.
