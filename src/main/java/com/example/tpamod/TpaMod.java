package com.example.tpamod;

import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * TPA Mod —— 玩家间传送请求（Minecraft 26.3 / Fabric）。
 *
 * <p>服务端专用模组：通过 {@link DedicatedServerModInitializer} 只在逻辑服务端加载，
 * 客户端无需安装（详见 fabric.mod.json 的 environment 与入口点）。</p>
 *
 * <p>本项目的<b>初版由旧模型 v3.2 生成</b>（面向 Minecraft 1.21.1 / Yarn，未经构建验证），
 * 现已依据《经验.md》升级到 26.3，并修复了初版中的并发与跨维度传送缺陷。</p>
 */
public class TpaMod implements DedicatedServerModInitializer {

    public static final String MOD_ID = "tpa";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitializeServer() {
        LOGGER.info("[{}] 初始化（Minecraft 26.3）", MOD_ID);

        // 注册命令：/tpa /tpaccept /tpdeny /tpacancel
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                TpaCommand.register(dispatcher));

        // 请求过期检查：由服务端主线程每刻驱动。
        // 初版用的是「每个请求开一个裸线程 + Thread.sleep(60s) 后从异步线程改 HashMap」，
        // 存在线程安全问题（主线程同时在读写），且请求被提前处理时线程仍会空等 60 秒。
        ServerTickEvents.END_SERVER_TICK.register(TpaRequests::tick);

        LOGGER.info("[{}] 初始化完成", MOD_ID);
    }
}
