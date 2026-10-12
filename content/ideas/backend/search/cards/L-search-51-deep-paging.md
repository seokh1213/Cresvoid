---
id: L-search-51
title: 깊은 페이지 넘기기
kind: lab
track: es-lab
stage: deep-paging
status: 후보
wave: 4
env: docker
prereq: B-search-20
---

# L-search-51 깊은 페이지 넘기기

- 모드: implement
- 실행 방식: Docker 의 Elasticsearch 에 상품 50만 개를 넣고 전체를 내보내는 배치를 만든다. 내보내는 동안 다른 작업이 계속 색인한다.
- 목표: from + size 의 한도와 비용, search_after 와 PIT 의 필요성을 체험하고 빠짐도 겹침도 없는 내보내기를 만든다.
- 불변식: 시작 시점에 있던 상품을 정확히 한 번씩 내보낸다. 메모리와 시간 기준을 지킨다.
- 출처: Elastic 문서의 Paginate search results.
