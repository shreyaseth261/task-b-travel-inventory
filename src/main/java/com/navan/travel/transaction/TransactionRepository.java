package com.navan.travel.transaction;

import com.navan.travel.transaction.entity.TransactionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface TransactionRepository extends JpaRepository<TransactionEntity, String> {
    Optional<TransactionEntity> findByExternalId(String externalId);

    @Query(value = """
            SELECT * FROM transactions
            ORDER BY created_at ASC, id ASC
            LIMIT :limit OFFSET :offset
            """, nativeQuery = true)
    java.util.List<TransactionEntity> findPage(@Param("limit") int limit, @Param("offset") int offset);
}
