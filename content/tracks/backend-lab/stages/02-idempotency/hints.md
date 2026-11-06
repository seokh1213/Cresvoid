# 첫 관찰 뒤 선택 힌트

시작 코드의 결과를 기록한 뒤 필요한 부분만 읽어라.

시작 코드의 `execute`는 결과를 보관하지 않고 매번 대행을 호출한다. 같은 키의 요청들이 공유할 상태와 요청 동일성을 판단할 위치를 찾아라.

[JDK 21 ConcurrentHashMap API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/ConcurrentHashMap.html)를 참고하라. 개별 메서드의 스레드 안전성과 여러 호출을 묶은 작업의 원자성을 구분하라. 느린 대행 호출을 맵 갱신 함수 안에서 실행하면 다른 갱신까지 기다리게 할 수 있다. 특정 자료구조나 잠금 기법을 강제하는 것은 아니다.
