# 페이지 단위 일괄 처리

## 작업 맥락

[1] 쇼핑 서비스의 주문 후처리 작업이다. 대기 중인 주문을 페이지 단위로 처리한 뒤 완료한 주문은 대기열에서 제거하고 일시적으로 실패한 주문은 대기열 끝으로 보낸다. 이 프로그램은 단일 스레드로 실행한다. 외부 서비스 대신 주문별 실패 횟수를 입력받아 처리 결과를 재현한다.

[2] 표준 입력에서 한 줄에 명령 하나를 EOF까지 읽고 결과를 표준 출력에 쓴다. 빈 줄은 무시한다. 명령과 인자는 하나 이상의 공백으로 나누며 앞뒤 공백은 무시한다. 명령은 대문자이고 주문 ID는 대소문자를 구분한다. 각 실행은 주문과 대기열이 없는 상태에서 시작한다.

## 명령과 주문 상태

[3] `ADD <주문 ID> <실패 횟수>`는 새 주문을 대기열 끝에 등록하고 `ADDED <주문 ID>`를 출력한다. ID는 영문자 또는 숫자로 시작하고 영문자, 숫자, 밑줄, 하이픈으로 이루어진 1~32자 문자열이다. 실패 횟수는 0~1000의 정수다. 등록 시 시도 횟수는 0이고 상태는 `WAITING`이다. 이미 등록한 ID는 완료 여부와 관계없이 다시 등록하지 못하며 `ERROR duplicate-order`를 출력한다.

[4] 주문을 한 번 시도할 때마다 시도 횟수가 1 증가한다. 남은 실패 횟수가 양수이면 1을 차감하고 결과는 `RETRY`이며 상태는 `WAITING`으로 남는다. 남은 실패 횟수가 0이면 결과는 `OK`이고 상태는 `DONE`으로 바뀐다. 실패 횟수가 1인 주문은 첫 시도에 실패하고 두 번째 시도에 성공한다.

[5] `SHOW <주문 ID>`는 `ORDER <주문 ID> <상태> attempts=<누적 시도 횟수> failures=<남은 실패 횟수>`를 출력한다. 없는 ID이면 `ERROR unknown-order`를 출력한다. 완료한 주문도 조회할 수 있다. `QUEUE`는 대기열의 ID를 현재 순서대로 쉼표로 이어 `QUEUE <ID 목록>`으로 출력하고, 비어 있으면 `QUEUE -`를 출력한다. 조회는 상태를 바꾸지 않는다.

## 페이지 실행

[6] `RUN <페이지 크기> <최대 페이지 수>`는 대기열이 빌 때까지 또는 지정한 페이지 수만큼 처리할 때까지 실행한다. 두 인자는 각각 1~1000의 정수다. 각 페이지는 그 페이지를 시작할 때 대기열 앞에서 페이지 크기만큼 고른 주문으로 구성한다. 남은 주문이 적으면 남은 주문만 고른다. 페이지 안의 주문은 앞에서부터 각각 한 번씩 시도한다.

[7] 한 페이지에서 고른 주문은 모두 대기열 앞에서 제거한다. 그중 실패한 주문만 원래 페이지 안의 순서대로 대기열 끝에 다시 넣는다. 아직 고르지 않은 주문의 상대 순서는 유지한다. 다시 넣은 주문은 같은 페이지에서 재시도하지 않으며 다음 페이지부터 다시 고를 수 있다. 다음 `RUN`은 이전 실행이 남긴 대기열과 주문 상태를 이어받는다.

[8] 처리한 페이지마다 `PAGE <번호> <주문 ID>:<결과>,<주문 ID>:<결과>,...`를 출력한다. 페이지 번호는 `RUN`마다 1부터 시작한다. 주문 ID와 결과는 그 페이지에서 시도한 순서대로 적는다. 모든 페이지 출력 뒤에는 `END pages=<처리한 페이지 수> attempts=<시도 횟수> completed=<성공한 주문 수> pending=<남은 대기 주문 수>`를 출력한다. `attempts`와 `completed`는 해당 `RUN`의 집계다. 빈 대기열에 실행하면 페이지 출력 없이 네 값이 모두 0인 `END` 한 줄만 출력한다.

## 잘못된 명령

[9] 알 수 없는 명령, 인자 수가 맞지 않는 명령, 형식이나 범위를 벗어난 인자는 `ERROR invalid-command`를 출력한다. 정수 인자는 ASCII 숫자로만 쓰며 선행 0은 허용한다. 잘못된 명령은 상태를 바꾸지 않는다. 전체 입력은 10000줄 이하이다.

## 버그 신고

[10] 주문 후처리 명령을 이어서 실행하다가 프로그램이 꺼진다. 앞서 처리한 주문은 완료 상태로 조회되는데, 뒤의 실행에서는 페이지 내역도 집계도 나오지 않는다. 사용한 입력은 다음과 같다.

```text
QUEUE
ADD ord-41 0
ADD ord-42 0
RUN 2 1
SHOW ord-41
ADD ord-51 0
ADD ord-52 1
ADD ord-53 0
QUEUE
SHOW ord-53
RUN 2 3
QUEUE
SHOW ord-52
```

[11] 기대한 표준 출력은 다음과 같다. 마지막 조회까지 실행되어야 한다.

```text
QUEUE -
ADDED ord-41
ADDED ord-42
PAGE 1 ord-41:OK,ord-42:OK
END pages=1 attempts=2 completed=2 pending=0
ORDER ord-41 DONE attempts=1 failures=0
ADDED ord-51
ADDED ord-52
ADDED ord-53
QUEUE ord-51,ord-52,ord-53
ORDER ord-53 WAITING attempts=0 failures=0
PAGE 1 ord-51:OK,ord-52:RETRY
PAGE 2 ord-53:OK,ord-52:OK
END pages=2 attempts=4 completed=3 pending=0
QUEUE -
ORDER ord-52 DONE attempts=2 failures=0
```

[12] 실제 표준 출력은 다음 줄에서 끝난다. 표준 오류에는 `java.util.ConcurrentModificationException`이 찍히고 종료 코드는 1이다. 뒤의 `QUEUE`와 `SHOW` 결과도 나오지 않는다.

```text
QUEUE -
ADDED ord-41
ADDED ord-42
PAGE 1 ord-41:OK,ord-42:OK
END pages=1 attempts=2 completed=2 pending=0
ORDER ord-41 DONE attempts=1 failures=0
ADDED ord-51
ADDED ord-52
ADDED ord-53
QUEUE ord-51,ord-52,ord-53
ORDER ord-53 WAITING attempts=0 failures=0
```
