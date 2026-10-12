# JVM

JVM 이 메모리, 스레드, 이름 해석을 다루는 기본 동작 가운데 서버 운영에서 문제가 되는 것을 실험으로 익힌다. 모든 실험은 JDK 21 만으로 돌아간다. Spring 위에서의 스레드 풀과 실행기는 [../spring/README.md](../spring/README.md) 의 '스레드와 실행기' 절에 있다.

## 1. 개념 지도

- 힙 크기를 정하지 않으면 최대 힙은 물리 메모리의 25%(`-XX:MaxRAMPercentage` 기본 25.0)다. 리눅스에서는 컨테이너 지원(`-XX:+UseContainerSupport`)이 기본으로 켜져 있어 컨테이너의 메모리 한도를 물리 메모리로 간주한다.
- 이름 해석 캐시: 보안 관리자가 없으면 성공한 조회는 구현이 정한 유한한 시간 동안, 실패한 조회는 10초(`networkaddress.cache.negative.ttl`) 동안 캐시한다. 하위 서비스 주소가 바뀌어도 캐시가 끝날 때까지 옛 주소로 간다.
- 스레드 풀의 스레드는 재사용되므로 ThreadLocal 값을 지우지 않으면 다음 작업에 남는다.
- JIT 는 자주 실행되는 코드를 실행 중에 컴파일한다. 예열 전에 잰 성능은 낮게 나오고 측정하는 코드가 결과를 쓰지 않으면 계산이 사라지기도 한다.
- G1 은 영역 크기의 절반 이상인 객체를 humongous 객체로 따로 다룬다. 카드를 만들 때 Oracle 의 G1 튜닝 문서로 확인한다.
- 스레드 덤프(`jcmd <pid> Thread.print`)는 스레드마다 상태와 잡고 있거나 기다리는 잠금을 보여 준다. 교착은 덤프 끝에 따로 보고된다.

## 2. 학습 경로

L-jvm-10~L-jvm-40 은 lab 종류다. lab 엔진 작업([../lab.md](../lab.md))이 먼저다. 추가 도구는 필요 없다.

## 3. 아이디어

| 아이디어 | 종류 | 메모 |
|---|---|---|
| 할당률과 GC 일시 정지 | lab | 짧게 사는 객체가 많을 때와 오래 사는 객체가 늘 때 |
| 직접 버퍼와 힙 밖 메모리 | lab | 컨테이너 한도를 넘는 원인 |
| 코루틴 취소와 블로킹 | lab | 의존성 결정 뒤. 블로킹 호출이 디스패처를 막는 경우 |
| glibc malloc 과 RSS | lab | 스레드마다 생기는 arena 가 해제한 메모리를 OS 에 돌려주지 않아 RSS 가 힙과 직접 버퍼를 합친 크기보다 크게 느는 경우. `MALLOC_ARENA_MAX`, `MALLOC_MMAP_THRESHOLD_` 를 정하면 임계값의 동적 조정이 멈추는 문제, Native Memory Tracking 합계와 RSS 의 차이. glibc 를 쓰는 Linux 컨테이너가 필요하다 |
| 컨테이너의 CPU 수 | lab | CPU 한도가 없는 컨테이너에서 `availableProcessors()` 가 노드의 코어 수를 돌려줘 GC 스레드와 스레드 풀이 커지는 경우, `-XX:ActiveProcessorCount`. JDK 19 부터 CPU 수를 셀 때 `cpu.shares` 를 쓰지 않는다(17.0.5, 11.0.17 에도 반영). Linux 컨테이너가 필요하다 |
| C1 전용 JIT 와 code cache | lab | 큰 앱에서 code cache 가 가득 차 JIT 컴파일이 멈추는 경우, `-XX:ReservedCodeCacheSize`. `-XX:TieredStopAtLevel=1` 이면 JDK 21 의 code cache 예약이 240MiB 에서 48MiB 로 준다(JDK 11 은 240MiB 그대로, 2026-10-12 측정) |

## 4. 출처

- The java Command(JDK 21): https://docs.oracle.com/en/java/javase/21/docs/specs/man/java.html
- JDK-8281181(Do not use CPU Shares to compute active processor count): https://bugs.openjdk.org/browse/JDK-8281181
- mallopt(3)(M_ARENA_MAX, M_MMAP_THRESHOLD 와 동적 임계값): https://man7.org/linux/man-pages/man3/mallopt.3.html
- InetAddress(JDK 21) 의 캐싱 절: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/net/InetAddress.html
- Oracle, HotSpot Virtual Machine Garbage Collection Tuning Guide 의 G1 장.
