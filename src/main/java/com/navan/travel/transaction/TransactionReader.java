package com.navan.travel.transaction;

import com.navan.travel.common.ApiException;
import com.navan.travel.transaction.entity.TransactionEntity;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Component
public class TransactionReader {
    private final TransactionRepository transactions;

    public TransactionReader(TransactionRepository transactions) {
        this.transactions = transactions;
    }

    @Transactional(readOnly = true)
    public TransactionEntity require(String id) {
        TransactionEntity entity = transactions.findById(id)
                .orElseThrow(() -> ApiException.notFound("TRANSACTION_NOT_FOUND", "Transaction not found: " + id));
        initialize(entity);
        return entity;
    }

    @Transactional(readOnly = true)
    public Optional<TransactionEntity> byExternalId(String externalId) {
        Optional<TransactionEntity> found = transactions.findByExternalId(externalId);
        found.ifPresent(this::initialize);
        return found;
    }

    public void initialize(TransactionEntity entity) {
        Hibernate.initialize(entity.getTaxLines());
        Hibernate.initialize(entity.getFeeLines());
        Hibernate.initialize(entity.getRailLegs());
        if (entity.getFlightDetails() != null) {
            Hibernate.initialize(entity.getFlightDetails().getCoupons());
        }
        if (entity.getHotelDetails() != null) {
            Hibernate.initialize(entity.getHotelDetails().getExtras());
        }
        Hibernate.initialize(entity.getNavanFeeDetails());
    }
}
