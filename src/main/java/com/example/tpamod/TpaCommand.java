package com.example.tpamod;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class TpaCommand {
    
    // 存储传送请求: 请求者UUID -> 目标玩家UUID
    private static final Map<UUID, TpaRequest> tpaRequests = new HashMap<>();
    
    // 玩家名称建议提供者 - 提供在线玩家名称列表
    public static final SuggestionProvider<ServerCommandSource> ONLINE_PLAYERS_SUGGESTION = (context, builder) -> {
        String remaining = builder.getRemaining().toLowerCase();
        
        for (ServerPlayerEntity player : context.getSource().getServer().getPlayerManager().getPlayerList()) {
            String playerName = player.getName().getString();
            if (playerName.toLowerCase().startsWith(remaining)) {
                builder.suggest(playerName);
            }
        }
        
        return builder.buildFuture();
    };
    
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher, 
                                CommandRegistryAccess registryAccess,
                                CommandManager.RegistrationEnvironment environment) {
        
        // /tpa <玩家名称> - 请求传送到指定玩家
        dispatcher.register(CommandManager.literal("tpa")
            .then(CommandManager.argument("player", StringArgumentType.word())
                .suggests(ONLINE_PLAYERS_SUGGESTION) // 提供玩家名称自动补全
                .executes(TpaCommand::executeTpa)
            )
            .executes(context -> {
                context.getSource().sendFeedback(() -> 
                    Text.literal("§c用法: /tpa <玩家名称>").formatted(Formatting.RED), false);
                return 0;
            })
        );
        
        // /tpaccept - 接受传送请求
        dispatcher.register(CommandManager.literal("tpaccept")
            .executes(TpaCommand::executeTpAccept)
        );
        
        // /tpdeny - 拒绝传送请求
        dispatcher.register(CommandManager.literal("tpdeny")
            .executes(TpaCommand::executeTpDeny)
        );
        
        // /tpacancel - 取消自己的传送请求
        dispatcher.register(CommandManager.literal("tpacancel")
            .executes(TpaCommand::executeTpCancel)
        );
    }
    
    private static int executeTpa(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        
        if (!(source.getEntity() instanceof ServerPlayerEntity requester)) {
            source.sendFeedback(() -> 
                Text.literal("§c只有玩家可以使用此命令").formatted(Formatting.RED), false);
            return 0;
        }
        
        String targetName = StringArgumentType.getString(context, "player");
        ServerPlayerEntity target = source.getServer().getPlayerManager().getPlayer(targetName);
        
        if (target == null) {
            source.sendFeedback(() -> 
                Text.literal("§c玩家 '" + targetName + "' 不在线或不存在").formatted(Formatting.RED), false);
            return 0;
        }
        
        if (target.getUuid().equals(requester.getUuid())) {
            source.sendFeedback(() -> 
                Text.literal("§c你不能传送给自己").formatted(Formatting.RED), false);
            return 0;
        }
        
        // 检查是否已有待处理的请求
        if (tpaRequests.containsKey(requester.getUuid())) {
            source.sendFeedback(() -> 
                Text.literal("§c你已经有一个待处理的传送请求了，使用 /tpacancel 取消").formatted(Formatting.RED), false);
            return 0;
        }
        
        // 创建传送请求
        TpaRequest request = new TpaRequest(requester.getUuid(), target.getUuid(), System.currentTimeMillis());
        tpaRequests.put(requester.getUuid(), request);
        
        // 通知请求者
        source.sendFeedback(() -> 
            Text.literal("§a已向 §e" + targetName + " §a发送传送请求").formatted(Formatting.GREEN), false);
        source.sendFeedback(() -> 
            Text.literal("§7请求将在60秒后过期，使用 /tpacancel 取消").formatted(Formatting.GRAY), false);
        
        // 通知目标玩家
        target.sendMessage(Text.literal("§e" + requester.getName().getString() + " §a想要传送到你这里").formatted(Formatting.GREEN), false);
        target.sendMessage(Text.literal("§a输入 §e/tpaccept §a接受 或 §e/tpdeny §a拒绝").formatted(Formatting.GREEN), false);
        target.sendMessage(Text.literal("§7请求将在60秒后过期").formatted(Formatting.GRAY), false);
        
        // 启动过期检查
        startExpiryCheck(requester.getUuid());
        
        return 1;
    }
    
    private static int executeTpAccept(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        
        if (!(source.getEntity() instanceof ServerPlayerEntity target)) {
            source.sendFeedback(() -> 
                Text.literal("§c只有玩家可以使用此命令").formatted(Formatting.RED), false);
            return 0;
        }
        
        // 查找以该玩家为目标的请求
        TpaRequest request = findRequestByTarget(target.getUuid());
        
        if (request == null) {
            source.sendFeedback(() -> 
                Text.literal("§c你没有待处理的传送请求").formatted(Formatting.RED), false);
            return 0;
        }
        
        ServerPlayerEntity requester = source.getServer().getPlayerManager().getPlayer(request.requesterUuid);
        
        if (requester == null) {
            source.sendFeedback(() -> 
                Text.literal("§c请求者已离线").formatted(Formatting.RED), false);
            tpaRequests.remove(request.requesterUuid);
            return 0;
        }
        
        // 执行传送 - 使用1.21.11兼容的方法
        requester.teleport(target.getX(), target.getY(), target.getZ(), true);
        
        // 通知双方
        String requesterName = requester.getName().getString();
        String targetName = target.getName().getString();
        
        requester.sendMessage(Text.literal("§a你已传送到 §e" + targetName + " §a身边").formatted(Formatting.GREEN), false);
        target.sendMessage(Text.literal("§e" + requesterName + " §a已传送到你身边").formatted(Formatting.GREEN), false);
        
        // 移除请求
        tpaRequests.remove(request.requesterUuid);
        
        return 1;
    }
    
    private static int executeTpDeny(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        
        if (!(source.getEntity() instanceof ServerPlayerEntity target)) {
            source.sendFeedback(() -> 
                Text.literal("§c只有玩家可以使用此命令").formatted(Formatting.RED), false);
            return 0;
        }
        
        TpaRequest request = findRequestByTarget(target.getUuid());
        
        if (request == null) {
            source.sendFeedback(() -> 
                Text.literal("§c你没有待处理的传送请求").formatted(Formatting.RED), false);
            return 0;
        }
        
        ServerPlayerEntity requester = source.getServer().getPlayerManager().getPlayer(request.requesterUuid);
        
        // 通知双方
        source.sendFeedback(() -> 
            Text.literal("§c你拒绝了传送请求").formatted(Formatting.RED), false);
        
        if (requester != null) {
            requester.sendMessage(Text.literal("§e" + target.getName().getString() + " §c拒绝了你的传送请求").formatted(Formatting.RED), false);
        }
        
        // 移除请求
        tpaRequests.remove(request.requesterUuid);
        
        return 1;
    }
    
    private static int executeTpCancel(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        
        if (!(source.getEntity() instanceof ServerPlayerEntity requester)) {
            source.sendFeedback(() -> 
                Text.literal("§c只有玩家可以使用此命令").formatted(Formatting.RED), false);
            return 0;
        }
        
        TpaRequest request = tpaRequests.remove(requester.getUuid());
        
        if (request == null) {
            source.sendFeedback(() -> 
                Text.literal("§c你没有待处理的传送请求").formatted(Formatting.RED), false);
            return 0;
        }
        
        // 通知目标玩家
        ServerPlayerEntity target = source.getServer().getPlayerManager().getPlayer(request.targetUuid);
        if (target != null) {
            target.sendMessage(Text.literal("§e" + requester.getName().getString() + " §c取消了传送请求").formatted(Formatting.RED), false);
        }
        
        source.sendFeedback(() -> 
            Text.literal("§a已取消传送请求").formatted(Formatting.GREEN), false);
        
        return 1;
    }
    
    private static TpaRequest findRequestByTarget(UUID targetUuid) {
        for (TpaRequest request : tpaRequests.values()) {
            if (request.targetUuid.equals(targetUuid)) {
                return request;
            }
        }
        return null;
    }
    
    private static void startExpiryCheck(UUID requesterUuid) {
        // 在新线程中检查请求是否过期
        new Thread(() -> {
            try {
                Thread.sleep(60000); // 60秒
                TpaRequest request = tpaRequests.remove(requesterUuid);
                if (request != null) {
                    // 可以在这里添加过期通知逻辑
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }).start();
    }
    
    // 传送请求数据类
    private static class TpaRequest {
        final UUID requesterUuid;
        final UUID targetUuid;
        final long timestamp;
        
        TpaRequest(UUID requesterUuid, UUID targetUuid, long timestamp) {
            this.requesterUuid = requesterUuid;
            this.targetUuid = targetUuid;
            this.timestamp = timestamp;
        }
    }
}
