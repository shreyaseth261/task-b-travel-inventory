package com.navan.travel.transaction;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.navan.travel.api.dto.FeeLineDto;
import com.navan.travel.api.dto.TaxLineDto;
import com.navan.travel.api.dto.TransactionResponse;
import com.navan.travel.common.ApiException;
import com.navan.travel.common.Money;
import com.navan.travel.transaction.entity.FlightCouponEntity;
import com.navan.travel.transaction.entity.FlightDetailsEntity;
import com.navan.travel.transaction.entity.HotelDetailsEntity;
import com.navan.travel.transaction.entity.HotelExtraEntity;
import com.navan.travel.transaction.entity.NavanFeeDetailsEntity;
import com.navan.travel.transaction.entity.RailLegEntity;
import com.navan.travel.transaction.entity.TransactionEntity;
import com.navan.travel.transaction.entity.TransactionFeeLineEntity;
import com.navan.travel.transaction.entity.TransactionTaxLineEntity;
import com.navan.travel.transaction.metadata.FlightMetadata;
import com.navan.travel.transaction.metadata.HotelMetadata;
import com.navan.travel.transaction.metadata.NavanFeeMetadata;
import com.navan.travel.transaction.metadata.RailMetadata;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Component
public class TransactionMapper {
    private final ObjectMapper objectMapper;

    public TransactionMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public TransactionResponse toResponse(TransactionEntity entity) {
        return new TransactionResponse(
                entity.getId(),
                entity.getType(),
                entity.getExternalId(),
                entity.getCurrency(),
                entity.getOccurredAt(),
                Money.of(entity.getTotal()),
                toTaxDtos(entity),
                toFeeDtos(entity),
                metadataNode(entity)
        );
    }

    public List<TaxLineDto> toTaxDtos(TransactionEntity entity) {
        List<TaxLineDto> lines = new ArrayList<>();
        for (TransactionTaxLineEntity line : entity.getTaxLines()) {
            lines.add(new TaxLineDto(line.getName(), line.getRate(), Money.of(line.getAmount())));
        }
        return lines;
    }

    public List<FeeLineDto> toFeeDtos(TransactionEntity entity) {
        List<FeeLineDto> lines = new ArrayList<>();
        for (TransactionFeeLineEntity line : entity.getFeeLines()) {
            lines.add(new FeeLineDto(line.getName(), Money.of(line.getAmount())));
        }
        return lines;
    }

    public JsonNode metadataNode(TransactionEntity entity) {
        return objectMapper.valueToTree(toMetadata(entity));
    }

    public Object toMetadata(TransactionEntity entity) {
        return switch (entity.getType()) {
            case FLIGHT -> toFlightMetadata(entity);
            case HOTEL -> toHotelMetadata(entity);
            case RAIL -> toRailMetadata(entity);
            case NAVAN_FEE -> toNavanMetadata(entity);
        };
    }

    public void applyTaxLines(TransactionEntity entity, List<TaxLineDto> lines) {
        entity.getTaxLines().clear();
        int position = 0;
        for (TaxLineDto dto : lines) {
            Money.requireNonNegative(dto.amount(), "tax_lines.amount");
            TransactionTaxLineEntity line = new TransactionTaxLineEntity();
            line.setTransaction(entity);
            line.setName(dto.name());
            line.setRate(dto.rate());
            line.setAmount(Money.of(dto.amount()));
            line.setPosition(position++);
            entity.getTaxLines().add(line);
        }
    }

    public void applyFeeLines(TransactionEntity entity, List<FeeLineDto> lines) {
        entity.getFeeLines().clear();
        int position = 0;
        for (FeeLineDto dto : lines) {
            Money.requireNonNegative(dto.amount(), "fee_lines.amount");
            TransactionFeeLineEntity line = new TransactionFeeLineEntity();
            line.setTransaction(entity);
            line.setName(dto.name());
            line.setAmount(Money.of(dto.amount()));
            line.setPosition(position++);
            entity.getFeeLines().add(line);
        }
    }

    public void applyMetadata(TransactionEntity entity, JsonNode metadata) {
        if (metadata == null || metadata.isNull() || metadata.isMissingNode() || metadata.isEmpty()) {
            throw ApiException.badRequest("INVALID_METADATA", "metadata is required and must match type " + entity.getType().toJson());
        }
        clearTypeSpecific(entity);
        switch (entity.getType()) {
            case FLIGHT -> applyFlight(entity, read(metadata, FlightMetadata.class));
            case HOTEL -> applyHotel(entity, read(metadata, HotelMetadata.class));
            case RAIL -> applyRail(entity, read(metadata, RailMetadata.class));
            case NAVAN_FEE -> applyNavan(entity, read(metadata, NavanFeeMetadata.class));
        }
    }

    private <T> T read(JsonNode node, Class<T> type) {
        try {
            return objectMapper.convertValue(node, type);
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest("INVALID_METADATA", "metadata does not match type " + type.getSimpleName());
        }
    }

    private void clearTypeSpecific(TransactionEntity entity) {
        entity.setFlightDetails(null);
        entity.setHotelDetails(null);
        entity.getRailLegs().clear();
        entity.setNavanFeeDetails(null);
    }

    private void applyFlight(TransactionEntity entity, FlightMetadata metadata) {
        requireText(metadata.origin(), "metadata.origin");
        requireText(metadata.destination(), "metadata.destination");
        Money.requireNonNegative(metadata.fare(), "metadata.fare");
        if (metadata.coupons() == null || metadata.coupons().isEmpty()) {
            throw ApiException.badRequest("INVALID_METADATA", "flight metadata.coupons must contain at least one coupon");
        }
        FlightDetailsEntity details = new FlightDetailsEntity();
        details.setOrigin(metadata.origin());
        details.setDestination(metadata.destination());
        details.setFare(Money.of(metadata.fare()));
        entity.setFlightDetails(details);
        int position = 0;
        for (FlightMetadata.Coupon coupon : metadata.coupons()) {
            requireText(coupon.flightNumber(), "metadata.coupons.flight_number");
            requireText(coupon.from(), "metadata.coupons.from");
            requireText(coupon.to(), "metadata.coupons.to");
            if (coupon.departure() == null || coupon.arrival() == null) {
                throw ApiException.badRequest("INVALID_METADATA", "coupon departure and arrival are required");
            }
            FlightCouponEntity row = new FlightCouponEntity();
            row.setFlight(details);
            row.setFlightNumber(coupon.flightNumber());
            row.setCarrier(coupon.carrier());
            row.setFromCode(coupon.from());
            row.setToCode(coupon.to());
            row.setDeparture(coupon.departure());
            row.setArrival(coupon.arrival());
            row.setPosition(position++);
            details.getCoupons().add(row);
        }
    }

    private void applyHotel(TransactionEntity entity, HotelMetadata metadata) {
        requireText(metadata.name(), "metadata.name");
        if (metadata.address() == null) {
            throw ApiException.badRequest("INVALID_METADATA", "hotel metadata.address is required");
        }
        requireText(metadata.address().street(), "metadata.address.street");
        requireText(metadata.address().city(), "metadata.address.city");
        requireText(metadata.address().country(), "metadata.address.country");
        if (metadata.checkIn() == null || metadata.checkOut() == null) {
            throw ApiException.badRequest("INVALID_METADATA", "hotel check_in and check_out are required");
        }
        if (metadata.nights() == null) {
            throw ApiException.badRequest("INVALID_METADATA", "hotel metadata.nights is required");
        }
        Money.requireNonNegative(metadata.roomRatePerNight(), "metadata.room_rate_per_night");
        HotelDetailsEntity details = new HotelDetailsEntity();
        details.setName(metadata.name());
        details.setStreet(metadata.address().street());
        details.setCity(metadata.address().city());
        details.setCountry(metadata.address().country());
        details.setCheckIn(metadata.checkIn());
        details.setCheckOut(metadata.checkOut());
        details.setNights(metadata.nights());
        details.setRoomRatePerNight(Money.of(metadata.roomRatePerNight()));
        entity.setHotelDetails(details);
        if (metadata.extras() != null) {
            int position = 0;
            for (HotelMetadata.Extra extra : metadata.extras()) {
                requireText(extra.name(), "metadata.extras.name");
                Money.requireNonNegative(extra.amount(), "metadata.extras.amount");
                HotelExtraEntity row = new HotelExtraEntity();
                row.setHotel(details);
                row.setName(extra.name());
                row.setAmount(Money.of(extra.amount()));
                row.setPosition(position++);
                details.getExtras().add(row);
            }
        }
    }

    private void applyRail(TransactionEntity entity, RailMetadata metadata) {
        if (metadata.legs() == null || metadata.legs().isEmpty()) {
            throw ApiException.badRequest("INVALID_METADATA", "rail metadata.legs must contain at least one leg");
        }
        int position = 0;
        for (RailMetadata.Leg leg : metadata.legs()) {
            requireText(leg.origin(), "metadata.legs.origin");
            requireText(leg.destination(), "metadata.legs.destination");
            if (leg.departure() == null || leg.arrival() == null) {
                throw ApiException.badRequest("INVALID_METADATA", "rail leg departure and arrival are required");
            }
            RailLegEntity row = new RailLegEntity();
            row.setTransaction(entity);
            row.setOrigin(leg.origin());
            row.setDestination(leg.destination());
            row.setDeparture(leg.departure());
            row.setArrival(leg.arrival());
            row.setTrainNumber(leg.trainNumber());
            row.setTravelClass(leg.travelClass());
            row.setPosition(position++);
            entity.getRailLegs().add(row);
        }
    }

    private void applyNavan(TransactionEntity entity, NavanFeeMetadata metadata) {
        if (metadata.feeKind() == null) {
            throw ApiException.badRequest("UNKNOWN_FEE_KIND", "metadata.fee_kind is required");
        }
        NavanFeeDetailsEntity details = new NavanFeeDetailsEntity();
        details.setFeeKind(metadata.feeKind());
        details.setDescription(metadata.description());
        details.setRelatedExternalId(metadata.relatedExternalId());
        entity.setNavanFeeDetails(details);
    }

    private FlightMetadata toFlightMetadata(TransactionEntity entity) {
        FlightDetailsEntity details = entity.getFlightDetails();
        List<FlightMetadata.Coupon> coupons = new ArrayList<>();
        for (FlightCouponEntity coupon : details.getCoupons()) {
            coupons.add(new FlightMetadata.Coupon(
                    coupon.getFlightNumber(),
                    coupon.getCarrier(),
                    coupon.getFromCode(),
                    coupon.getToCode(),
                    coupon.getDeparture(),
                    coupon.getArrival()
            ));
        }
        return new FlightMetadata(details.getOrigin(), details.getDestination(), Money.of(details.getFare()), coupons);
    }

    private HotelMetadata toHotelMetadata(TransactionEntity entity) {
        HotelDetailsEntity details = entity.getHotelDetails();
        List<HotelMetadata.Extra> extras = new ArrayList<>();
        for (HotelExtraEntity extra : details.getExtras()) {
            extras.add(new HotelMetadata.Extra(extra.getName(), Money.of(extra.getAmount())));
        }
        return new HotelMetadata(
                details.getName(),
                new HotelMetadata.Address(details.getStreet(), details.getCity(), details.getCountry()),
                details.getCheckIn(),
                details.getCheckOut(),
                details.getNights(),
                Money.of(details.getRoomRatePerNight()),
                extras.isEmpty() ? null : extras
        );
    }

    private RailMetadata toRailMetadata(TransactionEntity entity) {
        List<RailMetadata.Leg> legs = new ArrayList<>();
        for (RailLegEntity leg : entity.getRailLegs()) {
            legs.add(new RailMetadata.Leg(
                    leg.getOrigin(),
                    leg.getDestination(),
                    leg.getDeparture(),
                    leg.getArrival(),
                    leg.getTrainNumber(),
                    leg.getTravelClass()
            ));
        }
        return new RailMetadata(legs);
    }

    private NavanFeeMetadata toNavanMetadata(TransactionEntity entity) {
        NavanFeeDetailsEntity details = entity.getNavanFeeDetails();
        return new NavanFeeMetadata(details.getFeeKind(), details.getDescription(), details.getRelatedExternalId());
    }

    private void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw ApiException.badRequest("INVALID_METADATA", field + " is required");
        }
    }
}
