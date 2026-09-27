package net.jackcooper.shapeShifterCurseAddon.balance;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/** Regression cases found by reviewing real loader/consumer/sync wiring, beyond default pins. */
public final class BalanceReviewTest {
    private static int checks;
    public static void main(String[] args) throws Exception {
        var schema = SscBalanceSchema.create();
        var named = BalanceDocuments.normalize("abilities/bat_sonic_wave.json", "test-pack",
                "{\"schema_version\":1,\"damage\":8}");
        check(BalanceSnapshot.fromTree(schema, named, "test").getDouble("abilities.bat_sonic_wave", "damage") == 8, "named resource maps to scope");
        check(BalanceLoader.DIRECTORY.equals("balance"), "resource namespace is not repeated in directory");
        loaderUsesRealResourcePathsAndPackPriority(schema);
        reject(() -> BalanceDocuments.normalize("abilities/bat_sonic_wave.json", "broken", "{broken"), "broken files fail instead of being skipped");
        reject(() -> BalanceDocuments.normalize("abilities/bat_sonic_wave.json", "future", "{\"schema_version\":1.5}"), "fractional schema version");
        var affinity = snapshot(schema, "{\"affinity\":{\"mana_mul_wild_cat_sp\":0.6}}");
        check(affinity.getDouble("affinity", "mana_mul_wild_cat_sp") == .6, "single-component affinity scope accepts overrides");
        reject(() -> snapshot(schema, "{\"affinity\":{\"typo\":1}}"), "unknown affinity field");
        reject(() -> snapshot(schema, "{\"abilities\":{\"bat_sonic_wave\":{\"damage\":null}}}"), "explicit null is not a default");
        reject(() -> snapshot(schema, "{\"systems\":{\"domain\":{\"inner_radius\":32}}}"), "inverted domain radii");
        reject(() -> snapshot(schema, "{\"systems\":{\"domain\":{\"charge_ticks\":100}}}"), "expansion after charge completion");
        reject(() -> snapshot(schema, "{\"systems\":{\"explosion\":{\"sound_range\":32}}}"), "invalid sound interpolation denominator");
        var defaults = BalanceSnapshot.defaults(schema);
        check(!defaults.overridesPower("forms.snow_fox_sp", "melee_speed_bonus"), "default balance must preserve power JSON");
        var explicit = snapshot(schema, "{\"forms\":{\"snow_fox_sp\":{\"melee_speed_bonus\":0.1}}}");
        check(explicit.overridesPower("forms.snow_fox_sp", "melee_speed_bonus"), "explicit balance override retained");
        check(!explicit.hash().equals(defaults.hash()), "override presence affects effective power behavior and hash");

        var server = new ServerBalanceState(schema);
        server.commitDefaults();
        var player = UUID.randomUUID();
        server.playerJoined(player);
        server.playerAcked(player, server.revision());
        check(!server.isPlayerReady(player), "unsent snapshot cannot be ACKed");
        var old = server.buildSendSequence(player, "{\"schema_version\":1}");
        var client = new ClientBalanceState(schema, null);
        deliver(client, old);
        String changed = "{\"schema_version\":1,\"abilities\":{\"bat_sonic_wave\":{\"damage\":9}}}";
        server.commitTree(BalanceJson.parse("new", changed).tree, "new");
        var newer = server.buildSendSequence(player, changed);
        deliver(client, newer);
        deliver(client, old);
        check(client.current().getDouble("abilities.bat_sonic_wave", "damage") == 9, "complete old HELLO/chunks/COMMIT cannot roll back config");
        var newHeader = ((BalanceWire.Hello) newer.get(0)).header();
        server.playerAcked(player, newHeader.sessionId() - 1, newHeader.revision());
        check(!server.isPlayerReady(player), "old transfer ACK cannot unlock new transfer");
        server.playerAcked(player, newHeader.sessionId(), newHeader.revision());
        check(server.isPlayerReady(player), "matching transfer ACK unlocks casting");
        var badClient = new ClientBalanceState(schema, null);
        for (var message : newer) if (!(message instanceof BalanceWire.Commit)) badClient.offer(message);
        var changedHeader = new BalanceWire.SnapshotHeader(newHeader.sessionId(), newHeader.revision()+1,
                newHeader.schemaVersion(), newHeader.hash(), newHeader.totalChunks(), newHeader.totalLength());
        check(badClient.offer(new BalanceWire.Commit(changedHeader)) instanceof ClientBalanceState.Rejected, "commit cannot rewrite hello revision");
        check(!badClient.isReady(), "header rejection leaves client unready");

        // Use a valid string parameter with an actual three-byte UTF-8 character spanning two chunks.
        var builder = BalanceSchema.create();
        builder.scope("text.sample").stringParam("label", "default");
        var textSchema = builder.build();
        String unicode = "{\"schema_version\":1,\"text\":{\"sample\":{\"label\":\"\u96ea\"}}}";
        byte[] bytes = unicode.getBytes(StandardCharsets.UTF_8);
        int split = unicode.indexOf('\u96ea') + 1;
        var expected = BalanceSnapshot.fromTree(textSchema, BalanceJson.parse("utf8", unicode).tree, "utf8");
        var header = new BalanceWire.SnapshotHeader(1, 1, 1, expected.hash(), 2, bytes.length);
        var textClient = new ClientBalanceState(textSchema, null);
        deliver(textClient, List.of(new BalanceWire.Hello(header),
                new BalanceWire.Chunk(1, 0, java.util.Arrays.copyOfRange(bytes, 0, split)),
                new BalanceWire.Chunk(1, 1, java.util.Arrays.copyOfRange(bytes, split, bytes.length)), new BalanceWire.Commit(header)));
        check(textClient.isReady() && textClient.current().getString("text.sample", "label").equals("\u96ea"), "UTF-8 must be decoded after byte reassembly");
        byte[] protocol = BalanceWire.encode(new BalanceWire.Hello(header));
        protocol[3]++;
        reject(() -> BalanceWire.decode(protocol), "incompatible wire version is enforced");

        // Two logical sides in the same process must not share the authoritative lookup.
        var threadField = BalanceIntegration.class.getDeclaredField("clientThread");
        var serverField = BalanceIntegration.class.getDeclaredField("publishedServer");
        var clientField = BalanceIntegration.class.getDeclaredField("publishedClient");
        threadField.setAccessible(true); serverField.setAccessible(true); clientField.setAccessible(true);
        Object oldThread = threadField.get(null), oldServer = serverField.get(null), oldClient = clientField.get(null);
        try {
            threadField.set(null, Thread.currentThread()); serverField.set(null, defaults); clientField.set(null, affinity);
            check(BalanceIntegration.currentSnapshot() == affinity, "client reads connection mirror");
            var result = new java.util.concurrent.atomic.AtomicReference<BalanceSnapshot>();
            Thread integratedServer = new Thread(() -> result.set(BalanceIntegration.currentSnapshot()));
            integratedServer.start(); integratedServer.join();
            check(result.get() == defaults, "integrated server never reads client mirror");
        } finally { threadField.set(null, oldThread); serverField.set(null, oldServer); clientField.set(null, oldClient); }
        System.out.println("Balance review regressions: " + checks + " passed.");
    }
    private static BalanceSnapshot snapshot(BalanceSchema schema, String fields) {
        var tree = new java.util.LinkedHashMap<>(BalanceJson.parse("test", fields).tree);
        tree.put("schema_version", 1L);
        return BalanceSnapshot.fromTree(schema, tree, "test");
    }
    private static void loaderUsesRealResourcePathsAndPackPriority(BalanceSchema schema) {
        var low = resourcePack("low");
        var high = resourcePack("high");
        var files = new java.util.LinkedHashMap<net.minecraft.util.Identifier, List<net.minecraft.resource.Resource>>();
        files.put(new net.minecraft.util.Identifier("ssc_addon", "balance/z.json"), List.of(resource(low,
                "{\"schema_version\":1,\"abilities\":{\"bat_sonic_wave\":{\"damage\":5}}}")));
        files.put(new net.minecraft.util.Identifier("ssc_addon", "balance/a.json"), List.of(resource(high,
                "{\"schema_version\":1,\"abilities\":{\"bat_sonic_wave\":{\"damage\":9}}}")));
        files.put(new net.minecraft.util.Identifier("other", "balance/ignored.json"), List.of(resource(high, "invalid")));
        var manager = (net.minecraft.resource.ResourceManager) java.lang.reflect.Proxy.newProxyInstance(
                BalanceReviewTest.class.getClassLoader(), new Class<?>[]{net.minecraft.resource.ResourceManager.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "streamResourcePacks" -> java.util.stream.Stream.of(low, high);
                    case "getAllResources" -> files.get(arguments[0]);
                    case "findResources" -> {
                        @SuppressWarnings("unchecked") var predicate = (java.util.function.Predicate<net.minecraft.util.Identifier>) arguments[1];
                        var found = new java.util.LinkedHashMap<net.minecraft.util.Identifier, net.minecraft.resource.Resource>();
                        files.forEach((id, resources) -> {
                            if (id.getPath().startsWith(arguments[0] + "/") && predicate.test(id))
                                found.put(id, resources.get(resources.size() - 1));
                        });
                        yield found;
                    }
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        var loader = new BalanceLoader(layers -> {});
        var layers = loader.readLayers(manager);
        check(layers.size() == 3, "resource loader finds two canonical files and ignores other namespaces");
        check(BalanceSnapshot.fromTree(schema, BalanceMerge.merge(layers), "packs")
                .getDouble("abilities.bat_sonic_wave", "damage") == 9, "higher pack beats lower pack even with earlier filename");
        files.put(new net.minecraft.util.Identifier("ssc_addon", "balance/broken.json"), List.of(resource(high, "[]")));
        reject(() -> loader.readLayers(manager), "one bad resource rejects entire candidate");
    }
    private static net.minecraft.resource.ResourcePack resourcePack(String name) {
        return (net.minecraft.resource.ResourcePack) java.lang.reflect.Proxy.newProxyInstance(
                BalanceReviewTest.class.getClassLoader(), new Class<?>[]{net.minecraft.resource.ResourcePack.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getName")) return name;
                    throw new UnsupportedOperationException(method.getName());
                });
    }
    private static net.minecraft.resource.Resource resource(net.minecraft.resource.ResourcePack pack, String json) {
        return new net.minecraft.resource.Resource(pack,
                () -> new java.io.ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
    }
    private static void deliver(ClientBalanceState client, List<BalanceWire.Message> messages) { messages.forEach(client::offer); }
    private static void reject(Runnable action, String label) {
        try { action.run(); } catch (RuntimeException expected) { checks++; return; }
        throw new AssertionError(label);
    }
    private static void check(boolean condition, String label) { if (!condition) throw new AssertionError(label); checks++; }
}
