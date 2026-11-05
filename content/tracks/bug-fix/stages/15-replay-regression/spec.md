# 검색 반영 명단에 되살아난 옛 상품 내용

[1] 쇼핑 카탈로그의 전송 작업을 고친다. 운영 원천 LIVE와 실험 원천 TRIAL의 변경 기록을 회차별로 병합해 검색 서비스에 보낼 명단을 만든다. 이 프로그램은 명단을 표준 출력에 쓸 뿐 실제 검색 서비스에 접속하지 않는다. 대상 저장소가 오래된 쓰기를 거절하더라도 잘못 만든 명단은 오류다.

## 명령과 입출력

[2] 프로그램은 표준 입력에서 명령을 한 줄씩 EOF까지 읽는다. 빈 줄은 무시하며 토큰은 공백으로 구분한다. 명령은 CHANGE와 REPLAY 두 가지다. 한 실행에는 명령이 최대 2,000개 들어온다. 입력은 아래 형식을 지키며 오류 입력의 처리는 요구하지 않는다.

[3] `CHANGE <원천> <회차> <순번> PUT <키> <내용>`은 상품 내용을 기록한다. `CHANGE <원천> <회차> <순번> DELETE <키>`는 삭제를 기록한다. 원천은 LIVE 또는 TRIAL이다. 회차는 1 이상 1,000,000 이하인 정수이며 순번은 1 이상 1,000,000,000,000 이하인 정수다. 키와 내용은 영문 소문자·숫자·하이픈으로 이루어진 길이 1~40의 문자열이다. 같은 원천·회차·순번 조합은 중복되지 않는다. CHANGE는 출력하지 않는다.

[4] 회차는 변경을 묶는 배치 번호이고 순번은 그 회차 안의 기록 순서다. CHANGE의 입력 순서는 회차·순번 순서와 다를 수 있다. REPLAY는 명령을 읽는 시점까지 입력된 기록만 사용한다. 두 원천의 이력은 서로 독립적이다.

[5] `REPLAY <회차>`는 해당 회차의 전송 명단을 출력한다. 해당 회차에 CHANGE 기록이 하나 이상 있는 키만 후보가 된다. 후보 키에 그보다 큰 회차의 기록이 어느 원천에든 하나라도 있으면 그 키를 명단에서 제외한다. 나중 기록이 PUT인지 DELETE인지, 병합 결과에서 어느 원천이 우선하는지는 이 제외 규칙에 영향을 주지 않는다.

## 병합 규칙

[6] 명단에 남은 키는 요청한 회차까지의 기록으로 병합한다. 원천마다 해당 키의 기록 중 회차가 가장 큰 것을 고른다. 같은 회차에서는 순번이 가장 큰 것을 고른다. 요청한 회차보다 큰 기록을 병합에 사용하지 않는다. 순번은 서로 다른 회차나 원천 사이의 우선순위가 아니다.

[7] TRIAL에 선택된 기록이 있으면 그 기록이 병합 결과다. TRIAL에 기록이 없으면 LIVE의 선택된 기록이 결과다. 선택된 PUT은 내용 전체를 대체하며 선택된 DELETE는 상품을 숨긴다. TRIAL의 DELETE도 LIVE로 되돌아가라는 뜻이 아니다. 명단의 키마다 PUT 또는 DELETE를 정확히 한 번 출력한다.

[8] 출력의 첫 줄은 `BATCH <요청 회차> <키 수>`다. 이어서 키의 사전순으로 명단을 출력한다. PUT의 형식은 `PUT <키> <내용> <선택된 원천> <기록 회차> <순번>`이며 DELETE의 형식은 `DELETE <키> <선택된 원천> <기록 회차> <순번>`이다. 마지막 줄은 `END <요청 회차>`다. 삭제도 키 수에 포함한다. 명단이 비어 있으면 BATCH와 END 두 줄만 출력한다.

[9] REPLAY는 이력이나 별도의 전송 상태를 바꾸지 않는다. 같은 이력으로 같은 회차를 여러 번 실행하면 같은 명단이 나온다. 실행한 적이 없는 회차도 요청할 수 있으며, 그 회차의 변경이 없으면 빈 명단이 나온다. 다른 회차를 실행했다는 사실만으로 후보 키를 추가하거나 제외하지 않는다.

## 버그 신고

[10] 검색 반영용 명단을 검수하다가 마지막 명단에 예전 컵과 머그 내용이 다시 들어온 것을 봤다. 중간 명단까지는 검수 결과와 맞는다. 램프 항목은 정상으로 보인다. 아래는 내가 검수에 쓴 기록과 실행 순서다.

```text
CHANGE LIVE 12 10 PUT mug mug-v1
CHANGE LIVE 12 11 PUT cup cup-v1
CHANGE LIVE 12 12 PUT lamp lamp-v1
CHANGE TRIAL 12 1 PUT lamp lamp-preview
REPLAY 12
CHANGE LIVE 13 1 PUT mug mug-v2
CHANGE LIVE 13 2 PUT tray tray-v1
CHANGE TRIAL 13 1 DELETE cup
REPLAY 13
REPLAY 12
```

[11] 기대한 전체 출력은 다음과 같다.

```text
BATCH 12 3
PUT cup cup-v1 LIVE 12 11
PUT lamp lamp-preview TRIAL 12 1
PUT mug mug-v1 LIVE 12 10
END 12
BATCH 13 3
DELETE cup TRIAL 13 1
PUT mug mug-v2 LIVE 13 1
PUT tray tray-v1 LIVE 13 2
END 13
BATCH 12 1
PUT lamp lamp-preview TRIAL 12 1
END 12
```

[12] 현재 프로그램의 실제 전체 출력은 다음과 같다. 명단을 그대로 전송할 수 있도록 프로그램을 고쳐야 한다.

```text
BATCH 12 3
PUT cup cup-v1 LIVE 12 11
PUT lamp lamp-preview TRIAL 12 1
PUT mug mug-v1 LIVE 12 10
END 12
BATCH 13 3
DELETE cup TRIAL 13 1
PUT mug mug-v2 LIVE 13 1
PUT tray tray-v1 LIVE 13 2
END 13
BATCH 12 3
PUT cup cup-v1 LIVE 12 11
PUT lamp lamp-preview TRIAL 12 1
PUT mug mug-v1 LIVE 12 10
END 12
```
