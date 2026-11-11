package com.navan.travel.transaction;

import com.navan.travel.api.dto.CreateTransactionRequest;
import com.navan.travel.api.dto.PatchTransactionRequest;
import com.navan.travel.common.ApiException;
import com.navan.travel.common.Ids;
import com.navan.travel.common.Money;
import com.navan.travel.invoice.InvoiceLineRepository;
import com.navan.travel.transaction.entity.TransactionEntity;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Component
public class TransactionWriter {
    private final TransactionRepository transactions;
    private final InvoiceLineRepository invoiceLines;
    private final TransactionMapper mapper;

    public TransactionWriter(
            TransactionRepository transactions,
            InvoiceLineRepository invoiceLines,
            TransactionMapper mapper
    ) {
        this.transactions = transactions;
        this.invoiceLines = invoiceLines;
        this.mapper = mapper;
    }

    @Transactional
    public TransactionEntity insert(CreateTransactionRequest request) {
        Money.requireNonNegative(request.total(), "total");
        Instant now = Instant.now();
        TransactionEntity entity = new TransactionEntity();
        entity.setId(Ids.transaction());
        entity.setExternalId(request.externalId());
        entity.setType(request.type());
        entity.setCurrency(request.currency());
        entity.setOccurredAt(request.occurredAt());
        entity.setTotal(Money.of(request.total()));
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        mapper.applyTaxLines(entity, request.taxLines());
        mapper.applyFeeLines(entity, request.feeLines());
        mapper.applyMetadata(entity, request.metadata());
        return transactions.saveAndFlush(entity);
    }

    @Transactional
    public TransactionEntity update(String id, PatchTransactionRequest request) {
        TransactionEntity entity = transactions.findById(id)
                .orElseThrow(() -> ApiException.notFound("TRANSACTION_NOT_FOUND", "Transaction not found: " + id));
        if (invoiceLines.existsByTransactionId(id)) {
            throw ApiException.conflict("TRANSACTION_INVOICED", "Transaction is on an invoice and cannot be changed");
        }
        if (request.getTotal() != null) {
            Money.requireNonNegative(request.getTotal(), "total");
            entity.setTotal(Money.of(request.getTotal()));
        }
        if (request.getCurrency() != null) {
            if (request.getCurrency().isBlank()) {
                throw ApiException.badRequest("INVALID_CURRENCY", "currency must not be blank");
            }
            entity.setCurrency(request.getCurrency());
        }
        if (request.getOccurredAt() != null) {
            entity.setOccurredAt(request.getOccurredAt());
        }
        if (request.getTaxLines() != null) {
            mapper.applyTaxLines(entity, request.getTaxLines());
        }
        if (request.getFeeLines() != null) {
            mapper.applyFeeLines(entity, request.getFeeLines());
        }
        if (request.getMetadata() != null) {
            mapper.applyMetadata(entity, request.getMetadata());
        }
        entity.setUpdatedAt(Instant.now());
        return transactions.saveAndFlush(entity);
    }

    @Transactional
    public void delete(String id) {
        TransactionEntity entity = transactions.findById(id)
                .orElseThrow(() -> ApiException.notFound("TRANSACTION_NOT_FOUND", "Transaction not found: " + id));
        if (invoiceLines.existsByTransactionId(id)) {
            throw ApiException.conflict("TRANSACTION_INVOICED", "Transaction is on an invoice and cannot be deleted");
        }
        transactions.delete(entity);
        transactions.flush();
    }
}
