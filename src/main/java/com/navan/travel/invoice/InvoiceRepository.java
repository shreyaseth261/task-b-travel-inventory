package com.navan.travel.invoice;

import com.navan.travel.invoice.entity.InvoiceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface InvoiceRepository extends JpaRepository<InvoiceEntity, String> {
    @Query(value = """
            SELECT * FROM invoices
            ORDER BY created_at ASC, id ASC
            LIMIT :limit OFFSET :offset
            """, nativeQuery = true)
    List<InvoiceEntity> findPage(@Param("limit") int limit, @Param("offset") int offset);
}
