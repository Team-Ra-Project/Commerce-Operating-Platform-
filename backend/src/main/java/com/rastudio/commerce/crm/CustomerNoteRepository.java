package com.rastudio.commerce.crm;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerNoteRepository extends JpaRepository<CustomerNote, Long> {
  List<CustomerNote> findByCustomerIdOrderByCreatedAtDesc(Long customerId);
}
