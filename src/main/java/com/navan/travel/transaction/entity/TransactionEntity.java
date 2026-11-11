package com.navan.travel.transaction.entity;

import com.navan.travel.transaction.TransactionType;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "transactions")
public class TransactionEntity {
    @Id
    private String id;

    @Column(name = "external_id", nullable = false, unique = true)
    private String externalId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionType type;

    @Column(nullable = false)
    private String currency;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal total;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    @OneToMany(mappedBy = "transaction", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    private List<TransactionTaxLineEntity> taxLines = new ArrayList<>();

    @OneToMany(mappedBy = "transaction", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    private List<TransactionFeeLineEntity> feeLines = new ArrayList<>();

    @OneToOne(mappedBy = "transaction", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private FlightDetailsEntity flightDetails;

    @OneToOne(mappedBy = "transaction", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private HotelDetailsEntity hotelDetails;

    @OneToMany(mappedBy = "transaction", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    private List<RailLegEntity> railLegs = new ArrayList<>();

    @OneToOne(mappedBy = "transaction", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private NavanFeeDetailsEntity navanFeeDetails;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getExternalId() {
        return externalId;
    }

    public void setExternalId(String externalId) {
        this.externalId = externalId;
    }

    public TransactionType getType() {
        return type;
    }

    public void setType(TransactionType type) {
        this.type = type;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(Instant occurredAt) {
        this.occurredAt = occurredAt;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public void setTotal(BigDecimal total) {
        this.total = total;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public List<TransactionTaxLineEntity> getTaxLines() {
        return taxLines;
    }

    public List<TransactionFeeLineEntity> getFeeLines() {
        return feeLines;
    }

    public FlightDetailsEntity getFlightDetails() {
        return flightDetails;
    }

    public void setFlightDetails(FlightDetailsEntity flightDetails) {
        this.flightDetails = flightDetails;
        if (flightDetails != null) {
            flightDetails.setTransaction(this);
        }
    }

    public HotelDetailsEntity getHotelDetails() {
        return hotelDetails;
    }

    public void setHotelDetails(HotelDetailsEntity hotelDetails) {
        this.hotelDetails = hotelDetails;
        if (hotelDetails != null) {
            hotelDetails.setTransaction(this);
        }
    }

    public List<RailLegEntity> getRailLegs() {
        return railLegs;
    }

    public NavanFeeDetailsEntity getNavanFeeDetails() {
        return navanFeeDetails;
    }

    public void setNavanFeeDetails(NavanFeeDetailsEntity navanFeeDetails) {
        this.navanFeeDetails = navanFeeDetails;
        if (navanFeeDetails != null) {
            navanFeeDetails.setTransaction(this);
        }
    }
}
