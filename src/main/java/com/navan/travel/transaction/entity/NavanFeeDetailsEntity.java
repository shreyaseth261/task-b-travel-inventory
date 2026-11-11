package com.navan.travel.transaction.entity;

import com.navan.travel.transaction.FeeKind;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "navan_fee_details")
public class NavanFeeDetailsEntity {
    @Id
    @Column(name = "transaction_id")
    private String transactionId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "transaction_id")
    private TransactionEntity transaction;

    @Enumerated(EnumType.STRING)
    @Column(name = "fee_kind", nullable = false)
    private FeeKind feeKind;

    private String description;

    @Column(name = "related_external_id")
    private String relatedExternalId;

    public void setTransaction(TransactionEntity transaction) {
        this.transaction = transaction;
        if (transaction != null) {
            this.transactionId = transaction.getId();
        }
    }

    public FeeKind getFeeKind() {
        return feeKind;
    }

    public void setFeeKind(FeeKind feeKind) {
        this.feeKind = feeKind;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getRelatedExternalId() {
        return relatedExternalId;
    }

    public void setRelatedExternalId(String relatedExternalId) {
        this.relatedExternalId = relatedExternalId;
    }
}
