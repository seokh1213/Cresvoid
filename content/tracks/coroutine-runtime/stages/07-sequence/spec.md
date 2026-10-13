# 7단계: 요청할 때 한 값씩 만드는 시퀀스

## 1. 개요

[1.1] 소비자가 값을 요청할 때마다 생산자를 다음 `yield`까지만 실행하는 pull형 시퀀스 생성기를 더하라. `iterator()`와 `hasNext()`는 값을 소비하지 않고, `next()`는 정확히 한 값을 소비한다. 생산자는 `yield`에서 멈췄다가 그 값을 소비한 뒤 다음 값을 찾는 요청에서 이어서 실행된다. 1~6단계에서 허용한 입력의 출력은 그대로다.

[1.2] 이 단계의 시퀀스는 가상 시간 실행기의 Job이나 준비 큐를 갖지 않는다. 값을 요청한 작업의 현재 continuation 안에서 동기적으로 실행되며, 다른 작업이 중간에 끼어들지 않는다. `DELAY`가 가능한 cold Flow와 달리 시퀀스 본문은 값을 내놓는 지점에서만 멈춘다.

## 2. 배경 개념

[2.1] Kotlin 표준 라이브러리의 [`sequence {}`](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.sequences/sequence.html)는 본문을 미리 실행하지 않는다. [`yield(value)`](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.sequences/-sequence-scope/yield.html)는 값을 반복자에 전달하고 다음 값이 요청될 때까지 생산자의 실행을 멈춘다. 같은 시퀀스에서 만든 두 반복자는 각각 자신의 본문 위치와 지역 변수를 갖는다.

[2.2] `hasNext()`는 다음 값을 찾으려고 생산자를 재개할 수 있다. 찾은 값을 보관하므로 연속된 `hasNext()`가 생산자를 거듭 재개하거나 값을 소비해서는 안 된다. `next()`는 보관한 값을 꺼내며, 보관한 값이 없으면 다음 `yield`나 본문 끝까지 생산자를 실행한다.

[2.3] 반복자가 마지막 `yield`에서 멈춘 채 버려지면 그 뒤의 본문은 실행되지 않는다. `Sequence`에는 중단된 생산자를 닫거나 취소하는 계약이 없다. 이 단계에서는 시퀀스 본문에 `FINALLY`를 두지 않으며, 작업 취소도 버려진 반복자를 재개하지 않는다. 이 점은 `take`가 상류의 정리를 실행하는 6단계 Flow와 다르다.

## 3. 공개 Kotlin API

[3.1] 기존 공개 API에 다음 선언을 더하라. `pullSequence`는 Kotlin 표준 `Sequence<T>`를 반환하며, 본문은 `PullScope<T>.yield`에서만 중단할 수 있다. `PullScope<T>`와 `pullSequence`는 최상위 공개 선언으로 두어 별도의 Kotlin 코드가 직접 호출할 수 있게 한다.

```kotlin
interface PullScope<T> {
    suspend fun yield(value: T)
}

fun <T> pullSequence(block: suspend PullScope<T>.() -> Unit): Sequence<T>
```

[3.2] `pullSequence` 호출과 반환된 `Sequence.iterator()` 호출은 본문을 실행하지 않는다. 반복자마다 처음 요청할 때 본문을 처음부터 시작한다. 한 반복자에서 `hasNext()`를 여러 번 호출해도 같은 값을 보며, `next()` 뒤의 요청에서만 다음 `yield`까지 실행한다. 서로 다른 반복자는 본문 위치와 본문 내부의 지역 변수를 따로 갖는다. 본문이 포획한 외부 가변 상태는 공유될 수 있다. 중첩 `suspend` 함수 호출도 중단 지점부터 이어져야 한다. `null`도 `T`의 값이면 다른 값과 똑같이 전달한다.

[3.3] 본문이 끝난 반복자의 `hasNext()`는 계속 `false`를 반환한다. 끝난 반복자의 `next()`는 `NoSuchElementException`을 던진다. 본문에서 일반 예외가 나면 그 요청에 예외를 그대로 던지고 그 반복자를 끝낸다. 이후의 `hasNext()`는 `false`, `next()`는 `NoSuchElementException`이다. 다른 반복자는 영향을 받지 않는다.

[3.4] 생성기의 실행 상태는 반복자마다 `Continuation`으로 직접 보관하고 재개하라. Kotlin 표준 `sequence`, `iterator`, `generateSequence` 빌더에 구현을 위임하지 않는다. 생산자를 미리 끝까지 실행해 목록으로 저장하거나 생산자 전용 스레드를 만들지 않는다. `yield` 밖에서 임의로 중단한 본문과 비동기 재개는 이 API의 입력 범위가 아니다.

## 4. 입력과 사건

[4.1] 모든 `TASK` 정의를 닫은 뒤 채널·잠금·`ROOT` 선언 전에 `SEQUENCE <이름>`부터 `END`까지 정의할 수 있다. `FLOW` 정의와 `SEQUENCE` 정의는 서로 섞어 적을 수 있다. 시퀀스 이름은 시퀀스 정의 안에서 고유하며 Flow와 같은 이름을 써도 된다. 정의를 여러 반복자와 여러 작업이 공유할 수 있지만 각 반복자의 실행 상태는 독립한다.

[4.2] 시퀀스 본문에는 `YIELD <정수>`와 `LOG <값>`만 적는다. 빈 본문도 허용한다. `YIELD` 정수는 6단계 `EMIT`과 같은 -1,000,000,000 이상 1,000,000,000 이하의 형식이고 `LOG` 값은 1단계 [3.2]의 한 토큰이다. 시퀀스를 정의하거나 반복자를 만들 때는 사건을 내지 않는다.

[4.3] `TASK`의 일반 본문에 `ITER <시퀀스 이름> <반복자 이름>`, `HAS_NEXT <반복자 이름>`, `NEXT <반복자 이름>`을 더한다. `ITER`는 현재 작업 인스턴스에서 새 반복자를 만들고 본문을 실행하지 않는다. 반복자 이름은 1단계 [3.2]의 이름 형식이며 한 `TASK` 정의의 `ITER` 사이에서 고유하다. 자식·채널·Flow 이름과는 별개 공간이다. 같은 `TASK` 정의를 여러 번 실행하면 인스턴스마다 새 반복자를 만든다.

[4.4] `HAS_NEXT`는 다음 값이 있으면 현재 작업 경로에 `HAS_NEXT <반복자 이름> true`, 없으면 `HAS_NEXT <반복자 이름> false` 사건을 낸다. `NEXT`는 값을 얻으면 현재 작업 경로에 `ITEM <반복자 이름> <정수>` 사건을 낸다. 다음 값이 없을 때의 `NEXT`는 사건을 내지 않고 `SEQUENCE_END` 코드의 일반 실패를 일으킨다. 실패 전파와 정리는 4단계 규칙을 따른다.

[4.5] 시퀀스 본문의 `LOG`가 실행되면 값을 요청한 작업의 현재 시각과 경로로 `SEQLOG <시퀀스 이름> <값>` 사건을 낸다. `HAS_NEXT`가 값을 찾는 중에도 이 사건이 먼저 나올 수 있다. `YIELD` 자체는 사건을 내지 않고 값을 보관한 채 멈춘다. 보관한 값을 소비한 뒤 새 값을 찾는 요청은 그 `YIELD` 바로 뒤부터 실행한다. 끝까지 실행한 본문은 다시 시작하지 않는다.

[4.6] `ITER`, `HAS_NEXT`, `NEXT`는 준비 큐에 continuation을 넣거나 가상 시간을 옮기지 않는다. 이 명령들은 작업 Job의 취소 상태를 자체로 확인하지 않는다. 이미 취소를 요청받은 작업이 이 명령에 이르면 3단계의 다음 취소 확인 지점에 닿기 전까지는 그대로 실행한다. 작업이 종료되면 남은 반복자는 폐기하며 시퀀스 본문을 더 실행하지 않는다.

## 5. 오류 규칙

[5.1] 앞 단계의 입력 검증 방식과 오류 우선순위를 따른다. 시퀀스 정의가 `TASK` 안이나 자원·`ROOT` 선언 뒤에 있으면 `FORMAT`이다. 같은 시퀀스 이름 또는 한 `TASK` 정의 안의 같은 반복자 이름을 두 번 선언하면 뒤 선언 줄의 `DUPLICATE`다. 없는 시퀀스를 `ITER`에서 참조하면 `UNKNOWN`이다. 한 `ITER` 줄에서 이름 중복과 없는 시퀀스 참조가 함께 있으면 `DUPLICATE`를 먼저 낸다. 해당 `TASK` 본문에서 앞선 `ITER`로 만들지 않은 반복자를 `HAS_NEXT`나 `NEXT`에서 참조하면 `REFERENCE`다.

[5.2] 시퀀스 본문의 허용 명령·인자·정수 형식이 틀리면 `FORMAT`이다. 인자 없는 작업 명령 `YIELD`를 시퀀스 본문에 쓰거나 인자 있는 시퀀스 명령 `YIELD`를 작업 본문에 쓰면 `FORMAT`이다. 새 반복자 명령은 작업의 `FINALLY` 구간과 Flow 본문·정리 구간에서 쓸 수 없다. 반복자 소진과 `SEQUENCE_END`는 입력 형식 오류가 아니다.

## 6. 예시

[6.1] 반복자를 만드는 것만으로는 생산자가 실행되지 않는다. 첫 `HAS_NEXT`는 첫 값을 찾아 보관하고 두 번째 `HAS_NEXT`는 생산자를 다시 실행하지 않는다. 마지막 값을 소비한 뒤의 요청이 남은 본문을 실행한다.

```text
> TASK worker LAUNCH
> ITER numbers a
> LOG before
> HAS_NEXT a
> HAS_NEXT a
> NEXT a
> NEXT a
> HAS_NEXT a
> END
> SEQUENCE numbers
> LOG first
> YIELD 10
> LOG second
> YIELD 20
> LOG ended
> END
> ROOT worker r
> RUN
0 r START
0 r LOG before
0 r SEQLOG numbers first
0 r HAS_NEXT a true
0 r HAS_NEXT a true
0 r ITEM a 10
0 r SEQLOG numbers second
0 r ITEM a 20
0 r SEQLOG numbers ended
0 r HAS_NEXT a false
0 r DONE
```

[6.2] 두 반복자는 같은 정의를 쓰더라도 각자 처음부터 시작한다. 어느 반복자도 마지막 `YIELD` 뒤를 재개하지 않으므로 `LOG after`는 실행되지 않는다.

```text
> TASK worker LAUNCH
> ITER values a
> ITER values b
> NEXT a
> NEXT a
> NEXT b
> END
> SEQUENCE values
> LOG opening
> YIELD 1
> YIELD 2
> LOG after
> END
> ROOT worker r
> RUN
0 r START
0 r SEQLOG values opening
0 r ITEM a 1
0 r ITEM a 2
0 r SEQLOG values opening
0 r ITEM b 1
0 r DONE
```

## 7. 검사 방식

[7.1] 앞 단계의 입력·출력 비교 방식을 따르고 1~6단계 테스트를 다시 실행한다. 고정 테스트는 반복자 생성의 지연성, `hasNext`의 값 보관, `next`의 소비, 빈 시퀀스와 소진 실패, 반복자·작업 인스턴스 간 독립성, Flow와 같은 이름의 정의, 작업 취소 뒤의 동기 실행을 확인한다.

[7.2] 공개 API는 별도 Kotlin 호출로 확인한다. 본문 부작용의 순서, 중첩 `suspend` 함수의 지역 변수 보존, nullable 값, 두 반복자의 교차 요청, 예외 뒤 소진 상태, 마지막 `yield` 뒤 본문의 미실행을 검사한다. 대규모 입력에는 약 10,000개 작업과 30,000개 명령에 여러 반복자의 교차 요청을 섞는다. 한 테스트 제한 시간은 15초이고 모범 풀이 목표는 3초 이하다.
