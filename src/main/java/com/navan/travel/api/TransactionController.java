package com.navan.travel.api;

import com.navan.travel.api.dto.CreateTransactionRequest;
import com.navan.travel.api.dto.PageResponse;
import com.navan.travel.api.dto.PatchTransactionRequest;
import com.navan.travel.api.dto.TransactionResponse;
import com.navan.travel.common.Pagination;
import com.navan.travel.transaction.TransactionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/transactions")
public class TransactionController {
    private final TransactionService transactions;

    public TransactionController(TransactionService transactions) {
        this.transactions = transactions;
    }

    @PostMapping
    public ResponseEntity<TransactionResponse> create(@Valid @RequestBody CreateTransactionRequest request) {
        TransactionResponse created = transactions.create(request);
        return ResponseEntity.created(
                ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(created.id()).toUri()
        ).body(created);
    }

    @GetMapping("/{id}")
    public TransactionResponse get(@PathVariable String id) {
        return transactions.get(id);
    }

    @GetMapping
    public PageResponse<TransactionResponse> list(
            @RequestParam(defaultValue = "" + Pagination.DEFAULT_LIMIT) int limit,
            @RequestParam(defaultValue = "0") int offset
    ) {
        return transactions.list(limit, offset);
    }

    @PatchMapping("/{id}")
    public TransactionResponse patch(@PathVariable String id, @Valid @RequestBody PatchTransactionRequest request) {
        return transactions.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        transactions.delete(id);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}
