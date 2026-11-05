---
id: F-coroutines-34
title: 시작하자마자 끝나는 백그라운드 이벤트
kind: fix
track: bug-fix
stage: 05-instant-finish
status: 완료(2026-11-05)
wave: 4
env: kotlinx-coroutines
prereq: B-coroutines-20
---

# F-coroutines-34 시작하자마자 끝나는 백그라운드 이벤트

- 맥락: 이벤트 관리자가 이벤트마다 `launch`로 코루틴을 실행하고 그 `Job`을 이벤트의 `job` 필드에 담는다. 이벤트 본문은 `job?.isActive`가 참인 동안 일정 간격으로 일하다가 정해진 시간이 지나면 끝나고 관리자 목록에서 빠진다. 시작한 직후 상태를 물으면 이벤트가 아직 목록에 있어야 한다.
- 버그 후보: `job = scope.launch { run() }`에서 코루틴 본문이 할당보다 먼저 실행되면 `job`이 아직 `null`이라 반복을 건너뛰고 즉시 끝난다(먼저 고른다). 완료 여부를 생성 시점에 한 번 계산한 `val`로 두어 상태 조회가 언제나 "진행 중"을 답하는 경우.
- 버그 신고: "이벤트를 시작하자마자 상태를 조회하면 가끔 '이벤트가 없다'는 오류가 난다. 서버가 바쁠 때 잦고 단독으로 돌린 테스트에서는 재현되지 않는다."
- 형식: 180~260줄, 파일 3~4개.
- 난도: `launch`가 돌아오기 전에 코루틴이 먼저 실행될 수 있다는 점을 알아야 원인에 닿는다.
- 검증: 숨은 테스트는 즉시 실행하는 디스패처(`Dispatchers.Unconfined`)를 주입해 결정적으로 재현하고 가상 시간으로 이벤트 시간을 건너뛴다. 코루틴 자신의 `isActive`를 보는 수정, `CoroutineStart.LAZY`로 만들어 할당한 뒤 `start()`하는 수정이 모두 통과해야 한다.
- 출처: kotlinx.coroutines 문서(`CoroutineStart`, `Dispatchers.Unconfined`, `isActive`). 주제는 kotlin-lab `event-asynchronous`의 수정 [6cf5388](https://github.com/seokh1213/kotlin-lab/commit/6cf5388448769b4c18fb039d155fcaaec2717747)에서 가져왔다. 코드는 옮기지 않는다(D15).
- 피할 것: 실제 시간과 `Thread.sleep`에 기대는 재현, F-coroutines-31이 다룬 취소 예외 삼키기.
