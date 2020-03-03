package com.github.johnbanq.begatlin.gateway;

import com.github.johnbanq.begatlin.protocol.BedrockPong;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.function.Supplier;

/**
 * represents a server that needs to be served to local network
 */
public class OutboundServer {

    public Flux<PlayerConnection> playerConnections() {
        return null;
    }

    public void fetchPong(Supplier<Mono<BedrockPong>> pongEmitter) {

    }

    public Mono<Void> start() {
        return null;
    }

    public Mono<Void> stop() {
        return null;
    }

}
