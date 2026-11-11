package com.navan.travel.invoice;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.navan.travel.api.dto.CreateInvoiceRequest;
import com.navan.travel.api.dto.FeeLineDto;
import com.navan.travel.api.dto.InvoiceResponse;
import com.navan.travel.api.dto.PageResponse;
import com.navan.travel.api.dto.TaxLineDto;
import com.navan.travel.common.ApiException;
import com.navan.travel.common.Ids;
import com.navan.travel.common.Money;
import com.navan.travel.common.Pagination;
import com.navan.travel.invoice.entity.InvoiceEntity;
import com.navan.travel.invoice.entity.InvoiceFeeRollupEntity;
import com.navan.travel.invoice.entity.InvoiceLineEntity;
import com.navan.travel.invoice.entity.InvoiceLineFeeEntity;
import com.navan.travel.invoice.entity.InvoiceLineTaxEntity;
import com.navan.travel.invoice.entity.InvoiceTaxRollupEntity;
import com.navan.travel.transaction.TransactionMapper;
import com.navan.travel.transaction.TransactionReader;
import com.navan.travel.transaction.TransactionRepository;
import com.navan.travel.transaction.entity.TransactionEntity;
import com.navan.travel.transaction.entity.TransactionFeeLineEntity;
import com.navan.travel.transaction.entity.TransactionTaxLineEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class InvoiceService {
    private final InvoiceRepository invoices;
    private final TransactionRepository transactions;
    private final TransactionReader transactionReader;
    private final TransactionMapper transactionMapper;
    private final ObjectMapper objectMapper;

    public InvoiceService(
            InvoiceRepository invoices,
            TransactionRepository transactions,
            TransactionReader transactionReader,
            TransactionMapper transactionMapper,
            ObjectMapper objectMapper
    ) {
        this.invoices = invoices;
        this.transactions = transactions;
        this.transactionReader = transactionReader;
        this.transactionMapper = transactionMapper;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public InvoiceResponse create(CreateInvoiceRequest request) {
        List<TransactionEntity> txns = loadTransactions(resolveIds(request));
        InvoiceEntity invoice = new InvoiceEntity();
        invoice.setId(Ids.invoice());
        Instant now = Instant.now();
        invoice.setCreatedAt(now);
        invoice.setUpdatedAt(now);
        applySnapshot(invoice, txns);
        return toResponse(invoices.saveAndFlush(invoice));
    }

    @Transactional
    public InvoiceResponse update(String id, CreateInvoiceRequest request) {
        InvoiceEntity invoice = invoices.findById(id)
                .orElseThrow(() -> ApiException.notFound("INVOICE_NOT_FOUND", "Invoice not found: " + id));
        List<TransactionEntity> txns = loadTransactions(resolveIds(request));
        applySnapshot(invoice, txns);
        invoice.setUpdatedAt(Instant.now());
        return toResponse(invoices.saveAndFlush(invoice));
    }

    @Transactional(readOnly = true)
    public InvoiceResponse get(String id) {
        InvoiceEntity invoice = invoices.findById(id)
                .orElseThrow(() -> ApiException.notFound("INVOICE_NOT_FOUND", "Invoice not found: " + id));
        return toResponse(invoice);
    }

    @Transactional(readOnly = true)
    public PageResponse<InvoiceResponse> list(int limit, int offset) {
        Pagination.validate(limit, offset);
        List<InvoiceResponse> items = invoices.findPage(limit, offset).stream()
                .map(this::toResponse)
                .toList();
        return new PageResponse<>(items, limit, offset);
    }

    @Transactional
    public void delete(String id) {
        InvoiceEntity invoice = invoices.findById(id)
                .orElseThrow(() -> ApiException.notFound("INVOICE_NOT_FOUND", "Invoice not found: " + id));
        invoices.delete(invoice);
        invoices.flush();
    }

    private List<String> resolveIds(CreateInvoiceRequest request) {
        if (request.getTransactionIds() != null && request.getTransactionId() != null) {
            throw ApiException.badRequest("INVALID_INVOICE", "Provide transaction_id or transaction_ids, not both");
        }
        List<String> ids;
        if (request.getTransactionIds() != null) {
            ids = request.getTransactionIds();
        } else if (request.getTransactionId() != null) {
            ids = List.of(request.getTransactionId());
        } else {
            throw ApiException.badRequest("INVALID_INVOICE", "transaction_id or transaction_ids is required");
        }
        if (ids.isEmpty()) {
            throw ApiException.badRequest("INVALID_INVOICE", "At least one transaction id is required");
        }
        Set<String> seen = new HashSet<>();
        for (String id : ids) {
            if (id == null || id.isBlank()) {
                throw ApiException.badRequest("INVALID_INVOICE", "transaction id must not be blank");
            }
            if (!seen.add(id)) {
                throw ApiException.conflict("DUPLICATE_TRANSACTION_ID", "Transaction id listed more than once: " + id);
            }
        }
        return ids;
    }

    private List<TransactionEntity> loadTransactions(List<String> ids) {
        List<TransactionEntity> loaded = new ArrayList<>();
        for (String id : ids) {
            TransactionEntity txn = transactions.findById(id)
                    .orElseThrow(() -> ApiException.notFound("TRANSACTION_NOT_FOUND", "Transaction not found: " + id));
            transactionReader.initialize(txn);
            loaded.add(txn);
        }
        String currency = loaded.get(0).getCurrency();
        for (TransactionEntity txn : loaded) {
            if (!currency.equals(txn.getCurrency())) {
                throw ApiException.conflict("MIXED_CURRENCY", "Invoice transactions must share one currency");
            }
        }
        return loaded;
    }

    private void applySnapshot(InvoiceEntity invoice, List<TransactionEntity> txns) {
        invoice.getLines().clear();
        invoice.getTaxRollups().clear();
        invoice.getFeeRollups().clear();

        invoice.setCurrency(txns.get(0).getCurrency());
        BigDecimal grandTotal = Money.ZERO;
        Map<String, TaxBucket> taxBuckets = new LinkedHashMap<>();
        Map<String, BigDecimal> feeBuckets = new LinkedHashMap<>();

        int linePosition = 0;
        for (TransactionEntity txn : txns) {
            InvoiceLineEntity line = new InvoiceLineEntity();
            line.setInvoice(invoice);
            line.setTransactionId(txn.getId());
            line.setType(txn.getType().toJson());
            line.setExternalId(txn.getExternalId());
            line.setCurrency(txn.getCurrency());
            line.setOccurredAt(txn.getOccurredAt());
            line.setTotal(Money.of(txn.getTotal()));
            line.setMetadataSnapshot(writeJson(transactionMapper.metadataNode(txn)));
            line.setPosition(linePosition++);

            int taxPos = 0;
            for (TransactionTaxLineEntity tax : txn.getTaxLines()) {
                InvoiceLineTaxEntity copy = new InvoiceLineTaxEntity();
                copy.setLine(line);
                copy.setName(tax.getName());
                copy.setRate(tax.getRate());
                copy.setAmount(Money.of(tax.getAmount()));
                copy.setPosition(taxPos++);
                line.getTaxLines().add(copy);
                TaxBucket bucket = taxBuckets.computeIfAbsent(taxKey(tax.getName(), tax.getRate()), k -> new TaxBucket(tax.getName(), tax.getRate()));
                bucket.amount = Money.add(bucket.amount, tax.getAmount());
            }

            int feePos = 0;
            for (TransactionFeeLineEntity fee : txn.getFeeLines()) {
                InvoiceLineFeeEntity copy = new InvoiceLineFeeEntity();
                copy.setLine(line);
                copy.setName(fee.getName());
                copy.setAmount(Money.of(fee.getAmount()));
                copy.setPosition(feePos++);
                line.getFeeLines().add(copy);
                feeBuckets.merge(fee.getName(), Money.of(fee.getAmount()), Money::add);
            }

            invoice.getLines().add(line);
            grandTotal = Money.add(grandTotal, txn.getTotal());
        }

        invoice.setGrandTotal(grandTotal);

        int taxRollupPos = 0;
        for (TaxBucket bucket : taxBuckets.values()) {
            InvoiceTaxRollupEntity row = new InvoiceTaxRollupEntity();
            row.setInvoice(invoice);
            row.setName(bucket.name);
            row.setRate(bucket.rate);
            row.setAmount(bucket.amount);
            row.setPosition(taxRollupPos++);
            invoice.getTaxRollups().add(row);
        }

        int feeRollupPos = 0;
        for (Map.Entry<String, BigDecimal> entry : feeBuckets.entrySet()) {
            InvoiceFeeRollupEntity row = new InvoiceFeeRollupEntity();
            row.setInvoice(invoice);
            row.setName(entry.getKey());
            row.setAmount(entry.getValue());
            row.setPosition(feeRollupPos++);
            invoice.getFeeRollups().add(row);
        }
    }

    private InvoiceResponse toResponse(InvoiceEntity invoice) {
        List<InvoiceResponse.Line> lines = new ArrayList<>();
        for (InvoiceLineEntity line : invoice.getLines()) {
            List<TaxLineDto> taxes = new ArrayList<>();
            for (InvoiceLineTaxEntity tax : line.getTaxLines()) {
                taxes.add(new TaxLineDto(tax.getName(), tax.getRate(), Money.of(tax.getAmount())));
            }
            List<FeeLineDto> fees = new ArrayList<>();
            for (InvoiceLineFeeEntity fee : line.getFeeLines()) {
                fees.add(new FeeLineDto(fee.getName(), Money.of(fee.getAmount())));
            }
            lines.add(new InvoiceResponse.Line(
                    line.getTransactionId(),
                    line.getType(),
                    line.getExternalId(),
                    line.getCurrency(),
                    line.getOccurredAt(),
                    Money.of(line.getTotal()),
                    taxes,
                    fees,
                    readJson(line.getMetadataSnapshot())
            ));
        }
        List<TaxLineDto> taxRollup = new ArrayList<>();
        for (InvoiceTaxRollupEntity row : invoice.getTaxRollups()) {
            taxRollup.add(new TaxLineDto(row.getName(), row.getRate(), Money.of(row.getAmount())));
        }
        List<FeeLineDto> feeRollup = new ArrayList<>();
        for (InvoiceFeeRollupEntity row : invoice.getFeeRollups()) {
            feeRollup.add(new FeeLineDto(row.getName(), Money.of(row.getAmount())));
        }
        return new InvoiceResponse(invoice.getId(), invoice.getCurrency(), Money.of(invoice.getGrandTotal()), lines, taxRollup, feeRollup);
    }

    private String taxKey(String name, BigDecimal rate) {
        return name + "\0" + (rate == null ? "" : rate.stripTrailingZeros().toPlainString());
    }

    private String writeJson(JsonNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to snapshot metadata", ex);
        }
    }

    private JsonNode readJson(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to read metadata snapshot", ex);
        }
    }

    private static final class TaxBucket {
        private final String name;
        private final BigDecimal rate;
        private BigDecimal amount = Money.ZERO;

        private TaxBucket(String name, BigDecimal rate) {
            this.name = name;
            this.rate = rate;
        }
    }
}
