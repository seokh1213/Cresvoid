---
id: B-regex-10
title: 정규식 엔진
kind: build
track: regex
stage: 01-basic, 02-classes, 03-linear
status: 후보
wave: 5
env: 없음
prereq: 
---

# B-regex-10 정규식 엔진

- 목표: 정규식을 해석해 일치를 판정하고 최악 입력에서도 선형 시간에 동작하게 만든다.
- 1단계: 리터럴, 연결, `|`, `*`, `+`, `?`, 괄호, `.`.
- 2단계: 문자 클래스와 범위, 앵커, `{m,n}`, 가장 왼쪽-가장 긴 일치 규칙.
- 3단계: 톰슨 NFA 시뮬레이션. 대규모 테스트에 `(a|aa)*b` 같은 역추적 폭발 패턴을 넣는다.
