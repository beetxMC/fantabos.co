package com.matt.forgehax.mods;

import static com.matt.forgehax.Helper.getNetworkManager;

import com.matt.forgehax.asm.events.PacketEvent;
import com.matt.forgehax.events.LocalPlayerUpdateEvent;
import com.matt.forgehax.util.command.Setting;
import com.matt.forgehax.util.mod.Category;
import com.matt.forgehax.util.mod.ToggleMod;
import com.matt.forgehax.util.mod.loader.RegisterMod;

import java.util.Objects;
import java.util.UUID;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.init.Items;
import net.minecraft.network.play.server.SPacketSpawnPlayer;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

@RegisterMod
public class AutoLog extends ToggleMod {
  
  public AutoLog() {
    super(Category.COMBAT, "AutoLog", false, "automatically disconnect");
  }
  
  public final Setting<Integer> threshold =
      getCommandStub()
          .builders()
          .<Integer>newSettingBuilder()
          .name("threshold")
          .description("Health to go down to to disconnect\"")
          .min(0)
          .max(40)
          .defaultTo(0)
          .build();

  public final Setting<Boolean> noTotem =
      getCommandStub()
          .builders()
          .<Boolean>newSettingBuilder()
          .name("NoTotem")
          .description("Only disconnect if not holding a totem")
          .defaultTo(false)
          .build();

  public final Setting<Boolean> disconnectOnNewPlayer =
      getCommandStub()
          .builders()
          .<Boolean>newSettingBuilder()
          .name("NewPlayer")
          .description("Disconnect if a player enters render distance")
          .defaultTo(false)
          .build();

  @Override
  public String getDisplayText() {
    if (disconnectOnNewPlayer.get())
      return (super.getDisplayText() + " [" + TextFormatting.DARK_AQUA + "!" + TextFormatting.RESET + "]");
    return super.getDisplayText();
  }
  
  @SubscribeEvent
  public void onLocalPlayerUpdate(LocalPlayerUpdateEvent event) {
    if (MC.player != null) {
      int health = (int) (MC.player.getHealth() + MC.player.getAbsorptionAmount());
      if (health <= threshold.get()
          && (!noTotem.getAsBoolean()
              || !((MC.player.getHeldItemOffhand().getItem() == Items.TOTEM_OF_UNDYING)
                   || MC.player.getHeldItemMainhand().getItem() == Items.TOTEM_OF_UNDYING))) {
        AutoReconnectMod.hasAutoLogged = true;
        Objects.requireNonNull(getNetworkManager())
            .closeChannel(new TextComponentString("Health too low (" + health + ")"));
        disable(true);
      }
    }
  }
  
  @SubscribeEvent
  public void onPacketRecieved(PacketEvent.Incoming.Pre event) {
    if (MC.getConnection() == null) return;
    if (event.getPacket() instanceof SPacketSpawnPlayer) {
      if (disconnectOnNewPlayer.getAsBoolean()) {
        AutoReconnectMod.hasAutoLogged = true; // don't automatically reconnect
        String name = "unknown";
        UUID id = ((SPacketSpawnPlayer) event.getPacket()).getUniqueId();
        if (id != null) {
          name = id.toString();
          NetworkPlayerInfo info = MC.getConnection().getPlayerInfo(id);
          if (info != null) name = info.getGameProfile().getName();
        }
        Objects.requireNonNull(getNetworkManager())
            .closeChannel(new TextComponentString(name + " entered render distance"));
        disable(true);
      }
    }
  }
}
