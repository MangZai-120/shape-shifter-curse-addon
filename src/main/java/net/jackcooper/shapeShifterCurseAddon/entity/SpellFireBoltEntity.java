package net.jackcooper.shapeShifterCurseAddon.entity;

import net.jackcooper.shapeShifterCurseAddon.spell.SpellExpGrant;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.FlyingItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.jackcooper.shapeShifterCurseAddon.SscAddon;
import net.jackcooper.shapeShifterCurseAddon.util.ParticleUtils;
import net.jackcooper.shapeShifterCurseAddon.util.WhitelistUtils;

/**
 * 鏈堝皹榄旀硶路鐏悆鎶曞皠鐗╋紙jackcooper锛夈€傜粨鏋勪笌 {@link SpellFrostSpikeEntity} 鍚岃寖寮忥細
 * 鏈濆噯鏄熺洿绾垮寑閫熼琛岋紙鏃犻噸鍔涳級锛屽懡涓€犳垚榄旀硶浼ゅ骞?b>鐐圭噧</b>鐩爣鍚庢秷澶憋紱瓒呰窛/瓒呮椂鑷瘉銆?
 * 榛樿鐧藉悕鍗曪細涓讳汉鍦ㄧ嚎涓旂洰鏍囧彈淇濇姢鍒欎笉浼ゅ銆佷笉鐐圭噧銆?
 */
public class SpellFireBoltEntity extends ProjectileEntity implements FlyingItemEntity {

	private static final double SPEED = 0.8;         // 16 鏍?绉?
	private static final double MAX_DISTANCE = 50.0; // 鏈€澶ч琛岃窛绂?

	/** 榄旀硶绛夌骇锛?-5锛夛紝DataTracker 鍚屾锛堥鐣欐覆鏌撶鎹㈡ā鍨?缂╂斁锛屽綋鍓嶄粎瀛樻。鐢級銆?*/
	private static final TrackedData<Integer> LEVEL =
			DataTracker.registerData(SpellFireBoltEntity.class, TrackedDataHandlerRegistry.INTEGER);

	private Vec3d startPos;
	private int ticksAlive = 0;
	private float damage = 5.0f;
	/** 鍛戒腑鐐圭噧鏃堕暱锛坱ick锛夈€?*/
	private int fireTicks = 60;

	public SpellFireBoltEntity(EntityType<? extends SpellFireBoltEntity> entityType, World world) {
		super(entityType, world);
		this.startPos = this.getPos();
	}

	public SpellFireBoltEntity(World world, LivingEntity owner) {
		super(SscAddon.SPELL_FIRE_BOLT_ENTITY, world);
		this.setOwner(owner);
		this.setPosition(owner.getX(), owner.getEyeY() - 0.1, owner.getZ());
		this.startPos = this.getPos();
	}

	@Override
	protected void initDataTracker() {
		// 涓嶈皟 super锛圗ntity 鐨勬娊璞℃柟娉曟棤娉曡法涓ょ骇璁块棶锛沝ataTracker 鐢?Entity 鏋勯€犲櫒鍒濆鍖栵紝鍚屽啺閿ュ啓娉曪級
		this.dataTracker.startTracking(LEVEL, 1);
	}

	/** 璁剧疆鐏悆鍛戒腑浼ゅ銆?*/
	public void setDamage(float damage) {
		this.damage = damage;
	}
	/** 鍛戒腑鍙戞斁鐨勭粡楠岃祻閲戯紙脳10 鏁存暟锛沞xp_mode 1/2 鎸傝捣閮ㄥ垎鐢辨柦娉曟椂瑁呭叆锛孨BT 鎸佷箙鍖栬法 tick锛夈€?*/
	private int expBountyTen = 0;

	public void setExpBountyTen(int expTen) {
		this.expBountyTen = Math.max(0, expTen);
	}

	public int getExpBountyTen() {
		return expBountyTen;
	}
	/** 榄旀硶绛夌骇锛?-5锛夈€?*/
	public int getSpellLevel() {
		return this.dataTracker.get(LEVEL);
	}

	/** 璁剧疆榄旀硶绛夌骇锛堟湇鍔＄鏂芥硶鏃惰皟鐢紝DataTracker 鑷姩鍚屾瀹㈡埛绔級銆?*/
	public void setLevel(int level) {
		this.dataTracker.set(LEVEL, Math.max(1, Math.min(5, level)));
	}

	/** 璁剧疆鍛戒腑鐐圭噧鏃堕暱锛坱ick锛夈€?*/
	public void setFireTicks(int fireTicks) {
		this.fireTicks = fireTicks;
	}

	/** 璁剧疆椋炶鏂瑰悜锛堟湞鍑嗘槦锛夛紝閫熷害鎸夌瓑绾у€嶇巼缂╂斁銆?*/
	public void setDirection(Vec3d direction, float speedMultiplier) {
		Vec3d velocity = direction.normalize().multiply(SPEED * speedMultiplier);
		this.setVelocity(velocity.x, velocity.y, velocity.z);
		updateRotationFromVelocity(velocity);
	}

	/** 鎸夐€熷害鑷畻鏈濆悜锛堝悓鍐伴敟鍏紡锛夛紝渚涙覆鏌撳姝ｃ€?*/
	private void updateRotationFromVelocity(Vec3d v) {
		double horiz = Math.sqrt(v.x * v.x + v.z * v.z);
		this.setYaw((float) (MathHelper.atan2(-v.x, v.z) * (180.0 / Math.PI)));
		this.setPitch((float) (MathHelper.atan2(-v.y, horiz) * (180.0 / Math.PI)));
		this.prevYaw = this.getYaw();
		this.prevPitch = this.getPitch();
	}

	@Override
	public void tick() {
		super.tick();
		ticksAlive++;

		// 鏃犻噸鍔涘寑閫熺Щ鍔?
		Vec3d velocity = this.getVelocity();
		this.setPosition(this.getX() + velocity.x, this.getY() + velocity.y, this.getZ() + velocity.z);

		// 纰版挒妫€娴?
		HitResult hitResult = ProjectileUtil.getCollision(this, this::canHit);
		if (hitResult.getType() != HitResult.Type.MISS) {
			this.onCollision(hitResult);
		}
		if (this.isRemoved()) {
			return;
		}

		// 瓒呰窛 / 瓒呮椂鑷瘉锛堜粎鏈嶅姟绔潈濞侊紝鐞嗙敱鍚屽啺閿ワ細瀹㈡埛绔疄浣?startPos 鎭掑師鐐癸紝鍙岀鍒や細璇垹锛?
		if (!this.getWorld().isClient) {
			if (startPos != null && this.squaredDistanceTo(startPos) > MAX_DISTANCE * MAX_DISTANCE) {
				this.discard();
				return;
			}
			if (ticksAlive > 100) { // 5 绉掕秴鏃?
				this.discard();
				return;
			}
		}

		// 椋炶鎷栧熬绮掑瓙锛堟湇鍔＄鎾掞紝澶╃劧澶氫汉鍚屾锛?
		if (this.getWorld() instanceof ServerWorld serverWorld) {
			ParticleUtils.spawnParticles(serverWorld, ParticleTypes.FLAME,
					this.getX(), this.getY(), this.getZ(), 2, 0.1, 0.1, 0.1, 0.01);
		}
	}

	@Override
	protected void onCollision(HitResult hitResult) {
		super.onCollision(hitResult);
		if (!this.getWorld().isClient) {
			this.getWorld().playSound(null, this.getX(), this.getY(), this.getZ(),
					SoundEvents.ENTITY_BLAZE_SHOOT, SoundCategory.PLAYERS, 1.0f, 1.4f);
			if (this.getWorld() instanceof ServerWorld serverWorld) {
				ParticleUtils.spawnParticles(serverWorld, ParticleTypes.FLAME,
						this.getX(), this.getY(), this.getZ(), 15, 0.3, 0.3, 0.3, 0.1);
			}
			this.discard();
		}
	}

	@Override
	protected void onEntityHit(EntityHitResult entityHitResult) {
		super.onEntityHit(entityHitResult);
		Entity target = entityHitResult.getEntity();
		if (target instanceof LivingEntity livingTarget && !this.getWorld().isClient) {
			// 榛樿鐧藉悕鍗曪細涓讳汉鍦ㄧ嚎涓旂洰鏍囧彈淇濇姢 鈫?涓嶉€犳垚浼ゅ銆佷笉鐐圭噧
			if (this.getOwner() instanceof ServerPlayerEntity ownerPlayer
					&& WhitelistUtils.isProtected(ownerPlayer, livingTarget)) {
				return;
			}
			if (this.getOwner() instanceof LivingEntity owner) {
				// 法术伤害专用类型（ssc_addon:spell_damage）：供法术抗性附魔精确识别（jackcooper）
				livingTarget.damage(net.jackcooper.shapeShifterCurseAddon.spell.SpellDamageSource
						.of(this.getDamageSources(), owner), damage);
			} else {
				livingTarget.damage(net.jackcooper.shapeShifterCurseAddon.spell.SpellDamageSource
						.of(this.getDamageSources()), damage);
			}
			// exp_mode 1/2 鍛戒腑琛ュ彂锛歞amage 鎴愬姛鎵嶅彂鏀撅紝鍙戞斁鍚庢竻闆堕槻閲嶅
			if (livingTarget.hurtTime > 0 && this.getOwner() instanceof ServerPlayerEntity ownerPlayer) {
				SpellExpGrant.grant(ownerPlayer, expBountyTen);
				expBountyTen = 0;
			}
			// 鐐圭噧鐩爣锛堜激瀹虫暟鍊煎鐨勫浐瀹氶檮鍔犳晥鏋滐紱鏃堕暱鐢辨柦娉曟椂鎸夌瓑绾у啓鍏ワ級
			livingTarget.setFireTicks(fireTicks);
			this.getWorld().playSound(null, target.getX(), target.getY(), target.getZ(),
					SoundEvents.ENTITY_PLAYER_HURT_ON_FIRE, SoundCategory.PLAYERS, 1.0f, 1.0f);
		}
	}

	@Override
	protected boolean canHit(Entity entity) {
		return super.canHit(entity) && entity != this.getOwner() && entity instanceof LivingEntity;
	}

	@Override
	public void readCustomDataFromNbt(NbtCompound nbt) {
		super.readCustomDataFromNbt(nbt);
		if (nbt.contains("StartX")) {
			this.startPos = new Vec3d(nbt.getDouble("StartX"), nbt.getDouble("StartY"), nbt.getDouble("StartZ"));
		}
		if (nbt.contains("Damage")) {
			this.damage = nbt.getFloat("Damage");
		}
		if (nbt.contains("SpellLevel")) {
			setLevel(nbt.getInt("SpellLevel"));
		}
		if (nbt.contains("FireTicks")) {
			this.fireTicks = nbt.getInt("FireTicks");
		}
		if (nbt.contains("ExpBountyTen")) {
			this.expBountyTen = Math.max(0, nbt.getInt("ExpBountyTen"));
		}
	}

	@Override
	public void writeCustomDataToNbt(NbtCompound nbt) {
		super.writeCustomDataToNbt(nbt);
		if (startPos != null) {
			nbt.putDouble("StartX", startPos.x);
			nbt.putDouble("StartY", startPos.y);
			nbt.putDouble("StartZ", startPos.z);
		}
		nbt.putFloat("Damage", this.damage);
		nbt.putInt("SpellLevel", getSpellLevel());
		nbt.putInt("FireTicks", this.fireTicks);
		nbt.putInt("ExpBountyTen", this.expBountyTen);
	}

	@Override
	public Packet<ClientPlayPacketListener> createSpawnPacket() {
		return new EntitySpawnS2CPacket(this);
	}

	@Override
	public ItemStack getStack() {
		return new ItemStack(Items.FIRE_CHARGE);
	}
}
