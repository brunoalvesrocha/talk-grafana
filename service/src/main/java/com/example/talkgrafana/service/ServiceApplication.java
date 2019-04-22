package com.example.talkgrafana.service;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.reactivestreams.Publisher;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.annotation.Id;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.query.ReactiveQueryByExampleExecutor;
import org.springframework.data.repository.reactive.ReactiveSortingRepository;
import org.springframework.http.MediaType;
import org.springframework.http.server.PathContainer;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.server.RequestPredicate;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.reactive.function.server.support.ServerRequestWrapper;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.URI;

import static org.springframework.web.reactive.function.server.RequestPredicates.DELETE;
import static org.springframework.web.reactive.function.server.RequestPredicates.GET;
import static org.springframework.web.reactive.function.server.RequestPredicates.POST;
import static org.springframework.web.reactive.function.server.RequestPredicates.PUT;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;

//@org.springframework.context.annotation.Profile("demo")
@SpringBootApplication
@Log4j2
public class ServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ServiceApplication.class, args);
    }

    @Bean
    ApplicationRunner runner(OrderRepository repository) {
        return args -> {
            repository
                    .deleteAll()
                    .thenMany(
                            Flux
                                    .just("pants", "t-shirt", "shots", "dress")
                                    .map(name -> new Order(null, name ))
                                    .flatMap(repository::save)
                    )
                    .thenMany(repository.findAll())
                    .subscribe(order -> log.info("saving " + order.toString()));
        };
    }

}

@Document
@Data
@AllArgsConstructor
@NoArgsConstructor
class Order {

    @Id
    private String orderId;
    private String productName;
}

interface OrderRepository extends ReactiveMongoRepository<Order, String> {
}

@NoRepositoryBean
interface ReactiveMongoRepository<T, ID> extends ReactiveSortingRepository<T, ID>, ReactiveQueryByExampleExecutor<T> {

    <S extends T> Mono<S> insert(S entity);

    <S extends T> Flux<S> insert(Iterable<S> entities);

    <S extends T> Flux<S> insert(Publisher<S> entities);

    <S extends T> Flux<S> findAll(Example<S> example);

    <S extends T> Flux<S> findAll(Example<S> example, Sort sort);

}

@Log4j2
@Service
class OrderService {

    private final ApplicationEventPublisher publisher;
    private final OrderRepository orderRepository;

    OrderService(ApplicationEventPublisher publisher, OrderRepository orderRepository) {
        this.publisher = publisher;
        this.orderRepository = orderRepository;
    }

    public Flux<Order> all() {
        return this.orderRepository.findAll();
    }

    public Mono<Order> get(String id) {
        return this.orderRepository.findById(id);
    }

    public Mono<Order> update(String id, String productName) {
        return this.orderRepository
                .findById(id)
                .map(p -> new Order(p.getOrderId(), productName))
                .flatMap(this.orderRepository::save);
    }

    public Mono<Order> delete(String orderId) {
        return this.orderRepository
                .findById(orderId)
                .flatMap(p -> this.orderRepository.deleteById(p.getOrderId()).thenReturn(p));
    }

    public Mono<Order> create(Order order) {
        return this.orderRepository
                .save(order)
                .doOnSuccess(profile -> this.publisher.publishEvent(new OrderCreatedEvent(order)));
    }
}

class OrderCreatedEvent extends ApplicationEvent {

    public OrderCreatedEvent(Order source) {
        super(source);
    }
}

@Configuration
class OrderEndpointConfiguration {

    @Bean
    RouterFunction<ServerResponse> routes(OrderHandler handler) {
        return route(i(GET("/orders")), handler::all)
                .andRoute(i(GET("/orders/{id}")), handler::getById)
                .andRoute(i(DELETE("/orders/{id}")), handler::deleteById)
                .andRoute(i(POST("/orders")), handler::create)
                .andRoute(i(PUT("/orders/{id}")), handler::updateById);
    }


    private static RequestPredicate i(RequestPredicate target) {
        return new CaseInsensitiveRequestPredicate(target);
    }
}

class CaseInsensitiveRequestPredicate implements RequestPredicate {

    private final RequestPredicate target;

    CaseInsensitiveRequestPredicate(RequestPredicate target) {
        this.target = target;
    }

    @Override
    public boolean test(ServerRequest request) {
        return this.target.test(new LowerCaseUriServerRequestWrapper(request));
    }

    @Override
    public String toString() {
        return this.target.toString();
    }
}

class LowerCaseUriServerRequestWrapper extends ServerRequestWrapper {

    LowerCaseUriServerRequestWrapper(ServerRequest delegate) {
        super(delegate);
    }

    @Override
    public URI uri() {
        return URI.create(super.uri().toString().toLowerCase());
    }

    @Override
    public String path() {
        return uri().getRawPath();
    }

    @Override
    public PathContainer pathContainer() {
        return PathContainer.parsePath(path());
    }
}

@Component
class OrderHandler {


    private final OrderService orderService;

    OrderHandler(OrderService orderService) {
        this.orderService = orderService;
    }

    Mono<ServerResponse> getById(ServerRequest r) {
        return defaultReadResponse(this.orderService.get(id(r)));
    }

    Mono<ServerResponse> all(ServerRequest r) {
        return defaultReadResponse(this.orderService.all());
    }

    Mono<ServerResponse> deleteById(ServerRequest r) {
        return defaultReadResponse(this.orderService.delete(id(r)));
    }

    Mono<ServerResponse> updateById(ServerRequest r) {
        Flux<Order> id = r.bodyToFlux(Order.class)
                .flatMap(p -> this.orderService.update(id(r), p.getProductName()));
        return defaultReadResponse(id);
    }

    Mono<ServerResponse> create(ServerRequest request) {
        Flux<Order> flux = request
                .bodyToFlux(Order.class)
                .flatMap(toWrite -> this.orderService.create(toWrite));
        return defaultWriteResponse(flux);
    }


    private static Mono<ServerResponse> defaultWriteResponse(Publisher<Order> orders) {
        return Mono
                .from(orders)
                .flatMap(p -> ServerResponse
                        .created(URI.create("/orders/" + p.getOrderId()))
                        .contentType(MediaType.APPLICATION_JSON_UTF8)
                        .build()
                );
    }


    private static Mono<ServerResponse> defaultReadResponse(Publisher<Order> profiles) {
        return ServerResponse
                .ok()
                .contentType(MediaType.APPLICATION_JSON_UTF8)
                .body(profiles, Order.class);
    }

    private static String id(ServerRequest r) {
        return r.pathVariable("id");
    }
}