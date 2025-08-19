package dg.spigot.command;

import dg.DGSession;
import dg.DGSharedPool;
import dg.enums.DGState;
import dg.reflect.ReflectUtils;
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

import java.util.Set;
import java.util.concurrent.*;

public class MapCommand {
    // Track players currently processing the command
    private static final Set<String> processingPlayers = ConcurrentHashMap.newKeySet();
    // Timeout for async task
    private static final long TIMEOUT_SECONDS = 30L;

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
        final String playerName = player.getName();
        if (!processingPlayers.add(playerName)) {
            player.sendMessage("上一次二维码生成还未完成，请稍后再试。");
            return;
        }
        if (player.getInventory().getItemInHand().getType() != Material.AIR) {
            if (player.getInventory().getItemInHand().getType() != ReflectUtils.mapMaterial) {
                player.sendMessage("请先将主手物品放下再使用此命令。");
                processingPlayers.remove(playerName);
                return;
            }
        }
        // Async processing for blocking logic
        DGSharedPool.executorService.submit(() -> {
            ExecutorService singleExec = Executors.newSingleThreadExecutor();
            Future<?> future = null;
            try {
                Callable<QrCode> task = () -> {
                    DGSession session = SpigotMain.sessions.computeIfAbsent(playerName, k -> {
                        DGSession dgs = new DGSession(SpigotMain.a, SpigotMain.b);
                        DGSharedPool.executorService.submit(() -> {
                            dgs.awaitState(DGState.PLAYING);
                            if (dgs.getState() == DGState.PLAYING) {
                                Bukkit.getScheduler().runTask(SpigotMain.instance, () -> {
                                    player.sendMessage("连接成功辽！好好享受吧欸嘿嘿嘿~");
                                    SpigotMain.clearMaps(player);
                                });
                            }
                            dgs.awaitState(DGState.CLOSED);
                            SpigotMain.sessions.remove(playerName);
                            Bukkit.getScheduler().runTask(SpigotMain.instance, () -> {
                                player.sendMessage("连接断开辽...可能是因为强度设置低于服主设置的阈值，或者是网络问题？");
                            });
                        });
                        return dgs;
                    });
                    if (session.getState() == DGState.WAITING_SERVER) {
                        session.awaitState(DGState.WAITING_CLIENT);
                    }
                    if (session.getState() != DGState.WAITING_CLIENT) {
                        Bukkit.getScheduler().runTask(SpigotMain.instance, () -> {
                            player.sendMessage("连接失败，可能是已经连接过设备，或者网络错误？");
                        });
                        throw new RuntimeException("state error");
                    }
                    QrCode qrCode = session.getQrCode();
                    return qrCode;
                };
                future = singleExec.submit(task);
                QrCode qrCode = (QrCode) future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
                int dist = 32 - qrCode.size / 2;

                Bukkit.getScheduler().runTask(SpigotMain.instance, () -> {
                    try {
                        ItemStack map = new ItemStack(ReflectUtils.mapMaterial, 1);
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
                        ReflectUtils.processMapView1(meta, view);
                        meta.setDisplayName(SpigotMain.itemName);
                        map.setItemMeta(meta);
                        ReflectUtils.processMapView2(map, view);

                        player.getInventory().setItemInHand(map);
                    } catch (Exception ex) {
                        player.sendMessage("二维码生成失败，请联系管理员。");
                    } finally {
                        processingPlayers.remove(playerName);
                    }
                });
                singleExec.shutdown();
            } catch (TimeoutException e) {
                Bukkit.getScheduler().runTask(SpigotMain.instance, () -> {
                    player.sendMessage("二维码生成超时，请稍后重试。");
                    processingPlayers.remove(playerName);
                });
            } catch (Exception e) {
                Bukkit.getScheduler().runTask(SpigotMain.instance, () -> {
                    player.sendMessage("二维码生成失败，请联系管理员。");
                    processingPlayers.remove(playerName);
                });
            }
        });
    }
}
