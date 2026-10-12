---
id: L-spark-33
title: 재실행과 부수효과
kind: lab
track: spark-lab
stage: retries-and-side-effects
status: 후보
wave: 5
env: spark
prereq: R-spark-10
---

# L-spark-33 재실행과 부수효과

- 모드: observe
- 실행 방식: 일부 태스크를 일부러 실패시키는 작업을 `spark-submit` 으로 실행한다.
- 목표: 태스크 재실행과 투기 실행이 누산기와 외부 쓰기에 주는 영향을 예측한다.
- 실험: 변환 안과 행동 안에서 누산기를 늘린다. 투기 실행을 켜고 느린 태스크를 만든다. foreach 안에서 외부 파일에 기록한다. 클로저 안에서 드라이버 변수를 바꾼다.
- 예측 문항: 누산기 최종값, 외부 기록 줄 수, 드라이버 변수 값.
- 출처: Spark RDD Programming Guide 의 Accumulators 와 Understanding closures 절, Configuration 의 speculation 항목.
