package com.example.tpamod;

import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TpaMod implements DedicatedServerModInitializer {
    public static final String MOD_ID = "tpa";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitializeServer() {
        LOGGER.info("初始化 TPA Mod");

        // 注册命令
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            TpaCommand.register(dispatcher, registryAccess, environment);
        });

        LOGGER.info("TPA Mod 初始化完成");
    }
}
