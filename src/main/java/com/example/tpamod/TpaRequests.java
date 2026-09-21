package com.example.tpamod;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 传送请求表：请求者 UUID → 目标 UUID。
 *
 * <p><b>线程模型</b>：只在服务端主线程读写（命令回调与 ServerTickEvents.END_SERVER_TICK
 * 都在主线程执行），因此用普通 HashMap 即可，无需额外同步。
 * 初版在后台线程里操作共享 Map，属于数据竞争，已在 2.0.0 中移除。</p>
 *
 * <p><b>过期</b>：由 {@link #tick(MinecraftServer)} 每刻检查时间戳，不使用 sleep 线程，
 * 请求 {@value #TIMEOUT_SECONDS} 秒后自动失效并通知双方。</p>
 *
 * <p><b>多对一</b>：键是请求者，因此<b>允许多个玩家同时请求同一个人</b>。
 * 原来只有 {@link #findIncoming(UUID)} 的「返回第一个」语义，在多人同时请求时
 * 目标玩家点击同意可能接受的不是自己看到的那一条；因此现在改为返回全部匹配项
 * （{@link #findIncoming(UUID)} 返回列表、{@link #find(UUID, UUID)} 精确查找），
 * 命令与按钮都带上请求者名字，保证「点谁就是谁」。</p>
 */
public final class TpaRequests {

    /** 请求有效期（秒）。聊天提示里的秒数也读这里，避免文案与逻辑各写一份。 */
    public static final int TIMEOUT_SECONDS = 60;

    private static final long TIMEOUT_MILLIS = TIMEOUT_SECONDS * 1000L;

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

    /** 找出「目标为该玩家」的全部请求；同一目标可能同时收到多个不同玩家的请求。 */
    public static List<Request> findIncoming(UUID targetId) {
        List<Request> incoming = new ArrayList<>();
        for (Request request : REQUESTS.values()) {
            if (request.targetId().equals(targetId)) {
                incoming.add(request);
            }
        }
        return incoming;
    }

    /** 精确查找「该请求者 → 该目标」的请求，不存在时返回 {@code null}。 */
    public static Request find(UUID requesterId, UUID targetId) {
        Request request = REQUESTS.get(requesterId);
        return request != null && request.targetId().equals(targetId) ? request : null;
    }

    public static void clear() {
        REQUESTS.clear();
    }

    /** 每刻清理超时或「任一方已离线」的请求，并通知仍在线的相关玩家。 */
    public static void tick(MinecraftServer server) {
        if (REQUESTS.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, Request>> iterator = REQUESTS.entrySet().iterator();
        while (iterator.hasNext()) {
            Request request = iterator.next().getValue();

            boolean requesterOnline = server.getPlayerList().getPlayer(request.requesterId()) != null;
            boolean targetOnline = server.getPlayerList().getPlayer(request.targetId()) != null;

            if (requesterOnline && targetOnline) {
                if (now - request.createdAt() < TIMEOUT_MILLIS) {
                    continue;
                }
                iterator.remove();
                notifyPlayer(server, request.requesterId(), "tpa.message.expired");
                notifyPlayer(server, request.targetId(), "tpa.message.expired_target");
                continue;
            }

            // 任一方离线：请求已经没有兑现的可能。若不清理，请求者会白等到 60 秒超时，
            // 目标玩家则会看到一组永远点不通的按钮。
            iterator.remove();
            if (requesterOnline) {
                notifyPlayer(server, request.requesterId(), "tpa.message.target_offline");
            }
            if (targetOnline) {
                notifyPlayer(server, request.targetId(), "tpa.message.requester_left");
            }
        }
    }

    private static void notifyPlayer(MinecraftServer server, UUID playerId, String translationKey) {
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player != null) {
            player.sendSystemMessage(Component.translatable(translationKey));
        }
    }
}
