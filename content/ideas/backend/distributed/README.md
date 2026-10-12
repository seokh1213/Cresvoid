# 분산 시스템

여러 노드가 함께 상태를 지킬 때의 일관성, 합의, 시계, 복제 자료형, 데이터 배치, 시험 방법을 익힌다. 다른 도메인(HDFS, Kafka, Elasticsearch, 데이터베이스)에서 만나는 복제와 장애 처리의 공통 원리를 설명하는 힘을 기른다.

## 1. 개념 지도

- 일관성 모델: 선형화 가능성(모든 연산이 호출과 응답 사이 한 시점에 일어난 것처럼 보임), 순차 일관성, 인과 일관성, 최종 일관성. 강한 모델일수록 가용성과 지연의 대가가 크다.
- 복제 방식: 리더-팔로워, 다중 리더, 리더 없는 정족수(읽기 수 R 과 쓰기 수 W 가 R + W > N 이면 읽기가 최신 쓰기와 겹친다).
- Raft 합의: 리더 선출은 무작위 선거 타임아웃(논문의 예는 150~300ms)으로 표 갈림을 줄인다. 리더는 AppendEntries 로 로그를 복제하고 앞 항목의 임기와 위치가 맞는지 확인해 어긋난 팔로워 로그를 고친다. 리더는 현재 임기의 항목만 복제본 수를 세어 커밋하고 이전 임기 항목은 그 뒤에 함께 커밋된다. 투표는 자기보다 최신 로그를 가진 후보에게만 준다.
- 시계: 물리 시계는 노드마다 어긋난다. 램포트 시계는 인과 순서를 지키는 번호를 주고 벡터 시계는 동시 사건을 구별한다.
- CRDT: 어떤 순서로 합쳐도 같은 상태로 모이는 자료형이다. 증가만 하는 카운터(G-Counter), 증감 카운터(PN-Counter), 관찰-제거 집합(OR-Set), 마지막 쓰기 우선 레지스터(LWW)가 있다.
- 일관 해싱: 노드를 더하거나 뺄 때 옮기는 키를 1/N 정도로 줄인다. 가상 노드로 쏠림을 줄이고 용량이 다른 노드에 가상 노드 수를 달리 준다. 복제본은 링을 따라 서로 다른 물리 노드를 고른다.
- 결정적 시뮬레이션 시험: 여러 노드를 한 스레드의 가상 시계 위에서 실행하고 사건 순서와 장애를 시드로 정한다. 같은 시드면 실행이 똑같이 반복되어 실패를 그대로 재현할 수 있다. Dropbox 는 동기화 엔진을 이렇게 시험하며 실패한 시드를 기록하고 해시 순서까지 결정적으로 만든다. FoundationDB 는 클러스터 전체를 한 스레드 프로세스에서 시뮬레이션하며 네트워크, 기계, 데이터센터 수준의 장애를 넣는다.
- 펜싱 토큰과 분산 잠금은 [../load/README.md](../load/README.md) 의 '동시성' 절에 있다.

## 2. 학습 경로

1. R-distributed-10 기술 지문으로 Raft 의 선출과 커밋 규칙을 익힌다.
2. B-distributed-20 `raft` 트랙에서 결정적 시뮬레이터로 Raft 를 구현한다.
3. B-distributed-30 `crdt` 트랙에서 복제 자료형을 구현한다.
4. B-distributed-40 `consistent-hashing` 트랙에서 일관 해싱과 복제본 배치를 구현한다.
5. B-distributed-50 `sim-testing` 트랙에서 결정적 시뮬레이션 시험 도구를 만든다. B-distributed-20 의 결정적 실행을 시험 도구로 넓힌다.

## 3. 환경

모든 카드를 추가 도구 없이 표준 라이브러리로 만든다.

## 4. 아이디어

| 아이디어 | 종류 | 메모 |
|---|---|---|
| 점프 해시와 랑데부 해시 | reading | 일관 해싱과 옮기는 키 수, 메모리, 가중치 처리 비교 |
| 2단계 커밋과 사가 | reading | 조정자 장애, 보상 트랜잭션 |
| 선형화 가능성 검사기 | build | 연산 기록이 선형화 가능한지 판정 |
| 벡터 시계와 충돌 감지 | code | 동시 갱신 판정 코드 읽기 |

## 5. 출처

- Ongaro, Ousterhout, "In Search of an Understandable Consensus Algorithm (Extended Version)", USENIX ATC 2014.
- Lamport, "Time, Clocks, and the Ordering of Events in a Distributed System", CACM 1978.
- Shapiro 외, "Conflict-free Replicated Data Types", SSS 2011.
- Karger 외, "Consistent Hashing and Random Trees", STOC 1997.
- Jepsen, Consistency Models: https://jepsen.io/consistency
- Kleppmann, "Designing Data-Intensive Applications", 5장(복제), 9장(일관성과 합의).
- DeCandia 외, "Dynamo: Amazon's Highly Available Key-value Store", SOSP 2007.
- Dropbox, "Testing sync at Dropbox"(2020): https://dropbox.tech/infrastructure/-testing-our-new-sync-engine
- FoundationDB 문서, "Simulation and Testing": https://apple.github.io/foundationdb/testing.html
- Zeller, Hildebrandt, "Simplifying and Isolating Failure-Inducing Input", IEEE TSE 2002.
