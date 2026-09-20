package com.example.tpamod;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * 传送请求表：请求者 UUID → 目标 UUID。
 *
 * <p><b>线程模型</b>：只在服务端主线程读写（命令回调与 ServerTickEvents.END_SERVER_TICK
 * 都在主线程执行），因此用普通 HashMap 即可，无需额外同步。
 * 初版在后台线程里操作共享 Map，属于数据竞争，已在本次升级中移除。</p>
 *
 * <p><b>过期</b>：由 {@link #tick(MinecraftServer)} 每刻检查时间戳，不使用 sleep 线程，
 * 请求 60 秒后自动失效并通知双方（初版过期时不给任何提示）。</p>
 */
public final class TpaRequests {

    /** 请求有效期：60 秒。 */
    private static final long TIMEOUT_MILLIS = 60_000L;

    /** 一个玩家同一时间只能有一个待处理请求，故以请求者 UUID 为键。 */
    private static final Map<UUID, Request> REQUESTS = new HashMap<>();

    private TpaRequests() {
    }

    /** 一条待处理的传送请求。 */
    public record Request(UUID requesterId, UUID targetId, long createdAt) {
    }

    public static boolean hasPending(UUID requesterId) {
        return REQUESTS.containsKey(requesterId);
    }

    public static void put(UUID requesterId, UUID targetId) {
        REQUESTS.put(requesterId, new Request(requesterId, targetId, System.currentTimeMillis()));
    }

    public static Request remove(UUID requesterId) {
        return REQUESTS.remove(requesterId);
    }

    /** 找出「目标为该玩家」的请求；同一目标最多只会有一个有效请求。 */
    public static Request findIncoming(UUID targetId) {
        for (Request request : REQUESTS.values()) {
            if (request.targetId().equals(targetId)) {
                return request;
            }
        }
        return null;
    }

    public static void clear() {
        REQUESTS.clear();
    }

    /** 每刻清理超时请求并通知双方。 */
    public static void tick(MinecraftServer server) {
        if (REQUESTS.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, Request>> iterator = REQUESTS.entrySet().iterator();
        while (iterator.hasNext()) {
            Request request = iterator.next().getValue();
            if (now - request.createdAt() < TIMEOUT_MILLIS) {
                continue;
            }
            iterator.remove();
            notifyPlayer(server, request.requesterId(), "tpa.message.expired");
            notifyPlayer(server, request.targetId(), "tpa.message.expired_target");
        }
    }

    private static void notifyPlayer(MinecraftServer server, UUID playerId, String translationKey) {
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player != null) {
            player.sendSystemMessage(Component.translatable(translationKey));
        }
    }
}
