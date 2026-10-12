---
id: B-system-design-60
title: 웹 크롤러 만들기
kind: build
track: crawler
stage: 01-frontier, 02-dedup, 03-politeness
status: 후보
wave: 4
env: 없음
prereq: 
---

# B-system-design-60 웹 크롤러 만들기

- 목표: 입력으로 주어진 가짜 웹 그래프를 우선순위와 예의 규칙을 지키며 수집하는 크롤러를 만든다.
- 1단계 `01-frontier`: 수집할 주소 큐, 우선순위, 깊이 제한, 가상 시계로 정한 수집 순서.
- 2단계 `02-dedup`: 주소 정규화(대소문자, 끝의 슬래시, 조각 식별자), 본문 해시로 같은 내용 거르기, 끝없이 이어지는 달력 같은 함정.
- 3단계 `03-politeness`: 호스트마다 최소 간격, 단순화한 robots 규칙, 실패한 요청의 재시도와 지수 백오프.
- 난도: 단계마다 2시간 안팎. 대규모 테스트는 페이지 수십만 개다.
- 함정: 정규화 순서, 리다이렉트 고리, 호스트별 대기열이 비었을 때의 다음 선택.
- 검증: build 스킬의 교차 검증.
- 출처: System Design Interview 1권 9장의 주제, RFC 3986(URI 문법), RFC 9309(Robots Exclusion Protocol).
