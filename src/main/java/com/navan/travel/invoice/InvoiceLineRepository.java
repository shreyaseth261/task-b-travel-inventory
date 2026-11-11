package com.navan.travel.invoice;

import com.navan.travel.invoice.entity.InvoiceLineEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvoiceLineRepository extends JpaRepository<InvoiceLineEntity, Long> {
    boolean existsByTransactionId(String transactionId);
}
