# 트랜잭션 프록시의 함정

[1] 이 단계는 observe 실험이다. 시작 서비스는 HTTP 요청을 완료하지만 일부 관측 결과가 아래 합격 불변식과 맞지 않는다. 호출 경로를 읽고 행·예외·트랜잭션 지표를 비교해 관측 불일치의 원인을 찾아 고쳐라. 실패를 없애려고 모든 메서드에 트랜잭션을 붙이지 마라. 실제 프록시 호출이 끝난 뒤의 예외와 DB 상태를 정확히 기록하는 것이 실험의 합격 조건이다.

[2] JDK 21과 고정 실행 이미지의 `spring` 묶음을 사용한다. `.lab-libs`는 `spring`이며 Kotlin 코드는 `lab` 패키지에 둔다. 추가 라이브러리는 쓰지 않는다. 실험용 메서드에는 open을, final 실험에는 final을 명시해 이미지의 all-open 설정에 영향을 받지 않게 한다. `build.sh`로 빌드하고 `PORT=8080 ./run.sh`로 실행해라. HTTP는 루프백의 PORT에서 접근한다. 컨테이너 안에서는 포트 전달을 위해 모든 인터페이스에 바인딩하며 호스트에는 127.0.0.1로만 공개한다. 실험기는 같은 이미지의 app 서비스를 실행마다 독립된 compose 프로젝트로 띄우고 종료한다. H2는 앱 안의 메모리 DB이므로 외부 DB가 필요 없다.

[3] `GET /health`는 HTTP 200과 `ready`를 반환한다. `POST /observe/{case}`는 빈 본문을 받으며 `{case}`는 `self`, `private`, `final`, `checked`, `required`, `nested` 중 하나다. 정상적인 관측은 실험 본체가 예외를 던져도 HTTP 200이다. 알 수 없는 경로는 404, 허용하지 않는 HTTP 메서드는 405다. 요청마다 고유한 H2 DB를 만들고 닫으므로 반복 요청은 앞선 관측에 영향을 받지 않는다.

[4] 각 관측 응답은 UTF-8 `text/plain`이며 `case`, `rows`, `exception`, `outer_rows`, `inner_rows`, `transactional_rows`를 이 순서로 `키=값` 한 줄씩 출력한다. `case`는 요청한 식별자, `exception`은 호출 경계에서 잡은 예외 클래스의 단순 이름이며 예외가 없으면 `none`이다. 가장 안쪽 cause의 이름으로 바꾸지 마라. 나머지는 호출이 반환되거나 예외가 전달된 뒤 새 JDBC 조회로 센 정수다. `rows`는 전체 행, `outer_rows`는 before·after 행, `inner_rows`는 inner 행, `transactional_rows`는 삽입 당시 Spring 트랜잭션이 활성 상태였던 잔존 행 수다. 행 수와 예외 이름을 상수로 채우지 마라.

[5] 프록시는 실제 Spring `ProxyFactory`, `TransactionInterceptor`, `AnnotationTransactionAttributeSource`, [DataSourceTransactionManager](https://docs.spring.io/spring-framework/docs/7.0.10/javadoc-api/org/springframework/jdbc/datasource/DataSourceTransactionManager.html)로 만든다. 클래스 기반 프록시를 강제하며 인터페이스와 AspectJ는 사용하지 않는다. 명시적으로 open인 클래스와 메서드만 재정의할 수 있다. H2 커넥션의 기본 auto-commit은 true이고 전역 롤백 규칙을 추가하지 않는다. 모든 삽입은 `JdbcTemplate`을 거치며 `TransactionSynchronizationManager.isActualTransactionActive()`를 함께 저장한다.

[6] `self`는 open 클래스의 비트랜잭션 public `execute()`를 프록시로 호출한다. 본체는 같은 객체의 open public `@Transactional write()`를 직접 호출한다. write는 single 행 하나를 넣고 `IllegalStateException`을 던진다. `private`도 비트랜잭션 public execute에서 같은 일을 하는 private `@Transactional write()`를 호출한다. 두 경우 모두 외부 트랜잭션은 없다.

[7] `final`은 인터페이스가 없는 final 클래스의 `@Transactional execute()`를 대상으로 클래스 프록시 생성을 시도한다. Kotlin 클래스는 기본적으로 final이며 이 실험은 컴파일 플러그인도 열지 못하도록 final을 명시한다. execute의 본체는 single 행 하나를 넣고 `IllegalStateException`을 던지지만 프록시 생성에 실패하면 본체를 직접 호출하지 마라. 애플리케이션 전체를 중단하는 대신 이 요청의 예외로 기록해라. 컨텍스트의 BeanCreationException으로 감싸지 않고 ProxyFactory 호출 경계를 관측한다.

[8] `checked`는 open 클래스의 open public `@Transactional execute()`를 프록시로 호출한다. single 행 하나를 넣은 뒤 `java.io.IOException`을 던진다. rollbackFor나 기본 롤백 정책의 재설정은 없다. Kotlin에서 throws 선언을 강제하지 않는다는 사실과 Spring이 사용하는 JVM 예외 계층을 구분해라.

[9] `required`와 `nested`는 open public `@Transactional` 바깥 execute를 프록시로 호출한다. 바깥은 before 행을 넣고 별도 객체의 프록시를 통해 안쪽 메서드를 호출한다. 안쪽은 inner 행을 넣고 `IllegalStateException`을 던진다. 바깥은 이 예외만 잡고 after 행을 넣은 뒤 정상 반환을 시도한다. 안쪽 전파 속성은 각각 REQUIRED와 NESTED다. 같은 DataSourceTransactionManager를 사용하며 nestedTransactionAllowed는 true다. 바깥에 REQUIRES_NEW, noRollbackFor, 별도 커넥션을 넣지 마라.

[10] 합격 불변식은 다음과 같다. self와 private은 각각 rows=1, exception=IllegalStateException, transactional_rows=0이다. final은 rows=0, exception=AopConfigException이다. checked는 rows=1, exception=IOException, transactional_rows=1이다. required는 rows=0, exception=UnexpectedRollbackException이다. nested는 rows=2, exception=none, outer_rows=2, inner_rows=0, transactional_rows=2다. 다른 경우의 outer_rows와 inner_rows는 0이며 final과 required의 transactional_rows도 0이다. 이는 오류를 없애는 목표가 아니라 지정된 호출 구조의 실제 결과를 확인하는 기준이다.

[11] entry-samples는 self·private·final을, boundary-samples는 checked·required·nested를 관측한다. 각 시나리오는 준비 확인 뒤 500ms 간격으로 각 case를 두 번씩, 총 6회 호출한다. 공용 load.main.kts의 예정 시각 기반 개방형 부하를 사용한다. 요청 제한 시간은 10초, 준비 제한 시간은 60초다. 6개 모두 HTTP 200으로 완료되어야 한다. 지연 시간은 p95_ms로 관측만 하며 합격 임계값은 두지 않는다. 기계 부하에 따라 늦어져도 행과 예외가 같으면 결과는 같다.

[12] 지표는 해당 시나리오의 case별 `<case>_rows` 세 개, `http_failures`, `row_mismatches`, `exception_mismatches`, `transaction_mismatches`, `shape_mismatches`, `completed`, `p95_ms`로 총 10개다. case별 rows는 두 관측의 합이다. row_mismatches는 rows·outer_rows·inner_rows 중 하나라도 다른 응답 수, transaction_mismatches는 transactional_rows가 다른 응답 수, exception_mismatches는 예외가 다른 응답 수다. shape_mismatches는 키·순서·case·정수 형식이 계약과 다른 응답 수다. completed=6이며 http_failures와 모든 mismatches가 0이어야 한다. 응답 형식이 잘못되어도 지표를 출력하고 불변식 실패로 끝내라.

[13] 수정 대상은 관측 결과를 수집하고 응답으로 만드는 코드다. 어떤 호출 경계에서 결과가 확정되는지 판단해라. HTTP 계약, Database.kt의 조회, Workloads.kt의 호출 구조·가시성·애너테이션·전파 속성, `.lab-libs`, 실행 스크립트는 바꾸지 마라. 예외를 숨기거나 checked 예외까지 일괄 롤백하면 실험의 의미를 바꾸게 된다.

[14] 공식 근거는 Spring Framework 7.0.10 문서의 [프록시 제약과 자기 호출](https://docs.spring.io/spring-framework/reference/core/aop/proxying.html), [@Transactional의 프록시 호출 경계](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/annotations.html), [기본 롤백 규칙](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/rolling-back.html), [REQUIRED의 rollback-only와 NESTED의 JDBC 세이브포인트](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/tx-propagation.html)다. final 실험은 클래스 프록시를 강제한 경우에 한정하며 인터페이스 기반 JDK 프록시 전체로 일반화하지 마라. private 메서드는 바깥 트랜잭션에 참여할 수 있으므로 private 자체가 트랜잭션을 끊는다고 해석하지 마라.

[15] 예측 문항은 [6]~[9]의 호출 조건을 바꾼 가상 상황을 제시한다. 각 문항에서 명시한 변경만 적용하고 나머지 조건과 완료 후 조회 방식은 유지해라. [10]의 결과를 그대로 대입하지 말고 프록시 통과 여부, 롤백 규칙, 예외가 바깥 경계까지 전달되는지를 차례로 추론해라. 이 변경은 문항 안에서만 가정하며 시작 코드의 호출 구조를 바꾸라는 지시가 아니다.
