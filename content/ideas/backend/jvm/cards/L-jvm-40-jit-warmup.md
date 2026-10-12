---
id: L-jvm-40
title: JIT 예열과 측정 함정
kind: lab
track: jvm-lab
stage: jit-warmup
status: 후보
wave: 5
env: 없음
prereq: 
---

# L-jvm-40 JIT 예열과 측정 함정

- 모드: observe
- 실행 방식: 같은 함수를 반복 실행하며 구간별 시간을 재는 Kotlin 스크립트.
- 목표: 예열 전후의 차이, 결과를 쓰지 않아 계산이 사라지는 경우, 측정 순서가 결과를 바꾸는 경우를 예측한다.
- 예측 문항: 첫 1,000번과 그 뒤의 평균 시간 비율, 결과를 버린 루프의 시간.
- 출처: The java Command 의 JIT 관련 옵션.
