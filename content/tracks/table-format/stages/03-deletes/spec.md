# 3단계: 행 단위 삭제와 압축

## 1. 개요

[1.1] 3단계는 데이터 파일을 다시 쓰지 않고 몇 행만 지우는 행 단위 삭제를 더한다. POSDEL 은 데이터 파일 하나와 그 파일 안의 행 위치로 행을 지우는 위치 삭제 파일을 쓴다. EQDEL 은 열 값으로 행을 지우는 동등 삭제 파일을 쓴다. SCAN 은 읽을 때 순서 번호와 파티션 값으로 데이터 파일마다 적용할 삭제 파일을 고르고 COMPACT 는 삭제를 반영해 남은 행만 새 파일에 담는다.

[1.2] 1단계와 2단계의 규칙은 그대로다. 이 문서는 새로 생기는 규칙만 설명한다. 2단계 4.27 이 출력을 정하지 않은 POSDEL 과 EQDEL 의 출력을 이 문서가 정한다. 이 문서가 바꾸는 규칙은 모두 삭제 파일이 있을 때만 출력에 드러나므로 삭제 파일이 없는 입력에서 모든 명령은 앞 단계와 같은 출력을 낸다.

| 명령 | 하는 일 | 정상 출력 |
|---|---|---|
| `POSDEL <트랜잭션> <삭제 파일> <데이터 파일> <위치> …` | 위치 삭제 파일을 써서 트랜잭션이 더할 파일로 적는다 | 1줄 |
| `EQDEL <트랜잭션> <삭제 파일> <열 목록> <행> …` | 동등 삭제 파일을 써서 트랜잭션이 더할 파일로 적는다 | 1줄 |

[1.3] 앞 단계 명령에서 바뀌는 것은 다음과 같다. 커밋은 삭제 파일을 담는 매니페스트를 따로 쓰고 데이터 파일을 지울 때 그 파일을 가리키는 위치 삭제 파일도 함께 지운다(4.12~4.14). 충돌 판정은 위치 삭제가 걸린 파일을 더 따진다(4.15~4.16). SCAN 은 삭제된 행을 세지 않고 파일마다 적용한 삭제 파일 수를 보여 준다(4.17~4.21). COMPACT 는 삭제된 행을 옮기지 않는다(4.22). MANIFESTS, ENTRIES, EXPIRE, ORPHANS, LS 는 삭제 파일도 다룬다(4.23~4.25).

## 2. 배경 개념

[2.1] Iceberg 형식 판 2는 행 단위 삭제를 더했다. 삭제는 데이터 파일을 고치지 않고 지울 행을 적은 삭제 파일로 남긴다. 위치 삭제는 데이터 파일 경로와 그 파일 안의 행 위치로 행을 가리킨다. 쓰는 쪽은 지울 행이 어느 파일의 몇 번째 행인지 찾아야 하지만 읽는 쪽은 그 위치의 행만 빼면 된다. 동등 삭제는 `id = 5` 처럼 열 값으로 행을 가리킨다. 쓰는 쪽은 행을 찾지 않고 쓸 수 있지만 읽는 쪽은 적용 대상 파일의 행마다 값을 맞춰 봐야 한다. 형식 판 3은 위치 삭제를 데이터 파일마다 하나씩 두는 삭제 벡터로 담는다. 아직 채택되지 않고 개발 중인 형식 판 4는 새 동등 삭제를 쓰지 못하게 한다.

[2.2] 삭제 파일에도 데이터 파일처럼 파티션 값이 있고 삭제 파일도 매니페스트에 적힌다. 명세는 한 매니페스트가 데이터 파일과 삭제 파일 가운데 한 종류만 담게 하고 매니페스트 목록에 매니페스트의 내용 종류를 적게 한다. 스캔이 삭제 매니페스트에서도 DELETED 항목을 사용하지 않는 점은 데이터 매니페스트와 같다.

[2.3] 명세는 읽을 때 삭제 파일을 어느 데이터 파일에 적용할지 정한다. 위치 삭제 파일은 데이터 파일의 경로가 삭제 파일이 가리키는 경로와 같고 데이터 파일의 순서 번호가 삭제 파일의 순서 번호보다 크지 않으며 두 파일의 파티션이 같을 때 적용한다. 동등 삭제 파일은 데이터 파일의 순서 번호가 삭제 파일의 순서 번호보다 작고 두 파일의 파티션이 같을 때 적용한다. 그래서 동등 삭제는 같은 커밋이나 그 뒤의 커밋에서 순서 번호를 이어받은 데이터 파일의 행을 값이 같아도 지우지 않는다. 압축한 새 파일처럼 순서 번호를 직접 적은 데이터 파일에는 같은 커밋이나 그 뒤의 커밋이 더했어도 그 번호가 동등 삭제 파일의 순서 번호보다 작으면 그 동등 삭제가 적용된다(4.18, 4.22). 위치 삭제는 순서 번호가 같은 파일에도 적용되므로 같은 커밋에서 더한 행을 지울 수 있다. 명세에는 예외가 하나 더 있다. 파티션을 나누지 않는 설정으로 저장한 동등 삭제 파일은 파티션을 가리지 않고 적용된다.

[2.4] 압축은 바꿀 파일에서 삭제되지 않은 행만 새 파일에 옮긴다. 새 파일에 압축을 시작한 스냅숏의 순서 번호를 적으면 그때까지의 동등 삭제는 새 파일에 다시 적용되지 않는다. 압축하는 사이에 다른 커밋이 더한 동등 삭제는 순서 번호가 더 크므로 새 파일에도 적용되어 지운 행이 되살아나지 않는다. 반면 그사이에 더한 위치 삭제는 옛 파일의 경로를 가리키므로 새 파일에 적용되지 않는다. Iceberg 구현의 교체 연산은 이 삭제가 사라지지 않도록 시작 스냅숏 뒤에 바꿀 파일을 가리키는 위치 삭제가 더해졌으면 커밋하지 않는다. 새 파일에 시작 스냅숏의 순서 번호를 적은 교체는 이 검사에서 동등 삭제를 따지지 않는다.

[2.5] 위치 삭제를 커밋하는 쪽도 확인할 것이 있다. 위치 삭제가 가리키는 데이터 파일을 다른 커밋이 이미 지우거나 압축했으면 그 위치 삭제는 아무 행도 지우지 못한다. 압축한 새 파일에는 지우려던 행이 남는다. Iceberg 의 행 삭제 연산은 가리키는 데이터 파일이 아직 테이블에 있는지 확인하는 검사를 제공한다.

[2.6] 이 트랙은 행 단위 삭제를 단순하게 줄인다. 위치 삭제 파일 하나는 형식 판 3의 삭제 벡터처럼 데이터 파일 하나만 가리킨다. 파티션 설정이 하나뿐이므로 파티션을 가리지 않는 동등 삭제 파일은 없다. 동등 삭제 파일의 파티션 값은 그 파일에 적은 열 값으로 정하므로 열 목록에 모든 파티션 필드의 원본 열을 넣어야 한다. Iceberg 구현은 시작 스냅숏 뒤에 커밋된 스냅숏을 차례로 살펴 새 삭제를 찾지만 이 트랙은 현재 스냅숏의 삭제 파일과 순서 번호만 본다. Iceberg 구현은 어느 데이터 파일에도 적용되지 않게 된 삭제 파일을 커밋할 때 떼어 내기도 하지만 이 트랙은 지운 데이터 파일을 가리키는 위치 삭제 파일만 함께 지운다. 그래서 동등 삭제 파일은 그 파일을 더한 스냅숏과 그 뒤의 모든 스냅숏에 살아 있다. 삭제 파일의 열 값 하한과 상한은 사용하지 않는다.

## 3. 입출력 규칙

[3.1] 1단계의 입출력 규칙을 그대로 따른다.

## 4. 명령 규칙

### 삭제 파일

[4.1] 삭제 파일은 객체 저장소의 다섯째 종류 객체다. 삭제 파일의 이름은 POSDEL 과 EQDEL 에 적은 이름이고 데이터 파일 이름과 같은 규칙을 따른다. 파일 이름 하나는 입력 전체에서 데이터 파일과 삭제 파일을 통틀어 파일 하나에만 쓴다. 그래서 APPEND(1단계 5.5), COMPACT(2단계 5.3), POSDEL, EQDEL(5.3)은 FILE_EXISTS 를 확인할 때 데이터 파일 이름과 삭제 파일 이름을 함께 세어 쓴 적이 있는 이름인지 따진다. 객체 저장소에서 지운 파일의 이름도 센다(2단계 4.26). 이 문서에서 파일은 데이터 파일과 삭제 파일을 함께 가리킨다.

[4.2] 위치 삭제 파일에는 대상 데이터 파일 하나와 그 파일의 행 위치 목록이 있다. 위치 목록은 작은 위치부터 늘어놓고 같은 위치를 두 번 담지 않는다. 위치 삭제 파일의 파티션 값은 대상 데이터 파일의 파티션 값이고 행 수는 위치의 수다.

[4.3] 동등 삭제 파일에는 열 목록과 행 목록이 있다. 열 목록은 테이블의 열 가운데 몇 개를 적은 순서대로 늘어놓은 것이다. 행은 열 목록의 열마다 값 하나를 그 순서대로 담는다. 동등 삭제 파일의 행 수는 행의 수다.

### POSDEL

[4.4] `POSDEL <트랜잭션> <삭제 파일> <데이터 파일> <위치> …` 은 위치 삭제 파일을 그 이름으로 객체 저장소에 바로 쓰고 트랜잭션이 더할 파일로 적는다. 인자의 데이터 파일이 대상 데이터 파일이고 위치들이 위치 목록이다.

[4.5] 위치는 `^(0|[1-9][0-9]{0,8})$` 에 맞는 정수이고 하나 이상이다. 위치는 인자에서 앞의 위치보다 커야 한다. 각 위치는 대상 데이터 파일의 행 수보다 작아야 한다.

[4.6] 대상 데이터 파일은 둘 가운데 하나다. 하나는 그 트랜잭션의 시작 스냅숏에 살아 있고 그 트랜잭션이 지울 파일로 적지 않은 데이터 파일이다. 다른 하나는 그 트랜잭션이 APPEND 나 COMPACT 로 쓴 데이터 파일이다. 한 트랜잭션은 같은 데이터 파일에 POSDEL 을 여러 번 할 수 있다.

[4.7] POSDEL 은 `<트랜잭션> POSDEL <삭제 파일> <파티션 값> rows=<위치의 수>` 를 출력한다.

[4.8] 한 트랜잭션에서 POSDEL 의 대상 데이터 파일이 된 파일은 그 트랜잭션의 REMOVE 대상이나 COMPACT 의 바꿀 파일이 될 수 없다. 1단계 4.23 과 2단계 4.12 의 조건에 이 조건을 더한다.

### EQDEL

[4.9] `EQDEL <트랜잭션> <삭제 파일> <열 목록> <행> …` 은 동등 삭제 파일을 그 이름으로 객체 저장소에 바로 쓰고 트랜잭션이 더할 파일로 적는다. 열 목록 인자는 테이블의 열 이름을 쉼표로 이은 것이다. 열 이름은 서로 달라야 하고 모든 파티션 필드의 원본 열이 들어 있어야 한다. 행은 하나 이상이고 각 행은 열 목록의 열 값을 그 순서대로 쉼표로 이은 인자 하나다. 각 값은 그 열의 형식에 맞아야 한다.

[4.10] 동등 삭제 파일의 행마다 파티션 필드의 원본 열 값에 변환을 적용해 파티션 값을 구한다. 모든 행의 파티션 값이 같아야 하고 그 값이 동등 삭제 파일의 파티션 값이다. 파티션을 나누지 않는 테이블에서는 파티션 값이 `{}` 다.

[4.11] EQDEL 은 `<트랜잭션> EQDEL <삭제 파일> <파티션 값> rows=<행 수>` 를 출력한다.

### 커밋

[4.12] 데이터 파일을 담는 매니페스트를 데이터 매니페스트라 하고 그 내용 종류는 `data` 다. 삭제 파일을 담는 매니페스트를 삭제 매니페스트라 하고 그 내용 종류는 `deletes` 다. 1단계 4.24 의 더할 파일에는 삭제 파일도 들어간다. 1단계 4.26 의 새 매니페스트는 더할 데이터 파일이 있을 때만 쓰고 더할 데이터 파일만 담는다. 이 매니페스트를 새 데이터 매니페스트라 한다. 트랜잭션에 더할 삭제 파일이 있으면 커밋 시도는 새 삭제 매니페스트를 하나 더 쓴다. 새 삭제 매니페스트는 더할 삭제 파일마다 ADDED 항목 하나를 트랜잭션에 적은 순서대로 담고 항목의 스냅숏 ID 와 순서 번호는 1단계 4.26 과 같다. 새 삭제 매니페스트도 트랜잭션의 첫 커밋 시도에서 한 번만 쓴다. 두 번째 시도는 새 삭제 매니페스트를 다시 쓰지 않고 4.14 의 삭제 매니페스트 부분 맨 앞에 넣으며 목록에 두 번째 시도의 새 스냅숏 순서 번호로 적는다.

[4.13] 커밋 시도에서 지울 파일에는 트랜잭션이 지울 파일로 적은 데이터 파일 말고도 그 시도의 부모 스냅숏에 살아 있는 위치 삭제 파일 가운데 대상 데이터 파일이 그 데이터 파일들 가운데 하나인 파일이 들어간다. 1단계 4.27 은 이 위치 삭제 파일이 든 삭제 매니페스트도 같은 방법으로 다시 쓴다.

[4.14] 새 스냅숏의 매니페스트 목록은 데이터 매니페스트 부분 뒤에 삭제 매니페스트 부분을 둔다. 데이터 매니페스트 부분은 새 데이터 매니페스트와 부모 스냅숏의 데이터 매니페스트로 1단계 4.28 대로 만든다. 삭제 매니페스트 부분은 새 삭제 매니페스트와 부모 스냅숏의 삭제 매니페스트로 같은 방법으로 만든다. 한 시도는 새 데이터 매니페스트, 새 삭제 매니페스트, 다시 쓴 매니페스트들, 매니페스트 목록, 메타데이터 파일 순서로 객체를 쓴다. 다시 쓴 매니페스트들은 부모 스냅숏의 목록 순서대로 쓴다.

### 충돌

[4.15] 2단계 4.7 의 충돌에 둘을 더한다. 첫째로 POSDEL 의 대상 데이터 파일 가운데 시작 스냅숏에 살아 있던 파일이 판정에 사용하는 스냅숏에 살아 있지 않으면 그 데이터 파일이 충돌을 일으킨 파일이다. 둘째로 판정에 사용하는 스냅숏에 살아 있는 위치 삭제 파일 가운데 순서 번호가 시작 스냅숏의 순서 번호보다 크고 대상 데이터 파일이 그 트랜잭션의 COMPACT 가 바꿀 파일인 것이 있으면 그 바꿀 파일이 충돌을 일으킨 파일이다. 2단계 4.3 의 CONFLICT 줄에는 세 경우의 충돌을 일으킨 파일을 모두 모아 사전식 순서로 가장 앞선 파일을 쓴다.

[4.16] 동등 삭제 파일은 충돌을 일으키지 않는다. 동등 삭제 파일만 더한 트랜잭션은 APPEND 만 한 트랜잭션처럼 두 번째 시도에서 언제나 커밋한다.

### 읽을 때 삭제 적용

[4.17] 스냅숏 S 를 읽을 때 S 에 살아 있는 데이터 파일 F 의 순서 번호(1단계 4.18)를 f 라 한다. S 에 살아 있는 위치 삭제 파일 D 의 순서 번호를 같은 방법으로 d 라 할 때 D 의 대상 데이터 파일이 F 이고 f 가 d 보다 크지 않으면 S 에서 D 는 F 에 적용된다. D 와 F 의 파티션 값은 4.2 에 따라 언제나 같다.

[4.18] S 에 살아 있는 동등 삭제 파일 E 의 순서 번호를 같은 방법으로 e 라 할 때 E 와 F 의 파티션 값이 같고 f 가 e 보다 작으면 S 에서 E 는 F 에 적용된다. f 와 e 가 같으면 적용되지 않는다.

[4.19] S 에서 F 의 위치 p 인 행은 F 에 적용되는 위치 삭제 파일 가운데 위치 목록에 p 가 든 파일이 있거나 F 에 적용되는 동등 삭제 파일 가운데 그 파일의 열 목록의 모든 열에서 그 행과 값이 같은 행을 담은 파일이 있으면 삭제된 행이다.

[4.20] SCAN 이 계획하는 파일은 1단계 4.39~4.41 대로 정한다. 삭제 매니페스트도 데이터 매니페스트와 같은 규칙으로 제외하며 첫 줄의 매니페스트 수에 들어간다. 삭제 매니페스트의 항목이 가리키는 삭제 파일은 계획한 파일이 되지 않는다. 계획한 데이터 파일에 적용되는 삭제 파일은 그 데이터 파일과 파티션 값이 같으므로 그 삭제 파일이 든 삭제 매니페스트는 제외되지 않는다.

[4.21] SCAN 은 계획한 파일마다 읽는 스냅숏에서 삭제되지 않았고 조건에 맞는 행만 센다. 첫 줄의 `rows=` 는 이렇게 센 행 수의 합이다. 계획한 파일의 줄은 그 파일에 적용되는 삭제 파일이 하나 이상이면 `<파일> rows=<행 수> deletes=<적용되는 삭제 파일 수>` 이고 없으면 1단계 4.42 와 같다. 적용되는 삭제 파일은 4.17 과 4.18 로만 정하므로 그 파일의 행을 하나도 지우지 않는 삭제 파일도 `deletes=` 에 센다. 열의 하한과 상한은 삭제를 반영하지 않으므로 계획한 파일의 행이 모두 삭제되었어도 그 파일은 `rows=0` 으로 나온다.

### COMPACT

[4.22] COMPACT 의 새 파일에는 바꿀 파일의 행 가운데 그 트랜잭션의 시작 스냅숏에서 삭제되지 않은 행만 옮긴다. 행의 순서는 2단계 4.13 과 같다. 옮길 행이 하나도 없으면 오류다(5.6). 같은 트랜잭션이 COMPACT 앞에 POSDEL 이나 EQDEL 로 적은 삭제 파일은 시작 스냅숏에 없으므로 이 행을 고를 때 사용하지 않는다. 새 파일의 순서 번호는 2단계 4.15 대로 시작 스냅숏의 순서 번호다.

### 조회와 정리

[4.23] MANIFESTS 의 내용 종류 자리에는 데이터 매니페스트면 `data`, 삭제 매니페스트면 `deletes` 를 쓴다. 삭제 매니페스트의 상태별 항목 수와 파티션 범위도 1단계 4.17 대로 그 매니페스트의 항목과 삭제 파일의 파티션 값으로 정한다.

[4.24] ENTRIES 는 삭제 파일을 가리키는 항목의 줄 끝에 위치 삭제 파일이면 공백 하나와 `position <대상 데이터 파일>` 을, 동등 삭제 파일이면 공백 하나와 `equality <열 목록>` 을 붙인다. 열 목록은 EQDEL 에 적은 인자 그대로다. 이 줄의 `rows=` 는 삭제 파일의 행 수다.

[4.25] 2단계 4.16 의 사용 중인 객체에는 살아 있는 항목이 가리키는 삭제 파일과 활동 중인 트랜잭션이 POSDEL 과 EQDEL 로 쓴 삭제 파일도 들어간다. EXPIRE 와 ORPHANS 의 `files=` 는 지운 데이터 파일과 삭제 파일의 수를 합친 값이다. 트랜잭션이 끝나면 그 트랜잭션이 쓰고 커밋하지 못한 삭제 파일도 사용 중이 아닌 객체가 된다. LS 는 삭제 파일의 이름도 보여 준다.

## 5. 오류 규칙

[5.1] 1단계와 2단계의 오류 규칙을 그대로 따른다. 1단계 5.2 의 명령 목록에 POSDEL 과 EQDEL 을 더한다.

[5.2] 인자 수나 형식이 아래와 맞지 않으면 `ERR BAD_ARGUMENT` 를 출력한다. POSDEL 의 인자는 트랜잭션 이름, 삭제 파일 이름, 데이터 파일 이름, 위치 하나 이상이고 위치는 4.5 의 앞 두 문장을 지켜야 한다. EQDEL 의 인자는 트랜잭션 이름, 삭제 파일 이름, 열 목록, 행 하나 이상이고 열 목록과 행은 4.9 를 지켜야 한다.

[5.3] POSDEL 과 EQDEL 은 1단계 5.5 의 APPEND 처럼 트랜잭션을 확인하고 이어서 삭제 파일 이름을 이미 쓴 적이 있으면 `<트랜잭션> ERR FILE_EXISTS <삭제 파일>` 을 출력한다. 그다음 POSDEL 은 대상 데이터 파일이 4.6 을 어기면 `<트랜잭션> ERR NOT_LIVE <데이터 파일>` 을 출력한다. 마지막으로 POSDEL 은 위치를 인자 순서대로 보고 대상 데이터 파일의 행 수보다 작지 않은 첫 위치에 `<트랜잭션> ERR BAD_POSITION <위치>` 를 출력한다. EQDEL 은 트랜잭션과 파일 이름을 확인한 뒤 행의 파티션 값이 모두 같지는 않으면 `<트랜잭션> ERR MIXED_PARTITION` 을 출력한다.

[5.4] REMOVE 는 파일이 1단계 4.23 이나 4.8 을 어기면 `<트랜잭션> ERR NOT_LIVE <파일>` 을 출력한다. COMPACT 는 2단계 5.3 에서 바꿀 파일을 인자 순서대로 볼 때 2단계 4.12 의 앞 두 문장이나 4.8 을 어긴 첫 파일에 `<트랜잭션> ERR NOT_LIVE <파일>` 을 출력한다. 4.8 의 조건은 이 확인에 들어가고 따로 확인하는 차례는 없다.

[5.5] REMOVE 의 파일, COMPACT 의 바꿀 파일, POSDEL 의 대상 데이터 파일 자리에 적은 삭제 파일은 시작 스냅숏에 살아 있어도 5.3 과 5.4 의 NOT_LIVE 확인에 걸린다. 1단계 4.23, 2단계 4.12, 4.6 은 이 세 자리에 데이터 파일만 허용한다.

[5.6] COMPACT 는 2단계 5.3 의 확인을 모두 통과한 뒤 옮길 행이 하나도 없으면 `<트랜잭션> ERR NO_ROWS` 를 출력한다. 곧 COMPACT 는 트랜잭션, FILE_EXISTS, NOT_LIVE, MIXED_PARTITION, NO_ROWS 순서로 확인한다.

[5.7] 이 단계에서 새로 생긴 오류 줄은 아래와 같다.

| 오류 줄 | 뜻 |
|---|---|
| `<트랜잭션> ERR BAD_POSITION <위치>` | 대상 데이터 파일의 행 수보다 작지 않은 위치 |
| `<트랜잭션> ERR NO_ROWS` | COMPACT 가 새 파일로 옮길 행이 없음 |

## 6. 예시

[6.1] 예시를 읽는 방법은 1단계 6.1 과 같다.

[6.2] 순서 번호 규칙이다. b 는 동등 삭제 x1 과 데이터 파일 f2 를 한 커밋에 더한다. x1 과 f2 의 순서 번호는 모두 2 이므로 x1 은 f2 에 적용되지 않고 순서 번호가 1 인 f1 에만 적용된다. 그래서 id 2 인 행이 f1 에서는 삭제되고 f2 에서는 남는다. c 는 f1 의 위치 0 을 지우는 x2 를 쓰고 같은 커밋에서 더한 f3 의 위치 1 을 지우는 x3 을 쓴다. x3 과 f3 의 순서 번호는 3 으로 같지만 위치 삭제는 순서 번호가 같은 파일에도 적용된다. 매니페스트 목록에서는 데이터 매니페스트 셋 뒤에 삭제 매니페스트 둘이 온다.

```
> CREATE id:long kind:string
CREATE meta-1
> BEGIN a
a BEGIN - meta-1
> APPEND a f1 1,x 2,y 3,z
a APPEND f1 {} rows=3
> COMMIT a
a COMMIT seq=1 meta-2
> BEGIN b
b BEGIN a meta-2
> EQDEL b x1 id 2
b EQDEL x1 {} rows=1
> APPEND b f2 2,w 4,v
b APPEND f2 {} rows=2
> COMMIT b
b COMMIT seq=2 meta-3
> SCAN
SCAN b manifests=3/3 files=2 rows=4
f1 rows=2 deletes=1
f2 rows=2
> BEGIN c
c BEGIN b meta-3
> POSDEL c x2 f1 0
c POSDEL x2 {} rows=1
> APPEND c f3 5,u 6,t
c APPEND f3 {} rows=2
> POSDEL c x3 f3 1
c POSDEL x3 {} rows=1
> COMMIT c
c COMMIT seq=3 meta-4
> SCAN
SCAN c manifests=5/5 files=3 rows=4
f1 rows=1 deletes=2
f2 rows=2
f3 rows=1 deletes=1
> SCAN WHERE kind=z
SCAN c manifests=5/5 files=1 rows=1
f1 rows=1 deletes=2
> MANIFESTS c
MANIFESTS c 5
c-m1 data seq=3 added=1 existing=0 deleted=0
b-m1 data seq=2 added=1 existing=0 deleted=0
a-m1 data seq=1 added=1 existing=0 deleted=0
c-m2 deletes seq=3 added=2 existing=0 deleted=0
b-m2 deletes seq=2 added=1 existing=0 deleted=0
> ENTRIES c-m2
ENTRIES c-m2 2
ADDED x2 snapshot=c seq=3 {} rows=1 position f1
ADDED x3 snapshot=c seq=3 {} rows=1 position f3
```

[6.3] 압축이 삭제를 반영하는 예다. id 1, 3, 5 는 버킷 1 이고 2, 4 는 버킷 0 이다. 버킷 1 의 동등 삭제 x1 은 버킷 0 인 f2 에 적용되지 않는다. d 는 c 에서 시작해 f1 을 g1 으로 바꾼다. c 에서 f1 의 id 3 은 x1 이, id 5 는 x2 가 지웠으므로 g1 에는 id 1 인 행만 옮긴다. 그사이 e 가 id 1 을 지우는 동등 삭제 x3 을 커밋한다. d 는 다시 시도하지만 x3 은 동등 삭제라 충돌이 아니다. g1 의 순서 번호는 시작 스냅숏 c 의 순서 번호 3 이므로 순서 번호 4 인 x3 은 g1 에 적용되고 순서 번호 2 인 x1 은 적용되지 않는다. f1 을 가리키던 위치 삭제 x2 는 d 가 f1 과 함께 지우므로 c-m1 을 다시 쓴 d-m5 에 DELETED 로 남는다. d-m4 의 파티션 범위에는 DELETED 인 f1 의 버킷 1 도 들어간다. d-m2 와 d-m3 은 첫 시도가 다시 쓴 매니페스트라 어느 스냅숏도 사용하지 않는다.

```
> CREATE id:long kind:string PARTITION bucket[2](id)
CREATE meta-1
> BEGIN a
a BEGIN - meta-1
> APPEND a f1 1,x 3,y 5,z
a APPEND f1 {id_bucket=1} rows=3
> APPEND a f2 2,x 4,y
a APPEND f2 {id_bucket=0} rows=2
> COMMIT a
a COMMIT seq=1 meta-2
> BEGIN b
b BEGIN a meta-2
> EQDEL b x1 id 3
b EQDEL x1 {id_bucket=1} rows=1
> COMMIT b
b COMMIT seq=2 meta-3
> BEGIN c
c BEGIN b meta-3
> POSDEL c x2 f1 2
c POSDEL x2 {id_bucket=1} rows=1
> COMMIT c
c COMMIT seq=3 meta-4
> SCAN
SCAN c manifests=3/3 files=2 rows=3
f1 rows=1 deletes=2
f2 rows=2
> BEGIN d
d BEGIN c meta-4
> COMPACT d g1 f1
d COMPACT g1 {id_bucket=1} rows=1
> BEGIN e
e BEGIN c meta-4
> EQDEL e x3 id 1
e EQDEL x3 {id_bucket=1} rows=1
> COMMIT e
e COMMIT seq=4 meta-5
> COMMIT d
d RETRY meta-6
d COMMIT seq=5 meta-7
> SCAN
SCAN d manifests=5/5 files=2 rows=2
f2 rows=2
g1 rows=0 deletes=1
> MANIFESTS d
MANIFESTS d 5
d-m1 data seq=5 added=1 existing=0 deleted=0 id_bucket=1..1
d-m4 data seq=5 added=0 existing=1 deleted=1 id_bucket=0..1
e-m1 deletes seq=4 added=1 existing=0 deleted=0 id_bucket=1..1
d-m5 deletes seq=5 added=0 existing=0 deleted=1 id_bucket=1..1
b-m1 deletes seq=2 added=1 existing=0 deleted=0 id_bucket=1..1
> ENTRIES d-m1
ENTRIES d-m1 1
ADDED g1 snapshot=d seq=3 {id_bucket=1} rows=1
> ENTRIES d-m2
ERR NO_MANIFEST d-m2
```

[6.4] 위치 삭제가 걸린 충돌이다. 모든 id 는 버킷 0 이다. p 가 f1 과 f2 를 압축하는 사이에 q 가 f1 의 위치 0 을 지우는 x1 을 커밋한다. x1 의 순서 번호 2 는 p 의 시작 스냅숏 a 의 순서 번호 1 보다 크므로 p 는 f1 에서 충돌한다. 다음으로 r 이 f2 의 위치 1 을 지우려는 사이에 s 가 f1 과 f2 를 g2 로 바꾼다. s 의 시작 스냅숏 q 에서 f1 의 위치 0 은 x1 이 지웠으므로 g2 에는 행 넷이 들어가고 x1 은 f1 과 함께 지워진다. r 이 가리키는 f2 는 이미 테이블에 없으므로 r 은 f2 에서 충돌한다. ORPHANS 는 두 실패한 첫 시도가 쓴 meta-4, meta-6, snap-p-1, snap-r-1, p-m1, p-m2, r-m1 과 커밋하지 못한 g1, x2 를 지운다.

```
> CREATE id:long kind:string PARTITION bucket[2](id)
CREATE meta-1
> BEGIN a
a BEGIN - meta-1
> APPEND a f1 2,x 4,y 6,z
a APPEND f1 {id_bucket=0} rows=3
> APPEND a f2 8,x 20,y
a APPEND f2 {id_bucket=0} rows=2
> COMMIT a
a COMMIT seq=1 meta-2
> BEGIN p
p BEGIN a meta-2
> COMPACT p g1 f1 f2
p COMPACT g1 {id_bucket=0} rows=5
> BEGIN q
q BEGIN a meta-2
> POSDEL q x1 f1 0
q POSDEL x1 {id_bucket=0} rows=1
> COMMIT q
q COMMIT seq=2 meta-3
> COMMIT p
p RETRY meta-4
p CONFLICT f1
> BEGIN r
r BEGIN q meta-3
> POSDEL r x2 f2 1
r POSDEL x2 {id_bucket=0} rows=1
> BEGIN s
s BEGIN q meta-3
> COMPACT s g2 f1 f2
s COMPACT g2 {id_bucket=0} rows=4
> COMMIT s
s COMMIT seq=3 meta-5
> COMMIT r
r RETRY meta-6
r CONFLICT f2
> SCAN
SCAN s manifests=3/3 files=1 rows=4
g2 rows=4
> ENTRIES s-m1
ENTRIES s-m1 1
ADDED g2 snapshot=s seq=2 {id_bucket=0} rows=4
> ORPHANS
ORPHANS metadata=2 lists=2 manifests=3 files=2
```

[6.5] 오류다. 위치는 커지는 순서로 적어야 하고 행 수 2 인 f1 에는 위치 2 가 없다. 데이터 파일 이름으로 쓴 f2 는 삭제 파일 이름이 될 수 없다. 파티션 필드의 원본 열 id 가 빠진 열 목록과 같은 열을 두 번 적은 열 목록은 형식에 맞지 않는다. id 1 과 2 는 버킷이 달라 한 동등 삭제 파일에 담을 수 없다. 지울 파일로 적은 f2 는 POSDEL 의 대상이 될 수 없고 POSDEL 의 대상이 된 f1 은 바꿀 파일이 될 수 없다. c 가 id 3 을 지운 뒤 f1 의 두 행은 모두 삭제되었으므로 d 의 COMPACT 는 옮길 행이 없다.

```
> CREATE id:long kind:string PARTITION bucket[2](id)
CREATE meta-1
> BEGIN a
a BEGIN - meta-1
> APPEND a f1 1,x 3,y
a APPEND f1 {id_bucket=1} rows=2
> APPEND a f2 2,z
a APPEND f2 {id_bucket=0} rows=1
> COMMIT a
a COMMIT seq=1 meta-2
> BEGIN b
b BEGIN a meta-2
> POSDEL b x1 f1 1 0
ERR BAD_ARGUMENT
> POSDEL b x1 f1 2
b ERR BAD_POSITION 2
> POSDEL b f2 f1 0
b ERR FILE_EXISTS f2
> POSDEL b x1 f9 0
b ERR NOT_LIVE f9
> EQDEL b x1 kind x
ERR BAD_ARGUMENT
> EQDEL b x1 id,id 1,1
ERR BAD_ARGUMENT
> EQDEL b x1 id 1 2
b ERR MIXED_PARTITION
> REMOVE b f2
b REMOVE f2
> POSDEL b x1 f2 0
b ERR NOT_LIVE f2
> POSDEL b x1 f1 0
b POSDEL x1 {id_bucket=1} rows=1
> COMPACT b g1 f1
b ERR NOT_LIVE f1
> COMMIT b
b COMMIT seq=2 meta-3
> BEGIN c
c BEGIN b meta-3
> EQDEL c x2 id 3
c EQDEL x2 {id_bucket=1} rows=1
> COMMIT c
c COMMIT seq=3 meta-4
> BEGIN d
d BEGIN c meta-4
> COMPACT d g1 f1
d ERR NO_ROWS
> SCAN
SCAN c manifests=3/3 files=1 rows=0
f1 rows=0 deletes=2
```

## 7. 검사 방식

[7.1] 1단계 7.1~7.5, 7.7, 7.10 을 그대로 따른다.

[7.2] 대규모 테스트는 `CREATE id:long kind:string PARTITION bucket[8](id)` 로 만든 테이블에서 실행한다. 트랜잭션 6,000개 안팎이 대부분 하나씩 차례로 커밋하고 일부는 겹쳐서 RETRY 와 CONFLICT 를 낸다. 데이터 파일은 40,000개 안팎이고 파일마다 행이 8개 안팎이다. POSDEL 로 위치 삭제 파일 20,000개 안팎을, EQDEL 로 행이 4개 안팎인 동등 삭제 파일 10,000개 안팎을 쓴다. COMPACT 는 1,500번 안팎이다. SCAN 은 20,000번 안팎이고 절반가량은 AT 으로 과거 스냅숏을 읽는다. SCAN 의 조건은 id 같음이나 좁은 id 범위라서 SCAN 하나가 계획하는 파일이 몇 개 안팎이다. 계획한 데이터 파일 하나에 적용되는 삭제 파일은 평균 300개 안팎이고 많으면 1,300개를 넘는다. 그 대부분이 동등 삭제 파일이다. 제한 시간은 15초다. 계획한 데이터 파일마다 스냅숏의 살아 있는 삭제 파일을 모두 맞춰 보는 풀이와 행마다 적용되는 동등 삭제 파일의 행을 하나씩 맞춰 보는 풀이는 이 제한 시간을 넘기도록 규모를 정했다. 행마다 적용되는 동등 삭제 파일을 하나씩 확인하는 풀이와 SCAN 마다 적용되는 동등 삭제 파일의 행을 다시 모아 찾아보는 풀이도 실행 시간이 이 제한 시간에 가깝거나 그보다 길다.
