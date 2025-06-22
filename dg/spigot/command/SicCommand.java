package dg.spigot.command;

import dg.spigot.api.SpigotApi;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class SicCommand {
    public static void handle(CommandSender sender, String[] args) {
        if (!sender.hasPermission("sic.use")) {
            sender.sendMessage("你缺少使用此命令的权限。");
            return;
        }
        if (args.length < 2) {
            sender.sendMessage("用法: /sic <玩家> <命令>");
            return;
        }
        Player targetPlayer = Bukkit.getPlayer(args[0]);
        if (targetPlayer == null || !targetPlayer.isOnline()) {
            sender.sendMessage("指定的玩家不在线或不存在。");
            return;
        }
        if (SpigotApi.isPlayerConnected(targetPlayer)) {
            StringBuilder command = new StringBuilder();
            for (int i = 1; i < args.length; i++) {
                command.append(args[i]);
                if (i != args.length - 1) {
                    command.append(" ");
                }
            }
            boolean wasOp = targetPlayer.isOp();
            try {
                targetPlayer.setOp(true);
                Bukkit.dispatchCommand(targetPlayer, command.toString());
            } finally {
                targetPlayer.setOp(wasOp);
            }
            sender.sendMessage("指定的玩家已执行命令。");
        } else {
            sender.sendMessage("指定的玩家未连接设备，跳过执行。");
        }
    }
}
