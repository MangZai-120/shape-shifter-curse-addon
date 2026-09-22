package net.jackcooper.shapeShifterCurseAddon.client.renderer;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.jackcooper.shapeShifterCurseAddon.spell.ExplosionManager;
import net.jackcooper.shapeShifterCurseAddon.spell.ExplosionRules;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.world.ClientWorld;
import net.jackcooper.shapeShifterCurseAddon.client.CastingVisualState;
import net.jackcooper.shapeShifterCurseAddon.client.ExplosionEffects;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 爆裂魔法世界视觉渲染器（jackcooper，2026-09-22）：
 * 接收 {@code explosion_start} 快照（爆心 + 已流逝 tick），每 10t 校准，
 * 客户端按收包后的世界时间推进演出，无逐粒子网络包：
 * <ol>
 *   <li>地面 2 格红紫法阵（锁点并开始蓄力时出现）；</li>
 *   <li>第 27 秒（T-8s）主题音频起播，同时 0.15 格直径红色 16 棱圆柱光柱以 32 格/秒升至 128 格；</li>
 *   <li>光柱每经过 12/16/20/24/28/32/36 格高度，浮现半径 8/16/28/8/10/7/12 格法阵；</li>
 *   <li>第 34.1 秒红白球从中心长至 12 格，第 35 秒蓄力完成结算爆炸（纯视觉，类领域壳）；</li>
 *   <li>引爆后本地粒子覆盖直伤范围，服务端独立结算伤害。</li>
 * </ol>
 * 七层空中法阵各用独立几何，脚底及落点共用统一地面图案；粗线条、无文字符文，与离线预览共用几何。
 * 全部视觉开启深度测试，可被方块与实体正常遮挡（2026-09-22 用户改定，弃用穿墙效果）。
 */
@Environment(EnvType.CLIENT)
public final class ExplosionRenderer {
	private static final Map<UUID, View> SEQUENCES = new HashMap<>();
	private static ClientWorld world;
	private static final BufferBuilder BUFFER = new BufferBuilder(65536);

	private static final class View {
		final Vec3d center;
		int elapsed;
		boolean detonated;
		long receivedAt;
		final ExplosionEffects effects = new ExplosionEffects();
		View(Vec3d center, int elapsed, boolean detonated) { this.center = center; this.elapsed = elapsed; this.detonated = detonated; }
		float age(float delta) { return elapsed + Math.max(0, world.getTime() - receivedAt) + delta; }
	}

	private ExplosionRenderer() {}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(ExplosionManager.START, (client, handler, buf, sender) -> {
			var dimension = buf.readIdentifier();
			int count = buf.readVarInt();
			Map<UUID, View> parsed = new HashMap<>();
			for (int i = 0; i < count; i++) {
				UUID owner = buf.readUuid();
				Vec3d center = new Vec3d(buf.readDouble(), buf.readDouble(), buf.readDouble());
				int elapsed = buf.readVarInt();
				parsed.put(owner, new View(center, elapsed, buf.readBoolean()));
			}
			client.execute(() -> {
				if (client.world == null || !client.world.getRegistryKey().getValue().equals(dimension)) return;
				if (world != client.world) clearSequences();
				world = client.world;
				SEQUENCES.entrySet().removeIf(entry -> {
					if (parsed.containsKey(entry.getKey())) return false;
					if (!entry.getValue().detonated) entry.getValue().effects.stop();
					return true;
				});
				parsed.forEach((id, incoming) -> {
					View view = SEQUENCES.computeIfAbsent(id, key -> incoming);
					view.elapsed = incoming.elapsed;
					view.detonated = incoming.detonated;
					view.receivedAt = world.getTime();
				});
			});
		});
		net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT
				.register((handler, client) -> { clearSequences(); world = null; });
		WorldRenderEventsHolder.register();
		net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (client.world != world) { clearSequences(); world = client.world; }
			if (world == null) return;
			SEQUENCES.values().removeIf(view -> {
				if (world.getTime() - view.receivedAt <= 45) return false;
				view.effects.stop();
				return true;
			});
			for (View view : SEQUENCES.values()) view.effects.tick(world, view.center, (int) view.age(0), view.detonated);
		});
	}

	private static void clearSequences() {
		SEQUENCES.values().forEach(view -> view.effects.stop());
		SEQUENCES.clear();
	}

	/** 在世界渲染末阶段绘制；保留深度测试使法阵/光柱/球被地形与实体正常遮挡。 */
	private static final class WorldRenderEventsHolder {
		static void register() {
			net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents.LAST
					.register(ExplosionRenderer::render);
		}
	}

	private static void render(WorldRenderContext context) {
		if (context.world() == null) return;
		Vec3d camera = context.camera().getPos();
		Vec3d selected = net.jackcooper.shapeShifterCurseAddon.client.SpellcastClient.targetSelectionPreview();
		// 常态早退：无目标指针、无施法者法阵、无爆炸序列时不动渲染状态机，
		// 直接 return（省 BUFFER.begin + 7 次 RenderSystem 切换 + 空绘制）。
		boolean anyCaster = false;
		for (var player : context.world().getPlayers()) {
			if (player.isAlive() && !player.isSpectator() && CastingVisualState.isExplosion(player.getUuid())) {
				anyCaster = true;
				break;
			}
		}
		if (selected == null && !anyCaster && (world == null || world != context.world() || SEQUENCES.isEmpty())) return;
		BUFFER.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
		VertexConsumer vertices = BUFFER;
		if (selected != null) {
			var matrices = context.matrixStack();
			matrices.push();
			matrices.translate(selected.x - camera.x, selected.y - camera.y, selected.z - camera.z);
			drawTargetPointer(vertices, matrices.peek().getPositionMatrix());
			matrices.pop();
		}
		// Spell ID travels with the shared casting visual state, including other players.
		for (var player : context.world().getPlayers()) {
			if (!player.isAlive() || player.isSpectator() || !CastingVisualState.isExplosion(player.getUuid())) continue;
			Vec3d feet = player.getLerpedPos(context.tickDelta());
			if (feet.squaredDistanceTo(camera) > ExplosionRules.VIEW_RANGE * ExplosionRules.VIEW_RANGE) continue;
			var matrices = context.matrixStack();
			matrices.push();
			matrices.translate(feet.x - camera.x, feet.y + 0.025 - camera.y, feet.z - camera.z);
			double rotation = (player.age + context.tickDelta()) * 0.02;
			float alpha = CastingVisualState.circleAlpha(player.getUuid());
			CastingCircleRenderer.drawGround(vertices, matrices, 2, 0, rotation, alpha, MagicCircleGeometry.RED_PRIMARY);
			matrices.pop();
		}
		if (world == context.world()) for (View view : SEQUENCES.values()) {
			float age = view.age(context.tickDelta());
			var matrices = context.matrixStack();
			matrices.push();
			matrices.translate(view.center.x - camera.x, view.center.y - camera.y, view.center.z - camera.z);
			if (!view.detonated) {
				float elapsed = Math.min(ExplosionRules.EXPLODE_TICKS, age);
				renderGroundCircle(matrices, vertices, elapsed);
				renderBeam(matrices, vertices, elapsed);
				renderLayerCircles(matrices, vertices, elapsed);
				renderBall(matrices, vertices, elapsed);
			} else {
				// 终章（2026-09-22）：爆炸后法阵保持 0.7s，随后自上而下间隔 0.12s 逐个加速上飞并淡出
				renderFinale(matrices, vertices, age - ExplosionRules.EXPLODE_TICKS);
			}
			matrices.pop();
		}
		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		RenderSystem.disableCull();
		// 开启深度测试：法阵/光柱/红白球被方块与实体正常遮挡；不写深度避免半透明面片互相遮挡
		RenderSystem.enableDepthTest();
		RenderSystem.depthMask(false);
		RenderSystem.setShader(GameRenderer::getPositionColorProgram);
		try {
			BufferRenderer.drawWithGlobalProgram(BUFFER.end());
		} finally {
			RenderSystem.depthMask(true);
			RenderSystem.enableDepthTest();
			RenderSystem.enableCull();
			RenderSystem.disableBlend();
		}
	}

	// ---- 各阶段渲染 ----

	/** 定位指针（8 面体，2026-09-22 用户定稿）：下方保持倒四棱锥（1 格宽、2 格高、尖朝下到落点），
	 * 上方为底面对称的正四棱锥，高为下方的 1/3（2/3 格），顶尖朝上——整体成八面体轮廓。 */
	private static void drawTargetPointer(VertexConsumer vertices, Matrix4f matrix) {
		double[][] corners = {{-0.5, -0.5}, {0.5, -0.5}, {0.5, 0.5}, {-0.5, 0.5}};
		int[] colors = {0xFF3A32, 0xE02020, 0xB81420, 0xFF6550};
		// 下方倒四棱锥：底面在 y=2（1 格见方），尖点在原点（落点）
		for (int side = 0; side < 4; side++) {
			double[] a = corners[side], b = corners[(side + 1) % 4];
			quad(vertices, matrix, a[0], 2, a[1], b[0], 2, b[1], 0, 0, 0, 0, 0, 0, colors[side], 0.95f);
		}
		// 腰带面（y=2 方形底面）：上下两锥的分界面
		quad(vertices, matrix, -0.5, 2, -0.5, 0.5, 2, -0.5, 0.5, 2, 0.5, -0.5, 2, 0.5, 0xFF7660, 0.95f);
		// 上方正四棱锥：同底面对称，高 2/3 格，顶尖 y = 2 + 2/3
		double apex = 2.0 + 2.0 / 3.0;
		int[] topColors = {0xFF6550, 0xFF3A32, 0xE02020, 0xB81420};
		for (int side = 0; side < 4; side++) {
			double[] a = corners[side], b = corners[(side + 1) % 4];
			quad(vertices, matrix, a[0], 2, a[1], b[0], 2, b[1], 0.0, apex, 0.0, 0.0, 0.0, 0.0, topColors[side], 0.95f);
		}
	}

	/** 落点法阵：2 格半径红紫双色，与玩家脚底共用统一图案。 */
	private static void renderGroundCircle(net.minecraft.client.util.math.MatrixStack matrices,
	                                       VertexConsumer vertices, float elapsed) {
		float alpha = Math.min(1f, elapsed / 6f);
		CastingCircleRenderer.drawGround(vertices, matrices, 2.0, 0.025,
				elapsed * 0.02, alpha, MagicCircleGeometry.RED_PRIMARY);
	}

	/** 红色光柱：0.15 格直径 16 棱圆柱，从地面到当前渲染高度（爆前 0.3s 向下收缩归零）。 */
	private static void renderBeam(net.minecraft.client.util.math.MatrixStack matrices,
	                               VertexConsumer vertices, float elapsed) {
		double height = ExplosionRules.beamRenderHeight(elapsed);
		if (height <= 0) return;
		float alpha = 0.9f;
		double radius = ExplosionRules.BEAM_WIDTH / 2;
		Matrix4f matrix = matrices.peek().getPositionMatrix();
		// 16 棱圆柱侧面：相邻棱点连线成四边形，亮红→深红交替增强柱体立体感
		int sides = 16;
		for (int i = 0; i < sides; i++) {
			double a0 = 2 * Math.PI * i / sides, a1 = 2 * Math.PI * (i + 1) / sides;
			int color = i % 2 == 0 ? 0xFF5040 : 0xD03020;
			quad(vertices, matrix,
					Math.cos(a0) * radius, 0, Math.sin(a0) * radius,
					Math.cos(a1) * radius, 0, Math.sin(a1) * radius,
					Math.cos(a1) * radius, height, Math.sin(a1) * radius,
					Math.cos(a0) * radius, height, Math.sin(a0) * radius, color, alpha);
		}
	}

	/** 光柱途中七层法阵（各层图案固定，光柱经过后出现，旋转方向交替）。 */
	private static void renderLayerCircles(net.minecraft.client.util.math.MatrixStack matrices,
	                                       VertexConsumer vertices, float elapsed) {
		double height = ExplosionRules.beamHeight(elapsed);
		for (int layer = 0; layer < ExplosionRules.CIRCLE_HEIGHTS.length; layer++) {
			if (height < ExplosionRules.CIRCLE_HEIGHTS[layer]) break;
			float appeared = elapsed - (ExplosionRules.SOUND_START_TICKS
					+ (float) (ExplosionRules.CIRCLE_HEIGHTS[layer] / ExplosionRules.BEAM_BLOCKS_PER_TICK));
			float grow = Math.min(1f, appeared / 8f);
			drawMagicCircle(matrices, vertices, ExplosionRules.CIRCLE_HEIGHTS[layer],
					ExplosionRules.CIRCLE_RADII[layer] * grow,
					elapsed * (layer % 2 == 0 ? 0.015 : -0.012), grow, false, MagicCircleGeometry.layerStrokes(layer));
		}
	}

	/** 终章（2026-09-22 用户定稿）：爆炸后七层法阵原地保持 0.7s，随后自最上层（36格）向下
	 * 每隔 0.12s 启动一层，每层 0.5s 内快速加速上飞（0.2s 加速到末速）并线性淡出到消失；
	 * 末速自上而下 16/14/12/10/7/5/3 格每秒。光柱与红白球在爆炸时已消散，不参与终章。 */
	private static void renderFinale(net.minecraft.client.util.math.MatrixStack matrices,
	                                 VertexConsumer vertices, float sinceBlast) {
		if (sinceBlast < 0) return;
		// 冲击波球（2026-09-22 用户需求）：爆炸瞬间白色半透明球从爆心 0 半径扩张到 64 格，
		// 0.3s 内完成，透明度 10% 随扩张线性降到 0（与终章保持期重叠，先行消散）。
		if (sinceBlast < ExplosionRules.SHOCKWAVE_TICKS) {
			float progress = sinceBlast / (float) ExplosionRules.SHOCKWAVE_TICKS;
			renderShockwaveSphere(matrices, vertices, ExplosionRules.OUTER_RADIUS * progress,
					ExplosionRules.SHOCKWAVE_ALPHA * (1f - progress));
		}
		for (int layer = 0; layer < ExplosionRules.CIRCLE_HEIGHTS.length; layer++) {
			// 启动时刻：保持期结束（14t）后，自最上层（index 大）向下逐个延迟 0.12s（2.4t）
			int fromTop = ExplosionRules.CIRCLE_HEIGHTS.length - 1 - layer;
			float start = ExplosionRules.FINALE_HOLD_TICKS + fromTop * ExplosionRules.FINALE_STAGGER_TICKS;
			float since = sinceBlast - start;
			if (since < 0) {
				// 保持期：原地满透明度继续旋转
				drawMagicCircle(matrices, vertices, ExplosionRules.CIRCLE_HEIGHTS[layer],
						ExplosionRules.CIRCLE_RADII[layer], sinceBlast * (layer % 2 == 0 ? 0.015 : -0.012), 1f, false,
						MagicCircleGeometry.layerStrokes(layer));
				continue;
			}
			float progress = since / (float) ExplosionRules.FINALE_RISE_TICKS;
			if (progress >= 1f) continue; // 该层已飞完消失
			// 淡出：上升全程线性降到 0（末尾叠加快淡，避免尾拖影）
			float alpha = 1f - progress;
			double lift = ExplosionRules.finaleLift(fromTop, since);
			double rotation = (ExplosionRules.EXPLODE_TICKS + since) * (layer % 2 == 0 ? 0.015 : -0.012);
			drawMagicCircle(matrices, vertices, ExplosionRules.CIRCLE_HEIGHTS[layer] + lift,
					ExplosionRules.CIRCLE_RADII[layer], rotation, alpha, false, MagicCircleGeometry.layerStrokes(layer));
		}
	}

	/** 冲击波球：白色单色球壳（12 纬度 × 24 经度条带），透明度随扩张递减。 */
	private static void renderShockwaveSphere(net.minecraft.client.util.math.MatrixStack matrices,
	                                          VertexConsumer vertices, double radius, float alpha) {
		if (radius <= 0.05 || alpha <= 0f) return;
		Matrix4f matrix = matrices.peek().getPositionMatrix();
		int lats = 12, lons = 24;
		for (int lat = 0; lat < lats; lat++) {
			double phi0 = Math.PI * lat / lats, phi1 = Math.PI * (lat + 1) / lats;
			for (int lon = 0; lon < lons; lon++) {
				double theta0 = 2 * Math.PI * lon / lons, theta1 = 2 * Math.PI * (lon + 1) / lons;
				double s0 = Math.sin(phi0), c0 = Math.cos(phi0), s1 = Math.sin(phi1), c1 = Math.cos(phi1);
				sphereQuad(vertices, matrix, radius,
						Math.cos(theta0) * s0, c0, Math.sin(theta0) * s0,
						Math.cos(theta1) * s0, c0, Math.sin(theta1) * s0,
						Math.cos(theta1) * s1, c1, Math.sin(theta1) * s1,
						Math.cos(theta0) * s1, c1, Math.sin(theta0) * s1, 0xFFFFFF, alpha);
			}
		}
	}

	/** 爆前红白球：从中心生长至 12 格（类领域壳的简化单壳，红白双色条带）。 */
	private static void renderBall(net.minecraft.client.util.math.MatrixStack matrices,
	                               VertexConsumer vertices, float elapsed) {
		float since = elapsed - ExplosionRules.BALL_START_TICKS;
		if (since <= 0) return;
		float grow = Math.min(1f, since / (float) ExplosionRules.BALL_LEAD_TICKS);
		double radius = ExplosionRules.BALL_RADIUS * grow;
		Matrix4f matrix = matrices.peek().getPositionMatrix();
		// 纬度条带：红白交替（8 纬度 × 24 经度四边形条带）
		int lats = 8, lons = 24;
		for (int lat = 0; lat < lats; lat++) {
			double phi0 = Math.PI * lat / lats, phi1 = Math.PI * (lat + 1) / lats;
			int color = lat % 2 == 0 ? 0xFF5040 : 0xFFFFFF;
			float alpha = 0.5f + 0.3f * grow;
			for (int lon = 0; lon < lons; lon++) {
				double theta0 = 2 * Math.PI * lon / lons, theta1 = 2 * Math.PI * (lon + 1) / lons;
				double s0 = Math.sin(phi0), c0 = Math.cos(phi0), s1 = Math.sin(phi1), c1 = Math.cos(phi1);
				sphereQuad(vertices, matrix, radius,
						Math.cos(theta0) * s0, c0, Math.sin(theta0) * s0,
						Math.cos(theta1) * s0, c0, Math.sin(theta1) * s0,
						Math.cos(theta1) * s1, c1, Math.sin(theta1) * s1,
						Math.cos(theta0) * s1, c1, Math.sin(theta0) * s1, color, alpha);
			}
		}
	}

	// ---- 几何工具 ----

	/** 参考图重绘：粗线圆环与嵌套星形，无文字和装饰符号；红晕与亮色线条分层绘制。 */
	private static void drawMagicCircle(net.minecraft.client.util.math.MatrixStack matrices,
	                                    VertexConsumer vertices, double height, double radius,
	                                    double rotation, float alpha, boolean ground,
	                                    java.util.List<MagicCircleGeometry.Stroke> strokes) {
		CastingCircleRenderer.drawPattern(vertices, matrices, radius, height, rotation,
				alpha * (ground ? 1 : 0.94f), strokes, MagicCircleGeometry.GLOW_COLOR);
	}

	/** 竖直四边形（光柱侧面）。 */
	private static void quad(VertexConsumer vertices, Matrix4f matrix,
	                         double x0, double y0, double z0, double x1, double y1, double z1,
	                         double x2, double y2, double z2, double x3, double y3, double z3,
	                         int color, float alpha) {
		int r = (color >> 16) & 255, g = (color >> 8) & 255, b = color & 255;
		vertices.vertex(matrix, (float) x0, (float) y0, (float) z0).color(r / 255f, g / 255f, b / 255f, alpha).next();
		vertices.vertex(matrix, (float) x1, (float) y1, (float) z1).color(r / 255f, g / 255f, b / 255f, alpha).next();
		vertices.vertex(matrix, (float) x2, (float) y2, (float) z2).color(r / 255f, g / 255f, b / 255f, alpha).next();
		vertices.vertex(matrix, (float) x3, (float) y3, (float) z3).color(r / 255f, g / 255f, b / 255f, alpha).next();
	}

	/** 球面条带四边形（红白球）。 */
	private static void sphereQuad(VertexConsumer vertices, Matrix4f matrix, double radius,
	                               double ax, double ay, double az, double bx, double by, double bz,
	                               double cx, double cy, double cz, double dx, double dy, double dz,
	                               int color, float alpha) {
		quad(vertices, matrix,
				ax * radius, ay * radius, az * radius,
				bx * radius, by * radius, bz * radius,
				cx * radius, cy * radius, cz * radius,
				dx * radius, dy * radius, dz * radius, color, alpha);
	}
}
