# 6단계: 수집할 때 시작하는 Flow

## 1. 개요

[1.1] 수집할 때마다 새로 실행되는 cold Flow를 더하라. `map`, `filter`, `take`를 순서대로 적용하고, `take`가 필요한 값을 모두 받으면 상류의 남은 본문을 실행하지 말아야 한다. Flow 본문의 지연과 정리도 실제 `suspend` 실행에 참여한다. 1~5단계 입력의 출력은 그대로다.

## 2. 배경 개념

[2.1] cold Flow 정의는 값을 미리 만들거나 작업을 미리 시작하지 않는다. 수집자가 `collect`를 호출할 때 생산 본문을 처음부터 실행한다. 같은 Flow를 두 번 수집하면 두 실행은 각각 자신의 중단 지점과 정리 구간을 갖는다.

[2.2] `map`은 각 값에 함수를 적용하고, `filter`는 조건에 맞는 값만 아래로 보내며, `take`는 지정한 개수만 받은 뒤 상류를 멈춘다. 이 단계에서는 연산자를 `map`, `filter`, `take` 순서로 고정한다. `take`가 멈추는 것은 수집자의 Job 취소와 다르므로 수집자는 정리 뒤 다음 명령을 실행할 수 있다.

[2.3] Flow 본문은 수집자의 실행 문맥에서 순차적으로 실행된다. 상류의 `DELAY`는 수집자를 중단시키고, 중단이 끝나야 뒤의 `EMIT`을 처리한다. 여러 작업이 같은 Flow 정의를 수집하더라도 각각 독립해 진행한다.

## 3. 입출력 규칙

[3.1] `TASK` 정의를 모두 적은 뒤 채널·잠금 선언과 `ROOT` 선언 전에 `FLOW <이름>`부터 `END`까지의 정의를 적을 수 있다. Flow 정의는 중복할 수 없다. 같은 Flow 정의를 여러 작업이나 한 작업의 여러 `COLLECT`에서 다시 쓸 수 있다.

[3.2] Flow 본문에서 `LOG <값>`을 실행하면 수집 작업의 경로로 `FLOWLOG <Flow 이름> <값>` 사건을 낸다. 값이 필터를 통과하면 수집 작업의 경로로 `FLOW <Flow 이름> <변환된 정수>` 사건을 낸다. 떨어진 값은 사건을 내지 않는다.

[3.3] `EMIT` 값은 -1,000,000,000 이상 1,000,000,000 이하이고 `MAP` 덧셈 값은 -1,000,000 이상 1,000,000 이하이다. 정수는 `0` 또는 음수 기호가 없는 0 아닌 십진수, 또는 `-`와 0 아닌 십진수를 이은 형태다. `+`와 `-0`, 앞자리 0은 허용하지 않는다. `TAKE` 개수는 0 이상 1,000,000 이하이며 0은 개수 제한이 없다는 뜻이다.

## 4. Flow 정의와 수집 규칙

[4.1] Flow 본문에는 `EMIT <정수>`, `DELAY <밀리초>`, `LOG <값>`, `FAIL <코드>`를 적을 수 있다. `FINALLY` 뒤에는 `LOG`, `DELAY`, `SHIELD_DELAY`, `FAIL`을 적을 수 있다. 지연·실패·정리의 뜻은 3~4단계의 작업 본문과 같지만 Flow에는 별도의 Job, `START`, `DONE`, `CANCELLED` 사건이 없다.

[4.2] `COLLECT <Flow 이름> [MAP <정수>] [FILTER <ALL|EVEN|ODD>] [TAKE <개수>]`는 현재 작업에서 Flow를 새로 실행하고 끝날 때까지 중단 가능한 명령이다. `COLLECT`에 들어갈 때 자신의 Job 취소를 확인하며, 이미 취소됐다면 Flow 본문과 정리를 시작하지 않고 작업의 정리로 간다. 선택 부분은 적을 때 `MAP`, `FILTER`, `TAKE` 순서여야 하며 각 부분은 한 번만 적는다. 생략하면 차례대로 `MAP 0`, `FILTER ALL`, `TAKE 0`을 쓴다.

[4.3] 각 `EMIT` 값을 먼저 `MAP` 정수와 더한다. `FILTER ALL`은 모두 통과시키고, `EVEN`과 `ODD`는 변환한 정수의 짝수·홀수만 통과시킨다. 음수의 짝수·홀수도 2로 나눈 나머지가 0인지로 판단한다. 통과한 값마다 [3.2]의 `FLOW` 사건을 낸다. `TAKE`는 필터를 통과한 값만 센다.

[4.4] 양수 `TAKE` 개수에 도달하면 그 값을 출력한 직후 Flow 본문의 나머지 명령을 건너뛰고 Flow의 `FINALLY`를 실행한다. 정리가 끝나면 `COLLECT` 뒤 명령으로 돌아온다. 이 상류 중지는 수집 작업의 Job을 취소하지 않는다. 그래서 이때 Flow 정리의 보통 `DELAY`도 정상적으로 중단하고 재개할 수 있다.

[4.5] `TAKE` 개수에 못 미친 채 Flow 본문이 끝나면 그때까지의 값만 출력하고 정리 뒤 `COLLECT`를 끝낸다. 한 수집이 끝나도 다른 수집의 본문 위치, 타이머, 출력 값은 바뀌지 않는다. Flow 정의가 실행 전에 값을 저장하거나 앞선 수집의 값을 재사용하지 않는다.

[4.6] `EMIT`에 들어갈 때와 Flow 정리가 끝나 `COLLECT`에서 돌아가기 직전에 자신의 Job 취소를 확인한다. 이미 Flow 본문에 들어간 수집 작업이 취소되면 다음 확인 지점에서 남은 본문을 건너뛰고 Flow의 `FINALLY`를 한 번 실행한 뒤 작업의 `FINALLY`로 간다. 정리 뒤 확인에서 취소를 발견하면 Flow 정리를 반복하지 않는다. Flow 정리의 `DELAY`는 취소 중에 즉시 중단되고 `SHIELD_DELAY`는 기다린다. Flow의 `FAIL`은 수집 작업의 일반 실패가 된다. 이미 주된 실패가 있으면 4단계의 `SUPPRESSED` 규칙을 적용한다.

## 5. 오류 규칙

[5.1] 앞 단계의 입력 검증 방식과 오류 코드를 따른다. 같은 Flow 이름을 두 번 정의하면 `DUPLICATE`이고, 없는 Flow를 `COLLECT`하면 `UNKNOWN`이다. Flow 정의가 `TASK` 안이나 자원·루트 선언 뒤에 있으면 `FORMAT`이다. 허용하지 않은 Flow 명령, 정수 범위, 선택 부분의 순서나 중복도 `FORMAT`이다.

[5.2] Flow 본문의 `FINALLY`는 최대 한 번이고 그 뒤에는 [4.1]에 적힌 정리 명령만 올 수 있다. `EMIT`은 Flow의 본문에서만 쓸 수 있으며 `TASK`나 Flow 정리 구간에서는 `FORMAT`이다. Flow의 정상 종료와 `take` 종료는 입력 형식 오류가 아니다.

## 6. 예시

[6.1] 첫 수집은 값을 하나 받은 뒤 상류를 멈추고 정리한다. 두 번째 수집은 시각 0에서 Flow 본문을 새로 시작해 지연 뒤 두 번째 값을 받는다.

```text
> TASK worker LAUNCH
> COLLECT numbers MAP 1 FILTER EVEN TAKE 1
> COLLECT numbers
> END
> FLOW numbers
> LOG opening
> EMIT 1
> DELAY 2
> EMIT 2
> FINALLY
> LOG closing
> END
> ROOT worker r
> RUN
0 r START
0 r FLOWLOG numbers opening
0 r FLOW numbers 2
0 r FLOWLOG numbers closing
0 r FLOWLOG numbers opening
0 r FLOW numbers 1
2 r FLOW numbers 2
2 r FLOWLOG numbers closing
2 r DONE
```

[6.2] 필터에서 빠진 값은 `TAKE` 개수에 넣지 않는다. 상류가 끝나면 개수를 못 채워도 수집은 끝난다.

```text
> TASK worker LAUNCH
> COLLECT odds FILTER ODD TAKE 2
> LOG after
> END
> FLOW odds
> EMIT 2
> EMIT 3
> EMIT 4
> END
> ROOT worker r
> RUN
0 r START
0 r FLOW odds 3
0 r LOG after
0 r DONE
```

## 7. 검사 방식

[7.1] 앞 단계와 같은 입력·출력 비교 방식을 따르고 1~5단계 테스트를 먼저 다시 실행한다. 고정 테스트는 반복 수집의 독립성, 같은 Flow의 동시 수집, `map`과 `filter` 순서, 음수 홀짝, `take`의 상류 중지와 정리, 수집자 취소, Flow 실패를 확인한다.

[7.2] 대규모 입력은 약 10,000개 작업과 30,000개 명령에 반복 수집과 지연을 섞는다. 한 테스트 제한 시간은 15초이고 모범 풀이 목표는 3초 이하다. Flow 정의를 수집마다 복사하거나 앞 수집의 타이머를 뒤 수집과 공유할 필요는 없다.
