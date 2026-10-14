package com.soloeconomy.config;

import net.minecraftforge.common.ForgeConfigSpec;

import org.apache.commons.lang3.tuple.Pair;

/** Per-player display settings, in config/soloeconomy-client.toml. Changes apply as soon as the file is saved. */
public final class ClientConfig {

    public enum Corner { TOP_RIGHT, TOP_LEFT, BOTTOM_RIGHT, BOTTOM_LEFT, HIDDEN }

    public static final ForgeConfigSpec SPEC;
    public static final ClientConfig INSTANCE;

    public final ForgeConfigSpec.EnumValue<Corner> balanceCorner;
    public final ForgeConfigSpec.IntValue balanceOffsetX;
    public final ForgeConfigSpec.IntValue balanceOffsetY;

    private ClientConfig(ForgeConfigSpec.Builder builder) {
        builder.comment("Solo Economy - the emerald balance shown on screen").push("balance");

        balanceCorner = builder
                .comment("Which corner the balance sits in. Move it if it covers a minimap, or use HIDDEN",
                        "to turn it off; the stall always shows your balance.")
                .defineEnum("corner", Corner.TOP_RIGHT);
        balanceOffsetX = builder
                .comment("Distance from the left or right edge of the screen, in GUI pixels.")
                .defineInRange("offsetX", 4, 0, 4000);
        balanceOffsetY = builder
                .comment("Distance from the top or bottom edge. About 45 clears the hotbar in a bottom corner.")
                .defineInRange("offsetY", 4, 0, 4000);

        builder.pop();
    }

    static {
        Pair<ClientConfig, ForgeConfigSpec> pair = new ForgeConfigSpec.Builder().configure(ClientConfig::new);
        INSTANCE = pair.getLeft();
        SPEC = pair.getRight();
    }
}
