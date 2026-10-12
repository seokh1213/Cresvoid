---
id: B-distributed-30
title: 복제 자료형 만들기
kind: build
track: crdt
stage: 01-counters, 02-sets-and-registers, 03-convergence
status: 후보
wave: 5
env: 없음
prereq: 
---

# B-distributed-30 복제 자료형 만들기

- 목표: 복제본마다 따로 고치고 나중에 합쳐도 같은 상태로 모이는 자료형을 구현한다.
- 1단계 `01-counters`: G-Counter 와 PN-Counter 의 갱신, 합치기, 값 읽기.
- 2단계 `02-sets-and-registers`: OR-Set(추가마다 고유 표지, 관찰한 표지만 제거), LWW 레지스터(시각과 복제본 id 로 동점 해소).
- 3단계 `03-convergence`: 메시지 중복·순서 뒤바뀜·유실 뒤 재전송이 섞인 전달에서 모든 복제본이 같은 상태로 모이는지 확인하는 명령.
- 난도: 단계마다 2시간 안팎.
- 함정: 동시 추가와 제거, 같은 시각의 쓰기, 합치기가 멱등·교환·결합 법칙을 지키지 않는 구현.
- 검증: build 스킬의 교차 검증. 생성기가 전달 순서를 섞어 수렴을 확인한다.
- 출처: Shapiro 외 SSS 2011.
- 피할 것: 순서 있는 목록(텍스트 편집) CRDT 까지 넣는 것.
