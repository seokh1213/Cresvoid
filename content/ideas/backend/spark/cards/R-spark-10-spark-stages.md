---
id: R-spark-10
title: Spark 의 실행 계획과 셔플
kind: reading
track: tech-reading
stage: 05-spark-stages
status: 완료(2026-10-09)
wave: 2
env: 없음
prereq: 
---

# R-spark-10 Spark 의 실행 계획과 셔플

- 분야: 기술(분산 데이터 처리)
- 목표: 변환 목록이 잡, 스테이지, 태스크로 바뀌는 과정을 따라가고 새 파이프라인에서 스테이지 수, 태스크 수, 셔플 위치를 계산한다.
- 다룰 내용: 지연 실행과 행동, 좁은 의존과 넓은 의존, 셔플 경계에서 스테이지가 나뉘는 원리, 파티션과 태스크의 관계, 셔플이 비싼 세 가지 이유(디스크 I/O, 직렬화, 네트워크), reduceByKey 의 맵 쪽 결합과 groupByKey 의 차이, 같은 파티셔너로 나뉜 두 데이터의 조인이 셔플 없이 끝나는 이유, coalesce 와 repartition 의 차이, 적응 실행이 셔플 뒤 통계로 파티션을 병합하고 쏠림을 판정하는 기준(배수 조건과 절대 크기 조건을 함께 만족).
- 난도: 수능 기술 지문 최고난도. 개념 사이의 조건과 인과가 촘촘해야 한다.
- 형식: 2,800~3,200자, 7~8문단, 6문항. `<보기>` 하나는 변환 목록과 각 단계의 파티션 수를 주고 스테이지 수와 태스크 수를 묻는다. 다른 하나는 셔플 뒤 파티션 크기 목록과 판정 기준값을 주고 쏠린 파티션이나 병합 결과를 묻는다.
- 함정과 엣지 케이스: coalesce 를 셔플로 착각한 선지, 행동이 없으면 계산이 일어나지 않는다는 점, 쏠림 판정의 두 조건 가운데 하나만 맞는 파티션, coalesce(1) 이 앞 스테이지의 병렬성까지 없앤다는 점.
- 검증: `<보기>` 의 계산은 Kotlin 스크립트로 확인한다. 개념 서술은 Spark 문서와 RDD 논문으로 확인한다.
- 출처: Spark RDD Programming Guide 의 Shuffle operations 절, Performance Tuning 의 Adaptive Query Execution 절, Dataset API 의 coalesce 설명, Zaharia 외 NSDI 2012.
- 피할 것: 버전마다 바뀌는 기본값을 정답 근거로 삼는 것. 계산에 필요한 수치는 지문이나 `<보기>` 에서 준다. Spark API 이름을 외워야 풀리는 문항.
