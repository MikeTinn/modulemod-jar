package com.modulesmod;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.Locale;
import net.minecraft.network.chat.Component;

/** Our own log of every chat message (used by Chat Search) plus a little animation state. */
public final class ChatState {
	public record LogEntry(String timeText, Component text, String plain) {}

	/** Oldest first. */
	public static final ArrayDeque<LogEntry> LOG = new ArrayDeque<>();

	/** When the newest message arrived and how many lines it probably takes (for Smooth Chat). */
	public static volatile long lastAddedMs = 0;
	public static volatile int lastAddedLines = 1;

	/** Stacking: the last message, how many times in a row it was sent, and how many chat lines it uses. */
	public static volatile String lastPlain = null;
	public static volatile int lastCount = 1;
	public static volatile int lastVisibleLines = 0;
	/** How many lines of the previous copy the stacking hook must remove after the new copy is added. */
	public static volatile int stackRemove = 0;

	/** The chat scale vanilla last used (copied by ChatHudMixin, because getChatScale() is private). */
	public static volatile double lastChatScale = 1.0;

	private ChatState() {}

	public static void record(Component original, String plain) {
		String time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss", Locale.ENGLISH));
		LOG.addLast(new LogEntry(time, original, plain));
		int cap = Math.max(1000, (int) Modules.CH_HISTORY.num);
		while (LOG.size() > cap) LOG.pollFirst();
	}

	public static void added(int lines) {
		lastAddedLines = Math.max(1, Math.min(6, lines));
		lastAddedMs = System.currentTimeMillis();
	}
}
