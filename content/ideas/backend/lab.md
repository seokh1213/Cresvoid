# lab 종류 설계 초안

이 문서는 엔진과 `content/FORMAT.md` 에 넣을 계약의 초안이다. 첫 lab 단계를 만들기 전에 엔진 작업으로 구현하고 FORMAT.md 에 옮긴다.

## 1. 단계 디렉터리

```
stages/<단계>/
├── spec.md          시나리오, 규칙, 불변식, 지표 정의를 번호 문단으로 쓴다
├── skeleton/        학습자 작업 공간이 될 Kotlin 프로젝트. 핵심 함수만 TODO() 로 비운다
└── hidden/          채점용 파일. 학습자는 열지 않는다
    ├── quiz.json    엣지 케이스 예측 문항. choice 와 short 를 쓴다
    ├── lab.json     실험 정의('lab.json' 절)
    ├── solution/    참조 구현. cresvoid solution 으로 본다
    ├── variants/    검증용 오답 구현('검증(cresvoid validate)' 절)
    └── load/        k6 시나리오 스크립트(JavaScript)
```

- k6 스크립트는 JavaScript 다. 프로젝트 코드를 Kotlin 으로 통일한다는 원칙(D8)의 예외로 둔다. k6 가 JavaScript 만 실행하기 때문이다.
- 서비스는 JDK 21 의 `com.sun.net.httpserver.HttpServer` 로 만든다. Redis 클라이언트는 뼈대에 들어 있는 작은 RESP 클라이언트를 쓴다. 외부 의존성이 없다.
- 실제 DB 가 필요한 실험은 [spring/README.md](spring/README.md) 의 '실행 방식' 절의 방식(Docker 나 Maven 의존성)을 따른다.

## 2. lab.json

```json
{
  "mode": "implement",
  "editable": ["src/main/kotlin/catalog/ProductCache.kt"],
  "service": {
    "health": "/health",
    "stats": "/internal/stats",
    "startup_timeout_sec": 30
  },
  "dependencies": [
    { "name": "redis", "command": ["redis-server", "--port", "{port}", "--save", "", "--appendonly", "no"], "url_env": "REDIS_URL", "url": "redis://127.0.0.1:{port}" }
  ],
  "scenarios": [
    {
      "name": "hot-key-expiry",
      "script": "load/hot-key.js",
      "env": { "RATE": "500", "DURATION": "40s" },
      "invariants": [
        { "name": "origin-calls-per-expiry", "stat": "origin_calls.hot", "op": "<=", "value": 12, "refs": ["4.2"], "quote": "만료 한 번에 원천 조회는 한 번이다." }
      ],
      "repeat": 1
    }
  ],
  "must_fail": ["variants/no-lock", "variants/lock-without-token"]
}
```

| 필드 | 뜻 |
|---|---|
| mode | `implement`(핵심 코드 채우기), `tune`(설정과 허용된 코드만 바꿔 목표 맞추기), `observe`(실행 전에 예측하고 실행 뒤 관측값과 맞춰 보기) |
| editable | 학습자가 고칠 파일. 엔진은 이 밖의 파일이 시작 코드와 다르면 경고한다 |
| service | 건강 확인과 통계 경로. 포트는 엔진이 `PORT` 환경변수로 준다 |
| dependencies | 엔진이 띄울 의존 서비스. `{port}` 는 엔진이 고른 빈 포트다 |
| scenarios | k6 스크립트, 넘길 환경변수, 불변식, 반복 횟수 |
| must_fail | 검증 때 반드시 실패해야 하는 오답 구현 디렉터리 |

scenarios 와 must_fail 의 경로는 `hidden/` 기준이다. editable 의 경로는 작업 공간 기준이다.

## 3. 서비스 계약

- `PORT` 로 받은 포트에서 듣는다. 의존 서비스 주소는 lab.json 이 정한 환경변수로 받는다.
- `GET /health` 는 준비되면 200 을 준다.
- `GET /internal/stats` 는 불변식이 쓰는 카운터를 JSON 객체로 준다. 예: `{"origin_calls": {"hot": 9, "other": 412}, "stale_reads": 0}`. `POST /internal/stats/reset` 은 카운터를 0 으로 만든다.
- `POST /internal/faults` 는 장애를 주입한다. 예: `{"origin_latency_ms": 200, "origin_error_rate": 0.0}`. k6 시나리오가 정해진 시각에 호출한다.
- 원천 저장소(가짜 DB)는 메모리에 있고 지연과 오류를 주입할 수 있다. 원천 호출 수를 키별로 센다.

## 4. 엔진 동작

1. 작업 공간을 빌드한다.
2. 의존 서비스를 빈 포트로 띄우고 준비될 때까지 기다린다. 임시 디렉터리를 쓴다.
3. 서비스를 띄우고 `/health` 가 200 이 될 때까지 기다린다. 서비스의 표준 출력과 표준 오류는 보여 주지 않고 파일로도 남기지 않는다.
4. 시나리오마다 통계를 0 으로 만들고 k6 를 실행한다. 명령은 `k6 run --quiet -e BASE_URL=... --summary-export <임시 파일> <스크립트>` 꼴이다. k6 는 임계값이 실패하면 0 이 아닌 코드로 끝난다. 요약 JSON 에서 임계값 결과와 주요 지표(http_req_duration p95·p99, http_req_failed, dropped_iterations)를 읽는다.
5. `/internal/stats` 를 읽어 불변식을 판정한다.
6. 서비스와 의존 서비스를 모두 끝낸다. 실패하거나 Ctrl-C 를 받아도 끝낸다.
7. 실패하면 [FORMAT.md](../../FORMAT.md) 의 '테스트 실패 출력' 절의 항목을 보여 준다. 테스트 이름 자리에는 시나리오 이름과 실패한 불변식이나 임계값 이름을 쓴다. 집계 관측값(통계, 지연 백분위, 오류율, dropped_iterations)을 함께 보여 줄지는 lab 엔진 작업 때 정한다. tune 모드는 이 값을 보아야 설정을 맞출 수 있다.

## 5. 검증(cresvoid validate)

1. lab.json 형식, 파일 존재, k6 스크립트 문법(`k6 inspect`).
2. 참조 구현이 모든 시나리오를 세 번 연속 통과한다. 한 번이라도 실패하면 불변식이나 임계값이 불안정한 것이다.
3. 뼈대는 빌드되고 뜨지만 하나 이상의 불변식에서 실패한다.
4. `must_fail` 의 오답 구현은 세 번 모두 실패한다. 이 검사가 실험의 판별력을 보장한다. 예: 잠금 없이 캐시를 채우는 구현, 토큰 확인 없이 잠금을 푸는 구현.
5. 문항은 다른 트랙과 같은 규칙으로 검사한다.

## 6. 판별력과 안정성 기준

- 경합 창을 넓힌다. 읽기와 쓰기 사이, 원천 조회 안에 지연을 주입해 안전하지 않은 구현이 매번 실패하게 만든다.
- 주 불변식은 시각이 아니라 횟수와 불변 관계로 정한다. 예: 성공한 주문 수와 재고 감소량이 같다. 원천 조회는 만료 한 번에 한 번이다.
- 지연 임계값은 노트북에서도 넉넉히 통과하도록 정한다. 참조 구현 측정값의 3배 이상으로 둔다.
- JIT 예열 구간을 두고 그 뒤부터 측정한다. k6 시나리오의 `startTime` 으로 나눈다.
- 부하 모델은 열린 모델(constant-arrival-rate, ramping-arrival-rate)을 쓴다. 닫힌 모델은 서버가 느려지면 도착률이 함께 떨어져 문제를 가린다(coordinated omission).

## 7. 환경

- k6 와 Redis 가 이 기기에 없다. `brew install k6 redis` 가 필요하고 사용자 승인을 받아야 한다(2026-10-08 확인).
- Docker 바이너리는 있다. DB 나 Kafka 가 필요한 실험에서 쓸지 사용자와 정한다.
- 서비스, Redis, k6 는 모두 127.0.0.1 에서만 듣는다. 외부 네트워크를 쓰지 않는다.

## 8. 도메인 lab 의 의존 서비스

load·spring 밖의 도메인 실험이 쓰는 의존 서비스다. 'lab.json' 절의 `dependencies` 로 실행한다.

| 도메인 | 의존 서비스 | 띄우는 방식 |
|---|---|---|
| search | Elasticsearch | Docker 이미지. 단일 노드, 보안 끔, 힙 1~2GB |
| olap | ClickHouse | Docker 이미지 |
| messaging | Kafka(KRaft) | Docker 이미지. 내구성 실험은 브로커 3개 |
| database | MySQL 8.4, PostgreSQL | Docker 이미지 |
| spark | Spark | `spark-sql`, `spark-submit` 이 있는 Docker 이미지나 Homebrew 설치 |
| jvm, network | 없음 | JDK 만 쓴다. 실험은 `experiment.main.kts` 로 실행한다 |

- Docker 를 쓰는 의존 서비스는 컨테이너 이름에 실행마다 다른 접미를 붙이고 끝나면 지운다. 포트는 엔진이 고른 빈 포트에 연결한다.
- 이미지는 태그를 고정한다. `latest` 를 쓰지 않는다.
- 준비 확인은 서비스마다 다르다(ES 는 클러스터 상태, Kafka 는 메타데이터 조회). lab.json 에 준비 확인 명령을 둔다.
- observe 모드만 쓰는 실험은 k6 없이 실험 스크립트가 관측값을 출력한다.
