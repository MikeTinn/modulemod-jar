package com.modulesmod;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.TextGizmo;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

/**
 * Synced lyrics as text floating in the world around your crosshair.
 *  - The song comes from MediaReader (Windows media session).
 *  - The lyrics come from LRCLIB (lrclib.net) using the song title, artist, album and length.
 *  - LRCLIB only has line timings, so the time of every word is spread over its line by word length.
 *  - Each word is placed in the world when it is sung and fades out again; it stays where it was
 *    placed, so you can turn around and see it hanging in the air.
 * Called every frame from DebugRendererMixin (inside the gizmo collector, like the hitboxes).
 */
public final class LyricsController {
	private record Word(long startMs, String text, int lineIdx, int charStart, int lineChars,
						long lineStartMs, long lineEndMs, String lineText) {}

	private static final class Piece {
		String text;
		Vec3 pos;
		long born;
		long life;
		float scale;
		int rgb;
	}

	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(6)).build();
	private static final ExecutorService POOL = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "modulesmod-lyrics");
		t.setDaemon(true);
		return t;
	});
	private static final Pattern STAMP = Pattern.compile("\\[(\\d+):(\\d+)(?:[.:](\\d+))?]");
	private static final Random RNG = new Random();

	/** Words of the current song in time order. Replaced as a whole by the download thread. */
	private static volatile List<Word> words = List.of();
	private static volatile String requestedKey = "";

	/** For the GUI display: which word is being sung (index into words) and whether we are between lines. */
	private static volatile int hudSeq = -1;
	private static volatile boolean hudGap = false;
	private static volatile long hudPos = 0;
	/** Which row the GUI shows (line number, or line number + gap marker) and when it changed, for the slide animation. */
	private static volatile int hudKey = Integer.MIN_VALUE;
	private static volatile long hudKeyMs = 0;
	private static volatile String hudCurText = "";
	private static volatile String hudPrevText = "";

	private static final List<Piece> pieces = new ArrayList<>();
	private static boolean running = false;
	private static int lastSeq = -1;
	private static int lastLineShown = -1;
	private static int anchorLine = -1;
	private static Vec3 anchorCenter = Vec3.ZERO;
	private static Vec3 anchorRight = Vec3.ZERO;

	private LyricsController() {}

	// ------------------------------------------------------------------ per frame

	public static void emit(float tickProgress) {
		if (!Modules.LYRICS.isEnabled()) {
			if (running) {
				MediaReader.stop();
				running = false;
				pieces.clear();
				requestedKey = "";
				Diagnostics.lyrics = "off";
			}
			return;
		}
		try {
			tick();
		} catch (Exception e) {
			Diagnostics.error("lyrics", e);
		}
	}

	private static void tick() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.level == null) return;
		if (!running) {
			MediaReader.start();
			running = true;
		}
		long now = System.currentTimeMillis();
		MediaReader.Track t = MediaReader.get();
		if (t == null) {
			resetHud();
			if (MediaReader.supported()) Diagnostics.lyrics = "no song detected";
			draw(now);
			return;
		}

		String key = t.artist() + "\u0001" + t.title();
		if (!key.equals(requestedKey)) {
			requestedKey = key;
			words = List.of();
			resetHud();
			lastSeq = -1;
			lastLineShown = -1;
			anchorLine = -1;
			pieces.clear();
			Diagnostics.lyrics = "looking up lyrics for " + t.title();
			POOL.execute(() -> load(t, key));
		}

		List<Word> list = words;
		boolean gui = Modules.LY_DISPLAY.index() == 1;
		if (!list.isEmpty()) {
			long pos = t.positionMs() + (t.playing() ? now - t.receivedAtMs() : 0) + (long) Modules.LY_OFFSET.num;
			if (gui) {
				updateHud(list, pos, now);
			} else if (t.playing()) {
				spawnDue(mc, list, pos, now);
			}
		} else {
			resetHud();
		}
		if (gui) pieces.clear();
		else draw(now);
	}

	/** Index of the last word whose start time has passed, or -1 before the first word. */
	private static int seqAt(List<Word> list, long pos) {
		int lo = 0;
		int hi = list.size() - 1;
		int seq = -1;
		while (lo <= hi) {
			int mid = (lo + hi) >>> 1;
			if (list.get(mid).startMs() <= pos) {
				seq = mid;
				lo = mid + 1;
			} else {
				hi = mid - 1;
			}
		}
		return seq;
	}

	private static void spawnDue(Minecraft mc, List<Word> list, long pos, long now) {
		int seq = seqAt(list, pos);
		if (seq < 0 || seq == lastSeq) return;
		int from = (lastSeq >= 0 && seq > lastSeq && seq - lastSeq <= 3) ? lastSeq + 1 : seq;
		lastSeq = seq;
		boolean linesMode = Modules.LY_SHOW.index() == 1;
		for (int i = from; i <= seq; i++) {
			Word w = list.get(i);
			if (linesMode) {
				if (w.lineIdx() != lastLineShown) {
					lastLineShown = w.lineIdx();
					spawn(mc, w, w.lineText(), i, true, now);
				}
			} else {
				spawn(mc, w, w.text(), i, false, now);
			}
		}
	}

	private static void spawn(Minecraft mc, Word w, String text, int index, boolean wholeLine, long now) {
		Vec3 eye = mc.player.getEyePosition();
		double yaw = Math.toRadians(mc.player.getYRot());
		double pitch = Math.toRadians(mc.player.getXRot());
		Vec3 dir = new Vec3(-Math.sin(yaw) * Math.cos(pitch), -Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch));
		Vec3 right = new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw));
		Vec3 up = right.cross(dir);

		double dist = Modules.LY_DISTANCE.num;
		double spread = Modules.LY_SPREAD.num;
		float size = (float) Modules.LY_SIZE.num;
		Vec3 center = lift(mc, eye.add(dir.scale(dist)));
		Vec3 pos;

		if (wholeLine) {
			pos = center;
		} else if (Modules.LY_LAYOUT.index() == 1) {
			// "Center": the words of one line sit in a row, the row stays where the line began
			if (anchorLine != w.lineIdx()) {
				anchorLine = w.lineIdx();
				anchorCenter = center;
				anchorRight = right;
			}
			double charW = 0.09 * size;
			double x = (w.charStart() + w.text().length() / 2.0 - w.lineChars() / 2.0) * charW;
			pos = anchorCenter.add(anchorRight.scale(x));
		} else {
			// "Scatter": anywhere on a ring-ish area around the crosshair, never exactly on it
			double ang = RNG.nextDouble() * Math.PI * 2;
			double r = spread * (0.35 + 0.65 * Math.sqrt(RNG.nextDouble()));
			pos = lift(mc, center.add(right.scale(Math.cos(ang) * r * 1.4)).add(up.scale(Math.sin(ang) * r * 0.8)));
		}

		Piece p = new Piece();
		p.text = LyricsFonts.apply(text, Modules.LY_FONT.index());
		p.pos = pos;
		p.born = now;
		p.scale = size;
		p.life = wholeLine
				? Math.max(1500L, w.lineEndMs() - w.lineStartMs() + 700)
				: (long) Modules.LY_LIFETIME.num;
		p.rgb = Modules.LY_RAINBOW.bool
				? HudUtil.hsvToRgb((index * 0.07f) % 1f, 0.6f, 1f)
				: HudUtil.rgb(Modules.LY_COLOR.str, 0xFFFFFF);
		pieces.add(p);
		if (pieces.size() > 80) pieces.remove(0);
	}

	/**
	 * Keeps text at least "Min Height Above Ground" blocks above the first solid block (or water)
	 * under it, so it never sits in the terrain. Only ever moves the text up.
	 */
	private static Vec3 lift(Minecraft mc, Vec3 pos) {
		double min = Modules.LY_HEIGHT.num;
		if (min <= 0 || mc.level == null) return pos;
		int x = (int) Math.floor(pos.x);
		int z = (int) Math.floor(pos.z);
		int top = (int) Math.floor(pos.y);
		int bottom = Math.max(mc.level.getMinY(), top - 96);
		BlockPos.MutableBlockPos bp = new BlockPos.MutableBlockPos();
		for (int y = top; y >= bottom; y--) {
			bp.set(x, y, z);
			var state = mc.level.getBlockState(bp);
			if (!state.getCollisionShape(mc.level, bp).isEmpty() || !state.getFluidState().isEmpty()) {
				double wanted = y + 1 + min;
				return pos.y < wanted ? new Vec3(pos.x, wanted, pos.z) : pos;
			}
		}
		return pos;
	}

	private static void draw(long now) {
		Iterator<Piece> it = pieces.iterator();
		while (it.hasNext()) {
			Piece p = it.next();
			long age = now - p.born;
			if (age > p.life) {
				it.remove();
				continue;
			}
			float in = Math.min(1f, age / 160f);
			float fadeStart = p.life * 0.6f;
			float out = age > fadeStart ? 1f - (age - fadeStart) / (p.life - fadeStart) : 1f;
			float a = in * out;
			int alpha = Math.round(255 * a);
			if (alpha < 4) continue;
			float ease = 1f - (1f - in) * (1f - in) * (1f - in);
			float pop = 0.75f + 0.25f * ease;
			Vec3 at = p.pos.add(0, 0.12 * ease, 0);
			drawText(p.text, at, (alpha << 24) | p.rgb, p.scale * pop);
		}
	}

	/**
	 * The ONLY place that touches the game's text gizmo. If a Minecraft update renames something,
	 * this is the one method to fix.
	 */
	private static void drawText(String text, Vec3 pos, int argb, float scale) {
		Gizmos.billboardText(text, pos, TextGizmo.Style.forColor(argb).withScale(scale));
	}

	// ------------------------------------------------------------------ lyrics download

	private static void load(MediaReader.Track t, String key) {
		try {
			List<long[]> stamps = new ArrayList<>();
			List<String> texts = new ArrayList<>();
			String lrc = fetchSynced(t);
			if (lrc == null) {
				if (key.equals(requestedKey)) Diagnostics.lyrics = "no synced lyrics found for " + t.title();
				return;
			}
			parseLrc(lrc, stamps, texts);
			List<Word> built = buildWords(stamps, texts);
			if (key.equals(requestedKey)) {
				words = built;
				Diagnostics.lyrics = built.size() + " words ready for " + t.title();
			}
		} catch (Exception e) {
			Diagnostics.error("lyrics download", e);
			if (key.equals(requestedKey)) Diagnostics.lyrics = "lyrics download failed";
		}
	}

	private static String fetchSynced(MediaReader.Track t) throws Exception {
		StringBuilder q = new StringBuilder("track_name=").append(enc(t.title()))
				.append("&artist_name=").append(enc(t.artist()));
		if (!t.album().isEmpty()) q.append("&album_name=").append(enc(t.album()));
		if (t.durationMs() > 0) q.append("&duration=").append(Math.round(t.durationMs() / 1000.0));
		String body = get("https://lrclib.net/api/get?" + q);
		if (body != null) {
			String s = synced(JsonParser.parseString(body));
			if (s != null) return s;
		}
		// not an exact match: take the first search result that has synced lyrics
		String searchBody = get("https://lrclib.net/api/search?track_name=" + enc(t.title())
				+ "&artist_name=" + enc(t.artist()));
		if (searchBody != null) {
			JsonElement root = JsonParser.parseString(searchBody);
			if (root.isJsonArray()) {
				JsonArray arr = root.getAsJsonArray();
				for (JsonElement e : arr) {
					String s = synced(e);
					if (s != null) return s;
				}
			}
		}
		return null;
	}

	private static String synced(JsonElement e) {
		if (e == null || !e.isJsonObject()) return null;
		JsonObject o = e.getAsJsonObject();
		JsonElement s = o.get("syncedLyrics");
		if (s == null || s.isJsonNull()) return null;
		String str = s.getAsString();
		return str.isBlank() ? null : str;
	}

	private static String get(String url) throws Exception {
		HttpRequest req = HttpRequest.newBuilder(URI.create(url))
				.timeout(Duration.ofSeconds(10))
				.header("User-Agent", "modulesmod/" + ModulesMod.MOD_ID + " (Minecraft mod)")
				.GET().build();
		HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
		return res.statusCode() == 200 ? res.body() : null;
	}

	private static String enc(String s) {
		return URLEncoder.encode(s, StandardCharsets.UTF_8);
	}

	/** "[01:23.45] some words" (also several stamps on one line) -> start times and texts. */
	private static void parseLrc(String lrc, List<long[]> stamps, List<String> texts) {
		for (String raw : lrc.split("\\R")) {
			Matcher m = STAMP.matcher(raw);
			List<Long> times = new ArrayList<>();
			int end = 0;
			while (m.find()) {
				long min = Long.parseLong(m.group(1));
				long sec = Long.parseLong(m.group(2));
				long ms = 0;
				String frac = m.group(3);
				if (frac != null) {
					ms = frac.length() >= 3 ? Long.parseLong(frac.substring(0, 3))
							: Long.parseLong(frac) * (frac.length() == 1 ? 100 : 10);
				}
				times.add(min * 60_000 + sec * 1000 + ms);
				end = m.end();
			}
			if (times.isEmpty()) continue;
			String text = raw.substring(end).trim();
			for (long tm : times) {
				stamps.add(new long[]{tm});
				texts.add(text);
			}
		}
		// keep both lists in time order
		Integer[] order = new Integer[stamps.size()];
		for (int i = 0; i < order.length; i++) order[i] = i;
		java.util.Arrays.sort(order, Comparator.comparingLong(i -> stamps.get(i)[0]));
		List<long[]> s2 = new ArrayList<>();
		List<String> t2 = new ArrayList<>();
		for (Integer i : order) {
			s2.add(stamps.get(i));
			t2.add(texts.get(i));
		}
		stamps.clear();
		stamps.addAll(s2);
		texts.clear();
		texts.addAll(t2);
	}

	/** Splits every line into words and spreads the line's time over them by word length. */
	private static List<Word> buildWords(List<long[]> stamps, List<String> texts) {
		List<Word> out = new ArrayList<>();
		for (int i = 0; i < stamps.size(); i++) {
			String line = texts.get(i);
			if (line.isEmpty()) continue;
			long start = stamps.get(i)[0];
			long nextStart = i + 1 < stamps.size() ? stamps.get(i + 1)[0] : start + 5000;
			long span = Math.max(300, Math.min(nextStart - start, 7000) - 150);
			long lineEnd = start + span;

			String[] parts = line.split("\\s+");
			int total = 0;
			for (String p : parts) total += p.length() + 1;
			int cum = 0;
			int charPos = 0;
			for (String p : parts) {
				long ws = start + Math.round(span * (cum / (double) total));
				out.add(new Word(ws, p, i, charPos, line.length(), start, lineEnd, line));
				cum += p.length() + 1;
				charPos += p.length() + 1;
			}
		}
		return out;
	}

	// ------------------------------------------------------------------ GUI display

	/**
	 * What the GUI display shows. The line is sung + partial + rest (no gaps in between). In "By Letter"
	 * mode "partial" is the one letter that is lighting up right now (partialFrac = how far, 0-1);
	 * in "By Word" mode it is empty. prev is the line that just ended and anim runs 0 -> 1 while the
	 * rows slide up (1 = finished).
	 */
	public record HudLine(String sung, String partial, float partialFrac, String rest, String next,
						  String prev, float anim) {}

	private static void resetHud() {
		hudSeq = -1;
		hudKey = Integer.MIN_VALUE;
		hudCurText = "";
		hudPrevText = "";
	}

	private static void updateHud(List<Word> list, long pos, long now) {
		int seq = seqAt(list, pos);
		hudPos = pos;
		hudSeq = seq;
		hudGap = seq >= 0 && pos > list.get(seq).lineEndMs() + 3000;
		if (seq < 0) return;
		Word w = list.get(seq);
		int key = w.lineIdx() * 2 + (hudGap ? 1 : 0);
		if (key != hudKey) {
			hudPrevText = hudKey == Integer.MIN_VALUE ? "" : hudCurText;
			hudKey = key;
			hudKeyMs = now;
		}
		hudCurText = hudGap ? "\u266A" : w.lineText();
	}

	public static HudLine hudLine() {
		List<Word> list = words;
		int seq = hudSeq;
		if (list.isEmpty() || seq < 0 || seq >= list.size()) return null;
		Word w = list.get(seq);
		int first = seq;
		while (first > 0 && list.get(first - 1).lineIdx() == w.lineIdx()) first--;
		int last = seq;
		while (last + 1 < list.size() && list.get(last + 1).lineIdx() == w.lineIdx()) last++;
		String next = last + 1 < list.size() ? list.get(last + 1).lineText() : "";

		double ms = Modules.LY_TRANSITION.num;
		float anim = ms <= 0 ? 1f : (float) Math.max(0.0, Math.min(1.0, (System.currentTimeMillis() - hudKeyMs) / ms));
		String prev = hudPrevText;

		if (hudGap) return new HudLine("\u266A", "", 0f, "", next, prev, anim);

		StringBuilder sung = new StringBuilder();
		for (int i = first; i < seq; i++) {
			if (i > first) sung.append(' ');
			sung.append(list.get(i).text());
		}
		StringBuilder tail = new StringBuilder();
		for (int i = seq + 1; i <= last; i++) tail.append(' ').append(list.get(i).text());

		String cur = w.text();
		if (seq > first) sung.append(' ');
		if (Modules.LY_HIGHLIGHT.index() != 1) {
			// by word: the whole word lights up the moment it starts
			sung.append(cur);
			return new HudLine(sung.toString(), "", 0f, tail.toString(), next, prev, anim);
		}

		// by letter: letters light up one after another across the time of the word
		long start = w.startMs();
		long end = seq < last ? list.get(seq + 1).startMs() : w.lineEndMs();
		double frac = end > start ? Math.max(0.0, Math.min(1.0, (hudPos - start) / (double) (end - start))) : 1.0;
		double lit = frac * cur.length();
		int n = (int) Math.floor(lit);
		if (n >= cur.length()) {
			sung.append(cur);
			return new HudLine(sung.toString(), "", 0f, tail.toString(), next, prev, anim);
		}
		sung.append(cur, 0, n);
		return new HudLine(sung.toString(), cur.substring(n, n + 1), (float) (lit - n),
				cur.substring(n + 1) + tail, next, prev, anim);
	}

	// ------------------------------------------------------------------ status button

	public static void printStatus() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) return;
		MediaReader.Track t = MediaReader.get();
		String song = t == null ? "none detected" : t.artist() + " - " + t.title() + (t.playing() ? " (playing)" : " (paused)");
		mc.gui.hud.getChat().addMessage(Component.literal("[Lyrics] song: " + song));
		mc.gui.hud.getChat().addMessage(Component.literal("[Lyrics] status: " + Diagnostics.lyrics));
		if (!Modules.LYRICS.isEnabled()) {
			mc.gui.hud.getChat().addMessage(Component.literal("[Lyrics] the module is switched off, so nothing is looked up"));
		}
	}
}
