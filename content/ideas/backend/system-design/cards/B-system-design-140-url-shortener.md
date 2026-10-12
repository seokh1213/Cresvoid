---
id: B-system-design-140
title: URL 단축기 만들기
kind: build
track: url-shortener
stage: 01-base62, 02-collisions, 03-expiry
status: 후보
wave: 5
env: 없음
prereq: B-system-design-30
---

# B-system-design-140 URL 단축기 만들기

- 목표: 짧은 키를 만들고 원래 주소로 바꾸는 서비스를 구현한다.
- 1단계 `01-base62`: 순번 ID 의 base62 부호화와 복호화, 키 길이와 만들 수 있는 개수.
- 2단계 `02-collisions`: 해시 앞부분을 쓰는 방식의 충돌과 해소 규칙, 사용자 지정 별칭, 예약어, 같은 원래 주소를 다시 줄일 때의 규칙.
- 3단계 `03-expiry`: 만료와 삭제, 만료된 키의 재사용 금지 기간, 조회 수 집계.
- 난도: 단계마다 1~2시간.
- 함정: 대소문자 구분, 0 과 O 같은 헷갈리는 문자, 충돌 해소가 끝나지 않는 입력.
- 검증: build 스킬의 교차 검증.
- 출처: System Design Interview 1권 8장의 주제.
