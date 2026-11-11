package com.navan.travel.transaction.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "flight_coupons")
public class FlightCouponEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transaction_id", nullable = false)
    private FlightDetailsEntity flight;

    @Column(name = "flight_number", nullable = false)
    private String flightNumber;

    private String carrier;

    @Column(name = "from_code", nullable = false)
    private String fromCode;

    @Column(name = "to_code", nullable = false)
    private String toCode;

    @Column(nullable = false)
    private Instant departure;

    @Column(nullable = false)
    private Instant arrival;

    @Column(nullable = false)
    private int position;

    public void setFlight(FlightDetailsEntity flight) {
        this.flight = flight;
    }

    public String getFlightNumber() {
        return flightNumber;
    }

    public void setFlightNumber(String flightNumber) {
        this.flightNumber = flightNumber;
    }

    public String getCarrier() {
        return carrier;
    }

    public void setCarrier(String carrier) {
        this.carrier = carrier;
    }

    public String getFromCode() {
        return fromCode;
    }

    public void setFromCode(String fromCode) {
        this.fromCode = fromCode;
    }

    public String getToCode() {
        return toCode;
    }

    public void setToCode(String toCode) {
        this.toCode = toCode;
    }

    public Instant getDeparture() {
        return departure;
    }

    public void setDeparture(Instant departure) {
        this.departure = departure;
    }

    public Instant getArrival() {
        return arrival;
    }

    public void setArrival(Instant arrival) {
        this.arrival = arrival;
    }

    public void setPosition(int position) {
        this.position = position;
    }
}
