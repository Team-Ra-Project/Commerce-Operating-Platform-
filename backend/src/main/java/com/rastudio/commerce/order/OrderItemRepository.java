package com.rastudio.commerce.order;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {
  List<OrderItem> findByOrderIdOrderByIdAsc(Long orderId);

  List<OrderItem> findByOrderIdIn(List<Long> orderIds);
}
