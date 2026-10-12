---
id: F-kotlin-70
title: 가격 문자열 출력
kind: fix
track: bug-fix
stage: locale-format
status: 후보
wave: 5
env: 없음
prereq: 
---

# F-kotlin-70 가격 문자열 출력

- 맥락: 가격과 할인율을 문자열로 만들어 알림 문구와 정산 파일에 쓴다. 정산 파일은 외부 시스템이 읽으므로 숫자 형식이 고정되어 있다(천 단위 쉼표, 소수점 마침표). 서버의 기본 Locale 은 설정 명령으로 바뀐다.
- 버그 후보: `"%,d".format(...)`, `"%.2f".format(...)` 처럼 Locale 을 주지 않은 형식화가 기본 Locale 을 따라 독일어 Locale 에서 `1.234.567` 과 `1,50` 이 나온다(먼저 고른다), `String.format` 은 고쳤지만 `NumberFormat.getInstance()` 가 남은 경우.
- 버그 신고: "새로 늘린 서버에서 만든 정산 파일만 외부 시스템이 읽지 못한다. 금액 칸이 `1.234.567` 처럼 나온다."
- 형식: 150~220줄, 파일 2~3개.
- 난도: 같은 코드가 서버마다 다른 결과를 내 환경 차이를 의심해야 한다.
- 검증: 숨은 테스트는 Locale 설정 명령을 바꿔 가며 같은 결과가 나오는지 확인한다. `Locale.ROOT` 를 넘기거나 `DecimalFormat` 의 기호를 고정한 수정이 모두 통과해야 한다.
- 출처: Java SE 21 API 문서의 `Formatter`(Locale 을 주지 않으면 기본 Locale 을 쓴다)와 `Locale.setDefault`. Kotlin 표준 라이브러리 문서의 `String.format`.
- 피할 것: JVM 시작 옵션으로 Locale 을 바꾸는 테스트. 입력 명령으로 `Locale.setDefault` 를 부른다.
