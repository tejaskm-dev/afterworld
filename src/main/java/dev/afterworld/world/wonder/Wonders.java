package dev.afterworld.world.wonder;

import dev.afterworld.Afterworld;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import org.jspecify.annotations.Nullable;

/**
 * The wonders: colossal builds made offline (tools/wonders) and placed by world generation like any
 * other structure. Where each appears is data: worldgen/structure and worldgen/structure_set.
 */
public final class Wonders {
	public static final StructureType<WonderStructure> TYPE = Registry.register(BuiltInRegistries.STRUCTURE_TYPE, Afterworld.id("wonder"), () -> WonderStructure.CODEC);
	public static final StructurePieceType PIECE = Registry.register(BuiltInRegistries.STRUCTURE_PIECE, Afterworld.id("wonder"),
		(StructurePieceType.ContextlessType) WonderPiece::new);

	/** Every wonder, with the colour of its title card. */
	public static final String[] NAMES = {"titan", "angels", "cathedral", "citadel", "mill"};
	private static final int[] COLORS = {0xC9714A, 0xE8DCC0, 0xFFD27A, 0x9E86D9, 0xD9A05A};

	private Wonders() {
	}

	public static void init() {
		// Registration happens when the class loads.
	}

	public static int color(String wonder) {
		for (int i = 0; i < NAMES.length; i++) {
			if (NAMES[i].equals(wonder)) {
				return COLORS[i];
			}
		}
		return 0xE8DCC0;
	}

	/** The wonder whose bounds contain this position, if any. */
	public static @Nullable String at(ServerLevel level, BlockPos pos) {
		StructureStart start = level.structureManager().getStructureWithPieceAt(pos, h -> h.value() instanceof WonderStructure);
		if (start.isValid()) {
			for (StructurePiece piece : start.getPieces()) {
				if (piece instanceof WonderPiece w) {
					return w.wonder();
				}
			}
		}
		return null;
	}
}
