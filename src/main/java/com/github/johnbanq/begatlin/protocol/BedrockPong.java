package com.github.johnbanq.begatlin.protocol;

import com.google.common.primitives.UnsignedLong;
import lombok.Data;
import lombok.SneakyThrows;

import java.util.StringJoiner;

/**
 * copied from Nukkit's Raknet Pong analyzer, with:
 *     serverId promoted to UnsignedLong,
 *     modification on from/to rakNet parameter
 *
 */
@Data
public class BedrockPong {

    private String edition;
    private String motd;
    private int protocolVersion = -1;
    private String version;
    private int playerCount = -1;
    private int maximumPlayerCount = -1;
    private UnsignedLong serverId;
    private String subMotd;
    private String gameType;
    private boolean nintendoLimited;
    private int ipv4Port = -1;
    private int ipv6Port = -1;
    private String[] extras;

    @SneakyThrows
    public static BedrockPong fromRakNet(String idString) {
        String info = idString;
        BedrockPong bedrockPong = new BedrockPong();
        String[] infos = info.split(";");
        switch (infos.length) {
            case 0:
                break;
            default:
                bedrockPong.extras = new String[infos.length - 12];
                System.arraycopy(infos, 12, bedrockPong.extras, 0, bedrockPong.extras.length);
            case 12:
                bedrockPong.ipv6Port = Integer.parseInt(infos[11]);
                // ignore
            case 11:
                bedrockPong.ipv4Port = Integer.parseInt(infos[10]);
                // ignore
            case 10:
                bedrockPong.nintendoLimited = !"1".equalsIgnoreCase(infos[9]);
            case 9:
                bedrockPong.gameType = infos[8];
            case 8:
                bedrockPong.subMotd = infos[7];
            case 7:
                bedrockPong.serverId = UnsignedLong.valueOf(infos[6]);
                // ignore
            case 6:
                bedrockPong.maximumPlayerCount = Integer.parseInt(infos[5]);
                // ignore
            case 5:
                bedrockPong.playerCount = Integer.parseInt(infos[4]);
                // ignore
            case 4:
                bedrockPong.version = infos[3];
            case 3:
                bedrockPong.protocolVersion = Integer.parseInt(infos[2]);
                // ignore
            case 2:
                bedrockPong.motd = infos[1];
            case 1:
                bedrockPong.edition = infos[0];
        }
        return bedrockPong;
    }

    public String toRakNet() {
        StringJoiner joiner = new StringJoiner(";").add(this.edition).add(toString(this.motd)).add(Integer.toString(this.protocolVersion)).add(toString(this.version)).add(Integer.toString(this.playerCount)).add(Integer.toString(this.maximumPlayerCount)).add((this.serverId).toString()).add(toString(this.subMotd)).add(toString(this.gameType)).add(this.nintendoLimited ? "0" : "1").add(Integer.toString(this.ipv4Port)).add(Integer.toString(this.ipv6Port));
        if (this.extras != null) {
            for (String extra : this.extras) {
                joiner.add(extra);
            }
        }
        return joiner.toString();
    }

    private static String toString(String string) {
        return string == null ? "" : string;
    }

}
