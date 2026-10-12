---
id: L-jvm-10
title: 메모리 한도와 기본 힙 크기
kind: lab
track: jvm-lab
stage: container-heap
status: 후보
wave: 4
env: 없음
prereq: 
---

# L-jvm-10 메모리 한도와 기본 힙 크기

- 모드: observe
- 실행 방식: Kotlin 스크립트가 `-XX:MaxRAM` 과 `-XX:MaxRAMPercentage` 를 바꿔 가며 JVM 을 띄우고 최대 힙과 메모리 부족 시점을 출력한다.
- 목표: 힙을 정하지 않은 JVM 이 쓰는 힙 크기와 메모리 한도를 넘는 시점을 예측한다.
- 예측 문항: 한도 1GB 에서 최대 힙, 큰 배열을 만들 때 OutOfMemoryError 가 나는 크기, 힙 밖 메모리가 한도를 넘기는 경우.
- 출처: The java Command(JDK 21).
