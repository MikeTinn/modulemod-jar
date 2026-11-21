package com.modulesmod;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.resources.Identifier;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Cosmetics catalogue + what you have equipped. Only you see them (it is all client side).
 * The models are data: assets/modulesmod/cosmetics/cosmetics.json (made by tools/generate_cosmetics.py),
 * every cosmetic is a list of blocky cuboids on a texture in assets/modulesmod/textures/cosmetics/.
 */
public final class Cosmetics {
	public enum Category {
		CAPE("Capes"), WINGS("Wings"), HAT("Hats"), JACKET("Jackets"), PET("Pets"), TOOL("Tools");

		public final String label;

		Category(String label) {
			this.label = label;
		}
	}

	public record CubeDef(int u, int v, float x, float y, float z, float w, float h, float d, float inflate, boolean tint) {}

	public record PartDef(String name, float[] pivot, float[] rot, List<CubeDef> cubes) {}

	/** image = set for cosmetics made from your own pictures; icon = optional 16x16 ARGB picture for the menu card. */
	public record Def(String id, String name, Category category, String attach, String anim, float amp, int accent,
					  String texture, int texW, int texH, List<PartDef> parts, CustomImages.Image image, int[] icon,
					  int tint) {
		public Identifier textureId() {
			if (image != null) return image.current();
			return Identifier.fromNamespaceAndPath(ModulesMod.MOD_ID, "textures/cosmetics/" + texture + ".png");
		}
	}

	/** Order they are drawn in. */
	private static final Category[] DRAW_ORDER = {Category.CAPE, Category.JACKET, Category.WINGS, Category.HAT, Category.PET};

	private static final List<Def> BUILT_IN = new ArrayList<>();
	private static final List<Def> CUSTOM = new ArrayList<>();
	private static final Map<Category, String> selected = new EnumMap<>(Category.class);
	private static final Map<String, CosmeticModel> models = new HashMap<>();
	private static boolean customLoaded = false;

	/** Colour choices for cosmetics that can be recoloured (0 = the cosmetic's own colour, last = rainbow). */
	public static final String[] COLOR_NAMES = {"Original", "Red", "Orange", "Yellow", "Lime", "Green", "Cyan",
			"Blue", "Purple", "Pink", "White", "Dark", "Rainbow"};
	private static final int[] COLOR_RGB = {0, 0xE63946, 0xFF8A1F, 0xFFD93D, 0x9BE030, 0x2FBF5A, 0x27D3D3,
			0x3A7BFF, 0x9B5CFF, 0xFF6FB5, 0xFFFFFF, 0x4A4A55, 0};
	public static final int RAINBOW = COLOR_NAMES.length - 1;
	private static final Map<Category, Integer> colors = new EnumMap<>(Category.class);

	private Cosmetics() {}

	public static int colorIndex(Category category) {
		return colors.getOrDefault(category, 0);
	}

	public static void setColor(Category category, int index) {
		colors.put(category, index);
		saveSelection();
	}

	/** ARGB multiplier for the tinted cubes of this cosmetic. */
	public static int tintColor(Def def) {
		int idx = colorIndex(def.category());
		if (idx <= 0 || idx >= COLOR_RGB.length) return def.tint();
		if (idx == RAINBOW) {
			return 0xFF000000 | java.awt.Color.HSBtoRGB((System.currentTimeMillis() % 5000L) / 5000f, 0.65f, 1f);
		}
		return 0xFF000000 | COLOR_RGB[idx];
	}

	/** The colour shown on the swatch for choice i of this cosmetic (rainbow has no single colour). */
	public static int swatchColor(Def def, int index) {
		if (index <= 0) return def.tint();
		return 0xFF000000 | COLOR_RGB[index];
	}

	public static void init() {
		loadDefs();
		loadSelection();
		CustomImages.makeFolders();
		// our own pictures are turned into textures on the first tick (needs the game's graphics to be ready)
		net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.START_CLIENT_TICK.register(client -> {
			if (!customLoaded) {
				customLoaded = true;
				reloadCustom();
			}
		});
		// every player renderer gets our layer; it only draws for you
		LivingEntityFeatureRendererRegistrationCallback.EVENT.register((entityType, entityRenderer, registrationHelper, context) -> {
			if (entityRenderer instanceof AvatarRenderer playerRenderer) {
				registrationHelper.register(new CosmeticsFeature(playerRenderer));
			}
		});
	}

	// ------------------------------------------------------------------ catalogue

	public static List<Def> all() {
		List<Def> out = new ArrayList<>(BUILT_IN);
		out.addAll(CUSTOM);
		return out;
	}

	public static List<Def> in(Category category) {
		List<Def> out = new ArrayList<>();
		for (Def d : BUILT_IN) if (d.category() == category) out.add(d);
		for (Def d : CUSTOM) if (d.category() == category) out.add(d);
		return out;
	}

	/** Re-reads the pictures in the cosmetics folder (capes). */
	public static void reloadCustom() {
		for (Def d : CUSTOM) {
			models.remove(d.id() + "#t");
			models.remove(d.id() + "#f");
		}
		CUSTOM.clear();
		try {
			CUSTOM.addAll(CustomImages.load());
		} catch (Throwable t) {
			Diagnostics.error("custom images", t);
		}
	}

	private static void loadDefs() {
		BUILT_IN.clear();
		try (InputStream in = Cosmetics.class.getResourceAsStream("/assets/modulesmod/cosmetics/cosmetics.json")) {
			if (in == null) return;
			JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
			for (JsonElement e : root.getAsJsonArray("cosmetics")) {
				JsonObject o = e.getAsJsonObject();
				List<PartDef> parts = new ArrayList<>();
				for (JsonElement pe : o.getAsJsonArray("parts")) {
					JsonObject po = pe.getAsJsonObject();
					List<CubeDef> cubes = new ArrayList<>();
					for (JsonElement ce : po.getAsJsonArray("cubes")) {
						JsonObject co = ce.getAsJsonObject();
						float[] uv = floats(co.getAsJsonArray("uv"));
						float[] pos = floats(co.getAsJsonArray("pos"));
						float[] size = floats(co.getAsJsonArray("size"));
						cubes.add(new CubeDef((int) uv[0], (int) uv[1], pos[0], pos[1], pos[2],
								size[0], size[1], size[2], co.get("inflate").getAsFloat(), co.get("tint").getAsBoolean()));
					}
					parts.add(new PartDef(po.get("name").getAsString(), floats(po.getAsJsonArray("pivot")),
							floats(po.getAsJsonArray("rot")), cubes));
				}
				BUILT_IN.add(new Def(o.get("id").getAsString(), o.get("name").getAsString(),
						Category.valueOf(o.get("category").getAsString()), o.get("attach").getAsString(),
						o.get("anim").getAsString(), o.get("amp").getAsFloat(),
						0xFF000000 | Integer.parseInt(o.get("accent").getAsString().substring(1), 16),
						o.get("texture").getAsString(), o.get("texW").getAsInt(), o.get("texH").getAsInt(), parts, null, null,
						o.get("tintRef").getAsString().isEmpty() ? 0
								: 0xFF000000 | Integer.parseInt(o.get("tintRef").getAsString().substring(1), 16)));
			}
		} catch (Exception e) {
			Diagnostics.error("cosmetics catalogue", e);
		}
		loadToolSkins();
	}

	private static void loadToolSkins() {
		try (InputStream in = Cosmetics.class.getResourceAsStream("/assets/modulesmod/cosmetics/tool_skins.json")) {
			if (in == null) return;
			JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
			for (JsonElement e : root.getAsJsonArray("skins")) {
				JsonObject o = e.getAsJsonObject();
				String id = o.get("id").getAsString();
				BUILT_IN.add(new Def(id, o.get("name").getAsString(), Category.TOOL, "NONE", "none", 0f,
						0xFF000000 | Integer.parseInt(o.get("accent").getAsString().substring(1), 16),
						"", 0, 0, List.of(), null, ToolSkins.icon(id), 0));
			}
		} catch (Exception e) {
			Diagnostics.error("tool skins", e);
		}
	}

	private static float[] floats(JsonArray a) {
		float[] out = new float[a.size()];
		for (int i = 0; i < out.length; i++) out[i] = a.get(i).getAsFloat();
		return out;
	}

	// ------------------------------------------------------------------ what is equipped

	public static String selectedId(Category category) {
		return selected.get(category);
	}

	public static void select(Category category, String idOrNull) {
		if (idOrNull == null) selected.remove(category);
		else selected.put(category, idOrNull);
		saveSelection();
	}

	/** Equipped cosmetics, in the order they are drawn. */
	public static List<Def> equipped() {
		List<Def> out = new ArrayList<>();
		for (Category c : DRAW_ORDER) {
			String id = selected.get(c);
			if (id == null) continue;
			for (Def d : all()) if (d.id().equals(id)) out.add(d);
		}
		return out;
	}

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("modulesmod-cosmetics.json");
	}

	private static void loadSelection() {
		selected.clear();
		colors.clear();
		try {
			Path f = file();
			if (!Files.exists(f)) return;
			JsonObject o = JsonParser.parseString(Files.readString(f)).getAsJsonObject();
			for (Category c : Category.values()) {
				JsonElement v = o.get(c.name());
				if (v != null && !v.isJsonNull()) selected.put(c, v.getAsString());
				JsonElement col = o.get("COLOR_" + c.name());
				if (col != null && !col.isJsonNull()) colors.put(c, col.getAsInt());
			}
		} catch (Exception e) {
			Diagnostics.error("cosmetics load", e);
		}
	}

	private static void saveSelection() {
		try {
			JsonObject o = new JsonObject();
			for (Map.Entry<Category, String> e : selected.entrySet()) o.addProperty(e.getKey().name(), e.getValue());
			for (Map.Entry<Category, Integer> e : colors.entrySet()) o.addProperty("COLOR_" + e.getKey().name(), e.getValue());
			Files.writeString(file(), new Gson().toJson(o));
		} catch (Exception e) {
			Diagnostics.error("cosmetics save", e);
		}
	}

	// ------------------------------------------------------------------ models

	/** tinted = only the cubes that take the colour you pick; otherwise only the cubes that keep their own colours. */
	public static CosmeticModel model(Def def, boolean tinted) {
		String key = def.id() + (tinted ? "#t" : "#f");
		if (models.containsKey(key)) return models.get(key);
		CosmeticModel m = build(def, tinted);
		models.put(key, m);
		return m;
	}

	static CosmeticModel build(Def def, boolean tinted) {
		List<PartDef> parts = new ArrayList<>();
		for (PartDef p : def.parts()) {
			List<CubeDef> cubes = new ArrayList<>();
			for (CubeDef c : p.cubes()) if (c.tint() == tinted) cubes.add(c);
			if (!cubes.isEmpty()) parts.add(new PartDef(p.name(), p.pivot(), p.rot(), cubes));
		}
		if (parts.isEmpty()) return null;

		MeshDefinition data = new MeshDefinition();
		PartDefinition root = data.getRoot();
		for (PartDef p : parts) {
			CubeListBuilder b = CubeListBuilder.create();
			for (CubeDef c : p.cubes()) {
				b.texOffs(c.u(), c.v()).addBox(c.x(), c.y(), c.z(), c.w(), c.h(), c.d(), new CubeDeformation(c.inflate()));
			}
			root.addOrReplaceChild(p.name(), b, PartPose.offsetAndRotation(p.pivot()[0], p.pivot()[1], p.pivot()[2],
					p.rot()[0], p.rot()[1], p.rot()[2]));
		}
		ModelPart part = LayerDefinition.create(data, def.texW(), def.texH()).bakeRoot();
		return new CosmeticModel(part, def, parts);
	}
}
