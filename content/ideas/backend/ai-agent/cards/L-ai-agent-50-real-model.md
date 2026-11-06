---
id: L-ai-agent-50
title: 실제 모델로 도구 루프 평가하기
kind: lab
track: agent-lab
stage: 01-real-model
status: 완료(2026-11-06)
wave: 5
env: llm-api
prereq: B-ai-agent-20
---

# L-ai-agent-50 실제 모델로 도구 루프 평가하기

- 모드: implement 뒤 observe
- 실행 방식: B-ai-agent-20 에서 만든 실행기의 모델 자리에 실제 모델 API 를 연결한다.
- 목표: 각본 모델과 실제 모델의 차이(비결정성, 도구 선택, 오류 회복)를 관찰하고 평가 하네스로 측정한다.
- 실험: 같은 작업 묶음을 여러 번 실행해 성공률과 분산, 토큰 사용량, 도구 호출 수를 잰다. 도구 설명을 바꿨을 때와 프롬프트 캐싱을 켰을 때의 차이를 비교한다.
- 관측: 작업별 성공률, 평균 차례 수, 입력·출력 토큰과 캐시 읽기 토큰.
- 출처: Claude 문서(도구 사용, 프롬프트 캐싱), How we built our multi-agent research system 의 평가 절.
- 모델과 비용: OpenCode Go 의 OpenAI 호환 chat/completions 와 deepseek-v4.1-flash 를 쓴다. 키는 OPENCODE_API_KEY 환경 변수로만 받는다. 요청마다 전용 User-Agent 와 대화별 x-opencode-session 을 보낸다. 실험 한 번의 요청 수와 토큰에 상한을 두고 응답의 사용량으로 비용을 센다.
- 판정: 모델 출력은 결정적이지 않으므로 불변식은 하네스가 지켜야 하는 성질(도구 호출 검증, 오류 회복, 차례 상한, 사용량 집계)로 정하고 성공률은 관측값으로만 쓴다.
