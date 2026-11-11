package com.navan.travel.api;

import com.navan.travel.api.dto.CreateInvoiceRequest;
import com.navan.travel.api.dto.InvoiceResponse;
import com.navan.travel.api.dto.PageResponse;
import com.navan.travel.common.Pagination;
import com.navan.travel.invoice.InvoiceService;
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
@RequestMapping("/invoices")
public class InvoiceController {
    private final InvoiceService invoices;

    public InvoiceController(InvoiceService invoices) {
        this.invoices = invoices;
    }

    @PostMapping
    public ResponseEntity<InvoiceResponse> create(@RequestBody CreateInvoiceRequest request) {
        InvoiceResponse created = invoices.create(request);
        return ResponseEntity.created(
                ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(created.id()).toUri()
        ).body(created);
    }

    @GetMapping("/{id}")
    public InvoiceResponse get(@PathVariable String id) {
        return invoices.get(id);
    }

    @GetMapping
    public PageResponse<InvoiceResponse> list(
            @RequestParam(defaultValue = "" + Pagination.DEFAULT_LIMIT) int limit,
            @RequestParam(defaultValue = "0") int offset
    ) {
        return invoices.list(limit, offset);
    }

    @PatchMapping("/{id}")
    public InvoiceResponse patch(@PathVariable String id, @RequestBody CreateInvoiceRequest request) {
        return invoices.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        invoices.delete(id);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}
