package su.nightexpress.sunlight.mc_1_21;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundPlayerChatPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.server.dedicated.DedicatedPlayerList;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.inventory.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import org.bukkit.Bukkit;
import org.bukkit.ExplosionResult;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.craftbukkit.v1_21_R1.CraftServer;
import org.bukkit.craftbukkit.v1_21_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_21_R1.entity.CraftFallingBlock;
import org.bukkit.craftbukkit.v1_21_R1.entity.CraftPlayer;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.inventory.Inventory;
import org.jetbrains.annotations.NotNull;
import su.nightexpress.nightcore.util.Reflex;
import su.nightexpress.sunlight.mc_1_21.container.PlayerEnderChest;
import su.nightexpress.sunlight.mc_1_21.container.PlayerInventory;
import su.nightexpress.sunlight.nms.SunNMS;

import java.lang.reflect.Method;
import java.util.UUID;

public class MC_1_21 implements SunNMS {

    private static final Method SET_GAME_MODE = Reflex.getMethod(ServerPlayerGameMode.class, "a", GameType.class, GameType.class);

    @Override
    @SuppressWarnings("UnstableApiUsage")
    public boolean canDestroyBlocks(@NotNull EntityExplodeEvent event) {
        return this.canDestroyBlocks(event.getExplosionResult());
    }

    @Override
    @SuppressWarnings("UnstableApiUsage")
    public boolean canDestroyBlocks(@NotNull BlockExplodeEvent event) {
        return this.canDestroyBlocks(event.getExplosionResult());
    }

    @SuppressWarnings("UnstableApiUsage")
    private boolean canDestroyBlocks(@NotNull ExplosionResult result) {
        return result == ExplosionResult.DESTROY || result == ExplosionResult.DESTROY_WITH_DECAY;
    }

    @Override
    public void dropFallingContent(@NotNull FallingBlock fallingBlock) {
        CraftFallingBlock craft = (CraftFallingBlock) fallingBlock;
        craft.getHandle().spawnAtLocation(craft.getHandle().getBlockState().getBlock());
    }

    @NotNull
    public Object fineChatPacket(@NotNull Object packet) {
        ClientboundPlayerChatPacket chatPacket = (ClientboundPlayerChatPacket) packet;
        Component component = chatPacket.unsignedContent() == null ? Component.literal(chatPacket.body().content()) : chatPacket.unsignedContent();

        CraftServer server = (CraftServer) Bukkit.getServer();
        Registry<ChatType> chatTypeRegistry = server.getServer().registryAccess().registryOrThrow(Registries.CHAT_TYPE);

        Holder<ChatType> holder = chatPacket.chatType().chatType();
        ChatType type = chatTypeRegistry.stream().filter(has -> chatTypeRegistry.getId(has) == chatTypeRegistry.getId(holder.value())).findFirst().orElse(null);
        if (type == null) return packet;

        ChatType.Bound decorator = new ChatType.Bound(holder, chatPacket.chatType().name(), chatPacket.chatType().targetName());
        component = decorator.decorate(component);

        return new ClientboundSystemChatPacket(component, false);
    }

    @Override
    @NotNull
    public org.bukkit.entity.Player loadPlayerData(@NotNull UUID id, @NotNull String name) {
        CraftServer craftServer = (CraftServer) Bukkit.getServer();
        DedicatedServer server = craftServer.getServer();
        DedicatedPlayerList playerList = craftServer.getHandle();
        ServerLevel level = server.getLevel(Level.OVERWORLD);
        if (level == null) throw new IllegalStateException("Server level is null");

        GameProfile profile = new GameProfile(id, name);
        ServerPlayer serverPlayer = new ServerPlayer(server, level, profile, ClientInformation.createDefault()); // GameMode reset
        CompoundTag tag = playerList.playerIo.load(serverPlayer).orElse(new CompoundTag());
        serverPlayer.loadGameTypes(tag); // Save GameMode on load data

        return serverPlayer.getBukkitEntity();
    }

    @Override
    public void setGameMode(@NotNull Player player, @NotNull GameMode mode) {
        CraftPlayer craftPlayer = (CraftPlayer) player;
        ServerPlayer serverPlayer = craftPlayer.getHandle();
        //serverPlayer.gameMode.changeGameModeForPlayer(GameType.byName(mode.name().toLowerCase()));
        //craftPlayer.saveData();

        GameType gameType = GameType.byName(mode.name().toLowerCase());
        GameType previous = serverPlayer.gameMode.getPreviousGameModeForPlayer();

        Reflex.invokeMethod(SET_GAME_MODE, serverPlayer.gameMode, gameType, previous);
        craftPlayer.saveData();
    }

    @Override
    public void teleport(@NotNull Player player, @NotNull Location location) {
        CraftPlayer craftPlayer = (CraftPlayer) player;
        ServerPlayer serverPlayer = craftPlayer.getHandle();
        serverPlayer.setPosRaw(location.getX(), location.getY(), location.getZ());
        if (player.getWorld() != location.getWorld() && location.getWorld() != null) {
            CraftWorld craftWorld = (CraftWorld) location.getWorld();
            //serverPlayer.level = craftWorld.getHandle();
            serverPlayer.setServerLevel(craftWorld.getHandle());
        }
        craftPlayer.saveData();
    }

    @Override
    @NotNull
    public Inventory getPlayerEnderChest(@NotNull Player player) {
        return new PlayerEnderChest((CraftPlayer) player).getInventory();
    }

    @Override
    @NotNull
    public Inventory getPlayerInventory(@NotNull Player player) {
        return new PlayerInventory((CraftPlayer) player).getInventory();
    }

    @Override
    public void openAnvil(@NotNull Player player) {
        player.openInventory(this.createContainer(MenuType.ANVIL, player).getBukkitView());
    }

    @Override
    public void openEnchanting(@NotNull Player player) {
        player.openInventory(this.createContainer(MenuType.ENCHANTMENT, player).getBukkitView());
    }

    @Override
    public void openGrindstone(@NotNull Player player) {
        player.openInventory(this.createContainer(MenuType.GRINDSTONE, player).getBukkitView());
    }

    @Override
    public void openLoom(@NotNull Player player) {
        player.openInventory(this.createContainer(MenuType.LOOM, player).getBukkitView());
    }

    @Override
    public void openSmithing(@NotNull Player player) {
        player.openInventory(this.createContainer(MenuType.SMITHING, player).getBukkitView());
    }

    @Override
    public void openCartography(@NotNull Player player) {
        player.openInventory(this.createContainer(MenuType.CARTOGRAPHY_TABLE, player).getBukkitView());
    }

    @Override
    public void openStonecutter(@NotNull Player player) {
        player.openInventory(this.createContainer(MenuType.STONECUTTER, player).getBukkitView());
    }

    @Override
    public void openContainer(@NotNull Player player, @NotNull su.nightexpress.sunlight.api.MenuType menuType) {
        switch (menuType) {
            case STONECUTTER -> openStonecutter(player);
            case GRINDSTONE -> openGrindstone(player);
            case SMITHING -> openSmithing(player);
            case LOOM -> openLoom(player);
            case ENCHANTMENT -> openEnchanting(player);
            case CRAFTING -> player.openWorkbench(null, true);
            case ANVIL -> openAnvil(player);
            case CARTOGRAPHY -> openCartography(player);
        }
    }

    @NotNull
    private <T extends AbstractContainerMenu> AbstractContainerMenu createContainer(@NotNull MenuType<T> type, @NotNull Player player) {
        CraftPlayer craftPlayer = (CraftPlayer) player;
        ServerPlayer nmsPlayer = craftPlayer.getHandle();
        int contId = nmsPlayer.nextContainerCounter();
        ContainerLevelAccess access = ContainerLevelAccess.create(nmsPlayer.level(), nmsPlayer.blockPosition());
        net.minecraft.world.entity.player.Inventory inventory = nmsPlayer.getInventory();

        AbstractContainerMenu menu;
        if (type == MenuType.ENCHANTMENT) {
            menu = new EnchantmentMenu(contId, inventory, access);
        }
        else if (type == MenuType.ANVIL) {
            menu = new AnvilMenu(contId, inventory, access);
        }
        else if (type == MenuType.LOOM) {
            menu = new LoomMenu(contId, inventory, access);
        }
        else if (type == MenuType.GRINDSTONE) {
            menu = new GrindstoneMenu(contId, inventory, access);
        }
        else if (type == MenuType.SMITHING) {
            menu = new SmithingMenu(contId, inventory, access);
        }
        else if (type == MenuType.CARTOGRAPHY_TABLE) {
            menu = new CartographyTableMenu(contId, inventory, access);
        }
        else if (type == MenuType.STONECUTTER) {
            menu = new StonecutterMenu(contId, inventory, access);
        }
        else throw new UnsupportedOperationException("Container type not supported!");

        menu.checkReachable = false;
        return menu;
    }
}
