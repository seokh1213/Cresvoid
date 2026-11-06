# 인덱스를 쓰지 못하는 질의

[1] 이 단계는 tune 모드다. MySQL 8.4의 상품 100만 행을 조회하는 HTTP 서비스에서 실행 계획과 실제 읽은 행 수를 관찰하고 질의를 고쳐라. 같은 결과를 더 적게 읽어서 구하는 것이 목표다. 실행 시간이 짧아졌다는 이유만으로 합격하지 않는다.

[2] Docker Desktop과 JDK 21을 사용한다. 프로젝트 루트의 `.lab-libs`는 `jdbc-mysql`이다. `./build.sh`는 고정 실행 이미지에서 `build/app.jar`를 만들고, 실험은 같은 이미지를 사용해 `lab.MainKt`를 띄운다. `./run.sh`를 따로 실행할 때는 `PORT`, `DB_URL`, `DB_USER`, `DB_PASSWORD`를 환경 변수로 넘겨라. `PORT`는 서비스 포트이며 호스트의 127.0.0.1에만 공개한다. 컨테이너 안에서는 포트 전달을 위해 0.0.0.0에 바인딩한다. 새 드라이버나 라이브러리를 설치하지 마라.

[3] 실험기는 매 실행마다 새 MySQL을 띄우고 `products`를 만든다. 열은 `id BIGINT PRIMARY KEY`, `sku VARCHAR(12) CHARACTER SET ascii COLLATE ascii_bin NOT NULL`, `tenant_id INT NOT NULL`, `category_id INT NOT NULL`, `created_at DATETIME NOT NULL`, `price INT NOT NULL`이다. id는 1부터 1,000,000까지다. tenant_id는 `FLOOR(id/200)%100`, category_id는 `id%200`, price는 `id%100000`이다. created_at은 `2024-01-01 00:00:00`에 `id%1000`일과 `FLOOR(id/1000)`초를 더한 값이다. sku는 id를 12자리로 앞에 0을 채운 문자열이지만 id 999999만 `4321`이다. 따라서 `000000004321`과 `4321`은 서로 다른 상품이다. 데이터는 실험 중 바뀌지 않는다. 이 생성 규칙은 결과를 이해하기 위한 자료이며, 조회를 수식으로 대체하는 데 쓰지 마라.

[4] 기본 인덱스는 유일 인덱스 `idx_sku(sku)`, `idx_created(created_at,id)`, `idx_tenant_category(tenant_id,category_id,id)`, `idx_price(price,id)`다. 서비스 시작 시 `Queries.prepare`가 한 번 실행되므로 필요한 인덱스를 그곳에서 추가해라. 모든 상품 조회는 `Queries.select`가 반환한 단일 SELECT와 바인더로 실행한다. SQL은 Main.kt가 요청별 주석 `/*lab03:r번호*/`를 붙여 PreparedStatement로 실행한다. 이 경로를 유지해라.

[5] `GET /health`는 준비 후 HTTP 200과 `ready`를 반환한다. `GET /query`는 `kind`, `token`과 종류별 인자를 받는다. token은 `r` 뒤에 숫자가 붙은 문자열이다. 성공 응답은 HTTP 200, 본문은 상품 id를 쉼표로 이은 문자열이다. 빈 결과는 빈 본문이며 줄바꿈을 넣지 않는다. `day`는 ISO 날짜 `date`에 해당하는 하루의 상품을 `created_at,id` 오름차순으로 최대 25개 반환한다. 하루의 시작은 포함하고 다음 날 시작은 제외한다. `sku`는 `sku` 문자열이 정확히 같은 상품을 id 오름차순으로 모두 반환한다. 숫자 값이 같아도 문자열이 다르면 제외한다. `category`는 정수 `category`와 category_id가 같은 상품을 id 오름차순으로 최대 25개 반환한다. tenant_id 조건은 없다. `page`는 정수 `price`, 정수 `id`가 가리키는 커서보다 `(price,id)` 사전순으로 큰 상품을 같은 순서로 최대 25개 반환한다. 가격이 같으면 id를 비교한다. 함께 받는 `offset`은 현재 데이터에서 커서 이하인 상품 개수이며 기존 구현의 페이지 위치다. 커서가 실제 상품을 가리킬 필요는 없다. 없는 날짜·sku·분류와 마지막 커서 뒤는 빈 목록을 반환한다.

[6] 먼저 시작 코드를 빌드하고 실험 결과를 확인해라. `Queries.kt`의 네 SELECT와 바인더를 읽고 같은 값과 자료형을 넣은 `EXPLAIN`의 접근 유형, 선택한 key, 예상 rows, 정렬 여부를 비교해라. `EXPLAIN ANALYZE`는 질의를 실제로 실행하므로 예상치와 실제 실행을 구별해라. 인덱스가 선택됐다는 사실만으로 적게 읽었다고 결론 내리지 마라. 수정 후 같은 절차를 반복해 결과와 읽은 행 수를 함께 비교해라.

[7] `catalog-round`는 네 종류를 차례로 섞어 초당 2회, 12초간 총 24개 요청을 예정된 시각에 보낸다. 앞 요청의 완료를 기다려 도착률을 낮추지 않는다. 종류별 6개이며 날짜는 데이터 범위 밖과 첫날·중간·마지막 날, sku는 앞의 0이 있는 값·없는 값·없는 상품, 분류는 존재하는 값과 없는 값, 페이지는 첫 가격·깊은 위치·동일 가격 안의 경계·마지막 뒤를 포함한다. 기준 결과는 SQL과 별도로 데이터 모형에서 계산한다. 매 실행마다 DB와 서비스 준비를 확인하고 끝나면 서비스와 DB를 회수한다. 시나리오는 세 번 반복한다.

[8] 각 요청의 상품 ID 목록과 순서는 기준 결과와 같아야 한다. 24개 요청 모두 HTTP 200으로 끝나야 하며, 각 요청에서 성공한 상품 SELECT가 정확히 하나 기록되어야 한다. MySQL Performance Schema의 `events_statements_history_long.ROWS_EXAMINED`로 각 SELECT가 검사한 행 수를 읽는다. day·category·page는 요청마다 64행 이하, sku는 요청마다 4행 이하여야 한다. 빈 결과도 같은 상한을 적용한다. 이 값은 결과 행 수나 디스크 I/O 횟수가 아니다. EXPLAIN의 예상 rows도 아니다. 지연 시간은 관찰용이며 합격 임계값으로 쓰지 않는다. 요청 제한 시간 60초와 전체 실험 제한 시간 600초는 실행 오류를 감지하기 위한 것이다.

[9] 지표는 `requests`(24), `http_errors`(0), `result_mismatches`(0), `statement_gaps`(0), `day_max_rows`(64 이하), `sku_max_rows`(4 이하), `category_max_rows`(64 이하), `page_max_rows`(64 이하), `p95_ms`(관찰값)다. 괄호 안은 합격 조건이다. statement_gaps는 기록이 정확히 하나가 아니거나 SQL 오류가 기록된 요청 수다. max_rows는 해당 종류의 요청별 검사 행 수 중 최댓값이다. 합격하려면 모든 요청의 결과·기록·상한 조건을 동시에 지켜야 한다.

[10] 수정 범위는 `src/main/kotlin/lab/Queries.kt`의 SELECT, 바인딩, prepare의 인덱스 DDL이다. 일반 보조 인덱스를 최대 두 개 추가할 수 있다. 기존 인덱스 삭제·열 정의 변경·데이터 변경·함수 기반 인덱스·생성 열·새 테이블·뷰는 허용하지 않는다. Main.kt, 실행·빌드 파일, `.lab-libs`, 요청·응답 형식, LIMIT 25, 정렬 순서, 필터 의미를 바꾸지 마라. 캐시·사전 적재·별도 조회·결과 하드코딩·생성 수식으로 id 계산·측정 주석 변경·측정 설정 변경은 허용하지 않는다. 값은 PreparedStatement로 바인딩해라.

[11] 근거는 MySQL 8.4 공식 문서다. [EXPLAIN](https://dev.mysql.com/doc/refman/8.4/en/explain.html)은 실행 계획과 질의를 실제 실행하는 EXPLAIN ANALYZE를 설명한다. [Type Conversion](https://dev.mysql.com/doc/refman/8.4/en/type-conversion.html)은 문자열 열과 숫자의 비교에서 문자열 열의 인덱스를 조회에 쓸 수 없는 경우를 설명한다. [Multiple-Column Indexes](https://dev.mysql.com/doc/refman/8.4/en/multiple-column-indexes.html)는 복합 인덱스의 왼쪽 접두사를 설명하며, 실제 선택은 옵티마이저와 계획으로 확인해야 한다. [Range Optimization](https://dev.mysql.com/doc/refman/8.4/en/range-optimization.html)은 B-tree의 상수 비교와 AND·OR 범위 조건을 설명한다. [LIMIT Query Optimization](https://dev.mysql.com/doc/refman/8.4/en/limit-optimization.html)은 LIMIT와 정렬의 관계를 설명한다. [Statement Event Tables](https://dev.mysql.com/doc/refman/8.4/en/performance-schema-events-statements-current-table.html)는 ROWS_EXAMINED가 서버 계층에서 검사한 행 수임을 정의한다.

## 공개 관찰 환경

[12] 저장소 루트에서 다음 명령을 실행해라. `APP`은 학습자 프로젝트의 절대 경로다. 예시 대신 별도로 시작한 작업 공간의 경로를 지정해도 된다. 공개 도구는 고정 이미지 `cresvoid-lab/jvm-runner:2`와 MySQL을 사용하며 실험과 같은 `tools/catalog.sql`로 100만 행과 기본 인덱스를 준비한다. 도구가 앱을 시작하면 `Queries.prepare`도 실행된다. `hidden/`을 열거나 별도 준비 도구를 만들 필요는 없다.

```sh
export CRESVOID_ROOT="$PWD"
APP="$PWD/practice/db-lab/03-index-usage"
STAGE="$PWD/content/tracks/db-lab/stages/03-index-usage"
(cd "$APP" && ./build.sh)
(cd "$STAGE/tools" && kotlin observe.main.kts "$APP")
```

[13] `READY`가 나오면 아래 명령을 한 줄씩 입력해라. `sql`은 컨테이너 안의 MySQL 클라이언트로 `lab` DB에 접속한다. EXPLAIN할 때는 현재 `Queries.kt`의 SELECT를 복사하고 `?`를 바인더가 넘기는 자료형의 리터럴로 바꿔라. 예를 들어 `setLong`이면 따옴표 없는 숫자, `setString`이면 따옴표로 감싼 문자열을 사용한다. 다음 EXPLAIN은 시작 코드의 day 질의 예시다. 나머지 세 질의도 같은 방식으로 확인해라. `get`은 출력한 URL로 HTTP 요청을 보내 token·상태·본문을 표시한다. `events`는 해당 token의 SQL·검사 행 수·반환 행 수·MySQL 오류 번호와 메시지를 표시한다. 요청마다 새로운 token을 사용해라.

```text
sql SHOW INDEX FROM products;
sql EXPLAIN SELECT id FROM products WHERE DATE(created_at) = '2024-01-02' ORDER BY created_at,id LIMIT 25;
get kind=day&date=2024-01-02&token=r1001
events r1001
get kind=sku&sku=000000004321&token=r1002
events r1002
get kind=category&category=17&token=r1003
events r1003
get kind=page&price=70000&id=370000&offset=700004&token=r1004
events r1004
get kind=sku&sku=4321%20&token=r1005
events r1005
```

[14] `get`의 인자는 URL 인코딩해서 넘겨라. 공백은 `%20`으로 적어라. HTTP 500이면 같은 token의 `events`로 SQL 오류를 확인해라. HTTP 400이고 기록이 없다면 SQL 실행 전의 입력 해석과 바인딩 과정을 `Queries.kt`에서 추적해라. 기록은 최근 10,000건만 남으므로 요청 직후 확인해라. 오류를 고친 뒤에는 새 token으로 재현해라.

[15] `quit`이나 입력의 끝으로 관찰을 마쳐라. Ctrl+C로 중단해도 이 실행의 앱·DB·볼륨·네트워크를 회수한다. 코드를 고쳤으면 먼저 도구를 종료하고 `build.sh`부터 같은 절차를 다시 실행해라. 매번 새 DB를 만들므로 prepare의 인덱스 추가도 초기 상태에서 확인할 수 있다. 이 도구의 단일 요청 관찰로 전체 실험을 대체하지 마라. 수정 후에는 `engine/bin/cresvoid test db-lab -stage 3`도 실행해라.

<details>
<summary>첫 관찰 뒤에 읽을 힌트</summary>

[16] SELECT와 바인더의 자료형, 조건식, 인덱스의 열 순서, 반환 전에 읽고 버리는 행을 대조해라. 각 수정이 결과를 보존하면서 읽기를 줄이는지 실제 계획과 ROWS_EXAMINED로 확인해라.

</details>
