package me.w2n.w2nsmp.home;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

public record Home(String name, String world, double x, double y, double z, float yaw, float pitch) {
   public Home {
   }

   public Location location() {
      World bukkitWorld = this.world == null ? null : Bukkit.getWorld(this.world);
      return bukkitWorld == null ? null : new Location(bukkitWorld, this.x, this.y, this.z, this.yaw, this.pitch);
   }

   public static Home of(String name, Location location) {
      return new Home(name, location.getWorld().getName(), location.getX(), location.getY(), location.getZ(), location.getYaw(), location.getPitch());
   }

   public String coordinateText() {
      return this.world + " " + Math.round(this.x) + ", " + Math.round(this.y) + ", " + Math.round(this.z);
   }
}
