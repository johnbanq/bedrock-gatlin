package com.github.johnbanq.begatlin.protocol;

import com.google.common.primitives.UnsignedLong;
import org.junit.jupiter.api.Test;

import static com.github.johnbanq.begatlin.protocol.RaknetUnconnectedPong.fromRakNet;
import static com.google.common.io.BaseEncoding.base16;
import static org.assertj.core.api.Assertions.assertThat;


public class RaknetUnconnectedPongTest {

    final byte[] samplePong = base16().decode("1c00000000004dfb128efb524b33f8af1b00ffff00fefefefefdfdfdfd1234567800614d4350453b446564696361746564205365727665723b3338393b312e31342e32313b303b31303b31303330323931393035353438383130323137313b426564726f636b206c6576656c3b537572766976616c3b313b31393133323b31393133333b".toUpperCase());

    @Test
    public void fromRakNetWorks() {
        final RaknetUnconnectedPong pong = fromRakNet(samplePong);

        assertThat(pong.getMilliSinceStart()).isEqualTo(UnsignedLong.valueOf("5110546"));
        assertThat(pong.getServerGUID()).isEqualTo(UnsignedLong.valueOf("8efb524b33f8af1b", 16));
        assertThat(pong.getOfflineMessageDataID()).isEqualTo(base16().decode("00ffff00fefefefefdfdfdfd12345678".toUpperCase()));
        assertThat(pong.getIDString()).isEqualTo("MCPE;Dedicated Server;389;1.14.21;0;10;10302919055488102171;Bedrock level;Survival;1;19132;19133;");
    }

    @Test
    public void toRakNetWorks() {
        final RaknetUnconnectedPong pong = fromRakNet(samplePong);
        final byte[] recovered = pong.toRakNet();
        assertThat(base16().encode(recovered)).isEqualTo(base16().encode(samplePong));
    }

}
