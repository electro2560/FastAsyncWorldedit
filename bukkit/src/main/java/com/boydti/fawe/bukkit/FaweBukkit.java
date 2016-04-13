package com.boydti.fawe.bukkit;

import com.boydti.fawe.Fawe;
import com.boydti.fawe.FaweAPI;
import com.boydti.fawe.IFawe;
import com.boydti.fawe.bukkit.regions.FactionsFeature;
import com.boydti.fawe.bukkit.regions.FactionsUUIDFeature;
import com.boydti.fawe.bukkit.regions.GriefPreventionFeature;
import com.boydti.fawe.bukkit.regions.PlotMeFeature;
import com.boydti.fawe.bukkit.regions.PreciousStonesFeature;
import com.boydti.fawe.bukkit.regions.ResidenceFeature;
import com.boydti.fawe.bukkit.regions.TownyFeature;
import com.boydti.fawe.bukkit.regions.Worldguard;
import com.boydti.fawe.bukkit.v1_8.BukkitEditSessionWrapper_1_8;
import com.boydti.fawe.bukkit.v1_8.BukkitQueue_1_8;
import com.boydti.fawe.bukkit.v1_9.BukkitQueue_1_9;
import com.boydti.fawe.object.EditSessionWrapper;
import com.boydti.fawe.object.FaweCommand;
import com.boydti.fawe.object.FawePlayer;
import com.boydti.fawe.regions.FaweMaskManager;
import com.boydti.fawe.util.FaweQueue;
import com.boydti.fawe.util.StringMan;
import com.boydti.fawe.util.TaskManager;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.bukkit.WorldEditPlugin;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public class FaweBukkit extends JavaPlugin implements IFawe, Listener {

    private VaultUtil vault;
    private WorldEditPlugin worldedit;

    public VaultUtil getVault() {
        return this.vault;
    }

    public WorldEditPlugin getWorldEditPlugin() {
        if (this.worldedit == null) {
            this.worldedit = (WorldEditPlugin) Bukkit.getPluginManager().getPlugin("WorldEdit");
        }
        return this.worldedit;
    }

    @Override
    public void onEnable() {
        try {
            Bukkit.getPluginManager().registerEvents(this, this);
            Fawe.set(this);
        } catch (final Throwable e) {
            e.printStackTrace();
            this.getServer().shutdown();
        }
    }

    @Override
    public void debug(final String s) {
        this.getLogger().info(ChatColor.translateAlternateColorCodes('&', s));
    }

    @Override
    public File getDirectory() {
        return this.getDataFolder();
    }

    @Override
    public void setupCommand(final String label, final FaweCommand cmd) {
        this.getCommand(label).setExecutor(new BukkitCommand(cmd));
    }

    @Override
    public FawePlayer<Player> wrap(final Object obj) {
        if (obj.getClass() == String.class) {
            String name = (String) obj;
            FawePlayer existing = Fawe.get().getCachedPlayer(name);
            if (existing != null) {
                return existing;
            }
            return new BukkitPlayer(Bukkit.getPlayer(name));
        } else if (obj instanceof Player) {
            Player player = (Player) obj;
            FawePlayer existing = Fawe.get().getCachedPlayer(player.getName());
            return existing != null ? existing : new BukkitPlayer(player);
        } else {
            return null;
        }
    }

    @Override
    public void startMetrics() {
        Metrics metrics = new Metrics(this);
        metrics.start();
        debug("&6Metrics enabled.");
    }

    @Override
    public Set<FawePlayer> getPlayers() {
        HashSet<FawePlayer> players = new HashSet<>();
        for (Player player : Bukkit.getServer().getOnlinePlayers()) {
            players.add(wrap(player));
        }
        return players;
    }

    /**
     * Kinda a really messy class I just copied over from an old project<br>
     *  - Still works, so cbf cleaning it up<br>
     *  - Completely optional to have this class enabled since things get cancelled further down anyway<br>
     *  - Useful since it informs the player why an edit changed no blocks etc.<br>
     *  - Predicts the number of blocks changed and cancels the edit if it's too large<br>
     *  - Predicts where the edit will effect and cancels it if it's outside a region<br>
     *  - Restricts the brush iteration limit<br>
     */
    @Override
    public void setupWEListener() {
        this.getServer().getPluginManager().registerEvents(new WEListener(), this);
    }

    /**
     * Vault isn't required, but used for setting player permissions (WorldEdit bypass)
     * @return
     */
    @Override
    public void setupVault() {
        try {
            this.vault = new VaultUtil();
        } catch (final Throwable e) {
            this.debug("&cPlease install vault!");
        }
    }

    /**
     * The task manager handles sync/async tasks
     */
    @Override
    public TaskManager getTaskManager() {
        return new BukkitTaskMan(this);
    }

    private int[] version;

    @Override
    public int[] getVersion() {
        if (this.version == null) {
            try {
                this.version = new int[3];
                final String[] split = Bukkit.getBukkitVersion().split("-")[0].split("\\.");
                this.version[0] = Integer.parseInt(split[0]);
                this.version[1] = Integer.parseInt(split[1]);
                if (split.length == 3) {
                    this.version[2] = Integer.parseInt(split[2]);
                }
            } catch (final NumberFormatException e) {
                e.printStackTrace();
                Fawe.debug(StringMan.getString(Bukkit.getBukkitVersion()));
                Fawe.debug(StringMan.getString(Bukkit.getBukkitVersion().split("-")[0].split("\\.")));
                return new int[] { Integer.MAX_VALUE, 0, 0 };
            }
        }
        return this.version;
    }

    /**
     * The FaweQueue is a core part of block placement<br>
     *  - The queue returned here is used in the SetQueue class (SetQueue handles the implementation specific queue)<br>
     *  - Block changes are grouped by chunk (as it's more efficient for lighting/packet sending)<br>
     *  - The FaweQueue returned here will provide the wrapper around the chunk object (FaweChunk)<br>
     *  - When a block change is requested, the SetQueue will first check if the chunk exists in the queue, or it will create and add it<br>
     */
    @Override
    public FaweQueue getNewQueue(String world) {
        if (FaweAPI.checkVersion(this.getVersion(), 1, 9, 0)) {
            try {
                return new BukkitQueue_1_9(world);
            } catch (final Throwable e) {
                e.printStackTrace();
            }
        }
        return new BukkitQueue_1_8(world);
    }

    /**
     * The EditSessionWrapper should have the same functionality as the normal EditSessionWrapper but with some optimizations
     */
    @Override
    public EditSessionWrapper getEditSessionWrapper(final EditSession session) {
        return new BukkitEditSessionWrapper_1_8(session);
    }

    /**
     * A mask manager handles region restrictions e.g. PlotSquared plots / WorldGuard regions
     */
    @Override
    public Collection<FaweMaskManager> getMaskManagers() {
        final Plugin worldguardPlugin = Bukkit.getServer().getPluginManager().getPlugin("WorldGuard");
        final ArrayList<FaweMaskManager> managers = new ArrayList<>();
        if ((worldguardPlugin != null) && worldguardPlugin.isEnabled()) {
            try {
                managers.add(new Worldguard(worldguardPlugin, this));
                Fawe.debug("Plugin 'WorldGuard' found. Using it now.");
            } catch (final Throwable e) {
                e.printStackTrace();
            }
        } else {
            Fawe.debug("Plugin 'WorldGuard' not found. Worldguard features disabled.");
        }
        final Plugin plotmePlugin = Bukkit.getServer().getPluginManager().getPlugin("PlotMe");
        if ((plotmePlugin != null) && plotmePlugin.isEnabled()) {
            try {
                managers.add(new PlotMeFeature(plotmePlugin, this));
                Fawe.debug("Plugin 'PlotMe' found. Using it now.");
            } catch (final Throwable e) {
                e.printStackTrace();
            }
        } else {
            Fawe.debug("Plugin 'PlotMe' not found. PlotMe features disabled.");
        }
        final Plugin townyPlugin = Bukkit.getServer().getPluginManager().getPlugin("Towny");
        if ((townyPlugin != null) && townyPlugin.isEnabled()) {
            try {
                managers.add(new TownyFeature(townyPlugin, this));
                Fawe.debug("Plugin 'Towny' found. Using it now.");
            } catch (final Throwable e) {
                e.printStackTrace();
            }
        } else {
            Fawe.debug("Plugin 'Towny' not found. Towny features disabled.");
        }
        final Plugin factionsPlugin = Bukkit.getServer().getPluginManager().getPlugin("Factions");
        if ((factionsPlugin != null) && factionsPlugin.isEnabled()) {
            try {
                managers.add(new FactionsFeature(factionsPlugin, this));
                Fawe.debug("Plugin 'Factions' found. Using it now.");
            } catch (final Throwable e) {
                managers.add(new FactionsUUIDFeature(factionsPlugin, this));
                Fawe.debug("Plugin 'FactionsUUID' found. Using it now.");
            }
        } else {
            Fawe.debug("Plugin 'Factions' not found. Factions features disabled.");
        }
        final Plugin residencePlugin = Bukkit.getServer().getPluginManager().getPlugin("Residence");
        if ((residencePlugin != null) && residencePlugin.isEnabled()) {
            try {
                managers.add(new ResidenceFeature(residencePlugin, this));
                Fawe.debug("Plugin 'Residence' found. Using it now.");
            } catch (final Throwable e) {
                e.printStackTrace();
            }
        } else {
            Fawe.debug("Plugin 'Residence' not found. Factions features disabled.");
        }
        final Plugin griefpreventionPlugin = Bukkit.getServer().getPluginManager().getPlugin("GriefPrevention");
        if ((griefpreventionPlugin != null) && griefpreventionPlugin.isEnabled()) {
            try {
                managers.add(new GriefPreventionFeature(griefpreventionPlugin, this));
                Fawe.debug("Plugin 'GriefPrevention' found. Using it now.");
            } catch (final Throwable e) {
                e.printStackTrace();
            }
        } else {
            Fawe.debug("Plugin 'GriefPrevention' not found. GriefPrevention features disabled.");
        }
        final Plugin preciousstonesPlugin = Bukkit.getServer().getPluginManager().getPlugin("PreciousStones");
        if ((preciousstonesPlugin != null) && preciousstonesPlugin.isEnabled()) {
            try {
                managers.add(new PreciousStonesFeature(preciousstonesPlugin, this));
                Fawe.debug("Plugin 'PreciousStones' found. Using it now.");
            } catch (final Throwable e) {
                e.printStackTrace();
            }
        } else {
            Fawe.debug("Plugin 'PreciousStones' not found. PreciousStones features disabled.");
        }
        return managers;
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Fawe.get().unregister(event.getPlayer().getName());
    }
}
