package dev.afterworld.block;

import dev.afterworld.registry.AwBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** What a particular clue says, when a site gives it words of its own ({@code lore.afterworld.<key>}). */
public class ClueBlockEntity extends BlockEntity {
	private String lore = "";

	public ClueBlockEntity(BlockPos pos, BlockState state) {
		super(AwBlocks.CLUE_ENTITY, pos, state);
	}

	public String lore() {
		return this.lore;
	}

	public void setLore(String lore) {
		this.lore = lore;
		setChanged();
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		this.lore = input.getStringOr("lore", "");
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		if (!this.lore.isEmpty()) {
			output.putString("lore", this.lore);
		}
	}
}
