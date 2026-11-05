# NULL, 중복, 열 순서의 의미론

[1] 이 단계는 observe 뒤 implement 순서로 진행한다. 먼저 공개 입력과 `Observations.kt`의 질의를 읽고 결과 행, 중복 개수, NULL, 오류 여부를 예측해라. 예측 문항을 푼 뒤 시작 코드를 실험하고 관찰값과 예측의 차이를 설명해라. 마지막으로 `Queries.kt`를 고쳐 [7]부터 [10]까지의 불변식을 만족시켜라. 관찰용 질의는 고치지 않는다.

[2] 실행 환경은 JDK 21, Kotlin, 이미 승인된 실행 이미지의 `spark` 묶음(Spark SQL 4.3.0)이다. `spark-submit`이나 `spark-sql` 대신 Kotlin 서비스가 `local[2]` SparkSession에서 같은 SQL·Dataset API를 실행한다. UI는 끄고 셔플 파티션은 2개, 세션 시간대는 UTC, `spark.sql.ansi.enabled`는 true로 고정한다. 외부 Spark 클러스터나 새 라이브러리는 필요 없다. Docker Desktop을 켜고 프로젝트 루트에서 `./build.sh`를 실행한 뒤 `PORT=18080 ./run.sh`로 서비스를 띄워라. 학습 공간이 저장소 밖이면 `CRESVOID_ROOT`에 저장소 경로를 지정해라. `run.sh`는 지정한 PORT를 호스트의 127.0.0.1에만 공개한다.

[3] `GET /health`는 Spark와 모든 입력 뷰가 준비됐을 때 HTTP 200과 `ready`를 돌려준다. `GET /observe?case=sample`, `GET /observe?case=alternate`는 원본 실험을 실행한다. 같은 case의 `GET /repair?case=...`는 수정 대상 질의 묶음을 실행한다. 성공 응답은 HTTP 200, UTF-8 text/plain이다. case 누락·미지원 case는 400, 없는 경로는 404, GET 외 메서드는 405다. 요청 사이에 입력을 바꾸거나 결과를 재사용하지 않는다. Spark 작업은 요청마다 실행하며 한 세션에서 순차 처리한다.

[4] 응답은 `이름<TAB>결과` 줄의 집합이다. 정상 결과는 `ROWS:` 뒤에 행을 세미콜론으로 잇고 행 안의 열은 쉼표로 잇는다. NULL은 `~`로 표시하고, 행 전체의 문자열을 사전순으로 정렬하되 같은 행도 횟수만큼 남긴다. 빈 결과는 `ROWS:`다. SQL 오류는 `ERROR:<Spark 오류 조건>`으로 표시한다. 쿼리 생성과 collect 중의 오류를 모두 포착하며, 한 쿼리가 실패해도 나머지 쿼리를 계속 실행한다. 결과 이름과 열 순서는 [6]부터 [10]까지의 계약을 따른다.

[5] 입력은 중복을 보존하는 작은 테이블이다. lhs·rhs의 열은 `(id STRING, k INT)`이고 blocked는 `(k INT)`다. sample의 lhs는 `(a,1),(b,1),(c,2),(d,NULL),(e,NULL),(f,-1),(g,3)`, rhs는 `(u,1),(v,1),(w,NULL),(x,NULL),(y,-1)`, blocked는 `(2),(NULL)`이다. alternate의 lhs는 `(a,2),(b,NULL),(c,-1),(d,2),(e,4)`, rhs는 `(u,2),(v,2),(w,NULL),(x,-1),(y,-1)`, blocked는 `(-1)`이다. 실제 뷰 이름은 case 뒤에 `_lhs`, `_rhs`, `_blocked`를 붙인 것이다. -1도 정상 키이므로 NULL 대신 넣는 특수값으로 쓰지 마라.

[5.1] 문자열 병합 입력의 모든 열은 STRING이다. sample의 first는 열 순서 `(user,region)`으로 `(ann,east),(ann,east),(bob,west)`이고 second는 `(region,user)` 순서로 `(south,cy),(east,ann)`이다. alternate의 first는 `(user,region)` 순서로 `(dee,north)`, second는 `(region,user)` 순서로 `(north,dee),(west,eli)`다. 뷰 이름은 각각 `<case>_first`, `<case>_second`다. 병합 뒤의 공개 열 순서는 `(user,region)`이다.

[5.2] 수치 입력의 열은 `(id STRING, raw STRING, n INT)`이고 뷰 이름은 `<case>_numbers`다. sample은 `(t1,7,1),(t2,oops,2147483647),(t3,NULL,NULL),(t4,2147483648,-2),(t5,-2,4)`다. alternate는 `(t1,-8,-2147483648),(t2,NULL,0),(t3,bad,2147483647),(t4,0,NULL)`이다. raw의 숫자 표기도 문자열이며 NULL만 SQL NULL이다.

[6] observe 응답은 `eq_join`, `safe_join`, `not_in`, `groups`, `distinct_keys`, `counts`, `positional_union`, `named_union`, `strict_cast`, `strict_add`의 10줄이다. 처음 둘은 `lhs.k = rhs.k`와 `lhs.k <=> rhs.k` 내부 조인의 `(lhs.id,rhs.id)`다. not_in은 `k NOT IN (SELECT k FROM blocked)`를 통과한 lhs의 `(id)`다. groups는 `GROUP BY k`의 `(k,COUNT(*),COUNT(k))`, distinct_keys는 `SELECT DISTINCT k`, counts는 lhs의 `(COUNT(*),COUNT(k))`다. 두 union은 각각 `first.union(second)`와 `first.unionByName(second)`다. strict_cast는 numbers의 `(id,CAST(raw AS INT))`, strict_add는 `(id,n + 1)`을 수집한다. 원본 질의의 결과를 수정 목표와 혼동하지 마라.

[7] repair의 join은 `(lhs.id,rhs.id,match_count)`다. NULL 키끼리도 짝을 짓고 모든 중복 조합을 보존해라. match_count는 해당 왼쪽 행과 같은 키를 가진 rhs 행 수이며, 그 왼쪽 행의 모든 출력 행에 같은 값을 넣어라. 짝이 없는 왼쪽 행도 `(lhs.id,NULL,0)` 한 행으로 남겨라. sample에서 d는 w·x와 짝을 이루어 `(d,w,2),(d,x,2)`가 되고 c는 `(c,NULL,0)`이 된다. exclude는 blocked에 같은 키가 하나도 없는 lhs의 `(id)`다. 이 업무 규칙에서는 NULL도 같은 제외 키다. blocked에 NULL이 있으면 lhs의 NULL을 제외하고, 없으면 lhs의 NULL을 남겨라. 조인 입력을 DISTINCT로 줄이거나 NOT IN의 하위 질의에서 NULL만 제거하면 이 계약을 만족하지 못한다.

[8] repair의 groups는 lhs의 NULL을 포함한 모든 키를 한 키당 한 행으로 묶은 `(k,lhs_rows,lhs_nonnull,rhs_rows)`다. lhs_rows는 그 키의 lhs 행 수, lhs_nonnull은 그중 NULL이 아닌 k의 수, rhs_rows는 같은 키의 rhs 행 수다. rhs에도 NULL이 있으면 같은 키로 세고, 해당 키가 없으면 0으로 세어라. distinct_keys는 `(k,lhs_rows)`로 키별 빈도를 남겨라. counts는 한 행 `(lhs_total,lhs_nonnull,rhs_total,rhs_nonnull,matched_lhs)`다. 각 total은 해당 입력의 전체 행 수, nonnull은 NULL이 아닌 k의 수, matched_lhs는 rhs에 같은 키가 하나 이상 있는 lhs 행 수다. matched_lhs에서 같은 왼쪽 행을 여러 번 세지 마라. sample의 NULL 그룹은 `(NULL,2,0,2)`이고 counts는 `(7,5,5,3,5)`다. NULL을 제거하거나 특수값으로 치환하지 말고, 조인으로 입력의 빈도를 부풀리지 마라. 행 순서는 [4]를 따른다.

[9] repair의 union은 열 이름으로 대응시킨 뒤 `(user,region)`마다 한 행 `(user,region,first_count,second_count)`를 돌려준다. first_count와 second_count는 각각 first와 second에서 그 쌍이 나온 횟수이며, 없는 쪽은 0이다. sample의 ann·east는 `(ann,east,2,1)`이다. 모든 행의 first_count 합은 first의 행 수와, second_count 합은 second의 행 수와 같아야 한다. 열 타입이 같아서 위치 기반 병합도 성공한다는 사실에 주의해라. 집계 전에 중복을 제거하면 입력별 횟수가 사라진다.

[10] repair의 cast는 numbers의 `(id,변환한 INT)`다. 정상 문자열은 INT로 변환하고 잘못된 문자열·INT 범위 밖 문자열·NULL은 NULL로 남겨라. add는 `(id,n에 1을 더한 INT)`이고 INT 넘침과 NULL은 NULL로 남겨라. 모든 입력 행과 id를 보존하고 ANSI 모드는 계속 true여야 한다. sample의 t1은 cast 7·add 2, t2는 cast NULL·add NULL이다. alternate의 t1은 cast -8·add -2147483647이다. 오류를 일괄 NULL로 바꾸거나 ANSI를 꺼서 넘침을 순환시키면 실패다. observe의 엄격한 CAST와 덧셈은 이 입력들에서 각각 CAST_INVALID_INPUT, ARITHMETIC_OVERFLOW 오류로 남아야 한다.

[11] 시나리오 `query-workload`는 서비스를 한 번 띄우고 준비 상태를 확인한 뒤 sample 관찰, sample 수정, alternate 관찰, alternate 수정 요청을 시작 시각으로부터 0·1000·2000·3000ms에 보낸다. 앞 요청의 완료를 기다리지 않는 개방형 부하다. 각 요청의 제한 시간은 120초다. 준비 확인은 최대 120초, 실험 전체 제한은 300초다. 같은 시나리오를 새 서비스로 3회 반복한다. 데이터와 NULL·중복의 의미를 검증하는 실험이므로 지연 백분위수는 참고값이며 속도로 합격 여부를 판정하지 않는다.

[12] 지표 scheduled와 completed는 각각 예정 요청 수와 응답을 받은 요청 수이며 둘 다 4여야 한다. response_errors는 HTTP 200이 아니거나 [4]의 형식·결과 이름 집합이 어긋난 응답 수이며 0이어야 한다. observation_mismatches는 관찰 응답의 20개 결과 가운데 원본 SQL 의미론과 다른 결과 수다. join_mismatches·exclusion_mismatches·aggregate_mismatches·union_mismatches·ansi_mismatches는 수정 응답을 [7]·[7]·[8]·[9]·[10]에 대조한 불일치 결과 수다. 여섯 mismatch 지표 모두 0이어야 한다. 행 수가 같아도 값이나 중복 수가 다르면 불일치다. p95_ms는 요청을 실제 전송한 때부터 본문을 모두 읽을 때까지 걸린 밀리초의 95백분위수다. 연결 실패·시간 초과·준비 실패는 불변식 실패가 아닌 실험 오류다.

[13] 수정 범위는 `src/main/kotlin/lab/Queries.kt`뿐이다. SQL·Dataset API로 매 요청의 뷰에서 계산해라. 입력·관찰 질의·직렬화·HTTP 서버·실행 스크립트·Spark 설정은 고치지 마라. `Main.kt`는 기존 실행 템플릿의 MainKt 진입점을 lab 패키지로 연결하는 파일이다. `/health`와 두 HTTP API는 관찰과 수정을 직접 비교하기 위한 공개 계약이다.

[14] 공식 근거는 [Spark NULL Semantics](https://spark.apache.org/docs/latest/sql-ref-null-semantics.html)의 비교 연산자·집계·GROUP BY/DISTINCT·IN/NOT IN 절, [Dataset API](https://spark.apache.org/docs/latest/api/java/org/apache/spark/sql/Dataset.html)의 union과 unionByName, [ANSI Compliance](https://spark.apache.org/docs/latest/sql-ref-ansi-compliance.html)의 CAST·산술·try_cast·try_add, [Migration Guide](https://spark.apache.org/docs/latest/sql-migration-guide.html)의 Spark 3.5에서 4.0으로 바뀔 때 ANSI 기본값 변경이다. 문서의 latest가 바뀌더라도 이 단계는 [2]의 버전과 명시한 설정으로 실험한다.
