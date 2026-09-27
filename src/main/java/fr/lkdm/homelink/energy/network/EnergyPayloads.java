package fr.lkdm.homelink.energy.network;

import fr.lkdm.homelink.energy.HomeLinkEnergy;
import fr.lkdm.homelink.energy.blockentity.EnergyDeviceBlockEntity;
import fr.lkdm.homelink.energy.homelink.EnergyHomeCore;
import fr.lkdm.homelink.energy.menu.EnergyMenu;
import io.netty.buffer.ByteBuf;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * The only packets of HomeLink Energy, both about HomeNetwork membership. The client never sends
 * an energy value: it can only ask to join or leave a HomeNetwork from the block's open screen,
 * and the server re-checks the screen, distance and HomeCore permissions.
 */
public final class EnergyPayloads {
    public static final int MAX_CHOICES = 32;

    private EnergyPayloads() { }

    public record Choice(UUID id, String name) {
        public static final StreamCodec<ByteBuf, Choice> CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC, Choice::id, ByteBufCodecs.stringUtf8(128), Choice::name, Choice::new);
    }

    /** Server to client: current network of the block and the networks the player may manage. */
    public record Choices(BlockPos pos, String current, List<Choice> choices) implements CustomPacketPayload {
        public static final Type<Choices> TYPE = new Type<>(HomeLinkEnergy.id("network_choices"));
        public static final StreamCodec<ByteBuf, Choices> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Choices::pos, ByteBufCodecs.stringUtf8(128), Choices::current,
                Choice.CODEC.apply(ByteBufCodecs.list(MAX_CHOICES)), Choices::choices, Choices::new);

        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Client to server: attach the open block to a network (empty = detach). */
    public record Bind(BlockPos pos, Optional<UUID> network) implements CustomPacketPayload {
        public static final Type<Bind> TYPE = new Type<>(HomeLinkEnergy.id("bind_network"));
        public static final StreamCodec<ByteBuf, Bind> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Bind::pos, ByteBufCodecs.optional(UUIDUtil.STREAM_CODEC), Bind::network, Bind::new);

        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        registrar.playToServer(Bind.TYPE, Bind.CODEC, EnergyPayloads::bind);
        registrar.playToClient(Choices.TYPE, Choices.CODEC, (payload, context) ->
                fr.lkdm.homelink.energy.client.EnergyClientData.setChoices(payload));
    }

    public static void sendNetworkChoices(ServerPlayer player, EnergyDeviceBlockEntity entity) {
        List<Choice> choices = EnergyHomeCore.manageableNetworks(player).stream().limit(MAX_CHOICES)
                .map(network -> new Choice(network.id(), network.name())).toList();
        PacketDistributor.sendToPlayer(player, new Choices(entity.getBlockPos(), entity.homeNetworkName(), choices));
    }

    /** The request must come from the player's open, still valid screen of that block. */
    private static Optional<EnergyDeviceBlockEntity> openBlock(ServerPlayer player, BlockPos pos) {
        if (!(player.containerMenu instanceof EnergyMenu menu) || !menu.pos().equals(pos) || !menu.stillValid(player)) return Optional.empty();
        return player.serverLevel().getBlockEntity(pos) instanceof EnergyDeviceBlockEntity entity ? Optional.of(entity) : Optional.empty();
    }

    private static void bind(Bind payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        openBlock(player, payload.pos()).ifPresent(entity -> {
            EnergyHomeCore.BindResult result = EnergyHomeCore.bind(player, entity, payload.network());
            boolean ok = result != EnergyHomeCore.BindResult.DENIED && result != EnergyHomeCore.BindResult.UNKNOWN_NETWORK;
            player.displayClientMessage(Component.translatable("message.homelink_energy.network." + result.name().toLowerCase(Locale.ROOT),
                    entity.homeNetworkName()).withStyle(ok ? ChatFormatting.GOLD : ChatFormatting.RED), true);
            sendNetworkChoices(player, entity);
        });
    }
}
