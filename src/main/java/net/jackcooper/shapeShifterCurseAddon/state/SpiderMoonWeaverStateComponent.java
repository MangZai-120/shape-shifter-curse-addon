package net.jackcooper.shapeShifterCurseAddon.state;

import dev.onyxstudios.cca.api.v3.component.sync.AutoSyncedComponent;
import net.minecraft.nbt.NbtCompound;

/**
 * 月织蛛「织网术」模式开关持久化组件（服务端权威，登录/跟踪时自动同步，切换时显式同步）。
 *
 * <p>存 0=搭路 / 1=攻击。独立 CCA 组件，不挂 origin，因此<b>不会被 SSC 形态切换销毁重建</b>，
 * 天然跨会话 / 跨形态 / 死亡重生保留——解决原 apoli:resource 每次形态切换被重置回 start_value 的问题。
 * 重生复制策略 ALWAYS_COPY（见 RegSpiderMoonWeaverStateComponent）。
 */
public class SpiderMoonWeaverStateComponent implements AutoSyncedComponent {

	public static final int MODE_BRIDGE = 0;
	public static final int MODE_ATTACK = 1;

	private int mode = MODE_BRIDGE;

	public int getMode() {
		return mode;
	}

	public void setMode(int mode) {
		this.mode = (mode == MODE_ATTACK) ? MODE_ATTACK : MODE_BRIDGE;
	}

	@Override
	public void readFromNbt(NbtCompound nbt) {
		this.mode = nbt.contains("mode") ? nbt.getInt("mode") : MODE_BRIDGE;
	}

	@Override
	public void writeToNbt(NbtCompound nbt) {
		nbt.putInt("mode", this.mode);
	}
}
