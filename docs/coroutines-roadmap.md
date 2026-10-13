# 코루틴 현황과 완성 계획

2026-10-13 기준이다. 기존 콘텐츠 품질 보강, `coroutine-patterns`의 05·06, `coroutine-runtime`의 01~06을 제작했다. lab 기반·스킬·디스패처 실험은 후속 계획이다. Kotlin 언어 전체의 버전별 문제 트랙은 [별도 계획](../content/ideas/implementation/kotlin-versions.md)에 둔다.

## 현재 콘텐츠

코루틴 카드는 8장이다. 완료 카드 7장에 실제 콘텐츠 16단계가 있다. 남은 1장은 lab 실험 후보 카드이며 실제 학습 단계로 세지 않는다.

- [R-coroutines-10](../content/ideas/backend/coroutines/cards/R-coroutines-10-coroutines.md): `tech-reading/06-coroutines`. 중단과 상태 기계, 구조적 동시성, 취소와 예외 전파를 읽는다.
- [B-coroutines-20](../content/ideas/backend/coroutines/cards/B-coroutines-20-coroutine-patterns.md): `coroutine-patterns/01-fan-out`, `02-limits`, `03-channels`, `04-flow`. 동시 호출·시간 제한, 실행 상한·재시도, Channel 파이프라인, Flow·StateFlow를 구현한다.
- [B-coroutines-35](../content/ideas/backend/coroutines/cards/B-coroutines-35-coroutine-patterns-advanced.md): `coroutine-patterns/05-batching`, `06-single-flight`. 묶음 저장과 요청 합치기·오래된 캐시 갱신을 구현한다.
- [C-coroutines-30](../content/ideas/backend/coroutines/cards/C-coroutines-30-coroutine-order.md): `code-reading/02-coroutine-order`. 실행 순서·구조적 동시성·취소를 코드로 추적한다.
- [F-coroutines-31](../content/ideas/backend/coroutines/cards/F-coroutines-31-order-deadline.md): `bug-fix/02-order-deadline`. 하위 호출 시간 제한의 버그를 고친다.
- [C-coroutines-32](../content/ideas/backend/coroutines/cards/C-coroutines-32-flow-semantics.md): `code-reading/03-flow-semantics`. StateFlow·SharedFlow의 전달·수집 의미를 추적한다.
- [B-coroutines-40](../content/ideas/backend/coroutines/cards/B-coroutines-40-coroutine-runtime.md): `coroutine-runtime/01-event-loop`~`06-flow`. 실제 `suspend`와 `Continuation`으로 작은 실행기를 단계별로 만든다.

## 완료한 품질 보강

- 코드 읽기: 수정된 네 문항을 정답 없이 다시 풀어 모두 일치함을 확인했다. 03 안내에는 실제 `Dispatchers.IO` 대신 같은 가상 시계의 테스트 디스패처를 주입한다고 명시했다.
- 버그 수정: 02의 수정 대상을 처리기 파일로 한정하고 엔진이 다른 소스·실행 틀 변경을 거절하게 했다. 독립 수정본은 마감 시각 경계를 고친 뒤 숨은 테스트 10개를 통과했다.
- 활용 구현: 02·03의 독립 검토는 각각 숙련자 기준 약 3~5시간으로 추정했다. 이전의 1~1.5시간 추정과 달라 실제 학습 시간은 아직 측정되지 않았다. 03은 결과 채널이 무제한이라 저장이 늦으면 결과가 쌓일 수 있음을 적었다.
- 04 경계: 종료 타이머를 먼저 등록해 시각 0에 시작한 WATCH의 첫 처리 완료와 종료가 겹쳐도 종료가 우선하게 했다. 입력 제외 조건 하나를 없애고 고정 테스트를 더했다.
- 개념 독해: 수정된 세 문항을 지문만 보고 다시 풀어 모두 정답과 일치함을 확인했다.

문항의 독립 풀이와 실행 결과는 로컬 작업 기록에 남겼다.

## 제작한 활용 심화 05·06

[B-coroutines-35](../content/ideas/backend/coroutines/cards/B-coroutines-35-coroutine-patterns-advanced.md)를 기존 트랙 뒤에 더했다. 참조 풀이와 독립 테스트·문항을 교차 검증했고 01~04를 함께 회귀 검증했다. 스펙만 보고 작성한 별도 풀이도 05·06의 숨은 테스트 31건을 모두 통과했다. 두 단계 모두 숙련자 기준 2~3시간을 목표로 설계했으며 실제 학습 시간은 아직 측정되지 않았다.

### 05-batching

N개 또는 첫 이벤트부터 T시간이 되면 묶음을 닫고 순서대로 저장한다. 저장 중 대기 묶음 수를 제한해 역압력을 적용하고 정상 완료 시 남은 이벤트를 저장한다. 이벤트·타이머 경합, 실패, 취소 정책을 스펙에 명시한다. 타이머와 select 등 여러 정답 구현을 허용한다.

### 06-single-flight

같은 상품 요청은 하위 호출 하나를 공유한다. 기다리는 요청 하나가 취소돼도 다른 요청의 호출은 유지하고, 모두 사라지면 취소한다. 실패는 현재 대기자에게만 전달하고 캐시하지 않는다. 오래된 캐시 값은 즉시 반환하며 뒤에서 갱신 하나만 실행한다. 일반 대기 호출과 백그라운드 갱신의 생명주기를 구분하고 만료·갱신 실패의 경계를 정의한다.

학습자 코드는 kotlinx.coroutines 1.11.0의 안정 API만 사용한다. 05는 고정 테스트 12개와 생성 테스트 2개, 06은 고정 테스트 13개와 생성 테스트 2개를 통과했다. 06의 상품 20,000개·요청 80,000개 생성 입력은 참조 풀이에서 약 1초가 걸렸다(제한 15초).

## 제작한 실행기 01~06

[B-coroutines-40](../content/ideas/backend/coroutines/cards/B-coroutines-40-coroutine-runtime.md)는 표준 라이브러리와 JDK 21만 사용하는 단일 스레드 가상 시간 실행기다. 각 단계는 앞 단계 위에 쌓이며 실제 `suspend`, `startCoroutine`, `suspendCoroutine`, `Continuation`으로 중단과 재개를 구현한다.

1. `01-event-loop`: 준비 큐, 가상 지연, 자식 시작과 결과 대기.
2. `02-structured`: 부모·자식 완료, 지연 시작, `join`과 `scope`, 문맥 상속과 상태.
3. `03-cancellation`: 협력적 취소, `yield`, 정리와 취소 불가 지연, 시간 제한과 완료 처리기.
4. `04-exceptions`: 실패 전파와 감독, `await` 예외, 루트 처리기.
5. `05-channels`: 랑데부·버퍼 Channel의 송수신·닫힘과 Mutex 대기 순서.
6. `06-flow`: 수집할 때마다 새로 실행하는 cold Flow, `map`·`filter`·`take`와 상류 정리.

선행 시퀀스 생성기를 추가하면 일곱 단계가 되지만 현재 트랙에는 넣지 않았다. 실제 스레드 기반 디스패처, `select`, SharedFlow·StateFlow의 내부 구현도 이 트랙 범위가 아니다.

## 후속 계획: lab 기반과 디스패처 실험

[L-coroutines-50](../content/ideas/backend/coroutines/cards/L-coroutines-50-dispatcher-starvation.md)은 `coroutine-lab` 후보 카드다. 현재 엔진은 build·reading·code·fix만 지원한다. [lab 설계 초안](../content/ideas/backend/lab.md)에 맞춰 FORMAT·엔진·제작 스킬을 먼저 만든 뒤 실험을 제작한다. 이번에는 구현하지 않는다.

JDK HttpServer와 k6를 사용해 Default에서 블로킹하는 가짜 JDBC 호출을 관찰한다. IO·limitedParallelism·Semaphore로 개선하며 별도 경로의 p95와 블로킹 동시 실행 상한을 검사한다. 정확한 임계값과 observe·implement를 한 단계로 묶을지는 아직 정하지 않았다. Redis는 이 실험에 필요하지 않다.

## 보관한 추가 아이디어

도메인 README의 카드 밖 아이디어는 다섯 개다. 별도 제작 승인을 뜻하지 않는다.

- Channel 생산자·소비자 코드 읽기: rendezvous, buffer, close와 반복 종료.
- runTest와 가상 시간 코드 읽기.
- 가상 스레드와 코루틴 비교 실험.
- Flow 역압력 실험: buffer, conflate, collectLatest.
- `07-keyed-workers`: 키별 순서·작업자 상한·종료 기한·미처리 보고. 02·03과 작업자·정리 부분이 겹쳐 활용 심화에서 제외한 안이다. 새 07단계가 확정된 상태가 아니다.

INDEX.md의 '다음 번호 07'은 예정 06 뒤의 빈 번호다. 보관한 keyed-workers나 실행기 확장 단계 수와 혼동하지 마라.

## 다른 도메인과의 연결

- JVM의 코루틴 취소·블로킹 실험은 디스패처 실험과 함께 범위를 검토한다.
- [부하 도메인](../content/ideas/backend/load/README.md)의 캐시 스탬피드·분산 잠금은 06의 프로세스 안 요청 공유와 다르다. single-flight가 Redis 분산 잠금을 대신하지 않는다.
- [메시징](../content/ideas/backend/messaging/README.md)의 poll 묶음 변경 합치기·오프셋 커밋은 05의 묶음 저장과 별도 의미 규칙이다.
- Kotlin 재시도 정책 카드는 계산·지터·오류 분류를 다루며 코루틴 취소 검증을 대신하지 않는다.

## 범위 계산

현재 실제 학습 콘텐츠는 기존 8단계 + 활용 심화 2단계 + 실행기 6단계 = 16단계다. lab 후보 1단계를 만들면 17단계, 선택 시퀀스 생성기까지 만들면 18단계다. lab을 두 단계로 나누면 하나 늘어난다. 카드 밖 아이디어는 포함하지 않는다.

다음 제작 범위는 lab 기반과 실험, 추가 아이디어 순으로 다시 정한다.
