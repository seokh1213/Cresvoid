# 갭 잠금과 삽입 교착

[1] 먼저 두 세션의 문장 실행 순서를 읽고 각 잠금 읽기와 삽입이 완료될지 대기할지 예측해라. 교착이 생긴다면 어느 세션이 희생될지도 예상해라. 시작 코드를 실험한 뒤 관찰 경로를 그대로 두고 수정 경로의 정책만 바꿔라. 목표는 빈 범위에 대한 읽기 잠금을 이해하고 삽입 결과와 실제 저장 상태를 함께 지키는 것이다.

[2] JDK 21과 기존 실행 이미지의 jdbc-mysql 묶음을 사용한다. 프로젝트 루트의 .lab-libs와 lab 패키지를 유지해라. CRESVOID_ROOT를 저장소 루트로 설정하고 ./build.sh를 실행해라. ./run.sh는 PORT로 받은 포트를 루프백에 공개한다. 실험기는 고정 MySQL 8.4 이미지와 앱을 compose로 시작하고 준비 확인, 데이터 초기화, 요청, DB 검증, 종료를 맡는다. JDBC_URL은 compose가 전달하며 추가 라이브러리나 외부 부하 도구는 쓰지 않는다.

[3] 공개 테이블은 InnoDB의 slots(batch INT NOT NULL, id INT NOT NULL, PRIMARY KEY(batch,id))와 ballast(id INT PRIMARY KEY, value INT NOT NULL)다. 각 batch에는 id 10과 30이 있고 사이 구간은 비어 있다. ballast에는 값이 0인 16행이 있다. 실험마다 새 DB를 쓴다. 각 요청은 배정된 batch만 사용하며 관찰은 batch 0, 수정 실험은 batch 1~6을 사용한다. 초기 행과 다른 batch를 변경하지 마라.

[4] GET /health는 DB에 SELECT 1이 성공하면 HTTP 200과 ready를 반환한다. POST /observe는 batch 0에 대해 A와 B의 REPEATABLE READ 트랜잭션을 연다. A는 먼저 ballast 16행을 갱신하고 커밋하지 않는다. 이어 A, B 순서로 SELECT id FROM slots WHERE batch = 0 AND id > 10 AND id < 30 FOR SHARE를 끝까지 읽는다. 다음으로 A의 id 20 삽입을 별도 작업에서 시작한다. 감시 연결이 performance_schema.data_lock_waits에서 A의 대기를 확인하거나 A의 삽입·커밋이 끝난 뒤 B가 id 21 삽입을 시작한다. 각 세션은 성공하면 커밋하고 SQL 오류가 나면 롤백한다. 고정된 관찰 경로에는 일반 INSERT를 쓴다. 작은 트랜잭션을 희생자로 고르려는 InnoDB의 정책과 A의 선행 갱신을 함께 고려하되 희생자 이름은 실행 순서만으로 보장되는 API 계약으로 삼지 마라.

[5] POST /pair?batch=N&left=L&right=R은 같은 batch의 두 삽입을 실행한다. N은 양수이고 L과 R은 11~29다. 순서는 관찰 경로와 같지만 ballast 갱신 없이 두 잠금 읽기, A 삽입 시작, 대기 확인 또는 A 완료, B 삽입을 수행한다. 이 경로는 Policy.isolation과 Policy.insert를 사용한다. 각 요청의 batch는 한 번만 사용한다. 정책은 새 키를 실제로 커밋했을 때만 inserted를, 이미 있는 키라 새 행이 추가되지 않았을 때만 duplicate를 반환하게 만들어라. 재시도로 오류를 숨기거나 삽입을 생략하지 마라. 격리 수준과 조건부 삽입을 조합해도 된다.

[6] /observe와 /pair의 HTTP 200 본문은 a=결과, b=결과, a_wait=0 또는 1, reads=2의 네 줄이다. 결과는 inserted, duplicate, deadlock 또는 sql_오류번호다. MySQL 오류 1213은 deadlock으로 보고하며 커밋 전에 inserted로 응답하지 마라. 관찰 경로에서 두 읽기가 완료되고 A의 삽입 대기가 확인된 뒤, 한 세션은 deadlock으로 롤백되고 다른 세션은 inserted로 커밋되어야 한다. 최종 batch 0에는 초기 두 행과 생존 세션의 키만 있어야 한다. ballast 합계는 A가 커밋하면 16, 롤백하면 0이어야 한다.

[7] 각 시나리오는 관찰 요청 하나를 먼저 완료한 뒤 수정 요청 여섯 개를 보낸다. 수정 요청은 초당 2개로 3초 동안 0, 500, 1000, 1500, 2000, 2500ms에 예정되어 있으며 앞 요청의 완료를 기다려 도착률을 낮추지 않는다. 요청 i는 0~5이고 batch=i+1, left=12+i다. adjacent-arrivals는 right=left+7, repeated-arrivals는 right=left를 사용한다. HTTP 요청 제한은 25초, DB 잠금 대기 제한은 8초다. 시나리오마다 서비스를 한 번만 띄우고 모든 관찰과 측정이 끝나면 컨테이너와 볼륨을 정리한다.

[8] 서로 다른 키를 요청한 쌍은 두 삽입 모두 inserted로 끝나야 한다. 같은 키를 요청한 쌍은 inserted 하나와 duplicate 하나로 끝나야 한다. 수정 요청 여섯 개 모두 HTTP 200이어야 하며 deadlock, 다른 SQL 오류, 전송 오류가 없어야 한다. 응답 수를 합치면 adjacent-arrivals는 inserted 12개·duplicate 0개, repeated-arrivals는 inserted 6개·duplicate 6개다. 각 batch의 실제 행 집합은 초기 두 키와 요청 키의 합집합과 같아야 한다. 같은 키의 두 삽입 중 어느 세션이 inserted가 되는지는 정하지 않는다.

[9] 지표 observe_valid는 [6]의 관찰 불변식을 만족하면 1, 아니면 0이다. observe_wait는 관찰에서 A의 잠금 대기를 확인했으면 1이고 observe_deadlocks는 관찰의 deadlock 개수다. requests는 수정 HTTP 요청 수, http_errors는 비정상 HTTP·전송·응답 형식·분류되지 않은 SQL 결과 개수, inserted와 duplicates는 수정 응답별 결과 개수, deadlocks는 수정 경로의 deadlock 개수, row_mismatches는 기대 행 집합과 다른 batch 수다. pair_mismatches는 [8]의 쌍별 결과 개수를 어긴 수정 요청 수다. 응답 형식이 잘못되어 두 결과를 읽을 수 없는 요청도 여기에 센다. 합격하려면 observe_valid=1, observe_wait=1, observe_deadlocks=1, requests=6, http_errors=0, deadlocks=0, row_mismatches=0, pair_mismatches=0과 [8]의 전체 결과 개수를 모두 만족해야 한다. 지연 시간의 절대값은 합격 기준으로 쓰지 않는다.

[10] 수정 범위는 src/main/kotlin/lab/Policy.kt다. 관찰 경로, 실행 순서, 잠금 읽기, HTTP 처리, 측정 코드, 테이블 구조, 초기 데이터, 빌드·실행 설정을 바꾸지 마라. 새 의존성을 추가하거나 DB 밖의 값을 실제 저장 결과처럼 반환하지 마라. 시작 코드는 빌드되지만 수정 경로의 불변식을 만족하지 못한다. 먼저 예측과 관찰을 비교한 다음 정책을 고쳐 두 시나리오를 모두 통과시켜라.

[11] 근거는 MySQL 8.4 공식 문서의 [Transaction Isolation Levels](https://dev.mysql.com/doc/refman/8.4/en/innodb-transaction-isolation-levels.html), [Locking Reads](https://dev.mysql.com/doc/refman/8.4/en/innodb-locking-reads.html), [InnoDB Locking](https://dev.mysql.com/doc/refman/8.4/en/innodb-locking.html), [Deadlock Detection](https://dev.mysql.com/doc/refman/8.4/en/innodb-deadlock-detection.html)이다. REPEATABLE READ의 범위 잠금 읽기는 갭을 보호하고 FOR SHARE의 공유 잠금은 다른 공유 잠금과 공존한다. 갭 잠금은 삽입을 막지만 서로 공존할 수 있다. READ COMMITTED에서는 검색·인덱스 스캔의 갭 잠금을 사용하지 않지만 외래 키와 중복 키 검사는 예외다. 따라서 격리 수준을 바꿔도 중복 키 충돌의 처리는 별도로 필요하다. 교착이 감지되면 선택된 트랜잭션 전체가 롤백되며 희생자 선택은 단순한 문장 도착 순서 규칙이 아니다.

[12] 조건부 삽입의 근거는 공식 [INSERT Statement](https://dev.mysql.com/doc/refman/8.4/en/insert.html)다. INSERT IGNORE는 중복 행을 버리고 경고를 남길 수 있으므로 예외가 없다는 사실만으로 새 행이 추가되었다고 판단하지 마라. 공용 ./run.sh 래퍼는 JDBC_URL을 컨테이너에 전달하지 않으므로 호스트 환경 변수만 설정해서는 MySQL에 연결할 수 없다. DB 연결이 필요한 실행은 실험기를 사용해라. 직접 실행 환경을 구성한다면 별도 compose의 앱 서비스에 JDBC_URL을 설정해라. 실험기는 compose 내부 주소를 자동으로 전달한다.

[13] [11]의 Locking Reads 문서에 따르면 트랜잭션이 커밋되거나 롤백될 때 그 트랜잭션의 잠금 읽기가 설정한 잠금이 해제된다. 삽입 대기를 예측할 때는 다른 트랜잭션이 가진 잠금을 확인해라. 자신의 잠금만으로 자기 삽입을 막는 것은 아니다.
