# 8단계: 직접 쓰는 중단 상태 기계

## 1. 개요

[1.1] 1~7단계는 `suspend` 본문을 실행했으므로 중단 지점 뒤로 돌아가는 상태 기계는 Kotlin 컴파일러가 만들었다. 이번에는 앞 단계까지 완성한 코드를 바탕으로 두 결과를 차례로 기다리는 계산을 `Continuation<Int>`와 명시적인 상태로 직접 구현하라. 기존 단계에서 허용한 입력의 출력은 바꾸지 않는다.

[1.2] 새 계산은 `val first = steps.first(); val second = steps.second(first); return first + second`라는 중단 가능한 본문에 대응한다. 첫 결과를 두 번째 호출과 최종 덧셈에 다시 써야 한다. 두 작업은 즉시 결과를 줄 수도 있고 나중에 continuation을 재개할 수도 있다.

## 2. 배경 개념과 공개 API

[2.1] 컴파일러는 중단 가능한 함수의 중단 지점마다 재개할 위치를 기록하고, 그 뒤에도 필요한 지역 변수를 continuation 객체에 보관한다. `resumeWith`가 결과나 예외를 받으면 기록한 위치에서 이어 간다. 이번 공개 API는 이 변환을 작은 본문 하나에 직접 적용한다. `ManualSumMachine`의 계산 본문에는 `suspend`, `startCoroutine`, `createCoroutine`을 사용하지 않는다.

[2.2] 최상위 공개 선언은 다음 모양이다. 이름과 함수 시그니처를 유지하라. `ManualSumMachine`은 `Continuation<Int>`를 구현하며, `context`는 완료 continuation의 문맥과 같다.

```kotlin
interface ManualSteps {
    fun first(continuation: Continuation<Int>)
    fun second(first: Int, continuation: Continuation<Int>)
}

class ManualSumMachine(
    steps: ManualSteps,
    completion: Continuation<Int>,
) : Continuation<Int> {
    fun start()
    override val context: CoroutineContext
    override fun resumeWith(result: Result<Int>)
}
```

[2.3] `start()`는 첫 작업을 한 번 호출한다. 첫 결과가 성공하면 그 값을 보존해 `second(first, continuation)`을 호출한다. 두 번째 결과가 성공하면 보존한 첫 값과 두 번째 값을 더해 완료 continuation을 한 번 재개한다. 각 작업에 전달한 continuation은 완료 continuation과 같은 `context`를 가지며 해당 작업의 유효한 첫 결과만 기계의 `resumeWith`로 넘긴다. 각 호출 전에 다음 재개 위치를 정해야 하므로 작업이 continuation을 호출 안에서 바로 재개해도 같은 결과가 나온다.

[2.4] 첫 작업이나 두 번째 작업이 실패 결과를 보내면 같은 예외 객체를 완료 continuation에 한 번 전달한다. 첫 작업이 실패하면 두 번째 작업은 호출하지 않는다. 작업은 결과나 실패를 `resumeWith`로 전달하며 호출 자체는 예외를 던지지 않는다. 첫 값과 두 번째 값의 합은 `Int` 범위에 있는 입력만 검사한다.

[2.5] 시작 전 `resumeWith`, 두 번째 `start()`, 이미 처리한 작업 continuation의 두 번째 재개, 완료 후 재개는 `IllegalStateException`을 던진다. `ManualSumMachine.resumeWith`를 대기 중에 직접 호출하면 현재 기다리는 작업의 결과로 받는다. 그 뒤 늦게 도착한 해당 작업 continuation도 `IllegalStateException`을 던진다. 첫 작업의 continuation을 두 번 재개해도 두 번째 작업 결과로 오인하지 않는다. 잘못된 호출은 상태를 바꾸거나 완료 continuation을 다시 재개하지 않으며, 아직 기다리는 올바른 작업 continuation은 계속 유효하다. 서로 다른 `ManualSumMachine` 인스턴스는 상태와 보존한 첫 값을 공유하지 않는다.

## 3. 새 입력 모드

[3.1] 각 줄의 앞뒤 U+0020 공백을 뺀 뒤 빈 줄이 아닌 첫 줄이 `MANUAL`로 시작하면 전체 입력을 수동 상태 기계 모드로 읽는다. 이 모드에는 한 개 이상의 `MANUAL <이름> <첫값> <배수> <첫지연> <둘째지연> <결과종류>` 줄과 마지막 `RUN` 한 줄만 둔다. `RUN` 뒤에는 빈 줄밖에 올 수 없다. `TASK`, `ROOT` 등 앞 단계 명령을 이 모드에 섞을 수 없다. 첫 줄이 `MANUAL`로 시작하지 않으면 앞 단계의 입력 규칙을 그대로 따른다.

[3.2] 각 이름은 1단계의 이름 규칙을 따르고 이 입력 안에서 고유하다. 첫값은 0~1,000,000, 배수는 0~1,000, 두 지연은 각각 0~1,000,000,000의 앞에 불필요한 0이 없는 10진 정수다. 결과종류는 `OK`, `FAIL_FIRST`, `FAIL_SECOND` 가운데 하나다. 첫 작업은 첫지연 뒤 첫값을 돌려주고, 두 번째 작업은 둘째지연 뒤 전달받은 첫 값에 배수를 곱한 값을 돌려준다. 완료 결과는 `첫값 + 첫값 × 배수`다.

[3.3] 앞 단계처럼 각 줄의 앞뒤 U+0020 공백을 버리고 빈 줄은 건너뛴다. 인자는 한 개 이상의 U+0020으로 나눈다. 탭 등 다른 공백은 허용하지 않으며 명령어와 이름은 대소문자를 구분한다. 원본 줄 번호는 빈 줄도 세며 1부터 시작한다.

## 4. 실행과 출력

[4.1] 전체 입력을 먼저 검사한다. 올바르면 `MANUAL` 줄마다 별도의 `ManualSumMachine`을 만들어 가상 시각 0에 선언 순서로 시작하고 `<시각> <이름> START`를 기록한다. 입력 모드의 두 작업은 이 인스턴스가 넘긴 작업 continuation을 큐나 타이머에서 재개한다. 첫 작업은 첫지연 뒤, 두 번째 작업은 첫 성공 뒤 둘째지연 뒤에 재개된다. 지연이 0이어도 작업 함수는 즉시 호출하되 continuation의 재개는 준비 큐 끝에 넣어 다음 차례에 실행한다.

[4.2] 첫 작업 성공 시 `<시각> <이름> FIRST <첫값>`을 기록한 뒤 그 값으로 기계를 재개한다. 두 번째 작업 성공 시 `<시각> <이름> SECOND <곱>`을 기록한 뒤 재개한다. 완료 continuation은 같은 시각에 `<시각> <이름> DONE <합>`을 기록한다. `FAIL_FIRST`는 `START` 뒤 첫지연 시각에 `<시각> <이름> FAILED FIRST`를 기록하며 `FIRST`, `SECOND`, `DONE`을 기록하지 않는다. `FAIL_SECOND`는 `START`, `FIRST` 뒤 둘째지연 시각에 `<시각> <이름> FAILED SECOND`를 기록하며 `SECOND`, `DONE`을 기록하지 않는다.

[4.3] 준비 큐는 FIFO다. 비어 있으면 가장 이른 타이머 시각으로 옮겨 그 시각의 타이머를 등록 순서대로 준비 큐에 넣는다. 같은 시각에 새로 예약한 0 지연 작업은 이미 준비된 작업 뒤에 놓는다. 모든 기계가 완료되면 종료한다. 실제 시간 대기나 스레드 풀을 쓰지 않는다.

## 5. 오류 규칙

[5.1] 인자 개수, 이름 형식, 숫자 범위·표기, 결과종류, 명령 배치가 틀리면 `ERR <원본 줄 번호> FORMAT` 한 줄만 낸다. 같은 이름을 두 번 선언하면 뒤 선언 줄의 `DUPLICATE`다. `RUN`이 없으면 마지막 입력 줄 다음 번호의 `FORMAT`이다. 오류가 여러 개면 줄 번호가 작은 것을, 같은 줄이면 `FORMAT`을 `DUPLICATE`보다 먼저 낸다. 정상 실행 중에는 입력 오류가 나지 않는다.

## 6. 예시

[6.1] 첫 결과는 두 번째 작업에 전달되고 최종 덧셈에도 남아 있다.

```text
> MANUAL one 3 4 2 1 OK
> RUN
0 one START
2 one FIRST 3
3 one SECOND 12
3 one DONE 15
```

[6.2] 두 첫 작업이 같은 시각에 끝나면 선언 순서대로 재개된다. 둘째지연 0도 뒤에 예약되므로 이미 준비된 첫 작업보다 앞서지 않는다.

```text
> MANUAL a 2 3 0 0 OK
> MANUAL b 5 1 0 0 FAIL_SECOND
> RUN
0 a START
0 b START
0 a FIRST 2
0 b FIRST 5
0 a SECOND 6
0 a DONE 8
0 b FAILED SECOND
```

## 7. 검사 방식

[7.1] 고정 테스트는 두 중단 지점, 즉시·지연 재개, 지역 값 보존, 실패 전파, 동시 기계의 독립성, 같은 시각의 준비 큐 순서, 입력 오류를 확인한다. 생성 테스트는 여러 기계가 섞인 입력과 오류 입력을 확인하며, 대규모 입력은 약 10,000개 기계와 20,000번의 결과 전달을 포함한다. 한 테스트의 제한 시간은 15초다.

[7.2] 공개 API 검사는 `ManualSumMachine`을 직접 만들고 각 작업이 받은 continuation을 보관해 한 단계씩 재개한다. 동기 재개, 다른 인스턴스와의 교차 재개, 같은 예외 객체 전달, 잘못된 중복 호출도 확인한다. 명령 출력만 흉내 내고 공개 API를 구현하지 않으면 통과하지 못한다.

공식 근거: [Kotlin 언어 명세의 Continuation Passing Style과 상태 기계](https://kotlinlang.org/spec/asynchronous-programming-with-coroutines.html#implementation-details).
