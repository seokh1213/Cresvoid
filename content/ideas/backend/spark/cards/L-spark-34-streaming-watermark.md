---
id: L-spark-34
title: 워터마크와 늦은 이벤트
kind: lab
track: spark-lab
stage: streaming-watermark
status: 후보
wave: 5
env: spark
prereq: R-spark-10
---

# L-spark-34 워터마크와 늦은 이벤트

- 모드: observe
- 실행 방식: 파일 소스에 이벤트 묶음을 차례로 넣는 구조적 스트리밍 작업을 실행한다.
- 목표: 워터마크 계산, 늦은 이벤트의 반영 여부, 출력 모드별 출력 시점을 예측한다.
- 실험: 지연 한도 10분의 10분 창 집계에 이벤트 시각이 뒤섞인 묶음을 넣는다. append 와 update 모드를 비교한다. foreachBatch 안에서 일부러 실패시켜 같은 batchId 가 다시 오는지 확인한다.
- 예측 문항: 묶음마다 출력되는 창과 개수, 버려지는 이벤트, 재시작 뒤 중복 출력.
- 출처: Structured Streaming 문서의 watermark, output modes, foreachBatch 절.
