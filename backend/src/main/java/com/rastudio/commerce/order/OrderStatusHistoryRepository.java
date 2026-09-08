package com.rastudio.commerce.order;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderStatusHistoryRepository extends JpaRepository<OrderStatusHistory, Long> {
  List<OrderStatusHistory> findByOrderIdOrderByChangedAtAsc(Long orderId);
}
