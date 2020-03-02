package com.github.johnbanq.begatlin.protocol;

public class ProtocolUtil {

    public static void assertPacketType(byte expected, byte actual) {
        if(expected != actual) {
            throw new IllegalArgumentException("type: " + actual + " does not matches type: " + expected);
        }
    }

}
