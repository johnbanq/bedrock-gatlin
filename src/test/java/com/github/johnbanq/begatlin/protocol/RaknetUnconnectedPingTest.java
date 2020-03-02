package com.github.johnbanq.begatlin.protocol;

import com.google.common.primitives.UnsignedLong;
import org.junit.jupiter.api.Test;

import static com.google.common.io.BaseEncoding.base16;
import static org.assertj.core.api.Assertions.assertThat;

public class RaknetUnconnectedPingTest {

    private byte[] SAMPLE = base16().decode("0100000000004dfb1200ffff00fefefefefdfdfdfd12345678857ba389eb5323c2".toUpperCase());

    @Test
    public void fromRakNet() {
        final RaknetUnconnectedPing ping = RaknetUnconnectedPing.fromRakNet(SAMPLE);

        assertThat(ping.getMilliSinceStart()).isEqualTo(UnsignedLong.valueOf(5110546L));
        assertThat(ping.getClientGUID()).isEqualTo(UnsignedLong.valueOf("857ba389eb5323c2", 16));
        assertThat(ping.getOfflineMessageDataID()).isEqualTo(base16().decode("00ffff00fefefefefdfdfdfd12345678".toUpperCase()));
    }

    @Test
    public void toRakNet() {
        final RaknetUnconnectedPing ping = RaknetUnconnectedPing.fromRakNet(SAMPLE);
        final byte[] bytes = ping.toRakNet();
        assertThat(base16().encode(bytes)).isEqualTo(base16().encode(SAMPLE));
    }

}
