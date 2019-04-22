package com.example.talkgrafana.service;

import lombok.extern.log4j.Log4j2;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.context.annotation.Import;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.UUID;
import java.util.function.Predicate;

/**
 * @author bruno.alves.rocha
 */
@Log4j2
@DataMongoTest
@Import(OrderService.class)
public class OrderServiceTest {

    private final OrderService service;
    private final OrderRepository repository;

    public OrderServiceTest(@Autowired OrderService service,
                            @Autowired OrderRepository repository) {
        this.service = service;
        this.repository = repository;
    }

    @Test
    public void getAll() {
        Flux<Order> saved = repository.saveAll(Flux.just(new Order(null, "pants"), new Order(null, "shorts"), new Order(null, "T-shirt")));

        Flux<Order> composite = service.all().thenMany(saved);

        Predicate<Order> match = order -> saved.any(saveItem -> saveItem.equals(order)).block();

        StepVerifier
                .create(composite)
                .expectNextMatches(match)
                .expectNextMatches(match)
                .expectNextMatches(match)
                .verifyComplete();
    }

    @Test
    public void save() {
        Mono<Order> orderMono = this.service.create(new Order(null, "pants"));
        StepVerifier
                .create(orderMono)
                .expectNextMatches(saved -> StringUtils.hasText(saved.getOrderId()))
                .verifyComplete();
    }

    @Test
    public void delete() {
        final Order test = new Order(null, "pants");
        Mono<Order> deleted = this.service
                .create(test)
                .flatMap(saved -> this.service.delete(saved.getOrderId()));
        StepVerifier
                .create(deleted)
                .expectNextMatches(order -> order.getProductName().equalsIgnoreCase(test.getProductName()))
                .verifyComplete();
    }

    @Test
    public void update() throws Exception {
        final Order test = new Order(null, "pants");
        Mono<Order> saved = this.service
                .create(test)
                .flatMap(p -> this.service.update(p.getOrderId(), "pants"));
        StepVerifier
                .create(saved)
                .expectNextMatches(p -> p.getProductName().equalsIgnoreCase("pants"))
                .verifyComplete();
    }

    @Test
    public void getById() {
        Order test = new Order(null, UUID.randomUUID().toString());
        Mono<Order> deleted = this.service
                .create(test)
                .flatMap(saved -> this.service.get(saved.getOrderId()));
        StepVerifier
                .create(deleted)
                .expectNextMatches(order -> StringUtils.hasText(order.getOrderId()) && test.getProductName().equalsIgnoreCase(order.getProductName()))
                .verifyComplete();
    }
}
