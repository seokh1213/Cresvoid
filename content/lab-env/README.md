# lab 실험 환경

lab 단계의 실험 스크립트가 함께 쓰는 도구와 고정 이미지다. 계약의 요약은 `content/FORMAT.md` 5.1 절에 있다.

## 파일

| 파일 | 내용 |
|---|---|
| `load.main.kts` | 개방형 부하(`steady`, `runLoad`), 학습자 프로그램 시작과 준비 확인(`Program`), 빈 포트, 백분위수, METRIC 출력 |
| `docker.main.kts` | 고정 이미지 목록 `LAB_IMAGES`, Docker 확인(`requireDocker`), compose 실행 단위 `ComposeStack` |
| `kotlin/RedisClient.kt` | JDK 만으로 만든 RESP2 Redis 클라이언트. 시작 코드와 참조 풀이에 그대로 복사한다 |

## 쓰는 법

실험 스크립트 맨 위에서 필요한 도구를 불러온다. 경로는 `hidden/` 기준이다.

```kotlin
@file:Import("../../../../../lab-env/docker.main.kts")
@file:Import("../../../../../lab-env/load.main.kts")
```

외부 서비스가 필요하면 `hidden/compose.yaml`을 둔다.

```yaml
services:
  redis:
    image: ${REDIS_IMAGE}
    ports: ["127.0.0.1::6379"]
    command: ["redis-server", "--save", "", "--appendonly", "no"]
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 1s
      timeout: 1s
      retries: 30
    deploy:
      resources:
        limits: { cpus: "1", memory: 256m }
```

`ComposeStack(File("compose.yaml"), "<트랙>-<단계>").use { stack -> stack.up(); ... }` 로 띄운다. `stack.port("redis", 6379)` 로 열린 포트를 찾아 학습자 프로그램의 환경 변수로 넘긴다.

## 규칙

- 이미지는 `LAB_IMAGES`에 digest 로 고정한 것만 쓴다. 새 이미지는 버전과 digest 를 정하고 이 파일과 `docker.main.kts`에 함께 더한다.
- compose 서비스마다 healthcheck 와 CPU·메모리 한도를 둔다. 한도는 학습자 기기마다 결과가 달라지는 것을 줄인다.
- 포트는 임의 포트로 연다. 실행마다 프로젝트 이름이 달라 여러 실험이 동시에 돌아도 겹치지 않는다.
- 부하는 개방형 모델로 보낸다. 느려진 서비스가 도착률을 낮추지 않아야 과부하, 재시도, 스탬피드가 드러난다.
- 판정은 요청 수, 원천 호출 수, 동시 실행 상한처럼 관계와 개수로 한다. 지연 시간 임계값은 Docker Desktop 의 가상 머신을 거치므로 여유를 크게 둔다.
- Docker 가 꺼져 있으면 실험 오류다. 학습자에게 Docker Desktop 을 켜라고 알린다.

## 고정 이미지

| 변수 | 이미지 | 버전 |
|---|---|---|
| `REDIS_IMAGE` | `redis@sha256:21090af93f8324db2f0f0f4f38a7c60369d47d917e000c3423d89d2ae2883537` | 8.10.3 alpine |
