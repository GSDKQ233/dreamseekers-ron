package com.solegendary.reignofnether.auth;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.solegendary.reignofnether.hud.custombutton.*;
import io.netty.buffer.Unpooled;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.gametest.*;

/** Dedicated registered data codec round-trip plus the actual authenticated C2S handler.
 * No client rendering or full datapack reload is claimed by this probe. */
@GameTestHolder("api65_auth_codec") @PrefixGameTestTemplate(false)
public final class CustomButtonCodecGameTests {
    @GameTest(templateNamespace="api65_auth_codec", template="ore_mine_test_arena", batch="auth_button_codec_roundtrip")
    public static void buttonCodecGettersRoundTripAndDispatchAreServerSafe(GameTestHelper h) throws Exception {
        try (var f = new AuthBoundaryGameTests.Fixture(h)) {
            CustomButton original;
            JsonElement encoded;
            CustomButton restored;
            try {
                original = CustomButton.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(
                        "{\"name\":\"CodecAuth\",\"icon\":\"minecraft:textures/item/stone.png\",\"x\":17,\"y\":-9,"
                        + "\"icon_size\":23,\"light_up_on_hover\":false,\"enable\":true,"
                        + "\"left_click_actions\":[{\"type\":\"reignofnether:experience\",\"points\":2}],"
                        + "\"right_click_actions\":[{\"type\":\"reignofnether:experience\",\"points\":7}]}"))
                        .getOrThrow(false, message -> { throw new AssertionError(message); });
            } catch (Throwable failure) { throw new AssertionError("registered CustomButton CODEC decode must be dedicated-safe", failure); }
            try {
                // Calls every CODEC forGetter, not merely the constructor: client layout must stay lazy.
                encoded = CustomButton.CODEC.encodeStart(JsonOps.INSTANCE, original)
                        .getOrThrow(false, message -> { throw new AssertionError(message); });
            } catch (Throwable failure) { throw new AssertionError("registered CustomButton CODEC encode/getters must be dedicated-safe", failure); }
            try {
                restored = CustomButton.CODEC.parse(JsonOps.INSTANCE, encoded)
                        .getOrThrow(false, message -> { throw new AssertionError(message); });
                JsonElement again = CustomButton.CODEC.encodeStart(JsonOps.INSTANCE, restored)
                        .getOrThrow(false, message -> { throw new AssertionError(message); });
                h.assertTrue(encoded.equals(again), "complete data CODEC round-trip preserves configured fields and action arrays");
                h.assertTrue(restored.iconSize() == 23 && restored.offsetX() == 17 && restored.offsetY() == -9,
                        "nondefault layout values survive server codec round-trip");
            } catch (Throwable failure) { throw new AssertionError("registered CustomButton CODEC re-decode must be dedicated-safe", failure); }
            ResourceLocation id = ResourceLocation.parse("api65_auth:codec_probe");
            CustomButton previous = CustomButtonServerEvents.customButtons.put(id, restored);
            try {
                int before = f.actor.totalExperience;
                click(f, id, true);
                h.assertTrue(f.actor.totalExperience - before == 2, "left packet executes left action through real handler");
                click(f, id, false);
                h.assertTrue(f.actor.totalExperience - before == 9, "right packet executes independent right action through real handler");
                restored.isEnabled = false;
                click(f, id, true); click(f, id, false);
                h.assertTrue(f.actor.totalExperience - before == 9, "disabled round-tripped button rejects both packet actions");
            } finally {
                if (previous == null) CustomButtonServerEvents.customButtons.remove(id);
                else CustomButtonServerEvents.customButtons.put(id, previous);
            }
        }
        h.succeed();
    }
    private static void click(AuthBoundaryGameTests.Fixture f, ResourceLocation id, boolean left) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            new CustomButtonActionServerboundPacket(id, left).encode(buffer);
            CustomButtonActionServerboundPacket decoded = CustomButtonActionServerboundPacket.decode(buffer);
            f.h.assertTrue(buffer.readableBytes() == 0, "complete action wire codec");
            decoded.handle(() -> f.context);
            f.peer.drain();
        } finally { buffer.release(); }
    }
}
