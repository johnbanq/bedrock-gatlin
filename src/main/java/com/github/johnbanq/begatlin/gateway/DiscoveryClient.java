package com.github.johnbanq.begatlin.gateway;

import com.nukkitx.network.raknet.RakNetPong;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.datagram.DatagramPacket;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import reactor.core.publisher.Mono;
import reactor.netty.Connection;
import reactor.netty.udp.UdpClient;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoField;
import java.util.Set;

import static com.google.common.io.BaseEncoding.base16;

/**
 * a client for performing server discovery on network
 */
@Slf4j
public class DiscoveryClient {

    public void findServersOnLocalEthernet() {
        Connection connection =
                UdpClient.create()
                        .host("255.255.255.255")
                        .port(19132)
                        .handle(((udpInbound, udpOutbound) -> {
                            return null;
                        }))
                        .connectNow(Duration.ofSeconds(30));
    }

    private static boolean isUnconnectedPong(DatagramPacket packet) {
        return packet.data().getByte(0)==0x1C;
    }

    private static DiscoveredServer parseUnconnectedPong(DatagramPacket packet) {
        return null;
    }

}
