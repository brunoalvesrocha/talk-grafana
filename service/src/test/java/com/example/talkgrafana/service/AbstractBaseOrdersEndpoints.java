package com.example.talkgrafana.service;

import lombok.extern.log4j.Log4j2;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * @author bruno.alves.rocha
 */
@Log4j2
@WebFluxTest
public abstract class AbstractBaseOrdersEndpoints {

    private final WebTestClient client;

    @MockBean
    private OrderRepository repository;

    public AbstractBaseOrdersEndpoints(WebTestClient client) {
        this.client = client;
    }

    @Test
    public void getAll() {

        log.info("running  " + this.getClass().getName());


        Mockito
                .when(this.repository.findAll())
                .thenReturn(Flux.just(new Order("1", "A"), new Order("2", "B")));


        this.client
                .get()
                .uri("/orders")
                .accept(MediaType.APPLICATION_JSON_UTF8)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON_UTF8)
                .expectBody()
                .jsonPath("$.[0].orderId").isEqualTo("1")
                .jsonPath("$.[0].productName").isEqualTo("A")
                .jsonPath("$.[1].orderId").isEqualTo("2")
                .jsonPath("$.[1].productName").isEqualTo("B");
    }

    @Test
    public void save() {
        Order data = new Order("123", UUID.randomUUID().toString());
        Mockito
                .when(this.repository.save(Mockito.any(Order.class)))
                .thenReturn(Mono.just(data));
        MediaType jsonUtf8 = MediaType.APPLICATION_JSON_UTF8;
        this
                .client
                .post()
                .uri("/orders")
                .contentType(jsonUtf8)
                .body(Mono.just(data), Order.class)
                .exchange()
                .expectStatus().isCreated()
                .expectHeader().contentType(jsonUtf8);
    }

    @Test
    public void delete() {
        Order data = new Order("123", UUID.randomUUID().toString() + "@email.com");
        Mockito
                .when(this.repository.findById(data.getOrderId()))
                .thenReturn(Mono.just(data));
        Mockito
                .when(this.repository.deleteById(data.getOrderId()))
                .thenReturn(Mono.empty());
        this
                .client
                .delete()
                .uri("/orders/" + data.getOrderId())
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    public void update() {
        Order data = new Order("123", UUID.randomUUID().toString() + "@email.com");

        Mockito
                .when(this.repository.findById(data.getOrderId()))
                .thenReturn(Mono.just(data));

        Mockito
                .when(this.repository.save(data))
                .thenReturn(Mono.just(data));

        this
                .client
                .put()
                .uri("/orders/" + data.getOrderId())
                .contentType(MediaType.APPLICATION_JSON_UTF8)
                .body(Mono.just(data), Order.class)
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    public void getById() {

        Order data = new Order("1", "A");

        Mockito
                .when(this.repository.findById(data.getOrderId()))
                .thenReturn(Mono.just(data));

        this.client
                .get()
                .uri("/orders/" + data.getOrderId())
                .accept(MediaType.APPLICATION_JSON_UTF8)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON_UTF8)
                .expectBody()
                .jsonPath("$.orderId").isEqualTo(data.getOrderId())
                .jsonPath("$.productName").isEqualTo(data.getProductName());
    }
}
