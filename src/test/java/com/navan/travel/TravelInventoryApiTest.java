package com.navan.travel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TravelInventoryApiTest {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate http;

    @Autowired
    ObjectMapper mapper;

    @Autowired
    JdbcTemplate jdbc;

    private JsonNode seeds;

    @BeforeEach
    void setUp() throws Exception {
        jdbc.execute("SET REFERENTIAL_INTEGRITY FALSE");
        for (String table : List.of(
                "invoice_line_tax_lines",
                "invoice_line_fee_lines",
                "invoice_tax_rollups",
                "invoice_fee_rollups",
                "invoice_lines",
                "invoices",
                "flight_coupons",
                "hotel_extras",
                "flight_details",
                "hotel_details",
                "rail_legs",
                "navan_fee_details",
                "transaction_tax_lines",
                "transaction_fee_lines",
                "transactions"
        )) {
            jdbc.execute("DELETE FROM " + table);
        }
        jdbc.execute("SET REFERENTIAL_INTEGRITY TRUE");
        seeds = mapper.readTree(Files.readString(Path.of("fixtures/task-b/seed-payloads.json")));
        http.getRestTemplate().setRequestFactory(new JdkClientHttpRequestFactory());
    }

    @Test
    void createsAllTypesAndRoundTripsMetadata() {
        JsonNode flight = postTransaction(seeds.get("flight")).getBody();
        JsonNode hotel = postTransaction(seeds.get("hotel")).getBody();
        JsonNode rail = postTransaction(seeds.get("rail")).getBody();
        JsonNode tripFee = postTransaction(seeds.get("trip_fee")).getBody();
        JsonNode agentFee = postTransaction(seeds.get("agent_call_fee")).getBody();

        assertThat(getTransaction(flight.get("id").asText()).get("metadata").get("coupons")).isNotEmpty();
        assertThat(getTransaction(flight.get("id").asText()).get("metadata").get("coupons").get(0).get("flight_number").asText())
                .isEqualTo("BA982");
        assertThat(getTransaction(hotel.get("id").asText()).get("metadata").get("name").asText()).isEqualTo("Hotel Mitte London");
        assertThat(getTransaction(hotel.get("id").asText()).get("metadata").get("address").get("city").asText()).isEqualTo("London");
        assertThat(getTransaction(rail.get("id").asText()).get("metadata").get("legs")).hasSize(2);
        assertThat(getTransaction(tripFee.get("id").asText()).get("metadata").get("fee_kind").asText()).isEqualTo("trip_fee");
        assertThat(getTransaction(agentFee.get("id").asText()).get("metadata").get("fee_kind").asText()).isEqualTo("agent_call_fee");
    }

    @Test
    void duplicateExternalIdReturns409AndDoesNotInsertSecondRow() {
        JsonNode created = postTransaction(seeds.get("flight")).getBody();
        ResponseEntity<JsonNode> duplicate = postTransaction(seeds.get("flight"));
        assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(duplicate.getBody().get("existing_id").asText()).isEqualTo(created.get("id").asText());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM transactions WHERE external_id = ?", Integer.class,
                "flight-TXL-LHR-2026-03-11")).isEqualTo(1);
    }

    @Test
    void tripInvoiceMatchesExpectedTotalsAndRollups() {
        List<String> ids = seedTrip();
        JsonNode invoice = postInvoice(Map.of("transaction_ids", ids)).getBody();
        assertThat(money(invoice.get("grand_total"))).isEqualByComparingTo("685.70");
        assertThat(invoice.get("currency").asText()).isEqualTo("EUR");
        assertThat(invoice.get("lines")).hasSize(5);

        JsonNode flightLine = lineOfType(invoice, "flight");
        assertThat(money(flightLine.get("total"))).isEqualByComparingTo("220.00");
        assertThat(lineOfType(invoice, "hotel").get("total").decimalValue()).isEqualByComparingTo("331.70");
        assertThat(lineOfType(invoice, "rail").get("total").decimalValue()).isEqualByComparingTo("94.00");

        assertThat(invoice.get("tax_rollup").toString()).contains("Airport tax");
        assertThat(invoice.get("tax_rollup").toString()).contains("VAT");
        assertThat(invoice.get("fee_rollup").toString()).contains("YQ");
        assertThat(invoice.get("fee_rollup").toString()).contains("Booking fee");
        assertThat(lineOfType(invoice, "flight").get("metadata").get("coupons")).isNotEmpty();
        assertThat(lineOfType(invoice, "hotel").get("metadata").get("address").get("street").asText()).isEqualTo("12 Kingsway");
        assertThat(lineOfType(invoice, "rail").get("metadata").get("legs")).hasSize(2);
    }

    @Test
    void invoiceSnapshotIgnoresLaterLiveTransactionChange() {
        List<String> ids = seedTrip();
        String invoiceId = postInvoice(Map.of("transaction_ids", ids)).getBody().get("id").asText();
        String flightId = ids.get(0);

        jdbc.update("UPDATE transactions SET total = ? WHERE id = ?", new BigDecimal("999.00"), flightId);

        JsonNode live = getTransaction(flightId);
        assertThat(money(live.get("total"))).isEqualByComparingTo("999.00");

        JsonNode invoice = getInvoice(invoiceId);
        assertThat(money(lineOfType(invoice, "flight").get("total"))).isEqualByComparingTo("220.00");
        assertThat(money(invoice.get("grand_total"))).isEqualByComparingTo("685.70");
    }

    @Test
    void mixedCurrencyInvoiceReturns409() {
        String eurId = postTransaction(seeds.get("flight")).getBody().get("id").asText();
        ObjectNode usd = seeds.get("hotel").deepCopy();
        usd.put("external_id", "hotel-usd-" + UUID.randomUUID());
        usd.put("currency", "USD");
        String usdId = postTransaction(usd).getBody().get("id").asText();

        ResponseEntity<JsonNode> response = postInvoice(Map.of("transaction_ids", List.of(eurId, usdId)));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().get("code").asText()).isEqualTo("MIXED_CURRENCY");
    }

    @Test
    void concurrentDuplicateCreatesLeaveOneRow() throws Exception {
        JsonNode payload = uniqueFlight("race-" + UUID.randomUUID());
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Future<ResponseEntity<JsonNode>>> futures = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            futures.add(pool.submit(() -> {
                start.await(5, TimeUnit.SECONDS);
                RestTemplate rest = new RestTemplate();
                rest.setErrorHandler(new ResponseErrorHandler() {
                    @Override
                    public boolean hasError(ClientHttpResponse response) {
                        return false;
                    }

                    @Override
                    public void handleError(ClientHttpResponse response) {
                    }
                });
                return rest.postForEntity(url("/transactions"), json(payload), JsonNode.class);
            }));
        }
        start.countDown();
        ResponseEntity<JsonNode> first = futures.get(0).get(10, TimeUnit.SECONDS);
        ResponseEntity<JsonNode> second = futures.get(1).get(10, TimeUnit.SECONDS);
        pool.shutdownNow();

        assertThat(List.of(first.getStatusCode(), second.getStatusCode()))
                .contains(HttpStatus.CREATED)
                .allMatch(status -> status == HttpStatus.CREATED || status == HttpStatus.CONFLICT);

        String externalId = payload.get("external_id").asText();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM transactions WHERE external_id = ?", Integer.class, externalId))
                .isEqualTo(1);

        String existingId = first.getStatusCode() == HttpStatus.CREATED
                ? first.getBody().get("id").asText()
                : first.getBody().get("existing_id").asText();
        String otherId = second.getStatusCode() == HttpStatus.CREATED
                ? second.getBody().get("id").asText()
                : second.getBody().get("existing_id").asText();
        assertThat(otherId).isEqualTo(existingId);
    }

    @Test
    void patchUninvoicedTransactionUpdatesTotal() {
        String id = postTransaction(uniqueFlight("patch-" + UUID.randomUUID())).getBody().get("id").asText();
        ResponseEntity<JsonNode> patched = exchange(
                "/transactions/" + id,
                HttpMethod.PATCH,
                Map.of("total", new BigDecimal("10.00")),
                JsonNode.class
        );
        assertThat(patched.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(money(patched.getBody().get("total"))).isEqualByComparingTo("10.00");
        assertThat(money(getTransaction(id).get("total"))).isEqualByComparingTo("10.00");
        assertThat(patched.getBody().get("metadata").get("coupons")).isNotEmpty();
    }

    @Test
    void patchAndDeleteInvoicedTransactionReturn409() {
        List<String> ids = seedTrip();
        String invoiceId = postInvoice(Map.of("transaction_ids", ids)).getBody().get("id").asText();
        String flightId = ids.get(0);

        ResponseEntity<JsonNode> patched = exchange(
                "/transactions/" + flightId,
                HttpMethod.PATCH,
                Map.of("total", new BigDecimal("10.00")),
                JsonNode.class
        );
        assertThat(patched.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(money(getInvoice(invoiceId).get("grand_total"))).isEqualByComparingTo("685.70");
        assertThat(money(getTransaction(flightId).get("total"))).isEqualByComparingTo("220.00");

        ResponseEntity<Void> deleted = exchange("/transactions/" + flightId, HttpMethod.DELETE, null, Void.class);
        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(getTransaction(flightId).get("id").asText()).isEqualTo(flightId);
    }

    @Test
    void deleteInvoiceKeepsTransactions() {
        List<String> ids = seedTrip();
        String invoiceId = postInvoice(Map.of("transaction_ids", ids)).getBody().get("id").asText();

        ResponseEntity<Void> deleted = exchange("/invoices/" + invoiceId, HttpMethod.DELETE, null, Void.class);
        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(http.getForEntity(url("/invoices/" + invoiceId), JsonNode.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        for (String id : ids) {
            assertThat(getTransaction(id).get("id").asText()).isEqualTo(id);
        }
    }

    @Test
    void listTransactionsUsesDefaultLimitAndRejectsUnboundedLimit() {
        for (int i = 0; i < 3; i++) {
            postTransaction(uniqueFlight("list-" + i + "-" + UUID.randomUUID()));
        }
        JsonNode page = http.getForEntity(url("/transactions"), JsonNode.class).getBody();
        assertThat(page.get("limit").asInt()).isEqualTo(50);
        assertThat(page.get("offset").asInt()).isEqualTo(0);
        assertThat(page.get("items").size()).isEqualTo(3);

        ResponseEntity<JsonNode> tooBig = http.getForEntity(url("/transactions?limit=1000"), JsonNode.class);
        assertThat(tooBig.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM transactions", Integer.class)).isEqualTo(3);
    }

    @Test
    void unknownTypeIsRejected() {
        ObjectNode body = seeds.get("flight").deepCopy();
        body.put("type", "bus");
        body.put("external_id", "bus-" + UUID.randomUUID());
        assertThat(http.postForEntity(url("/transactions"), json(body), JsonNode.class).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void duplicateIdsOnInvoiceReturn409() {
        String id = postTransaction(seeds.get("flight")).getBody().get("id").asText();
        ResponseEntity<JsonNode> response = postInvoice(Map.of("transaction_ids", List.of(id, id)));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    private List<String> seedTrip() {
        return List.of(
                postTransaction(seeds.get("flight")).getBody().get("id").asText(),
                postTransaction(seeds.get("hotel")).getBody().get("id").asText(),
                postTransaction(seeds.get("rail")).getBody().get("id").asText(),
                postTransaction(seeds.get("trip_fee")).getBody().get("id").asText(),
                postTransaction(seeds.get("agent_call_fee")).getBody().get("id").asText()
        );
    }

    private ObjectNode uniqueFlight(String externalId) {
        ObjectNode copy = seeds.get("flight").deepCopy();
        copy.put("external_id", externalId);
        return copy;
    }

    private ResponseEntity<JsonNode> postTransaction(JsonNode body) {
        ResponseEntity<JsonNode> response = http.postForEntity(url("/transactions"), json(body), JsonNode.class);
        if (!response.getStatusCode().is2xxSuccessful() && !response.getStatusCode().isSameCodeAs(HttpStatus.CONFLICT)) {
            throw new AssertionError("POST /transactions failed: " + response.getStatusCode() + " " + response.getBody() + " payload=" + body);
        }
        return response;
    }

    private ResponseEntity<JsonNode> postInvoice(Object body) {
        return http.postForEntity(url("/invoices"), json(body), JsonNode.class);
    }

    private JsonNode getTransaction(String id) {
        ResponseEntity<JsonNode> response = http.getForEntity(url("/transactions/" + id), JsonNode.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private JsonNode getInvoice(String id) {
        ResponseEntity<JsonNode> response = http.getForEntity(url("/invoices/" + id), JsonNode.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private <T> ResponseEntity<T> exchange(String path, HttpMethod method, Object body, Class<T> type) {
        return http.exchange(url(path), method, json(body), type);
    }

    private HttpEntity<Object> json(Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private JsonNode lineOfType(JsonNode invoice, String type) {
        for (JsonNode line : invoice.get("lines")) {
            if (type.equals(line.get("type").asText())) {
                return line;
            }
        }
        throw new AssertionError("No invoice line of type " + type);
    }

    private BigDecimal money(JsonNode node) {
        return node.decimalValue();
    }
}
