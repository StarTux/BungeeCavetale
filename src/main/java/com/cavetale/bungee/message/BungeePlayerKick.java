package com.cavetale.bungee.message;

import com.winthier.connect.payload.OnlinePlayer;
import lombok.Data;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.chat.ComponentSerializer;

@Data
public final class BungeePlayerKick {
    private OnlinePlayer player;
    private String message; // Component

    public BaseComponent[] parseMessage() {
        return ComponentSerializer.parse(this.message);
    }
}
