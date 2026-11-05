# 재입고 알림을 놓치는 구독자

[1] 쇼핑 카탈로그의 재입고 알림 서비스다. 상품별로 알림을 신청한 사용자를 관리한다. 한 번만 받는 구독자는 알림을 받은 뒤 스스로 구독을 해제하고, 계속 받는 구독자는 다음 재입고도 기다린다. 외부 전송 대신 수신자 목록을 출력하는 단일 스레드 프로그램이다.

## 입출력

[2] 표준 입력에서 명령을 한 줄씩 EOF까지 읽고 명령마다 결과 한 줄을 표준 출력에 쓴다. 빈 줄은 무시한다. 명령과 인자는 공백으로 구분한다. 상품과 사용자 식별자는 영문 소문자로 시작하며 길이가 1~20자인 영문 소문자·숫자·하이픈 문자열이다. 입력은 아래 형식을 지키며 명령은 최대 1,000개다. 잘못된 입력 처리는 요구하지 않는다.

[3] 구독 방식은 `ONCE`와 `KEEP`이다. `WATCH <상품> <사용자> <방식>`은 구독을 목록 끝에 추가하고 `WATCHED <상품> <사용자>`를 출력한다. 이미 그 상품을 구독 중이면 기존 방식과 등록 순서, 초대 설정을 유지하고 `EXISTS <상품> <사용자>`를 출력한다. 같은 사용자가 서로 다른 상품을 구독할 수 있다.

[4] `UNWATCH <상품> <사용자>`는 해당 구독과 그 구독의 초대 설정을 없애고 `UNWATCHED <상품> <사용자>`를 출력한다. 구독이 없으면 `ABSENT <상품> <사용자>`를 출력한다. 해제한 사용자가 다시 신청하면 새 구독으로 목록 끝에 추가한다.

[5] `LIST <상품>`은 현재 구독자를 등록 순서대로 `SUBSCRIBERS <상품> <사용자>:<방식>,<사용자>:<방식>` 형식으로 출력한다. 목록이 비었으면 마지막 필드는 `-`다. 이 명령은 구독 상태를 바꾸지 않는다.

## 알림과 초대

[6] `RESTOCK <상품>`은 재입고 알림 한 회차를 실행한다. 회차 번호는 상품마다 1부터 시작하며 구독자가 없어도 명령마다 1씩 늘어난다. 해당 회차를 시작할 때 구독 중인 모든 사용자는 등록 순서대로 정확히 한 번 알림을 받는다. 알림 도중 새로 등록한 구독자는 이번 회차에 알림을 받지 않는다.

[7] `ONCE` 구독자는 알림을 받은 뒤 자신의 구독을 해제한다. `KEEP` 구독자는 그대로 남는다. 출력은 `NOTIFIED <상품> <회차> <수신자목록>`이며 수신자는 수신 순서대로 쉼표로 잇는다. 수신자가 없으면 목록은 `-`다. 출력 시점에는 해당 회차의 자기 해제와 초대 등록이 모두 끝나 있다.

[8] 가족 공유 관심 목록의 초대를 처리한다. 초대받을 사용자의 동의는 이미 확인됐다고 가정한다. `INVITE <상품> <초대자> <초대받을사용자> <방식>`은 초대자가 다음 알림을 받은 직후 실행할 구독 등록을 예약하고 `INVITED <상품> <초대자> <초대받을사용자>`를 출력한다. 초대자가 해당 상품의 구독자가 아니면 `ABSENT <상품> <초대자>`를 출력하며 예약하지 않는다. 한 구독에는 예약 하나만 두고 새 예약이 이전 예약을 덮어쓴다. 예약만으로 구독자가 추가되지는 않는다.

[9] 예약은 초대자가 다음 알림을 받으면 한 번만 실행되고 사라진다. 초대자는 먼저 자신의 방식에 따라 구독을 유지하거나 해제한 뒤 같은 상품에 초대받은 사용자의 구독을 등록한다. 등록 규칙은 [3]과 같지만 별도 출력은 없다. 이미 구독 중인 사용자는 바뀌지 않는다. 자신을 초대할 수도 있다. 이때 `ONCE` 구독자는 해제 후 새 구독으로 등록된다. 초대 등록으로 생긴 구독은 다음 회차부터 알림을 받는다.

## 수정 범위

[10] 시작 코드는 위 기능을 구현하지만 재입고 알림 결과가 명세와 다르다는 신고가 들어왔다. 명령과 출력 형식을 유지하면서 원인을 찾아 고친다. 스레드나 실제 네트워크 전송은 다루지 않는다.

## 버그 신고

[11] 가족끼리 재입고 알림을 신청하고 초대를 예약했다. 두 차례 재입고 뒤 구독 목록은 예상과 같았지만 수신 기록에는 첫 회차의 sora와 두 번째 회차의 nari가 없었다. 아래 명령으로 확인했다.

```text
WATCH boots mina KEEP
WATCH boots joon ONCE
WATCH boots sora KEEP
INVITE boots mina yuri ONCE
INVITE boots joon nari KEEP
RESTOCK boots
LIST boots
RESTOCK boots
LIST boots
```

[12] 기대한 결과는 다음과 같다. 첫 회차에는 mina, joon, sora가 알림을 받는다. 초대받은 yuri와 nari는 두 번째 회차부터 알림을 받는다.

```text
WATCHED boots mina
WATCHED boots joon
WATCHED boots sora
INVITED boots mina yuri
INVITED boots joon nari
NOTIFIED boots 1 mina,joon,sora
SUBSCRIBERS boots mina:KEEP,sora:KEEP,yuri:ONCE,nari:KEEP
NOTIFIED boots 2 mina,sora,yuri,nari
SUBSCRIBERS boots mina:KEEP,sora:KEEP,nari:KEEP
```

[13] 실제 결과는 다음과 같다. 첫 회차에는 mina와 joon만 알림을 받았고 두 번째 회차에는 mina, sora, yuri만 알림을 받았다.

```text
WATCHED boots mina
WATCHED boots joon
WATCHED boots sora
INVITED boots mina yuri
INVITED boots joon nari
NOTIFIED boots 1 mina,joon
SUBSCRIBERS boots mina:KEEP,sora:KEEP,yuri:ONCE,nari:KEEP
NOTIFIED boots 2 mina,sora,yuri
SUBSCRIBERS boots mina:KEEP,sora:KEEP,nari:KEEP
```
