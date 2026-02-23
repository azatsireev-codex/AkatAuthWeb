package net.akat.auth.util;

import com.velocitypowered.api.proxy.Player;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;

public class IpUtils {
    private static final Logger logger = LoggerFactory.getLogger(IpUtils.class);

    public static String getPlayerIp(Player player) {
        if (player == null || player.getRemoteAddress() == null) {
            return "unknown";
        }

        try {
            InetSocketAddress address = player.getRemoteAddress();
            if (address != null && address.getAddress() != null) {
                return address.getAddress().getHostAddress();
            }
        } catch (Exception e) {
            logger.warn("Не удалось получить IP игрока: {}", player.getUsername(), e);
        }

        return "unknown";
    }
}
