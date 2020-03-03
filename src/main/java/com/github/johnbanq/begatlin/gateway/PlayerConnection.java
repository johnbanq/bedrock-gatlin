package com.github.johnbanq.begatlin.gateway;

import io.vertx.core.buffer.Buffer;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.function.Function;

/**
 * represents a player connection
 */
public class PlayerConnection {

    private final Flux<Buffer> receiveFlux;

    private final Function<Buffer, Mono<Void>> sender;

    public PlayerConnection(Flux<Buffer> receiveFlux, Function<Buffer, Mono<Void>> senderFunction) {
        this.receiveFlux = receiveFlux;
        this.sender = senderFunction;
    }

    /**
     * the flux that represents inbound data of a connection
     */
    public Flux<Buffer> receiver() {
        return receiveFlux;
    }

    /**
     * the flux sink that represents outbound data of a connection
     */
    public Mono<Void> send(Buffer data) {
        return sender.apply(data);
    }

}
