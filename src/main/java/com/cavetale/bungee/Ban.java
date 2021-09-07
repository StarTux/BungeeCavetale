package com.cavetale.bungee;

import java.text.DateFormatSymbols;
import java.util.Calendar;
import java.util.Date;
import java.util.UUID;
import lombok.Data;
import net.md_5.bungee.api.ChatColor;

@Data
public final class Ban {
    private int id;
    private String type;
    private PlayerInfo player;
    private PlayerInfo admin;
    private String reason;
    private final long time;
    private final long expiry;

    @Data
    public static final class PlayerInfo {
        private UUID uuid;
        private String name;
    }

    public static String format(String msg, Object... args) {
        msg = ChatColor.translateAlternateColorCodes('&', msg);
        if (args.length > 0) msg = String.format(msg, args);
        return msg;
    }

    public static String formatDate(Date date) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        return String.format("%s %02d %02d %02d:%02d",
                             DateFormatSymbols.getInstance().getShortMonths()[cal.get(Calendar.MONTH)],
                             cal.get(Calendar.DAY_OF_MONTH),
                             cal.get(Calendar.YEAR),
                             cal.get(Calendar.HOUR_OF_DAY),
                             cal.get(Calendar.MINUTE));
    }

    public String getMessage() {
        String passive;
        switch (type) {
        case "BAN": passive = "banned"; break;
        default: passive = type.toLowerCase() + "ed";
        }
        StringBuilder sb = new StringBuilder();
        sb.append(format("&cYou have been %s by &o%s&c.", passive, admin != null ? admin.name : "Console"));
        if (expiry != 0L) {
            Date now = new Date();
            Date exp = new Date(expiry);
            if (now.compareTo(exp) < 0) {
                sb.append(format("\n&cExpiry: &o%s&c", formatDate(exp)));
            }
        }
        if (reason != null) {
            sb.append(format("\n&cReason: &o%s", reason));
        }
        if ("BAN".equals(type) && expiry == 0L) {
            sb.append("\n");
            sb.append(format("&cAppeal at &9&nhttps://cavetale.com/ban-appeal/"));
        }
        return sb.toString();
    }
}
