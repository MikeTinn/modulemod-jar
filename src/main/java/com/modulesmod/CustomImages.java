package com.modulesmod;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.w3c.dom.Node;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.ImageInputStream;
import com.mojang.blaze3d.platform.NativeImage;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Your own pictures as cosmetics. Drop .png, .jpg, .jpeg or .gif files into
 * config/modulesmod-cosmetics/capes/ and they show up as capes (a gif becomes an animated cape).
 * The picture is cropped to the cape's shape (5:8) and laid onto the back of the cape.
 */
public final class CustomImages {
	/** A cape picture: one texture, or several frames for a gif. */
	public static final class Image {
		private final List<Identifier> frames;
		private final int[] delaysMs;
		private final int totalMs;

		Image(List<Identifier> frames, List<Integer> delays) {
			this.frames = frames;
			this.delaysMs = delays.stream().mapToInt(Integer::intValue).toArray();
			int sum = 0;
			for (int d : delaysMs) sum += d;
			this.totalMs = Math.max(1, sum);
		}

		public Identifier current() {
			if (frames.size() == 1) return frames.get(0);
			long t = System.currentTimeMillis() % totalMs;
			for (int i = 0; i < frames.size(); i++) {
				t -= delaysMs[i];
				if (t < 0) return frames.get(i);
			}
			return frames.get(frames.size() - 1);
		}
	}

	private static final int K = 8;           // texture pixels per model unit
	private static final int MAX_FRAMES = 80;
	private static final List<Identifier> REGISTERED = new ArrayList<>();

	private CustomImages() {}

	public static Path root() {
		return FabricLoader.getInstance().getConfigDir().resolve("modulesmod-cosmetics");
	}

	public static Path capesFolder() {
		return root().resolve("capes");
	}

	public static void makeFolders() {
		try {
			Files.createDirectories(capesFolder());
		} catch (Exception e) {
			Diagnostics.error("cosmetics folder", e);
		}
	}

	/** (Re)loads every picture in the capes folder. Must run on the game's main thread. */
	static List<Cosmetics.Def> load() throws Exception {
		Minecraft mc = Minecraft.getInstance();
		for (Identifier id : REGISTERED) mc.getTextureManager().release(id);
		REGISTERED.clear();

		List<Cosmetics.Def> out = new ArrayList<>();
		makeFolders();
		List<File> files = new ArrayList<>();
		try (Stream<Path> s = Files.list(capesFolder())) {
			s.map(Path::toFile).filter(f -> f.isFile() && isImage(f.getName())).sorted().forEach(files::add);
		}
		for (File f : files) {
			try {
				out.add(makeCape(f));
			} catch (Throwable t) {
				Diagnostics.error("image " + f.getName(), t);
			}
		}
		return out;
	}

	private static boolean isImage(String name) {
		String n = name.toLowerCase(Locale.ROOT);
		return n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".gif");
	}

	private static Cosmetics.Def makeCape(File file) throws Exception {
		List<BufferedImage> frames = new ArrayList<>();
		List<Integer> delays = new ArrayList<>();
		if (file.getName().toLowerCase(Locale.ROOT).endsWith(".gif")) {
			readGif(file, frames, delays);
		}
		if (frames.isEmpty()) {
			BufferedImage img = ImageIO.read(file);
			if (img == null) throw new IllegalStateException("not a readable picture");
			frames.add(img);
			delays.add(1000);
		}

		String slug = slug(file.getName()) + "_" + Integer.toHexString(file.getName().hashCode());
		List<Identifier> ids = new ArrayList<>();
		for (int i = 0; i < frames.size(); i++) {
			ids.add(register(capeCanvas(frames.get(i)), slug + "_" + i));
		}

		BufferedImage first = frames.get(0);
		int[] icon = thumbnail(first);
		int accent = averageColor(first) | 0xFF000000;

		String name = file.getName();
		int dot = name.lastIndexOf('.');
		String shown = (dot > 0 ? name.substring(0, dot) : name) + (frames.size() > 1 ? " (gif)" : "");

		Cosmetics.PartDef part = new Cosmetics.PartDef("cape", new float[]{0f, 0f, 2f}, new float[]{0.14f, 0f, 0f},
				List.of(new Cosmetics.CubeDef(0, 0, -5f, 0f, 0f, 10f, 16f, 1f, 0f, false)));
		return new Cosmetics.Def("custom:" + file.getName(), shown, Cosmetics.Category.CAPE, "BODY", "sway", 1f,
				accent, "", 22, 17, List.of(part), new Image(ids, delays), icon, 0);
	}

	private static String slug(String name) {
		return name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "_");
	}

	private static Identifier register(BufferedImage img, String name) throws Exception {
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		ImageIO.write(img, "png", bos);
		NativeImage ni = NativeImage.read(new ByteArrayInputStream(bos.toByteArray()));
		Identifier id = Identifier.fromNamespaceAndPath(ModulesMod.MOD_ID, "custom/" + name);
		Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> name, ni));
		REGISTERED.add(id);
		return id;
	}

	// ------------------------------------------------------------------ drawing the cape texture

	/**
	 * The cape model's texture is 22 x 17 "units": the back of the cape is the 10 x 16 area at x 12..22, y 1..17.
	 * We build a bitmap with that layout (8 pixels per unit) and put the picture on the back (and front).
	 */
	private static BufferedImage capeCanvas(BufferedImage src) {
		BufferedImage out = new BufferedImage(22 * K, 17 * K, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = out.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		g.setColor(new Color(averageColor(src) | 0xFF000000));
		g.fillRect(0, 0, out.getWidth(), out.getHeight());
		drawCover(g, src, 12 * K, 1 * K, 10 * K, 16 * K);
		drawCover(g, src, 1 * K, 1 * K, 10 * K, 16 * K);
		g.dispose();
		return out;
	}

	/** Fills the rectangle with the picture, cropping the middle so it is not stretched. */
	private static void drawCover(Graphics2D g, BufferedImage src, int x, int y, int w, int h) {
		double scale = Math.max(w / (double) src.getWidth(), h / (double) src.getHeight());
		int cw = (int) Math.round(w / scale);
		int ch = (int) Math.round(h / scale);
		int sx = (src.getWidth() - cw) / 2;
		int sy = (src.getHeight() - ch) / 2;
		g.drawImage(src, x, y, x + w, y + h, sx, sy, sx + cw, sy + ch, null);
	}

	private static int averageColor(BufferedImage img) {
		long r = 0, gr = 0, b = 0, n = 0;
		int stepX = Math.max(1, img.getWidth() / 16);
		int stepY = Math.max(1, img.getHeight() / 16);
		for (int y = 0; y < img.getHeight(); y += stepY) {
			for (int x = 0; x < img.getWidth(); x += stepX) {
				int p = img.getRGB(x, y);
				if ((p >>> 24) < 40) continue;
				r += (p >> 16) & 0xFF;
				gr += (p >> 8) & 0xFF;
				b += p & 0xFF;
				n++;
			}
		}
		if (n == 0) return 0x808080;
		return ((int) (r / n) << 16) | ((int) (gr / n) << 8) | (int) (b / n);
	}

	private static int[] thumbnail(BufferedImage src) {
		BufferedImage t = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = t.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		drawCover(g, src, 0, 0, 16, 16);
		g.dispose();
		return t.getRGB(0, 0, 16, 16, null, 0, 16);
	}

	// ------------------------------------------------------------------ gif

	private static void readGif(File file, List<BufferedImage> frames, List<Integer> delays) throws Exception {
		ImageReader reader = ImageIO.getImageReadersByFormatName("gif").next();
		try (ImageInputStream in = ImageIO.createImageInputStream(file)) {
			reader.setInput(in, false);
			int count = Math.min(reader.getNumImages(true), MAX_FRAMES);

			int w = 0;
			int h = 0;
			IIOMetadata streamMeta = reader.getStreamMetadata();
			if (streamMeta != null) {
				Node root = streamMeta.getAsTree("javax_imageio_gif_stream_1.0");
				for (Node c = root.getFirstChild(); c != null; c = c.getNextSibling()) {
					if ("LogicalScreenDescriptor".equals(c.getNodeName())) {
						w = parse(attr(c, "logicalScreenWidth"), 0);
						h = parse(attr(c, "logicalScreenHeight"), 0);
					}
				}
			}

			BufferedImage canvas = null;
			for (int i = 0; i < count; i++) {
				BufferedImage frame = reader.read(i);
				int left = 0;
				int top = 0;
				int delay = 10;
				String disposal = "none";
				Node tree = reader.getImageMetadata(i).getAsTree("javax_imageio_gif_image_1.0");
				for (Node c = tree.getFirstChild(); c != null; c = c.getNextSibling()) {
					if ("ImageDescriptor".equals(c.getNodeName())) {
						left = parse(attr(c, "imageLeftPosition"), 0);
						top = parse(attr(c, "imageTopPosition"), 0);
					} else if ("GraphicControlExtension".equals(c.getNodeName())) {
						delay = parse(attr(c, "delayTime"), 10);
						disposal = attr(c, "disposalMethod");
					}
				}
				if (canvas == null) {
					if (w <= 0) w = frame.getWidth() + left;
					if (h <= 0) h = frame.getHeight() + top;
					canvas = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
				}
				BufferedImage before = "restoreToPrevious".equals(disposal) ? copy(canvas) : null;
				Graphics2D g = canvas.createGraphics();
				g.drawImage(frame, left, top, null);
				g.dispose();
				frames.add(copy(canvas));
				delays.add(delay <= 1 ? 100 : delay * 10);

				if ("restoreToBackgroundColor".equals(disposal)) {
					Graphics2D cg = canvas.createGraphics();
					cg.setComposite(java.awt.AlphaComposite.Clear);
					cg.fillRect(left, top, frame.getWidth(), frame.getHeight());
					cg.dispose();
				} else if (before != null) {
					canvas = before;
				}
			}
		} finally {
			reader.dispose();
		}
	}

	private static BufferedImage copy(BufferedImage src) {
		BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = out.createGraphics();
		g.drawImage(src, 0, 0, null);
		g.dispose();
		return out;
	}

	private static String attr(Node n, String name) {
		Node a = n.getAttributes().getNamedItem(name);
		return a == null ? "" : a.getNodeValue();
	}

	private static int parse(String s, int fallback) {
		try {
			return Integer.parseInt(s);
		} catch (Exception e) {
			return fallback;
		}
	}
}
