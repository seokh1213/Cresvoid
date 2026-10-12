---
id: L-spark-30
title: NULL, 중복, 열 순서의 의미론
kind: lab
track: spark-lab
stage: null-semantics
status: 후보
wave: 4
env: spark
prereq: R-spark-10
---

# L-spark-30 NULL, 중복, 열 순서의 의미론

- 모드: observe 뒤 implement
- 실행 방식: 작은 데이터셋에 `spark-sql` 로 질의를 실행한다.
- 목표: 결과 행 수가 예상과 다른 질의의 원인을 Spark SQL 의미론으로 설명하고 고친다.
- 실험 묶음: `=` 조인과 `<=>` 조인의 행 수, NULL 이 섞인 `NOT IN` 하위 질의, GROUP BY 와 DISTINCT 의 NULL 묶음, COUNT(*) 와 COUNT(열), 열 순서가 다른 두 DataFrame 의 `union`, ANSI 모드에서 실패하는 CAST 와 넘침.
- 예측 문항: 실험마다 결과 행 수, 결과 값, 오류 여부를 묻는다.
- implement 목표: 주어진 질의 묶음을 NULL 과 열 순서에 안전하게 고쳐 기대 결과를 낸다.
- 출처: Spark NULL Semantics, Migration Guide(3.5 에서 4.0), Dataset API 의 union 과 unionByName 설명.
