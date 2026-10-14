package dev.afterworld.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.afterworld.AwConfig;
import dev.afterworld.edict.Edict;
import dev.afterworld.player.Discipline;
import dev.afterworld.player.Disciplines;
import dev.afterworld.player.PlayerState;
import dev.afterworld.player.Progress;
import dev.afterworld.site.Site;
import dev.afterworld.site.SiteKind;
import dev.afterworld.site.Sites;
import dev.afterworld.world.AfterworldData;
import dev.afterworld.world.Faction;
import dev.afterworld.world.Settlement;
import dev.afterworld.world.Settlements;
import dev.afterworld.world.WorldSim;
import java.util.Arrays;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** /afterworld: a little information for everyone, and tools for server operators. */
public final class AwCommands {
	private AwCommands() {
	}

	public static void init() {
		CommandRegistrationCallback.EVENT.register((dispatcher, ctx, selection) -> register(dispatcher));
	}

	private static SuggestionProvider<CommandSourceStack> names(String[] values) {
		return (c, b) -> SharedSuggestionProvider.suggest(values, b);
	}

	private static void register(CommandDispatcher<CommandSourceStack> d) {
		String[] edicts = Arrays.stream(Edict.VALUES).map(e -> e.id).toArray(String[]::new);
		String[] disciplines = Arrays.stream(Discipline.VALUES).filter(x -> x != Discipline.NONE).map(x -> x.id).toArray(String[]::new);
		String[] factions = Arrays.stream(Faction.VALUES).map(f -> f.id).toArray(String[]::new);
		String[] kinds = Arrays.stream(SiteKind.VALUES).map(k -> k.id).toArray(String[]::new);
		d.register(Commands.literal("afterworld")
			.then(Commands.literal("info").executes(AwCommands::info))
			.then(Commands.literal("wardrobe").executes(c -> {
				dev.afterworld.player.Arrival.openWardrobe(c.getSource().getPlayerOrException());
				return 1;
			}))
			.then(Commands.literal("arrive").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)).executes(c -> {
				var p = c.getSource().getPlayerOrException();
				var s = dev.afterworld.player.PlayerState.of(p);
				s.arrived = false;
				net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(p, new dev.afterworld.net.AwPayloads.OpenArrival(false, s.discipline.ordinal()));
				return 1;
			}))
			.then(Commands.literal("choice")
				.then(Commands.argument("choice", StringArgumentType.word())
					.then(Commands.argument("site", IntegerArgumentType.integer(0)).executes(c -> {
						ServerPlayer p = c.getSource().getPlayerOrException();
						boolean ok = dev.afterworld.site.Unremembered.choose(p, StringArgumentType.getString(c, "choice"), IntegerArgumentType.getInteger(c, "site"));
						return ok ? 1 : 0;
					}))))
			.then(Commands.literal("learn").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.literal("all").executes(c -> {
					ServerPlayer p = c.getSource().getPlayerOrException();
					for (Edict e : Edict.VALUES) {
						Progress.learn(p, e);
					}
					return Edict.COUNT;
				}))
				.then(Commands.argument("edict", StringArgumentType.word()).suggests(names(edicts)).executes(c -> {
					Edict e = Edict.byId(StringArgumentType.getString(c, "edict"));
					if (e == null) {
						c.getSource().sendFailure(Component.literal("Unknown edict"));
						return 0;
					}
					Progress.learn(c.getSource().getPlayerOrException(), e);
					return 1;
				})))
			.then(Commands.literal("discipline").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.argument("name", StringArgumentType.word()).suggests(names(disciplines)).executes(c -> {
					ServerPlayer p = c.getSource().getPlayerOrException();
					for (Discipline x : Discipline.VALUES) {
						if (x.id.equals(StringArgumentType.getString(c, "name"))) {
							PlayerState s = PlayerState.of(p);
							s.discipline = Discipline.NONE;
							Disciplines.choose(p, x);
							return 1;
						}
					}
					return 0;
				})))
			.then(Commands.literal("rep").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.argument("faction", StringArgumentType.word()).suggests(names(factions))
					.then(Commands.argument("amount", IntegerArgumentType.integer(-200, 200)).executes(c -> {
						ServerPlayer p = c.getSource().getPlayerOrException();
						for (Faction f : Faction.VALUES) {
							if (f.id.equals(StringArgumentType.getString(c, "faction"))) {
								Progress.rep(p, f, IntegerArgumentType.getInteger(c, "amount"));
								c.getSource().sendSuccess(() -> Component.literal(f.id + " = " + PlayerState.of(p).rep(f)), false);
								return 1;
							}
						}
						return 0;
					}))))
			.then(Commands.literal("site").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.argument("kind", StringArgumentType.word()).suggests(names(kinds))
					.executes(c -> site(c, null))
					.then(Commands.argument("edict", StringArgumentType.word()).suggests(names(edicts))
						.executes(c -> site(c, Edict.byId(StringArgumentType.getString(c, "edict")))))))
			.then(Commands.literal("siteat").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.argument("kind", StringArgumentType.word()).suggests(names(kinds))
					.then(Commands.argument("x", IntegerArgumentType.integer())
						.then(Commands.argument("z", IntegerArgumentType.integer()).executes(c -> {
							var level = c.getSource().getLevel();
							SiteKind kind = SiteKind.byId(StringArgumentType.getString(c, "kind"));
							int x = IntegerArgumentType.getInteger(c, "x");
							int z = IntegerArgumentType.getInteger(c, "z");
							Site site = Sites.create(level.getServer(), kind, level.dimension().identifier().toString(), x, z, -1, -1,
								kind == SiteKind.ANOMALY ? WorldSim.anomalyEdict(level.getRandom()).ordinal() : -1, null);
							Sites.forceBuild(level, site);
							c.getSource().sendSuccess(() -> Component.literal("Building " + site.name + " at " + x + ", " + site.y + ", " + z), true);
							return site.id;
						})))))
			.then(Commands.literal("cast").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.argument("edict", StringArgumentType.word()).suggests(names(edicts)).executes(c -> {
					ServerPlayer p = c.getSource().getPlayerOrException();
					Edict e = Edict.byId(StringArgumentType.getString(c, "edict"));
					if (e == null) {
						return 0;
					}
					boolean ok = dev.afterworld.edict.EdictSystem.tryCast(p, PlayerState.of(p), e, 1.0F, true);
					return ok ? 1 : 0;
				})))
			.then(Commands.literal("rumour").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)).executes(c -> {
				ServerPlayer p = c.getSource().getPlayerOrException();
				dev.afterworld.rumour.Rumours.ask(p, java.util.UUID.randomUUID(), "a voice in the dark", Settlements.at(p.level(), p.blockPosition()));
				return 1;
			}))
			.then(Commands.literal("simulate").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.argument("days", IntegerArgumentType.integer(1, 30)).executes(c -> {
					var server = c.getSource().getServer();
					AfterworldData data = AfterworldData.get(server);
					long day = Progress.day(server);
					int days = IntegerArgumentType.getInteger(c, "days");
					for (int i = 0; i < days; i++) {
						WorldSim.simulateDay(server, data, day + 1000 + i, false);
					}
					c.getSource().sendSuccess(() -> Component.literal("Simulated " + days + " days. Chronicle pages: " + data.chronicle.size()), true);
					return days;
				})))
			.then(Commands.literal("selftest").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)).executes(c -> {
				var lines = SelfTest.run(c.getSource().getLevel());
				for (String line : lines) {
					c.getSource().sendSuccess(() -> Component.literal(line), false);
				}
				return lines.size();
			}))
			.then(Commands.literal("ladder").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.argument("floor", IntegerArgumentType.integer(1, dev.afterworld.dungeon.Ladder.TOP)).executes(c -> {
					dev.afterworld.dungeon.Ladder.jump(c.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(c, "floor"));
					return 1;
				})))
			.then(Commands.literal("build").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.argument("name", StringArgumentType.word())
					.then(Commands.argument("sink", IntegerArgumentType.integer(0, 64))
						.then(Commands.argument("turn", IntegerArgumentType.integer(0, 3)).executes(c -> build(c, IntegerArgumentType.getInteger(c, "turn"))))
						.executes(c -> build(c, 0)))))
			.then(Commands.literal("reload").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)).executes(c -> {
				AwConfig.load();
				c.getSource().sendSuccess(() -> Component.literal("Reloaded config/afterworld.json"), true);
				return 1;
			})));
	}

	/** Places a whole build (a wonder or a landmark) centred on the caller, for looking at it. */
	private static int build(CommandContext<CommandSourceStack> c, int turn) throws CommandSyntaxException {
		String name = StringArgumentType.getString(c, "name");
		int sink = IntegerArgumentType.getInteger(c, "sink");
		var data = dev.afterworld.world.wonder.WonderData.get(name);
		if (data == null) {
			c.getSource().sendFailure(Component.literal("No build called " + name));
			return 0;
		}
		var level = c.getSource().getLevel();
		var rot = net.minecraft.world.level.block.Rotation.values()[turn];
		var at = net.minecraft.core.BlockPos.containing(c.getSource().getPosition());
		int w = data.width(rot);
		int d = data.depth(rot);
		int x0 = ((at.getX() >> 4) << 4) - (w / 32) * 16;
		int z0 = ((at.getZ() >> 4) << 4) - (d / 32) * 16;
		var origin = new net.minecraft.core.BlockPos(x0, at.getY() - sink, z0);
		var random = net.minecraft.util.RandomSource.create(name.hashCode());
		for (int cx = x0; cx < x0 + w; cx += 16) {
			for (int cz = z0; cz < z0 + d; cz += 16) {
				var box = new net.minecraft.world.level.levelgen.structure.BoundingBox(cx, level.getMinY(), cz, cx + 15, level.getMaxY(), cz + 15);
				data.place(level, origin, rot, box, random);
			}
		}
		c.getSource().sendSuccess(() -> Component.literal("Built " + name + " at " + origin.toShortString()), true);
		return 1;
	}

	private static int info(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		ServerPlayer p = c.getSource().getPlayerOrException();
		PlayerState s = PlayerState.of(p);
		AfterworldData data = AfterworldData.get(p.level().getServer());
		long day = Progress.day(p);
		c.getSource().sendSuccess(() -> Component.literal("Day " + day + " · " + s.discipline.displayName().getString()
			+ " · " + s.learned.size() + " Edicts · " + s.activeRumourCount() + " open rumours · " + s.codex.size() + " codex entries"), false);
		Settlement here = Settlements.at(p.level(), p.blockPosition());
		if (here != null) {
			c.getSource().sendSuccess(() -> Component.literal(here.name + ": " + here.safetyWord() + ", " + here.prosperityWord() + ", "
				+ here.legitimacyWord() + ", " + here.anomalyWord() + " (" + here.leader + ")"), false);
		}
		c.getSource().sendSuccess(() -> Component.literal(data.settlements.size() + " settlements known · " + data.sites.size() + " sites · "
			+ data.chronicle.size() + " pages in the Chronicle"), false);
		return 1;
	}

	private static int site(CommandContext<CommandSourceStack> c, Edict edict) throws CommandSyntaxException {
		ServerPlayer p = c.getSource().getPlayerOrException();
		SiteKind kind = SiteKind.byId(StringArgumentType.getString(c, "kind"));
		var look = p.getLookAngle();
		int dist = kind.radius + 8;
		int x = (int) (p.getX() + look.x * dist);
		int z = (int) (p.getZ() + look.z * dist);
		Site site = Sites.create(p.level().getServer(), kind, p.level().dimension().identifier().toString(), x, z, -1, -1,
			edict != null ? edict.ordinal() : kind == SiteKind.ANOMALY ? WorldSim.anomalyEdict(p.getRandom()).ordinal() : -1, null);
		Sites.forceBuild(p.level(), site);
		c.getSource().sendSuccess(() -> Component.literal("Building " + site.name + " (#" + site.id + ") at " + x + ", " + z), true);
		return site.id;
	}
}
