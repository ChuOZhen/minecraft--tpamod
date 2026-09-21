package com.example.tpamod;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Relative;

import java.util.Set;

/**
 * 四个命令：/tpa、/tpaccept [玩家名]、/tpdeny [玩家名]、/tpacancel。
 *
 * <p><b>可点击按钮</b>：请求到达时，目标的聊天栏会收到一行按钮
 * （绿色【同意】/ 红色【拒绝】），请求者自己也会收到一个【取消】按钮。
 * 按钮用原版 {@link ClickEvent.RunCommand} 实现 —— 属于纯服务端能力，客户端无需装模组：
 * 点击时由客户端执行该命令，等价于玩家手输。客户端 {@code Screen#clickCommandAction}
 * 会经 {@code Commands.trimOptionalPrefix} 去掉命令前的 "/"，带与不带都安全。</p>
 *
 * <p><b>按钮为什么要带玩家名</b>：请求表以「请求者」为键，允许多人同时请求同一个人。
 * 若按钮只执行 {@code /tpaccept}，目标玩家点某一条按钮时命令本身无法表达「点的是谁」，
 * 只会命中最先遍历到的那个请求。因此按钮与提示都带上请求者名字，做到点谁就是谁。</p>
 *
 * <p><b>传送</b>使用 teleportTo(ServerLevel, x, y, z, ...) 并<b>显式带上目标所在世界</b>：
 * 初版只传了坐标（teleport(x, y, z, true)），两人处于不同维度时会传到错误位置。</p>
 */
public final class TpaCommand {

    /** 命令返回值：成功 / 失败，避免调用处出现裸的 0/1。 */
    private static final int OK = 1;
    private static final int FAIL = 0;

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

    /** 只补全「正在请求传送给我」的玩家名，避免 /tpaccept 里混入无关玩家。 */
    private static final SuggestionProvider<CommandSourceStack> INCOMING_REQUESTERS = (context, builder) -> {
        ServerPlayer self = context.getSource().getPlayer();
        if (self == null) {
            return builder.buildFuture();
        }
        String remaining = builder.getRemainingLowerCase();
        for (TpaRequests.Request request : TpaRequests.findIncoming(self.getUUID())) {
            ServerPlayer requester = context.getSource().getServer().getPlayerList().getPlayer(request.requesterId());
            if (requester == null) {
                continue;
            }
            String name = requester.getScoreboardName();
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

        dispatcher.register(Commands.literal("tpaccept")
                .executes(context -> decide(context, true, null))
                .then(Commands.argument("player", StringArgumentType.word())
                        .suggests(INCOMING_REQUESTERS)
                        .executes(context -> decide(context, true, StringArgumentType.getString(context, "player")))));

        dispatcher.register(Commands.literal("tpdeny")
                .executes(context -> decide(context, false, null))
                .then(Commands.argument("player", StringArgumentType.word())
                        .suggests(INCOMING_REQUESTERS)
                        .executes(context -> decide(context, false, StringArgumentType.getString(context, "player")))));

        dispatcher.register(Commands.literal("tpacancel").executes(TpaCommand::cancel));
    }

    // ------------------------------------------------------------------
    // 组件构造：可点击按钮
    // ------------------------------------------------------------------

    /**
     * 构造一个可点击按钮。
     *
     * @param labelKey  按钮文案的翻译键，如 {@code tpa.button.accept}
     * @param color     颜色（同意＝绿、拒绝＝红、取消＝gold）
     * @param command   点击后执行的命令；本环境客户端会 trim 掉前导 "/"，故带上更直观
     * @param hoverKey  悬停提示的翻译键
     * @param hoverArgs 悬停提示的格式化参数
     */
    private static MutableComponent button(String labelKey, ChatFormatting color, String command,
                                           String hoverKey, Object... hoverArgs) {
        return Component.translatable(labelKey).withStyle(style -> style
                .withColor(color)
                .withBold(true)
                .withClickEvent(new ClickEvent.RunCommand(command))
                .withHoverEvent(new HoverEvent.ShowText(Component.translatable(hoverKey, hoverArgs))));
    }

    /** 把若干个组件拼成聊天栏里的一行（前缀是换行符）。 */
    private static MutableComponent newLine(MutableComponent... parts) {
        MutableComponent line = Component.literal("\n");
        for (MutableComponent part : parts) {
            line = line.append(part);
        }
        return line;
    }

    // ------------------------------------------------------------------
    // /tpa <玩家名>
    // ------------------------------------------------------------------

    private static int request(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer requester = source.getPlayer();
        if (requester == null) {
            source.sendFailure(Component.translatable("tpa.error.player_only"));
            return FAIL;
        }

        String targetName = StringArgumentType.getString(context, "player");
        ServerPlayer target = source.getServer().getPlayerList().getPlayerByName(targetName);
        if (target == null) {
            source.sendFailure(Component.translatable("tpa.error.not_online", targetName));
            return FAIL;
        }
        if (target.getUUID().equals(requester.getUUID())) {
            source.sendFailure(Component.translatable("tpa.error.self"));
            return FAIL;
        }
        if (TpaRequests.hasPending(requester.getUUID())) {
            source.sendFailure(Component.translatable("tpa.error.already_pending"));
            return FAIL;
        }

        TpaRequests.put(requester.getUUID(), target.getUUID());

        String requesterName = requester.getScoreboardName();
        String targetDisplayName = target.getScoreboardName();
        // 命令参数按 Brigadier 规则转义：玩家名理论上只含 [A-Za-z0-9_]，但不要赌这一条。
        String requesterArg = StringArgumentType.escapeIfRequired(requesterName);

        // 请求者：已发送 + 【取消】按钮
        requester.sendSystemMessage(Component
                .translatable("tpa.message.sent", targetDisplayName, TpaRequests.TIMEOUT_SECONDS)
                .withStyle(ChatFormatting.YELLOW)
                .append(newLine(button("tpa.button.cancel", ChatFormatting.GOLD,
                        "/tpacancel", "tpa.hover.cancel"))));

        // 目标：请求文案 + 【同意】（绿）/【拒绝】（红）按钮
        target.sendSystemMessage(Component
                .translatable("tpa.message.received", requesterName)
                .withStyle(ChatFormatting.YELLOW)
                .append(newLine(
                        button("tpa.button.accept", ChatFormatting.GREEN,
                                "/tpaccept " + requesterArg, "tpa.hover.accept", requesterName),
                        Component.literal("  "),
                        button("tpa.button.deny", ChatFormatting.RED,
                                "/tpdeny " + requesterArg, "tpa.hover.deny", requesterName))));

        TpaMod.LOGGER.info("[{}] {} 请求传送到 {}", TpaMod.MOD_ID, requesterName, targetDisplayName);
        return OK;
    }

    // ------------------------------------------------------------------
    // /tpaccept [玩家名] 与 /tpdeny [玩家名]
    // ------------------------------------------------------------------

    /**
     * 接受或拒绝请求。
     *
     * @param accept        是否接受（否则为拒绝）
     * @param requesterName 指定的请求者名字；为 {@code null} 时表示「未指定」，
     *                      此时仅在只有一个待处理请求的情况下才继续
     */
    private static int decide(CommandContext<CommandSourceStack> context, boolean accept, String requesterName) {
        CommandSourceStack source = context.getSource();
        ServerPlayer target = source.getPlayer();
        if (target == null) {
            source.sendFailure(Component.translatable("tpa.error.player_only"));
            return FAIL;
        }

        TpaRequests.Request request = pickIncoming(source, target, requesterName);
        if (request == null) {
            return FAIL; // pickIncoming 已经给出了失败原因
        }

        // 先移除请求：重复点击时第二次必然落在「没有待处理请求」上，不会二次传送
        TpaRequests.remove(request.requesterId());

        ServerPlayer requester = source.getServer().getPlayerList().getPlayer(request.requesterId());
        if (requester == null) {
            source.sendFailure(Component.translatable("tpa.error.requester_offline"));
            return FAIL;
        }

        String requesterActualName = requester.getScoreboardName();
        String targetName = target.getScoreboardName();

        if (!accept) {
            requester.sendSystemMessage(Component.translatable("tpa.message.denied_requester", targetName));
            source.sendSuccess(() -> Component.translatable("tpa.message.denied"), false);
            TpaMod.LOGGER.info("[{}] {} 拒绝了 {} 的传送请求", TpaMod.MOD_ID, targetName, requesterActualName);
            return OK;
        }

        // 显式指定目标所在世界，跨维度也能正确落点
        ServerLevel targetLevel = (ServerLevel) target.level();
        requester.teleportTo(targetLevel, target.getX(), target.getY(), target.getZ(),
                Set.<Relative>of(), target.getYRot(), target.getXRot(), true);

        requester.sendSystemMessage(Component.translatable("tpa.message.accepted_requester", targetName));
        target.sendSystemMessage(Component.translatable("tpa.message.accepted_target", requesterActualName));

        TpaMod.LOGGER.info("[{}] {} 已传送到 {}", TpaMod.MOD_ID, requesterActualName, targetName);
        return OK;
    }

    /**
     * 在「发给该玩家」的请求里挑一条：指定了名字就按名字精确匹配，
     * 没指定则要求恰好只有一条（多条时让玩家自己指定，避免点/输错人）。
     */
    private static TpaRequests.Request pickIncoming(CommandSourceStack source, ServerPlayer target, String requesterName) {
        java.util.List<TpaRequests.Request> incoming = TpaRequests.findIncoming(target.getUUID());
        if (incoming.isEmpty()) {
            source.sendFailure(Component.translatable("tpa.error.no_pending"));
            return null;
        }

        if (requesterName == null) {
            if (incoming.size() == 1) {
                return incoming.getFirst();
            }
            source.sendFailure(Component.translatable("tpa.error.multiple_pending", incoming.size()));
            return null;
        }

        ServerPlayer requester = source.getServer().getPlayerList().getPlayerByName(requesterName);
        if (requester != null) {
            for (TpaRequests.Request request : incoming) {
                if (request.requesterId().equals(requester.getUUID())) {
                    return request;
                }
            }
        }
        source.sendFailure(Component.translatable("tpa.error.no_request_from", requesterName));
        return null;
    }

    // ------------------------------------------------------------------
    // /tpacancel
    // ------------------------------------------------------------------

    private static int cancel(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer requester = source.getPlayer();
        if (requester == null) {
            source.sendFailure(Component.translatable("tpa.error.player_only"));
            return FAIL;
        }

        TpaRequests.Request request = TpaRequests.remove(requester.getUUID());
        if (request == null) {
            source.sendFailure(Component.translatable("tpa.error.no_pending"));
            return FAIL;
        }

        ServerPlayer target = source.getServer().getPlayerList().getPlayer(request.targetId());
        if (target != null) {
            target.sendSystemMessage(Component.translatable("tpa.message.cancelled_target",
                    requester.getScoreboardName()));
        }

        source.sendSuccess(() -> Component.translatable("tpa.message.cancelled"), false);
        TpaMod.LOGGER.info("[{}] {} 取消了传送请求", TpaMod.MOD_ID, requester.getScoreboardName());
        return OK;
    }
}
