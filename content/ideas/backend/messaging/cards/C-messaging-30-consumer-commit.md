---
id: C-messaging-30
title: 오프셋 커밋 시점과 중복 처리
kind: code
track: code-reading
stage: consumer-commit
status: 후보
wave: 4
env: 없음
prereq: R-messaging-10
---

# C-messaging-30 오프셋 커밋 시점과 중복 처리

- 도메인: 주문 이벤트를 읽어 재고와 포인트를 바꾸는 소비자. 브로커와 장애는 메모리 안의 가짜 구현이고 입력 명령으로 소비자를 멈추거나 재조정을 일으킨다.
- 겨냥: 처리와 커밋의 순서, 묶음 단위 커밋과 개별 처리 실패, 멱등 처리 기록의 범위, 재조정 콜백에서 커밋하지 않은 오프셋, 예외가 묶음 처리 루프를 빠져나가는 경로.
- 형식: 250~350줄, 파일 3~5개, 6문항(실행 결과 예측 short 2~3). 목표 25분.
- 검증: 문항 정답은 코드 실행으로 확인한다.
- 출처: Kafka Consumer Configs, Message Delivery Semantics 절.
