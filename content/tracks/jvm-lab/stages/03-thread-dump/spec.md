# 스레드 덤프로 교착 찾기

[1] 두 계좌 사이의 이체를 처리하는 Kotlin HTTP 서비스를 관찰하고 수정하라. 먼저 시작 코드를 실행해 응답이 끝나지 않는 상황을 만들고, 살아 있는 JVM의 스레드 덤프에서 원인을 찾아라. 그 뒤 이체 요청이 모두 완료되고 잔액도 맞도록 고쳐라. JDK 21과 Kotlin 표준 라이브러리만 사용한다.

[2] 프로젝트에서 `./build.sh`를 실행한 뒤 `PORT=18083 ./run.sh`로 서비스를 띄워라. 서비스는 PORT가 지정한 127.0.0.1 포트에서만 수신한다. 다른 터미널에서 `curl -s http://127.0.0.1:18083/health`를 실행하라. HTTP 200 본문 `READY <pid>`의 양의 정수가 진단할 JVM의 PID다. 포트가 사용 중이면 다른 포트를 골라라. 실험기는 빈 포트를 골라 실행하고 최대 30초 동안 준비를 확인한다.

[3] 두 터미널에서 `curl -s -X POST 'http://127.0.0.1:18083/transfer?from=A&to=B&amount=7&pair=demo'`와 `curl -s -X POST 'http://127.0.0.1:18083/transfer?from=B&to=A&amount=3&pair=demo'`를 거의 동시에 실행하라. 응답이 끝나지 않으면 서버를 끄기 전에 `jcmd <pid> Thread.print -l`로 덤프를 얻어라. `transfer-worker-` 스레드의 스택, 기다리는 잠금 주소, `Locked ownable synchronizers`의 소유 잠금 주소를 연결해 기다림의 고리를 그려라. 상태 이름 하나만 보고 교착으로 판단하지 마라. ReentrantLock 대기는 WAITING과 parking으로 나타날 수 있다. 덤프 끝의 교착 보고와 각 스레드가 소유한 잠금을 함께 확인하라.

[4] `GET /health`는 HTTP 200과 `READY <pid>`를 반환한다. `GET /balances`는 HTTP 200과 `A=<잔액> B=<잔액>`을 반환한다. 시작 잔액은 A와 B 각각 10000이다. `/balances`는 진단을 위해 계좌 잠금 없이 volatile 값을 읽으므로 부하 중의 일관된 스냅샷을 보장하지 않는다. 잔액 판정은 해당 부하의 응답을 모두 수집한 뒤에만 한다.

[5] `POST /transfer?from=<계좌>&to=<계좌>&amount=<금액>`은 출금 계좌에서 금액을 빼고 입금 계좌에 같은 금액을 더한 뒤 HTTP 200과 정확히 `OK`를 반환한다. 계좌는 A와 B이고 서로 달라야 한다. 금액은 1 이상 100 이하의 정수다. 각 유효 요청은 정확히 한 번 반영한다. 잠금 충돌을 이유로 이체를 건너뛰거나 오류로 반환해서는 안 된다. 실험은 잔액이 음수가 되지 않는 입력만 보낸다. 잘못된 인자는 HTTP 400과 `INVALID`, 알려진 경로의 다른 메서드는 HTTP 405와 `METHOD`, 없는 경로는 HTTP 404와 `NOT_FOUND`를 반환한다.

[6] 선택 인자 `pair`는 영문 소문자·숫자·하이픈으로 이루어진 1~32자 식별자다. 실험은 같은 식별자를 정확히 두 요청에만 사용한다. `FirstLockWindows.enter`는 첫 계좌 잠금을 얻은 직후 호출하는 재현 장치다. 같은 식별자의 두 호출이 도착하거나 2초가 지나면 반환한다. 일반 요청에는 pair가 없다. 이 장치를 제거하거나 호출 위치를 옮겨 증상을 숨기지 마라.

[6.1] 이 단계에서 다루는 쿼리는 각 키가 한 번만 나타나는 `키=값` 필드를 `&`로 연결한 형식이다. 키와 값은 URL 인코딩 없이 그대로 쓰며, 중복 키와 URL 인코딩이 포함된 입력은 실험과 오류 응답 계약의 범위 밖이다. 이 범위 안에서 필수 인자가 빠졌거나, 알 수 없는 키가 있거나, 값이 [5]와 [6]의 규칙에 맞지 않으면 HTTP 400과 `INVALID`를 반환한다.

[7] `paired-arrivals`는 시각 0에 A→B 7과 B→A 3을 같은 pair로 보낸다. 두 응답 수집 뒤의 기대 잔액은 A=9996 B=10004다. 이어 일반 요청 24개를 50ms 간격으로 보낸다. 0부터 센 짝수 요청은 A→B 2, 홀수 요청은 B→A 1이다. 최종 기대 잔액은 A=9984 B=10016이며 총 요청 수는 26개다.

[8] `mixed-arrivals`는 시각 0에 B→A 11과 A→B 5를 같은 pair로 보낸다. 두 응답 수집 뒤의 기대 잔액은 A=10006 B=9994다. 이어 일반 요청 48개를 25ms 간격으로 보낸다. 0부터 센 인덱스가 3의 배수이면 B→A 3, 나머지는 A→B 2다. 최종 기대 잔액은 A=9990 B=10010이며 총 요청 수는 50개다. 일반 요청의 예정 시각은 앞선 응답을 기다리지 않고 정한다. 각 시나리오는 새 JVM에서 실행한다.

[9] 모든 이체 요청의 클라이언트 제한 시간은 8초다. 이를 넘기거나 연결·전송이 실패한 요청을 `stalled`로 센다. `requested`는 예정한 요청 수, `completed`는 HTTP 200과 정확한 본문 OK를 받은 수, `bad_responses`는 전송은 끝났지만 상태나 본문이 다른 수다. 따라서 requested = completed + stalled + bad_responses여야 한다. `p99_ms`는 실패를 포함한 모든 이체 요청의 전송 시작부터 응답 또는 오류까지 걸린 시간을 정렬한 뒤 ceil(0.99×요청 수)번째 값이다. p99는 관찰용이며 별도 합격 임계값은 없다. 2초 재현 대기에 비해 8초 제한은 여유가 있지만 영구 대기는 반드시 실패한다.

[10] `balance_errors`는 두 이체 응답 수집 뒤와 일반 부하 응답 수집 뒤에 A와 B를 각각 기대값과 비교해 다른 항목 수를 합한 값이다. 범위는 0~4다. 잔액 조회는 2초 안에 응답해야 하며 조회 실패나 형식 오류는 그 시점의 두 항목을 모두 오류로 센다. 두 검사 시점에 `jcmd <pid> Thread.print -l`을 실행한다. `deadlock_cycles`는 두 덤프의 마지막 `Found N deadlock` 보고에서 얻은 N의 최댓값이며 보고가 없으면 0이다. JVM이 보고한 교착 개수이며 스레드 수와는 다르다. jcmd가 실행되지 않거나 10초 안에 성공하지 않으면 실험 오류다.

[11] 합격 조건은 requested가 해당 시나리오의 총 요청 수와 같고 completed=requested이며 stalled=0, bad_responses=0, balance_errors=0, deadlock_cycles=0인 것이다. 모든 요청의 완료와 정확한 잔액을 함께 만족해야 한다. 각 시나리오를 세 번 연속 통과하라. 시작 코드의 실패를 관찰한 뒤 서버를 종료하고 수정한 코드로 다시 실행하라. 수동 관찰을 마쳤으면 curl과 서버도 종료하라.

[12] 수정 범위는 `src/main/kotlin/AccountStore.kt`뿐이다. 계좌별 ReentrantLock 두 개를 모두 보유한 상태에서 출금과 입금을 수행하라. 성공 여부와 무관하게 획득한 잠금을 반드시 해제하라. Main.kt의 HTTP 계약, 플랫폼 작업 스레드, FirstLockWindows와 그 호출, 초기 잔액, snapshot, 빌드·실행 파일은 바꾸지 마라. 전역 잠금으로 직렬화하거나 잠금을 없애는 방식은 이 단계의 수정 범위에 들지 않는다.

[13] 공식 근거는 [JDK 21 jcmd 문서](https://docs.oracle.com/en/java/javase/21/docs/specs/man/jcmd.html)와 [JDK 21 ReentrantLock API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/locks/ReentrantLock.html)다. jcmd는 대상 JVM과 같은 기계에서 같은 유효 사용자·그룹으로 실행해야 한다. Thread.print는 스레드와 스택을 출력하며 -l은 java.util.concurrent 잠금 정보를 추가한다. ReentrantLock의 tryLock은 잠금을 즉시 얻을 수 없으면 false를 반환하고, 획득한 잠금은 finally에서 unlock하는 패턴으로 해제한다. 덤프는 한 시점의 관찰이므로 보고된 교착이 없다는 사실만으로 모든 요청의 진행과 이체 반영을 보장할 수는 없다.
