package com.modulesmod;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Press the key and a /give command for the item in your main hand goes to the clipboard, including
 * its enchantments, custom name, lore and every other data component. Paste it into chat or a
 * command block.
 */
public final class GiveCopier {
	private static KeyMapping copyKey;

	private GiveCopier() {}

	public static void register() {
		copyKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
				"key.modulesmod.copy_give", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, ZoomController.category()));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (copyKey.consumeClick()) {
				if (!Modules.COPY_GIVE.isEnabled()) continue;
				try {
					copy(client);
				} catch (Exception e) {
					Diagnostics.error("copy give", e);
				}
			}
		});
	}

	private static void copy(Minecraft mc) {
		if (mc.player == null || mc.level == null) return;
		ItemStack stack = mc.player.getMainHandItem();
		if (stack.isEmpty()) {
			say(mc, "Hold an item first");
			return;
		}
		String command = command(mc, stack);
		mc.keyboardHandler.setClipboard(command);
		if (Modules.GC_MESSAGE.bool) {
			String shown = command.length() > 70 ? command.substring(0, 67) + "..." : command;
			say(mc, "Copied: " + shown);
		}
	}

	private static void say(Minecraft mc, String message) {
		if (mc.player != null) mc.player.displayClientMessage(Component.literal(message), true);
	}

	private static String command(Minecraft mc, ItemStack stack) {
		String target = switch (Modules.GC_TARGET.index()) {
			case 1 -> "@p";
			case 2 -> mc.player.getName().getString();
			default -> "@s";
		};
		StringBuilder sb = new StringBuilder("/give ").append(target).append(' ')
				.append(BuiltInRegistries.ITEM.getKey(stack.getItem()));
		String components = components(mc, stack);
		if (!components.isEmpty()) sb.append('[').append(components).append(']');
		if (Modules.GC_COUNT.bool) sb.append(' ').append(stack.getCount());
		return sb.toString();
	}

	/** "minecraft:enchantments={...},minecraft:custom_name='...'" for everything that differs from a plain item. */
	private static String components(Minecraft mc, ItemStack stack) {
		var ops = mc.level.registryAccess().createSerializationContext(NbtOps.INSTANCE);
		Tag encoded = ItemStack.CODEC.encodeStart(ops, stack).result().orElse(null);
		if (!(encoded instanceof CompoundTag root)) return "";
		if (!(root.get("components") instanceof CompoundTag comps)) return "";

		List<String> keys = new ArrayList<>(comps.keySet());
		Collections.sort(keys);
		StringBuilder sb = new StringBuilder();
		for (String key : keys) {
			if (sb.length() > 0) sb.append(',');
			if (key.startsWith("!")) {
				sb.append(key); // a removed component is written without a value
			} else {
				sb.append(key).append('=').append(comps.get(key));
			}
		}
		return sb.toString();
	}
}
