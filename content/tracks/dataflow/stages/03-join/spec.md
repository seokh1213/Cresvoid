# 3단계: 조인 전략과 쏠림

## 1. 개요

[1.1] 3단계는 두 계보를 키로 잇는 join 변환을 더한다. 엔진은 크기 추정과 임계값으로 브로드캐스트 조인과 정렬 병합 조인 가운데 하나를 고르고 입력이 이미 조인의 파티셔너로 나뉘어 있으면 그 입력을 셔플하지 않는다. 키가 NULL 인 행은 `=` 의 뜻대로 짝이 없고 nullsafe 를 붙이면 NULL 끼리 짝짓는다. 실행할 때는 셔플 뒤 파티션 크기로 쏠린 파티션을 찾아 여러 태스크로 쪼갠다.

[1.2] 1단계와 2단계의 입출력 규칙과 명령은 그대로다. 이 문서는 새로 생기는 규칙만 설명한다. 1단계 4.15 가 출력을 정하지 않은 join 변환과 2단계 5.1 이 출력을 정하지 않은 설정 이름 broadcastThreshold, skewFactor, skewThreshold, advisorySize 는 이 문서가 출력을 정한다. 설정 이름 adaptive 와 minPartitionSize 는 이 문서도 출력을 정하지 않는다.

| 더하는 것 | 하는 일 |
|---|---|
| `join <이름> <n>` 변환 | 계보와 다른 계보를 키가 같은 행끼리 잇는다 |
| `join <이름> <n> nullsafe` 변환 | NULL 키끼리도 잇는다 |
| 설정 broadcastThreshold | 브로드캐스트 조인을 고르는 크기 임계값 |
| 설정 skewFactor, skewThreshold, advisorySize | 쏠림 판정과 쪼개기의 기준 |

[1.3] 조인이 없는 계보의 계획, 결과 행, 지표는 1단계와 2단계 그대로다.

## 2. 배경 개념

[2.1] 내부 조인은 두 입력에서 키가 같은 행을 모두 짝짓는다. 왼쪽 입력에 키 k 의 행이 a 개, 오른쪽 입력에 b 개 있으면 결과에는 키 k 의 행이 a × b 개 생긴다. 결과 행은 키 하나와 왼쪽 값, 오른쪽 값을 함께 가진다. 한쪽에만 있는 키는 결과에 나오지 않는다.

[2.2] SQL 의 `=` 는 한쪽이 NULL 이면 참이 아니므로 키가 NULL 인 행은 어떤 행과도 짝이 되지 않는다. 두 키가 모두 NULL 이어도 마찬가지다. Spark SQL 의 `<=>` 처럼 NULL 끼리 같다고 보는 비교를 null-safe 비교라 한다. null-safe 조인에서는 키가 NULL 인 왼쪽 행과 오른쪽 행이 서로 짝이 된다. Spark SQL 은 `=` 로 잇는 내부 조인 앞에 키가 NULL 이 아닌 행만 남기는 filter 를 스스로 넣어 짝이 될 수 없는 행을 셔플하지 않는다. 이 엔진은 그러지 않으므로 NULL 키 행도 셔플되고 모두 해시 파티셔너의 0 번 파티션으로 간다. NULL 키 행이 많으면 그 파티션이 커진다.

[2.3] 정렬 병합 조인은 두 입력을 같은 파티셔너로 나누어 같은 키를 양쪽에서 같은 번호의 파티션에 모은 뒤 파티션마다 양쪽을 키로 정렬해 앞에서부터 짝지어 나간다. 입력이 이미 그 파티셔너로 나뉘어 있으면 그 입력은 셔플하지 않는다. 기술 지문(`tech-reading` 트랙 5단계 "Spark 의 실행 계획과 셔플")이 설명하듯 두 입력이 모두 그렇다면 결과의 i 번 파티션은 두 입력의 i 번 파티션만 읽으면 되므로 조인은 양쪽 모두 좁은 의존이 된다. 한쪽만 그렇다면 다른 쪽만 셔플한다.

[2.4] 브로드캐스트 조인은 한쪽 입력이 작을 때 쓴다. 엔진은 작은 쪽의 행을 모두 모아 다른 쪽의 모든 태스크에 사본을 보내고 각 태스크는 그 사본으로 해시 테이블을 만들어 자기 파티션의 행과 짝짓는다. 사본을 만드는 쪽을 빌드 쪽, 나머지를 스트림 쪽이라 한다. 스트림 쪽은 셔플하지 않으므로 조인은 스트림 쪽과 좁은 의존이다. 대신 빌드 쪽 전체가 태스크마다 복제되므로 빌드 쪽이 크면 손해다. Spark SQL 은 추정 크기가 `spark.sql.autoBroadcastJoinThreshold`(기본 10MB) 이하인 쪽을 빌드 쪽으로 고르고 양쪽 모두 그렇다면 작은 쪽을, 크기가 같으면 오른쪽을 고른다. 이 값을 −1 로 두면 크기로 브로드캐스트 조인을 고르지 않는다. Spark SQL 은 빌드 쪽을 모으는 일을 따로 잡으로 실행하지만 이 엔진은 한 잡의 스테이지로 다룬다.

[2.5] 계획을 세우는 때에는 변환의 결과가 얼마나 클지 모르므로 엔진은 크기를 추정한다. Spark SQL 은 열 통계가 없으면 filter 나 집계가 행 수를 줄이지 않는다고 본다. 조인의 크기는 한쪽 키가 유일하다고 알 때 두 크기의 합으로, 모를 때는 곱으로 추정한다. 이 엔진은 원천 데이터셋의 실제 크기에서 시작해 입력이 하나인 변환은 크기를 바꾸지 않고 조인은 두 크기를 더한다. 그래서 filter 가 행을 거의 다 버려도 추정 크기는 줄지 않고 브로드캐스트 조인을 고르지 못한다. 4단계의 적응 실행이 이 문제를 셔플 뒤의 실제 크기로 고친다.

[2.6] 조인이 들어가면 계보는 목록이 아니라 나무가 된다. 스테이지 하나가 셔플 여럿을 읽기도 하고 스테이지 여럿의 출력이 스테이지 하나로 모이기도 한다. 이 엔진은 나무를 왼쪽 입력부터 차례로 따라가며 스테이지 번호를 붙인다.

[2.7] 키 하나에 행이 몰리면 그 키가 든 파티션 하나가 커지고 그 파티션을 맡은 태스크 하나 때문에 스테이지 전체가 늦어진다. Spark 의 적응 실행은 셔플이 끝난 뒤 조인의 각 입력에서 파티션 크기를 재어 파티션이 그 입력의 파티션 크기 중앙값에 배수(`skewedPartitionFactor`, 기본 5)를 곱한 값보다 크고 절대 크기(`skewedPartitionThresholdInBytes`, 기본 256MB)보다도 크면 쏠렸다고 판정한다. 배수 조건만 있다면 파티션이 모두 작을 때도 중앙값의 몇 배인 파티션을 쪼개고 절대 크기 조건만 있다면 모든 파티션이 고르게 클 때 전부 쏠렸다고 판정할 것이다.

[2.8] 쏠린 파티션은 여러 조각으로 쪼개 조각마다 태스크 하나에 맡기고 상대편의 같은 번호 파티션은 조각마다 복제해 짝짓는다. 양쪽의 같은 번호 파티션이 모두 쏠렸으면 왼쪽 조각과 오른쪽 조각의 모든 조합마다 태스크가 생긴다. 쪼개는 단위는 맵 태스크 하나가 그 파티션 몫으로 쓴 출력이다. 그래서 쏠린 파티션의 데이터가 모두 맵 태스크 하나에서 왔다면 쪼갤 수 없다. Spark 는 조각 하나의 목표 크기를 권장 크기(`advisoryPartitionSizeInBytes`, 기본 64MB)와 쏠리지 않은 파티션의 평균 크기 가운데 큰 쪽으로 정한다. Spark 는 쏠림 처리를 적응 실행의 일부로 하지만 이 엔진은 3단계부터 적응 실행과 상관없이 쏠림 처리를 한다.

## 3. 입출력 규칙

[3.1] 1단계의 입출력 규칙을 그대로 쓴다.

## 4. 명령 규칙

### join 변환

[4.1] join 변환은 `join <이름> <n>` 이나 `join <이름> <n> nullsafe` 다. 이름은 1단계 4.1 의 이름이고 n 은 1 이상 1,000 이하의 정수 토큰이다. join 은 첫 자리에 올 수 없고 둘째 자리부터 어디든 온다. 이름은 1단계 4.14 의 셋째 확인 대상이므로 PIPE 는 source 의 이름과 join 의 이름을 변환 순서대로 확인해 처음 나온 없는 이름을 `ERR NOT_FOUND <이름>` 으로 알린다.

[4.2] join 의 왼쪽 입력은 계보에서 join 바로 앞까지의 변환이 내보내는 행이고 오른쪽 입력은 이름이 가리키는 데이터셋이나 파이프라인을 1단계 4.16 대로 펼친 계보가 내보내는 행이다. join 뒤의 변환은 join 의 출력을 입력으로 받으므로 계보는 join 마다 두 갈래로 갈라지는 나무가 된다. 같은 파이프라인을 두 번 쓰면 펼친 계보가 두 벌 생기고 엔진은 두 벌을 따로 계산한다.

[4.3] 3단계는 값 종류에 짝 값을 더한다. 짝 값은 왼쪽 값과 오른쪽 값을 차례로 담은 정수 값 두 개다. join 은 두 입력이 모두 정수 값일 때만 받고 짝 값을 내보낸다. `map key prefix`, `filter key`, `repartition`, `coalesce` 는 짝 값도 받고 그대로 내보낸다. `map value sum`, `map value min`, `map value max` 는 목록 값과 짝 값을 받고 정수 값을 내보낸다. 짝 값에서 sum 은 두 값의 합이고 min 과 max 는 두 값 가운데 작은 값과 큰 값이다. `map value count` 와 1단계 4.13 에서 정수 값만 받는 변환은 짝 값을 받지 않는다. 받지 않는 값 종류는 1단계 4.14 의 넷째 확인에서 `ERR TYPE <i>` 다. join 이 받지 않는 값 종류이면 i 는 그 join 의 번호다.

[4.4] join 은 왼쪽 행과 오른쪽 행의 모든 쌍 가운데 두 키가 짝이 되는 쌍마다 결과 행 하나를 낸다. nullsafe 가 없으면 두 키가 모두 NULL 이 아니고 같을 때만 짝이 된다. nullsafe 가 있으면 두 키가 모두 NULL 일 때도 짝이 된다. 결과 행의 키는 그 키이고 값은 왼쪽 행의 값을 앞에, 오른쪽 행의 값을 뒤에 둔 짝 값이다. 브로드캐스트 조인에서 빌드 쪽이 왼쪽이어도 짝의 앞은 왼쪽 값이다.

[4.5] RUN 은 짝 값을 왼쪽 값과 오른쪽 값을 쉼표로 이어 괄호로 감싸 `(5,100)` 처럼 쓴다. 결과 행의 순서는 1단계 4.34 대로 키로 정하고 키가 같으면 왼쪽 값, 그다음 오른쪽 값이 작은 쪽이 앞선다. 2단계 4.12 의 레코드 크기에서 짝 값의 값 부분은 16 이다.

### 크기 추정

[4.6] 계획을 세울 때 계보의 변환마다 출력의 추정 크기를 바이트 단위로 정한다. `source d` 는 계획을 세우는 때 데이터셋 d 에 있는 모든 행의 2단계 4.12 레코드 크기를 더한 값이다. join 은 왼쪽 입력과 오른쪽 입력의 추정 크기를 더한 값이다. 나머지 변환은 입력의 추정 크기를 그대로 쓴다. PLAN, RUN, PROFILE 은 실행하는 때마다 계획을 새로 세우므로 그 사이에 APPEND 로 행이 늘면 추정 크기와 조인 전략이 바뀔 수 있다.

### 조인 전략

[4.7] 브로드캐스트 임계값은 처음에 10485760 이다. `SET broadcastThreshold <T>` 로 바꾼다. T 는 −1 이거나 0 이상 1,000,000,000,000,000 이하의 정수 토큰이다.

[4.8] 임계값이 −1 이 아니고 두 입력 가운데 추정 크기가 임계값 이하인 쪽이 있으면 브로드캐스트 조인이다. 그런 쪽이 하나이면 그쪽이 빌드 쪽이다. 둘 다 그렇다면 추정 크기가 작은 쪽이 빌드 쪽이고 크기가 같으면 오른쪽이 빌드 쪽이다. 나머지 경우는 정렬 병합 조인이다.

[4.9] 브로드캐스트 조인에서 조인과 빌드 쪽 입력 사이는 브로드캐스트 경계이고 조인과 스트림 쪽 입력 사이는 좁은 의존이다. 조인의 출력은 스트림 쪽 입력의 파티션 수와 파티셔너를 그대로 가진다. n 은 쓰지 않는다.

[4.10] 정렬 병합 조인의 출력은 파티션 수가 n 이고 해시 파티셔너 n 으로 나뉘어 있다. 입력마다 따로 의존을 정한다. 입력이 해시 파티셔너 n 으로 나뉘어 있으면 그 입력과는 좁은 의존이고 그렇지 않으면 넓은 의존이다. 넓은 의존인 입력은 2단계 4.7 의 해시 규칙으로 받는 파티션 n 개에 셔플한다. 4.9 와 이 문단이 정한 조인 출력의 파티셔너는 어떤 출력에도 드러나지 않는다. 파티셔너를 쓰는 reduceByKey, groupByKey, join 은 짝 값을 받지 않고 짝 값을 정수 값으로 바꾸는 map 은 파티셔너를 지우기 때문이다.

[4.11] 계획의 변환 표시는 브로드캐스트 조인이 `join(broadcast-left)` 나 `join(broadcast-right)` 이고 정렬 병합 조인이 `join(sortmerge)` 다. left 와 right 는 빌드 쪽이 어느 쪽인지 나타낸다.

### 스테이지

[4.12] 1단계 4.26 과 4.27 을 나무로 넓힌다. 계보의 나무에서 넓은 의존과 브로드캐스트 경계를 모두 끊으면 나무가 여러 조각으로 나뉜다. 조각 하나하나가 스테이지다. 스테이지마다 출력을 내보내는 마지막 변환이 하나 있다.

[4.13] 스테이지 안의 변환 표시는 그 스테이지의 변환을 후위 순서로 늘어놓은 것이다. 후위 순서는 join 이면 왼쪽 입력 쪽 변환들, 오른쪽 입력 쪽 변환들, join 자신의 차례이고 그 밖의 변환이면 입력 쪽 변환들 다음에 자신이 오는 차례다. 조인이 없으면 1단계 4.29 의 계보 차례와 같다.

[4.14] 스테이지 번호는 나무 전체를 후위 순서로 따라가며 붙인다. 따라가다가 어떤 스테이지의 마지막 변환에 이르면 그 스테이지에 아직 쓰지 않은 가장 작은 번호를 준다. 번호는 0 부터다. 이렇게 붙이면 스테이지는 자기가 읽는 스테이지보다 번호가 크고 조인의 왼쪽 입력 쪽에만 있는 스테이지는 오른쪽 입력 쪽에만 있는 스테이지보다 번호가 작다. 조인이 없으면 1단계 4.27 의 번호와 같다.

[4.15] 스테이지의 태스크 수는 1단계 4.27 대로 그 스테이지의 마지막 변환이 내보내는 파티션 수다. 보낼 곳은 출력이 넓은 의존으로 스테이지 s 에 가면 `shuffle <s>`, 브로드캐스트 경계로 스테이지 s 의 조인에 가면 `broadcast <s>`, 행동의 결과이면 `result` 다.

### 조인 실행

[4.16] 정렬 병합 조인의 출력 파티션 p 는 왼쪽 입력의 파티션 p 와 오른쪽 입력의 파티션 p 를 4.4 대로 짝지은 결과다. 넓은 의존인 입력의 파티션 p 는 그 셔플의 받는 파티션 p 이고 좁은 의존인 입력의 파티션 p 는 그 입력의 파티션 p 다. 브로드캐스트 조인의 출력 파티션 i 는 스트림 쪽 입력의 파티션 i 와 빌드 쪽 입력의 모든 행을 짝지은 결과다. 빌드 쪽 입력의 행은 빌드 쪽 마지막 스테이지의 모든 파티션에서 모은다. 어느 전략이든 조인의 출력 파티션 안에서 행은 4.5 의 결과 행 순서로 놓인다.

[4.17] 조인 입력의 셔플은 결합하지 않는다. 행 하나가 레코드 하나이고 2단계의 레코드 크기, 해시 규칙, 흘림 규칙을 그대로 쓴다. 조인 입력의 셔플을 받는 쪽은 2단계 4.11 의 repartition 처럼 온 레코드를 맵 태스크 번호 순서로 놓지만 조인이 4.16 대로 다시 정렬하므로 그 순서는 결과에 드러나지 않는다.

### 쏠림 처리

[4.18] 쏠림 배수는 처음에 5 이고 `SET skewFactor <F>` 로 바꾼다. F 는 1 이상 1,000 이하의 정수 토큰이다. 쏠림 임계값은 처음에 268435456 이고 `SET skewThreshold <S>` 로 바꾼다. S 는 0 이상 1,000,000,000,000,000 이하의 정수 토큰이다. 권장 크기는 처음에 67108864 이고 `SET advisorySize <A>` 로 바꾼다. A 는 1 이상 1,000,000,000,000,000 이하의 정수 토큰이다.

[4.19] RUN 과 PROFILE 은 실행 중에 정렬 병합 조인마다 쏠림을 처리할지 정한다. 두 입력을 모두 셔플하는 정렬 병합 조인이고 그 조인이 든 스테이지에 그 조인 말고는 map 과 filter 만 있을 때 쏠림을 처리한다. 이때 조인은 그 스테이지의 첫 변환이다. 쏠림 처리는 두 입력의 맵 태스크가 모두 끝난 뒤 받는 파티션의 실제 크기로 한다. PLAN 은 데이터를 계산하지 않으므로 쏠림 처리를 하지 않는다.

[4.20] 쏠림은 입력마다 따로 판정한다. 한 입력의 받는 파티션 크기를 b₀, b₁, …, bₙ₋₁ 이라 하자. bₚ 는 받는 파티션 p 가 받은 레코드의 바이트 수 합이다. 중앙값 m 은 크기를 작은 것부터 늘어놓아 n 이 홀수이면 가운데 값이고 n 이 짝수이면 가운데 두 값의 합을 2 로 나눈 몫이다. 쏠림 기준선 Q 는 m × F 와 S 가운데 큰 값이다. bₚ 가 Q 보다 크면 파티션 p 가 쏠렸다. 곧 bₚ 가 m × F 보다 크고 S 보다도 커야 쏠렸다.

[4.21] 쏠린 파티션을 쪼갤 때 조각 하나의 목표 크기 G 는 A 와 그 입력에서 쏠리지 않은 파티션 크기의 평균 가운데 큰 값이다. 평균은 쏠리지 않은 파티션 크기의 합을 그 파티션 수로 나눈 몫이다. F 가 1 이상이므로 쏠리지 않은 파티션은 언제나 하나 이상 있다.

[4.22] 쏠린 파티션 p 의 블록은 그 입력의 맵 태스크 0, 1, 2, … 가 파티션 p 몫으로 보낸 레코드 묶음이고 블록 크기는 그 레코드들의 바이트 수 합이다. 블록을 맵 태스크 번호 순서로 하나씩 조각에 넣는다. 크기가 0 보다 큰 블록은 지금 조각의 크기가 0 보다 크고 지금 조각의 크기에 그 블록 크기를 더하면 G 보다 클 때 새 조각을 시작하고 그렇지 않으면 지금 조각에 들어간다. 크기가 0 인 블록은 언제나 지금 조각에 들어간다. 조각이 하나뿐이면 그 파티션은 쪼개지 않는다.

[4.23] 파티션 p 에서 왼쪽 입력의 조각 수를 Lₚ, 오른쪽 입력의 조각 수를 Rₚ 라 하자. 쪼개지 않은 쪽은 조각 수가 1 이다. 파티션 p 는 Lₚ × Rₚ 개의 태스크로 실행하고 태스크마다 왼쪽 조각 하나와 오른쪽 조각 하나를 4.4 대로 짝짓는다. 이 태스크들의 출력이 실행할 때 조인의 출력 파티션이다. 출력 파티션은 p 가 작은 것부터, p 가 같으면 왼쪽 조각 순서, 그다음 오른쪽 조각 순서로 번호를 0 부터 다시 매긴다. 같은 스테이지의 map 과 filter 는 이 파티션마다 적용한다. 그래서 그 스테이지의 태스크 수는 모든 p 의 Lₚ × Rₚ 를 더한 값이고 그 스테이지가 셔플에 쓰면 이 번호가 맵 태스크 번호다.

[4.24] RUN 의 머리줄과 PROFILE 의 머리줄과 스테이지 줄은 실제로 실행한 태스크 수를 쓴다. 쏠림 처리를 한 스테이지는 PLAN 의 태스크 수와 다를 수 있다.

### PROFILE

[4.25] 빌드 쪽 마지막 스테이지는 PROFILE 에서 스테이지 줄 다음에 `broadcast rows <R> bytes <B>` 를 출력한다. R 은 빌드 쪽에서 모은 행 수이고 B 는 그 행들의 레코드 크기 합이다.

[4.26] 쏠림 처리로 쪼갠 파티션이 있는 스테이지는 스테이지 줄 바로 다음에 쪼갠 파티션마다 `skew <쪽> <p> <조각 수>` 를 한 줄씩 출력한다. 쪽은 `left` 나 `right` 다. p 가 작은 줄부터 쓰고 p 가 같으면 left 줄을 먼저 쓴다. 이때 2단계 4.17 의 셔플 줄과 4.25 의 broadcast 줄은 스테이지 줄 바로 다음이 아니라 skew 줄들 다음에 온다.

## 5. 오류 규칙

[5.1] join 변환이 4.1 의 꼴이 아니면 PIPE 는 `ERR SYNTAX` 다. 없는 이름은 `ERR NOT_FOUND <이름>`, 받지 않는 값 종류는 `ERR TYPE <i>` 이고 확인 순서는 1단계 4.14 그대로다.

[5.2] SET 의 설정 이름에 broadcastThreshold, skewFactor, skewThreshold, advisorySize 를 더한다. 값이 4.7 과 4.18 의 범위 밖이면 `ERR SYNTAX` 다. 줄이 세 토큰이고 설정 이름이 adaptive 나 minPartitionSize 이면 이 문서는 그 줄부터 입력 끝까지의 출력을 정하지 않는다. 이 두 이름은 4단계에서 더한다. 그 밖의 이름은 2단계 5.1 대로 `ERR UNKNOWN_SETTING` 이다.

[5.3] 3단계에서 새로 생기는 오류 줄은 없다.

## 6. 예시

[6.1] 예시의 형식은 1단계 6.1 과 같다. 6.2, 6.3, 6.4, 6.7 은 실행 한 번을 이 차례로 나누어 적은 것이므로 6.3, 6.4, 6.7 은 그 실행에서 앞선 블록의 줄을 생략한다. 6.5 와 6.6 은 저마다 새 실행이고 그 실행에 들지 않는다.

[6.2] 전략과 결과다. orders 의 추정 크기는 행 5개의 레코드 크기를 더한 58 이고 users 는 46 이다. 둘 다 처음 임계값 이하이므로 작은 users 가 빌드 쪽이다. 빌드 쪽은 스테이지 0 에서 계산해 스테이지 1 로 브로드캐스트하고 스트림 쪽 orders 는 조인과 같은 스테이지에 든다. 임계값을 −1 로 두면 정렬 병합 조인이 되어 양쪽을 셔플한다. 두 전략의 결과 행은 같다. 키가 NULL 인 행은 양쪽에 있어도 짝이 없다.

```
> DATA orders 2
> 0 u1 10
> 0 u2 20
> 1 u1 5
> 1 NULL 7
> 1 u3 1
> END
> DATA users 3
> 0 u1 100
> 1 u2 200
> 2 NULL 300
> 2 u4 400
> END
> PIPE j = source orders | join users 2
> PLAN j
stage 0 tasks 3 source(users) -> broadcast 1
stage 1 tasks 2 source(orders) join(broadcast-right) -> result
> PROFILE j
job 0 stages 2 tasks 5 rows 3
stage 0 tasks 3 source(users) -> broadcast 1
broadcast rows 4 bytes 46
stage 1 tasks 2 source(orders) join(broadcast-right) -> result
> RUN j
job 1 stages 2 tasks 5 rows 3
u1 (5,100)
u1 (10,100)
u2 (20,200)
> SET broadcastThreshold -1
> PLAN j
stage 0 tasks 2 source(orders) -> shuffle 2
stage 1 tasks 3 source(users) -> shuffle 2
stage 2 tasks 2 join(sortmerge) -> result
> PROFILE j
job 2 stages 3 tasks 7 rows 3
stage 0 tasks 2 source(orders) -> shuffle 2
write records 5 bytes 58 spills 0 sizes 46,12
stage 1 tasks 3 source(users) -> shuffle 2
write records 4 bytes 46 spills 0 sizes 22,24
stage 2 tasks 2 join(sortmerge) -> result
```

[6.3] nullsafe 다. NULL 키 행끼리 짝이 되어 결과가 한 행 늘고 NULL 키 행이 맨 앞에 온다.

```
> PIPE js = source orders | join users 2 nullsafe
> RUN js
job 3 stages 3 tasks 7 rows 4
NULL (7,300)
u1 (5,100)
u1 (10,100)
u2 (20,200)
```

[6.4] 이미 나뉜 입력이다. ra 와 rb 는 해시 파티셔너 2 로 나뉘어 있으므로 both 는 양쪽 모두 좁은 의존이라 조인이 두 reduceByKey 와 한 스테이지에 든다. one 은 오른쪽 users 만 셔플한다. mapped 의 map 은 파티셔너를 잃게 하므로 왼쪽을 다시 셔플한다. 이 계보에서 왼쪽 입력 쪽의 스테이지 0 과 1 이 오른쪽 입력 쪽의 스테이지 2 보다 먼저 번호를 받는다. other 는 n 이 3 이라 파티셔너가 맞지 않아 양쪽을 셔플한다. 임계값을 100 으로 올리면 both 의 두 입력이 모두 임계값 이하가 되고 이미 나뉜 입력이어도 브로드캐스트 조인을 고른다.

```
> PIPE ra = source orders | reduceByKey sum 2
> PIPE rb = source users | reduceByKey sum 2
> PIPE both = source ra | join rb 2
> PLAN both
stage 0 tasks 2 source(orders) -> shuffle 2
stage 1 tasks 3 source(users) -> shuffle 2
stage 2 tasks 2 reduceByKey reduceByKey join(sortmerge) -> result
> PIPE one = source ra | join users 2
> PLAN one
stage 0 tasks 2 source(orders) -> shuffle 2
stage 1 tasks 3 source(users) -> shuffle 2
stage 2 tasks 2 reduceByKey join(sortmerge) -> result
> PIPE mapped = source ra | map value add 0 | join rb 2
> PLAN mapped
stage 0 tasks 2 source(orders) -> shuffle 1
stage 1 tasks 2 reduceByKey map -> shuffle 3
stage 2 tasks 3 source(users) -> shuffle 3
stage 3 tasks 2 reduceByKey join(sortmerge) -> result
> PIPE other = source ra | filter value > 0 | join rb 3
> PLAN other
stage 0 tasks 2 source(orders) -> shuffle 1
stage 1 tasks 2 reduceByKey filter -> shuffle 4
stage 2 tasks 3 source(users) -> shuffle 3
stage 3 tasks 2 reduceByKey -> shuffle 4
stage 4 tasks 3 join(sortmerge) -> result
> SET broadcastThreshold 100
> PLAN both
stage 0 tasks 2 source(orders) -> shuffle 3
stage 1 tasks 3 source(users) -> shuffle 2
stage 2 tasks 2 reduceByKey -> broadcast 3
stage 3 tasks 2 reduceByKey join(broadcast-right) -> result
```

[6.5] 크기 추정이다. left 와 right 는 추정 크기가 24 로 같아 임계값 24 에서는 오른쪽이 빌드 쪽이 되고 임계값 23 에서는 정렬 병합 조인이 된다. fb 는 big 의 행을 하나만 남기지만 추정 크기는 big 과 같은 72 라서 임계값 71 에서 브로드캐스트되지 않는다. w 의 추정 크기는 조인한 두 입력의 합인 48 이다. 임계값이 48 이면 v 의 빌드 쪽이 되고 47 이면 되지 못한다. 임계값 47 에서 v 의 오른쪽 입력 안에 있는 t 는 여전히 브로드캐스트 조인이다. x 는 왼쪽 입력이 빌드 쪽이지만 짝 값의 앞에는 여전히 왼쪽 값이 온다. 이 블록은 새 실행이다.

```
> DATA left 2
> 0 k1 1
> 1 k2 2
> END
> DATA right 1
> 0 k1 10
> 0 k3 30
> END
> DATA big 2
> 0 k1 1
> 0 k2 2
> 0 k3 3
> 1 k1 4
> 1 k2 5
> 1 k3 6
> END
> SET broadcastThreshold 24
> PIPE t = source left | join right 2
> PLAN t
stage 0 tasks 1 source(right) -> broadcast 1
stage 1 tasks 2 source(left) join(broadcast-right) -> result
> SET broadcastThreshold 23
> PLAN t
stage 0 tasks 2 source(left) -> shuffle 2
stage 1 tasks 1 source(right) -> shuffle 2
stage 2 tasks 2 join(sortmerge) -> result
> PIPE fb = source big | filter value > 5
> PIPE u = source big | join fb 2
> SET broadcastThreshold 71
> PLAN u
stage 0 tasks 2 source(big) -> shuffle 2
stage 1 tasks 2 source(big) filter -> shuffle 2
stage 2 tasks 2 join(sortmerge) -> result
> PIPE w = source t | map value sum
> PIPE v = source big | join w 2
> SET broadcastThreshold 48
> PLAN v
stage 0 tasks 1 source(right) -> broadcast 1
stage 1 tasks 2 source(left) join(broadcast-right) map -> broadcast 2
stage 2 tasks 2 source(big) join(broadcast-right) -> result
> SET broadcastThreshold 47
> PLAN v
stage 0 tasks 2 source(big) -> shuffle 3
stage 1 tasks 1 source(right) -> broadcast 2
stage 2 tasks 2 source(left) join(broadcast-right) map -> shuffle 3
stage 3 tasks 2 join(sortmerge) -> result
> RUN v
job 0 stages 4 tasks 7 rows 2
k1 (1,11)
k1 (4,11)
> PIPE x = source right | join big 2
> PLAN x
stage 0 tasks 1 source(right) -> broadcast 1
stage 1 tasks 2 source(big) join(broadcast-left) -> result
> RUN x
job 1 stages 2 tasks 3 rows 4
k1 (10,1)
k1 (10,4)
k3 (30,3)
k3 (30,6)
```

[6.6] 쏠림이다. views 를 받는 파티션 4개로 셔플하면 a 다섯 행과 e 가 파티션 1 에 모여 크기가 66 이 된다. 크기 11, 66, 11, 11 의 중앙값은 11 이고 쏠림 기준선은 11 × 2 와 20 가운데 큰 22 다. 쏠리지 않은 파티션의 평균은 11 이므로 목표 크기는 25 다. 파티션 1 의 블록은 맵 태스크마다 22 라서 조각 셋으로 쪼개지고 조인 스테이지의 태스크는 1 + 3 + 1 + 1 = 6 개가 된다. items 의 파티션 1 은 크기가 기준선 22 와 같아 쏠리지 않았다. 권장 크기를 50 으로 올리면 처음 두 블록이 한 조각에 들어가 조각이 둘이 된다. 100 으로 올리면 조각이 하나뿐이라 쪼개지 않는다. hot_total 에서는 조인 스테이지의 태스크 6개가 다음 셔플의 맵 태스크가 되므로 키 a 의 레코드가 조각마다 하나씩 세 개 나간다. 이 블록은 새 실행이다.

```
> DATA views 3
> 0 a 1
> 0 a 2
> 0 b 3
> 1 a 4
> 1 a 5
> 1 c 6
> 2 a 7
> 2 d 8
> 2 e 9
> END
> DATA items 2
> 0 a 10
> 0 b 20
> 1 c 30
> 1 a 40
> END
> SET broadcastThreshold -1
> SET skewThreshold 20
> SET skewFactor 2
> SET advisorySize 25
> PIPE hot = source views | join items 4
> PLAN hot
stage 0 tasks 3 source(views) -> shuffle 2
stage 1 tasks 2 source(items) -> shuffle 2
stage 2 tasks 4 join(sortmerge) -> result
> PROFILE hot
job 0 stages 3 tasks 11 rows 12
stage 0 tasks 3 source(views) -> shuffle 2
write records 9 bytes 99 spills 0 sizes 11,66,11,11
stage 1 tasks 2 source(items) -> shuffle 2
write records 4 bytes 44 spills 0 sizes 0,22,11,11
stage 2 tasks 6 join(sortmerge) -> result
skew left 1 3
> SET advisorySize 50
> PROFILE hot
job 1 stages 3 tasks 10 rows 12
stage 0 tasks 3 source(views) -> shuffle 2
write records 9 bytes 99 spills 0 sizes 11,66,11,11
stage 1 tasks 2 source(items) -> shuffle 2
write records 4 bytes 44 spills 0 sizes 0,22,11,11
stage 2 tasks 5 join(sortmerge) -> result
skew left 1 2
> SET advisorySize 100
> RUN hot 3
job 2 stages 3 tasks 9 rows 12
a (1,10)
a (1,40)
a (2,10)
> SET advisorySize 25
> PIPE hot_total = source hot | map value sum | reduceByKey sum 2
> PROFILE hot_total
job 3 stages 4 tasks 13 rows 3
stage 0 tasks 3 source(views) -> shuffle 2
write records 9 bytes 99 spills 0 sizes 11,66,11,11
stage 1 tasks 2 source(items) -> shuffle 2
write records 4 bytes 44 spills 0 sizes 0,22,11,11
stage 2 tasks 6 join(sortmerge) map -> shuffle 3
skew left 1 3
write records 5 bytes 55 spills 0 sizes 11,44
stage 3 tasks 2 reduceByKey -> result
> RUN hot_total
job 4 stages 4 tasks 13 rows 3
a 288
b 23
c 36
```

[6.7] 짝 값과 오류다. spend 는 짝 값을 합으로 바꾼 뒤 사용자마다 가장 큰 값을 고른다. 이때 임계값은 6.4 끝에서 정한 100 이므로 j 는 브로드캐스트 조인이다. 짝 값에는 count 를 쓸 수 없다. bad2 의 둘째 join 은 왼쪽 입력이 짝 값이라 받지 않는다. bad3 의 join 은 왼쪽 입력이 목록 값이다. 이 블록은 6.4 에 이어 실행한 것이다.

```
> PIPE spend = source j | map value sum | reduceByKey max 1
> RUN spend
job 4 stages 3 tasks 6 rows 2
u1 110
u2 220
> PIPE bad1 = source j | map value count
ERR TYPE 2
> PIPE bad2 = source orders | join ra 2 | join users 2
ERR TYPE 3
> PIPE bad3 = source orders | groupByKey 2 | join users 2
ERR TYPE 3
> PIPE bad4 = source orders | join users
ERR SYNTAX
> PIPE bad5 = source orders | join nope 2 | join zzz 2
ERR NOT_FOUND nope
> SET broadcastThreshold -2
ERR SYNTAX
> SET skewFactor 0
ERR SYNTAX
```

## 7. 검사 방식

[7.1] 입력 방식, 종료 코드 검사, 표준 출력 비교, 일반 테스트 제한 시간 10초, 입력 한계는 2단계와 같다. PLAN, RUN, PROFILE 이 다루는 계보의 변환 수 1,000개에는 join 의 오른쪽 입력을 펼친 변환도 들어간다.

[7.2] 3단계 검사 입력에서 쏠림 기준선, 추정 크기, 바이트 수는 64비트 정수를 넘지 않는다.

[7.3] 대규모 테스트는 파티션 8개짜리 사실 데이터셋에 행 500,000개, 파티션 4개짜리 차원 데이터셋에 행 100,000개를 넣는다. 서로 다른 키는 100,000개 안팎이고 사실 데이터셋의 키 몇 개에는 행이 수천 개씩 몰린다. 차원 데이터셋에서 NULL 이 아닌 키는 저마다 많아야 세 번 나오고 NULL 키 행도 섞인다. 정렬 병합 조인과 브로드캐스트 조인, 이미 나뉜 입력의 조인, nullsafe 조인, 쏠림 처리가 일어나는 조인을 섞어 RUN 과 PROFILE 을 모두 15개 안팎 실행한다. RUN 은 모두 개수를 20 이하로 준다. 제한 시간은 20초다. 파티션마다 왼쪽 행과 오른쪽 행의 모든 쌍을 비교하거나 스트림 쪽 행마다 빌드 쪽 행을 모두 훑는 풀이는 이 제한 시간을 넘기도록 규모를 정했다.

[7.4] 3단계 검사 입력에는 5.2 가 출력을 정하지 않은 줄이 나오지 않는다.

[7.5] 3단계를 검사할 때 1단계와 2단계 테스트도 다시 실행한다. 4단계를 검사할 때도 3단계 테스트를 다시 실행한다.
