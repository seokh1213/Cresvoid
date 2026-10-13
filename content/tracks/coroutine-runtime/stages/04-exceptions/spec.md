# 4단계: 실패 전파와 감독

## 1. 개요

[1.1] 작업의 일반 실패를 부모에게 전하고 형제를 취소하라. 감독 스코프는 자식 실패를 격리한다. `AWAIT`의 실패 전달, 루트의 처리기, 취소 예외를 잡은 뒤의 동작을 더한다. 앞 단계 입력의 출력은 바꾸지 않는다.

## 2. 배경 개념

[2.1] 일반 Job의 자식이 실패하면 그 실패는 부모로 올라가고 부모의 다른 자식도 취소된다. `ASYNC`는 실패를 결과로 보관하지만, 일반 부모의 자식이라는 관계까지 끊지는 않는다. 감독 Job은 직계 자식의 실패가 다른 자식이나 감독자 자신을 취소하지 않게 한다.

[2.2] `CancellationException`은 보통 실패 보고 대상이 아니다. 취소 예외를 잡고 아무 일도 없었던 것처럼 넘겨도 Job의 취소 상태는 되돌아오지 않는다. 다음 중단점에서 다시 취소가 관찰될 수 있다. 실패가 여럿이면 첫 일반 예외를 주된 실패로 두고 나중 예외를 별도로 기록한다.

## 3. 입출력 규칙

[3.1] 1~3단계 형식을 따른다. 일반 예외 코드는 `^[A-Z][A-Z0-9_]{0,31}$`에 맞는다. 새 사건은 `FAILED <코드>`, `SUPPRESSED <코드>`, `UNHANDLED <코드>`, `CAUGHT <자식 이름> <FAILED|CANCELLED> <코드|->`, `CAUGHT_CANCEL`과 `ACTIVE`이다.

[3.2] `STATE`에는 최종 상태 `FAILED`를 더한다. 일반 예외가 발생한 뒤 정리와 자식 완료를 기다리는 동안에는 `CANCELLING`으로 보인다. 3단계의 `ON_COMPLETE` 처리기는 실패한 작업에서 `HANDLER <값> FAILED <코드>`를 출력한다. 성공과 취소 때의 처리기 출력은 그대로다.

## 4. 실패와 감독 규칙

[4.1] `FAIL <코드>`는 현재 작업에서 일반 예외를 던지고 남은 본문을 건너뛰어 `FINALLY`로 간다. `FINALLY`에도 `FAIL`을 둘 수 있으며 그 뒤의 정리 명령은 실행하지 않는다. 실패 중인 작업의 보통 중단 함수는 취소된 작업처럼 동작한다. 작업은 남은 자식의 정리까지 마치고 `FAILED <주된 코드>`를 낸다. 실패가 발생한 시각에 자식과 후손에게 3단계 [4.1]의 깊이 우선 순서로 취소를 요청한다.

[4.2] 일반 부모의 자식에서 첫 일반 예외가 발생하면 그 시각에 부모도 그 코드로 실패를 시작하고 나머지 자식에게 취소를 요청한다. 부모가 `JOIN`, `AWAIT`, `SCOPE`, `TIMEOUT`에서 기다렸으면 대기를 풀고 자신의 `FINALLY`로 간다. 부모의 최종 `FAILED` 사건은 자식 정리가 모두 끝난 뒤에 낸다. 이미 본문을 마친 부모도 자식이 남아 있는 동안에는 실패를 받아들인다. 이 규칙은 루트나 감독 경계에 이를 때까지 반복한다. `ASYNC` 자식의 실패도 일반 부모에게 똑같이 전파된다.

[4.3] 한 작업의 첫 일반 예외 코드가 주된 코드다. 뒤에 그 작업의 `FINALLY`나 다른 자식에서 일반 예외가 나면 발생 순서대로 그 작업 경로에서 `SUPPRESSED <코드>`를 내고 주된 코드는 바꾸지 않는다. 자식의 주된 실패만 부모에게 전파하고, 자식이 낸 `SUPPRESSED` 코드는 조상에게 다시 전파하거나 중복 출력하지 않는다. 취소 예외는 주된 코드가 되거나 `SUPPRESSED` 사건이 되지 않는다. 명시적 취소 뒤 정리에서 처음 일반 예외가 나면 그 예외가 주된 실패가 된다.

[4.4] `SUPERVISOR <정의> <자식 이름> [tag=<값>] [trace=<값>]`는 2단계 `SCOPE`처럼 `LAUNCH` 정의를 시작하고 완료를 기다리되, 그 Job의 직계 자식 실패를 격리한다. 직계 자식이 실패해도 감독 Job과 다른 자식은 계속한다. 감독 본문 자체가 실패하면 감독 아래의 남은 자식을 취소하고 일반 자식 실패처럼 자신의 부모에게 전파한다. 감독 아래에 다시 `SCOPE`를 열면 그 안의 자식 실패는 먼저 일반 스코프 안에서 전파된다.

[4.5] 일반 루트가 `FAILED` 사건과 완료 처리기를 모두 낸 뒤 `UNHANDLED <주된 코드>`를 낸다. 감독 Job의 직계 `LAUNCH` 자식이 실패하면 그 자식의 `FAILED`와 완료 처리기 뒤에 그 자식 경로에서 `UNHANDLED <주된 코드>`를 낸다. 감독 아래의 실패한 `ASYNC` 자식은 `AWAIT`하지 않으면 감독 Job이 완료한 뒤에도 `UNHANDLED`를 내지 않는다. 실패 코드는 그 자식의 결과에 남고 `AWAIT` 또는 `AWAIT_CATCH`에서만 관찰한다. `UNHANDLED`는 대기자의 재개나 부모의 완료보다 앞선다.

[4.6] `JOIN`은 감독 아래의 실패한 직계 자식도 최종 상태까지 기다린 뒤 정상적으로 돌아오고 실패를 다시 던지지 않는다. 실패한 `LAUNCH` 자식의 `UNHANDLED`는 `JOIN`의 재개보다 먼저 나온다. `AWAIT`는 성공한 `ASYNC` 자식에 대해 1단계의 `VALUE`를 낸다. 실패한 자식이면 그 코드의 예외를 현재 작업에 던지고, 취소된 자식이면 3단계처럼 취소 예외를 던진다. 현재 작업 자체가 이미 취소 중이거나 실패 중이면 자신의 상태가 우선하므로 `AWAIT`의 자식 결과를 처리하지 않고 정리로 간다.

[4.7] `AWAIT_CATCH <자식 이름>`은 `AWAIT`와 같이 기다리되, 활성 상태의 현재 작업에 자식의 실패 또는 취소가 돌아오면 예외를 잡고 `CAUGHT <자식 이름> FAILED <코드>` 또는 `CAUGHT <자식 이름> CANCELLED -`를 낸 뒤 다음 명령으로 간다. 성공한 자식은 `AWAIT`처럼 `VALUE`를 낸다. 현재 작업 자신이 취소 중이면 그 취소를 잡지 않고 정리로 간다. 일반 부모가 실패한 자식을 기다릴 때는 [4.2]의 부모 실패가 먼저 적용된다.

[4.8] `CANCEL_SELF`는 현재 작업과 후손에 취소를 요청하고 다음 명령을 계속 실행한다. `SWALLOW_CANCEL`은 자신의 취소 상태를 확인해 취소 중이면 그 자리에서 취소 예외를 잡고 `CAUGHT_CANCEL`을 내며, 활성 상태면 `ACTIVE`를 낸다. 두 경우 모두 다음 명령으로 간다. 잡은 뒤에도 Job이 취소 중이면 `DELAY`, `YIELD`, `ENSURE` 등 다음 취소 확인 지점에서 정리로 간다.

[4.9] `TIMEOUT`의 자식이 기한 전에 실패하면 시간 초과 사건 대신 일반 실패 규칙을 적용한다. 기한으로 시작된 취소 뒤 정리에서 일반 예외가 나면 그 예외가 시간 초과보다 우선해 부모에게 전파되고 `TIMEOUT` 사건은 내지 않는다. 단순 취소에는 루트 실패 처리기를 호출하지 않는다.

## 5. 오류 규칙

[5.1] 앞 단계의 검증 순서와 오류 코드를 따른다. `FAIL`에 잘못된 코드가 있거나 `SUPERVISOR`가 `ASYNC` 정의를 참조하면 `FORMAT`이다. `AWAIT_CATCH`가 없는 자식이나 `LAUNCH` 자식을 가리키면 `REFERENCE`다. `SUPERVISOR`의 자식 이름 중복은 `DUPLICATE`다. 순환 검사는 `SPAWN`, `SCOPE`, `TIMEOUT`, `SUPERVISOR` 참조를 한 목록으로 합쳐 1단계 [5.3]의 원본 줄 순서를 적용한다.

[5.2] `CANCEL_SELF`와 `SWALLOW_CANCEL`은 인자가 없어야 한다. 실패는 실행 결과이며 `ERR` 형식 오류가 아니다. 주된 예외 코드가 같아도 서로 다른 작업의 `FAILED` 사건은 각각 낸다.

## 6. 예시

[6.1] 감독의 실패한 `LAUNCH` 자식은 처리기에서 보고하지만 다른 자식은 끝까지 실행한다.

```text
> TASK broken LAUNCH
> FAIL BOOM
> END
> TASK healthy LAUNCH
> DELAY 1
> LOG okay
> END
> TASK group LAUNCH
> SPAWN broken a
> SPAWN healthy b
> JOIN b
> END
> TASK parent LAUNCH
> SUPERVISOR group s
> LOG after
> END
> ROOT parent r
> RUN
0 r START
0 r.s START
0 r.s.a START
0 r.s.a FAILED BOOM
0 r.s.a UNHANDLED BOOM
0 r.s.b START
1 r.s.b LOG okay
1 r.s.b DONE
1 r.s DONE
1 r LOG after
1 r DONE
```

[6.2] 취소 예외를 잡아도 Job은 취소된 채로 남는다. 다음 `DELAY`가 남은 본문을 건너뛴다.

```text
> TASK worker LAUNCH
> CANCEL_SELF
> SWALLOW_CANCEL
> LOG after_catch
> DELAY 0
> LOG unreachable
> FINALLY
> LOG cleanup
> END
> ROOT worker r
> RUN
0 r START
0 r CAUGHT_CANCEL
0 r LOG after_catch
0 r LOG cleanup
0 r CANCELLED
```

## 7. 검사 방식

[7.1] 1~3단계의 입력 방식과 출력 비교 방식을 따르고 이전 테스트를 다시 실행한다. 고정 테스트는 일반 부모와 감독 부모의 차이, `ASYNC` 실패 뒤 `AWAIT`와 `AWAIT_CATCH`, 루트 보고 시점, 형제 취소, 정리에서 두 번째 실패, 취소 예외를 잡은 뒤의 중단을 확인한다.

[7.2] 대규모 입력은 약 10,000개 작업과 30,000개 명령에 독립된 실패 집단을 섞는다. 한 테스트 제한 시간은 15초이고 모범 풀이 목표는 3초 이하다. 실패 전파 때 전체 Job 목록을 훑는 구현은 이 규모에서 늦어진다.
