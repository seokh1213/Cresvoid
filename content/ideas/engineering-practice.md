# 실무 문제 학습 계획

파일 분할, 이력 정리, 실행 증명, 여러 원천의 병합, 잠금과 결과 배치를 작은 구현·수정·실험으로 배운다. 카드 원본은 기존 카탈로그의 분야별 폴더에 두고 이 문서는 공부 순서와 연결만 관리한다. 기존 카드의 번호와 내용을 바꾸지 않고 후보 18개를 더했다.

## 1. 현재 범위

아래 18개는 모두 후보 카드다. 실행할 문제·시작 코드·참조 풀이·테스트는 아직 만들지 않았다. 종류별 트랙을 연결한 학습 계획이며 여러 종류를 한 트랙에 섞거나 자동으로 잠금 해제하는 새 기능은 아니다.

첫 버전은 Kotlin 표준 라이브러리와 JDK 21만 사용하도록 계획했다. 파일·시각·실패를 가짜 저장소와 입력 사건으로 재현하며 lab만 JDK HTTP 서비스를 직접 실행한다. 실제 제품을 쓰는 실험은 별도 범위를 정한 뒤 추가한다.

## 2. 먼저 할 묶음

첫 제작 묶음은 운영·실험 데이터 병합이다. 하나의 도메인에서 병합 규칙, 과거 회차 재실행, 실험 종료 복구라는 세 과제를 이어 배운다.

1. [B-pipeline-50](backend/pipeline/cards/B-pipeline-50-index-overlay.md)에서 두 원천의 갱신·삭제·복원을 구현한다.
2. [F-pipeline-60](backend/pipeline/cards/F-pipeline-60-replay-regression.md)에서 옛 회차가 최신 결과를 되돌리는 버그를 고친다.
3. [B-pipeline-70](backend/pipeline/cards/B-pipeline-70-experiment-recovery.md)에서 정상 경로가 빠진 대상까지 운영 상태로 복구한다.

짧은 버그 수정부터 시작하려면 F-kotlin-130, F-pipeline-20, F-pipeline-60을 각각 선택할 수 있다. 관련 build를 먼저 끝내는 것은 권장 순서이며 그 카드의 정답을 알아야 풀리는 문제로 만들지 않는다.

## 3. 추가한 카드

### 파일을 안전하게 나누기

| 카드 | 종류 | 새 과제 |
|---|---|---|
| [B-record-range-10](implementation/cards/B-record-range-10-record-range.md) | build | 줄·객체 배열의 레코드 경계와 버전이 있는 바이트 구간 |
| [F-kotlin-130](implementation/cards/F-kotlin-130-byte-offset.md) | fix | 문자 위치와 UTF-8 바이트 위치를 혼동한 버그 |
| [F-kotlin-140](implementation/cards/F-kotlin-140-short-read.md) | fix | 부분 읽기를 한 번으로 끝내는 버그 |

기존 [C-kotlin-80](implementation/cards/C-kotlin-80-csv-import.md)의 CSV 문법·문자열 API와 달리 파일 위치와 입력 스트림의 계약을 다룬다.

### 변경 이력 정리와 완료 증명

| 카드 | 종류 | 새 과제 |
|---|---|---|
| [B-pipeline-10](backend/pipeline/cards/B-pipeline-10-history-rollover.md) | build | 현재 상태를 보존하는 기준 상태 생성과 이력 정리 |
| [F-pipeline-20](backend/pipeline/cards/F-pipeline-20-delete-scope.md) | fix | 다시 적재한 키로 삭제 범위를 제한하기 |
| [B-pipeline-30](backend/pipeline/cards/B-pipeline-30-batch-workflow.md) | build | 입력·세대·단계별 완료 표식과 실패 재개 |
| [F-pipeline-40](backend/pipeline/cards/F-pipeline-40-missing-chunk.md) | fix | 0건 성공한 조각과 실행되지 않은 조각 구분 |

기존 [B-lakehouse-30](backend/lakehouse/cards/B-lakehouse-30-table-format-build.md)의 스냅숏·커밋·행 삭제 뒤에서 사용하는 유지 작업이다. 건수 일치와 표본 검사를 내용 전체의 정확성 증명으로 취급하지 않는다.

### 운영·실험 데이터 병합

| 카드 | 종류 | 새 과제 |
|---|---|---|
| [B-pipeline-50](backend/pipeline/cards/B-pipeline-50-index-overlay.md) | build | 두 원천의 우선순위와 삭제·복원 |
| [F-pipeline-60](backend/pipeline/cards/F-pipeline-60-replay-regression.md) | fix | 이전 회차 재실행의 역행 방지 |
| [B-pipeline-70](backend/pipeline/cards/B-pipeline-70-experiment-recovery.md) | build | 실험 종료 후의 계획·검증·멱등 복구 |

기존 [C-kotlin-30](implementation/cards/C-kotlin-30-order-events.md)과 [L-load-100](backend/load/cards/L-load-100-idempotent-consumer.md)은 사건 순서·멱등성과 연결된다. 새 묶음은 서로 다른 두 원천의 우선순위와 실험 철회가 목표다.

### 만료되는 잠금

| 카드 | 종류 | 새 과제 |
|---|---|---|
| [B-distributed-60](backend/distributed/cards/B-distributed-60-lease-lock.md) | build | 획득별 식별자와 가상 시간의 lease |
| [F-distributed-70](backend/distributed/cards/F-distributed-70-late-lock-release.md) | fix | 이전 소유자의 늦은 해제가 새 소유권을 지우는 버그 |
| [B-distributed-80](backend/distributed/cards/B-distributed-80-fenced-writes.md) | build | 저장소에서 오래된 획득 번호의 쓰기 거절 |

기존 [동시성 원리 목록](backend/load/README.md)에 있는 분산 잠금·펜싱을 전용 카드로 구체화했다. 저장소가 새 토큰을 관측한 뒤의 역행 방지와 lease 만료 직후 모든 쓰기의 즉시 차단을 구분한다.

### 제약이 있는 검색 결과 배치

| 카드 | 종류 | 새 과제 |
|---|---|---|
| [B-search-60](backend/search/cards/B-search-60-result-placement.md) | build | 후보열 순서·노출 제약을 지키는 최적 배치 |
| [F-search-70](backend/search/cards/F-search-70-fallback-constraints.md) | fix | 대체 알고리즘에서 빠진 필수 노출 상한 |
| [C-search-80](backend/search/cards/C-search-80-stable-diversity.md) | code | 세션별 재현성과 품질 하한을 지키는 다양성 |

기존 [search-engine](implementation/cards/B-search-engine-10-search-engine.md)의 관련도 계산과 [es-cluster](backend/search/cards/B-search-20-es-cluster.md)의 샤드 결과 합산 뒤에 놓이는 문제다. 빠른 대체 경로도 필수 제약을 지키되 최적 점수는 별도 목표로 둔다.

### 중복 탐지의 비용과 정확도

| 카드 | 종류 | 새 과제 |
|---|---|---|
| [B-pipeline-80](backend/pipeline/cards/B-pipeline-80-dedup-candidates.md) | build | 정확 일치 묶기, 무손실 후보 제외, 연결 요소와 대표 |
| [L-pipeline-90](backend/pipeline/cards/L-pipeline-90-candidate-recall.md) | lab | 비교 횟수와 놓친 중복쌍을 함께 측정 |

기존 [L-spark-31](backend/spark/cards/L-spark-31-skew-and-aqe.md)은 결과를 보존하는 조인 쏠림 실험이다. 새 실험은 후보를 잘라 없애는 최적화가 판정 결과를 바꾸는지 확인한다.

## 4. 공부 항목으로 남길 후속 주제

아래 항목은 아직 별도 카드가 아니다. 기존 카드의 목표와 검사 방법이 충분히 겹치는지 먼저 확인하고 독립 과제일 때 분리한다.

| 주제 | 기존 항목과의 연결 | 카드로 나누기 전 정할 것 |
|---|---|---|
| 로그 보관의 안전한 커밋 지점 | [C-messaging-30](backend/messaging/cards/C-messaging-30-consumer-commit.md), [B-system-design-160](backend/system-design/cards/B-system-design-160-object-store.md) | 업로드 완료 순서·열린 파일·재기동 위치를 별도 과제로 할지 |
| 상태 게이트와 판정 불가 | [L-load-60](backend/load/cards/L-load-60-cache-outage.md), load의 회로 차단 아이디어 | 우회와 복귀 비용, 관측 실패 때의 정책, 고정 실험 부하 |
| 계층 재시도와 이관 준비 완료 | [C-kotlin-50](implementation/cards/C-kotlin-50-retry-policy.md), [C-messaging-30](backend/messaging/cards/C-messaging-30-consumer-commit.md) | 명령과 상태의 차이, 그룹 전체 완료, 마지막 실패 기록을 각각 분리할지 |
| 끝 신호 없는 스트림의 완료 판정 | [B-pipeline-30](backend/pipeline/cards/B-pipeline-30-batch-workflow.md), [B-system-design-110](backend/system-design/cards/B-system-design-110-ad-click-aggregator.md) | 건수 균형과 누락 없음의 차이, 중복 집계에 따른 조기 완료, 잠정 판정과 확정 완료를 구분할 조건 |
| 작업자 자가 회복과 조사 자료의 신뢰성 | [L-jvm-30](backend/jvm/cards/L-jvm-30-thread-dump.md), [B-observability-20](backend/observability/cards/B-observability-20-metrics.md) | 강제 종료 후 남는 자원의 모형, 대조군·실패한 집계·갱신되지 않은 자료의 표시 |

## 5. 제작할 때 확인할 것

- 카드는 분야별 폴더에서 관리하고 이 문서에는 링크와 공부 순서만 유지한다. 같은 후보를 두 카드로 복제하지 않는다.
- 원본 사례 문서는 수정하지 않는다. 학습용 상황과 입력은 새로 만들고 조직 정보·실제 운영 수치는 공개 콘텐츠에 옮기지 않는다.
- 공식 출처와 과제가 정한 정책을 구분한다. 카드의 출처에서 제품의 모든 동작을 보장한다고 해석하지 않는다.
- 같은 사례의 앞 문제 해설이 뒤 문제의 수정 위치를 알려 주면 다른 상황으로 옮긴 전이 문제로 만든다. fix는 원인 하나를 유지한다.
- 실제 제작에서는 공통·종류별 스킬의 독립 출제·검증을 거친다. 후보 등록과 문제 검증 완료를 같은 상태로 기록하지 않는다.
