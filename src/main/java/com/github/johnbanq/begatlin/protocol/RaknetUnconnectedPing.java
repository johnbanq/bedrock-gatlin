package com.github.johnbanq.begatlin.protocol;

import com.google.common.primitives.UnsignedLong;
import io.netty.buffer.Unpooled;
import lombok.*;

import static com.github.johnbanq.begatlin.util.ByteBufUtil.*;
import static com.github.johnbanq.begatlin.protocol.ProtocolUtil.assertPacketType;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RaknetUnconnectedPing {

    public static final byte TYPE = 0x01;

    //8Byte millisecond SinceStart
    private UnsignedLong milliSinceStart;

    //16Byte messageDataID
    private byte[] offlineMessageDataID;

    //8Byte client GUID
    private UnsignedLong clientGUID;

    @SneakyThrows
    public static RaknetUnconnectedPing fromRakNet(byte[] udpPayload) {
        final val buf = Unpooled.wrappedBuffer(udpPayload);
        final val ping = new RaknetUnconnectedPing();
        try {
            assertPacketType(TYPE, buf.readByte());
            ping.setMilliSinceStart(readUnsignedLong(buf));
            ping.setOfflineMessageDataID(readByteArray(buf, 16));
            ping.setClientGUID(readUnsignedLong(buf));
            return ping;
        } finally {
            buf.release();
        }
    }

    public byte[] toRakNet() {
        final val buf = Unpooled.buffer();
        try {
            buf.writeByte(TYPE);
            writeUnsignedLong(buf, milliSinceStart);
            buf.writeBytes(offlineMessageDataID);
            writeUnsignedLong(buf, clientGUID);

            byte[] array = new byte[buf.readableBytes()];
            buf.readBytes(array);
            return array;
        } finally {
            buf.release();
        }
    }

}
