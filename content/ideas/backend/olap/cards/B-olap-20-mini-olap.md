---
id: B-olap-20
title: 열 지향 집계 엔진 만들기
kind: build
track: mini-olap
stage: 01-aggregate, 02-scale, 03-rollup
status: 완료(2026-10-10)
wave: 2
env: 없음
prereq: R-olap-10
---

# B-olap-20 열 지향 집계 엔진 만들기

- 목표: 메모리 안의 열 지향 테이블에 집계 질의를 실행하는 엔진을 만들고 대규모 데이터에서 열 배치와 부호화가 왜 필요한지 체감한다.
- 입출력 모양: 테이블 정의(열 이름과 자료형), 행 적재, `SELECT 집계 FROM 표 WHERE 조건 GROUP BY 열 ORDER BY ... LIMIT` 의 작은 부분집합이다. 결과 출력 형식과 숫자 반올림 규칙은 명세가 정한다.
- 1단계 `01-aggregate`: COUNT(*), COUNT(열), SUM, AVG, MIN, MAX 와 NULL 규칙(COUNT(*) 는 NULL 행도 세고 나머지는 건너뛰며 빈 입력의 SUM·MAX 는 NULL), WHERE 의 3값 논리, GROUP BY 의 NULL 묶음, ORDER BY 의 NULL 위치.
- 2단계 `02-scale`: 행 수백만 개, 문자열 차원 수십만 종류. 행 객체 목록으로는 제한 시간을 넘고 열 배열과 문자열 사전 부호화로 통과하게 만든다. 메모리 상한도 명세에 둔다.
- 3단계 `03-rollup`: GROUPING SETS, ROLLUP, CUBE 와 어느 열이 묶음에서 빠졌는지 알려 주는 GROUPING 표시. 사전 집계 테이블을 만들고 질의가 그 테이블로 답할 수 있는지 판정하는 규칙(차원 포함 관계, 합칠 수 있는 집계만)을 더한다. AVG 는 SUM 과 COUNT 로 다시 계산해야 한다는 점이 드러난다.
- 난도: 1단계 2시간, 2단계 2~3시간, 3단계 3시간.
- 함정: AVG 의 반올림, 빈 그룹, NULL 과 빈 문자열의 구분, ROLLUP 이 만드는 총계 행의 NULL 과 실제 NULL 값의 구분, 사전 집계로 답할 수 없는 COUNT(DISTINCT).
- 검증: build 스킬의 교차 검증. 생성기 입력으로 단순한 행 단위 계산기와 대조한다.
- 출처: 이 도메인 README 의 '모델링과 사전 집계' 절, Kimball 의 Data Warehouse Toolkit.
- 피할 것: SQL 파서 구현이 단계의 대부분을 차지하게 만드는 것. 문법은 작게 고정한다.
