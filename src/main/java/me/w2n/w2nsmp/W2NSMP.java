package me.w2n.w2nsmp;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import me.w2n.w2nsmp.auction.AuctionManager;
import me.w2n.w2nsmp.bounty.BountyService;
import me.w2n.w2nsmp.combat.CombatService;
import me.w2n.w2nsmp.config.ConfigManager;
import me.w2n.w2nsmp.config.GuiConfigs;
import me.w2n.w2nsmp.config.Messages;
import me.w2n.w2nsmp.economy.DynamicEconomy;
import me.w2n.w2nsmp.economy.EconomyManager;
import me.w2n.w2nsmp.fishing.AutoFishService;
import me.w2n.w2nsmp.fishing.FishingService;
import me.w2n.w2nsmp.gear.GearService;
import me.w2n.w2nsmp.gui.AuctionMenu;
import me.w2n.w2nsmp.gui.HomeMenu;
import me.w2n.w2nsmp.gui.ProfileMenu;
import me.w2n.w2nsmp.gui.AutoFishMenu;
import me.w2n.w2nsmp.gui.FishMenu;
import me.w2n.w2nsmp.gui.RodMenu;
import me.w2n.w2nsmp.gui.SellMenu;
import me.w2n.w2nsmp.gui.SettingsMenu;
import me.w2n.w2nsmp.gui.SkillMenu;
import me.w2n.w2nsmp.home.HomeManager;
import me.w2n.w2nsmp.hook.HookManager;
import me.w2n.w2nsmp.manager.CommandManager;
import me.w2n.w2nsmp.manager.ListenerManager;
import me.w2n.w2nsmp.nametag.NametagService;
import me.w2n.w2nsmp.player.NightVisionService;
import me.w2n.w2nsmp.player.PlayerSettingsService;
import me.w2n.w2nsmp.rtp.RtpService;
import me.w2n.w2nsmp.scoreboard.ScoreboardService;
import me.w2n.w2nsmp.sell.SellManager;
import me.w2n.w2nsmp.skill.SkillService;
import me.w2n.w2nsmp.stats.StatisticsService;
import me.w2n.w2nsmp.teleport.TeleportService;
import me.w2n.w2nsmp.teleport.TpaService;
import me.w2n.w2nsmp.utility.CompatAudit;
import me.w2n.w2nsmp.utility.EnvironmentReport;
import me.w2n.w2nsmp.utility.GuiSounds;
import me.w2n.w2nsmp.worth.WorthService;
import org.bukkit.plugin.java.JavaPlugin;

public final class W2NSMP extends JavaPlugin {
   private static W2NSMP instance;
   private ConfigManager configManager;
   private HookManager hookManager;
   private EconomyManager economyManager;
   private SellManager sellManager;
   private HomeManager homeManager;
   private TeleportService teleportService;
   private RtpService rtpService;
   private CombatService combatService;
   private WorthService worthService;
   private AuctionManager auctionManager;
   private StatisticsService statisticsService;
   private ScoreboardService scoreboardService;
   private PlayerSettingsService settingsService;
   private NightVisionService nightVisionService;
   private SkillService skillService;
   private GearService gearService;
   private FishingService fishingService;
   private AutoFishService autoFishService;
   private NametagService nametagService;
   private TpaService tpaService;
   private BountyService bountyService;
   private DynamicEconomy dynamicEconomy;
   private GuiConfigs guiConfigs;
   private GuiSounds guiSounds;
   private CompatAudit compatAudit;
   private ListenerManager listenerManager;
   private CommandManager commandManager;

   public W2NSMP() {
   }

   public static W2NSMP get() {
      return instance;
   }

   public void onEnable() {
      instance = this;
      long startedAt = System.currentTimeMillis();
      this.getLogger().info("Loading W2NSMP...");
      this.getLogger()
         .info("Target Paper API " + EnvironmentReport.apiTarget() + " | Java " + Runtime.version().feature() + " | Server " + EnvironmentReport.serverName());
      this.configManager = new ConfigManager(this);
      this.hookManager = new HookManager(this);
      this.hookManager.registerAll();
      this.economyManager = new EconomyManager(this, this.configManager, this.hookManager.vault().provider());
      this.sellManager = new SellManager(this);
      this.sellManager.prices().load();
      this.teleportService = new TeleportService(this);
      this.homeManager = new HomeManager(this);
      this.homeManager.load();
      this.rtpService = new RtpService(this);
      this.combatService = new CombatService(this);
      this.auctionManager = new AuctionManager(this);
      this.auctionManager.load();
      this.getLogger()
         .info(
            "Auction: "
               + this.auctionManager.listingCount()
               + " listing aktif & "
               + this.auctionManager.mailboxTotal()
               + " item di kotak dimuat dari auctions.yml."
         );
      this.worthService = new WorthService(this);
      this.worthService.load();
      this.statisticsService = new StatisticsService(this);
      this.statisticsService.load();
      this.statisticsService.startTasks();
      this.scoreboardService = new ScoreboardService(this);
      this.scoreboardService.load();
      this.scoreboardService.startTasks();
      this.guiConfigs = new GuiConfigs(this);
      this.guiConfigs.load();
      this.guiSounds = new GuiSounds(this);
      this.settingsService = new PlayerSettingsService(this);
      this.settingsService.load();
      this.skillService = new SkillService(this);
      this.skillService.load();
      this.skillService.startTasks();
      this.nightVisionService = new NightVisionService(this);
      this.nightVisionService.reload();
      this.gearService = new GearService(this);
      this.gearService.reload();
      this.fishingService = new FishingService(this);
      this.fishingService.reload();
      this.autoFishService = new AutoFishService(this);
      this.autoFishService.reload();
      this.nametagService = new NametagService(this);
      this.nametagService.load();
      this.nametagService.startTasks();
      this.tpaService = new TpaService(this);
      this.tpaService.load();
      this.bountyService = new BountyService(this);
      this.bountyService.load();
      this.dynamicEconomy = new DynamicEconomy(this);
      this.dynamicEconomy.load();
      this.compatAudit = new CompatAudit(this);
      this.listenerManager = new ListenerManager(this);
      this.listenerManager.register();
      this.commandManager = new CommandManager(this);
      this.commandManager.register();
      this.getLogger().info(EnvironmentReport.softDependencySummary());
      if (this.configManager.compatibilityReportOnStartup()) {
         this.getLogger().info("Kompatibilitas Bedrock: " + this.hookManager.bedrock().summary());

         for (String line : this.compatAudit.designLines()) {
            this.getLogger().info(line);
         }
      }

      this.logCommandOwnership();
      this.getLogger().info("W2NSMP berhasil diaktifkan!");
      this.getLogger().info("Startup selesai dalam " + (System.currentTimeMillis() - startedAt) + " ms.");
   }

   private void logCommandOwnership() {
      Map<String, String> labels = this.commandManager.claimedLabels();
      if (labels.isEmpty()) {
         this.getLogger()
            .info(
               "Command: tidak ada label polos plugin lain yang diambil alih (mode "
                  + this.configManager.plainLabelMode().name().toLowerCase(Locale.ROOT)
                  + ")."
            );
      } else {
         this.getLogger()
            .info(
               "Command: "
                  + labels.size()
                  + " label polos diambil alih dari plugin lain ("
                  + this.configManager.plainLabelMode().name().toLowerCase(Locale.ROOT)
                  + ")."
            );

         for (String owner : this.commandManager.pluginsLosingLabels()) {
            List<String> taken = this.commandManager.labelsTakenFrom(owner);
            this.getLogger().info("Command: dari " + owner + " -> /" + String.join(", /", taken));
            if (owner.toLowerCase(Locale.ROOT).contains("essential")) {
               this.getLogger()
                  .info("Command: agar tidak dobel, tambahkan ini ke \"disabled-commands\" di plugins/Essentials/config.yml lalu jalankan /essentials reload:");
               this.getLogger().info("Command:   disabled-commands: [" + String.join(", ", taken) + "]");
            }
         }

         this.getLogger().info("Command: detail & bantuan: /w2nsmp commands");
      }
   }

   public void onDisable() {
      try {
         SellMenu.closeAll(this);
      } catch (RuntimeException exception) {
         this.getLogger().warning("Gagal menutup GUI sell saat disable: " + exception);
      }

      try {
         HomeMenu.closeAll(this);
      } catch (RuntimeException exception) {
         this.getLogger().warning("Gagal menutup GUI home saat disable: " + exception);
      }

      try {
         AuctionMenu.closeAll(this);
      } catch (RuntimeException exception) {
         this.getLogger().warning("Gagal menutup GUI auction saat disable: " + exception);
      }

      try {
         ProfileMenu.closeAll(this);
         SettingsMenu.closeAll(this);
         SkillMenu.closeAll(this);
         RodMenu.closeAll(this);
         FishMenu.closeAll(this);
         AutoFishMenu.closeAll(this);
      } catch (RuntimeException exception) {
         this.getLogger().warning("Gagal menutup GUI profile/setting saat disable: " + exception);
      }

      if (this.teleportService != null) {
         this.teleportService.cancelAll();
         this.teleportService = null;
      }

      if (this.rtpService != null) {
         this.rtpService.clearState();
         this.rtpService = null;
      }

      if (this.combatService != null) {
         this.combatService.shutdown();
         this.combatService = null;
      }

      if (this.auctionManager != null) {
         this.auctionManager.shutdown();
         this.auctionManager = null;
      }

      if (this.worthService != null) {
         this.worthService.shutdown();
         this.worthService = null;
      }

      if (this.statisticsService != null) {
         this.statisticsService.shutdown();
         this.statisticsService = null;
      }

      if (this.scoreboardService != null) {
         this.scoreboardService.shutdown();
         this.scoreboardService = null;
      }

      if (this.nightVisionService != null) {
         this.nightVisionService.shutdown();
         this.nightVisionService = null;
      }

      if (this.skillService != null) {
         this.skillService.shutdown();
         this.skillService = null;
      }

      this.gearService = null;

      if (this.autoFishService != null) {
         this.autoFishService.shutdown();
         this.autoFishService = null;
      }

      this.fishingService = null;

      if (this.nametagService != null) {
         this.nametagService.shutdown();
         this.nametagService = null;
      }

      if (this.tpaService != null) {
         this.tpaService.shutdown();
         this.tpaService = null;
      }

      if (this.bountyService != null) {
         this.bountyService.shutdown();
         this.bountyService = null;
      }

      if (this.dynamicEconomy != null) {
         this.dynamicEconomy.shutdown();
         this.dynamicEconomy = null;
      }

      if (this.settingsService != null) {
         this.settingsService.saveNow();
         this.settingsService = null;
      }

      this.guiConfigs = null;
      this.guiSounds = null;
      this.compatAudit = null;
      if (this.homeManager != null) {
         this.homeManager.saveNow();
         this.homeManager = null;
      }

      if (this.commandManager != null) {
         this.commandManager.unregister();
         this.commandManager = null;
      }

      if (this.economyManager != null) {
         this.economyManager.setProvider(null);
         this.economyManager = null;
      }

      this.sellManager = null;
      this.listenerManager = null;
      this.hookManager = null;
      this.configManager = null;
      this.getLogger().info("W2NSMP dinonaktifkan.");
      instance = null;
   }

   public void reloadAll() {
      this.configManager.reload();
      this.hookManager.registerAll();
      this.economyManager.setProvider(this.hookManager.vault().provider());
      this.economyManager.reloadFormat();
      this.sellManager.prices().load();
      if (this.homeManager != null) {
         this.homeManager.reload();
      }

      if (this.combatService != null && !this.configManager.combatEnabled()) {
         this.combatService.clearState();
      }

      if (this.auctionManager != null) {
         this.auctionManager.reload();
      }

      if (this.worthService != null) {
         this.worthService.reload();
      }

      if (this.statisticsService != null) {
         this.statisticsService.reload();
      }

      if (this.scoreboardService != null) {
         this.scoreboardService.reload();
      }

      if (this.guiConfigs != null && this.configManager.guiConfigEnabled()) {
         this.guiConfigs.reload();
      }

      if (this.settingsService != null) {
         this.settingsService.reload();
      }

      if (this.skillService != null) {
         this.skillService.reload();
      }

      if (this.gearService != null) {
         this.gearService.reload();
      }

      if (this.fishingService != null) {
         this.fishingService.reload();
      }

      if (this.autoFishService != null) {
         this.autoFishService.reload();
      }

      if (this.nightVisionService != null) {
         this.nightVisionService.reload();
      }

      if (this.nametagService != null) {
         this.nametagService.reload();
      }

      if (this.tpaService != null) {
         this.tpaService.reload();
      }

      if (this.bountyService != null) {
         this.bountyService.reload();
      }

      if (this.dynamicEconomy != null) {
         this.dynamicEconomy.reload();
      }

      this.getLogger().info("Configuration reloaded.");
   }

   public void debug(String message) {
      if (this.configManager != null && this.configManager.debug()) {
         this.getLogger().info("[debug] " + message);
      }
   }

   public ConfigManager config() {
      return this.configManager;
   }

   public Messages messages() {
      return this.configManager.messages();
   }

   public HomeManager homes() {
      return this.homeManager;
   }

   public TeleportService teleport() {
      return this.teleportService;
   }

   public RtpService rtp() {
      return this.rtpService;
   }

   public CombatService combat() {
      return this.combatService;
   }

   public AuctionManager auction() {
      return this.auctionManager;
   }

   public WorthService worth() {
      return this.worthService;
   }

   public StatisticsService stats() {
      return this.statisticsService;
   }

   public ScoreboardService scoreboard() {
      return this.scoreboardService;
   }

   public PlayerSettingsService settings() {
      return this.settingsService;
   }

   public NametagService nametag() {
      return this.nametagService;
   }

   /**
    * Layanan skill (/skill). Bisa {@code null} sebelum onEnable selesai atau sesudah onDisable,
    * jadi pemanggil wajib memeriksa null seperti pada layanan lain di kelas ini.
    */
   public SkillService skills() {
      return this.skillService;
   }

   /** Riwayat & perk gear (v1.4.0). Bisa {@code null} di luar masa hidup plugin. */
   public GearService gear() {
      return this.gearService;
   }

   /** Custom fishing & rod (v1.4.0). Bisa {@code null} di luar masa hidup plugin. */
   public FishingService fishing() {
      return this.fishingService;
   }

   /** Layanan autofishing (v1.4.1). Bisa {@code null} di luar masa hidup plugin. */
   public AutoFishService autoFish() {
      return this.autoFishService;
   }

   /** Night Vision /setting (v1.5.1). Bisa {@code null} di luar masa hidup plugin. */
   public NightVisionService nightVision() {
      return this.nightVisionService;
   }

   public GuiConfigs guiConfigs() {
      return this.guiConfigs;
   }

   public GuiSounds guiSounds() {
      return this.guiSounds;
   }

   public TpaService tpa() {
      return this.tpaService;
   }

   public BountyService bounty() {
      return this.bountyService;
   }

   public DynamicEconomy dynamic() {
      return this.dynamicEconomy;
   }

   public CompatAudit compat() {
      return this.compatAudit;
   }

   public HookManager hooks() {
      return this.hookManager;
   }

   public EconomyManager economy() {
      return this.economyManager;
   }

   public SellManager sell() {
      return this.sellManager;
   }

   public CommandManager commandManager() {
      return this.commandManager;
   }
}
