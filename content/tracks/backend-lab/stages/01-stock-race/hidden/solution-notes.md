# 관찰·수정 후 해설

시작 코드의 예측과 실험, 수정을 마친 뒤 읽어라.

[10] 확인 후 행동은 확인할 때 참이던 조건이 행동할 때도 참이라고 가정하는 패턴이다. 두 요청이 재고 1을 읽고 각각 0을 쓰면 두 주문에 201을 주고도 실제 차감은 1개에 그친다. 개별 읽기와 쓰기의 가시성을 보장하는 것만으로 이 조합 전체가 원자적이 되지는 않는다. AtomicInteger.get()과 set()도 각각의 연산만 보장하며, 그 사이의 수량 확인까지 한 연산으로 묶어 주지는 않는다. compareAndSet은 예상한 값이 아직 같을 때만 바꾸며, 재시도한다면 매번 최신 재고로 부족 여부를 다시 판단해야 한다.

[11] synchronized는 같은 모니터를 공유하는 스레드끼리만 직렬화한다. 서로 다른 OrderService 인스턴스의 this에 잠금을 걸면 잠금도 둘이므로 공유 재고를 보호하지 못한다. 반대로 두 서비스가 같은 상품 객체의 모니터를 사용하면 이 JVM 안에서는 같은 상품의 갱신을 직렬화할 수 있다. 서버 프로세스가 둘이고 저장소가 외부에 있다면 각 JVM의 잠금은 공유되지 않는다. JVM 안에서의 보호 범위와 외부 저장소에서의 원자성을 구분하라.

[12] ConcurrentHashMap.compute는 해당 호출 전체를 원자적으로 수행하지만 계산 중 다른 갱신을 막을 수 있으므로 함수는 짧게 유지해야 한다. 해시 충돌로 같은 버킷을 쓰는 다른 키의 갱신도 영향을 받을 수 있다. compute 안에서 10ms 이상 기다리는 설계를 모든 상품의 병렬성을 보장하는 방법이라고 해석하지 마라. 이 단계는 한 상품의 정합성을 실험하며 다른 키의 처리량은 합격 기준으로 삼지 않는다.

[13] 저장소가 DB라면 UPDATE products SET stock = stock - :quantity WHERE id = :productId AND stock >= :quantity처럼 수량 조건과 차감을 한 문장으로 묶는 방법을 생각하라. 이 계약에서는 id가 유일하다고 가정한다. 영향받은 행 수가 1이면 차감 성공이고 0이면 조건에 맞는 상품이 없으므로 성공 주문으로 세면 안 된다. 상품 존재 여부를 별도로 보장하지 않는 API라면 행 수 0만으로 재고 부족과 없는 상품을 구분할 수 없다.

[14] API 근거는 JDK 21의 AtomicInteger 문서(get, set, compareAndSet), JLS 21 §17.1의 모니터 규칙, ConcurrentHashMap 문서(compute), PostgreSQL의 UPDATE 문서(WHERE 조건과 명령 태그 UPDATE count)다. 각각 https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/atomic/AtomicInteger.html , https://docs.oracle.com/javase/specs/jls/se21/html/jls-17.html#jls-17.1 , https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/ConcurrentHashMap.html#compute(K,java.util.function.BiFunction) , https://www.postgresql.org/docs/current/sql-update.html 을 참고하라.
