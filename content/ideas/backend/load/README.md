# 부하 엣지 케이스

부하가 걸린 백엔드에서 생기는 엣지 케이스를 예상하고 견디는 코드를 쓰는 힘을 기른다. 구현 트랙과 달리 뼈대 서비스가 이미 있고 핵심 코드만 비어 있다. 학습자는 명세를 읽고 엣지 케이스 문항을 푼 뒤 핵심 코드를 채운다. 엔진은 k6 로 부하를 넣고 불변식과 지표로 채점한다.

프레임워크 고급 기능과 튜닝 실험은 [../spring/README.md](../spring/README.md) 에 따로 둔다. 둘 다 `lab` 종류를 쓰고 그 설계는 [../lab.md](../lab.md) 에 있다.

## 1. 학습 흐름

1. `cresvoid read`: 명세를 읽는다. 서비스 구조, 부하 시나리오, 지켜야 할 불변식, 지표 정의가 있다.
2. `cresvoid quiz`: 부하에서 생길 엣지 케이스를 예측하는 문항을 푼다. 문항을 모두 닫아야 실험이 열린다.
3. 핵심 코드를 채운다. AI 없이 한다.
4. `cresvoid test`: 엔진이 의존 서비스(Redis 등)와 학습자 서비스를 띄우고 k6 시나리오를 실행한다. 불변식과 임계값이 모두 맞아야 통과다.

## 2. 엣지 케이스 조사

항목마다 현상, 부하에서 드러나는 조건, 대응, 실험으로 확인하는 방법을 적는다. 출처는 아래 '출처' 절에 모았다. 카드는 이 목록에서 항목을 골라 만든다.

### 2.1 캐시

- **스탬피드(hot key 만료)**: 인기 키가 만료되는 순간 동시 요청이 모두 원천을 조회한다. 원천이 느릴수록 겹치는 요청이 늘어난다. 대응은 요청 병합(single-flight), 키 단위 잠금(`SET key token NX PX ttl`)과 대기, 만료 전 확률적 재계산(XFetch), 소프트 TTL 과 오래된 값 제공이다. 실험은 원천 조회 수가 만료 횟수에 비례하는지 확인한다.
- **잠금 자체의 함정**: 잠금 TTL 이 재계산 시간보다 짧으면 두 번째 재계산이 시작된다. 토큰을 확인하지 않고 잠금을 지우면 남의 잠금을 지운다(확인과 삭제를 Lua 로 한 번에 한다). 잠금을 잡은 프로세스가 죽으면 기다리는 요청은 잠금 TTL 만큼 멈춘다. 대기를 짧은 sleep 반복으로 하면 Redis 호출이 폭증한다.
- **관통(없는 키)**: 존재하지 않는 키는 캐시에 남지 않아 매번 원천을 친다. 대응은 짧은 TTL 의 음성 캐시와 블룸 필터다. 음성 캐시 TTL 이 길면 새로 만든 데이터가 그동안 안 보인다.
- **동시 만료(눈사태)**: 같은 시각에 채운 키들이 같은 TTL 로 함께 만료되거나 캐시가 재시작되면 원천 호출이 한꺼번에 몰린다. 대응은 TTL 지터, 예열, 원천 호출 상한이다. 실험은 초당 원천 호출의 최댓값을 측정한다.
- **오래된 값 기록(stale set)**: 읽기 경로가 캐시 미스 뒤 원천에서 옛 값을 읽는 사이에 쓰기 경로가 원천을 고치고 캐시를 지우면 읽기 경로가 옛 값을 캐시에 쓴다. 이 값은 TTL 까지 남는다. 대응은 버전을 붙여 새 버전만 쓰는 조건부 쓰기, 지연 재삭제, 리스(lease), 짧은 TTL 안전망이다.
- **쓰기 순서**: 캐시를 먼저 고치고 원천을 고치면 원천 쓰기가 실패할 때 캐시만 틀린다. 원천을 고치고 캐시를 고치면 동시 쓰기 순서가 뒤바뀔 때 캐시가 옛 값으로 남는다. 그래서 갱신보다 삭제가 흔하다.
- **핫 키와 큰 키**: 한 키에 요청이 몰리면 그 키를 가진 노드가 병목이 된다. 큰 값은 한 번 읽을 때 네트워크와 Redis 단일 스레드 시간을 오래 쓴다. 대응은 프로세스 안 로컬 캐시 층, 값 나누기, 크기 제한이다.
- **TTL 의미**: `SET` 으로 값을 덮어쓰면 TTL 이 지워진다. `INCR` 와 `HSET` 은 TTL 을 유지한다. 0 이하의 TTL 로 `EXPIRE` 하면 키가 지워진다. 만료 시각은 절대 시각이라 서버 시계가 바뀌면 만료도 바뀐다. 값만 고치는 코드가 TTL 없는 영구 키를 만드는 사고가 흔하다.
- **메모리 한도와 축출**: `maxmemory-policy` 가 `noeviction` 이면 한도에서 쓰기 명령이 오류를 낸다. `volatile-*` 정책은 TTL 이 있는 키가 없으면 `noeviction` 처럼 동작한다. 축출은 근사 LRU·LFU 라 정확히 가장 오래된 키가 지워지지는 않는다.
- **캐시 장애와 원천 보호**: 캐시가 죽었을 때 모든 요청을 원천으로 돌리면 원천이 함께 무너진다. 대응은 원천 요청률 상한과 load shedding, 오래된 값 제공, 회로 차단이다.
- **직렬화 형식 변경**: 배포 중 옛 버전과 새 버전 서버가 같은 캐시를 읽는다. 형식에 버전을 넣고 옛 형식도 읽을 수 있게 한다.

### 2.2 동시성

- **갱신 손실(read-modify-write)**: 두 요청이 같은 값을 읽고 각자 계산해 쓰면 한쪽 갱신이 사라진다. 대응은 원자적 연산(`UPDATE t SET stock = stock - 1 WHERE id = ? AND stock >= 1`, Redis `INCR`/`DECRBY`, `AtomicInteger.updateAndGet`), 낙관적 잠금(버전 비교와 재시도), 비관적 잠금(`SELECT ... FOR UPDATE`)이다.
- **확인 후 행동(check-then-act)**: 재고를 확인한 뒤 차감하면 그 사이에 다른 요청이 끼어 초과 판매가 난다. 확인과 행동을 한 연산으로 묶는다.
- **원자적이지 않은 조합**: `AtomicInteger` 의 `get()` 뒤 `set()`, `ConcurrentHashMap` 의 `get()` 뒤 `put()` 은 원자적이지 않다. `compute`·`merge`·`compareAndSet` 을 쓴다. `compute` 안에서 느린 일을 하면 다른 키의 갱신까지 기다리게 할 수 있다.
- **JVM 잠금의 범위**: `synchronized` 와 `ReentrantLock` 은 한 프로세스 안에서만 막는다. 인스턴스가 둘이면 쓸모가 없다.
- **잠금과 트랜잭션 경계**: `@Transactional` 메서드 안에서 잠금을 잡고 풀면 트랜잭션 커밋 전에 잠금이 풀려 다음 요청이 커밋 전 값을 읽는다. 잠금 해제를 커밋 뒤로 미룬다.
- **분산 잠금의 만료**: 잠금을 잡은 프로세스가 GC 나 네트워크 지연으로 TTL 보다 오래 멈추면 다른 프로세스가 잠금을 얻고 둘이 함께 쓴다. 저장소가 펜싱 토큰(잠금마다 커지는 번호)을 확인해 늦은 쓰기를 거절해야 안전하다.
- **중복 요청**: 더블 클릭, 클라이언트 재시도, 게이트웨이 재시도로 같은 요청이 여러 번 온다. 멱등 키로 첫 결과를 저장하고 같은 키의 요청에는 저장한 결과를 준다. 처리 중인 같은 키 요청과 같은 키에 다른 본문이 온 경우도 정한다.
- **교착 상태**: 두 트랜잭션이 자원을 다른 순서로 잠그면 교착이 생긴다. 잠금 순서를 고정하고 교착 감지 오류는 재시도한다.
- **풀 교착**: 한 요청이 커넥션 두 개를 동시에 쓰면 풀 크기만큼의 요청이 각자 하나씩 잡고 두 번째를 기다리며 멈춘다. 최소 풀 크기는 Tn × (Cm − 1) + 1 이다(Tn: 동시 스레드 수, Cm: 스레드당 동시 커넥션 수).

### 2.3 부하와 회복력

- **타임아웃**: 타임아웃이 없으면 느린 하위 서비스가 스레드와 커넥션을 점유한다. 허용할 거짓 타임아웃 비율을 정하고 하위 서비스 지연의 해당 백분위로 정한다. DNS 와 TLS 시간도 포함한다.
- **재시도 증폭**: 층마다 재시도하면 재시도 수가 곱해진다. 재시도는 한 층에서만 한다. 지수 백오프에 상한과 지터를 둔다. 재시도 예산(토큰 버킷)으로 재시도 비율을 묶는다. 부작용이 있는 API 는 멱등이어야 재시도할 수 있다.
- **회로 차단의 판정**: 하위 서비스가 타임아웃보다는 빠르지만 평소보다 크게 느려지면 실패가 생기지 않는다. 실패율로만 판정하는 회로 차단기는 열리지 않는다. 그동안 요청은 느려진 만큼 스레드와 커넥션을 오래 점유한다. 호출이 적은 경로에서는 판정에 필요한 최소 호출 수를 채우지 못해 판정이 일어나지 않는다. 대응은 느린 호출의 기준 시간과 비율을 쓰는 판정, 그 경로의 호출량에 맞춘 최소 호출 수와 창 크기, 반열림 상태의 시험 호출 수 지정이다. Resilience4j 의 기본값은 실패율 50%, 최소 호출 100, 반열림 시험 호출 10 이다. 느린 호출 판정은 기본값이 기준 60초와 비율 100% 라서 사실상 동작하지 않는다. 실험은 하위 서비스를 오류 없이 느리게 만들고 차단기가 열리는 시각과 그동안 늘어난 동시 요청 수를 잰다. L-load-60 의 회로 차단은 캐시 호출만 다룬다.
- **과부하와 goodput**: 처리량이 한계를 넘으면 지연이 늘고 클라이언트가 타임아웃으로 떠난 요청까지 서버가 처리하느라 쓸모 있는 처리량(goodput)이 떨어진다. 대응은 큐 길이 제한, 처리 전 마감 시각 확인, 빠른 거절(503)이다.
- **속도 제한**: 고정 창은 창 경계에서 두 배 폭주를 허용한다. 토큰 버킷과 미끄러지는 창을 비교한다. Redis 에서 `INCR` 뒤 `EXPIRE` 를 따로 부르면 그 사이 장애로 TTL 없는 카운터가 남는다.
- **커넥션 풀 고갈**: 느린 쿼리나 느린 외부 호출이 커넥션을 오래 점유하면 풀이 비고 나머지 요청이 `connectionTimeout` 까지 기다린다. 풀은 작게, 커넥션을 점유하는 구간은 짧게 둔다.
- **부하 측정의 함정**: 닫힌 모델은 서버가 느려지면 도착률이 떨어져 꼬리 지연을 숨긴다. 열린 모델에서 VU 가 모자라면 k6 는 반복을 버리고 `dropped_iterations` 로 센다. 평균이 아니라 p95·p99 를 확인한다.

### 2.4 메시지와 이벤트

- **최소 한 번 전달과 중복**: 소비자가 처리 뒤 오프셋 커밋 전에 죽거나 재조정되면 같은 메시지를 다시 받는다. 처리 기록이나 자연 키로 멱등 소비자를 만든다.
- **순서**: 키가 다른 메시지 사이의 순서는 보장되지 않는다. 같은 파티션 안에서도 재시도가 순서를 바꿀 수 있다. 버전 비교로 늦게 온 옛 이벤트를 버린다.
- **이중 쓰기**: DB 커밋과 메시지 발행을 따로 하면 한쪽만 성공한다. 트랜잭셔널 아웃박스로 같은 트랜잭션에 기록하고 따로 발행한다.
- **독 메시지**: 처리할 수 없는 메시지를 무한히 재시도하면 파티션이 멈춘다. 재시도 한도와 DLT 를 둔다.

### 2.5 데이터 조회

- **오프셋 페이지네이션 표류**: 페이지를 넘기는 사이 앞쪽에 행이 추가되거나 지워지면 중복이나 누락이 생긴다. 커서 페이지네이션과 고유한 정렬 키를 쓴다.
- **N+1 쿼리**: 목록을 읽은 뒤 행마다 연관 데이터를 따로 읽으면 부하에서 쿼리 수가 곱으로 늘고 커넥션을 오래 점유한다.

### 2.6 공개 사례

- 처리율 제한(Stripe, Cloudflare), 적응형 동시성 제한(Netflix), 셔플 샤딩(AWS) 사례는 [../system-design/README.md](../system-design/README.md) 의 '빅테크 공개 사례' 절에 요약했다. L-load-90 은 Stripe 와 Cloudflare 사례를, L-load-80 과 L-load-120 은 Stripe 의 부하 차단과 Netflix 사례를 배경으로 쓴다.
- 공개 장애 보고서 가운데 무작위 백오프 없이 몰린 재시작(Google Cloud 2025, R-incidents-60)은 L-load-70 의 배경이다. 연휴 뒤 차가운 캐시와 급증(Slack 2021, R-incidents-70)은 L-load-40 과 L-load-60 의 배경이다. 보고서 목록은 [../incidents/README.md](../incidents/README.md) 에 있다.

## 3. 출처

- Vattani, Chierichetti, Lowenstein, "Optimal Probabilistic Cache Stampede Prevention", PVLDB 8(8), 2015. https://cseweb.ucsd.edu/~avattani/papers/cache_stampede.pdf
- Brinkley, Chhabra, "Caching challenges and strategies", Amazon Builders' Library. https://aws.amazon.com/builders-library/caching-challenges-and-strategies/
- Nishtala 외, "Scaling Memcache at Facebook", NSDI 2013 (리스, 오래된 값 기록, gutter 풀). https://www.usenix.org/system/files/conference/nsdi13/nsdi13-final170_update.pdf
- Kleppmann, "How to do distributed locking", 2016 (펜싱 토큰). https://martin.kleppmann.com/2016/02/08/how-to-do-distributed-locking.html
- Redis, EXPIRE 명령 문서(TTL 유지와 삭제, 만료 방식). https://redis.io/docs/latest/commands/expire/
- Redis, Key eviction 문서(maxmemory-policy). https://redis.io/docs/latest/develop/reference/eviction/
- "Timeouts, retries, and backoff with jitter", Amazon Builders' Library. https://builder.aws.com/content/3EumjoZascWd1oZiEgL8ORlv3qE/timeouts-retries-and-backoff-with-jitter
- "Using load shedding to avoid overload", Amazon Builders' Library. https://aws.amazon.com/builders-library/using-load-shedding-to-avoid-overload/
- Resilience4j, CircuitBreaker 문서(기본값). https://resilience4j.readme.io/docs/circuitbreaker
- Stripe, "Designing robust and predictable APIs with idempotency". https://stripe.com/blog/idempotency
- MySQL 8.4 Reference Manual, Locking Reads. https://dev.mysql.com/doc/refman/8.4/en/innodb-locking-reads.html
- HikariCP, About Pool Sizing. https://github.com/brettwooldridge/HikariCP/wiki/About-Pool-Sizing
- k6 문서: Open and closed models, Dropped iterations, Thresholds, Custom summary. https://grafana.com/docs/k6/latest/
- Apache Kafka 문서의 Message Delivery Semantics 절.

## 4. 카드

트랙은 `backend-lab`(종류 lab)이다. 카드는 `cards/` 에 한 장씩 있고 번호 순서가 로드맵 순서다. lab 종류 엔진 작업([../lab.md](../lab.md))과 k6·Redis 설치가 먼저다.
