---
id: L-load-70
title: 타임아웃, 재시도, 백오프
kind: lab
track: backend-lab
stage: retry-storm
status: 후보
wave: 5
env: k6
prereq: 
---

# L-load-70 타임아웃, 재시도, 백오프

- 모드: implement
- 난도: 보통.
- 목표: 하위 서비스가 느려질 때 재시도가 장애를 키우지 않게 한다.
- 비울 핵심: 하위 호출 클라이언트의 타임아웃, 재시도 횟수, 상한 있는 지수 백오프와 지터, 재시도 예산.
- 불변식: 장애 구간의 하위 요청 수 ≤ 상위 요청 수 × 1.2, 장애가 끝난 뒤 10초 안에 지연이 정상 수준으로 회복된다.
