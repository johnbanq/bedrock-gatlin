package com.github.johnbanq.begatlin.gateway;

import com.github.johnbanq.begatlin.protocol.RaknetUnconnectedPing;
import com.github.johnbanq.begatlin.protocol.RaknetUnconnectedPong;
import reactor.core.publisher.Mono;

/**
 * represents a server provided to proxy, need to be served
 */
public interface InboundServer {

    Mono<RaknetUnconnectedPong> handlePing(RaknetUnconnectedPing ping);

    Mono<Void> handleNewPlayer(PlayerConnection conn);

}
