---
id: B-system-design-70
title: 뉴스 피드 팬아웃 만들기
kind: build
track: news-feed
stage: 01-fanout-on-write, 02-fanout-on-read, 03-hybrid
status: 후보
wave: 4
env: 없음
prereq: 
---

# B-system-design-70 뉴스 피드 팬아웃 만들기

- 목표: 글을 쓸 때 팔로워 피드에 미리 복사해 두는 방식과 읽을 때 모으는 방식을 구현하고 비용을 비교한다.
- 1단계 `01-fanout-on-write`: 팔로우 관계, 글쓰기 때 팔로워 피드에 넣기, 피드 길이 한도, 쓰기 비용(넣은 수) 보고.
- 2단계 `02-fanout-on-read`: 읽을 때 팔로우한 사람들의 글을 합치기, 읽기 비용(읽은 글 수) 보고.
- 3단계 `03-hybrid`: 팔로워가 기준보다 많은 사용자는 읽을 때 합치는 혼합 방식, 팔로우 취소와 글 삭제의 반영, 두 방식의 결과가 같음을 확인.
- 난도: 단계마다 2시간 안팎. 대규모 테스트는 팔로워가 매우 많은 사용자를 포함한다.
- 함정: 같은 시각 글의 순서, 팔로우 직후의 피드, 삭제된 글이 남은 피드, 기준 경계의 사용자.
- 검증: build 스킬의 교차 검증.
- 출처: System Design Interview 1권 11장의 주제.
