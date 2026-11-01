# 데이터 파이프라인

여러 번 실행되는 데이터 처리에서 상태 보존, 단계 완료, 원천 간 우선순위와 복구를 익힌다. 작은 메모리 모형으로 원리를 구현하고 실패·재실행의 결과를 확인한다. 특정 제품의 동작을 그대로 복제하지 않고 카드가 정한 저장·시각·실패 계약을 쓴다.

## 1. 학습할 원리

- 상태와 이력: 기준 시점의 상태를 남기고 과거 변경을 정리한다. 안 바뀐 필드와 키도 보존해야 한다. 과거 전체 이력을 영구 보존하는 이벤트 저장소와 이 과제의 보관 계약은 다르다.
- 실행 증거: 완료 표식은 실행 세대·입력·단계·조각을 식별한다. 빈 결과로 끝난 작업과 실행되지 않은 작업을 구분한다. 파일 개수만 맞는 것과 필요한 작업이 모두 끝난 것은 다르다.
- 여러 원천의 병합: 두 원천의 갱신·삭제·복원 우선순위를 명시한다. 부재와 삭제를 구분하고 하나의 회차에서 같은 키에 서로 모순된 출력을 만들지 않는다.
- 재실행과 복구: 옛 회차 재실행은 이후의 결과를 되돌리지 않는다. 정상 경로가 일부 빠졌을 때도 원천과 대상의 상태를 다시 맞출 수 있어야 한다.
- 후보 생성: 비교할 쌍을 줄이는 규칙이 최종 판정에 미치는 영향을 잰다. 결과를 보존하는 가지치기와 일부 답을 놓치는 근사를 구분한다.

## 2. 학습 경로

아래 카드는 모두 후보이며 실제 단계는 아직 없다. 단계 하나짜리 code·fix·lab은 기존 공용 트랙 또는 새 전용 트랙에 제작 순서대로 붙인다.

| 순서 | 카드 | 익힐 것 |
|---|---|---|
| 1 | [B-pipeline-10](cards/B-pipeline-10-history-rollover.md) | 기준 상태와 이후 변경, 이력 정리 |
| 2 | [F-pipeline-20](cards/F-pipeline-20-delete-scope.md) | 다시 적재한 키로 삭제 범위 제한 |
| 3 | [B-pipeline-30](cards/B-pipeline-30-batch-workflow.md) | 완료 표식, 실패 재개, 전체 조각의 완료 |
| 4 | [F-pipeline-40](cards/F-pipeline-40-missing-chunk.md) | 비어 있는 조각과 빠진 조각 구분 |
| 5 | [B-pipeline-50](cards/B-pipeline-50-index-overlay.md) | 운영·실험 갱신과 삭제 병합 |
| 6 | [F-pipeline-60](cards/F-pipeline-60-replay-regression.md) | 이전 회차가 최신 결과를 되돌리지 않게 하기 |
| 7 | [B-pipeline-70](cards/B-pipeline-70-experiment-recovery.md) | 실험 종료 뒤 원천 기준의 멱등 복구 |
| 8 | [B-pipeline-80](cards/B-pipeline-80-dedup-candidates.md) | 정확 일치와 유사 후보, 대표 선택 |
| 9 | [L-pipeline-90](cards/L-pipeline-90-candidate-recall.md) | 비교 비용과 중복쌍 재현율의 동시 측정 |

처음 제작할 묶음은 B-pipeline-50, F-pipeline-60, B-pipeline-70이다. 병합·재실행·실험 철회의 서로 다른 과제를 한 흐름으로 배울 수 있다. 전체 도메인을 한 번에 끝내야 시작할 수 있는 구성은 아니다.

## 3. 기존 항목과의 연결

- [table-format](../lakehouse/cards/B-lakehouse-30-table-format-build.md)은 스냅숏과 커밋, 행 삭제를 다룬다. 이 도메인은 그 위에서 실행하는 정리·완료·복구 작업을 다룬다.
- [주문 이벤트 처리](../../implementation/cards/C-kotlin-30-order-events.md)와 [멱등 소비자](../load/cards/L-load-100-idempotent-consumer.md)는 한 원천의 사건 처리와 관련된다. 두 원천의 우선순위와 실험 종료 복원은 별도 과제다.
- [Spark 쏠림 실험](../spark/cards/L-spark-31-skew-and-aqe.md)은 같은 결과를 보존하는 조인 최적화다. 후보쌍을 버리는 최적화는 놓친 답을 별도로 측정한다.
- 파일 처리·분산 잠금·검색 배치까지 묶은 경로는 [실무 문제 학습 계획](../../engineering-practice.md)에 있다.

## 4. 환경과 제작 범위

모든 새 카드의 첫 버전은 Kotlin 표준 라이브러리와 JDK 21만 쓴다. 시각·장애·저장소 상태는 입력으로 주고 실제 Spark·HBase·Kafka·Elasticsearch를 요구하지 않는다. lab은 JDK HTTP 서비스와 실험 실행기를 사용하며 정합성과 작업량을 먼저 판정한다.

원문의 운영 수치를 합격 기준으로 옮기지 않는다. 구체적인 명령·시간 경계·동점·같은 회차 재수행의 계약은 단계 스펙에 고정하고 독립 구현으로 검증한다.

## 5. 출처

- [Apache Kafka 4.2 Design](https://kafka.apache.org/42/design/design/): 키의 마지막 상태를 보존하는 로그 압축. 과제의 필드별 상태 접기와 삭제 경계는 별도 규칙이다.
- [Apache Flink Fault Tolerance](https://nightlies.apache.org/flink/flink-docs-stable/docs/learn-flink/fault_tolerance/): 원천 위치와 연산자 상태를 함께 다루는 일관된 스냅숏.
- [AWS Step Functions Distributed Map](https://docs.aws.amazon.com/step-functions/latest/dg/state-map-distributed.html): 조각별 실행과 결과·실패 추적. 카드의 완료 표식 형식과 전체 성공 규칙은 별도 규칙이다.
- [Elasticsearch Index API](https://www.elastic.co/docs/api/doc/elasticsearch/operation/operation-index): 외부 버전과 오래된 색인 요청의 충돌. 실험 원천의 우선순위를 Elasticsearch의 기본 정책으로 해석하지 않는다.
- [Event Sourcing pattern](https://learn.microsoft.com/en-us/azure/architecture/patterns/event-sourcing): 상태 재생성과 스냅숏. 이 문서의 이벤트 이력 영구 보존과 카드의 유한 보관 계약을 구분한다.
- [Python Record Linkage Toolkit, Indexing](https://recordlinkage.readthedocs.io/en/latest/ref-index.html): 전수 후보쌍, blocking, sorted neighbourhood. 라이브러리 설치 없이 원리를 구현한다.
