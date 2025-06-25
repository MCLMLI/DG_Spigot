package dg.reflect;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapView;

import java.lang.reflect.Method;

public class ReflectUtils {
    public static final Material mapMaterial;
    private static final boolean isLegacy;

    static {
        Material m;
        boolean l = false;
        try {
            m = (Material) Class.forName("org.bukkit.Material").getField("FILLED_MAP").get(null);
        } catch (Exception ex1) {
            try {
                m = (Material) Class.forName("org.bukkit.Material").getField("MAP").get(null);
                l = true;
            } catch (Exception ex2) {
                throw new IllegalStateException("Failed to initialize ReflectUtils: " + ex2.getMessage(), ex2);
            }
        }
        mapMaterial = m;
        isLegacy = l;
    }

    private static final Method setMapView;

    static {
        if (!isLegacy) {
            try {
                setMapView = new ItemStack(mapMaterial, 1).getItemMeta().getClass().getMethod("setMapView", MapView.class);
            } catch (Exception e) {
                throw new IllegalStateException("Failed to find setMapView method: " + e.getMessage(), e);
            }
        } else {
            setMapView = null; // Legacy versions do not have this method
        }
    }

    public static void processMapView1(MapMeta meta, MapView view) {
        if (!isLegacy) {
            try {
                setMapView.invoke(meta, view);
            } catch (Exception e) {
                throw new IllegalStateException("Failed to process MapView: " + e.getMessage(), e);
            }
        }
    }

    private static final Method getId;

    static {
        if (isLegacy) {
            try {
                getId = Bukkit.createMap(Bukkit.getWorlds().get(0)).getClass().getMethod("getId");
            } catch (Exception e) {
                throw new IllegalStateException("Failed to find getId method: " + e.getMessage(), e);
            }
        } else {
            getId = null; // Non-legacy versions do not have this method
        }
    }

    public static void processMapView2(ItemStack item, MapView view) {
        if (isLegacy) {
            try {
                item.setDurability((short) getId.invoke(view));
            } catch (Exception e) {
                throw new IllegalStateException("Failed to process MapView: " + e.getMessage(), e);
            }
        }
    }


}
