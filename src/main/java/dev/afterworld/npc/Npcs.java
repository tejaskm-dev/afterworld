package dev.afterworld.npc;

import dev.afterworld.Afterworld;
import dev.afterworld.mixin.MannequinAccessor;
import dev.afterworld.registry.AwAttachments;
import dev.afterworld.world.AfterworldData;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.Vec3;

/**
 * The Afterworld's people are vanilla mannequins wearing a painted skin, a set of garb and an
 * {@link AwAttachments#NPC} mark. They never walk; they turn to look at whoever comes near, face
 * whoever is talking to them, and look about when alone. Right-clicking one starts a conversation.
 */
public final class Npcs {
	private static final Set<Mannequin> LOADED = new ReferenceOpenHashSet<>();
	/** How far an NPC notices a traveller, and how far a conversation may stretch. */
	public static final double NOTICE = 8.0;
	public static final double TALK_RANGE = 7.0;

	private Npcs() {
	}

	public static void init() {
		ServerEntityEvents.ENTITY_LOAD.register((e, level) -> {
			if (e instanceof Mannequin m && npcOf(m) != null) {
				LOADED.add(m);
			}
		});
		ServerEntityEvents.ENTITY_UNLOAD.register((e, level) -> {
			if (e instanceof Mannequin m) {
				LOADED.remove(m);
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(Npcs::tick);
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (!(entity instanceof Mannequin m)) {
				return InteractionResult.PASS;
			}
			Npc npc = npcOf(m);
			if (npc == null) {
				return InteractionResult.PASS;
			}
			if (hand != InteractionHand.MAIN_HAND) {
				return InteractionResult.SUCCESS;
			}
			if (player instanceof ServerPlayer sp) {
				// A delivery from the Warden's Board changes hands before anything is said.
				dev.afterworld.economy.Board.onTalk(sp, npc);
				Dialogue.start(sp, m, npc);
			}
			return InteractionResult.SUCCESS;
		});
		// Nobody strikes the Wardens' people, not even by accident (creative builders excepted).
		AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) ->
			npcOf(entity) != null && !player.isCreative() ? InteractionResult.FAIL : InteractionResult.PASS);
	}

	public static Npc npcOf(Entity e) {
		String id = e.getAttached(AwAttachments.NPC);
		return id == null ? null : Npc.byId(id);
	}

	/** The skin: a profile with no account behind it, patched to wear the NPC's painted texture. */
	static ResolvableProfile profile(Npc npc) {
		CompoundTag t = new CompoundTag();
		t.putString("texture", Afterworld.MOD_ID + ":entity/npc/" + npc.id);
		t.putString("model", npc.slim ? "slim" : "wide");
		return ResolvableProfile.CODEC.parse(NbtOps.INSTANCE, t).getOrThrow();
	}

	/** Makes (but does not add) an NPC standing at the bottom centre of {@code pos}, facing {@code yaw}. */
	public static Mannequin create(ServerLevel level, Npc npc, BlockPos pos, float yaw) {
		Mannequin m = EntityTypes.MANNEQUIN.create(level, EntitySpawnReason.STRUCTURE);
		if (m == null) {
			return null;
		}
		m.snapTo(Vec3.atBottomCenterOf(pos), yaw, 0.0F);
		m.setYHeadRot(yaw);
		m.setYBodyRot(yaw);
		MannequinAccessor a = (MannequinAccessor) m;
		a.afterworld$setProfile(profile(npc));
		a.afterworld$setImmovable(true);
		a.afterworld$setDescription(Component.literal(npc.role).withColor(0xB8AE98));
		m.setCustomName(Component.literal(npc.displayName).withColor(npc.color));
		m.setCustomNameVisible(true);
		m.setPermanentlyInvulnerable(true);
		m.setSilent(true);
		if (npc.mainHand != Items.AIR) {
			m.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(npc.mainHand));
		}
		if (npc.offHand != Items.AIR) {
			m.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(npc.offHand));
		}
		m.setAttached(AwAttachments.GARB, npc.garb);
		m.setAttached(AwAttachments.NPC, npc.id);
		return m;
	}

	/** Stands an NPC in the world now, and remembers where it lives. */
	public static Mannequin spawn(ServerLevel level, Npc npc, BlockPos pos, float yaw) {
		Mannequin m = create(level, npc, pos, yaw);
		if (m != null) {
			level.addFreshEntity(m);
			home(level.getServer(), npc, pos);
		}
		return m;
	}

	static void home(MinecraftServer server, Npc npc, BlockPos pos) {
		AfterworldData data = AfterworldData.get(server);
		data.npcHomes.put(npc.id, pos.asLong());
		data.setDirty();
	}

	/** Where an NPC was last stood, if the world knows. */
	public static BlockPos home(MinecraftServer server, Npc npc) {
		Long at = AfterworldData.get(server).npcHomes.get(npc.id);
		return at == null ? null : BlockPos.of(at);
	}

	/**
	 * A marker in a placed wonder: {@code npc:<id>[:<facing>]} stands that keeper there. Called on
	 * world generation threads for landmarks (the entity goes into the chunk being generated), and
	 * on the server thread for the camp.
	 */
	public static void marker(ServerLevelAccessor level, BlockPos pos, String name, Rotation r) {
		if (name.startsWith("mob:") || name.startsWith("elite:")) {
			creatureMarker(level, pos, name);
			return;
		}
		if (name.startsWith("boss:")) {
			bossMarker(level, pos, name);
			return;
		}
		if (name.startsWith("seal:")) {
			dev.afterworld.block.SealBlockEntity.fromMarker(level, pos, name, r);
			return;
		}
		if (name.startsWith("ladder:")) {
			dev.afterworld.dungeon.Ladder.marker(pos, name);
			return;
		}
		if (name.startsWith("lore:")) {
			if (level.getBlockEntity(pos) instanceof dev.afterworld.block.ClueBlockEntity clue) {
				clue.setLore(name.substring(5));
			}
			return;
		}
		String[] parts = name.split(":");
		if (parts.length >= 2 && parts[0].equals("npc")) {
			Npc npc = Npc.byId(parts[1]);
			if (npc == null) {
				Afterworld.LOG.warn("Unknown NPC marker {}", name);
				return;
			}
			Direction facing = parts.length >= 3 ? Direction.byName(parts[2]) : null;
			float yaw = facing == null || facing.getAxis().isVertical() ? 0.0F : r.rotate(facing).toYRot();
			Mannequin m = create(level.getLevel(), npc, pos, yaw);
			if (m != null) {
				level.addFreshEntity(m);
				if (level instanceof ServerLevel sl) {
					home(sl.getServer(), npc, pos);
				}
			}
		}
	}

	/**
	 * {@code mob:<entity>[:<count>]} stands creatures at a site; {@code elite:<entity>} a guardian:
	 * tougher, glowing, named, and carrying better spoils.
	 */
	private static void creatureMarker(ServerLevelAccessor level, BlockPos pos, String name) {
		boolean elite = name.startsWith("elite:");
		String rest = name.substring(elite ? 6 : 4);
		int count = 1;
		int colon = rest.lastIndexOf(':');
		if (colon > rest.indexOf(':')) {
			try {
				count = Integer.parseInt(rest.substring(colon + 1));
				rest = rest.substring(0, colon);
			} catch (NumberFormatException ignored) {
				// not a count: part of the id
			}
		}
		var type = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getOptional(net.minecraft.resources.Identifier.parse(rest));
		if (type.isEmpty()) {
			Afterworld.LOG.warn("Unknown creature marker {}", name);
			return;
		}
		for (int i = 0; i < count; i++) {
			var e = type.get().create(level.getLevel(), net.minecraft.world.entity.EntitySpawnReason.STRUCTURE);
			if (!(e instanceof net.minecraft.world.entity.Mob mob)) {
				continue;
			}
			mob.snapTo(pos.getX() + 0.5 + (i % 2) * 0.6, pos.getY(), pos.getZ() + 0.5 + (i / 2) * 0.6, level.getRandom().nextFloat() * 360, 0);
			mob.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), net.minecraft.world.entity.EntitySpawnReason.STRUCTURE, null);
			mob.setPersistenceRequired();
			if (elite) {
				makeElite(mob);
			}
			level.addFreshEntity(mob);
		}
	}

	/**
	 * {@code boss:<entity>[:dormant]}: a Warden keeps this lair. A dormant one sleeps (stone, still,
	 * untouchable) until a seal's riddle is solved or an offering is made nearby.
	 */
	private static void bossMarker(ServerLevelAccessor level, BlockPos pos, String name) {
		String[] parts = name.split(":");
		if (parts.length < 3) {
			return;
		}
		boolean dormant = parts.length >= 4 && parts[3].equals("dormant");
		var type = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getOptional(net.minecraft.resources.Identifier.fromNamespaceAndPath(parts[1], parts[2]));
		if (type.isEmpty()) {
			Afterworld.LOG.warn("Unknown boss marker {}", name);
			return;
		}
		var e = type.get().create(level.getLevel(), net.minecraft.world.entity.EntitySpawnReason.STRUCTURE);
		if (e instanceof dev.afterworld.entity.boss.WardenBoss boss) {
			boss.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, level.getRandom().nextFloat() * 360, 0);
			boss.setHome(net.minecraft.world.phys.Vec3.atBottomCenterOf(pos));
			boss.setDormant(dormant);
			level.addFreshEntity(boss);
		}
	}

	/** Two and a half times the health, half again the bite, a golden name and a glow. */
	public static void makeElite(net.minecraft.world.entity.Mob mob) {
		mob.addTag("afterworld:elite");
		var hp = mob.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
		if (hp != null) {
			hp.setBaseValue(hp.getBaseValue() * 2.5);
			mob.setHealth(mob.getMaxHealth());
		}
		var atk = mob.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
		if (atk != null) {
			atk.setBaseValue(atk.getBaseValue() * 1.5);
		}
		var armor = mob.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR);
		if (armor != null) {
			armor.setBaseValue(armor.getBaseValue() + 4);
		}
		mob.setCustomName(net.minecraft.network.chat.Component.translatable("creature.afterworld.elite", mob.getType().getDescription()).withColor(0xE8C060));
		mob.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.GLOWING, -1, 0, false, false));
	}

	/** All loaded NPCs of one kind (usually one). */
	public static List<Mannequin> loaded(Npc npc) {
		List<Mannequin> out = new ArrayList<>();
		for (Mannequin m : LOADED) {
			if (!m.isRemoved() && npcOf(m) == npc) {
				out.add(m);
			}
		}
		return out;
	}

	// ------------------------------------------------------------------ behaviour

	private static void tick(MinecraftServer server) {
		if (LOADED.isEmpty()) {
			return;
		}
		LOADED.removeIf(Entity::isRemoved);
		for (Mannequin m : LOADED) {
			if (!(m.level() instanceof ServerLevel level)) {
				continue;
			}
			Player partner = Dialogue.partnerOf(m);
			Player watch = partner != null ? partner : level.getNearestPlayer(m, NOTICE);
			float yaw = m.getYHeadRot();
			float pitch = m.getXRot();
			if (watch != null && !watch.isSpectator()) {
				Vec3 eye = m.getEyePosition();
				Vec3 to = watch.getEyePosition().subtract(eye);
				yaw = (float) (Mth.atan2(to.z, to.x) * Mth.RAD_TO_DEG) - 90.0F;
				pitch = (float) -(Mth.atan2(to.y, Math.sqrt(to.x * to.x + to.z * to.z)) * Mth.RAD_TO_DEG);
			} else if ((level.getGameTime() + m.getId() * 37L) % 90 == 0) {
				// Alone: glance somewhere else now and then, never far from where they stand facing.
				yaw = m.yBodyRot + (level.getRandom().nextFloat() - 0.5F) * 100.0F;
				pitch = (level.getRandom().nextFloat() - 0.6F) * 30.0F;
				m.setAttached(LOOK, new float[] {yaw, pitch});
			} else {
				float[] look = m.getAttached(LOOK);
				if (look != null) {
					yaw = look[0];
					pitch = look[1];
				}
			}
			float y = Mth.approachDegrees(m.getYHeadRot(), yaw, 9.0F);
			float p = Mth.approach(m.getXRot(), Mth.clamp(pitch, -40.0F, 40.0F), 6.0F);
			if (Math.abs(Mth.wrapDegrees(y - m.getYHeadRot())) > 0.05F || Math.abs(p - m.getXRot()) > 0.05F) {
				m.setYHeadRot(y);
				m.setYRot(y);
				m.setXRot(p);
			}
			// A speaker gestures now and then.
			if (partner != null && (level.getGameTime() + m.getId()) % 50 == 0 && level.getRandom().nextInt(3) == 0) {
				m.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
			}
		}
	}

	/** Where an NPC idly looks (not saved). */
	private static final net.fabricmc.fabric.api.attachment.v1.AttachmentType<float[]> LOOK =
		net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry.create(Afterworld.id("npc_look"));
}
