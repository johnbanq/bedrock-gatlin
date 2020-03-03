package com.github.johnbanq.begatlin.gateway;

import com.github.johnbanq.begatlin.network.ReactiveDatagramSocket;
import com.github.johnbanq.begatlin.protocol.BedrockPong;
import io.vertx.core.Vertx;
import io.vertx.core.datagram.DatagramSocketOptions;
import io.vertx.core.net.SocketAddress;
import lombok.val;
import reactor.core.Disposables;
import reactor.core.publisher.*;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedList;

import static com.github.johnbanq.begatlin.network.SimpleDatagramPacket.fromComponents;

/**
 * represents a server that is reachable in the local network, to be served by the gateway
 */
public class InboundServer {

    private static final SocketAddress ANY_ADDRESS = SocketAddress.inetSocketAddress(0, "0.0.0.0");

    private final Vertx vertx;

    private final SocketAddress localServerAddress;

    private final DatagramSocketOptions options;


    public static class EstablishedConnection {

        public final PlayerConnection connection;

        public final ReactiveDatagramSocket socket;

        public EstablishedConnection(PlayerConnection connection, ReactiveDatagramSocket socket) {
            this.connection = connection;
            this.socket = socket;
        }

    }

    /**
     * hot mono, indicates the state of server (running, successfully stop(complete) and errored(error))
     */
    private Flux<Void> stateFlux;

    private FluxSink<Void> stateSink;

    /**
     * list of connected clients and their respective sockets
     */
    private LinkedList<EstablishedConnection> connections;


    public InboundServer(Vertx vertx, SocketAddress localServerAddress) {
        this(vertx, localServerAddress, new DatagramSocketOptions());
    }

    public InboundServer(Vertx vertx, SocketAddress localServerAddress, DatagramSocketOptions options) {
        this.vertx = vertx;
        this.localServerAddress = localServerAddress;
        this.options = options;

        this.connections = null;
        this.stateFlux = null;
    }


    public Mono<Void> start() {
        if(isServerRunning()) {
            return Mono.error(new IllegalStateException("could not start a already started server!"));
        }

        //setup the state flux, sink and cleanup if it is done
        final DirectProcessor<Void> processor = DirectProcessor.create();
        stateFlux = processor.publish(v->v);
        stateSink = processor.sink();
        stateSink.onDispose(this::cleanup);

        try {
            //setup location for connections to store
            connections = new LinkedList<>();
        } catch (RuntimeException e) {
            stateSink.error(e);
        }

        //subscribe, aka start the server
        return Mono.fromRunnable(()->{
            stateFlux.subscribe();
        });
    }

    public Flux<Void> getStateFlux() {
        return stateFlux;
    }

    public Mono<Void> stop() {
        return Mono.create(sink->{
            //if running, submit complete event and start the shutdown sequence
            if(isServerRunning()) {
                stateSink.complete();
            }
            sink.success();
        });
    }

    private void cleanup() {
        // pull down "the server indicator"
        final val connections = this.connections;
        this.connections = null;
        this.stateFlux = null;
        this.stateSink = null;
    }

    /**
     * add a player connection to a running server
     * @param connection the connection to add
     * @return the mono indicates the status of the established connection, unsub for terminate
     */
    public Mono<Void> addPlayer(PlayerConnection connection) {
        if(!isServerRunning()) {
            return Mono.error(new IllegalStateException("could only add player to a running server!"));
        }
        final val socket = new ReactiveDatagramSocket(vertx, options);
        return Mono.empty()
                //get a socket
                .flatMap(v->socket.start(ANY_ADDRESS))
                .flatMap((v)->{
                    //add this pair to connections list
                    final val establishedConnection = new EstablishedConnection(connection, socket);
                    connections.addFirst(establishedConnection);
                    final val iter = connections.iterator();
                    //Mono that repr. the state of connection
                    return Mono.<Void>create(sink->{
                        // client -> server
                        final val outbound = connection.receiver()
                                .flatMap(p -> socket.send(fromComponents(localServerAddress, p)))
                                .subscribe(v2->{}, sink::error, sink::success);
                        // server -> client
                        final val inbound = socket.receiver()
                                .flatMap(p -> connection.send(p.data()))
                                .subscribe(v2->{}, sink::error, sink::success);
                        //if the server went down, we are also stopping for same cause
                        final val stateListener = stateFlux
                                .doOnError(sink::error)
                                .doOnComplete(sink::success)
                                .subscribe();
                        //clean-ups
                        sink.onDispose(Disposables.composite(inbound, outbound, stateListener));
                    })
                            //remove from connections
                            .doFinally(t->iter.remove())
                            //stop the socket
                            .doFinally(t->socket.stop().subscribe())
                            ;
                })
                ;
    }

    private boolean isServerRunning() {
        return connections != null;
    }

    LinkedList<EstablishedConnection> getConnections() {
        if(!isServerRunning()) {
            throw new IllegalStateException("could only list connections to a running server!");
        }
        return connections;
    }

    public Mono<BedrockPong> readPong() {
        return periodicPing(Duration.ofSeconds(1)).next();
    }

    private <T> Flux<BedrockPong> periodicPing(Duration pingDuration) {
        final val socket = new ReactiveDatagramSocket(vertx, new DatagramSocketOptions().setBroadcast(true));
        return DiscoveryFn.periodicPing(socket, SocketAddress.inetSocketAddress(localServerAddress.port(), "255.255.255.255"), pingDuration)
                .filter(i->i.getIp().equals(localServerAddress.host()))
                .map(DiscoveredServer::getPongMessage)
                ;
    }

}
