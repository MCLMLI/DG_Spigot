package dg.spigot;

import dg.DGSession;
import dg.DGSharedPool;
import dg.enums.DGChannel;
import dg.enums.DGState;
import nano.http.d2.qr.QrCode;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;

public class SpigotMain extends JavaPlugin implements Listener {
    private static final String itemName = "郊狼绑定二维码";

    private static boolean freeze = false;
    private static boolean multiply = false;
    private static int a = 0;
    private static int b = 0;
    private static int punishInteract = 0;
    private static int punishHurt = 0;

    private void clearMaps(Player p) {
        for (int i = 0; i < p.getInventory().getSize(); i++) {
            ItemStack item = p.getInventory().getItem(i);
            if (item != null && item.getType() == Material.FILLED_MAP) {
                MapMeta meta = (MapMeta) item.getItemMeta();
                if (meta != null && itemName.equals(meta.getDisplayName())) {
                    p.getInventory().setItem(i, null);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        DGSession session = sessions.get(event.getPlayer().getName());
        if (session != null && session.getState() == DGState.PLAYING) {
            if (punishInteract != 0) {
                if (session.isPlaying()) {
                    return;
                }
                session.sendStrength(a / 2, DGChannel.A);
                session.sendStrength(b / 2, DGChannel.B);
                session.sendWave(punishInteract, DGChannel.BOTH);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onHurt(EntityDamageEvent event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof Player)) {
            return; // 仅处理玩家受伤事件
        }
        Player player = (Player) entity;
        DGSession session = sessions.get(player.getName());
        if (session != null && session.getState() == DGState.PLAYING) {
            if (punishHurt != 0) {
                session.sendStrength(a, DGChannel.A);
                session.sendStrength(b, DGChannel.B);
                session.sendWave(punishHurt, DGChannel.BOTH);
            }
        } else {
            if (multiply) {
                event.setDamage(event.getDamage() * 2);
                player.sendMessage("请使用/dgmap命令完成郊狼绑定，避免受到双倍伤害！");
            }
        }
    }

    private static final Map<String, Long> lastNagTime = new HashMap<>();

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        DGSession session = sessions.get(event.getPlayer().getName());
        if (session == null || session.getState() != DGState.PLAYING) {
            if (freeze) {
                if (Math.abs(event.getTo().getX() - event.getFrom().getX()) > 0.01 ||
                        Math.abs(event.getTo().getZ() - event.getFrom().getZ()) > 0.01) {
                    event.setCancelled(true);
                    long lastNag = lastNagTime.getOrDefault(event.getPlayer().getName(), 0L);
                    long now = System.currentTimeMillis();
                    if (now - lastNag > 1000) {
                        event.getPlayer().sendMessage("请使用/dgmap命令完成郊狼绑定后方可游玩！");
                        lastNagTime.put(event.getPlayer().getName(), now);
                    }
                }
            }
        }
    }


    private static final Map<String, DGSession> sessions = new HashMap<>();

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        clearMaps(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        DGSession session = sessions.remove(player.getName());
        if (session != null) {
            session.close();
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        ItemStack item = event.getCurrentItem();
        if (item != null && item.getType() == Material.FILLED_MAP) {
            MapMeta meta = (MapMeta) item.getItemMeta();
            if (meta != null && itemName.equals(meta.getDisplayName())) {
                event.setCancelled(true);
                item.setAmount(0);
            }
        }
    }

    @EventHandler
    public void onDrop(ItemSpawnEvent event) {
        ItemStack item = event.getEntity().getItemStack();
        if (item.getType() == Material.FILLED_MAP) {
            MapMeta meta = (MapMeta) item.getItemMeta();
            if (meta != null && itemName.equals(meta.getDisplayName())) {
                event.setCancelled(true);
            }
        }
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        DGSession.endpoint = getConfig().getString("server");
        String punish = getConfig().getString("punishment");
        if ("freeze".equalsIgnoreCase(punish)) {
            freeze = true;
        } else if ("double".equalsIgnoreCase(punish)) {
            multiply = true;
        } else if (!"none".equalsIgnoreCase(punish)) {
            getLogger().warning("未知的惩罚方式 '" + punish + "'，请检查配置文件。");
            getLogger().warning("使用默认惩罚方式 'none'。");
        }
        a = getConfig().getInt("A");
        b = getConfig().getInt("B");
        if (a <= 0 || b <= 0 || a > 200 || b > 200) {
            getLogger().warning("A 和 B 的值必须在 1 到 200 之间，请检查配置文件。");
            getLogger().warning("使用默认值 A=100, B=50。");
            a = 100;
            b = 50;
        }
        punishInteract = getConfig().getInt("shock_interact", 0);
        punishHurt = getConfig().getInt("shock_duration", 0);
        Bukkit.getPluginManager().registerEvents(this, this);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("dgmap.use")) {
            sender.sendMessage("你缺少使用此命令的权限。");
            return true;
        }
        if (!(sender instanceof Player)) {
            sender.sendMessage("仅限玩家执行此命令。");
            return true;
        }
        final Player player = (Player) sender;
        if (player.getInventory().getItemInMainHand().getType() != Material.AIR) {
            if (player.getInventory().getItemInMainHand().getType() != Material.FILLED_MAP) {
                player.sendMessage("请先将主手物品放下再使用此命令。");
                return true;
            }
        }

        DGSession session = sessions.computeIfAbsent(player.getName(), k -> {
            DGSession dgs = new DGSession(a, b);
            DGSharedPool.executorService.submit(() -> {
                dgs.awaitState(DGState.PLAYING);
                if (dgs.getState() == DGState.PLAYING) {
                    player.sendMessage("连接成功辽！好好享受吧欸嘿嘿嘿~");
                    clearMaps(player);
                }
                dgs.awaitState(DGState.CLOSED);
                sessions.remove(player.getName());
                player.sendMessage("连接断开辽...可能是因为强度设置低于服主设置的阈值，或者是网络问题？");
            });
            return dgs;
        });
        if (session.getState() == DGState.WAITING_SERVER) {
            session.awaitState(DGState.WAITING_CLIENT);
        }
        if (session.getState() != DGState.WAITING_CLIENT) {
            player.sendMessage("连接失败，可能是已经连接过设备，或者网络错误？");
            return true;
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
        meta.setDisplayName(itemName);
        map.setItemMeta(meta);
        player.getInventory().setItemInMainHand(map);
        return true;
    }
}
