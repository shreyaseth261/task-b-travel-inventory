package com.navan.travel.transaction;

import com.navan.travel.api.dto.CreateTransactionRequest;
import com.navan.travel.api.dto.PageResponse;
import com.navan.travel.api.dto.PatchTransactionRequest;
import com.navan.travel.api.dto.TransactionResponse;
import com.navan.travel.common.ApiException;
import com.navan.travel.common.Pagination;
import com.navan.travel.transaction.entity.TransactionEntity;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class TransactionService {
    private final TransactionRepository transactions;
    private final TransactionWriter writer;
    private final TransactionReader reader;
    private final TransactionMapper mapper;

    public TransactionService(
            TransactionRepository transactions,
            TransactionWriter writer,
            TransactionReader reader,
            TransactionMapper mapper
    ) {
        this.transactions = transactions;
        this.writer = writer;
        this.reader = reader;
        this.mapper = mapper;
    }

    public TransactionResponse create(CreateTransactionRequest request) {
        try {
            return mapper.toResponse(writer.insert(request));
        } catch (DataIntegrityViolationException ex) {
            throw duplicate(request.externalId(), ex);
        } catch (ApiException ex) {
            if (ex.getStatus() == HttpStatus.BAD_REQUEST) {
                var existing = reader.byExternalId(request.externalId());
                if (existing.isPresent()) {
                    throw duplicate(request.externalId(), ex);
                }
            }
            throw ex;
        }
    }

    private ApiException duplicate(String externalId, RuntimeException fallback) {
        TransactionEntity existing = reader.byExternalId(externalId)
                .orElseThrow(() -> fallback);
        return ApiException.conflict("DUPLICATE_EXTERNAL_ID", "A transaction with this external_id already exists")
                .with("existing_id", existing.getId())
                .with("transaction", mapper.toResponse(existing));
    }

    public TransactionResponse get(String id) {
        return mapper.toResponse(reader.require(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<TransactionResponse> list(int limit, int offset) {
        Pagination.validate(limit, offset);
        List<TransactionResponse> items = transactions.findPage(limit, offset).stream()
                .peek(reader::initialize)
                .map(mapper::toResponse)
                .toList();
        return new PageResponse<>(items, limit, offset);
    }

    @Transactional
    public TransactionResponse update(String id, PatchTransactionRequest request) {
        TransactionEntity updated = writer.update(id, request);
        reader.initialize(updated);
        return mapper.toResponse(updated);
    }

    public void delete(String id) {
        writer.delete(id);
    }
}
