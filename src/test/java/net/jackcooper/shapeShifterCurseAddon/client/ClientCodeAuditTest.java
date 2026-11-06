package net.jackcooper.shapeShifterCurseAddon.client;

import net.jackcooper.shapeShifterCurseAddon.client.colorpicker.AdvancedColorBridge;

/** Protocol, shader preservation, optional dependency and preset persistence regressions. */
public final class ClientCodeAuditTest {
    private static int checks;

    public static void main(String[] args) throws Exception {
        net.minecraft.SharedConstants.createGameVersion();
        net.minecraft.Bootstrap.initialize();
        colorProtocol();
        brewingMixins();
        tidalOwnerSnapshot();
        shaderPreservation();
        performanceCompletion();
        checks += net.jackcooper.shapeShifterCurseAddon.client.palette.PalettePresetStoreAuditTest.run();
        checks += net.jackcooper.shapeShifterCurseAddon.compat.MixinSelectionAuditTest.run();
        checks += net.jackcooper.shapeShifterCurseAddon.client.renderer.WeakEntityStateMapTest.run();
        System.out.println("Client code audit regressions PASS: " + checks + " checks");
    }

    private static void performanceCompletion() {
        boolean rejected=false;
        try {
            net.jackcooper.shapeShifterCurseAddon.client.perf.NeoCastingPerformanceProbe.verifyResult();
        } catch(AssertionError expected) {
            rejected=expected.getMessage().contains("before producing complete measurements");
        }
        require(rejected,"An unfinished performance run must fail verification");
    }

    private static void tidalOwnerSnapshot() {
        var owner = java.util.UUID.randomUUID();
        var saved = new net.minecraft.nbt.NbtCompound();
        saved.putUuid("Owner", owner);
        saved.putInt("TetherDuration", 204);
        var serverOrb = new net.jackcooper.shapeShifterCurseAddon.entity.TidalOrbEntity(
                net.jackcooper.shapeShifterCurseAddon.SscAddon.TIDAL_ORB_ENTITY, null);
        serverOrb.readCustomDataFromNbt(saved);
        require(serverOrb.getTrackedOwnerUuid().orElseThrow().equals(owner), "Loaded tide publishes its owner");
        require(serverOrb.getTetherTotal() == 204, "Loaded tide publishes its original duration snapshot");
        var buffer = net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();
        try {
            new net.minecraft.network.packet.s2c.play.EntityTrackerUpdateS2CPacket(
                    serverOrb.getId(), serverOrb.getDataTracker().getDirtyEntries()).write(buffer);
            var received = new net.minecraft.network.packet.s2c.play.EntityTrackerUpdateS2CPacket(buffer);
            var clientOrb = new net.jackcooper.shapeShifterCurseAddon.entity.TidalOrbEntity(
                    net.jackcooper.shapeShifterCurseAddon.SscAddon.TIDAL_ORB_ENTITY, null);
            clientOrb.getDataTracker().writeUpdatedEntries(received.trackedValues());
            require(clientOrb.getTrackedOwnerUuid().orElseThrow().equals(owner), "Real entity metadata packet preserves owner UUID");
            require(clientOrb.getTetherTotal() == 204, "Real entity metadata packet preserves Aling duration independent of local form");
            require(!buffer.isReadable(), "Entity metadata is consumed exactly");
        } finally {
            buffer.release();
        }
    }

    private static void brewingMixins() throws Exception {
        var potionSlot = Class.forName("net.minecraft.screen.BrewingStandScreenHandler$PotionSlot")
                .getDeclaredMethod("matches", net.minecraft.item.ItemStack.class);
        potionSlot.setAccessible(true);
        net.minecraft.item.Item[][] recipes = {
                {net.jackcooper.shapeShifterCurseAddon.SscAddon.INFINITE_ENERGY_POTION,
                        net.jackcooper.shapeShifterCurseAddon.SscAddon.INFINITE_ENERGY_POTION_SPLASH,
                        net.jackcooper.shapeShifterCurseAddon.SscAddon.INFINITE_ENERGY_POTION_LINGERING},
                {net.jackcooper.shapeShifterCurseAddon.SscAddon.UNIVERSAL_ENERGY_POTION,
                        net.jackcooper.shapeShifterCurseAddon.SscAddon.UNIVERSAL_ENERGY_POTION_SPLASH,
                        net.jackcooper.shapeShifterCurseAddon.SscAddon.UNIVERSAL_ENERGY_POTION_LINGERING},
                {net.jackcooper.shapeShifterCurseAddon.SscAddon.WITHER_POTION,
                        net.jackcooper.shapeShifterCurseAddon.SscAddon.WITHER_POTION_SPLASH,
                        net.jackcooper.shapeShifterCurseAddon.SscAddon.WITHER_POTION_LINGERING}
        };
        var stand = new net.minecraft.block.entity.BrewingStandBlockEntity(net.minecraft.util.math.BlockPos.ORIGIN,
                net.minecraft.block.Blocks.BREWING_STAND.getDefaultState());
        for (var forms : recipes) {
            for (var form : forms) {
                var input = new net.minecraft.item.ItemStack(form);
                require((boolean) potionSlot.invoke(null, input), "Real GUI potion slot accepts each custom potion form");
                require(stand.isValid(0, input), "Real hopper inventory validation accepts an empty potion slot");
            }
            var drink = new net.minecraft.item.ItemStack(forms[0]);
            var splash = new net.minecraft.item.ItemStack(forms[1]);
            var powder = new net.minecraft.item.ItemStack(net.minecraft.item.Items.GUNPOWDER);
            var breath = new net.minecraft.item.ItemStack(net.minecraft.item.Items.DRAGON_BREATH);
            require(net.minecraft.recipe.BrewingRecipeRegistry.hasRecipe(drink, powder), "Real registry finds custom drink-to-splash recipe");
            require(net.minecraft.recipe.BrewingRecipeRegistry.craft(powder, drink).isOf(forms[1]), "Real registry crafts splash output");
            require(net.minecraft.recipe.BrewingRecipeRegistry.hasRecipe(splash, breath), "Real registry finds custom splash-to-lingering recipe");
            require(net.minecraft.recipe.BrewingRecipeRegistry.craft(breath, splash).isOf(forms[2]), "Real registry crafts lingering output");
            require(!net.minecraft.recipe.BrewingRecipeRegistry.hasRecipe(drink, breath), "Unsupported direct lingering conversion is rejected");
        }
        var occupied = new net.minecraft.item.ItemStack(recipes[0][0]);
        stand.setStack(0, occupied);
        require(!stand.isValid(0, occupied.copy()), "Occupied potion slots retain vanilla insertion constraints");
        var vanilla = new net.minecraft.item.ItemStack(net.minecraft.item.Items.POTION);
        var powder = new net.minecraft.item.ItemStack(net.minecraft.item.Items.GUNPOWDER);
        require((boolean) potionSlot.invoke(null, vanilla), "Vanilla potion slot remains valid");
        require(net.minecraft.recipe.BrewingRecipeRegistry.hasRecipe(vanilla, powder), "Vanilla brewing recipe remains valid");
        require(net.minecraft.recipe.BrewingRecipeRegistry.craft(powder, vanilla).isOf(net.minecraft.item.Items.SPLASH_POTION), "Vanilla brewing output remains unchanged");
    }

    private static void colorProtocol() throws Exception {
        var receiver = net.onixary.shapeShifterCurseFabric.networking.ModPacketsC2S.class.getDeclaredMethod(
                "onUpdatePlayerCustomColor", net.minecraft.server.MinecraftServer.class,
                net.minecraft.server.network.ServerPlayerEntity.class,
                net.minecraft.server.network.ServerPlayNetworkHandler.class,
                net.minecraft.network.PacketByteBuf.class,
                net.fabricmc.fabric.api.networking.v1.PacketSender.class);
        receiver.setAccessible(true);
        for (int first : new int[]{0x00112233, 0xff112233}) {
            var state = new AdvancedColorBridge.WorkingState();
            for (int i = 0; i < 5; i++) state.colorsARGB[i] = first + i;
            state.greyReverse[1] = true;
            var buffer = AdvancedColorBridge.encodeColors(state);
            try {
                require(buffer.readableBytes() == 24, "Complete SSC color payload includes extraData header");
                require(!buffer.readBoolean(), "Explicit no-extra-data header");
                for (int i = 0; i < 5; i++) require(buffer.readInt()
                        == net.onixary.shapeShifterCurseFabric.util.FormTextureUtils.ARGB2ABGR(state.colorsARGB[i]), "Correct ABGR field order");
                for (int i = 0; i < 3; i++) require(buffer.readBoolean() == state.greyReverse[i], "Grey reverse field order");
                require(!buffer.isReadable(), "No trailing bytes");
                buffer.readerIndex(0);
                try {
                    receiver.invoke(null, null, null, null, buffer, null);
                } catch (java.lang.reflect.InvocationTargetException expected) {
                    // A null server is intentional: successful decoding reaches server.execute.
                    if (!(expected.getCause() instanceof NullPointerException)) throw expected;
                }
                require(!buffer.isReadable(), "Actual SSC receiver consumes the full packet without underflow");
            } finally {
                buffer.release();
            }
        }
    }

    private static void shaderPreservation() {
        String body = "\n  float fog = pow(lViewPos * 0.71, 3.0) * blindness;\n"
                + "  color = mix(color, vec3(0.0), fog); // custom $normal\\formula\n";
        String original = "#version 150\nvoid DoBlindnessFog(inout vec3 color, float lViewPos) {" + body + "}\n";
        String patched = FearShaderPatch.patch(original);
        require(patched.contains(body), "Ordinary blindness body remains byte-for-byte unchanged");
        require(patched.contains("smoothstep(12.0, 16.0, lViewPos)"), "Fear branch retains configured distances");
        require(patched.indexOf("return;") < patched.indexOf(body), "Fear branch exits before original body");
        require(patched.equals(FearShaderPatch.patch(patched)), "Shader patch is idempotent");
        String unrelated = "void OtherFog() { color = fogColor; }";
        require(unrelated.equals(FearShaderPatch.patch(unrelated)), "Unrelated shader is unchanged");
        String nested = "void DoBlindnessFog(inout vec3 color, float lViewPos) { if (true) { color = mix(color, vec3(0.0), fog); } }";
        require(nested.equals(FearShaderPatch.patch(nested)), "Unsupported nested function safely remains unchanged");
    }

    private static void require(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
