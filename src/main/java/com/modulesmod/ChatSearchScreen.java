package com.modulesmod;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/** Search through your whole chat history (newest first). Open it with /chatsearch or from the Chat settings. */
public class ChatSearchScreen extends Screen {
	private static final int TOP = 44;
	private static final int LINE_H = 10;
	private static final int MAX_RESULTS = 300;

	private final Screen parent;
	private String query;
	private final List<FormattedCharSequence> lines = new ArrayList<>();
	private int results = 0;
	private int scroll = 0;
	private int maxScroll = 0;

	public ChatSearchScreen(Screen parent, String query) {
		super(Component.literal("Chat Search"));
		this.parent = parent;
		this.query = query == null ? "" : query;
	}

	private int contentW() {
		return Math.min(width - 40, 520);
	}

	@Override
	protected void init() {
		int w = contentW();
		EditBox field = new EditBox(font, (width - w) / 2, 16, w, 20, Component.literal("Search"));
		field.setHint(Component.literal("Search chat history..."));
		field.setMaxLength(100);
		field.setValue(query);
		field.setResponder(s -> {
			query = s;
			scroll = 0;
			rebuild();
		});
		addRenderableWidget(field);
		setInitialFocus(field);

		addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
				.bounds(width / 2 - 100, height - 28, 200, 20).build());
		rebuild();
	}

	private void rebuild() {
		lines.clear();
		results = 0;
		String q = query.trim().toLowerCase(Locale.ROOT);
		int w = contentW() - 10;

		Iterator<ChatState.LogEntry> it = ChatState.LOG.descendingIterator();
		while (it.hasNext()) {
			ChatState.LogEntry e = it.next();
			if (!q.isEmpty() && !e.plain().toLowerCase(Locale.ROOT).contains(q)) continue;
			if (++results > MAX_RESULTS) {
				results = MAX_RESULTS;
				break;
			}
			Component body = q.isEmpty() ? e.text() : ChatTransformer.highlight(e.text(), q);
			Component shown = Component.literal("[" + e.timeText() + "] ").withStyle(ChatFormatting.GRAY).append(body);
			lines.addAll(font.split(shown, w));
		}
		updateScrollBounds();
	}

	private void updateScrollBounds() {
		int viewH = height - 34 - TOP;
		maxScroll = Math.max(0, lines.size() * LINE_H - viewH);
		scroll = Math.max(0, Math.min(maxScroll, scroll));
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		scroll = Math.max(0, Math.min(maxScroll, scroll - (int) (verticalAmount * 24)));
		return true;
	}

	@Override
	public void onClose() {
		minecraft.gui.setScreen(parent);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
		super.extractRenderState(ctx, mouseX, mouseY, delta);
		ctx.drawString(font, "Chat Search", (width - font.width("Chat Search")) / 2, 4, 0xFFFFFFFF);

		updateScrollBounds();
		int left = (width - contentW()) / 2 + 5;
		int bottom = height - 34;

		if (lines.isEmpty()) {
			String msg = ChatState.LOG.isEmpty() ? "No messages logged yet" : "No messages found";
			ctx.drawString(font, msg, (width - font.width(msg)) / 2, TOP + 20, 0xFFAAAAAA);
		}

		for (int i = 0; i < lines.size(); i++) {
			int y = TOP + i * LINE_H - scroll;
			if (y < TOP || y + 9 > bottom) continue;
			ctx.drawString(font, lines.get(i), left, y, 0xFFFFFFFF, true);
		}

		String count = results + (results >= MAX_RESULTS ? "+" : "") + " result" + (results == 1 ? "" : "s");
		ctx.drawString(font, count, (width - contentW()) / 2, bottom + 2, 0xFF888888);
	}
}
