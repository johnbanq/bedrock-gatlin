package com.github.johnbanq.begatlin.gateway;

import com.github.johnbanq.begatlin.protocol.BedrockPong;
import com.google.common.primitives.UnsignedLong;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class DiscoveredServer {

    private final String ip;

    private final UnsignedLong guid;

    private final BedrockPong pongMessage;

}
