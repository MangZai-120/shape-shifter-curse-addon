package net.jackcooper.shapeShifterCurseAddon.client.screen;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.jackcooper.shapeShifterCurseAddon.network.SscAddonNetworking;
import net.jackcooper.shapeShifterCurseAddon.screen.SpellResearchTableScreenHandler;
import net.jackcooper.shapeShifterCurseAddon.spell.FormationData;
import net.jackcooper.shapeShifterCurseAddon.spell.FormationElement;
import net.jackcooper.shapeShifterCurseAddon.spell.FormationKnowledgeComponent;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

/**
 * 法术研究台界面（jackcooper）。匠魂式双页签：
 * <ul>
 *   <li><b>法阵抄写页</b>：左侧纸/墨/尘三槽，右侧 4×3 滚动列表展示可抄写法阵（已学习），
 *       选中后底部「抄写」按钮发 C2S 包（服务端重验耗纸+对应系墨×等级）；</li>
 *   <li><b>法术学习页</b>：中间大滚动框展示已记录未学习的法阵，选中后右侧出现「学习」确认按钮，
 *       底部显示所需月尘（每级 2 个）；月尘放左侧槽。</li>
 * </ul>
 * <p>学习数据经 CCA 组件 {@link FormationKnowledgeComponent} 自动同步到客户端。</p>
 */
public class SpellResearchTableScreen extends HandledScreen<SpellResearchTableScreenHandler> {

	private static final Identifier TEXTURE = new Identifier("ssc_addon", "textures/gui/spell_research_table.png");
	/** 槽位凹槽材质（18×18，与注魔台/魔法书同款）。 */
	private static final Identifier SLOT_CELL = new Identifier("ssc_addon", "textures/gui/spellbook_slot.png");
	/** 魔法槽位列表面板（131×72：5×3 网格区 + 右侧滚动轨道，无指示位干净版）。 */
	private static final Identifier LIST_PANEL = new Identifier("ssc_addon", "textures/gui/magic_slot_panel.png");
	/** 显示包框（20×20，内部 16×16 显示法阵图标）。 */
	private static final Identifier FRAME = new Identifier("ssc_addon", "textures/gui/formation_frame.png");
	/** 滚动滑块（8×13，与轨道等宽）。 */
	private static final Identifier SCROLL_THUMB = new Identifier("ssc_addon", "textures/gui/scroll_thumb.png");
	/** 法阵物品图标。 */
	private static final Identifier FORMATION_ICON = new Identifier("ssc_addon", "textures/item/formation.png");

	/** 当前页签：0=法阵抄写，1=法术学习。 */
	private int tab = 0;
	/** 抄写页滚动偏移（行）。 */
	private int scribeScroll = 0;
	/** 学习页滚动偏移（行）。 */
	private int learnScroll = 0;
	/** 抄写页选中项索引（-1 = 未选）。 */
	private int scribeSelected = -1;
	/** 学习页选中项索引（-1 = 未选）。 */
	private int learnSelected = -1;

	/** 列表面板区（相对 GUI 左上角）：面板 131×72，5 列 × 3 行，CELL=22，格子起始面板内 (4,4)。 */
	private static final int PANEL_X = 38, PANEL_Y = 24, PANEL_W = 131, PANEL_H = 72;
	private static final int CELL = 22, CELLS_PER_ROW = 5, VISIBLE_ROWS = 3;
	/** 滚动轨道：x117..124（宽 8），滑块 8×13；静止位：顶部贴上限绿框 y6，底部贴下限绿框 y53。 */
	private static final int TRACK_X = 117, THUMB_W = 8, THUMB_H = 13;
	private static final int THUMB_Y_MIN = 6, THUMB_Y_MAX = 53;
	/** 滑块当前渲染位置（面板内 y，直接驱动渲染，不再从 scroll 反推）。 */
	private int thumbY = THUMB_Y_MIN;
	/** 滚动状态：滑块拖拽中。 */
	private boolean draggingThumb = false;

	private ButtonWidget tabButton;
	private ButtonWidget actionButton;

	public SpellResearchTableScreen(SpellResearchTableScreenHandler handler, PlayerInventory inventory, Text title) {
		super(handler, inventory, title);
		this.backgroundWidth = 176;
		this.backgroundHeight = 212;
		this.playerInventoryTitleY = this.backgroundHeight - 94;
	}

	@Override
	protected void init() {
		super.init();
		// 页签切换按钮（右上角）
		this.tabButton = this.addDrawableChild(ButtonWidget.builder(
				Text.translatable("gui.ssc_addon.research.tab_scribe"),
				b -> toggleTab()).dimensions(this.x + 121, this.y + 7, 48, 14).build());
		// 主操作按钮（抄写/学习，底部右刨）
		this.actionButton = this.addDrawableChild(ButtonWidget.builder(
				Text.translatable("gui.ssc_addon.research.scribe"),
				b -> onAction()).dimensions(this.x + 110, this.y + 98, 52, 18).build());
	}

	private void toggleTab() {
		this.tab = 1 - this.tab;
		this.scribeSelected = -1;
		this.learnSelected = -1;
		this.scribeScroll = 0;
		this.learnScroll = 0;
		refreshWidgets();
	}

	private void refreshWidgets() {
		if (this.tabButton != null) {
			this.tabButton.setMessage(Text.translatable(this.tab == 0
					? "gui.ssc_addon.research.tab_scribe" : "gui.ssc_addon.research.tab_learn"));
		}
		if (this.actionButton != null) {
			this.actionButton.setMessage(Text.translatable(this.tab == 0
					? "gui.ssc_addon.research.scribe" : "gui.ssc_addon.research.learn"));
		}
	}

	/** 主操作按钮：按页签发对应 C2S 包（服务端权威重验）。 */
	private void onAction() {
		net.minecraft.network.PacketByteBuf buf = net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();
		if (this.tab == 0) {
			if (this.scribeSelected < 0) {
				return;
			}
			buf.writeString(scribeEntries()[this.scribeSelected].id);
			buf.writeVarInt(scribeEntries()[this.scribeSelected].level);
			ClientPlayNetworking.send(SscAddonNetworking.PACKET_FORMATION_SCRIBE, buf);
		} else {
			if (this.learnSelected < 0) {
				return;
			}
			buf.writeString(learnEntries()[this.learnSelected].id);
			buf.writeVarInt(learnEntries()[this.learnSelected].level);
			ClientPlayNetworking.send(SscAddonNetworking.PACKET_FORMATION_LEARN, buf);
		}
	}

	// ---- 列表条目（客户端从本地 CCA 同步数据读取） ----

	/** 列表条目：系别 + 等级。 */
	private record Entry(String id, int level) {
	}

	/** 抄写页条目：已学习的全部法阵（各系最高等级以内全部可抄）。 */
	private Entry[] scribeEntries() {
		java.util.List<Entry> list = new java.util.ArrayList<>();
		if (this.client != null && this.client.player != null) {
			FormationKnowledgeComponent knowledge = FormationKnowledgeComponent.get(this.client.player);
			for (FormationElement element : FormationElement.values()) {
				int learned = knowledge.getLearnedLevel(element);
				for (int lv = 1; lv <= learned; lv++) {
					list.add(new Entry(element.id, lv));
				}
			}
		}
		return list.toArray(new Entry[0]);
	}

	/** 学习页条目：已记录但未学习到位的等级（已学习的不放上去）。 */
	private Entry[] learnEntries() {
		java.util.List<Entry> list = new java.util.ArrayList<>();
		if (this.client != null && this.client.player != null) {
			FormationKnowledgeComponent knowledge = FormationKnowledgeComponent.get(this.client.player);
			for (FormationElement element : FormationElement.values()) {
				int learned = knowledge.getLearnedLevel(element);
				for (int lv = learned + 1; lv <= FormationData.MAX_FORMATION_LEVEL; lv++) {
					if (knowledge.hasRecorded(element, lv)) {
						list.add(new Entry(element.id, lv));
					}
				}
			}
		}
		return list.toArray(new Entry[0]);
	}

	/** 滚动滑块按下检测也需 ≤3 行锁定：不满页时不可拖。 */
	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		int px = (int) mouseX - this.x;
		int py = (int) mouseY - this.y;
		// 滚动滑块拖拽命中（轨道整段：x117..124 × y4..68；左键起拖；≤3 行锁定不可拖）
		if (button == 0 && maxScroll() > 0 && px >= PANEL_X + TRACK_X && px < PANEL_X + TRACK_X + THUMB_W
				&& py >= PANEL_Y + 4 && py < PANEL_Y + 68) {
			this.draggingThumb = true;
			updateThumbDrag(mouseY);
			return true;
		}
		// 列表区点击选中（先于槽位处理；命中列表区时吞掉事件）
		if (inGridArea(px, py)) {
			int col = (px - gridOriginX()) / CELL;
			int row = (py - gridOriginY()) / CELL;
			int index = (currentScroll() + row) * CELLS_PER_ROW + col;
			Entry[] entries = currentEntries();
			if (index >= 0 && index < entries.length) {
				setCurrentSelected(index);
				return true;
			}
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
		if (this.draggingThumb) {
			updateThumbDrag(mouseY);
			return true;
		}
		return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
	}

	/** 拖拽中：滑块随鼠标实时走，并联动列表滚动量（松手时由 mouseReleased 吸附到行档位）。 */
	private void updateThumbDrag(double mouseY) {
		int py = (int) mouseY - this.y;
		this.thumbY = Math.max(THUMB_Y_MIN, Math.min(THUMB_Y_MAX, py - PANEL_Y - THUMB_H / 2));
		float progress = (float) (this.thumbY - THUMB_Y_MIN) / (THUMB_Y_MAX - THUMB_Y_MIN);
		int maxScroll = maxScroll();
		setCurrentScroll(Math.max(0, Math.min(maxScroll, Math.round(progress * maxScroll))));
	}

	/** 滚轮滚动后同步滑块渲染位置（行档位对齐）。 */
	private void syncThumbFromScroll() {
		int maxScroll = maxScroll();
		float progress = maxScroll <= 0 ? 0 : (float) currentScroll() / maxScroll;
		this.thumbY = THUMB_Y_MIN + Math.round(Math.max(0f, Math.min(1f, progress)) * (THUMB_Y_MAX - THUMB_Y_MIN));
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		if (this.draggingThumb) {
			this.draggingThumb = false;
			// 松手回弹：滑块回到与滚动量一致的合法位置
			syncThumbFromScroll();
			return true;
		}
		return super.mouseReleased(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
		int px = (int) mouseX - this.x;
		int py = (int) mouseY - this.y;
		// 面板区域滚轮：一次滚一行，滑块直接跳到对应行档位；≤3 行锁定不动
		if (px >= PANEL_X && px < PANEL_X + PANEL_W && py >= PANEL_Y && py < PANEL_Y + PANEL_H) {
			int maxScroll = maxScroll();
			if (maxScroll > 0) {
				setCurrentScroll(Math.max(0, Math.min(maxScroll, currentScroll() - (int) Math.signum(amount))));
				syncThumbFromScroll();
			}
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, amount);
	}

	/** 最大滚动偏移（总行数 - 可见 3 行；≤0 表示无需滚动）。 */
	private int maxScroll() {
		return Math.max(0, currentEntries().length - CELLS_PER_ROW * VISIBLE_ROWS);
	}

	/** 当前页签的列表条目。 */
	private Entry[] currentEntries() {
		return this.tab == 0 ? scribeEntries() : learnEntries();
	}

	/** 当前页签的滚动偏移。 */
	private int currentScroll() {
		return this.tab == 0 ? this.scribeScroll : this.learnScroll;
	}

	private void setCurrentScroll(int v) {
		if (this.tab == 0) {
			this.scribeScroll = v;
		} else {
			this.learnScroll = v;
		}
	}

	private int currentSelected() {
		return this.tab == 0 ? this.scribeSelected : this.learnSelected;
	}

	private void setCurrentSelected(int v) {
		if (this.tab == 0) {
			this.scribeSelected = v;
		} else {
			this.learnSelected = v;
		}
	}

	/** 格子网格原点（面板内网格区起始，相对 GUI）。 */
	private int gridOriginX() {
		return PANEL_X + 4;
	}

	private int gridOriginY() {
		return PANEL_Y + 4;
	}

	/** 命中格子网格区（含 5×3 格）。 */
	private boolean inGridArea(int px, int py) {
		return px >= gridOriginX() && px < gridOriginX() + CELLS_PER_ROW * CELL
				&& py >= gridOriginY() && py < gridOriginY() + VISIBLE_ROWS * CELL;
	}

	@Override
	protected void drawBackground(DrawContext ctx, float delta, int mouseX, int mouseY) {
		int x = (this.width - this.backgroundWidth) / 2;
		int y = (this.height - this.backgroundHeight) / 2;
		ctx.drawTexture(TEXTURE, x, y, 0, 0, this.backgroundWidth, this.backgroundHeight, 256, 256);
		// 4 个功能槽凹槽（背景只留纯面板，与注魔台同款）
		for (int i = 0; i < 4; i++) {
			Slot slot = this.handler.slots.get(i);
			ctx.drawTexture(SLOT_CELL, x + slot.x - 1, y + slot.y - 1, 0, 0, 18, 18, 18, 18);
		}
		// 列表页（面板/包框/滑块都在背景层绘制，屏幕坐标）
		drawListTab(ctx);
	}

	@Override
	protected void drawForeground(DrawContext ctx, int mouseX, int mouseY) {
		ctx.drawText(this.textRenderer, this.title, this.titleX, this.titleY, 0x000000, false);
		ctx.drawText(this.textRenderer, this.playerInventoryTitle,
				this.playerInventoryTitleX, this.playerInventoryTitleY, 0x404040, false);
	}

	/** 列表页渲染（背景层，屏幕坐标）：魔法槽位面板 + 显示包框 + 图标 + 滚动滑块。 */
	private void drawListTab(DrawContext ctx) {
		Entry[] entries = currentEntries();
		int px = this.x + PANEL_X;
		int py = this.y + PANEL_Y;
		// 面板底图（无指示位干净版，无绿色标记）
		ctx.drawTexture(LIST_PANEL, px, py, 0, 0, PANEL_W, PANEL_H, PANEL_W, PANEL_H);
		if (entries.length == 0) {
			drawEmptyHint(ctx);
			return;
		}
		int selected = currentSelected();
		int scroll = currentScroll();
		for (int i = 0; i < VISIBLE_ROWS * CELLS_PER_ROW; i++) {
			int index = scroll + i;
			if (index >= entries.length) {
					break;
			}
			int col = i % CELLS_PER_ROW;
			int row = i / CELLS_PER_ROW;
			int cx = px + 4 + col * CELL;
			int cy = py + 4 + row * CELL;
			// 选中格：白色 1px 细边框标记（不填充，避免白块遮图标）
			if (index == selected) {
				ctx.fill(cx, cy, cx + 20, cy + 1, 0xFFFFFFFF);
				ctx.fill(cx, cy + 19, cx + 20, cy + 20, 0xFFFFFFFF);
				ctx.fill(cx, cy, cx + 1, cy + 20, 0xFFFFFFFF);
				ctx.fill(cx + 19, cy, cx + 20, cy + 20, 0xFFFFFFFF);
			}
			// 显示包框（20×20）+ 法阵图标（16×16 居中）
			ctx.drawTexture(FRAME, cx, cy, 0, 0, 20, 20, 20, 20);
			ctx.drawTexture(FORMATION_ICON, cx + 2, cy + 2, 0, 0, 16, 16, 16, 16);
			// 等级角标（包框右下角，品质色）
			int lv = entries[index].level;
			ctx.drawText(this.textRenderer, String.valueOf(lv), cx + 14, cy + 12,
					0xFF000000 | FormationData.getRarity(lv).color.getColorValue(), false);
		}
		// 滚动滑块（按 thumbY 渲染；拖拽限位在 y6~53，滚轮经 syncThumbFromScroll 同步）
		if (entries.length > 0) {
			ctx.drawTexture(SCROLL_THUMB, px + TRACK_X, py + this.thumbY, 0, 0, THUMB_W, THUMB_H, THUMB_W, THUMB_H);
		}
		// 选中项耗材提示（学习页显示所需月尘，与左侧耗材槽左缘对齐）
		if (this.tab != 0 && selected >= 0 && selected < entries.length) {
			int dust = learnDustCost(entries[selected].level);
			ctx.drawText(this.textRenderer, Text.translatable("gui.ssc_addon.research.learn_cost", dust),
					this.x + 14, this.y + 104, 0x7070C0, false);
		}
	}

	/** 空列表提示：面板中心两行居中（避免溢出与重叠）。 */
	private void drawEmptyHint(DrawContext ctx) {
		int center = PANEL_X + PANEL_W / 2;
		int y = PANEL_Y + 26;
		String mainKey = this.tab == 0 ? "gui.ssc_addon.research.scribe_empty" : "gui.ssc_addon.research.learn_empty";
		Text main = Text.translatable(mainKey);
		ctx.drawTextWithShadow(this.textRenderer, main, this.x + center - this.textRenderer.getWidth(main) / 2, this.y + y, 0xB0B0B0);
		if (this.tab != 0) {
			Text hint = Text.translatable("gui.ssc_addon.research.learn_empty_hint");
			ctx.drawTextWithShadow(this.textRenderer, hint, this.x + center - this.textRenderer.getWidth(hint) / 2, this.y + y + 10, 0x707070);
		}
	}

	/** 学习耗材：每级 2 个未加工月之尘（L1=2、L2=4…）。 */
	public static int learnDustCost(int level) {
		return level * 2;
	}

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		// 拖拽滑块兑底：即使 mouseDragged 事件未被派发，按住期间每帧也主动同步位置
		if (this.draggingThumb && this.client != null && this.client.mouse != null
				&& GLFW.glfwGetMouseButton(this.client.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS) {
			updateThumbDrag(mouseY);
		}
		super.render(ctx, mouseX, mouseY, delta);
	}
}
