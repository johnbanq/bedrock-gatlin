package com.github.johnbanq.begatlin.gateway;

import com.github.johnbanq.begatlin.protocol.RaknetUnconnectedPing;
import com.github.johnbanq.begatlin.protocol.RaknetUnconnectedPong;
import reactor.core.publisher.Mono;

public interface Inbound {

    Mono<RaknetUnconnectedPong> handlePing(RaknetUnconnectedPing ping);

    Mono<Void> handleNewPlayer(PlayerConnection conn);

}
