package dg.spigot.api;

import dg.DGSession;
import dg.enums.DGChannel;
import dg.spigot.SpigotMain;
import dg.spigot.command.MapCommand;
import org.bukkit.entity.Player;

public class SpigotApi {
    /**
     * Checks if the player is connected to a DGSession and is in the PLAYING state.
     *
     * @param player The player to check.
     * @return true if the player is connected and in the PLAYING state, false otherwise.
     */
    public static boolean isPlayerConnected(Player player) {
        DGSession dgs = SpigotMain.sessions.get(player.getName());
        return dgs != null && dgs.getState() == dg.enums.DGState.PLAYING;
    }

    /**
     * Sends a shockwave to the player with specified strengths and duration.
     *
     * @param player    The player to send the shockwave to.
     * @param strengthA The strength of channel A.
     * @param strengthB The strength of channel B.
     * @param duration  The duration of the shockwave, in seconds.
     * @return true if the shockwave was sent successfully, false otherwise.
     */
    public static boolean sendShockwave(Player player, int strengthA, int strengthB, int duration) {
        DGSession dgs = SpigotMain.sessions.get(player.getName());
        if (dgs != null && dgs.getState() == dg.enums.DGState.PLAYING) {
            if (strengthA == 0) {
                if (strengthB == 0) {
                    return false;
                } else {
                    dgs.sendStrength(strengthB, DGChannel.B);
                    dgs.sendWave(duration, DGChannel.B);
                    return true;
                }
            } else {
                if (strengthB == 0) {
                    dgs.sendStrength(strengthA, DGChannel.A);
                    dgs.sendWave(duration, DGChannel.A);
                    return true;
                } else {
                    dgs.sendStrength(strengthA, DGChannel.A);
                    dgs.sendStrength(strengthB, DGChannel.B);
                    dgs.sendWave(duration, DGChannel.BOTH);
                    return true;
                }
            }
        } else {
            return false;
        }
    }

    /**
     * Make a player bind to the DG device via a QR code.
     *
     * @param player The player to bind.
     */
    public static void doBind(Player player) {
        MapCommand.handle(player);
    }
}
