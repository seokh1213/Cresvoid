---
id: B-system-design-160
title: 객체 저장소 만들기
kind: build
track: object-store
stage: 01-versioning, 02-multipart, 03-durability
status: 후보
wave: 5
env: 없음
prereq: 
---

# B-system-design-160 객체 저장소 만들기

- 목표: 메타데이터와 데이터를 나눈 객체 저장소의 버전 관리, 멀티파트 업로드, 내구성을 구현한다.
- 1단계 `01-versioning`: 버킷, 객체 쓰기·읽기·지우기, 버전과 삭제 표시, 버전 목록.
- 2단계 `02-multipart`: 멀티파트 업로드의 시작, 조각 업로드, 완료, 중단, 남은 조각 청소.
- 3단계 `03-durability`: 노드 배치, 복제와 소거 코딩의 저장 비용 계산, 노드를 잃었을 때의 복구 계획.
- 난도: 단계마다 2~3시간.
- 함정: 삭제 표시가 최신 버전일 때의 읽기, 같은 조각 번호로 다시 업로드한 경우, 완료 전에 읽기.
- 검증: build 스킬의 교차 검증.
- 출처: System Design Interview 2권 9장의 주제, HDFS Erasure Coding 문서(hdfs README 의 '운영 한계' 절).
