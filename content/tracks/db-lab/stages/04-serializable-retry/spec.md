# 직렬화 실패와 재시도

[1] 같은 근무조에 의사 두 명이 당직을 선다. 한 명은 남아 있어야 한다. 각 의사는 현재 당직 인원을 읽고 두 명 이상이면 자신의 당직을 해제한다. 서로 다른 행을 갱신하더라도 두 트랜잭션이 같은 이전 상태를 읽으면 둘 다 퇴근하는 쓰기 비틀림이 생길 수 있다. 이 단계에서는 PostgreSQL의 SERIALIZABLE 격리와 전체 트랜잭션 재실행으로 이 규칙을 지켜라.

[2] 먼저 `Retry.kt`와 `Database.kt`를 읽고 같은 근무조의 두 요청이 충돌했을 때 응답과 남은 당직 인원을 예측해라. 시작 코드를 빌드하고 실험한 뒤 `response_errors`, `database_errors`, `policy_errors`를 비교해라. 코드를 고친 뒤 같은 시나리오를 다시 실행해라. 오류를 HTTP 성공으로 바꾸는 것만으로는 합격하지 못한다.

[3] JDK 21과 고정 실행 이미지의 `jdbc-postgres` 묶음을 사용한다. `.lab-libs`와 빌드·실행 스크립트는 제공된 그대로 사용해라. `build.sh`는 `build/app.jar`를 만들며, 실험기는 PostgreSQL과 앱을 compose로 시작하고 건강 상태를 확인한 뒤 앱의 루프백 공개 포트로 요청한다. 앱 진입점은 `lab.MainKt`이며 `PORT` 환경 변수의 포트를 사용한다. 데이터베이스 주소는 `JDBC_URL`로 받는다. 별도 라이브러리를 추가하지 마라.

[4] 공개 서비스는 `GET /health`, `POST /prepare?rounds=N`, `POST /leave?round=R&doctor=D&maxAttempts=M`을 제공한다. 본문은 비어 있다. `prepare`는 근무조 1부터 N까지 각각 두 의사를 당직 상태로 만들고 결정·시도 기록을 초기화한다. N과 R은 1~100, D는 1~2, M은 1~5다. `prepare`는 부하를 시작하기 전에 한 번만 호출한다. 실험은 같은 근무조·의사 조합을 HTTP로 두 번 보내지 않는다. HTTP 요청 자체의 중복 제거는 이번 범위가 아니다.

[5] `leave`는 한 번의 트랜잭션마다 당직 인원을 새로 읽고, 두 명 이상이면 자신의 행을 해제해 `outcome=left`, 한 명이면 그대로 두어 `outcome=stayed`를 응답한다. 두 경우 모두 결정 행을 하나 커밋하고 HTTP 200을 반환한다. 첫 시도를 포함한 전체 시도 수가 maxAttempts 이하여야 한다. SQLSTATE 40001만 재시도하고, 한도까지 실패하면 마지막 40001을 전파해 HTTP 409를 반환해라. 그 밖의 SQL 오류는 첫 실패를 전파해 HTTP 422를 반환해라. 실패한 시도의 업무 변경과 결정 행은 모두 롤백되어야 한다.

[6] 수정 대상은 `src/main/kotlin/lab/Retry.kt`의 `retry` 함수다. 인자로 받은 `transaction`의 실행 범위와 자원 수명은 `Main.kt`와 `Database.kt`에서 확인해라. `Database.kt`, `Main.kt`, 계측 코드, SQL, 격리 수준, 장벽, 요청 계약과 실행 파일은 바꾸지 마라. 새 Kotlin 파일은 추가하지 마라.

[7] 연속한 두 시도 사이의 대기는 `trace.pause(millis)`로 한 번 기록해라. 재시도 없이 끝나는 실패 뒤에는 기다리지 마라. 대기 시간은 매번 20~80ms에서 새로 무작위 선택한 정수여야 한다. 고정값, 시도 번호로 정한 값, 요청 식별자로 정한 값은 지터로 인정하지 않는다. `pause`는 실제로 잠든 뒤 선택값과 부족한 대기를 기록한다. 전체 표본의 서로 다른 대기 값은 두 개 이상이어야 한다. 이 범위와 분산 기준은 이번 실험의 정책이며 PostgreSQL의 의무 규칙은 아니다.

[8] 관찰용 선택 인자는 `paired=true`, `faults=F`, `state=S`다. `paired`는 각 근무조의 두 첫 시도가 모두 조회를 마친 뒤 갱신하도록 장벽으로 맞춘다. 재시도에는 장벽을 적용하지 않는다. `faults`는 0~99이며 처음 F번의 시도에서 업무 변경과 결정 기록 뒤, 커밋 전에 PostgreSQL이 S 오류를 발생시키게 한다. S는 40001 또는 23514이며 기본값은 40001이다. 인위적인 40001은 상한·롤백을 검사하고, 장벽 실험은 실제 직렬화 충돌을 검사한다.

[9] 응답은 세미콜론으로 구분한 `key=value` 텍스트다. 성공은 `outcome`, 실패는 `state`를 포함하며, 모두 `attempts`, 쉼표로 구분한 `delays`, `shortSleeps`를 포함한다. 예를 들어 두 번 실패한 뒤 성공하면 `outcome=left;attempts=3;delays=24,63;shortSleeps=0`처럼 응답한다. 대기 값은 실행마다 다를 수 있다. `shortSleeps`는 요청한 시간의 90%보다 짧게 잠든 횟수다. 이 값은 0이어야 하며 대기 개수는 시도 수보다 정확히 하나 적어야 한다.

[10] paired-arrivals는 근무조 12개를 차례로 실험한다. 근무조마다 doctor=1과 doctor=2 요청을 같은 예정 시각에 개방형 부하로 보내며 maxAttempts=3, paired=true를 사용한다. 앞 근무조의 응답 두 개가 끝나면 다음 근무조의 부하를 시작한다. 24개 요청 모두 200이어야 하고, 각 조의 응답은 left 하나와 stayed 하나여야 한다. 각 조에 당직 한 명이 남고 결정 24개 중 left 12개, stayed 12개가 커밋되어야 한다. 전체 트랜잭션 시도는 36번, 대기는 12번이어야 한다.

[11] bounded-arrivals는 서로 다른 근무조에 doctor=1 요청 28개를 초당 2개씩 14초 동안 예정 시각에 보낸다. 앞 요청이 느려져도 뒤 요청의 도착을 늦추지 않는다. 1~8번은 maxAttempts=3, faults=2, state=40001로 보내며 각각 세 번째 시도에 left로 성공해야 한다. 9~16번은 maxAttempts=3, faults=99, state=40001로 보내며 각각 세 번 시도하고 409여야 한다. 17~24번은 maxAttempts=3, faults=99, state=23514로 보내며 각각 한 번 시도하고 422여야 한다. 25~28번은 maxAttempts=1, faults=99, state=40001로 보내며 각각 한 번 시도하고 409여야 한다.

[12] bounded-arrivals의 전체 시도 수는 60번, 대기는 32번이어야 한다. 성공한 1~8번 근무조에만 당직 한 명이 남고 나머지는 두 명이 모두 남아야 한다. 결정은 성공한 left 8개만 커밋되어야 한다. 실패한 요청이 남긴 결정이나 당직 변경이 있으면 실패다. 모든 시나리오에서 SQL은 실제 PostgreSQL에서 실행해야 한다.

[13] 실험기는 요청 결과와 고정 계측 정보를 모으고 PostgreSQL에서 당직·결정·시도 기록을 직접 읽는다. `requests`는 요청 수, `response_errors`는 상태·결과·오류 코드·시도 수·대기 부족 응답 위반 수, `database_errors`는 최종 당직·결정·조별 결과의 위반 수, `policy_errors`는 응답 오류와 시도·대기 개수·범위·분산 위반을 합친 값이다. 세 오류 지표가 모두 0이어야 한다. `transaction_attempts`, `max_attempts`, `retry_delays`, `distinct_delays`도 표시한다. 한 시나리오를 세 번 실행해 모두 통과해라.

[14] `p95_ms`는 HTTP 요청을 보내기 시작한 뒤 응답을 끝까지 받은 지연의 95백분위수다. 지연 자체는 합격 기준이 아니다. HTTP 제한 시간은 요청마다 30초, 첫 시도 장벽 대기는 20초, 서비스 준비 제한은 120초, 시나리오 전체 제한은 240초다. 연결 실패·준비 실패·측정 SQL 실패는 정상적인 불변식 실패로 세지 않는다. 실험기는 성공과 실패 모두에서 앱·데이터베이스·볼륨·네트워크를 정리한다.

[15] 공식 근거는 [PostgreSQL 18 Transaction Isolation의 Serializable Isolation Level](https://www.postgresql.org/docs/18/transaction-iso.html#XACT-SERIALIZABLE)과 [Serialization Failure Handling](https://www.postgresql.org/docs/18/mvcc-serialization-failure-handling.html)이다. PostgreSQL은 직렬화 실패를 SQLSTATE 40001로 알리며, 실행할 SQL과 값을 정하는 판단까지 포함해 전체 트랜잭션을 다시 실행하도록 요구한다. 재실행해도 충돌이 반복될 수 있으므로 무한 성공을 가정하지 마라. 이번 계약은 40001만 재시도하며 다른 오류 코드를 일괄 재시도하지 않는다.
