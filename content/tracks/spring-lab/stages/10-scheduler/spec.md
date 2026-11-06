# @Scheduled 단일 스레드

[1] 실행 전에 느린 작업과 짧은 작업이 각각 언제 실행될지 예측하고 baseline과 comparison의 지표를 비교해라. 시작 코드를 먼저 실험하고 합격 불변식과 다른 관측값의 원인을 코드에서 찾아라. 관측값을 근거로 comparison 설정을 고친 뒤 같은 실험을 반복해라.

[2] 이 실험은 Spring Boot 자동 구성 없이 Spring Context의 `@EnableScheduling`과 `@Scheduled`를 사용한다. 적합한 TaskScheduler나 ScheduledExecutorService 빈이 없으면 Spring이 로컬 단일 스레드 스케줄러를 만든다. ThreadPoolTaskScheduler도 기본 풀 크기는 1이며, 작업 본문을 스케줄러 스레드에서 직접 실행한다. 서로 다른 작업이라도 같은 스레드를 쓰면 한 작업의 대기가 다른 작업을 늦춘다. 이는 모든 Spring 애플리케이션의 스케줄러가 항상 하나라는 뜻은 아니다.

[3] 두 실행 모두 새로운 Spring 컨텍스트와 ScheduledProbe 빈 하나를 만든다. slowJob은 initialDelay 0ms, fixedDelay 60,000ms로 등록하고 해제 신호를 기다린다. 15초 안에 신호가 없으면 안전장치로 종료하고 expired를 증가시킨다. pulse는 initialDelay 200ms, fixedDelay 100ms로 등록한다. fixedDelay는 직전 호출이 끝난 뒤부터 다음 호출까지의 간격이다. pulse는 느린 작업이 시작되기 전에는 기록하지 않는다. 스케줄 시각은 실행 보장이 아니며, HTTP 요청 처리 스레드와 스케줄러 스레드는 별개다.

[4] 공개 서비스는 PORT 환경 변수의 포트를 사용한다. run.sh는 호스트의 127.0.0.1에만 포트를 공개하고, 컨테이너 안의 서비스는 0.0.0.0에서 수신한다. GET /health는 200과 ready를 반환한다. POST /start?mode=baseline 또는 comparison은 이전 컨텍스트를 닫고 새 실행을 시작한 뒤 200과 started를 반환한다. 잘못된 mode는 400이다. POST /release는 현재 느린 작업의 대기를 풀고 200과 released를 반환한다. GET /snapshot은 200과 관측값을 반환한다. 실행 전 release와 snapshot은 409, 그 밖의 경로·메서드는 404다. 각 응답은 UTF-8 평문이다.

[5] snapshot은 long_started, long_finished, pulses, during, first_pulse_ms, long_thread, pulse_thread, expired를 각각 key=정수 형식의 행으로 반환한다. long_started는 시작 전 0, 시작 후 1이다. long_finished는 끝난 느린 작업 수다. pulses는 짧은 작업의 누적 실행 수이고 during은 느린 작업 시작 후 종료 전에 실행된 짧은 작업 수다. first_pulse_ms는 느린 작업 시작부터 첫 pulse까지의 경과 밀리초이며, 아직 실행되지 않았다면 -1이다. 두 thread 값은 각 작업을 실제 실행한 스레드의 양수 ID이며, 실행 전에는 0이다. 한 번의 snapshot은 같은 잠금 아래 읽어 일관성을 지켜라.

[6] 실험기는 서비스를 한 번 띄우고 준비 상태를 확인한 뒤 baseline, comparison을 차례로 실행한다. 각 실행에서 long_started=1을 최대 5초 동안 기다린다. 이후 예정 시각 0, 200, 400ms부터 200ms 간격으로 snapshot 요청을 독립적으로 보내고, scheduled-window는 1,600ms, extended-window는 2,600ms에 release 요청을 보낸다. 각 HTTP 요청의 제한은 3초이며 이전 응답 때문에 다음 요청을 늦추지 않는 개방형 부하다. release 뒤 long_finished=1과 pulses≥1을 최대 5초 동안 기다린다. 각 시나리오는 세 번 반복한다. 준비 확인에는 최대 30초를 허용한다.

[7] 합격 불변식은 baseline에서 during=0이고 두 작업의 스레드 ID가 같으며, comparison에서 during≥1이고 두 작업의 스레드 ID가 다른 것이다. 두 실행 모두 long_started=1, long_finished=1, pulses≥1, expired=0이어야 한다. 부하·시작·해제·최종 snapshot의 HTTP 요청은 모두 200이어야 한다. 모든 pulse를 없애거나 양쪽 풀을 함께 늘리는 방식은 통과할 수 없다. 경과 시간 자체에는 합격 임계값을 두지 않으며, 해제 전 실행 여부와 스레드 관계를 판정한다.

[8] 지표 baseline_during과 comparison_during은 각 실행의 during, baseline_same_thread와 comparison_distinct_thread는 해당 스레드 관계가 참이면 1, 아니면 0이다. baseline_first_ms와 comparison_first_ms는 first_pulse_ms다. completed는 정상 종료한 느린 작업 수의 합이며 2여야 한다. http_errors는 준비 확인을 제외한 HTTP 실패 수, contract_failures는 [7]의 실행별 시작·종료·pulse·expired 조건 위반 수다. 합격하려면 두 오류 지표가 모두 0이어야 한다. 시작을 관측하지 못해도 지표를 출력하고 불변식 실패로 처리한다. Docker 준비 실패나 실험기 내부 오류는 별도 실험 오류다.

[9] 우선 코드를 고치지 말고 두 실행의 예상값을 적은 뒤 실험해라. 수정은 src/main/kotlin/lab/SchedulerSetup.kt의 comparison 풀 크기에 한정한다. baseline에는 스케줄러 빈을 추가하지 마라. 그 밖의 Kotlin 파일, @Scheduled 설정, 관측 코드, HTTP 계약, .lab-libs와 빌드·실행 파일은 수정하지 마라. 새 의존성 없이 고정 실행 이미지의 spring 묶음과 JDK 21을 사용한다. build.sh로 빌드한 뒤 run.sh로 서비스를 실행하며, 실험기는 이와 같은 실행 이미지를 compose로 띄우고 종료한다.

[10] 공식 근거는 Spring Framework 7.0.10의 [EnableScheduling](https://docs.spring.io/spring-framework/docs/7.0.10/javadoc-api/org/springframework/scheduling/annotation/EnableScheduling.html), [ThreadPoolTaskScheduler](https://docs.spring.io/spring-framework/docs/7.0.10/javadoc-api/org/springframework/scheduling/concurrent/ThreadPoolTaskScheduler.html), [Scheduled](https://docs.spring.io/spring-framework/docs/7.0.10/javadoc-api/org/springframework/scheduling/annotation/Scheduled.html) API다. 각각 기본 스케줄러 선택, 풀 크기와 실행 스레드, fixedDelay의 기준 시점을 설명한다. 밀리초 지표는 부하와 실행 환경에 따라 달라지므로 특정 값으로 암기하지 마라.
