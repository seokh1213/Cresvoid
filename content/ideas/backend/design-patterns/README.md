# 설계 패턴

GoF의 생성·구조·행위 패턴과 스레드 동시성 패턴을 이론, 코드 읽기, 버그 수정, 구현으로 익힌다. 패턴 이름을 외우는 데서 멈추지 않고 언제 쓰는지, 무엇과 바꿀 수 있는지, Kotlin 언어 기능이 어느 패턴을 대신하는지 판단한다. 모든 카드는 Kotlin 표준 라이브러리와 JDK 21만 쓴다.

## 1. 개념 지도

- 생성 패턴: 팩토리 메서드, 추상 팩토리, 빌더, 프로토타입, 싱글턴. 객체를 만드는 책임을 쓰는 쪽에서 떼어 낸다. Kotlin에서는 기본 인자와 이름 붙인 인자가 빌더의 상당 부분을, `object`가 싱글턴을, data class의 `copy`가 프로토타입의 일부를 대신한다. `copy`는 얕은 복사다.
- 구조 패턴: 어댑터, 브리지, 컴포지트, 데코레이터, 퍼사드, 프록시, 플라이웨이트. 어댑터는 인터페이스를 맞추고 데코레이터는 기능을 덧붙이며 프록시는 접근을 통제한다. 세 패턴 모두 다른 객체를 감싸므로 구조보다 목적으로 구분한다. Kotlin 클래스 위임(`by`)은 위임 대상 안에서 일어나는 자기 호출을 가로채지 못한다.
- 행위 패턴: 전략, 템플릿 메서드, 옵저버, 커맨드, 책임 연쇄, 상태, 방문자, 메멘토 등. 무엇을 떼어 내는지(알고리즘, 단계, 상태, 요청, 알림)로 구분한다. sealed 계층과 `when`은 방문자와 같은 문제를 반대 방향으로 푼다. 새 연산은 더하기 쉽고 새 타입은 더하기 어렵다.
- 동시성 패턴: 생산자-소비자, 독약 객체(poison pill)로 끝내기, 보호된 대기(guarded suspension), 거부(balking), 이중 확인 잠금, 스레드 풀. 공유 상태의 검사와 갱신을 원자적으로 묶어야 하는 지점, 깨어난 뒤 조건을 다시 확인해야 하는 이유, 작업자를 끝내는 순서를 다룬다.
- 회복력 패턴: 재시도, 회로 차단기, 쓰로틀링, 앰배서더. 다른 도메인의 카드가 이미 다룬다('기존 항목과의 연결' 절).

## 2. 학습 경로

1. R-design-patterns-10, R-design-patterns-20, R-design-patterns-30 기술 지문으로 생성·구조·행위 패턴의 의도와 경계를 읽는다.
2. C-design-patterns-40, C-design-patterns-50 코드 읽기로 실무 모양 코드 속 패턴이 만드는 실행 순서를 예측한다.
3. F-design-patterns-60, F-design-patterns-70 버그 수정으로 옵저버와 커맨드를 잘못 구현했을 때 생기는 버그를 고친다.
4. B-design-patterns-80 `doc-editor` 트랙에서 커맨드, 컴포지트, 메멘토로 되돌리기를 지원하는 편집기를 만든다.
5. C-design-patterns-90, F-design-patterns-100, F-design-patterns-110으로 스레드 동시성 패턴의 종료, 검사 후 갱신, 조건 대기를 익힌다. 같은 문제를 코루틴으로 푸는 방법은 [../coroutines/README.md](../coroutines/README.md)가 다룬다.

## 3. 기존 항목과의 연결

- 재시도: C-kotlin-50(백오프, 지터, 오류 분류)과 F-kotlin-90(멱등 키)이 재시도 패턴과 앰배서더의 핵심을 이미 다룬다.
- 쓰로틀링: rate-limiter 트랙의 B-system-design-20이 서버 쪽 제한을, B-system-design-25가 호출자 쪽 적응형 쓰로틀링을 다룬다.
- 샤딩: B-distributed-40(일관 해싱)과 R-database-40(온라인 재샤딩)이 다룬다.
- 캐시: C-kotlin-40(LRU 캐시).
- 비동기 작업의 시작과 완료: F-coroutines-34(시작하자마자 끝나는 백그라운드 이벤트).

## 4. 환경

- 모든 카드가 추가 도구 없이 동작한다(env 없음).
- 동시성 fix 카드는 실행마다 결과가 달라지는 테스트를 쓰지 않는다. 검사와 갱신 사이, 대기와 깨움 사이에 시험용 지점을 두어 실패하는 순서를 결정적으로 만든다. 고친 코드가 그 지점을 거치지 않아도 테스트가 멈추지 않게 대기에 상한을 둔다.

## 5. 아이디어

| 아이디어 | 종류 | 메모 |
|---|---|---|
| 이중 확인 잠금과 volatile | reading | 메모리 가시성 문제는 테스트로 결정적으로 재현하기 어렵다. 지문과 문항으로만 다룬다 |
| 플라이웨이트와 값 공유 | code | 공유한 값 객체의 동일성 비교와 가변 상태가 섞일 때의 함정 |
| 인터프리터와 작은 규칙 언어 | build | search-engine의 질의 파서와 겹치지 않게 범위를 정한다 |
| 중재자와 이벤트 버스 | code | 구독 순서, 재진입, 예외가 난 구독자의 처리 |

## 6. 출처

- Gamma, Helm, Johnson, Vlissides, "Design Patterns: Elements of Reusable Object-Oriented Software", Addison-Wesley, 1994. 23개 패턴의 의도, 구조, 결과와 "클래스 상속보다 객체 합성" 원칙.
- Schmidt, Stal, Rohnert, Buschmann, "Pattern-Oriented Software Architecture, Volume 2: Patterns for Concurrent and Networked Objects", Wiley, 2000. 동시성 패턴의 구조.
- Lea, "Concurrent Programming in Java: Design Principles and Patterns", 2nd ed., Addison-Wesley, 1999. 상태에 따른 거부(balking)와 보호된 대기.
- Goetz 외, "Java Concurrency in Practice", Addison-Wesley, 2006. 검사 후 행동 경쟁, 독약 객체로 끝내기, 조건 큐.
- Philip Wadler, "The Expression Problem", java-genericity 메일링 리스트, 1998. 새 타입과 새 연산의 확장 방향.
- [Kotlin 문서: Delegation](https://kotlinlang.org/docs/delegation.html), [Object declarations](https://kotlinlang.org/docs/object-declarations.html), [Sealed classes](https://kotlinlang.org/docs/sealed-classes.html), [Data classes](https://kotlinlang.org/docs/data-classes.html).
- JDK 21 API 문서: `java.util.concurrent.BlockingQueue`, `ExecutorService`, `Object.wait`(깨어난 뒤 조건을 반복해서 확인해야 한다).
- 주제 출처: kotlin-lab의 `patterns`(iluwatar/java-design-patterns, MIT를 debop/kotlin-design-patterns, Apache 2.0이 Kotlin으로 옮긴 것)의 패턴 목록. 코드와 문장은 옮기지 않는다(D15).
