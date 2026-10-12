# 2단계: 낙관적 커밋과 만료

## 1. 개요

[1.1] 2단계는 같은 판에서 시작한 트랜잭션들이 차례로 커밋할 때의 규칙을 정한다. 기준 판이 현재 메타데이터 파일이 아닌 트랜잭션의 커밋 시도는 교체에 실패한다. 그러면 프로그램은 새 현재 판에서 충돌을 판정하고 충돌이 없으면 한 번 더 시도한다. 트랜잭션을 그만두는 ABORT, 데이터 파일 여러 개를 그 행을 옮겨 담은 파일 하나로 바꾸는 COMPACT, 스냅숏을 메타데이터에서 지우는 EXPIRE, 사용 중이 아닌 객체를 지우는 ORPHANS 도 더한다.

[1.2] 1단계의 규칙은 그대로다. 이 문서는 새로 생기는 규칙만 설명한다. 1단계 4.44 가 출력을 정하지 않은 줄 가운데 ABORT, COMPACT, EXPIRE, ORPHANS 의 줄과 기준 판이 현재 메타데이터 파일이 아닌 트랜잭션의 COMMIT 의 출력을 이 문서가 정한다. POSDEL 과 EQDEL 의 줄은 이 문서도 출력을 정하지 않는다(4.27).

| 명령 | 하는 일 | 정상 출력 |
|---|---|---|
| `ABORT <트랜잭션>` | 트랜잭션을 커밋하지 않고 끝낸다 | 1줄 |
| `COMPACT <트랜잭션> <새 파일> <바꿀 파일> …` | 바꿀 파일들의 행을 새 파일 하나에 옮겨 담고 바꿀 파일들을 지울 파일로 적는다 | 1줄 |
| `EXPIRE <스냅숏> …` | 스냅숏을 메타데이터에서 지우고 더는 사용 중이 아닌 객체를 지운다 | 1줄 |
| `ORPHANS` | 사용 중이 아닌 객체를 지운다 | 1줄 |

[1.3] COMMIT 의 새 출력 줄은 교체에 실패했다는 `<트랜잭션> RETRY <메타데이터 파일>` 과 충돌로 커밋하지 못했다는 `<트랜잭션> CONFLICT <파일>` 이다. RETRY 줄 뒤에는 COMMIT 줄이나 CONFLICT 줄이 이어지므로 COMMIT 줄 하나가 출력 줄을 둘 내기도 한다.

## 2. 배경 개념

[2.1] Iceberg 명세에 따르면 쓰는 쪽은 현재 판이 커밋 전에 바뀌지 않으리라 가정하고 새 메타데이터 파일을 낙관적으로 만든 뒤 포인터를 기준 판에서 새 판으로 바꾸어 커밋한다. 쓰는 쪽이 바탕으로 삼은 스냅숏이 더는 현재가 아니면 새 현재 판을 바탕으로 다시 시도해야 한다. 어떤 연산은 정해진 조건이 맞을 때 메타데이터 변경만 새 판에 다시 적용하는 방식으로 재시도한다. 쓰는 쪽이 무엇을 확인할지는 쓰는 쪽이 고르며 그 선택이 보장되는 격리 수준을 정한다.

[2.2] 재시도는 작업을 처음부터 되풀이하지 않는다. 이미 쓴 데이터 파일은 다시 쓰지 않고 매니페스트 목록과 메타데이터 파일은 시도할 때마다 새로 쓴다. 새 파일을 적은 매니페스트는 순서 번호를 비워 두고 매니페스트 목록에서 이어받으므로 재시도에서 순서 번호가 바뀌어도 다시 쓸 필요가 없다. 명세는 커밋이 실패해 다시 시도하면 순서 번호를 새로 받는다고 정한다.

[2.3] 명세가 정한 충돌 규칙은 연산마다 다르다. 파일을 더하기만 하는 추가 연산은 요구 조건이 없어 언제나 다시 적용할 수 있다. 압축처럼 파일 여러 개를 같은 행을 담은 파일로 바꾸는 교체 연산은 지울 파일이 아직 모두 테이블에 있는지 확인해야 한다. 특정 파일을 지우는 삭제 연산도 그 파일이 아직 테이블에 있는지 확인해야 한다. 다른 커밋이 압축할 파일 하나를 이미 지웠는데 압축을 다시 적용하면 새 파일이 지워진 행을 되살리므로 그런 압축은 실패해야 한다.

[2.4] 압축한 파일에는 옛 행이 들어 있다. Iceberg 명세는 옛 순서 번호에 속하는 데이터를 담은 새 파일에는 순서 번호를 이어받지 않고 직접 적도록 허용한다. Iceberg 의 Spark 압축 작업은 기본 설정에서 새 파일에 압축을 시작한 때의 스냅숏 순서 번호를 적는다. 이 번호가 왜 중요한지는 3단계의 삭제에서 드러난다.

[2.5] 스냅숏 만료는 메타데이터에서 스냅숏을 없애 테이블에서 빠지거나 압축으로 바뀐 데이터 파일을 저장소에서 지울 수 있게 한다. 만료는 남은 스냅숏이 아직 사용하는 파일을 지우지 않는다. 명세의 주석은 데이터 파일을 그 파일이 살아 있는 마지막 스냅숏이 사라질 때 지울 수 있다고 적는다. 만료된 스냅숏으로는 시간 여행을 할 수 없다. Iceberg 의 `expire_snapshots` 절차는 나이와 남길 개수로 만료할 스냅숏을 고르며 스냅숏 ID 를 직접 줄 수도 있다. 현재 스냅숏은 만료할 수 없다.

[2.6] 고아 파일은 테이블의 어떤 메타데이터도 가리키지 않는 파일이다. 교체에 실패한 시도가 남긴 매니페스트 목록과 메타데이터 파일, 커밋하지 못하고 끝난 쓰는 쪽의 데이터 파일이 고아 파일이 된다. 아직 커밋하지 않은 쓰는 쪽의 파일도 메타데이터가 가리키지 않으므로 Iceberg 의 `remove_orphan_files` 절차는 기본 설정에서 3일보다 오래된 파일만 지운다.

[2.7] 이 트랙은 Iceberg 의 커밋 규칙을 단순하게 줄인다. Iceberg 구현은 커밋을 시작할 때 최신 메타데이터를 다시 읽으므로 교체 실패는 다른 쓰는 쪽이 그사이에 커밋할 때만 생긴다. 이 트랙은 BEGIN 부터 COMMIT 까지를 그 사이로 보고 첫 시도를 BEGIN 때의 판으로 한다. Iceberg 는 실패한 시도의 파일을 지우려 하지만 이 트랙의 쓰는 쪽은 아무것도 지우지 않고 남은 파일을 ORPHANS 가 지운다. 이 트랙에는 시각이 없으므로 만료할 스냅숏은 ID 로만 고르고 고아 파일 정리는 나이 대신 활동 중인 트랜잭션이 쓴 파일인지로 거른다.

## 3. 입출력 규칙

[3.1] 1단계의 입출력 규칙을 그대로 따른다.

[3.2] COMMIT 은 출력 줄을 하나나 둘 낸다. 출력 줄의 순서는 4.2~4.4 가 정한다.

## 4. 명령 규칙

### 커밋 시도와 재시도

[4.1] COMMIT 의 첫 시도는 1단계 4.24~4.30 대로 트랜잭션의 기준 판을 바탕으로 하고 시도 번호는 1 이다. 교체에 성공하면 1단계 4.24 대로 트랜잭션을 끝내고 COMMIT 줄 하나를 출력한다.

[4.2] 트랜잭션의 기준 판이 현재 메타데이터 파일이 아니면 첫 시도의 교체는 실패한다. 이때 `<트랜잭션> RETRY <첫 시도가 쓴 메타데이터 파일>` 을 출력한다. 첫 시도가 쓴 매니페스트, 매니페스트 목록, 메타데이터 파일은 객체 저장소에 그대로 남는다.

[4.3] 첫 시도의 교체에 실패하면 그 순간의 현재 메타데이터 파일의 현재 스냅숏으로 충돌을 판정한다(4.7). 충돌이 있으면 `<트랜잭션> CONFLICT <파일>` 을 출력하고 트랜잭션을 끝낸다. 파일 자리에는 충돌을 일으킨 파일 가운데 사전식 순서로 가장 앞선 파일을 쓴다. 충돌이 있으면 두 번째 시도를 하지 않고 객체를 더 쓰지 않는다.

[4.4] 충돌이 없으면 두 번째 시도를 한다. 두 번째 시도의 기준 판은 그 순간의 현재 메타데이터 파일이고 시도 번호는 2 다. 나머지는 1단계 4.25~4.30 과 같으므로 새 스냅숏의 부모 스냅숏은 두 번째 시도의 기준 판의 현재 스냅숏이고 순서 번호는 그 기준 판의 마지막 순서 번호에 1 을 더한 값이다. 두 번째 시도의 교체는 언제나 성공한다. COMMIT 은 트랜잭션을 끝내고 `<트랜잭션> COMMIT seq=<순서 번호> <메타데이터 파일>` 을 출력한다. 순서 번호와 메타데이터 파일은 두 번째 시도의 것이다.

[4.5] 두 번째 시도는 첫 시도가 쓴 1단계 4.26 의 새 매니페스트를 다시 쓰지 않고 그대로 목록 맨 앞에 넣는다. 두 번째 시도는 이 매니페스트를 목록에 두 번째 시도의 새 스냅숏 순서 번호로 적는다. 그 매니페스트의 ADDED 항목 가운데 APPEND 로 쓴 파일의 항목은 이 번호를 이어받는다. COMPACT 로 쓴 새 파일의 항목은 4.15 대로 첫 시도에 적은 시작 스냅숏의 순서 번호를 그대로 지닌다. 지울 파일이 든 매니페스트는 두 번째 시도의 부모 스냅숏의 목록에서 찾아 1단계 4.27 대로 새로 다시 쓴다. 첫 시도에서 다시 쓴 매니페스트는 두 번째 시도의 목록에 넣지 않는다.

[4.6] 프로그램은 한 줄을 다 처리한 뒤에 다음 줄을 읽으므로 두 번째 시도가 객체를 쓰는 동안 다른 커밋이 끼어들지 않는다. 한 COMMIT 의 시도는 많아야 둘이다.

### 충돌

[4.7] 판정에 사용하는 스냅숏은 4.3 이 정한 스냅숏이다. 충돌 판정은 트랜잭션이 지울 파일로 적은 파일이 판정에 사용하는 스냅숏에 모두 살아 있는지 본다. 지울 파일은 REMOVE 로 적은 파일과 COMPACT 로 바꿀 파일이다. 판정에 사용하는 스냅숏에 살아 있지 않은 지울 파일이 충돌을 일으킨 파일이고 그런 파일이 하나라도 있으면 충돌이다.

[4.8] 더할 파일은 충돌을 일으키지 않으므로 APPEND 만 한 트랜잭션은 첫 시도의 교체에 실패해도 두 번째 시도에서 언제나 커밋한다.

### ABORT

[4.9] `ABORT <트랜잭션>` 은 트랜잭션을 끝내고 `<트랜잭션> ABORT` 를 출력한다. 그 트랜잭션이 APPEND 와 COMPACT 로 쓴 데이터 파일은 객체 저장소에 남는다.

[4.10] 트랜잭션은 교체에 성공한 COMMIT, CONFLICT 를 낸 COMMIT, ABORT 가운데 하나로 끝난다.

### COMPACT

[4.11] `COMPACT <트랜잭션> <새 파일> <바꿀 파일> …` 은 바꿀 파일들의 행을 옮겨 담은 데이터 파일을 새 파일 이름으로 객체 저장소에 바로 쓴다. 새 파일은 트랜잭션이 더할 파일이 되고 바꿀 파일들은 트랜잭션이 지울 파일이 된다. 바꿀 파일은 하나 이상이고 서로 달라야 한다.

[4.12] 바꿀 파일은 모두 그 트랜잭션의 시작 스냅숏에 살아 있는 데이터 파일이어야 한다. 그 트랜잭션이 이미 지울 파일로 적은 파일은 바꿀 파일이 될 수 없다. 바꿀 파일들의 파티션 값은 모두 같아야 하고 그 값이 새 파일의 파티션 값이다.

[4.13] 새 파일의 행은 바꿀 파일들을 인자에 적은 순서대로 늘어놓고 각 파일의 행을 위치 순서대로 이어 붙인 것이다. 새 파일의 행 수와 열의 하한·상한은 이 행들로 정한다.

[4.14] COMPACT 는 `<트랜잭션> COMPACT <새 파일> <파티션 값> rows=<행 수>` 를 출력한다.

[4.15] 새 파일은 APPEND 로 쓴 파일과 함께 1단계 4.26 의 새 매니페스트에 트랜잭션에 적은 순서대로 들어간다. 새 파일을 가리키는 ADDED 항목은 순서 번호를 이어받지 않고 그 트랜잭션의 시작 스냅숏의 순서 번호를 직접 적는다. 두 번째 시도에서 새 스냅숏의 순서 번호가 바뀌어도 이 번호는 바뀌지 않는다.

### 사용 중인 객체

[4.16] 아래 객체를 사용 중인 객체라 한다. 현재 메타데이터 파일과 그 파일의 이전 메타데이터 파일 기록에 든 메타데이터 파일은 사용 중이다. 현재 메타데이터 파일의 스냅숏 목록에 있는 스냅숏마다 그 스냅숏의 매니페스트 목록, 그 목록에 든 매니페스트, 그 매니페스트들의 살아 있는 항목이 가리키는 파일은 사용 중이다. 활동 중인 트랜잭션이 APPEND 와 COMPACT 로 쓴 데이터 파일도 사용 중이다. 나머지 객체는 사용 중이 아니다. 이전 메타데이터 파일이 담은 스냅숏은 따라가지 않는다.

### EXPIRE

[4.17] `EXPIRE <스냅숏> …` 은 인자로 적은 스냅숏들을 만료한다. 스냅숏은 하나 이상이고 서로 달라야 한다. 각 스냅숏은 현재 메타데이터 파일의 스냅숏 목록에 있어야 한다. 현재 스냅숏과 활동 중인 트랜잭션의 시작 스냅숏은 만료할 수 없다.

[4.18] EXPIRE 는 현재 메타데이터 파일에서 만료할 스냅숏을 뺀 새 메타데이터 파일을 1단계 4.13 의 다음 이름으로 쓴다. 새 메타데이터 파일의 현재 스냅숏과 마지막 순서 번호는 그대로이고 남은 스냅숏의 내용도 그대로다. 이전 메타데이터 파일 기록은 원래 기록 끝에 그때까지의 현재 메타데이터 파일 이름을 더한 것이다. EXPIRE 는 교체로 포인터를 새 메타데이터 파일로 바꾼다. 교체의 기대한 이름은 그때까지의 현재 메타데이터 파일 이름이므로 이 교체는 언제나 성공한다.

[4.19] 교체한 뒤 EXPIRE 는 교체 전에는 사용 중이었지만 교체 뒤에는 사용 중이 아닌 객체를 객체 저장소에서 모두 지운다. 이 객체는 만료한 스냅숏만 사용하던 매니페스트 목록, 매니페스트, 데이터 파일이다. 메타데이터 파일은 이전 메타데이터 파일 기록에 남으므로 EXPIRE 가 지우지 않는다. 교체 전에도 사용 중이 아니던 객체는 EXPIRE 가 지우지 않는다.

[4.20] EXPIRE 는 `EXPIRE <새 메타데이터 파일> lists=<지운 매니페스트 목록 수> manifests=<지운 매니페스트 수> files=<지운 데이터 파일 수>` 를 출력한다.

[4.21] 만료한 스냅숏은 현재 메타데이터 파일의 스냅숏 목록에 없으므로 그 스냅숏을 가리키는 SCAN 의 AT 과 MANIFESTS 는 1단계 5.5 의 `ERR NO_SNAPSHOT` 을 낸다. EXPIRE 가 지운 매니페스트는 1단계 4.33 의 매니페스트가 아니므로 ENTRIES 는 `ERR NO_MANIFEST` 를 낸다. EXPIRE 가 데이터 파일을 지워도 남은 매니페스트에서 그 파일을 가리키는 항목은 그대로 남고 ENTRIES 는 그 항목도 1단계 4.33 대로 보여 준다. 남은 스냅숏의 부모 스냅숏이 만료되어도 SNAPSHOTS 는 그 부모 스냅숏 ID 를 그대로 쓴다.

[4.22] EXPIRE 도 포인터를 바꾸므로 EXPIRE 전에 BEGIN 한 트랜잭션은 다른 커밋이 없었어도 기준 판이 현재 메타데이터 파일이 아니다. 그런 트랜잭션의 COMMIT 은 4.2 대로 RETRY 를 낸다.

### ORPHANS

[4.23] `ORPHANS` 는 객체 저장소에서 사용 중이 아닌 객체를 모두 지우고 `ORPHANS metadata=<지운 메타데이터 파일 수> lists=<지운 매니페스트 목록 수> manifests=<지운 매니페스트 수> files=<지운 데이터 파일 수>` 를 출력한다.

[4.24] ORPHANS 는 메타데이터 파일을 쓰지 않고 포인터를 바꾸지 않으므로 활동 중인 트랜잭션의 COMMIT 에 RETRY 를 일으키지 않는다.

[4.25] 4.16 에 따라 교체에 실패한 시도가 쓴 매니페스트, 매니페스트 목록, 메타데이터 파일은 사용 중이 아니다. 끝난 트랜잭션이 쓰고 커밋하지 못한 데이터 파일도 사용 중이 아니다. 단, 교체에 실패한 첫 시도의 새 매니페스트를 두 번째 시도가 목록에 넣었으면 그 매니페스트는 사용 중이다.

### 이름

[4.26] 1단계 4.13 의 이름 규칙은 그대로다. 메타데이터 파일 번호는 교체에 실패한 시도와 EXPIRE 가 쓴 메타데이터 파일까지 쓴 순서대로 센다. 매니페스트 차례 번호는 트랜잭션이 첫 시도와 두 번째 시도에서 쓴 매니페스트를 이어서 센다. ORPHANS 와 EXPIRE 가 지운 객체의 이름도 다시 사용하지 않는다. 지운 데이터 파일의 이름도 1단계 5.5 의 쓴 적이 있는 이름이다.

### 뒤 단계에서 뜻이 생기는 입력

[4.27] 명령어가 `POSDEL` 이나 `EQDEL` 인 줄은 이 문서가 출력을 정하지 않는다. 두 명령은 3단계에서 뜻이 생긴다. 이 줄이 나온 뒤의 출력도 이 문서는 정하지 않는다.

## 5. 오류 규칙

[5.1] 1단계의 오류 규칙을 그대로 따른다. 1단계 5.2 의 명령 목록에 ABORT, COMPACT, EXPIRE, ORPHANS 를 더한다.

[5.2] 인자 수나 형식이 아래와 맞지 않으면 `ERR BAD_ARGUMENT` 를 출력한다. ABORT 의 인자는 트랜잭션 이름 하나다. COMPACT 의 인자는 트랜잭션 이름, 새 파일 이름, 바꿀 파일 이름 하나 이상이고 바꿀 파일 이름은 서로 달라야 한다. EXPIRE 의 인자는 이름 하나 이상이고 서로 달라야 한다. ORPHANS 는 인자가 없다.

[5.3] ABORT 와 COMPACT 는 1단계 5.5 의 APPEND 처럼 트랜잭션을 확인해 `<트랜잭션> ERR NO_TX` 나 `<트랜잭션> ERR TX_ENDED` 를 출력한다. COMPACT 는 이어서 새 파일 이름을 이미 데이터 파일 이름으로 쓴 적이 있으면 `<트랜잭션> ERR FILE_EXISTS <새 파일>` 을 출력한다. 그다음 바꿀 파일을 인자 순서대로 보고 4.12 의 앞 두 문장을 어긴 첫 파일에 `<트랜잭션> ERR NOT_LIVE <파일>` 을 출력한다. 마지막으로 바꿀 파일들의 파티션 값이 모두 같지는 않으면 `<트랜잭션> ERR MIXED_PARTITION` 을 출력한다.

[5.4] EXPIRE 는 인자를 앞에서부터 차례로 본다. 인자마다 현재 메타데이터 파일의 스냅숏 목록에 없으면 `ERR NO_SNAPSHOT <스냅숏>` 을, 현재 스냅숏이나 활동 중인 트랜잭션의 시작 스냅숏이면 `ERR IN_USE <스냅숏>` 을 출력한다. 처음 걸린 인자의 오류 하나만 출력하고 아무 스냅숏도 만료하지 않는다.

[5.5] 이 단계에서 새로 생긴 오류 줄은 아래와 같다.

| 오류 줄 | 뜻 |
|---|---|
| `ERR IN_USE <스냅숏>` | 현재 스냅숏이거나 활동 중인 트랜잭션의 시작 스냅숏이라 만료할 수 없음 |

## 6. 예시

[6.1] 예시를 읽는 방법은 1단계 6.1 과 같다.

[6.2] 같은 판에서 시작한 두 트랜잭션이다. b 와 c 는 모두 meta-2 를 기준 판으로 시작한다. b 가 먼저 커밋해 포인터가 meta-3 으로 바뀌므로 c 의 첫 시도는 c-m1, c-m2, snap-c-1, meta-4 를 쓴 뒤 교체에 실패한다. c 가 지울 f1 은 현재 스냅숏 b 에 살아 있으므로 충돌이 없다. 두 번째 시도는 c-m1 을 다시 쓰지 않고 목록에 넣으며 b 의 목록에서 f1 이 든 a-m1 을 다시 찾아 c-m3 으로 다시 쓴다. 새 스냅숏 c 의 부모는 b 이고 순서 번호는 3 이며 c-m1 의 항목도 순서 번호 3 을 이어받는다. 첫 시도가 쓴 c-m2, snap-c-1, meta-4 는 사용 중이 아니므로 ORPHANS 가 지운다. f1 은 스냅숏 a 와 b 에 살아 있으므로 사용 중이다.

```
> CREATE id:long name:string PARTITION bucket[4](id)
CREATE meta-1
> BEGIN a
a BEGIN - meta-1
> APPEND a f1 1,ann 5,bob
a APPEND f1 {id_bucket=1} rows=2
> COMMIT a
a COMMIT seq=1 meta-2
> BEGIN b
b BEGIN a meta-2
> BEGIN c
c BEGIN a meta-2
> APPEND b f2 9,cat
b APPEND f2 {id_bucket=1} rows=1
> APPEND c f3 13,dan
c APPEND f3 {id_bucket=3} rows=1
> REMOVE c f1
c REMOVE f1
> COMMIT b
b COMMIT seq=2 meta-3
> COMMIT c
c RETRY meta-4
c COMMIT seq=3 meta-5
> SNAPSHOTS
SNAPSHOTS meta-5 3
a seq=1 parent=- list=snap-a-1
b seq=2 parent=a list=snap-b-1
c seq=3 parent=b list=snap-c-2
> MANIFESTS c
MANIFESTS c 3
c-m1 data seq=3 added=1 existing=0 deleted=0 id_bucket=3..3
b-m1 data seq=2 added=1 existing=0 deleted=0 id_bucket=1..1
c-m3 data seq=3 added=0 existing=0 deleted=1 id_bucket=1..1
> ENTRIES c-m1
ENTRIES c-m1 1
ADDED f3 snapshot=c seq=3 {id_bucket=3} rows=1
> ORPHANS
ORPHANS metadata=1 lists=1 manifests=1 files=0
> LS
LS 14
a-m1
b-m1
c-m1
c-m3
f1
f2
f3
meta-1
meta-2
meta-3
meta-5
snap-a-1
snap-b-1
snap-c-2
```

[6.3] 충돌과 압축이다. c 와 d 는 모두 b 에서 시작한다. d 가 f2 를 먼저 지운다. 그래서 f1, f2, f3 을 바꾸려던 c 는 RETRY 를 낸 뒤 충돌 판정에서 f2 가 현재 스냅숏 d 에 살아 있지 않아 CONFLICT 를 낸다. c 의 첫 시도가 쓴 c-m1, c-m2, c-m3, snap-c-1, meta-5 와 COMPACT 로 쓴 g1 은 사용 중이 아니므로 ORPHANS 가 지운다. e 는 남은 f1 과 f3 을 g2 로 바꾼다. g2 의 항목은 e 의 시작 스냅숏 d 의 순서 번호 3 을 직접 적으므로 매니페스트 목록에 적힌 순서 번호 4 와 다르다. g2 는 id 범위가 1..9 라서 `id=5` 로 계획되지만 id 5 인 행은 d 가 f2 와 함께 지웠다.

```
> CREATE id:long name:string PARTITION bucket[4](id)
CREATE meta-1
> BEGIN a
a BEGIN - meta-1
> APPEND a f1 1,ann
a APPEND f1 {id_bucket=1} rows=1
> APPEND a f2 5,bob
a APPEND f2 {id_bucket=1} rows=1
> COMMIT a
a COMMIT seq=1 meta-2
> BEGIN b
b BEGIN a meta-2
> APPEND b f3 9,cat
b APPEND f3 {id_bucket=1} rows=1
> COMMIT b
b COMMIT seq=2 meta-3
> BEGIN c
c BEGIN b meta-3
> BEGIN d
d BEGIN b meta-3
> COMPACT c g1 f1 f2 f3
c COMPACT g1 {id_bucket=1} rows=3
> REMOVE d f2
d REMOVE f2
> COMMIT d
d COMMIT seq=3 meta-4
> COMMIT c
c RETRY meta-5
c CONFLICT f2
> ORPHANS
ORPHANS metadata=1 lists=1 manifests=3 files=1
> BEGIN e
e BEGIN d meta-4
> COMPACT e g2 f1 f3
e COMPACT g2 {id_bucket=1} rows=2
> COMMIT e
e COMMIT seq=4 meta-6
> MANIFESTS e
MANIFESTS e 3
e-m1 data seq=4 added=1 existing=0 deleted=0 id_bucket=1..1
e-m2 data seq=4 added=0 existing=0 deleted=1 id_bucket=1..1
e-m3 data seq=4 added=0 existing=0 deleted=1 id_bucket=1..1
> ENTRIES e-m1
ENTRIES e-m1 1
ADDED g2 snapshot=e seq=3 {id_bucket=1} rows=2
> SCAN WHERE id=5
SCAN e manifests=3/3 files=1 rows=0
g2 rows=0
```

[6.4] 만료다. c 는 f1 을 지운다. c 는 현재 스냅숏이고 활동 중인 w 의 시작 스냅숏이라 만료할 수 없다. b 를 만료하면 b 만 사용하던 snap-b-1 하나만 지운다. a-m1 과 f1 은 아직 a 가 사용한다. 이어서 a 를 만료하면 snap-a-1, a-m1, f1 을 지운다. f1 을 DELETED 로 적은 c-m1 은 c 의 목록에 남고 ENTRIES 는 지운 f1 의 항목도 보여 준다. 만료한 a 로는 시간 여행을 할 수 없다. w 가 쓴 f3 은 w 가 활동 중이라 사용 중인 객체다. w 의 기준 판 meta-4 는 EXPIRE 로 현재 메타데이터 파일이 아니게 되었으므로 w 의 COMMIT 은 RETRY 를 낸다. 두 번째 시도에서 c-m1 은 살아 있는 항목이 없어 목록에서 빠진다.

```
> CREATE id:long name:string
CREATE meta-1
> BEGIN a
a BEGIN - meta-1
> APPEND a f1 1,ann
a APPEND f1 {} rows=1
> COMMIT a
a COMMIT seq=1 meta-2
> BEGIN b
b BEGIN a meta-2
> APPEND b f2 2,bob
b APPEND f2 {} rows=1
> COMMIT b
b COMMIT seq=2 meta-3
> BEGIN c
c BEGIN b meta-3
> REMOVE c f1
c REMOVE f1
> COMMIT c
c COMMIT seq=3 meta-4
> BEGIN w
w BEGIN c meta-4
> APPEND w f3 3,cat
w APPEND f3 {} rows=1
> EXPIRE c
ERR IN_USE c
> EXPIRE b
EXPIRE meta-5 lists=1 manifests=0 files=0
> EXPIRE a
EXPIRE meta-6 lists=1 manifests=1 files=1
> SCAN AT a
ERR NO_SNAPSHOT a
> SNAPSHOTS
SNAPSHOTS meta-6 1
c seq=3 parent=b list=snap-c-1
> LS
LS 11
b-m1
c-m1
f2
f3
meta-1
meta-2
meta-3
meta-4
meta-5
meta-6
snap-c-1
> ENTRIES c-m1
ENTRIES c-m1 1
DELETED f1 snapshot=c seq=1 {} rows=1
> COMMIT w
w RETRY meta-7
w COMMIT seq=4 meta-8
> MANIFESTS w
MANIFESTS w 2
w-m1 data seq=4 added=1 existing=0 deleted=0
b-m1 data seq=2 added=1 existing=0 deleted=0
> ORPHANS
ORPHANS metadata=1 lists=1 manifests=0 files=0
```

[6.5] 오류다. 끝난 트랜잭션은 ABORT 할 수 없다. 바꿀 파일 이름이 겹치면 형식에 맞지 않는다. 바꿀 파일은 시작 스냅숏에 살아 있어야 하고 파티션 값이 같아야 하며 이미 지울 파일로 적은 f3 은 바꿀 수 없다. 스냅숏 a 는 현재 스냅숏이라 만료할 수 없다. b 를 ABORT 하면 b 가 COMPACT 로 쓴 g1 은 사용 중이 아닌 데이터 파일이 된다.

```
> CREATE id:long name:string PARTITION bucket[2](id)
CREATE meta-1
> BEGIN a
a BEGIN - meta-1
> APPEND a f1 1,ann
a APPEND f1 {id_bucket=1} rows=1
> APPEND a f2 2,bob
a APPEND f2 {id_bucket=0} rows=1
> APPEND a f3 3,cat
a APPEND f3 {id_bucket=1} rows=1
> COMMIT a
a COMMIT seq=1 meta-2
> ABORT a
a ERR TX_ENDED
> BEGIN b
b BEGIN a meta-2
> COMPACT b g1 f1 f1
ERR BAD_ARGUMENT
> COMPACT b f2 f1
b ERR FILE_EXISTS f2
> COMPACT b g1 f1 f9
b ERR NOT_LIVE f9
> COMPACT b g1 f1 f2
b ERR MIXED_PARTITION
> REMOVE b f3
b REMOVE f3
> COMPACT b g1 f1 f3
b ERR NOT_LIVE f3
> COMPACT b g1 f1
b COMPACT g1 {id_bucket=1} rows=1
> EXPIRE a
ERR IN_USE a
> EXPIRE x
ERR NO_SNAPSHOT x
> EXPIRE a a
ERR BAD_ARGUMENT
> ORPHANS now
ERR BAD_ARGUMENT
> ABORT b
b ABORT
> ORPHANS
ORPHANS metadata=0 lists=0 manifests=0 files=1
```

## 7. 검사 방식

[7.1] 1단계 7.1~7.5, 7.7, 7.10 을 그대로 따른다.

[7.2] 대규모 테스트는 `CREATE id:long name:string PARTITION bucket[8](id)` 로 만든 테이블에서 실행한다. 트랜잭션 6,000개 안팎이 실행되고 같은 때에 활동 중인 트랜잭션은 1~4개다. 트랜잭션의 절반가량은 데이터 파일 30개 안팎을 APPEND 하고 나머지는 REMOVE 나 COMPACT 로 파일 수십 개를 지우거나 바꾸며 300개 안팎은 ABORT 로 끝난다. COMMIT 의 3분의 1 안팎이 RETRY 를 내고 그 가운데 10분의 1 안팎이 CONFLICT 로 끝난다. EXPIRE 는 COMMIT 30번마다 실행해 오래된 스냅숏을 만료하고 스냅숏이 200개 안팎 남게 한다. ORPHANS 는 COMMIT 60번마다 실행하고 SCAN 은 5,000번 안팎 섞는다. 제한 시간은 15초다. EXPIRE 와 ORPHANS 를 실행할 때마다 남은 스냅숏마다 매니페스트 목록의 매니페스트 항목을 따로 모두 훑어 사용 중인 객체를 다시 모으는 풀이는 이 제한 시간을 넘기도록 규모를 정했다. REMOVE 와 COMPACT 를 처리할 때마다 시작 스냅숏의 살아 있는 데이터 파일을 모두 다시 모으는 풀이도 이 제한 시간을 넘긴다.

[7.3] 2단계 검사 입력에는 4.27 의 줄이 나오지 않는다.

[7.4] 3단계를 검사할 때도 2단계 테스트를 다시 실행한다.
