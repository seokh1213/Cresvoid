# 3단계: 소진율 경보

## 1. 개요

[1.1] 3단계는 SLO 와 소진율 경보를 더한다. SLO 는 목표 비율과 함께 전체 요청 수와 나쁜 요청 수를 세는 카운터를 정한다. BURN 은 창 하나에서 SLO 의 소진율을 계산한다. ALERT 는 긴 창과 짧은 창의 소진율이 함께 기준을 넘을 때 켜지는 경보를 정한다. EVAL 은 정해진 시각마다 경보를 평가해 경보가 켜지고 꺼진 시각을 출력한다.

[1.2] 1단계와 2단계의 규칙은 이 문서가 넓히는 곳 말고는 그대로다. 두 단계가 정한 출력은 바뀌지 않는다. 이 문서는 새로 생기는 규칙만 설명한다. 1단계 5.2 와 2단계 5.1 이 출력을 정하지 않은 SLO, BURN, ALERT, EVAL 의 출력을 이 문서가 정한다. 2단계 5.1 이 출력을 정하지 않은 나머지 줄의 출력도 1단계, 2단계, 이 문서의 규칙으로 정한다.

| 명령 | 하는 일 | 정상 출력 |
|---|---|---|
| `SLO <SLO> <목표> <전체 지표> <나쁜 지표> [<레이블 쌍> ...]` | SLO 를 정한다 | 없음 |
| `BURN <시각> <창> <SLO>` | 창 하나의 소진율을 구한다 | 1줄 |
| `ALERT <경보> <SLO> <긴 창> <짧은 창> <기준>` | 소진율 경보를 정한다 | 없음 |
| `EVAL <시작> <끝> <간격>` | 경보를 평가해 켜지고 꺼진 시각을 보여 준다 | 상태가 바뀔 때마다 1줄, 마지막에 1줄 |

## 2. 배경 개념

[2.1] 서비스 수준 목표(SLO)는 일정 기간 전체 요청 가운데 좋은 요청의 비율이 이르러야 할 목표치다. 좋은 요청은 실패하지 않은 요청일 수도 있고 기준 시간 안에 응답한 요청일 수도 있다. 30일 동안 99.9% 가 목표이면 나머지 0.1% 가 오류 예산, 곧 그 기간에 나빠도 되는 요청의 몫이다.

[2.2] 소진율은 SLO 에 견주어 오류 예산을 얼마나 빨리 쓰는지를 나타낸다. 어느 창에서 잰 오류율, 곧 나쁜 요청의 비율을 오류 예산 비율로 나눈 값이다. 99.9% SLO 에서 오류율 0.1% 가 이어지면 소진율은 1 이고 예산은 기간이 끝날 때 정확히 바닥난다. 요청이 고르게 들어오면 어떤 창 동안 쓴 예산의 몫은 소진율에 창 길이를 곱하고 SLO 기간으로 나눈 값이다. 30일 SLO 에서 소진율 10 이 이어지면 3일, 모든 요청이 실패하는 소진율 1,000 이면 약 43분 만에 예산이 바닥난다.

[2.3] 경보는 예산을 크게 쓰는 사건만 알려야 한다. 요청이 고르게 들어오면 최근 1시간 소진율이 14.4 를 넘을 때 알리는 것은 그 1시간에 30일 예산의 2% 를 넘게 썼을 때 알리는 것과 같다. 기준을 높이면 헛경보가 줄지만 기준보다 낮은 소진율은 아무리 오래 이어져도 알리지 못한다. 그래서 창이 더 길고 기준이 더 낮은 경보를 함께 둔다.

[2.4] 긴 창 하나로만 판정하는 경보는 늦게 꺼진다. 오류가 멈춘 뒤에도 창 안에 오류가 난 구간이 남아 있는 동안은 그 창의 오류율이 기준을 넘을 수 있기 때문이다. Google SRE Workbook 은 긴 창과 그 12분의 1 길이인 짧은 창이 함께 같은 기준을 넘을 때만 경보를 울리라고 권한다. 긴 창은 예산을 충분히 썼는지를, 짧은 창은 지금도 쓰고 있는지를 확인한다. 오류가 멈추고 짧은 창 길이쯤 지나면 짧은 창의 오류율이 기준 아래로 떨어져 경보가 꺼진다. SLO 가 99.9% 일 때 권하는 시작값은 1시간·5분 창에서 소진율 14.4, 6시간·30분 창에서 6 을 넘으면 호출하고 3일·6시간 창에서 1 을 넘으면 티켓을 남기는 것이다.

[2.5] SRE Workbook 은 `sum(rate(slo_errors[10m])) by (job) / sum(rate(slo_requests[10m])) by (job)` 같은 기록 규칙으로 창마다 오류율을 계산해 두고 경보 규칙이 그 값을 기준 × 오류 예산 비율과 비교하게 한다. Prometheus 는 규칙을 정해진 평가 간격마다 평가하므로 경보는 평가 시각에만 켜지거나 꺼진다. Prometheus 의 경보 규칙에는 조건이 일정 시간 이어져야 울리게 하는 for 절이 있다. SRE Workbook 은 SLO 경보에 이런 지속 시간 조건을 권하지 않는다. 오류율이 잠깐이라도 기준 아래로 내려가면 지속 시간을 처음부터 다시 재기 때문이다. 이 트랙도 지속 시간 조건을 쓰지 않는다.

[2.6] 오류를 세는 카운터 시계열은 첫 오류가 날 때 처음 생기는 경우가 많다. 그 전에는 오류율의 분자가 될 시계열이 없어서 Prometheus 의 나눗셈 결과도 비고 경보는 울리지 않는다. Prometheus 문서는 생길 줄 미리 아는 시계열을 0 값으로 내보내 두라고 권한다.

[2.7] 이 트랙은 평가 시각을 평가 간격의 배수로 맞춘다. 그래서 간격이 같으면 평가 구간을 어디서 시작하든 같은 시각에 평가한다. 창은 평가 시각에서 끝나고 1단계의 범위처럼 평가 시각의 표본을 포함한다.

## 3. 입출력 규칙

[3.1] 1단계와 2단계의 입출력 규칙을 그대로 따른다.

[3.2] SLO 이름과 경보 이름은 `^[A-Za-z0-9._:-]{1,64}$` 에 맞는 문자열이다. 대소문자를 구분한다. SLO 이름과 경보 이름은 따로 관리하므로 같은 문자열을 SLO 이름과 경보 이름에 함께 쓸 수 있다.

[3.3] 목표 인자는 `^(0|[1-9][0-9]?)(\.[0-9]{1,3})?$` 에 맞고 값이 0 보다 크고 100 보다 작은 수다. 단위는 퍼센트다. `99.9`, `99`, `99.95`, `0.5` 는 목표 인자이고 `100`, `0`, `0.000`, `99.9999`, `099` 는 목표 인자가 아니다.

[3.4] 기준 인자는 `^(0|[1-9][0-9]{0,5})(\.[0-9]{1,3})?$` 에 맞고 값이 0 보다 크고 100,000 이하인 수다. `14.4`, `6`, `1`, `0.5` 는 기준 인자이고 `0`, `6.0001`, `.5` 는 기준 인자가 아니다.

[3.5] 창 인자와 간격 인자는 1 이상 1,000,000,000 이하인 정수이고 단위는 초다. 시작 인자와 끝 인자는 1단계 3.12 의 시각 인자다.

## 4. 명령 규칙

### SLO

[4.1] `SLO <SLO> <목표> <전체 지표> <나쁜 지표> [<레이블 쌍> ...]` 은 SLO 를 정한다. 전체 지표는 모든 요청 수를 세는 counter 지표이고 나쁜 지표는 나쁜 요청 수를 세는 counter 지표다. 레이블 쌍은 두 지표에 똑같이 붙는다. 전체 선택자는 전체 지표 이름 뒤에 그 레이블 쌍을 붙인 선택자이고 나쁜 선택자는 나쁜 지표 이름 뒤에 같은 레이블 쌍을 붙인 선택자다. 두 지표 이름은 같아도 된다. 성공하면 아무것도 출력하지 않는다.

[4.2] 이미 정한 SLO 이름이면 `ERR EXISTS` 를 출력한다. 두 지표 가운데 정하지 않은 지표가 있으면 `ERR NO_METRIC` 을, 종류가 counter 가 아닌 지표가 있으면 `ERR WRONG_TYPE` 을 출력한다. 정한 SLO 는 바꾸거나 지울 수 없다.

[4.3] SLO 는 선택자만 기억한다. 선택자가 고르는 시계열은 BURN 이나 EVAL 을 처리할 때 정해지므로 SLO 를 정한 뒤에 생긴 시계열도 고른다.

[4.4] 오류 예산 비율은 (100 − 목표) ÷ 100 이다. 목표가 99.9 이면 오류 예산 비율은 0.001 이다.

### 소진율

[4.5] 시각 t 에서 창 w 의 소진율은 아래처럼 구한다. 전체 증가량 T 는 전체 선택자로 1단계 INCREASE 를 시각 t, 범위 w 로 실행할 때 출력할 값이다. 나쁜 증가량 B 는 나쁜 선택자로 같은 시각과 범위의 INCREASE 를 실행할 때 출력할 값이다.

[4.6] T 나 B 가 NONE 이거나 T 가 0 이면 소진율이 없다. 그 밖에는 (B ÷ T) ÷ 오류 예산 비율의 정확한 값을 1단계 4.10 대로 반올림한 값이 소진율이다.

[4.7] `BURN <시각> <창> <SLO>` 는 그 SLO 의 시각 t, 창 w 소진율을 1단계 4.12 의 형식으로 출력한다. 소진율이 없으면 `NONE` 을 출력한다. SLO 가 없으면 `ERR NO_SLO` 를 출력한다.

### 경보

[4.8] `ALERT <경보> <SLO> <긴 창> <짧은 창> <기준>` 은 SLO 하나에 소진율 경보를 정한다. 짧은 창은 긴 창보다 짧아야 한다. 짧은 창이 긴 창과 같거나 길면 형식에 맞지 않는 인자다. 성공하면 아무것도 출력하지 않는다.

[4.9] 이미 정한 경보 이름이면 `ERR EXISTS` 를, SLO 가 없으면 `ERR NO_SLO` 를 출력한다. 정한 경보는 바꾸거나 지울 수 없다.

[4.10] 시각 t 에 경보 조건이 참이라는 것은 시각 t 의 긴 창 소진율과 짧은 창 소진율이 모두 있고 둘 다 기준보다 크다는 뜻이다. 소진율이 기준과 같으면 기준보다 크지 않다. 비교에는 4.6 에서 반올림한 소진율을 쓴다.

### 평가

[4.11] `EVAL <시작> <끝> <간격>` 은 그 줄 앞에서 정한 경보를 모두 평가한다. 평가 시각은 시작 이상 끝 이하인 정수 가운데 간격의 배수인 수다. 0 도 간격의 배수다. 평가 시각은 작은 것부터 차례로 처리한다. 시작이 끝보다 크면 형식에 맞지 않는 인자다.

[4.12] 경보는 켜짐과 꺼짐 가운데 한 상태에 있고 EVAL 을 시작할 때 모든 경보는 꺼져 있다. 평가 시각마다 모든 경보의 조건을 판정한다. 꺼진 경보의 조건이 참이면 경보가 켜지고 `<평가 시각> <경보> FIRING` 을 출력한다. 켜진 경보의 조건이 거짓이면 경보가 꺼지고 `<평가 시각> <경보> RESOLVED` 를 출력한다. 그 밖에는 출력하지 않는다. 평가 시각은 앞에 0 을 붙이지 않은 10진 정수로 쓴다.

[4.13] 출력은 평가 시각의 오름차순이다. 한 평가 시각에 상태가 바뀐 경보가 여럿이면 경보 이름의 바이트 오름차순으로 출력한다.

[4.14] 평가 시각을 모두 처리한 뒤 `EVALUATED <평가 시각 수>` 를 출력한다. 평가 시각 수는 앞에 0 을 붙이지 않은 10진 정수로 쓴다. 평가 시각이 하나도 없으면 `EVALUATED 0` 만 출력한다.

[4.15] EVAL 은 어떤 상태도 바꾸지 않는다. 마지막 평가 시각에 켜져 있던 경보도 다음 EVAL 에서는 꺼진 상태로 시작한다.

## 5. 오류 규칙

[5.1] 1단계와 2단계의 오류 규칙을 그대로 따른다. 명령어 목록에 SLO, BURN, ALERT, EVAL 이 더해진다. 이 문서에는 출력을 정하지 않는 줄이 없다.

[5.2] 인자 수는 SLO 가 4 이상, BURN 이 3, ALERT 가 5, EVAL 이 3 이다. SLO 의 다섯째 인자부터는 레이블 쌍 자리다.

[5.3] 줄마다 명령어, 인자 수, 인자 형식을 차례로 확인한다. 인자 형식은 왼쪽 인자부터 확인한다. ALERT 의 짧은 창이 긴 창보다 짧은지와 EVAL 의 시작이 끝보다 크지 않은지는 인자마다 형식을 확인한 뒤에 확인한다. 그다음 SLO 는 이름이 겹치는지, 전체 지표가 있는지, 나쁜 지표가 있는지, 전체 지표의 종류, 나쁜 지표의 종류를 차례로 확인한다. BURN 은 SLO 가 있는지 확인한다. ALERT 는 이름이 겹치는지와 SLO 가 있는지를 차례로 확인한다. 처음 걸린 오류 하나만 출력한다.

[5.4] 이 단계에서 새로 생기는 오류 줄은 아래와 같다.

| 오류 줄 | 뜻 |
|---|---|
| `ERR NO_SLO` | 정하지 않은 SLO 를 씀 |

## 6. 예시

[6.1] 예시의 표기는 1단계 6.1 과 같다.

[6.2] 60초마다 읽는 카운터 두 개로 정한 SLO 다. 요청은 60초마다 600개씩 늘고 나쁜 요청은 시각 180 부터 360 까지 60초마다 30개씩 는다. 목표 99 의 오류 예산 비율은 0.01 이다. 시각 240 의 120초 창에는 시각 180 과 240 의 표본이 들고 보외 구간이 표본 구간의 두 배라 T 는 1200, B 는 60 이다. 소진율은 (60 ÷ 1200) ÷ 0.01 = 5 다. 시각 240 의 300초 창에는 표본 다섯이 들고 T 는 3000, B 는 37.5 라 소진율은 1.25 다. 첫 EVAL 은 시각 0 부터 480 까지 60초마다 평가한다. ticket 은 시각 300 에서 480초 창 소진율 2 와 120초 창 소진율 5 가 모두 1.5 보다 커서 켜진다. page 는 시각 300 에서 300초 창 소진율이 2.5 라 켜지지 않고 시각 360 에서 켜진다. 시각 420 의 120초 창에는 늘어난 나쁜 요청이 없어 두 경보가 함께 꺼지고 이름 순서대로 page 를 먼저 출력한다. 둘째 EVAL 의 평가 시각은 90 의 배수인 270, 360, 450 이다.

```
> TYPE api_requests_total counter
> TYPE api_errors_total counter
> ADD 0 0 api_requests_total job=checkout
> ADD 0 0 api_errors_total job=checkout
> ADD 60 600 api_requests_total job=checkout
> ADD 60 0 api_errors_total job=checkout
> ADD 120 1200 api_requests_total job=checkout
> ADD 120 0 api_errors_total job=checkout
> ADD 180 1800 api_requests_total job=checkout
> ADD 180 0 api_errors_total job=checkout
> ADD 240 2400 api_requests_total job=checkout
> ADD 240 30 api_errors_total job=checkout
> ADD 300 3000 api_requests_total job=checkout
> ADD 300 60 api_errors_total job=checkout
> ADD 360 3600 api_requests_total job=checkout
> ADD 360 90 api_errors_total job=checkout
> ADD 420 4200 api_requests_total job=checkout
> ADD 420 90 api_errors_total job=checkout
> ADD 480 4800 api_requests_total job=checkout
> ADD 480 90 api_errors_total job=checkout
> SLO checkout 99 api_requests_total api_errors_total job=checkout
> BURN 240 120 checkout
5.000000
> BURN 240 300 checkout
1.250000
> BURN 300 300 checkout
2.500000
> BURN 420 300 checkout
3.750000
> BURN 420 120 checkout
0.000000
> ALERT page checkout 300 120 3
> ALERT ticket checkout 480 120 1.5
> EVAL 0 480 60
300 ticket FIRING
360 page FIRING
420 page RESOLVED
420 ticket RESOLVED
EVALUATED 9
> EVAL 250 500 90
360 page FIRING
360 ticket FIRING
450 page RESOLVED
450 ticket RESOLVED
EVALUATED 3
```

[6.3] 소진율이 없는 경우와 기준과 같은 소진율이다. SLO 는 시계열이 없어도 정할 수 있다. 시계열이 하나도 없으면 소진율이 없다. 시각 90 의 60초 창에서 T 는 120 이지만 나쁜 지표의 시계열이 아직 없어 B 가 NONE 이므로 소진율이 없다. 나쁜 요청의 표본을 적재한 뒤 시각 60 의 60초 창에는 두 지표의 표본이 둘씩 들고 B 는 0 이다. 요청이 늘지 않아 T 도 0 이므로 소진율이 없다. b-tie 는 시각 90 에서 두 창의 소진율이 모두 정확히 10 이라 기준 10 보다 크지 않으므로 켜지지 않는다. a-hot 은 시각 90 에서 켜지고 시각 120 에서 꺼진다. 시각 120 의 90초 창 소진율은 5 로 기준 3 보다 크지만 60초 창에서는 나쁜 요청이 늘지 않았기 때문이다. 마지막 EVAL 에는 30 의 배수인 평가 시각이 없다.

```
> TYPE req_total counter
> TYPE err_total counter
> SLO web 99 req_total err_total service=web
> BURN 100 60 web
NONE
> ADD 30 500 req_total service=web pod=p1
> ADD 60 500 req_total service=web pod=p1
> ADD 90 560 req_total service=web pod=p1
> ADD 120 620 req_total service=web pod=p1
> BURN 90 60 web
NONE
> ADD 30 0 err_total service=web pod=p1
> ADD 60 0 err_total service=web pod=p1
> ADD 90 6 err_total service=web pod=p1
> ADD 120 6 err_total service=web pod=p1
> BURN 60 60 web
NONE
> BURN 90 60 web
10.000000
> BURN 120 90 web
5.000000
> ALERT b-tie web 60 59 10
> ALERT a-hot web 90 60 3
> EVAL 0 200 30
90 a-hot FIRING
120 a-hot RESOLVED
EVALUATED 7
> EVAL 61 89 30
EVALUATED 0
```

[6.4] 오류다. SLO 는 이름이 겹치는지를 지표보다 먼저 확인하고 ALERT 는 이름이 겹치는지를 SLO 보다 먼저 확인한다. 짧은 창은 긴 창보다 짧아야 한다. SLO 이름 api 를 경보 이름으로도 쓸 수 있다.

```
> TYPE req_total counter
> TYPE err_total counter
> TYPE latency_ms histogram 100 300
> TYPE inflight gauge
> SLO api 100 req_total err_total
ERR BAD_ARGUMENT
> SLO api 99.9999 req_total err_total
ERR BAD_ARGUMENT
> SLO api 99.9 req_total missing_total
ERR NO_METRIC
> SLO api 99.9 req_total inflight
ERR WRONG_TYPE
> SLO api 99.9 latency_ms err_total
ERR WRONG_TYPE
> SLO api 99.9 req_total err_total job=api
> SLO api 99 req_total missing_total
ERR EXISTS
> SLO api/v1 99 req_total err_total
ERR BAD_ARGUMENT
> BURN 10 60 nope
ERR NO_SLO
> BURN 10 0 api
ERR BAD_ARGUMENT
> ALERT page api 300 300 14.4
ERR BAD_ARGUMENT
> ALERT page api 3600 300 0
ERR BAD_ARGUMENT
> ALERT page nope 3600 300 14.4
ERR NO_SLO
> ALERT page api 3600 300 14.4
> ALERT page nope 21600 1800 6
ERR EXISTS
> ALERT api api 21600 1800 6
> ALERT api api 21600 1800 6.0001
ERR BAD_ARGUMENT
> EVAL 100 50 10
ERR BAD_ARGUMENT
> EVAL 100 150 0
ERR BAD_ARGUMENT
```

## 7. 검사 방식

[7.1] 1단계 7.1~7.5, 7.7, 7.10, 7.11 을 그대로 따른다.

[7.2] 대규모 테스트는 시계열 20,000개 안팎과 표본 790,000개 안팎을 적재한 뒤 SLO 4개와 경보 12개를 정하고 BURN 수천 줄과 EVAL 몇 줄을 실행하는 입력이다. SLO 가 고르는 카운터 시계열 4개는 2초 간격으로 4일 동안의 표본을 담는다. SLO 하나는 그 가운데 2개나 4개를 고른다. 경보의 창은 5분부터 3일까지이고 EVAL 들의 평가 시각을 모두 더하면 50,000개 안팎이다. 제한 시간은 15초다. 평가 시각마다 창 안의 표본을 하나씩 훑는 풀이는 이 제한 시간을 넘기도록 규모를 정했다.

[7.3] 검사 입력의 EVAL 한 줄이 평가하는 시각은 20,000개 이하다.

[7.4] 3단계를 검사할 때는 1단계와 2단계 테스트를 먼저 다시 실행한다.
