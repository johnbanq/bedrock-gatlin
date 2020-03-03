package com.github.johnbanq.begatlin;

import com.github.johnbanq.begatlin.gateway.DiscoveryClient;
import com.github.johnbanq.begatlin.gateway.Inbound;
import com.github.johnbanq.begatlin.gateway.OutboundServer;
import com.github.johnbanq.begatlin.gateway.PlayerConnection;
import com.github.johnbanq.begatlin.protocol.RaknetUnconnectedPing;
import com.github.johnbanq.begatlin.protocol.RaknetUnconnectedPong;
import io.vertx.core.Vertx;
import io.vertx.core.net.SocketAddress;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import reactor.core.publisher.DirectProcessor;
import reactor.core.publisher.Mono;

import static com.google.common.io.BaseEncoding.base16;

@Slf4j
public class App {

    final static byte[] samplePong = base16().decode("1c00000000004dfb128efb524b33f8af1b00ffff00fefefefefdfdfdfd1234567800614d4350453b446564696361746564205365727665723b3338393b312e31342e32313b303b31303b31303330323931393035353438383130323137313b426564726f636b206c6576656c3b537572766976616c3b313b31393133323b31393133333b".toUpperCase());

    @SneakyThrows
    public static void main(String[] args) {
        Vertx vertx = Vertx.vertx();

        final val outboundServer = new OutboundServer(vertx, SocketAddress.inetSocketAddress(19170, "127.0.0.1"));
        outboundServer.connect(new Inbound() {
            @Override
            public Mono<RaknetUnconnectedPong> handlePing(RaknetUnconnectedPing ping) {
                System.out.println("ping packet requested");
                return Mono.just(RaknetUnconnectedPong.fromRakNet(samplePong));
            }

            @Override
            public Mono<Void> handleNewPlayer(PlayerConnection conn) {
                System.out.println("new player detected!");
                return Mono.empty();
            }
        });
        outboundServer.start().log("start").block();
        outboundServer.getStateFlux().log("state/external").subscribe();
    }

}
