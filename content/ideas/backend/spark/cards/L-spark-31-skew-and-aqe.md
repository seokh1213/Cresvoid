---
id: L-spark-31
title: 데이터 쏠림과 적응 실행
kind: lab
track: spark-lab
stage: skew-and-aqe
status: 후보
wave: 4
env: spark
prereq: B-spark-20
---

# L-spark-31 데이터 쏠림과 적응 실행

- 모드: tune
- 실행 방식: 키 하나에 행이 몰린 조인을 `spark-submit` 으로 실행한다.
- 목표: 쏠림이 태스크 시간 분포에 나타나는 모습을 읽고 적응 실행, 브로드캐스트 힌트, 소금(salting) 가운데 알맞은 방법으로 푼다.
- 실험: 적응 실행을 끈 실행과 켠 실행을 비교한다. 쏠림 판정 기준(배수, 절대 크기)을 바꿔 쪼개지는 파티션이 달라지는지 확인한다.
- 불변식: 결과는 기준 결과와 같다. 가장 긴 태스크 시간이 중앙값의 정해진 배수 이하다. 셔플 파티션 수는 정해진 상한 이하다.
- 출처: Spark Performance Tuning 의 Adaptive Query Execution 과 Join Strategy Hints 절.
