---
id: L-jvm-30
title: 스레드 덤프로 교착 찾기
kind: lab
track: jvm-lab
stage: thread-dump
status: 후보
wave: 4
env: 없음
prereq: 
---

# L-jvm-30 스레드 덤프로 교착 찾기

- 모드: observe 뒤 implement
- 실행 방식: 가끔 멈추는 Kotlin 서비스를 띄우고 `jcmd Thread.print` 로 덤프를 얻는다.
- 목표: 덤프에서 잠금 순서가 엇갈린 스레드를 찾고 잠금 순서를 고정해 고친다.
- 불변식: 부하 동안 멈춤 0건.
- 출처: jcmd 문서.
