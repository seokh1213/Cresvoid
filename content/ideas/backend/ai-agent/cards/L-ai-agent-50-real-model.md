---
id: L-ai-agent-50
title: 실제 모델로 도구 루프 평가하기
kind: lab
track: agent-lab
stage: real-model
status: 후보
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
- 메모: API 키와 비용 한도를 사용자가 정한 뒤 카드를 채운다.
