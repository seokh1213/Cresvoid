# 메모리 한도와 기본 힙 크기

[1] 이 단계는 최대 힙·현재 확보한 힙·직접 메모리를 구분하는 입문 observe 실험이다. 구현 수정량은 작으며, 목표는 설정과 실제 할당 결과로 측정값의 의미를 검증하는 것이다. 먼저 최대 힙과 배열 할당 결과를 예측하고, 시작 코드를 실행해 지표와 예측을 비교해라. 그 뒤 관찰 코드의 잘못된 측정을 고쳐라. 처리량이나 GC 성능을 높이는 단계가 아니다. JDK 21 HotSpot과 Kotlin 표준 라이브러리만 사용한다.

[2] 모든 크기는 MiB로 표시하며 1 MiB는 1,048,576바이트다. 여기서 한도는 `-XX:MaxRAM`으로 전달하는 힙 크기 계산용 가상 메모리 예산이다. 이 옵션은 프로세스 RSS를 제한하거나 컨테이너를 만들지 않는다. 운영체제의 컨테이너 메모리 제한을 넘었을 때 일어나는 강제 종료는 이 실험에서 재현하지 않는다.

[3] `-Xmx`를 생략하면 HotSpot은 가용 메모리와 `MaxRAMPercentage` 등으로 최대 힙을 정한다. `MaxRAMPercentage`의 기본값은 25다. 따라서 예산 1,024 MiB에서 기본 설정의 명목 최대 힙은 256 MiB이고, 50을 지정하면 512 MiB다. 작은 힙에는 `MinRAMPercentage`가 적용될 수 있고 수집기와 정렬도 영향을 주므로 모든 환경에서 단순 곱셈이 정확한 바이트 수를 주지는 않는다. 이번 실험은 G1, 영역 크기 1 MiB, 초기 힙 16 MiB를 고정한다.

[4] 최대 힙은 JVM이 사용하려고 시도할 수 있는 힙의 상한이다. 현재 확보한 힙과 그 안의 여유는 별개의 값이며, 할당이나 GC에 따라 달라질 수 있다. 측정값이 실제 할당의 성공·실패를 설명하는지 비교하고 Runtime API에서 각 값의 의미를 확인해라. `-Xmx128m`처럼 최대 힙을 직접 지정하면 이 실험에서는 예산이나 백분율보다 그 값이 우선한다. 초기 힙이 작아도 최대 힙이 작은 것은 아니다.

[5] 한 번의 관찰은 새 JVM에서 시작한다. 요청한 크기의 `ByteArray` 하나를 만들고 이어서 `ByteBuffer.allocateDirect`로 직접 버퍼 하나를 만든다. 두 객체는 응답의 측정이 끝날 때까지 함께 보관한다. 각 할당에서만 `OutOfMemoryError`를 잡아 해당 실패를 기록하고 서비스는 응답을 마쳐라. 힙 할당이 실패해도 직접 버퍼 할당을 시도한다. JVM이나 서비스 전체의 장애를 할당 실패로 바꾸지 마라. 배열을 여러 조각으로 나누거나 요청 크기를 줄이지 마라.

[6] 배열의 데이터 크기는 최대 힙 전체를 사용할 수 없다. 객체 헤더, 다른 살아 있는 객체와 G1의 영역 배치에도 공간이 필요하기 때문이다. 이 실험은 정확한 OOM 경계를 찾는 대신 경계에서 충분히 떨어진 크기를 비교한다. 최대 힙보다 큰 배열은 실패해야 하며, 아래에 공개한 작은 배열은 성공해야 한다. OOM이 발생한 정확한 배열 크기를 모든 JVM의 보편적인 한계로 일반화하지 마라.

[7] 직접 버퍼는 일반적인 GC 힙 밖에 저장될 수 있다. `MaxDirectMemorySize`는 NIO 직접 버퍼 할당의 별도 상한이다. `budget_exceeded`는 성공해서 함께 보관 중인 배열의 데이터 바이트와 직접 버퍼 용량을 더한 값이 가상 예산보다 클 때만 1이다. 이는 실제 RSS 측정이 아니라 메모리 사용량의 보수적인 하한 비교다. 합계가 예산 이하라고 프로세스가 안전하다고 결론 내리지 마라. 메타스페이스, 코드 캐시, 스레드 스택 등도 메모리를 쓴다.

[8] `run.sh`는 `PORT` 환경 변수의 포트에서 `127.0.0.1`에만 HTTP 서비스를 열어라. `GET /health`는 HTTP 200과 `ready` 한 줄을 반환한다. `GET /probe?heapMiB=H&directMiB=D&budgetMiB=B`는 HTTP 200과 다음 여덟 필드를 순서와 무관하게 한 줄씩 `이름=정수`로 반환한다. 필드 이름은 `max_heap_bytes`, `heap_requested_bytes`, `heap_retained_bytes`, `direct_requested_bytes`, `direct_retained_bytes`, `heap_oom`, `direct_oom`, `budget_exceeded`다. 중복이나 추가 필드는 허용하지 않는다. 바이트 필드는 음이 아닌 Long이며 세 판정 필드는 0 또는 1이다.

[9] H는 0~768, D는 0~384, B는 256~1,024의 정수다. 이 입력 범위는 할당 성공을 보장하는 범위가 아니다. 특히 D가 직접 버퍼 상한 이하여도 성공을 보장하지 않는다. 공개 성공 조건 밖의 경계 요청은 실제 할당 결과로 해석하고, 원인을 확인하지 않은 실패를 특정 구성 요소 탓으로 단정하지 마라. 요청 바이트는 각각 H와 D에 1,048,576을 곱한 값이다. 할당 성공 시 보관 바이트는 요청 바이트와 같고 해당 OOM은 0이다. 할당 실패 시 보관 바이트는 0이고 해당 OOM은 1이다. 크기 0도 성공이며 보관 바이트는 0이다. 잘못된 경로나 HTTP 메서드는 각각 404, 405로, 누락·중복·범위 밖의 매개변수는 400으로 응답해라. B는 실험기가 지정한 MaxRAM과 같은 값이며 응답의 예산 비교에만 사용한다.

[10] 부하는 직렬 관찰 요청 12개다. 각 요청마다 새 서비스 JVM을 띄워 이전 요청의 객체나 GC 상태가 다음 관찰에 섞이지 않게 한다. 공통 JVM 옵션은 `-XX:+UseG1GC -XX:G1HeapRegionSize=1m -XX:ActiveProcessorCount=2 -Xms16m -XX:MaxDirectMemorySize=384m`다. 아래에서 직접 버퍼 상한을 따로 지정하면 공통값을 대체한다. 백분율이 기본값인 경우 `MaxRAMPercentage` 옵션 자체를 전달하지 않는다. 실험기는 서비스를 시작하고 준비를 확인한 뒤 100ms가 지난 시각에 관찰 요청을 보낸다. 준비 대기는 최대 20초, 개별 HTTP 요청은 최대 15초이며 이 시간은 오류 감지를 위한 안전장치다. 응답 속도나 처리량을 합격 지표로 사용하지 않는다.

[11] 예산 1,024 MiB·기본 백분율에서 H=32와 H=384를 각각 관찰한다. 최대 힙 기대값은 두 번 모두 256 MiB이며 배열은 각각 성공과 힙 OOM이어야 한다. 예산 1,024 MiB·백분율 50에서는 H=32, H=320, H=640을 각각 관찰한다. 최대 힙 기대값은 512 MiB이며 배열은 차례로 성공, 성공, 힙 OOM이어야 한다. 이 다섯 요청의 D는 0이다.

[12] 예산 512 MiB·백분율 50에서는 H=32와 H=320을 각각 관찰한다. 최대 힙 기대값은 256 MiB이며 배열은 각각 성공과 힙 OOM이어야 한다. 예산 1,024 MiB·백분율 50에 `-Xmx128m`을 추가하면 H=32는 성공하고 H=192는 힙 OOM이어야 한다. 이 두 요청의 최대 힙 기대값은 128 MiB다. 이 네 요청의 D는 0이다.

[13] 예산 256 MiB·백분율 50에서 H=80·D=192와 H=16·D=0을 각각 관찰한다. 최대 힙 기대값은 128 MiB이며 두 할당은 모두 성공해야 한다. 첫 요청은 데이터만 272 MiB를 보관하므로 `budget_exceeded=1`이고 둘째 요청은 0이다. 같은 예산·백분율에서 직접 버퍼 상한만 `-XX:MaxDirectMemorySize=16m`으로 바꾸고 H=16·D=32를 관찰하면 배열은 성공, 직접 버퍼는 OOM, 보관 합계는 16 MiB, `budget_exceeded=0`이어야 한다.

[14] 합격하려면 관찰 응답 12개가 모두 계약을 지켜야 한다. 각 `max_heap_bytes`는 해당 기대값에서 8 MiB 이내여야 하며 요청·보관 바이트, 할당 실패 구분, 예산 초과 판정이 전부 맞아야 한다. 집계는 응답 오류 `response_failures`, 최대 힙 오측정 `heap_mismatches`, 할당 결과 불일치 `allocation_mismatches`, 예산 판정 불일치 `budget_mismatches`를 쓰며 네 값이 모두 0이어야 한다. 실패한 응답은 나머지 비교에서 빼고 응답 오류로 센다. 유효한 응답의 힙 OOM은 총 4건, 직접 버퍼 OOM은 1건, 예산 초과는 1건이어야 한다.

[15] 출력 지표는 `requests=12`, 네 불일치 개수, `heap_oom_count`, `direct_oom_count`, `budget_exceeded_count`, `default_max_heap_mib`, `configured_max_heap_mib`의 열 개다. 마지막 두 값은 각각 [11]의 첫 요청과 세 번째 요청에서 관찰한 최대 힙을 MiB로 환산한 값이며 정상 기대값은 256과 512다. 그 응답이 무효이면 해당 값은 0이다. 실험기는 출력에 고유한 이름의 `METRIC 이름 유한한숫자`만 쓰며 불변식 통과는 종료 코드 0, 불변식 실패는 1, 실험기 자체 오류는 2로 구분한다.

[15.1] 집계 지표는 개별 응답을 보여 주지 않는다. 어느 조건이 다른지 찾으려면 작업 공간에서 `./build.sh`를 실행한 뒤 공개 조건 하나만 새 JVM으로 재현해라. 예를 들어 아래 첫 명령을 한 터미널에서 실행하고, 다른 터미널에서 `/health`가 `ready`를 반환할 때까지 확인해라. 준비 뒤 100ms 이상 기다리고 `/probe` 요청을 한 번 보내 여덟 필드를 직접 읽어라. 아래 예시는 [11]의 H=320 조건이다. 다른 조건은 [10]의 공통 옵션을 유지하며 예산·백분율·명시적 최대 힙·직접 버퍼 상한과 H·D·B를 함께 맞춰라. 기본 백분율을 재현할 때는 백분율 옵션을 빼라. 관찰이 끝나면 서버 터미널에서 Ctrl+C를 누르고 프로세스가 끝난 뒤 다음 조건을 새 JVM으로 시작해라. 18921 포트가 사용 중이면 두 터미널의 포트를 같은 빈 포트로 바꿔라.

```sh
env -u JDK_JAVA_OPTIONS -u _JAVA_OPTIONS PORT=18921 JAVA_TOOL_OPTIONS='-XX:+UseG1GC -XX:G1HeapRegionSize=1m -XX:ActiveProcessorCount=2 -Xms16m -XX:MaxDirectMemorySize=384m -XX:MaxRAM=1024m -XX:MaxRAMPercentage=50' ./run.sh
```

```sh
curl --max-time 15 --fail 'http://127.0.0.1:18921/health'
sleep 0.1
curl --max-time 15 --fail 'http://127.0.0.1:18921/probe?heapMiB=320&directMiB=0&budgetMiB=1024'
```

[16] 시작 코드의 `src/main/kotlin/Measurement.kt`에서 최대 힙 측정과 예산 계산만 고쳐라. HTTP 처리와 실제 할당·보관·OOM 포착은 관찰 장치이므로 유지해라. `Main.kt`, 빌드·실행 파일, JVM 옵션, 요청 크기와 실험기는 바꾸지 마라. 응답 상수나 예측값을 돌려주는 대신 실행 중인 JVM과 실제 성공한 할당에서 측정해라. 새 의존성을 추가하지 마라.

[17] 근거는 JDK 21의 [The java Command](https://docs.oracle.com/en/java/javase/21/docs/specs/man/java.html)의 `MaxRAM`, `MaxRAMPercentage`, `MinRAMPercentage`, `-Xmx`, `MaxDirectMemorySize` 설명과 [Runtime API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Runtime.html)의 메모리 측정 메서드, [ByteBuffer API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/ByteBuffer.html)의 Direct buffers 절이다. 실제 컨테이너 한도와 힙 이외 메모리를 구분한 뒤 실험 결과를 해석해라.
