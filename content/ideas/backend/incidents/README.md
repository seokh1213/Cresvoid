# 장애 보고서 독해

큰 회사가 공개한 사후 장애 보고서를 바탕으로 원인 사슬, 기여 요인, 대응, 재발 방지책을 따라가는 지문을 만든다. 긴 기술 문서를 정확히 읽는 훈련과 실무 장애 감각을 함께 기른다. 트랙은 `incident-reading`(종류 reading)이다.

## 1. 지문 쓰는 규칙

- 회사가 공식으로 낸 사후 보고서만 쓴다. 언론 기사와 추측 글은 쓰지 않는다.
- 보고서 문장을 옮기지 않는다. 사실만 골라 새로 쓴다. 보고서의 설정 이름, 코드, 긴 인용은 쓰지 않는다.
- 날짜, 시각, 기간, 수치는 보고서의 값을 쓴다. 보고서에 없는 원인을 덧붙이지 않는다.
- 사람이나 회사를 탓하는 서술을 쓰지 않는다. 시스템과 절차의 약점으로 쓴다.
- 구성: 서비스 구조 설명, 시간 순서의 사건, 원인 사슬, 회복이 늦어진 이유, 재발 방지책. 문단은 6~7개, 길이는 2,600~3,000자다.
- 문항: 원인 사슬의 순서, 근본 원인과 기여 요인의 구별, 시간표 적용, 재발 방지책이 사슬의 어느 고리를 끊는지, 다른 시스템의 상황을 `<보기>` 로 주고 같은 원리를 적용하기. 5~6문항, 모두 choice, 선지 5개다.
- 출제와 검증은 `content/skills/cresvoid-reading-track` 을 따르고 `track` 은 `incident-reading` 으로 준다.

## 2. 보고서 목록

| 카드 | 사건 | 날짜 | 핵심 교훈 | 이어지는 카드 |
|---|---|---|---|---|
| R-incidents-10 | Cloudflare 정규식 CPU 고갈 | 2019-07-02 | 역추적 폭발, 전역 즉시 배포, 빠진 CPU 보호 | B-regex-10 정규식 엔진(선형 시간) |
| R-incidents-20 | GitHub 네트워크 분할과 갈라진 쓰기 | 2018-10-21 | 지역을 넘은 자동 장애 조치, 가용성보다 정합성 | R-distributed-10, B-distributed-20 |
| R-incidents-30 | AWS S3 용량 제거 명령 | 2017-02-28 | 도구 안전장치, 오래 재시작하지 않은 하위 시스템, 셀 | SD 셔플 샤딩 사례 |
| R-incidents-40 | Meta 백본 단절과 DNS | 2021-10-04 | 감사 도구의 버그, 스스로 광고를 거둔 DNS, 복구 접근 경로 | NET 아이디어 |
| R-incidents-50 | CrowdStrike 채널 파일 291 | 2024-07-19 | 필드 21개와 20개의 불일치, 검증기 빈틈, 단계 배포 없는 콘텐츠 | 설정도 코드처럼 검증 |
| R-incidents-60 | Google Cloud Service Control | 2025-06-12 | 기능 플래그 없는 코드 경로, 빈 필드 정책의 전역 복제, 백오프 없는 재시작 | load L-load-70 |
| R-incidents-70 | Slack 연휴 뒤 첫 월요일 | 2021-01-04 | 차가운 캐시와 급증, 게이트웨이 확장 지연, 설정 서비스 한도에 막힌 확장 | load L-load-40, L-load-60 |

## 3. 학습 경로

R-incidents-10 부터 차례로 읽는다. 보고서마다 이어지는 구현·실험 카드를 함께 하면 원인을 직접 재현해 볼 수 있다.

## 4. 환경

추가 도구가 필요 없다.

## 5. 아이디어

| 사건 | 메모 |
|---|---|
| Knight Capital(2012) | 오래된 코드와 재사용한 플래그가 배포 누락 서버에서 되살아난 사건. 미국 SEC 문서로 사실을 확인한 뒤 카드로 만든다 |
| GitLab 데이터베이스 삭제(2017) | 복제 지연 대응 중 잘못된 서버에서 데이터 삭제, 백업 다섯 가지가 모두 기대대로 동작하지 않음. 회사 사후 보고서로 확인한다 |
| Atlassian 사이트 삭제(2022) | 잘못된 ID 목록으로 지운 사이트와 긴 복구. 회사 사후 보고서로 확인한다 |

## 6. 출처

- Cloudflare, "Details of the Cloudflare outage on July 2, 2019": https://blog.cloudflare.com/details-of-the-cloudflare-outage-on-july-2-2019/
- GitHub, "October 21 post-incident analysis"(2018): https://github.blog/news-insights/company-news/oct21-post-incident-analysis/
- AWS, "Summary of the Amazon S3 Service Disruption in the Northern Virginia (US-EAST-1) Region"(2017): https://aws.amazon.com/message/41926/
- Meta, "More details about the October 4 outage"(2021): https://engineering.fb.com/2021/10/05/networking-traffic/outage-details/
- CrowdStrike, "Channel File 291 Incident Root Cause Analysis"(2024-08-06): https://www.crowdstrike.com/wp-content/uploads/2024/08/Channel-File-291-Incident-Root-Cause-Analysis-08.06.2024.pdf
- Google Cloud, Service Health 사건 보고(2025-06-12): https://status.cloud.google.com/incidents/ow5i3PPK96RduMcb1SsW
- Slack Engineering, "Slack's Outage on January 4th 2021": https://slack.engineering/slacks-outage-on-january-4th-2021/
