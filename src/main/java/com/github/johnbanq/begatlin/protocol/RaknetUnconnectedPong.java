package com.github.johnbanq.begatlin.protocol;

import com.google.common.primitives.UnsignedLong;
import io.netty.buffer.Unpooled;
import lombok.*;

import java.nio.charset.StandardCharsets;

import static com.github.johnbanq.begatlin.util.ByteBufUtil.*;
import static com.github.johnbanq.begatlin.util.ByteBufUtil.writeUnsignedLong;
import static com.github.johnbanq.begatlin.protocol.ProtocolUtil.assertPacketType;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RaknetUnconnectedPong {

    public static final byte TYPE = 0x1C;

    //8Byte millisecond SinceStart
    private UnsignedLong milliSinceStart;

    //8Byte server GUID
    private UnsignedLong serverGUID;

    //16Byte messageDataID
    private byte[] offlineMessageDataID;

    //server ID String
    private String IDString;

    @SneakyThrows
    public static RaknetUnconnectedPong fromRakNet(byte[] udpPayload) {
        final val buf = Unpooled.wrappedBuffer(udpPayload);
        final val pong = new RaknetUnconnectedPong();
        try {
            assertPacketType(TYPE, buf.readByte());
            pong.setMilliSinceStart(readUnsignedLong(buf));
            pong.setServerGUID(readUnsignedLong(buf));
            pong.setOfflineMessageDataID(readByteArray(buf, 16));
            short len = buf.readShort();
            byte[] strBuf = new byte[len];
            buf.readBytes(strBuf);
            pong.setIDString(new String(strBuf, StandardCharsets.UTF_8));
            return pong;
        } finally {
            buf.release();
        }
    }

    public byte[] toRakNet() {
        final val buf = Unpooled.buffer();
        try {
            buf.writeByte(TYPE);
            writeUnsignedLong(buf, milliSinceStart);
            writeUnsignedLong(buf, serverGUID);
            buf.writeBytes(offlineMessageDataID);
            final val strBuf = IDString.getBytes(StandardCharsets.UTF_8);
            buf.writeShort(strBuf.length);
            buf.writeBytes(strBuf);
            byte[] array = new byte[buf.readableBytes()];
            buf.readBytes(array);
            return array;
        } finally {
            buf.release();
        }
    }

}
