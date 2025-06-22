package dg.spigot.command;

import dg.spigot.api.SpigotApi;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class WavCommand {
    public static void handle(CommandSender sender, String[] args) {
        if (!sender.hasPermission("wav.use")) {
            sender.sendMessage("你缺少使用此命令的权限。");
            return;
        }
        if (args.length != 4) {
            sender.sendMessage("用法: /wav <玩家> <a通道> <b通道> <秒数>");
            return;
        }

        int a, b, seconds;
        try {
            a = Integer.parseInt(args[1]);
            b = Integer.parseInt(args[2]);
            seconds = Integer.parseInt(args[3]);
            if (a <= 0 || b <= 0 || seconds <= 0 || a > 200 || b > 200) {
                throw new NumberFormatException();
            }
        } catch (Exception e) {
            sender.sendMessage("输入有误...请输入正整数。");
            return;
        }

        Player targetPlayer = Bukkit.getPlayer(args[0]);
        if (targetPlayer == null || !targetPlayer.isOnline()) {
            sender.sendMessage("指定的玩家不在线或不存在。");
            return;
        }

        try {
            if (SpigotApi.sendShockwave(targetPlayer, a, b, seconds)) {
                sender.sendMessage("波形已发送给 " + targetPlayer.getName() + "。");
            } else {
                sender.sendMessage("发送失败，设备连接错误。");
            }
        } catch (Exception e) {
            sender.sendMessage("参数错误: " + e.getMessage());
        }
    }
}
