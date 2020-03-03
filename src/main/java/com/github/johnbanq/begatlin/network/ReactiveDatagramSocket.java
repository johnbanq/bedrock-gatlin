package com.github.johnbanq.begatlin.network;

import io.vertx.core.Vertx;
import io.vertx.core.datagram.DatagramPacket;
import io.vertx.core.datagram.DatagramSocket;
import io.vertx.core.datagram.DatagramSocketOptions;
import io.vertx.core.net.SocketAddress;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class ReactiveDatagramSocket {

    private final DatagramSocket socket;

    public ReactiveDatagramSocket(Vertx vertx) {
        this.socket = vertx.createDatagramSocket();
    }

    public ReactiveDatagramSocket(Vertx vertx, DatagramSocketOptions options) {
        this.socket = vertx.createDatagramSocket(options);
    }

    public Mono<ReactiveDatagramSocket> start(SocketAddress address) {
        return Mono.create(sink->{
            socket.listen(address.port(), address.host(), h->{
                if(h.succeeded()) {
                    sink.success(this);
                } else {
                    sink.error(h.cause());
                }
            });
        });
    }

    public Mono<Void> stop() {
        return Mono.create(sink->{
            socket.close(h->{
                if(h.succeeded()) {
                    sink.success();
                } else {
                    sink.error(h.cause());
                }
            });
        });
    }

    public Flux<DatagramPacket> receiver() {
        return Flux.create(sink->{
            socket.handler(sink::next);
            socket.endHandler(h->sink.complete());
            socket.exceptionHandler(sink::error);
            sink.onDispose(()->{
                System.out.println("cleaned up!");
                socket.handler(h->{});
                socket.endHandler(h->{});
                socket.exceptionHandler(ex->{});
            });
        });
    }

    public Mono<Void> send(DatagramPacket packet) {
        return Mono.create(sink -> {
            socket.send(
                    packet.data(), packet.sender().port(), packet.sender().host(),
                    h -> {
                        if (h.succeeded()) {
                            sink.success();
                        } else {
                            if (h.cause() != null) {
                                sink.error(h.cause());
                            } else {
                                sink.error(new RuntimeException("unknown error"));
                            }
                        }
                    });
        });
    }

    public DatagramSocket getSocket() {
        return socket;
    }

}
