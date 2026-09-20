package com.example.tpamod;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Relative;

import java.util.Set;

/**
 * 四个命令：/tpa、/tpaccept、/tpdeny、/tpacancel。
 *
 * <p>传送使用 teleportTo(ServerLevel, x, y, z, ...) 并<b>显式带上目标所在世界</b>：
 * 初版只传了坐标（teleport(x, y, z, true)），两人处于不同维度时会传到错误位置。</p>
 */
public final class TpaCommand {

    private TpaCommand() {
    }

    /** 在线玩家名补全（按已输入前缀过滤，忽略大小写）。 */
    private static final SuggestionProvider<CommandSourceStack> ONLINE_PLAYERS = (context, builder) -> {
        String remaining = builder.getRemainingLowerCase();
        for (ServerPlayer player : context.getSource().getServer().getPlayerList().getPlayers()) {
            String name = player.getScoreboardName();
            if (name.toLowerCase().startsWith(remaining)) {
                builder.suggest(name);
            }
        }
        return builder.buildFuture();
    };

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("tpa")
                .then(Commands.argument("player", StringArgumentType.word())
                        .suggests(ONLINE_PLAYERS)
                        .executes(TpaCommand::request))
                .executes(context -> {
                    context.getSource().sendFailure(Component.translatable("tpa.usage.tpa"));
                    return 0;
                }));

        dispatcher.register(Commands.literal("tpaccept").executes(TpaCommand::accept));
        dispatcher.register(Commands.literal("tpdeny").executes(TpaCommand::deny));
        dispatcher.register(Commands.literal("tpacancel").executes(TpaCommand::cancel));
    }

    private static int request(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer requester = source.getPlayer();
        if (requester == null) {
            source.sendFailure(Component.translatable("tpa.error.player_only"));
            return 0;
        }

        String targetName = StringArgumentType.getString(context, "player");
        ServerPlayer target = source.getServer().getPlayerList().getPlayerByName(targetName);
        if (target == null) {
            source.sendFailure(Component.translatable("tpa.error.not_online", targetName));
            return 0;
        }
        if (target.getUUID().equals(requester.getUUID())) {
            source.sendFailure(Component.translatable("tpa.error.self"));
            return 0;
        }
        if (TpaRequests.hasPending(requester.getUUID())) {
            source.sendFailure(Component.translatable("tpa.error.already_pending"));
            return 0;
        }

        TpaRequests.put(requester.getUUID(), target.getUUID());

        String requesterName = requester.getScoreboardName();
        String targetDisplayName = target.getScoreboardName();

        source.sendSuccess(() -> Component.translatable("tpa.message.sent", targetDisplayName), false);
        source.sendSuccess(() -> Component.translatable("tpa.message.expires_hint"), false);
        target.sendSystemMessage(Component.translatable("tpa.message.received", requesterName));
        target.sendSystemMessage(Component.translatable("tpa.message.hint"));

        TpaMod.LOGGER.info("[{}] {} 请求传送到 {}", TpaMod.MOD_ID, requesterName, targetDisplayName);
        return 1;
    }

    private static int accept(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer target = source.getPlayer();
        if (target == null) {
            source.sendFailure(Component.translatable("tpa.error.player_only"));
            return 0;
        }

        TpaRequests.Request request = TpaRequests.findIncoming(target.getUUID());
        if (request == null) {
            source.sendFailure(Component.translatable("tpa.error.no_pending"));
            return 0;
        }

        // 先移除请求，避免被重复接受
        TpaRequests.remove(request.requesterId());

        ServerPlayer requester = source.getServer().getPlayerList().getPlayer(request.requesterId());
        if (requester == null) {
            source.sendFailure(Component.translatable("tpa.error.requester_offline"));
            return 0;
        }

        // 显式指定目标所在世界，跨维度也能正确落点
        ServerLevel targetLevel = (ServerLevel) target.level();
        requester.teleportTo(targetLevel, target.getX(), target.getY(), target.getZ(),
                Set.<Relative>of(), target.getYRot(), target.getXRot(), true);

        String requesterName = requester.getScoreboardName();
        String targetName = target.getScoreboardName();

        requester.sendSystemMessage(Component.translatable("tpa.message.accepted_requester", targetName));
        target.sendSystemMessage(Component.translatable("tpa.message.accepted_target", requesterName));

        TpaMod.LOGGER.info("[{}] {} 已传送到 {}", TpaMod.MOD_ID, requesterName, targetName);
        return 1;
    }

    private static int deny(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer target = source.getPlayer();
        if (target == null) {
            source.sendFailure(Component.translatable("tpa.error.player_only"));
            return 0;
        }

        TpaRequests.Request request = TpaRequests.findIncoming(target.getUUID());
        if (request == null) {
            source.sendFailure(Component.translatable("tpa.error.no_pending"));
            return 0;
        }

        TpaRequests.remove(request.requesterId());

        ServerPlayer requester = source.getServer().getPlayerList().getPlayer(request.requesterId());
        if (requester != null) {
            requester.sendSystemMessage(Component.translatable("tpa.message.denied_requester",
                    target.getScoreboardName()));
        }

        source.sendSuccess(() -> Component.translatable("tpa.message.denied"), false);
        return 1;
    }

    private static int cancel(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer requester = source.getPlayer();
        if (requester == null) {
            source.sendFailure(Component.translatable("tpa.error.player_only"));
            return 0;
        }

        TpaRequests.Request request = TpaRequests.remove(requester.getUUID());
        if (request == null) {
            source.sendFailure(Component.translatable("tpa.error.no_pending"));
            return 0;
        }

        ServerPlayer target = source.getServer().getPlayerList().getPlayer(request.targetId());
        if (target != null) {
            target.sendSystemMessage(Component.translatable("tpa.message.cancelled_target",
                    requester.getScoreboardName()));
        }

        source.sendSuccess(() -> Component.translatable("tpa.message.cancelled"), false);
        return 1;
    }
}
