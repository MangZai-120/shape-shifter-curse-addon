package net.jackcooper.shapeShifterCurseAddon.compat;

import net.jackcooper.shapeShifterCurseAddon.balance.BalanceIntegration;
import net.minecraft.server.network.ServerPlayerEntity;

/** Optional authentication bridge. No EasyAuth classes are loaded when the mod is absent. */
public final class EasyAuthBalanceCompatibility {
    private EasyAuthBalanceCompatibility() {}

    public interface AuthenticationState {
        boolean ssca$isAuthenticated();
    }

    /** Resolve only the public boolean API, lazily, without importing EasyAuth's Java 21 classes. */
    public static boolean readAuthenticationState(Object player) {
        try { return (boolean) Api.AUTHENTICATED.invokeExact(player); }
        catch (Throwable error) { throw new IllegalStateException("Cannot read EasyAuth authentication state", error); }
    }

    private static final class Api {
        private static final java.lang.invoke.MethodHandle AUTHENTICATED = resolve();
        private static java.lang.invoke.MethodHandle resolve() {
            try {
                var type = Class.forName("xyz.nikitacartes.easyauth.interfaces.PlayerAuth");
                return java.lang.invoke.MethodHandles.publicLookup().findVirtual(type, "easyAuth$isAuthenticated",
                        java.lang.invoke.MethodType.methodType(boolean.class))
                        .asType(java.lang.invoke.MethodType.methodType(boolean.class, Object.class));
            } catch (ReflectiveOperationException error) {
                throw new IllegalStateException("Unsupported EasyAuth PlayerAuth API", error);
            }
        }
    }

    public static boolean canSynchronize(ServerPlayerEntity player) {
        return !(player instanceof AuthenticationState state) || state.ssca$isAuthenticated();
    }

    public static void authenticationChanged(ServerPlayerEntity player, boolean authenticated) {
        var server = player.getServer();
        if (server == null) return;
        server.execute(() -> {
            // Ignore an old connection or an authentication change superseded before this task ran.
            if (player.isDisconnected() || server.getPlayerManager().getPlayer(player.getUuid()) != player
                    || canSynchronize(player) != authenticated) return;
            if (authenticated) BalanceIntegration.onPlayerReady(player);
            else BalanceIntegration.onPlayerLeft(player);
        });
    }
}
