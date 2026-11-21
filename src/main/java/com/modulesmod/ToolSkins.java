package com.modulesmod;

import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.List;

/**
 * Tool skins. The tool in your main hand gets a custom_model_data string (the skin id); the item definitions
 * in assets/minecraft/items/ (made by tools/generate_tool_skins.py) pick the skin's model for that string.
 * Everything else is the normal item rendering, so enchant glint, animations and so on keep working.
 * Only the stack in YOUR main hand is changed, and only on the client.
 */
public final class ToolSkins {
	private ToolSkins() {}

	/** Called for every item the game is about to draw; returns the same stack unless a skin applies. */
	public static ItemStack apply(ItemStack stack) {
		try {
			if (stack.isEmpty() || !Modules.COSMETICS.isEnabled()) return stack;
			String skin = Cosmetics.selectedId(Cosmetics.Category.TOOL);
			if (skin == null) return stack;
			Minecraft mc = Minecraft.getInstance();
			if (mc.player == null || stack != mc.player.getMainHandItem()) return stack;
			if (!isSkinnable(stack)) return stack;

			ItemStack copy = stack.copy();
			copy.set(DataComponents.CUSTOM_MODEL_DATA,
					new CustomModelData(List.of(), List.of(), List.of(skin), List.of()));
			return copy;
		} catch (Throwable t) {
			return stack;
		}
	}

	/** Vanilla swords, pickaxes, axes, shovels and hoes. */
	private static boolean isSkinnable(ItemStack stack) {
		var id = BuiltInRegistries.ITEM.getKey(stack.getItem());
		if (!"minecraft".equals(id.getNamespace())) return false;
		String p = id.getPath();
		return p.endsWith("_sword") || p.endsWith("_pickaxe") || p.endsWith("_axe")
				|| p.endsWith("_shovel") || p.endsWith("_hoe");
	}

	/** 16x16 ARGB picture of the skin's sword, for the menu card. */
	static int[] icon(String skinId) {
		try (InputStream in = ToolSkins.class.getResourceAsStream("/assets/modulesmod/textures/item/skin_" + skinId + "_sword.png")) {
			if (in == null) return null;
			BufferedImage img = ImageIO.read(in);
			return img.getRGB(0, 0, 16, 16, null, 0, 16);
		} catch (Exception e) {
			return null;
		}
	}
}
