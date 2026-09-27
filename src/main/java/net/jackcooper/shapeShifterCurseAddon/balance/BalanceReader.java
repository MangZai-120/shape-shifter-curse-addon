package net.jackcooper.shapeShifterCurseAddon.balance;

/**
 * Reads the published snapshot for the current logical side. The client main
 * thread uses its server-synchronized mirror; the integrated server uses its
 * independent authoritative snapshot. Publication uses volatile references.
 * Capture values when a running ability must retain its cast-time parameters.
 */
public final class BalanceReader {

    private final String scope;

    public BalanceReader(String scope) {
        this.scope = scope;
    }

    /** DOUBLE 参数：快照缺失/未初始化回退 def。 */
    public double d(String param, double def) {
        BalanceSnapshot s = BalanceIntegration.currentSnapshot();
        return s == null ? def : s.getDouble(scope, param);
    }

    /** INT 参数：快照缺失/未初始化回退 def。 */
    public int i(String param, int def) {
        BalanceSnapshot s = BalanceIntegration.currentSnapshot();
        return s == null ? def : (int) s.getInt(scope, param);
    }
}
