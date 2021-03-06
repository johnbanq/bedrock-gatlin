package com.github.johnbanq.begatlin.util;

import com.google.common.primitives.UnsignedLong;
import io.netty.buffer.ByteBuf;
import lombok.val;

import java.math.BigInteger;
import java.util.Arrays;

import static com.google.common.io.BaseEncoding.base16;

public class ByteBufUtil {


    public static UnsignedLong readUnsignedLong(ByteBuf buffer) {
        byte[] array = new byte[9];
        buffer.readBytes(array, 1, 8);
        return UnsignedLong.valueOf(new BigInteger(array));
    }

    public static void writeUnsignedLong(ByteBuf buffer, UnsignedLong value) {
        final val array = value.bigIntegerValue().toByteArray();
        for (int i=8-array.length;i>0;i--) {
            buffer.writeByte(0);
        }
        buffer.writeBytes(array, Math.max(0, array.length-8), Math.min(8, array.length));
    }

    public static byte[] readByteArray(ByteBuf buffer, int length) {
        final val array = new byte[length];
        buffer.readBytes(array);
        return array;
    }

}
