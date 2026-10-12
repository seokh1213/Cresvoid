# 5단계: 묶음 저장과 역압력

## 1. 목표

[1.1] 가격 변경 기록을 Flow 로 받아 순서대로 묶어 저장하라. 묶음은 정해진 개수에 도달하거나 첫 기록을 받은 뒤 정해진 시간이 지나면 닫힌다. 저장은 한 번에 하나만 진행하고, 저장을 기다리는 묶음의 수를 제한하라. 이 단계에서 구현할 함수는 `Batching.kt` 의 `persistBatches` 다.

[1.2] 1~4단계의 명령과 출력은 그대로다. 시작 코드에는 이 단계의 명령을 읽는 `BatchCommands.kt`, 가짜 원본과 저장소, 가상 시각 실행 틀이 들어 있다. Flow 확장 함수 `persistBatches` 의 수신 Flow, 두 매개변수, 반환형은 유지하라. `BatchServices` 의 메서드는 관찰과 저장에 쓰며, 구현에 필요한 내부 상태와 코루틴 구조는 직접 정하라.

## 2. 입력과 실행

[2.1] `CHANGE <시각> <id>` 는 다음 실행의 변경 기록을 하나 더한다. 시각은 1 이상이고 앞 `CHANGE` 의 시각보다 늦다. id 는 한 실행 안에서 겹치지 않는 1~20자의 영문 소문자·숫자·하이픈이다. `CHANGE` 는 없어도 된다.

[2.2] `SOURCE <시각> END|FAIL` 은 원본이 정상 종료하거나 실패하는 가장 이른 시각을 정한다. 시각은 1 이상이고 마지막 `CHANGE` 의 시각보다 늦다. 한 실행에 `SOURCE` 는 정확히 하나이며, 그 뒤에는 `CHANGE` 를 더하지 않는다.

[2.3] `PERSIST <개수 N> <기간 T> <대기 K> <저장 시간 D> <실패 순번 F> <취소 시각 C>` 가 지금까지의 `CHANGE` 와 `SOURCE` 로 한 번 실행한다. N 은 1~100,000, T 와 D 는 1~1,000,000,000, K 와 F 는 0~100,000, C 는 0~1,000,000,000 이다. F 가 0 이면 저장은 실패하지 않고, 양수면 그 순번의 저장이 끝날 때 실패한다. C 가 0 이면 취소하지 않고, 양수면 그 시각에 실행을 취소한다. 실행 뒤에는 모아 둔 변경과 원본 종료 계획을 비운다.

[2.4] 모든 시각의 단위는 가상 밀리초다. 원본은 변경의 예정 시각까지 기다려 `offer <id>` 를 기록한 뒤 `BatchChange` 를 Flow 로 내보낸다. 앞 기록을 넘기느라 예정 시각이 지났으면 그 일을 마친 시각에 다음 기록을 바로 제안한다. `SOURCE` 도 앞 기록을 넘긴 뒤 정한 시각까지 기다리므로 실제 종료나 실패는 예정 시각보다 늦을 수 있다. 정상 종료에는 `source end`, 실패에는 `source fail` 을 기록한다.

[2.5] `persistBatches` 가 변경을 받아 묶음에 넣을 때 `services.take(change)` 를 정확히 한 번 호출하라. 그 호출이 기록하는 줄은 `take <id>` 다. 원본의 `offer` 와 묶음의 `take` 는 역압력이 걸리면 다른 시각에 나타난다.

## 3. 묶음

[3.1] 비어 있는 묶음에 첫 변경을 받은 시각을 s 라 하자. 그 묶음의 마감 시각은 s+T 다. 뒤의 변경을 받아도 마감 시각은 바뀌지 않는다. 마감 전 N 번째 변경을 받으면 그 시각에 묶음을 닫는다. N 에 도달하지 못하면 마감 시각에 닫는다. 닫힌 묶음에는 받은 순서대로 기록이 들어간다.

[3.2] 변경을 받는 시각이 열린 묶음의 마감 시각과 같거나 늦으면 먼저 그 묶음을 닫고, 그 변경으로 새 묶음을 시작하라. 따라서 N 번째 변경과 시간 조건이 같은 시각에 맞으면 그 변경은 다음 묶음에 들어간다. 마감 시각에 변경과 타이머 가운데 무엇이 먼저 실행돼도 결과는 같아야 한다.

[3.3] 묶음을 닫을 때 `services.seal(batch)` 를 한 번 호출하라. 기록은 `seal <id1,id2,...>` 다. 기록이 하나도 없는 빈 묶음은 닫거나 저장하지 않는다. 닫힌 묶음의 내용은 이후 바꾸지 않는다.

[3.4] 원본이 정상 종료하면 열려 있는 묶음을 그 시각에 닫고, 앞서 닫힌 묶음과 함께 모두 저장한 뒤 반환하라. `SOURCE END` 뒤에는 새 변경을 받지 않는다. 변경이 하나도 없으면 묶음을 만들지 않고 종료한다.

## 4. 저장과 역압력

[4.1] 묶음은 닫힌 순서대로 저장하라. 저장은 `services.save(batch)` 로 실행하고, 앞 저장이 끝나기 전에는 다음 저장을 시작하지 마라. 저장 중인 묶음은 대기 묶음 수에서 빼고, 대기 큐에는 최대 K 개를 담아라. K 가 0 이면 저장기가 받을 수 있을 때만 닫힌 묶음을 넘긴다.

[4.2] 대기 큐가 찼는데 새 묶음을 닫으면 그 묶음을 넘길 때까지 입력을 더 받지 마라. 이때 넘기려는 묶음 하나는 대기 큐 바깥에 있을 수 있다. 원본 Flow 는 다음 변경을 제안한 뒤 `take` 를 기다릴 수 있고, 그동안 이후 변경의 `offer` 도 늦어진다. 대기 큐는 묶음을 버리거나 순서를 바꾸지 않는다.

[4.3] 저장소는 저장 시작에 `start save <순번> <id1,id2,...>` 를 기록한다. D 가 지난 뒤 성공하면 `ok save <순번> <id1,id2,...>`, F 번째면 `fail save <순번> <id1,id2,...>` 를 기록하고 `SaveFailure` 를 던진다. 진행 중인 저장이 취소되면 `cancel save <순번> <id1,id2,...>` 를 기록한다. 순번은 실행마다 1부터 센다.

[4.4] 모든 저장이 성공하면 `BatchReport(saved, changes)` 를 반환하라. saved 는 성공한 묶음 수이고 changes 는 성공한 묶음에 든 기록 수다. 결과 줄은 `PERSIST DONE <종료 시각> saved=<saved> changes=<changes>` 다.

## 5. 실패, 취소, 같은 시각

[5.1] 원본이 실패하면 열린 묶음을 버리고 새 저장을 시작하지 마라. 진행 중인 저장은 취소하고, 이미 대기 중인 묶음도 버린 뒤 `SourceFailure` 를 전파하라. 이미 성공한 저장은 되돌리지 않는다. 결과 줄은 `PERSIST FAIL SOURCE <실패를 처리한 시각>` 이다.

[5.2] 저장이 실패하면 원본 수집과 열려 있는 묶음의 처리를 멈추고 대기 묶음을 버린 뒤 `SaveFailure` 를 전파하라. 이미 성공한 저장은 되돌리지 않는다. 결과 줄은 `PERSIST FAIL SAVE <시각> <순번>` 이다.

[5.3] C 가 양수면 취소 타이머를 학습자 함수보다 먼저 등록한다. 같은 시각에 취소와 다른 일이 예정돼 있으면 취소를 먼저 처리한다. 취소는 원본 수집과 저장을 멈추고 열린 묶음과 대기 묶음을 버린다. 취소를 일반 실패로 바꾸지 마라. 결과 줄은 `PERSIST CANCELLED <시각>` 이다.

[5.4] 같은 시각의 사건 줄은 1단계 [3.12] 대로 사건 문구의 사전순으로 정렬한다. 따라서 출력에서 `seal` 이 `take` 보다 먼저 보여도 두 호출의 실행 순서를 뜻하지 않는다. 검사 입력은 원본 실패와 저장 실패를 한 실행에 함께 두지 않고, 원본 실패 시각을 열린 묶음의 마감 시각이나 진행 중인 저장의 완료 시각에 맞추지 않는다.

## 6. 예시

[6.1] 첫 변경 a 는 2 에 들어와 7 에 마감된다. b 는 정확히 7 에 제안되므로 a 를 먼저 닫고 새 묶음을 시작한다. c 는 b 의 묶음을 9 에 N=2 로 닫는다. 저장은 순서대로 끝난다.

```
> CHANGE 2 a
> CHANGE 7 b
> CHANGE 9 c
> SOURCE 15 END
> PERSIST 2 5 0 8 0 0
2 offer a
2 take a
7 offer b
7 seal a
7 start save 1 a
7 take b
9 offer c
9 seal b,c
9 take c
15 ok save 1 a
15 source end
15 start save 2 b,c
23 ok save 2 b,c
PERSIST DONE 23 saved=2 changes=3
```

[6.2] K=0 이고 저장이 느리면 원본이 뒤 기록을 제안하는 시각도 늦어진다. d 는 예정 시각 5 보다 늦은 12 에 제안된다. 정상 종료 뒤에는 남은 d 도 저장한다.

```
> CHANGE 2 a
> CHANGE 3 b
> CHANGE 4 c
> CHANGE 5 d
> SOURCE 6 END
> PERSIST 1 20 0 10 0 0
2 offer a
2 seal a
2 start save 1 a
2 take a
3 offer b
3 seal b
3 take b
4 offer c
12 offer d
12 ok save 1 a
12 seal c
12 start save 2 b
12 take c
22 ok save 2 b
22 seal d
22 source end
22 start save 3 c
22 take d
32 ok save 3 c
32 start save 4 d
42 ok save 4 d
PERSIST DONE 42 saved=4 changes=4
```

[6.3] 실패한 원본은 열린 묶음을 저장하지 않는다. 다음 세 실행은 각각 원본 실패, 저장 실패, 취소를 보인다.

```
> CHANGE 2 a
> CHANGE 3 b
> SOURCE 5 FAIL
> PERSIST 3 20 1 10 0 0
2 offer a
2 take a
3 offer b
3 take b
5 source fail
PERSIST FAIL SOURCE 5

> CHANGE 2 a
> CHANGE 3 b
> SOURCE 5 END
> PERSIST 1 20 1 10 1 0
2 offer a
2 seal a
2 start save 1 a
2 take a
3 offer b
3 seal b
3 take b
5 source end
12 fail save 1 a
PERSIST FAIL SAVE 12 1

> CHANGE 2 a
> CHANGE 3 b
> SOURCE 20 END
> PERSIST 1 20 1 10 0 5
2 offer a
2 seal a
2 start save 1 a
2 take a
3 offer b
3 seal b
3 take b
5 cancel save 1 a
PERSIST CANCELLED 5
```

## 7. 검사

[7.1] 1단계 [7.1]~[7.5], [7.7], [7.10] 을 그대로 따른다. `TRACE OFF` 면 사건 줄을 생략하고 결과 줄만 낸다.

[7.2] 무작위 입력은 여러 실행에서 N, T, K, D 를 바꾸고 개수와 시간의 경계, 정상 종료, 원본 실패, 저장 실패, 취소, 느린 저장의 조합을 확인한다. 대규모 입력은 변경 약 3,000개와 실행 여러 개를 사용한다. 제한 시간은 15초다. 자료구조의 점근 성능을 가르는 단계가 아니므로 무작위 입력으로 조합을 넓게 확인한다.

[7.3] 5단계를 검사할 때는 1~4단계 테스트를 먼저 다시 실행한다. 이전 명령의 출력은 바꾸지 않는다.
