package com.github.johnbanq.begatlin.protocol;

import static com.google.common.io.BaseEncoding.base16;

public class ProtocolUtil {

    public static byte[] OFFLINE_MESSAGE_ID_MAGIC = base16().decode("00ffff00fefefefefdfdfdfd12345678".toUpperCase());

    public static void assertPacketType(byte expected, byte actual) {
        if(expected != actual) {
            throw new IllegalArgumentException("type: " + actual + " does not matches type: " + expected);
        }
    }

}
