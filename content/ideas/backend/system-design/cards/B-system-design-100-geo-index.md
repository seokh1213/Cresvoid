---
id: B-system-design-100
title: 근처 장소 검색 만들기
kind: build
track: geo-index
stage: 01-geohash, 02-radius-search, 03-quadtree
status: 후보
wave: 4
env: 없음
prereq: 
---

# B-system-design-100 근처 장소 검색 만들기

- 목표: 위치로 근처 장소를 찾는 공간 색인을 만든다.
- 1단계 `01-geohash`: 지오해시 부호화와 복호화, 정밀도, 이웃 칸 구하기.
- 2단계 `02-radius-search`: 반경 안 후보 칸 고르기, 거리 계산(공식과 반올림은 명세가 정함), 결과 정렬.
- 3단계 `03-quadtree`: 칸 안 장소 수가 기준을 넘으면 나누는 쿼드트리, 가장 가까운 k 개.
- 난도: 단계마다 2~3시간. 대규모 테스트는 장소 수백만 개다.
- 함정: 경도 180도 근처, 극지방, 칸 경계에 걸친 반경, 부동소수 비교.
- 검증: build 스킬의 교차 검증. 생성기 입력으로 모든 장소와 거리를 재는 단순한 방법과 대조한다.
- 출처: System Design Interview 2권 1장의 주제.
