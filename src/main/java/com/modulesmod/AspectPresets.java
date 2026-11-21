package com.modulesmod;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Built-in and user-saved aspect ratio presets. User presets live in config/modulesmod-presets.json. */
public final class AspectPresets {
	public record Preset(String name, double ratio, boolean builtIn) {}

	private static final List<Preset> BUILT_IN = List.of(
			new Preset("1:1", 1.00, true),
			new Preset("5:4", 1.25, true),
			new Preset("4:3", 1.33, true),
			new Preset("3:2", 1.50, true),
			new Preset("16:10", 1.60, true),
			new Preset("16:9", 1.78, true),
			new Preset("21:9", 2.33, true));

	private static final Map<String, Double> USER = new LinkedHashMap<>();
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private AspectPresets() {}

	/** Your presets first, then the built-in ones. */
	public static List<Preset> all() {
		List<Preset> out = new ArrayList<>();
		for (Map.Entry<String, Double> e : USER.entrySet()) out.add(new Preset(e.getKey(), e.getValue(), false));
		out.addAll(BUILT_IN);
		return out;
	}

	private static boolean matches(Preset p, double ratio) {
		return Math.abs(p.ratio() - ratio) < 0.005;
	}

	/** Name of the preset matching this ratio, or "Custom". */
	public static String label(double ratio) {
		for (Preset p : all()) if (matches(p, ratio)) return p.name();
		return "Custom";
	}

	/** Ratio of the next preset in the list (wraps around). */
	public static double next(double current) {
		List<Preset> list = all();
		int idx = -1;
		for (int i = 0; i < list.size(); i++) {
			if (matches(list.get(i), current)) {
				idx = i;
				break;
			}
		}
		return list.get((idx + 1) % list.size()).ratio();
	}

	public static void saveUser(String name, double ratio) {
		double r = Math.round(ratio * 100.0) / 100.0;
		String n = name == null ? "" : name.trim();
		if (n.isEmpty()) n = "Preset " + String.format(Locale.ROOT, "%.2f", r);
		if (n.length() > 24) n = n.substring(0, 24);
		for (Preset p : BUILT_IN) {
			if (p.name().equalsIgnoreCase(n)) return; // don't shadow the built-in names
		}
		USER.put(n, r);
		persist();
	}

	/** Deletes the saved preset that matches this ratio (built-in presets can't be deleted). */
	public static void deleteMatching(double ratio) {
		String found = null;
		for (Map.Entry<String, Double> e : USER.entrySet()) {
			if (Math.abs(e.getValue() - ratio) < 0.005) {
				found = e.getKey();
				break;
			}
		}
		if (found != null) {
			USER.remove(found);
			persist();
		}
	}

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("modulesmod-presets.json");
	}

	public static void load() {
		Path f = file();
		if (!Files.exists(f)) return;
		try (Reader r = Files.newBufferedReader(f)) {
			Map<String, Double> map = GSON.fromJson(r, new TypeToken<Map<String, Double>>() {}.getType());
			USER.clear();
			if (map != null) USER.putAll(map);
		} catch (Exception e) {
			ModulesMod.LOGGER.warn("Could not load presets", e);
		}
	}

	private static void persist() {
		try (Writer w = Files.newBufferedWriter(file())) {
			GSON.toJson(USER, w);
		} catch (Exception e) {
			ModulesMod.LOGGER.warn("Could not save presets", e);
		}
	}
}
