package dg.spigot.command;

import dg.DGSession;
import dg.DGSharedPool;
import dg.enums.DGState;
import dg.spigot.SpigotMain;
import nano.http.d2.qr.QrCode;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;

public class MapCommand {
    public static void handle(CommandSender sender) {
        if (!sender.hasPermission("dgmap.use")) {
            sender.sendMessage("你缺少使用此命令的权限。");
            return;
        }
        if (!(sender instanceof Player)) {
            sender.sendMessage("仅限玩家执行此命令。");
            return;
        }
        final Player player = (Player) sender;
        if (player.getInventory().getItemInMainHand().getType() != Material.AIR) {
            if (player.getInventory().getItemInMainHand().getType() != Material.FILLED_MAP) {
                player.sendMessage("请先将主手物品放下再使用此命令。");
                return;
            }
        }

        DGSession session = SpigotMain.sessions.computeIfAbsent(player.getName(), k -> {
            DGSession dgs = new DGSession(SpigotMain.a, SpigotMain.b);
            DGSharedPool.executorService.submit(() -> {
                dgs.awaitState(DGState.PLAYING);
                if (dgs.getState() == DGState.PLAYING) {
                    player.sendMessage("连接成功辽！好好享受吧欸嘿嘿嘿~");
                    SpigotMain.clearMaps(player);
                }
                dgs.awaitState(DGState.CLOSED);
                SpigotMain.sessions.remove(player.getName());
                player.sendMessage("连接断开辽...可能是因为强度设置低于服主设置的阈值，或者是网络问题？");
            });
            return dgs;
        });
        if (session.getState() == DGState.WAITING_SERVER) {
            session.awaitState(DGState.WAITING_CLIENT);
        }
        if (session.getState() != DGState.WAITING_CLIENT) {
            player.sendMessage("连接失败，可能是已经连接过设备，或者网络错误？");
            return;
        }
        QrCode qrCode = session.getQrCode();
        int dist = 32 - qrCode.size / 2;
        ItemStack map = new ItemStack(Material.FILLED_MAP, 1);
        MapMeta meta = (MapMeta) map.getItemMeta();
        MapView view = Bukkit.createMap(Bukkit.getWorlds().get(0));
        view.setScale(MapView.Scale.CLOSEST);
        view.getRenderers().clear();
        view.addRenderer(new MapRenderer() {
            public void render(MapView mapView, MapCanvas mapCanvas, Player player) {
                for (int _x = 0; _x < 128; _x++) {
                    for (int _y = 0; _y < 128; _y++) {
                        int x = _x / 2;
                        int y = _y / 2;
                        if (x >= dist && x < qrCode.size + dist && y >= dist && y < qrCode.size + dist && qrCode.getModule(x - dist, y - dist)) {
                            mapCanvas.setPixel(_x, _y, (byte) 89); // Set foreground to black
                        } else {
                            mapCanvas.setPixel(_x, _y, (byte) 34); // Set background to white
                        }
                    }
                }
            }
        });
        meta.setMapView(view);
        meta.setDisplayName(SpigotMain.itemName);
        map.setItemMeta(meta);
        player.getInventory().setItemInMainHand(map);
    }
}
