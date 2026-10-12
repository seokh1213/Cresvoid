# HDFS

Hadoop 분산 파일 시스템의 구조와 쓰기 경로, 장애 처리, 운영 한계를 익힌다. 파일 하나가 블록과 복제본으로 나뉘어 놓이는 방식과 데이터노드가 죽었을 때 일어나는 일을 설명하고 작은 파일과 할당량 같은 운영 문제를 예측하는 힘을 기른다.

## 1. 개념 지도

기본값은 2026-10-08 에 Hadoop 3.5.0 문서(`hdfs-default.xml`)로 확인한 값이다.

### 1.1 구조

- 네임노드는 이름 공간(디렉터리 트리)과 블록이 어느 데이터노드에 있는지를 메모리에 둔다. 이름 공간의 바뀐 내용은 EditLog 에, 전체 이름 공간은 FsImage 에 남긴다.
- 체크포인트는 1시간(`dfs.namenode.checkpoint.period` 3600)이나 트랜잭션 100만 건(`dfs.namenode.checkpoint.txns`) 가운데 먼저 닿는 쪽에서 일어난다.
- 데이터노드는 블록을 저장하고 3초(`dfs.heartbeat.interval`)마다 하트비트를, 6시간(`dfs.blockreport.intervalMsec` 21600000)마다 블록 보고를 보낸다.
- 파일은 한 번 쓰고 여러 번 읽는다. 덧붙이기와 자르기 말고는 고칠 수 없고 쓰는 사람은 언제나 한 명이다.

### 1.2 블록과 배치

- 블록 크기는 128MB(`dfs.blocksize` 134217728), 복제 수는 3(`dfs.replication`)이다. 최소 블록 크기는 1MB(`dfs.namenode.fs-limits.min-block-size`)다.
- 기본 배치 규칙(복제 3): 쓰는 쪽이 데이터노드면 첫 복제본을 그 노드에, 아니면 쓰는 쪽과 같은 랙의 임의 노드에 둔다. 두 번째는 다른 랙의 노드에, 세 번째는 두 번째와 같은 랙의 다른 노드에 둔다.
- 쓰기 파이프라인: 클라이언트는 64KB(`dfs.client-write-packet-size` 65536) 패킷을 첫 데이터노드에 보내고 데이터노드는 받은 패킷을 다음 데이터노드로 넘긴다. 파이프라인의 노드가 실패하면 빼고 교체 노드를 찾는다(`dfs.client.block.write.replace-datanode-on-failure.enable` 기본 true, 정책 DEFAULT).
- `hflush()` 는 새로 여는 읽기에 데이터가 보이게 할 뿐 내구성은 보장하지 않는다. `hsync()` 는 디스크까지 저장되기를 기다린다. 쓰는 중인 파일의 메타데이터 길이는 실제로 읽을 수 있는 데이터보다 작을 수 있다.

### 1.3 장애와 회복

- 하트비트가 끊긴 데이터노드는 죽은 것으로 표시되고 새 입출력을 받지 않는다. 네임노드는 복제가 모자란 블록을 계속 추적해 다시 복제한다.
- 죽은 노드 판정 시간은 재확인 간격(`dfs.namenode.heartbeat.recheck-interval` 300000ms)과 하트비트 간격으로 계산한다. 2 × 재확인 간격 + 10 × 하트비트 간격(630초)이라는 계산식은 구현에 있다. 카드를 만들 때 DatanodeManager 소스로 확인한다.
- 30초(`dfs.namenode.stale.datanode.interval`) 동안 하트비트가 없는 노드는 stale 로 판정한다.
- 안전 모드: 네임노드는 시작할 때 안전 모드에 들어간다. 안전하게 복제된 블록의 비율이 0.999(`dfs.namenode.safemode.threshold-pct`)에 이르고 30초(`dfs.namenode.safemode.extension` 30000)가 더 지나면 빠져나온다.

### 1.4 운영 한계

- 작은 파일: 파일, 디렉터리, 블록이 저마다 네임노드 메모리에서 약 150바이트를 차지한다(경험 규칙). 192MB 파일 하나는 150 × (1 + 2 × 3) = 1,050바이트, 1MB 파일 192개는 150 × (192 + 192 × 3) = 115,200바이트다.
- 디렉터리 하나의 항목 수 한도는 1,048,576(`dfs.namenode.fs-limits.max-directory-items`)이다.
- 이름 할당량은 디렉터리 아래 파일과 디렉터리 이름 수의 상한이다. 공간 할당량은 바이트 상한이고 블록의 복제본마다 따로 센다. 블록 하나를 끝까지 쓸 공간이 남아 있지 않으면 블록 할당이 실패한다.
- 소거 코딩: 3배 복제는 저장 공간 오버헤드가 200% 이고 소거 코딩은 50% 이하다. 블록 6개를 3배 복제하면 18개, RS(6,3) 이면 9개가 든다. 기본 정책은 RS-6-3-1024k 다. 입출력이 적은 따뜻하거나 차가운 데이터에 맞고 스트라이프 폭(RS-6-3 이면 9) 이상의 데이터노드와 랙 사이 대역폭이 필요하다. hflush, hsync, append, truncate 같은 연산에 제약이 있다.

### 1.5 고가용성

- 액티브 네임노드와 스탠바이 네임노드가 저널노드 다수결로 EditLog 를 공유하고 장애 조치 때 펜싱으로 두 액티브를 막는다. 카드를 만들 때 "HDFS High Availability Using the Quorum Journal Manager" 문서로 세부를 확인한다.

## 2. 학습 경로

1. R-hdfs-10 기술 지문으로 쓰기 경로와 배치 규칙, 장애 처리를 익힌다.
2. B-hdfs-20 `mini-dfs` 트랙에서 네임노드의 이름 공간, 블록 배치, 재복제, 안전 모드를 시뮬레이터로 구현한다.

## 3. 환경

두 카드 모두 추가 도구가 필요 없다. 실제 HDFS 실험은 Docker 로 Hadoop 을 띄워야 하고 노트북에서는 무겁다. 필요해지면 lab 카드를 더한다.

## 4. 아이디어

| 아이디어 | 종류 | 메모 |
|---|---|---|
| 소거 코딩과 복제의 비용 | reading | 저장 오버헤드, 재구성 비용, 작은 파일에서의 비효율 |
| 네임노드 HA 와 펜싱 | reading | 저널노드 다수결, 분할 뇌 방지 |
| 리스와 덧붙이기 회복 | build 단계 | 쓰던 클라이언트가 죽었을 때 리스 회수와 마지막 블록 길이 확정 |
| 랙 인식과 밸런서 | build 단계 | 노드를 더한 뒤 사용률을 맞추는 이동 계획 |

## 5. 출처

- HDFS Architecture: https://hadoop.apache.org/docs/current/hadoop-project-dist/hadoop-hdfs/HdfsDesign.html
- hdfs-default.xml: https://hadoop.apache.org/docs/current/hadoop-project-dist/hadoop-hdfs/hdfs-default.xml
- Output stream 명세(hflush, hsync): https://hadoop.apache.org/docs/current/hadoop-project-dist/hadoop-common/filesystem/outputstream.html
- HDFS Erasure Coding: https://hadoop.apache.org/docs/current/hadoop-project-dist/hadoop-hdfs/HDFSErasureCoding.html
- HDFS Quotas Guide: https://hadoop.apache.org/docs/current/hadoop-project-dist/hadoop-hdfs/HdfsQuotaAdminGuide.html
- White, "The Small Files Problem", Cloudera 블로그: https://www.cloudera.com/blog/technical/the-small-files-problem.html
- Shvachko 외, "The Hadoop Distributed File System", MSST 2010. Ghemawat 외, "The Google File System", SOSP 2003.
