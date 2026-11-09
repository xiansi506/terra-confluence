package com.example.terraplateextension.config;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * 模组通用配置（config/terra_plate_extension-common.toml）。
 *
 * <p>扩展点：后续新增配置项时，在下面按同样的方式添加静态字段并在
 * {@code static} 块中使用 builder 定义即可。
 */
public final class ModConfig {

    public static final ForgeConfigSpec COMMON_SPEC;

    /** 条件不满足时是否向附近玩家发送提示信息 */
    public static final ForgeConfigSpec.BooleanValue FEEDBACK_ENABLED;

    /** 提示信息的发送半径（方块） */
    public static final ForgeConfigSpec.IntValue FEEDBACK_RADIUS;

    /** 同一次失败条件的最短重复提示间隔（tick，20 = 1 秒） */
    public static final ForgeConfigSpec.IntValue FEEDBACK_INTERVAL_TICKS;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.comment("泰拉凝炼通用配置").push("general");

        FEEDBACK_ENABLED = builder
                .comment("条件不满足时是否向附近玩家发送提示信息")
                .define("feedbackEnabled", true);

        FEEDBACK_RADIUS = builder
                .comment("提示信息的发送半径（方块）")
                .defineInRange("feedbackRadius", 8, 1, 64);

        FEEDBACK_INTERVAL_TICKS = builder
                .comment("同一次失败条件的最短重复提示间隔（tick，20 = 1 秒）")
                .defineInRange("feedbackIntervalTicks", 20, 1, 200);

        builder.pop();

        COMMON_SPEC = builder.build();
    }

    private ModConfig() {
    }
}
