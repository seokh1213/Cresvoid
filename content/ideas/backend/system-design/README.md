# 시스템 설계

대규모 서비스 설계에서 반복해 나오는 구성 요소를 작게 직접 만들고 빅테크가 공개한 설계 사례로 판단 근거를 익힌다. 주제의 뼈대는 Alex Xu 의 "System Design Interview" 1·2권(번역서 『가상 면접 사례로 배우는 대규모 시스템 설계 기초』 1·2, 인사이트) 목차다. 책의 내용은 옮기지 않고 장 주제만 출발점으로 쓴다.

## 1. 책 목차와 cresvoid 컨텐츠

장 제목은 영문판 목차다.

| 권·장 | 주제 | cresvoid 에서 다루는 곳 |
|---|---|---|
| 1·1 | Scale From Zero To Millions Of Users | R-system-design-10 지문 일부. 단계별 확장은 아이디어 |
| 1·2 | Back-of-the-envelope Estimation | R-system-design-10 |
| 1·3 | A Framework For System Design Interviews | 다루지 않는다(면접 진행 방법) |
| 1·4 | Design A Rate Limiter | B-system-design-20 구현, load L-load-90 실험 |
| 1·5 | Design Consistent Hashing | distributed B-distributed-40 |
| 1·6 | Design A Key-value Store | implementation B-kv-store-10, database B-database-30, distributed B-distributed-20 |
| 1·7 | Design A Unique Id Generator In Distributed Systems | B-system-design-30 |
| 1·8 | Design A Url Shortener | B-system-design-140 |
| 1·9 | Design A Web Crawler | B-system-design-60 |
| 1·10 | Design A Notification System | B-system-design-150 |
| 1·11 | Design A News Feed System | B-system-design-70 |
| 1·12 | Design A Chat System | B-system-design-80 |
| 1·13 | Design A Search Autocomplete System | B-system-design-90 |
| 1·14 | Design Youtube | 아이디어 |
| 1·15 | Design Google Drive | 아이디어. 동기화 시험은 distributed B-distributed-50 |
| 2·1 | Proximity Service | B-system-design-100 |
| 2·2 | Nearby Friends | 아이디어 |
| 2·3 | Google Maps | 아이디어 |
| 2·4 | Distributed Message Queue | messaging B-messaging-20 |
| 2·5 | Metrics Monitoring | observability B-observability-20 |
| 2·6 | Ad Click Event Aggregation | B-system-design-110. Spark 의 L-spark-34 와 이어진다 |
| 2·7 | Hotel Reservation | B-system-design-120 |
| 2·8 | Distributed Email Service | 아이디어 |
| 2·9 | S3-like Object Storage | B-system-design-160 |
| 2·10 | Real-time Gaming Leaderboard | B-system-design-130 |
| 2·11 | Payment System | B-system-design-40 |
| 2·12 | Digital Wallet | B-system-design-40 후속 아이디어 |
| 2·13 | Stock Exchange | B-system-design-50 |

## 2. 빅테크 공개 사례

카드를 만들 때 배경 자료로 쓴다. 수치는 각 글이 쓰인 때의 값이다.

- 처리율 제한(Stripe, 2017): 사용자별 초당 요청 수 제한, 동시 요청 수 제한, 중요 요청을 위해 용량을 남기는 함대 사용률 부하 차단, 워커 사용률 부하 차단의 네 가지를 쓴다. Redis 의 토큰 버킷으로 구현했다. 부하를 버릴 때 중요한 메서드를 마지막까지 지키고 테스트 모드, GET, POST 순서로 먼저 버린다. 제한기에 버그가 있거나 Redis 에 장애가 나도 요청이 실패하지 않게(fail open) 모든 층에서 예외를 처리한다.
- 미끄러지는 창 근사(Cloudflare, 2017): 이전 창의 개수에 남은 비율을 곱하고 현재 창의 개수를 더한다. 예: 42 × (60 − 15)/60 + 18 = 49.5. 요청 4억 건을 분석해 잘못 허용하거나 막은 요청이 0.003%, 실제 비율과의 평균 차이가 6% 였다. 요청 시각을 모두 저장하는 방식은 긴 창에서 메모리와 처리량이 감당되지 않아 쓰지 않았다.
- 고유 ID(Twitter Snowflake, 2010): 시각 41비트(밀리초, 사용자 정의 기준 시각, 약 69년), 기계 ID 10비트(1,024대), 순번 12비트(기계당 밀리초마다 4,096개). 기계끼리 조율하지 않고 대략 시간 순서(k-정렬)로 정렬된다.
- 샤드 ID 를 담은 ID(Instagram, 2012): 시각 41비트, 논리 샤드 13비트, 순번 10비트(1,024로 나눈 나머지). 논리 샤드 수천 개를 물리 서버 몇 대에 나눠 두고 ID 가운데 비트로 샤드를 찾는다. PostgreSQL 함수(PL/pgSQL)로 만든다.
- 적응형 동시성 제한(Netflix concurrency-limits): 리틀의 법칙(한도 = 평균 초당 요청 × 평균 지연)으로 고정 RPS 한도 대신 동시 실행 한도를 쓴다. 지연 기반 추정(Vegas: L × (1 − minRTT/sampleRTT))과 긴 창·짧은 창 평균의 차이(Gradient2)로 한도를 조정하고 한도를 넘으면 429 로 바로 거절한다.
- 장애 반경(AWS 셔플 샤딩): 워커 8개를 2개씩 묶는 조합은 28가지다. 고객마다 다른 조합을 주면 한 조합이 망가져도 영향은 약 1/28(3.6%)이다. 단순히 4개 샤드로 나누면 25% 다. Route 53 은 가상 네임 서버 2,048개를 4개씩 묶어 조합이 약 7,300억 가지다. 요청자가 재시도로 다른 서버를 쓸 수 있어야 효과가 있다.
- 결제 멱등성(Airbnb Orpheus, 2019): 요청을 사전 단계(요청 기록), 원격 호출 단계, 사후 단계(결과 기록)로 나눈다. 사전·사후 단계에서는 네트워크 호출을 하지 않고 원격 호출 단계에서는 DB 를 쓰지 않는다. 응답을 다시 시도할 수 있는 것과 없는 것으로 나눈다.
- 데이터 이전과 샤딩(Stripe 2017, Notion 2021, Figma 2024), 메시지 저장(Discord 2023)은 [../database/README.md](../database/README.md) 의 '사례 글' 절에 있다.
- 공개 장애 보고서는 [../incidents/README.md](../incidents/README.md) 에 있다.

## 3. 학습 경로

1. R-system-design-10 기술 지문으로 규모 추정을 익힌다.
2. 구성 요소 구현은 서로 독립적이다. 처리율 제한기(B-system-design-20), ID 생성기(B-system-design-30), 원장(B-system-design-40), 매칭 엔진(B-system-design-50)을 먼저 권한다. 나머지는 관심 순서로 한다.

## 4. 환경

모든 카드가 추가 도구 없이 표준 라이브러리로 만든다. 시각은 입력 명령으로 움직이는 가상 시계를 쓴다.

## 5. 아이디어

| 아이디어 | 출발점 | 메모 |
|---|---|---|
| 단계별 확장 지문 | 1권 1장 | 상태 없는 웹 계층, 캐시, CDN, 복제, 샤딩을 언제 더하는가 |
| 동영상 변환 작업 그래프 | 1권 14장 | 변환 작업 DAG 와 재시도, 진행률 |
| 파일 동기화와 충돌 | 1권 15장 | 블록 단위 변경 전송, 동시 수정 충돌 |
| 주변 친구 | 2권 2장 | 위치 갱신 팬아웃과 구독 |
| 경로 탐색 | 2권 3장 | 다익스트라, A*, 지도 타일 |
| 이메일 저장과 검색 | 2권 8장 | 스레드 묶기, 메일함 검색 색인 |
| 전자 지갑 | 2권 12장 | 이벤트 소싱과 상태 재구성, 송금 사가 |

## 6. 출처

- Xu, "System Design Interview – An Insider's Guide" 1권(2020), Xu, Lam, 2권(2022). 번역서 인사이트.
- 장 목록 확인: https://blog.bytebytego.com/p/system-design-interview-books-volume
- Stripe, "Scaling your API with rate limiters"(2017-03-30): https://stripe.com/blog/rate-limiters
- Cloudflare, "How we built rate limiting capable of scaling to millions of domains"(2017-06-07): https://blog.cloudflare.com/counting-things-a-lot-of-different-things/
- Twitter Snowflake README(2010): https://github.com/twitter-archive/snowflake/tree/snowflake-2010
- Instagram Engineering, "Sharding & IDs at Instagram"(2012). 원문 접속이 막혀 2차 인용으로 확인했다. 카드를 만들 때 원문을 다시 확인한다.
- Netflix concurrency-limits: https://github.com/Netflix/concurrency-limits
- Amazon Builders' Library, "Workload isolation using shuffle-sharding": https://builder.aws.com/content/3F06NpJ8YeoIGP8VHTw4n81pFn8/workload-isolation-using-shuffle-sharding
- Airbnb Engineering, "Avoiding Double Payments in a Distributed Payments System"(2019). 원문 접속이 막혀 2차 인용으로 확인했다.
