---
id: L-spark-32
title: 파티션 덮어쓰기와 작은 파일
kind: lab
track: spark-lab
stage: overwrite-and-small-files
status: 후보
wave: 4
env: spark
prereq: R-spark-10
---

# L-spark-32 파티션 덮어쓰기와 작은 파일

- 모드: observe
- 실행 방식: 날짜로 나눈 테이블에 하루치를 다시 쓰는 작업을 실행한다.
- 목표: 덮어쓰기 모드에 따라 사라지는 파티션과 `partitionBy` 가 만드는 파일 수를 예측한다.
- 실험: STATIC 과 DYNAMIC 모드로 같은 INSERT OVERWRITE 를 실행하고 남은 파티션을 센다. 태스크 수와 파티션 값 수를 바꿔 생기는 파일 수를 센다. coalesce, repartition(열), maxRecordsPerFile 로 파일 수를 조절한다.
- 예측 문항: 실행 뒤 남는 날짜 파티션, 생기는 파일 수, coalesce 를 넣었을 때 앞 단계 병렬성의 변화.
- 출처: SQLConf 의 partitionOverwriteMode 설명, Dataset API 의 coalesce 설명.
