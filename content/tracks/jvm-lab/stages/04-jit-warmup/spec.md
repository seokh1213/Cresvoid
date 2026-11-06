# JIT 예열과 측정 함정

[1] 이 단계는 observe 실험이다. 먼저 시작 코드에서 첫 1,000회와 이후 평균 시간의 비율, 결과를 버린 루프의 시간, A와 B의 측정 순서를 뒤집었을 때의 변화를 예측해라. 문항을 닫은 뒤 실험하고 실제 지표와 비교해라. 마지막으로 측정 절차를 고쳐 비교할 수 있는 기록을 만들어라. JDK 21 HotSpot과 Kotlin 표준 라이브러리만 사용한다.

[2] HotSpot은 실행 중 자주 쓰는 코드를 컴파일하며 계층형 컴파일과 인라이닝 등으로 같은 소스의 실행 비용이 바뀔 수 있다. 첫 구간에는 해석 실행, 클래스 초기화, 컴파일, OS 스케줄링 등이 섞일 수 있다. 예열 횟수를 채웠다고 최적화 완료가 보장되지는 않는다. 첫 1,000회의 평균이 이후보다 반드시 몇 배 느려야 한다는 정답은 없다.

[3] `Kernels.kt`의 A와 B는 같은 Int 변환을 서로 다른 반복문으로 계산한다. 입력 x에 대해 64회 `x = (x xor (x ushr 13)) * 1664525 + 1013904223`을 수행한 결과를 반환한다. 모든 연산은 Kotlin Int의 32비트 넘침을 그대로 쓴다. 배치의 i번째 호출에는 `seed+i`를 전달한다. 결과 소비 배치는 각 반환값을 부호 있는 Long으로 바꿔 더하고, 타이머를 멈춘 뒤 volatile sink에 합계를 기록한다. 응답에는 이 sink에서 읽은 합계를 담는다. 출력·HTTP·volatile 쓰기는 측정 구간 밖에 있고 결과 합산은 측정 구간 안에 있다.

[4] 결과 폐기 배치는 같은 함수를 같은 횟수만큼 호출하되 반환값을 합산하거나 저장하지 않는다. 그 배치의 checksum은 0이다. JIT는 관찰 가능한 효과가 없는 계산을 제거할 수 있다. 폐기 배치가 짧게 나와도 필요한 계산을 빠르게 수행했다고 해석하지 마라. 합산 유무도 두 루프의 차이이므로 시간 비율 하나만으로 제거 여부를 확정하지 마라. 이 작은 실험을 일반적인 마이크로벤치마크의 대체물로 쓰지 마라.

[5] `./build.sh`는 `build/app.jar`를 만들고 `PORT=18084 ./run.sh`는 지정한 루프백 포트에서 서비스를 시작한다. `GET /health`는 쿼리를 무시하고 HTTP 200과 `ready` 한 줄을 반환하며 계산 커널을 호출하지 않는다. `GET /observe?seed=S&order=O`는 HTTP 200으로 배치 기록을 반환한다. S는 1~100의 정수이고 O는 `abba` 또는 `baab`다. 관찰은 JVM마다 한 번만 허용하며 이후 유효 관찰 요청은 409와 `USED`를 반환한다. 알려진 경로의 다른 메서드는 405와 `METHOD`, 없는 경로는 404와 `NOT_FOUND`다. `/observe`의 쿼리에서 누락·중복·알 수 없는 키·범위 밖 값은 400과 `INVALID`다. 쿼리는 URL 인코딩 없는 키=값을 &로 연결하는 형식만 다룬다.

[6] 응답은 실행 순서대로 한 배치당 한 줄이다. 형식은 `phase|candidate|calls|retention|checksum|elapsed_ns`이며 공백과 헤더는 없다. phase는 `cold`, `warmup`, `discard`, `measure` 중 하나, candidate는 `A` 또는 `B`, retention은 `USED` 또는 `DISCARDED`다. calls는 실제 실행을 요청한 양의 반복 횟수, checksum은 부호 있는 Long, elapsed_ns는 두 System.nanoTime 호출의 차인 양의 Long이다. 배치 기록은 고정 관찰 장치인 MeasurementRig가 실제 실행에서 만들며, 별도로 만들어 끼우거나 시간·합계·횟수를 상수로 반환하지 마라.

[7] 합격 기록은 정확히 열 줄이다. 먼저 cold A와 cold B를 각각 1,000회 실행하고, warmup A와 warmup B를 각각 60,000회 실행해라. 이어 discard A와 discard B를 각각 500,000회 실행해라. 마지막 네 measure 배치는 각각 500,000회이며 order=abba이면 A,B,B,A, order=baab이면 B,A,A,B 순서다. discard 두 배치만 DISCARDED이고 나머지는 모두 USED여야 한다. cold는 각 커널의 첫 1,000회이며 그 전에 커널을 호출하지 마라. 예열은 측정 배치에 합치지 마라. A와 B가 앞쪽·뒤쪽 위치를 대칭으로 차지하도록 순서를 지켜라.

[8] `first-window`는 seed=17·order=abba, `second-window`는 seed=29·order=baab로 실행한다. 각 시나리오는 새 서비스 JVM을 사용한다. 준비를 확인한 뒤 100ms 시점에 관찰 요청 하나를 예정하고 공용 개방형 부하 도구로 보낸다. 완료 시간으로 다음 도착 시각을 정하지 않는다. 준비는 최대 30초, 관찰 요청은 최대 30초이며 전체 실험 제한은 120초다. 이 시간은 멈춘 프로그램을 검출하는 안전장치이고 성능 합격 기준은 아니다. JVM 옵션은 `-Xms64m -Xmx256m -XX:ActiveProcessorCount=2 -XX:+TieredCompilation`으로 고정한다. 다른 Java 옵션 환경 변수는 실험에서 비운다.

[9] 합격하려면 응답 오류, 배치 절차 오류, 결과 소비 오류가 모두 0이어야 한다. 응답 오류는 상태·줄 수·필드 형식·0 이하 시간의 오류를 합쳐 응답당 한 건으로 센다. 유효한 응답에서 배치 절차 오류는 위치별 phase·candidate·calls가 [7]과 다른 줄 수다. 결과 소비 오류는 retention이나 checksum이 해당 줄의 실제 candidate·calls와 [3]·[4]에서 유도한 값과 다른 줄 수다. 응답 오류가 있으면 나머지 두 오류는 집계하지 않는다. 모든 USED 배치의 checksum을 검증하므로 결과를 버린 채 횟수만 맞추는 구현은 실패한다. 각 시나리오를 세 번 실행해 통과해라.

[10] 지표는 `response_errors`, `procedure_errors`, `consumption_errors`, `cold_warm_ratio`, `discard_used_ratio`, `a_b_ratio`, `a_position_ratio`, `b_position_ratio`의 여덟 개다. cold_warm_ratio는 첫 두 줄의 호출당 평균 시간의 산술평균을 마지막 네 measure 배치의 호출당 평균 시간의 산술평균으로 나눈 값이다. discard_used_ratio는 다섯째·여섯째 줄의 호출당 평균의 산술평균을 같은 measure 평균으로 나눈 값이다. a_b_ratio는 measure A 두 배치의 호출당 평균의 산술평균을 B의 값으로 나눈 것이다. 두 position_ratio는 각 후보의 첫 measure 시간/두 번째 measure 시간이다. 응답 오류가 있거나 마지막 네 줄이 A·B 각각 두 개의 measure 배치가 아니면 다섯 비율은 0으로 표시한다. 나머지 경우에는 오류가 있어도 실제 기록의 비율을 보여 준다. 먼저 세 오류 지표를 읽어라. 소비 오류가 있으면 discard_used_ratio의 분모도 결과를 소비하지 않았을 수 있고, 예열·순서 오류가 있으면 각 비율의 비교 조건도 어긋난다. 비율은 관찰용이며 합격 임계값이 없다. 역순 실행끼리 a_b_ratio를 비교하되 새 JVM 사이의 환경 차이도 함께 고려해라. 시간 비율이 1의 어느 쪽에 있는지만으로 우열이나 JIT 완료를 단정하지 마라.

[11] 작업 공간에서 빌드한 뒤 수정 전후의 원시 기록을 수동으로 비교해라. [8]의 JVM 옵션을 JAVA_TOOL_OPTIONS로 지정하고 `PORT=18084 ./run.sh`로 시작해라. 다른 터미널에서 `curl --fail http://127.0.0.1:18084/health`로 준비를 확인하고 100ms 이상 기다린 뒤 `curl --max-time 30 --fail 'http://127.0.0.1:18084/observe?seed=17&order=abba'`로 열 줄을 받아라. 관찰 뒤 서버를 Ctrl+C로 종료해라. 매번 새 JVM을 시작하는 같은 절차로 seed=17·order=baab도 관찰하고, 두 order를 번갈아 각각 세 번 실행해라. 입력을 고정해 순서 비교에서 seed 차이를 없애되, 새 JVM 사이의 컴파일 상태·스케줄링 등 환경 차이는 여전히 남는다고 해석해라. 수정 전후에는 세 오류 지표와 measure의 retention을 먼저 대조하고, 그 뒤 discard_used_ratio의 비교 대상이 어떻게 달라졌는지 설명해라. [8]의 자동 시나리오는 seed까지 다르므로 그 비율 차이를 순서 효과만으로 설명하지 마라. 포트가 사용 중이면 서버와 curl 명령을 같은 빈 포트로 바꿔라.

[12] 수정 범위는 `src/main/kotlin/Benchmark.kt`의 배치 구성과 결과 소비 선택뿐이다. Main.kt, Kernels.kt, MeasurementRig.kt, 빌드·실행 파일과 JVM 옵션은 유지해라. MeasurementRig.sample을 통해 실제 배치를 실행하고 반환된 기록을 순서대로 돌려줘라. 커널 변경, 임의 대기, 타이머 조작, 미리 구한 결과 반환, 별도 커널 호출로 예열을 숨기는 방식은 허용하지 않는다. 새 의존성을 추가하지 마라.

[13] 공식 근거는 JDK 21 [The java Command](https://docs.oracle.com/en/java/javase/21/docs/specs/man/java.html)의 Advanced JIT Compiler Options, `-XX:+PrintCompilation`, `-XX:-TieredCompilation`, `-Xbatch`, `-Xint` 설명과 [System.nanoTime API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/System.html#nanoTime())다. nanoTime은 같은 JVM에서 경과 시간을 구할 때 차로 사용하며 나노초 단위가 나노초 해상도를 보장하지는 않는다. 필요하면 자동 채점 밖의 새 JVM에서 PrintCompilation으로 컴파일 시점을 관찰해라. 진단 옵션은 출력과 실행에 영향을 줄 수 있으므로 해당 실행의 시간을 기존 실행과 직접 비교하지 마라.
