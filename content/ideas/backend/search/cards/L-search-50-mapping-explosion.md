---
id: L-search-50
title: 동적 매핑과 필드 폭증
kind: lab
track: es-lab
stage: mapping-explosion
status: 후보
wave: 4
env: docker
prereq: R-search-10
---

# L-search-50 동적 매핑과 필드 폭증

- 모드: observe 뒤 implement
- 실행 방식: Docker 의 Elasticsearch 에 상품 속성을 키-값 객체로 담은 문서를 넣는다. 판매자마다 속성 키가 다르다.
- 목표: 동적 매핑으로 필드 수가 늘다 한도에서 색인이 실패하는 과정을 예측하고 매핑 설계로 막는다.
- 예측 문항: 몇 번째 문서에서 실패하는가, 실패한 문서와 이미 들어간 문서는 어떻게 되는가, 문자열이 text 와 keyword 로 둘 다 잡힐 때 필드 수.
- implement 목표: 속성을 이름과 값의 nested 배열이나 flattened 같은 구조로 바꿔 필드 수를 상수로 묶고 같은 검색을 지원한다.
- 출처: Elastic 문서의 Mapping limit settings 와 동적 매핑.
