package dev.afterworld.net;

import dev.afterworld.Afterworld;
import dev.afterworld.player.Garb;
import io.netty.buffer.ByteBuf;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** All Afterworld packets. Kept compact: the HUD packet is only sent when something changed. */
public final class AwPayloads {
	private AwPayloads() {
	}

	// ------------------------------------------------------------------ client -> server

	/** Cast the Edict in {@code slot} (0-3), or the discipline signature when slot is {@link #SIGNATURE}. */
	public record Cast(int slot) implements CustomPacketPayload {
		public static final int SIGNATURE = 10;
		/** The signature key held down (the Wayfarer's Wrong Turn). */
		public static final int SIGNATURE_HELD = 11;
		public static final Type<Cast> TYPE = new Type<>(Afterworld.id("cast"));
		public static final StreamCodec<FriendlyByteBuf, Cast> CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, Cast::slot, Cast::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** Every journal interaction goes through one validated action packet. */
	public record JournalAction(int action, int a, int b, String s) implements CustomPacketPayload {
		public static final int OPEN = 0;
		public static final int EQUIP = 1;
		public static final int CHOOSE_DISCIPLINE = 2;
		public static final int TRACK_RUMOUR = 3;
		public static final int ABANDON_RUMOUR = 4;
		public static final int CLAIM_BOON = 5;
		public static final int PAY_BOND = 6;
		public static final int RELEASE_BOND = 7;
		public static final int CHOICE = 8;
		/** Spend three Chronicle Pages to restore a missing page; {@code s} is the page's cause. */
		public static final int RESTORE_PAGE = 9;

		public static final Type<JournalAction> TYPE = new Type<>(Afterworld.id("journal_action"));
		public static final StreamCodec<FriendlyByteBuf, JournalAction> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, JournalAction::action,
			ByteBufCodecs.VAR_INT, JournalAction::a,
			ByteBufCodecs.VAR_INT, JournalAction::b,
			ByteBufCodecs.stringUtf8(256), JournalAction::s,
			JournalAction::new
		);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/**
	 * The traveller's choices from the arrival screen. {@code origin} and {@code discipline} are
	 * ordinals; -1 means "unchanged" (the wardrobe only sends garb).
	 */
	public record Arrive(int origin, int discipline, int affinity, String name, Garb garb) implements CustomPacketPayload {
		public static final Type<Arrive> TYPE = new Type<>(Afterworld.id("arrive"));
		public static final StreamCodec<ByteBuf, Arrive> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, Arrive::origin,
			ByteBufCodecs.VAR_INT, Arrive::discipline,
			ByteBufCodecs.VAR_INT, Arrive::affinity,
			ByteBufCodecs.stringUtf8(64), Arrive::name,
			Garb.STREAM_CODEC, Arrive::garb,
			Arrive::new
		);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	// ------------------------------------------------------------------ server -> client

	/** A title card: a place, a region or a boss announcing itself. */
	public record Title(int style, Component title, Component subtitle, int color) implements CustomPacketPayload {
		public static final int PLACE = 0;
		public static final int BOSS = 1;
		public static final int REGION = 2;
		public static final int CHAPTER = 3;
		public static final int RENOWN = 4;
		public static final Type<Title> TYPE = new Type<>(Afterworld.id("title"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Title> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, Title::style,
			ComponentSerialization.TRUSTED_STREAM_CODEC, Title::title,
			ComponentSerialization.TRUSTED_STREAM_CODEC, Title::subtitle,
			ByteBufCodecs.INT, Title::color,
			Title::new
		);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** Shakes the camera: a slam, a roar, a toll. Strength is in degrees at the peak. */
	public record Shake(float strength, int ticks) implements CustomPacketPayload {
		public static final Type<Shake> TYPE = new Type<>(Afterworld.id("shake"));
		public static final StreamCodec<ByteBuf, Shake> CODEC = StreamCodec.composite(
			ByteBufCodecs.FLOAT, Shake::strength,
			ByteBufCodecs.VAR_INT, Shake::ticks,
			Shake::new
		);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** Opens the arrival screen (or, with {@code wardrobe}, just its garb step). */
	public record OpenArrival(boolean wardrobe, int discipline) implements CustomPacketPayload {
		public static final Type<OpenArrival> TYPE = new Type<>(Afterworld.id("open_arrival"));
		public static final StreamCodec<ByteBuf, OpenArrival> CODEC = StreamCodec.composite(
			ByteBufCodecs.BOOL, OpenArrival::wardrobe,
			ByteBufCodecs.VAR_INT, OpenArrival::discipline,
			OpenArrival::new
		);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** Everything the Edict HUD needs. */
	public record Hud(int discipline, int slotCount, int[] slots, int[] cdRemain, int[] cdTotal, int sigRemain, int sigTotal, int flags, int learnedMask)
		implements CustomPacketPayload {
		public static final int FLAG_ECHO = 1;
		public static final int FLAG_PACT = 1 << 1;
		public static final int FLAG_OATHBREAKER = 1 << 2;
		public static final int FLAG_FORGOTTEN = 1 << 3;
		public static final int FLAG_HUNGER = 1 << 4;
		public static final int FLAG_WRONG_TURN = 1 << 5;
		public static final int FLAG_FAVOUR = 1 << 6;
		/** Bits 8..11: suppressed slots. */
		public static final int SUPPRESS_SHIFT = 8;

		public static final Type<Hud> TYPE = new Type<>(Afterworld.id("hud"));
		public static final StreamCodec<FriendlyByteBuf, Hud> CODEC = StreamCodec.of(Hud::write, Hud::read);

		private static void write(FriendlyByteBuf buf, Hud h) {
			buf.writeVarInt(h.discipline);
			buf.writeVarInt(h.slotCount);
			for (int i = 0; i < 4; i++) {
				buf.writeVarInt(h.slots[i] + 1);
				buf.writeVarInt(h.cdRemain[i]);
				buf.writeVarInt(h.cdTotal[i]);
			}
			buf.writeVarInt(h.sigRemain);
			buf.writeVarInt(h.sigTotal);
			buf.writeVarInt(h.flags);
			buf.writeVarInt(h.learnedMask);
		}

		private static Hud read(FriendlyByteBuf buf) {
			int discipline = buf.readVarInt();
			int slotCount = buf.readVarInt();
			int[] slots = new int[4];
			int[] rem = new int[4];
			int[] tot = new int[4];
			for (int i = 0; i < 4; i++) {
				slots[i] = buf.readVarInt() - 1;
				rem[i] = buf.readVarInt();
				tot[i] = buf.readVarInt();
			}
			return new Hud(discipline, slotCount, slots, rem, tot, buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
		}

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** The journal's data for one tab, sent on request. */
	public record Journal(int tab, CompoundTag data) implements CustomPacketPayload {
		public static final Type<Journal> TYPE = new Type<>(Afterworld.id("journal"));
		public static final StreamCodec<FriendlyByteBuf, Journal> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, Journal::tab,
			ByteBufCodecs.TRUSTED_COMPOUND_TAG, Journal::data,
			Journal::new
		);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** A quiet notice: codex update, chronicle entry, rumour heard. Rendered as a small HUD toast. */
	public record Notice(int kind, Component text) implements CustomPacketPayload {
		public static final int CODEX = 0;
		public static final int CHRONICLE = 1;
		public static final int RUMOUR = 2;
		public static final int BOND = 3;
		public static final int WORLD = 4;
		public static final int EDICT = 5;
		public static final int ADAPT = 6;
		public static final int QUEST = 7;

		public static final Type<Notice> TYPE = new Type<>(Afterworld.id("notice"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Notice> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, Notice::kind,
			ComponentSerialization.TRUSTED_STREAM_CODEC, Notice::text,
			Notice::new
		);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	// ------------------------------------------------------------------ the Path, conversations and waystones

	/** The traveller picked an answer ({@code option}) in a conversation, or walked away (-1). */
	public record DialogueChoose(int entity, int option) implements CustomPacketPayload {
		public static final Type<DialogueChoose> TYPE = new Type<>(Afterworld.id("dialogue_choose"));
		public static final StreamCodec<ByteBuf, DialogueChoose> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, DialogueChoose::entity,
			ByteBufCodecs.VAR_INT, DialogueChoose::option,
			DialogueChoose::new
		);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** One page of a conversation: what the NPC says (with voice lines) and the answers on offer. {@code close} ends it. */
	public record DialogueShow(int entity, String npc, String name, String role, int color, java.util.List<Line> lines, java.util.List<String> options, boolean close)
		implements CustomPacketPayload {
		/** One spoken line: its subtitle, sound, length, delivery cue and when each tenth of it is said (ms). */
		public record Line(String text, String sound, int ticks, String cue, java.util.List<Integer> beats) {
			public static final StreamCodec<ByteBuf, Line> CODEC = StreamCodec.composite(
				ByteBufCodecs.stringUtf8(1024), Line::text,
				ByteBufCodecs.stringUtf8(256), Line::sound,
				ByteBufCodecs.VAR_INT, Line::ticks,
				ByteBufCodecs.stringUtf8(64), Line::cue,
				ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(16)), Line::beats,
				Line::new
			);

			/** How much of the text should be showing {@code ms} into the line (0..1). */
			public float revealed(long ms, long fallbackTotal) {
				if (this.beats.size() >= 2) {
					int last = this.beats.size() - 1;
					if (ms <= this.beats.get(0)) {
						return 0;
					}
					for (int i = 1; i <= last; i++) {
						int a = this.beats.get(i - 1);
						int b = this.beats.get(i);
						if (ms < b) {
							return (i - 1 + (ms - a) / (float) Math.max(1, b - a)) / last;
						}
					}
					return 1;
				}
				return Math.min(1.0F, ms / (float) Math.max(1, fallbackTotal));
			}
		}

		public static final Type<DialogueShow> TYPE = new Type<>(Afterworld.id("dialogue_show"));
		public static final StreamCodec<ByteBuf, DialogueShow> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, DialogueShow::entity,
			ByteBufCodecs.stringUtf8(64), DialogueShow::npc,
			ByteBufCodecs.stringUtf8(128), DialogueShow::name,
			ByteBufCodecs.stringUtf8(128), DialogueShow::role,
			ByteBufCodecs.INT, DialogueShow::color,
			Line.CODEC.apply(ByteBufCodecs.list(32)), DialogueShow::lines,
			ByteBufCodecs.stringUtf8(256).apply(ByteBufCodecs.list(8)), DialogueShow::options,
			ByteBufCodecs.BOOL, DialogueShow::close,
			DialogueShow::new
		);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** The quest tracker: the chapter, its objectives (with where they point), a keeper's mark and the tracked lead. */
	public record QuestSync(Component head, Component title, java.util.List<Entry> entries) implements CustomPacketPayload {
		public static final int MAIN = 0;
		public static final int MARK = 1;
		public static final int LEAD = 2;
		public static final int SIDE = 3;
		public static final int CONTRACT = 4;

		/** {@code y} is Integer.MIN_VALUE when the target is "on the ground there" and the client should find the surface. */
		public record Entry(int kind, Component text, int have, int need, boolean done, boolean hasTarget, int x, int y, int z, String dim) {
		}

		public static final Type<QuestSync> TYPE = new Type<>(Afterworld.id("quest"));
		public static final StreamCodec<RegistryFriendlyByteBuf, QuestSync> CODEC = StreamCodec.of(QuestSync::write, QuestSync::read);

		private static void write(RegistryFriendlyByteBuf buf, QuestSync q) {
			ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, q.head);
			ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, q.title);
			buf.writeVarInt(q.entries.size());
			for (Entry e : q.entries) {
				buf.writeVarInt(e.kind);
				ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, e.text);
				buf.writeVarInt(e.have);
				buf.writeVarInt(e.need);
				buf.writeBoolean(e.done);
				buf.writeBoolean(e.hasTarget);
				buf.writeInt(e.x);
				buf.writeInt(e.y);
				buf.writeInt(e.z);
				buf.writeUtf(e.dim, 128);
			}
		}

		private static QuestSync read(RegistryFriendlyByteBuf buf) {
			Component head = ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf);
			Component title = ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf);
			int n = Math.min(16, buf.readVarInt());
			java.util.List<Entry> entries = new java.util.ArrayList<>(n);
			for (int i = 0; i < n; i++) {
				entries.add(new Entry(buf.readVarInt(), ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf), buf.readVarInt(), buf.readVarInt(),
					buf.readBoolean(), buf.readBoolean(), buf.readInt(), buf.readInt(), buf.readInt(), buf.readUtf(128)));
			}
			return new QuestSync(head, title, entries);
		}

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** The waystones a traveller knows, offered for travel. {@code from} is the stone they stand at (-1: none). */
	public record WaystoneList(int from, int waitTicks, int fare, java.util.List<Entry> entries) implements CustomPacketPayload {
		public record Entry(int id, String name, int x, int y, int z, String dim, boolean camp) {
			public static final StreamCodec<ByteBuf, Entry> CODEC = new StreamCodec<>() {
				@Override
				public Entry decode(ByteBuf buf) {
					return new Entry(ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.stringUtf8(128).decode(buf), buf.readInt(), buf.readInt(), buf.readInt(),
						ByteBufCodecs.stringUtf8(128).decode(buf), buf.readBoolean());
				}

				@Override
				public void encode(ByteBuf buf, Entry e) {
					ByteBufCodecs.VAR_INT.encode(buf, e.id);
					ByteBufCodecs.stringUtf8(128).encode(buf, e.name);
					buf.writeInt(e.x);
					buf.writeInt(e.y);
					buf.writeInt(e.z);
					ByteBufCodecs.stringUtf8(128).encode(buf, e.dim);
					buf.writeBoolean(e.camp);
				}
			};
		}

		public static final Type<WaystoneList> TYPE = new Type<>(Afterworld.id("waystones"));
		public static final StreamCodec<ByteBuf, WaystoneList> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, WaystoneList::from,
			ByteBufCodecs.VAR_INT, WaystoneList::waitTicks,
			ByteBufCodecs.VAR_INT, WaystoneList::fare,
			Entry.CODEC.apply(ByteBufCodecs.list(256)), WaystoneList::entries,
			WaystoneList::new
		);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** Travel to the waystone with this id. */
	public record WaystoneTravel(int id) implements CustomPacketPayload {
		public static final Type<WaystoneTravel> TYPE = new Type<>(Afterworld.id("waystone_travel"));
		public static final StreamCodec<ByteBuf, WaystoneTravel> CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, WaystoneTravel::id, WaystoneTravel::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** Fades the screen to a colour and back: in, hold and out, in ticks. */
	public record Fade(int in, int hold, int out, int color) implements CustomPacketPayload {
		public static final Type<Fade> TYPE = new Type<>(Afterworld.id("fade"));
		public static final StreamCodec<ByteBuf, Fade> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, Fade::in,
			ByteBufCodecs.VAR_INT, Fade::hold,
			ByteBufCodecs.VAR_INT, Fade::out,
			ByteBufCodecs.INT, Fade::color,
			Fade::new
		);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	// ------------------------------------------------------------------ the economy

	/** The wallet: carried and banked Marks, and the change that caused this update (0: none). */
	public record WalletSync(long marks, long banked, int delta) implements CustomPacketPayload {
		public static final Type<WalletSync> TYPE = new Type<>(Afterworld.id("wallet"));
		public static final StreamCodec<ByteBuf, WalletSync> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_LONG, WalletSync::marks,
			ByteBufCodecs.VAR_LONG, WalletSync::banked,
			ByteBufCodecs.INT, WalletSync::delta,
			WalletSync::new
		);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** A merchant's counter, as it stands right now. {@code haggle} is today's bargain in percent (999: none yet). */
	public record ShopView(int entity, String merchant, String name, String role, int color, long wallet, int repTier, int haggle,
		java.util.List<Buy> buy, java.util.List<Sell> sell, java.util.List<Sell> buyback, Component message) implements CustomPacketPayload {
		/** One offer: the bundle, its price, stock left (-1: plenty), category, demand arrow, and the Renown needed (0: none). */
		public record Buy(net.minecraft.world.item.ItemStack stack, int price, int stock, int category, int demand, int locked) {
		}

		/** Something the traveller can sell: the inventory slot (or buyback index), the stack, unit price and demand. */
		public record Sell(int slot, net.minecraft.world.item.ItemStack stack, int price, int demand) {
		}

		public static final Type<ShopView> TYPE = new Type<>(Afterworld.id("shop"));
		public static final StreamCodec<RegistryFriendlyByteBuf, ShopView> CODEC = StreamCodec.of(ShopView::write, ShopView::read);

		private static void write(RegistryFriendlyByteBuf buf, ShopView v) {
			buf.writeVarInt(v.entity);
			buf.writeUtf(v.merchant);
			buf.writeUtf(v.name);
			buf.writeUtf(v.role);
			buf.writeInt(v.color);
			buf.writeVarLong(v.wallet);
			buf.writeVarInt(v.repTier + 2);
			buf.writeVarInt(v.haggle + 1000);
			buf.writeVarInt(v.buy.size());
			for (Buy b : v.buy) {
				net.minecraft.world.item.ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, b.stack);
				buf.writeVarInt(b.price);
				buf.writeVarInt(b.stock + 1);
				buf.writeVarInt(b.category);
				buf.writeVarInt(b.demand + 1);
				buf.writeVarInt(b.locked);
			}
			for (java.util.List<Sell> list : java.util.List.of(v.sell, v.buyback)) {
				buf.writeVarInt(list.size());
				for (Sell s : list) {
					buf.writeVarInt(s.slot);
					net.minecraft.world.item.ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, s.stack);
					buf.writeVarInt(s.price);
					buf.writeVarInt(s.demand + 1);
				}
			}
			ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, v.message);
		}

		private static ShopView read(RegistryFriendlyByteBuf buf) {
			int entity = buf.readVarInt();
			String merchant = buf.readUtf();
			String name = buf.readUtf();
			String role = buf.readUtf();
			int color = buf.readInt();
			long wallet = buf.readVarLong();
			int rep = buf.readVarInt() - 2;
			int haggle = buf.readVarInt() - 1000;
			int n = Math.min(128, buf.readVarInt());
			java.util.List<Buy> buy = new java.util.ArrayList<>();
			for (int i = 0; i < n; i++) {
				buy.add(new Buy(net.minecraft.world.item.ItemStack.OPTIONAL_STREAM_CODEC.decode(buf), buf.readVarInt(), buf.readVarInt() - 1, buf.readVarInt(),
					buf.readVarInt() - 1, buf.readVarInt()));
			}
			java.util.List<java.util.List<Sell>> lists = new java.util.ArrayList<>();
			for (int l = 0; l < 2; l++) {
				int m = Math.min(64, buf.readVarInt());
				java.util.List<Sell> list = new java.util.ArrayList<>();
				for (int i = 0; i < m; i++) {
					list.add(new Sell(buf.readVarInt(), net.minecraft.world.item.ItemStack.OPTIONAL_STREAM_CODEC.decode(buf), buf.readVarInt(), buf.readVarInt() - 1));
				}
				lists.add(list);
			}
			Component message = ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf);
			return new ShopView(entity, merchant, name, role, color, wallet, rep, haggle, buy, lists.get(0), lists.get(1), message);
		}

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** Something done at a shop counter (see {@link dev.afterworld.economy.Shop}). */
	public record ShopAction(int action, int index, int count, int extra) implements CustomPacketPayload {
		public static final Type<ShopAction> TYPE = new Type<>(Afterworld.id("shop_action"));
		public static final StreamCodec<ByteBuf, ShopAction> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, ShopAction::action,
			ByteBufCodecs.VAR_INT, ShopAction::index,
			ByteBufCodecs.VAR_INT, ShopAction::count,
			ByteBufCodecs.VAR_INT, ShopAction::extra,
			ShopAction::new
		);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** The Guild Ledger: carried, banked, and what today's interest will be. */
	public record BankView(long marks, long banked, long interest) implements CustomPacketPayload {
		public static final Type<BankView> TYPE = new Type<>(Afterworld.id("bank"));
		public static final StreamCodec<ByteBuf, BankView> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_LONG, BankView::marks,
			ByteBufCodecs.VAR_LONG, BankView::banked,
			ByteBufCodecs.VAR_LONG, BankView::interest,
			BankView::new
		);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** Deposit (0) or withdraw (1) an amount (-1: everything). */
	public record BankAction(int action, long amount) implements CustomPacketPayload {
		public static final Type<BankAction> TYPE = new Type<>(Afterworld.id("bank_action"));
		public static final StreamCodec<ByteBuf, BankAction> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, BankAction::action,
			ByteBufCodecs.VAR_LONG, BankAction::amount,
			BankAction::new
		);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** A voice in the world (a great power, the narrator): its words and how to show them. */
	public record VoiceLine(int entity, String name, int color, int style, DialogueShow.Line line) implements CustomPacketPayload {
		public static final int BOSS = 0;
		public static final int NARRATOR = 1;
		public static final Type<VoiceLine> TYPE = new Type<>(Afterworld.id("voice_line"));
		public static final StreamCodec<ByteBuf, VoiceLine> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, VoiceLine::entity,
			ByteBufCodecs.stringUtf8(128), VoiceLine::name,
			ByteBufCodecs.INT, VoiceLine::color,
			ByteBufCodecs.VAR_INT, VoiceLine::style,
			DialogueShow.Line.CODEC, VoiceLine::line,
			VoiceLine::new
		);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	// ------------------------------------------------------------------ movement and powers

	/**
	 * A move the client has begun: it moves itself at once, and the server pays the stamina and
	 * grants what the move protects (dodge frames, the parry window, no fall damage). {@code x} and
	 * {@code z} carry a direction, or 1/0 to start/stop the held moves.
	 */
	public record MoveAction(int action, float x, float z) implements CustomPacketPayload {
		public static final int DODGE = 0;
		public static final int PARRY = 1;
		public static final int SLIDE = 2;
		public static final int MANTLE = 3;
		public static final int WALL_JUMP = 4;
		public static final int DOUBLE_JUMP = 5;
		public static final int GLIDE = 6;
		public static final int CLIMB = 7;
		public static final int WATER_WALK = 8;
		/** Unleash the equipped Aspect. */
		public static final int ULTIMATE = 9;
		/** Cycle the equipped Aspect. */
		public static final int NEXT_ASPECT = 10;
		/** A leap up the wall while climbing. */
		public static final int CLIMB_LEAP = 11;
		public static final Type<MoveAction> TYPE = new Type<>(Afterworld.id("move"));
		public static final StreamCodec<ByteBuf, MoveAction> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, MoveAction::action,
			ByteBufCodecs.FLOAT, MoveAction::x,
			ByteBufCodecs.FLOAT, MoveAction::z,
			MoveAction::new
		);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** Stamina, the wheel's size, the Gifts known (bits), and whether a slide is under way. */
	public record MoveSync(float stamina, float max, int gifts, boolean sliding) implements CustomPacketPayload {
		public static final Type<MoveSync> TYPE = new Type<>(Afterworld.id("move_sync"));
		public static final StreamCodec<ByteBuf, MoveSync> CODEC = StreamCodec.composite(
			ByteBufCodecs.FLOAT, MoveSync::stamina,
			ByteBufCodecs.FLOAT, MoveSync::max,
			ByteBufCodecs.VAR_INT, MoveSync::gifts,
			ByteBufCodecs.BOOL, MoveSync::sliding,
			MoveSync::new
		);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** The equipped Aspect (ordinal; -1: none), the Resonance toward it, and every Aspect bound (bits). */
	public record AspectSync(int aspect, float resonance, int owned) implements CustomPacketPayload {
		public static final Type<AspectSync> TYPE = new Type<>(Afterworld.id("aspect"));
		public static final StreamCodec<ByteBuf, AspectSync> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, AspectSync::aspect,
			ByteBufCodecs.FLOAT, AspectSync::resonance,
			ByteBufCodecs.VAR_INT, AspectSync::owned,
			AspectSync::new
		);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** A beat for the camera: an ultimate unleashed, a boss changing phase, a perfect parry. */
	public record Cinematic(int kind, int arg, int entity, int color) implements CustomPacketPayload {
		public static final int ULTIMATE = 0;
		public static final int BOSS_PHASE = 1;
		public static final int PERFECT = 2;
		public static final Type<Cinematic> TYPE = new Type<>(Afterworld.id("cinematic"));
		public static final StreamCodec<ByteBuf, Cinematic> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, Cinematic::kind,
			ByteBufCodecs.VAR_INT, Cinematic::arg,
			ByteBufCodecs.VAR_INT, Cinematic::entity,
			ByteBufCodecs.INT, Cinematic::color,
			Cinematic::new
		);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** A number rising from a struck creature, for the one who struck it. */
	/** A player's move, for everyone who can see them: climbing (with the wall), a slide, a dodge, a mantle, a glide, or nothing. */
	public record PoseSync(int entity, int kind, int wall) implements CustomPacketPayload {
		public static final Type<PoseSync> TYPE = new Type<>(Afterworld.id("pose"));
		public static final StreamCodec<ByteBuf, PoseSync> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, PoseSync::entity,
			ByteBufCodecs.VAR_INT, PoseSync::kind,
			ByteBufCodecs.VAR_INT, PoseSync::wall,
			PoseSync::new
		);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** A shape of light for a moment (see {@link dev.afterworld.util.Vfx} for what each kind takes). */
	public record Vfx(int kind, net.minecraft.world.phys.Vec3 a, net.minecraft.world.phys.Vec3 b, float size, float width, float extra, int color, int life, int entity)
		implements CustomPacketPayload {
		public static final Type<Vfx> TYPE = new Type<>(Afterworld.id("vfx"));
		public static final StreamCodec<ByteBuf, Vfx> CODEC = StreamCodec.of((buf, v) -> {
			ByteBufCodecs.VAR_INT.encode(buf, v.kind);
			net.minecraft.world.phys.Vec3.STREAM_CODEC.encode(buf, v.a);
			net.minecraft.world.phys.Vec3.STREAM_CODEC.encode(buf, v.b);
			ByteBufCodecs.FLOAT.encode(buf, v.size);
			ByteBufCodecs.FLOAT.encode(buf, v.width);
			ByteBufCodecs.FLOAT.encode(buf, v.extra);
			ByteBufCodecs.INT.encode(buf, v.color);
			ByteBufCodecs.VAR_INT.encode(buf, v.life);
			ByteBufCodecs.VAR_INT.encode(buf, v.entity + 1);
		}, buf -> new Vfx(ByteBufCodecs.VAR_INT.decode(buf), net.minecraft.world.phys.Vec3.STREAM_CODEC.decode(buf), net.minecraft.world.phys.Vec3.STREAM_CODEC.decode(buf),
			ByteBufCodecs.FLOAT.decode(buf), ByteBufCodecs.FLOAT.decode(buf), ByteBufCodecs.FLOAT.decode(buf), ByteBufCodecs.INT.decode(buf),
			ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf) - 1));

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** A player acting out a power (see {@link dev.afterworld.util.Acts}). */
	public record Act(int entity, int act, int ticks) implements CustomPacketPayload {
		public static final Type<Act> TYPE = new Type<>(Afterworld.id("act"));
		public static final StreamCodec<ByteBuf, Act> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, Act::entity,
			ByteBufCodecs.VAR_INT, Act::act,
			ByteBufCodecs.VAR_INT, Act::ticks,
			Act::new
		);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	public record DamageNumber(int entity, float amount, int kind) implements CustomPacketPayload {
		public static final int NORMAL = 0;
		public static final int CRIT = 1;
		public static final int MAGIC = 2;
		public static final int HEAL = 3;
		public static final int PARRY = 4;
		public static final int DODGE = 5;
		public static final Type<DamageNumber> TYPE = new Type<>(Afterworld.id("damage_number"));
		public static final StreamCodec<ByteBuf, DamageNumber> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, DamageNumber::entity,
			ByteBufCodecs.FLOAT, DamageNumber::amount,
			ByteBufCodecs.VAR_INT, DamageNumber::kind,
			DamageNumber::new
		);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** Everything the Character screen shows, as a tag (see {@link dev.afterworld.player.CharacterServer#send}). */
	public record CharacterView(CompoundTag data) implements CustomPacketPayload {
		public static final Type<CharacterView> TYPE = new Type<>(Afterworld.id("character"));
		public static final StreamCodec<FriendlyByteBuf, CharacterView> CODEC = StreamCodec.composite(
			ByteBufCodecs.TRUSTED_COMPOUND_TAG, CharacterView::data, CharacterView::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** A trial begins (the anvil, a race...): the client opens its screen or its field panel. */
	public record TrialOpen(String id, CompoundTag data) implements CustomPacketPayload {
		public static final Type<TrialOpen> TYPE = new Type<>(Afterworld.id("trial_open"));
		public static final StreamCodec<FriendlyByteBuf, TrialOpen> CODEC = StreamCodec.composite(
			ByteBufCodecs.stringUtf8(32), TrialOpen::id, ByteBufCodecs.TRUSTED_COMPOUND_TAG, TrialOpen::data, TrialOpen::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** A field trial's progress: counters, time left, where the next goal is, what phase it's in. */
	public record TrialState(String id, CompoundTag data) implements CustomPacketPayload {
		public static final Type<TrialState> TYPE = new Type<>(Afterworld.id("trial_state"));
		public static final StreamCodec<FriendlyByteBuf, TrialState> CODEC = StreamCodec.composite(
			ByteBufCodecs.stringUtf8(32), TrialState::id, ByteBufCodecs.TRUSTED_COMPOUND_TAG, TrialState::data, TrialState::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** A trial is over. */
	public record TrialEnd(String id, boolean success, String why) implements CustomPacketPayload {
		public static final Type<TrialEnd> TYPE = new Type<>(Afterworld.id("trial_end"));
		public static final StreamCodec<ByteBuf, TrialEnd> CODEC = StreamCodec.composite(
			ByteBufCodecs.stringUtf8(32), TrialEnd::id, ByteBufCodecs.BOOL, TrialEnd::success, ByteBufCodecs.stringUtf8(64), TrialEnd::why, TrialEnd::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** The traveller's side of a trial: a finished score ({@link #RESULT}) or giving up ({@link #QUIT}). */
	public record TrialReply(String id, int code, int value) implements CustomPacketPayload {
		public static final int RESULT = 0;
		public static final int QUIT = 1;
		public static final Type<TrialReply> TYPE = new Type<>(Afterworld.id("trial_reply"));
		public static final StreamCodec<ByteBuf, TrialReply> CODEC = StreamCodec.composite(
			ByteBufCodecs.stringUtf8(32), TrialReply::id, ByteBufCodecs.VAR_INT, TrialReply::code, ByteBufCodecs.VAR_INT, TrialReply::value, TrialReply::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** A Warden's Board: its offers today and the player's contracts. */
	public record BoardOpen(CompoundTag data) implements CustomPacketPayload {
		public static final Type<BoardOpen> TYPE = new Type<>(Afterworld.id("board_open"));
		public static final StreamCodec<FriendlyByteBuf, BoardOpen> CODEC = StreamCodec.composite(
			ByteBufCodecs.TRUSTED_COMPOUND_TAG, BoardOpen::data, BoardOpen::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** A choice made at a Warden's Board: take offer {@code index}, or hand in / abandon contract {@code index}. */
	public record BoardAction(int action, int index, BlockPos board) implements CustomPacketPayload {
		public static final int TAKE = 0;
		public static final int HAND_IN = 1;
		public static final int ABANDON = 2;
		public static final Type<BoardAction> TYPE = new Type<>(Afterworld.id("board_action"));
		public static final StreamCodec<ByteBuf, BoardAction> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, BoardAction::action, ByteBufCodecs.VAR_INT, BoardAction::index, BlockPos.STREAM_CODEC, BoardAction::board, BoardAction::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** A choice made on the Character screen. */
	public record CharacterAction(int action, int a, String s) implements CustomPacketPayload {
		public static final int OPEN = 0;
		public static final int EQUIP_ASPECT = 1;
		public static final int UNEQUIP_RELIC = 2;
		public static final int BUY_SKILL = 3;
		public static final int RANK_EDICT = 4;
		public static final Type<CharacterAction> TYPE = new Type<>(Afterworld.id("character_action"));
		public static final StreamCodec<ByteBuf, CharacterAction> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, CharacterAction::action,
			ByteBufCodecs.VAR_INT, CharacterAction::a,
			ByteBufCodecs.stringUtf8(64), CharacterAction::s,
			CharacterAction::new
		);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** The Warden's Ladder's gate: how far the traveller has climbed, where they may take it up again. */
	public record LadderOpen(CompoundTag data) implements CustomPacketPayload {
		public static final Type<LadderOpen> TYPE = new Type<>(Afterworld.id("ladder_open"));
		public static final StreamCodec<FriendlyByteBuf, LadderOpen> CODEC = StreamCodec.composite(
			ByteBufCodecs.TRUSTED_COMPOUND_TAG, LadderOpen::data, LadderOpen::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** A choice at the Ladder's gate: begin at the first floor, or take the climb up again. */
	public record LadderAction(int action, BlockPos gate) implements CustomPacketPayload {
		public static final int BEGIN = 0;
		public static final int RESUME = 1;
		public static final Type<LadderAction> TYPE = new Type<>(Afterworld.id("ladder_action"));
		public static final StreamCodec<ByteBuf, LadderAction> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, LadderAction::action, BlockPos.STREAM_CODEC, LadderAction::gate, LadderAction::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** The climb in progress, for the panel at the top of the screen (an empty tag hides it). */
	public record LadderState(CompoundTag data) implements CustomPacketPayload {
		public static final Type<LadderState> TYPE = new Type<>(Afterworld.id("ladder_state"));
		public static final StreamCodec<FriendlyByteBuf, LadderState> CODEC = StreamCodec.composite(
			ByteBufCodecs.TRUSTED_COMPOUND_TAG, LadderState::data, LadderState::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	public static void register() {
		PayloadTypeRegistry.clientboundPlay().register(TrialOpen.TYPE, TrialOpen.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(BoardOpen.TYPE, BoardOpen.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(PoseSync.TYPE, PoseSync.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(BoardAction.TYPE, BoardAction.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(TrialState.TYPE, TrialState.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(TrialEnd.TYPE, TrialEnd.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(TrialReply.TYPE, TrialReply.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Cast.TYPE, Cast.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(JournalAction.TYPE, JournalAction.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Hud.TYPE, Hud.CODEC);
		PayloadTypeRegistry.clientboundPlay().registerLarge(Journal.TYPE, Journal.CODEC, 1 << 20);
		PayloadTypeRegistry.clientboundPlay().register(Notice.TYPE, Notice.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Arrive.TYPE, Arrive.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(OpenArrival.TYPE, OpenArrival.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Title.TYPE, Title.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Shake.TYPE, Shake.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(DialogueChoose.TYPE, DialogueChoose.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(DialogueShow.TYPE, DialogueShow.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(QuestSync.TYPE, QuestSync.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(WaystoneList.TYPE, WaystoneList.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(WaystoneTravel.TYPE, WaystoneTravel.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Fade.TYPE, Fade.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(WalletSync.TYPE, WalletSync.CODEC);
		PayloadTypeRegistry.clientboundPlay().registerLarge(ShopView.TYPE, ShopView.CODEC, 1 << 20);
		PayloadTypeRegistry.serverboundPlay().register(ShopAction.TYPE, ShopAction.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(BankView.TYPE, BankView.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(BankAction.TYPE, BankAction.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(MoveAction.TYPE, MoveAction.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(VoiceLine.TYPE, VoiceLine.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(MoveSync.TYPE, MoveSync.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(AspectSync.TYPE, AspectSync.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Cinematic.TYPE, Cinematic.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(DamageNumber.TYPE, DamageNumber.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Vfx.TYPE, Vfx.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Act.TYPE, Act.CODEC);
		PayloadTypeRegistry.clientboundPlay().registerLarge(CharacterView.TYPE, CharacterView.CODEC, 1 << 18);
		PayloadTypeRegistry.serverboundPlay().register(CharacterAction.TYPE, CharacterAction.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(LadderOpen.TYPE, LadderOpen.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(LadderAction.TYPE, LadderAction.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(LadderState.TYPE, LadderState.CODEC);
	}
}
