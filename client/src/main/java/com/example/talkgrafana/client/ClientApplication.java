package com.example.talkgrafana.client;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.net.URI;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@SpringBootApplication
public class ClientApplication {

    public static void main(String[] args) {
        SpringApplication.run(ClientApplication.class, args);
    }

    @Bean
    WebClient client(HedgeExchangeFilterFunction eff) {
        return WebClient.builder()
                .baseUrl("http://service")
                .filter(eff).build();
    }

}

@Log4j2
@Component
class HedgeExchangeFilterFunction implements ExchangeFilterFunction {

    private final DiscoveryClient discoveryClient;
    private final LoadBalancerClient loadBalancerClient;
    private final int attempts, maxAttempts;

    @Autowired
    HedgeExchangeFilterFunction(DiscoveryClient dc, LoadBalancerClient lbc) {
        this(dc, lbc, 1);
    }

    HedgeExchangeFilterFunction(DiscoveryClient discoveryClient, LoadBalancerClient loadBalancerClient, int attempts) {
        this.discoveryClient = discoveryClient;
        this.loadBalancerClient = loadBalancerClient;
        this.attempts = attempts;
        this.maxAttempts = attempts * 2;
    }

    @Override
    public Mono<ClientResponse> filter(ClientRequest request, ExchangeFunction next) {

        URI originalURI = request.url();
        String serviceID = originalURI.getHost();
        final List<ServiceInstance> serviceInstanceList = this.discoveryClient.getInstances(serviceID);
        Assert.state(serviceInstanceList.size() >= this.attempts, "there must be at least " +
                this.attempts + " instances of the service " + serviceID + "!");
        int counter = 0;

        Map<String, Mono<ClientResponse>> ships = new HashMap<>();

        while (ships.size() < this.attempts && (counter++ < this.maxAttempts)) {
            ServiceInstance lb = this.loadBalancerClient.choose(serviceID);
            String asciiString = lb.getUri().toASCIIString();
            ships.computeIfAbsent(asciiString, str -> invoke(lb, originalURI, request, next));

        }

        return Flux
                .first(ships.values())
                .singleOrEmpty();
    }

    private Mono<ClientResponse> invoke(ServiceInstance serviceInstance,
                                        URI originalURI,
                                        ClientRequest request,
                                        ExchangeFunction next) {
        URI uri = this.loadBalancerClient.reconstructURI(serviceInstance, originalURI);
        ClientRequest newRequest = ClientRequest.
                create(request.method(), uri)
                .headers(h -> h.addAll(request.headers()))
                .cookies(c -> c.addAll(request.cookies()))
                .attributes(a -> a.putAll(request.attributes()))
                .body(request.body())
                .build();

        return next
                .exchange(newRequest)
                .doOnNext(cr -> log.info("launching " + newRequest.url()));
    }
}

@Log4j2
@RestController
class HedgingRestController {

    private final WebClient client;

    HedgingRestController(WebClient client) {
        this.client = client;
    }

    @GetMapping("/hedge")
    Flux<String> greet() {
        return this.client
                .get()
                .uri("/hi")
                .retrieve()
                .bodyToFlux(String.class);
    }

    @GetMapping("/orders")
    Flux<Order> getOrders() {
        return this.client
                .get()
                .uri("/orders")
                .retrieve()
                .bodyToFlux(Order.class);
    }

    @GetMapping("/orders/{id}")
    Mono<Order> getOrderById(@PathVariable("id") String orderId) {
        return this.client
                .get()
                .uri("orders/{id}", orderId)
                .retrieve()
                .bodyToMono(Order.class);
    }

    @PostMapping("/orders")
    Mono<Order> createOrder(@RequestBody @Valid Order order) {
        return this.client
                .post()
                .uri("/orders")
                .body(Mono.just(order), Order.class)
                .retrieve()
                .bodyToMono(Order.class);
    }

    @GetMapping("/orders/{id}/events")
    Flux<OrderEvents> getOrderEvents(@PathVariable("id") String orderId) {
        return this.client
                .get()
                .uri("/orders/{id}/events", orderId)
                .exchange()
                .flatMapMany(clientResponse -> clientResponse.bodyToFlux(OrderEvents.class));


    }
}


@Data
@AllArgsConstructor
@NoArgsConstructor
class Order {

    private String orderId;
    @NotNull(message = "The productName cannot be null")
    private String productName;
//    private DateTime dataOfPurchase;
//    private BigDecimal value;
}

@Data
@NoArgsConstructor
@AllArgsConstructor
class OrderEvents {
    private String orderId;
    private Date orderDate;
}