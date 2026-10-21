package dev.donutauction;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Screen to pick any item, set name/time/min bid and start the auction. Open with K. */
public class AuctionScreen extends Screen {
	private static final int COLS = 9;
	private static final int ROWS = 4;
	private static final int CELL = 22;
	private static final int PER_PAGE = COLS * ROWS;

	private record Entry(Item item, String name, String haystack) {
	}

	private static List<Entry> allItems;

	// remembered between openings
	private static String lastSearch = "";
	private static String lastName = "";
	private static String lastTime = "5m";
	private static String lastMin = "1k";
	private static String lastCount = "1";
	private static Item lastItem;

	private List<Entry> filtered = new ArrayList<>();
	private int page;
	private Item selected = lastItem;

	private TextFieldWidget search;
	private TextFieldWidget nameField;
	private TextFieldWidget timeField;
	private TextFieldWidget minField;
	private TextFieldWidget countField;
	private final List<ButtonWidget> cells = new ArrayList<>();

	private int left;
	private int top;
	private int gridX;
	private int gridY;
	private String status = "";
	private int statusColor = 0xFFFF5555;

	public AuctionScreen() {
		super(Text.literal("Auction creator"));
	}

	private static List<Entry> items() {
		if (allItems == null) {
			List<Entry> list = new ArrayList<>();
			for (Item item : Registries.ITEM) {
				if (item == Items.AIR) continue;
				ItemStack stack = item.getDefaultStack();
				String name = stack.getName().getString();
				String id = Registries.ITEM.getId(item).getPath().replace('_', ' ');
				list.add(new Entry(item, name, (name + " " + id).toLowerCase(Locale.ROOT)));
			}
			list.sort(Comparator.comparing(Entry::name, String.CASE_INSENSITIVE_ORDER));
			allItems = list;
		}
		return allItems;
	}

	@Override
	protected void init() {
		left = this.width / 2 - 100;
		top = Math.max(6, (this.height - 252) / 2);
		gridX = left + 1;
		gridY = top + 36;

		search = new TextFieldWidget(this.textRenderer, left, top + 14, 200, 18, Text.literal("Search"));
		search.setPlaceholder(Text.literal("Search item (e.g. diamond)"));
		search.setMaxLength(50);
		search.setText(lastSearch);
		search.setChangedListener(s -> {
			lastSearch = s;
			page = 0;
			applyFilter();
		});
		addDrawableChild(search);

		for (int i = 0; i < PER_PAGE; i++) {
			final int idx = i;
			int cx = gridX + (i % COLS) * CELL;
			int cy = gridY + (i / COLS) * CELL;
			ButtonWidget cell = ButtonWidget.builder(Text.empty(), b -> {
				Entry e = entryAt(idx);
				if (e != null) {
					selected = e.item();
					lastItem = selected;
					if (nameField.getText().isBlank()) nameField.setText(e.name() + " auction");
				}
			}).dimensions(cx, cy, CELL - 2, CELL - 2).build();
			cells.add(cell);
			addDrawableChild(cell);
		}

		addDrawableChild(ButtonWidget.builder(Text.literal("<"), b -> changePage(-1))
			.dimensions(left, top + 126, 40, 16).build());
		addDrawableChild(ButtonWidget.builder(Text.literal(">"), b -> changePage(1))
			.dimensions(left + 160, top + 126, 40, 16).build());

		nameField = new TextFieldWidget(this.textRenderer, left, top + 162, 200, 18, Text.literal("Name"));
		nameField.setPlaceholder(Text.literal("Auction name"));
		nameField.setMaxLength(40);
		nameField.setText(lastName);
		addDrawableChild(nameField);

		timeField = new TextFieldWidget(this.textRenderer, left, top + 184, 98, 18, Text.literal("Time"));
		timeField.setPlaceholder(Text.literal("Time: 5m, 90s, 1h"));
		timeField.setMaxLength(12);
		timeField.setText(lastTime);
		addDrawableChild(timeField);

		minField = new TextFieldWidget(this.textRenderer, left + 102, top + 184, 98, 18, Text.literal("Min bid"));
		minField.setPlaceholder(Text.literal("Min bid: 1k, 2.5m"));
		minField.setMaxLength(14);
		minField.setText(lastMin);
		addDrawableChild(minField);

		countField = new TextFieldWidget(this.textRenderer, left, top + 206, 48, 18, Text.literal("Quantity"));
		countField.setPlaceholder(Text.literal("Qty"));
		countField.setMaxLength(5);
		countField.setText(lastCount);
		addDrawableChild(countField);

		addDrawableChild(ButtonWidget.builder(Text.literal("Start auction"), b -> onStart())
			.dimensions(left + 52, top + 205, 148, 20).build());

		if (AuctionManager.active() != null) {
			addDrawableChild(ButtonWidget.builder(Text.literal("Cancel running auction"), b -> {
				AuctionManager.cancel(this.client);
				close();
			}).dimensions(left, top + 228, 200, 20).build());
		}

		setInitialFocus(search);
		applyFilter();
	}

	private void applyFilter() {
		String q = search.getText().trim().toLowerCase(Locale.ROOT);
		String[] tokens = q.isEmpty() ? new String[0] : q.split("\\s+");
		List<Entry> out = new ArrayList<>();
		for (Entry e : items()) {
			boolean ok = true;
			for (String t : tokens) {
				if (!e.haystack().contains(t)) {
					ok = false;
					break;
				}
			}
			if (ok) out.add(e);
		}
		filtered = out;
		clampPage();
		refreshCells();
	}

	private int pageCount() {
		return Math.max(1, (filtered.size() + PER_PAGE - 1) / PER_PAGE);
	}

	private void clampPage() {
		page = Math.max(0, Math.min(page, pageCount() - 1));
	}

	private void changePage(int delta) {
		page += delta;
		clampPage();
		refreshCells();
	}

	private Entry entryAt(int cellIndex) {
		int i = page * PER_PAGE + cellIndex;
		return i >= 0 && i < filtered.size() ? filtered.get(i) : null;
	}

	private void refreshCells() {
		for (int i = 0; i < cells.size(); i++) {
			boolean has = entryAt(i) != null;
			cells.get(i).visible = has;
			cells.get(i).active = has;
		}
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		if (verticalAmount != 0) {
			changePage(verticalAmount < 0 ? 1 : -1);
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
	}

	private void fail(String message) {
		status = message;
		statusColor = 0xFFFF5555;
	}

	private void onStart() {
		if (selected == null) {
			fail("Click an item first.");
			return;
		}
		String name = nameField.getText().trim();
		if (name.isEmpty()) {
			fail("Write a name for the auction.");
			return;
		}
		long seconds = Util.parseDurationSeconds(timeField.getText());
		if (seconds < 0) {
			fail("Time not valid. Use 5m, 90s, 1h30m...");
			return;
		}
		double minBid = Util.parseMoney(minField.getText());
		if (minBid < 0) {
			fail("Min bid not valid. Use 1000, 5k, 2.5m...");
			return;
		}
		int count;
		try {
			count = Integer.parseInt(countField.getText().trim());
		} catch (NumberFormatException e) {
			count = -1;
		}
		if (count < 1) {
			fail("Quantity must be 1 or more.");
			return;
		}

		String error = AuctionManager.start(this.client, name, selected.getDefaultStack(), count, seconds, minBid);
		if (error != null) {
			fail(error);
			return;
		}
		close();
	}

	@Override
	public void removed() {
		if (nameField != null) lastName = nameField.getText();
		if (timeField != null) lastTime = timeField.getText();
		if (minField != null) lastMin = minField.getText();
		if (countField != null) lastCount = countField.getText();
		super.removed();
	}

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		super.render(ctx, mouseX, mouseY, delta);

		ctx.drawCenteredTextWithShadow(this.textRenderer, Text.literal("DonutSMP Auction Creator"), this.width / 2, top, 0xFFFFFFFF);

		// item icons on top of the (empty) cell buttons
		ItemStack hovered = null;
		for (int i = 0; i < PER_PAGE; i++) {
			Entry e = entryAt(i);
			if (e == null) continue;
			int cx = gridX + (i % COLS) * CELL;
			int cy = gridY + (i / COLS) * CELL;
			ItemStack stack = e.item().getDefaultStack();
			ctx.drawItem(stack, cx + 1, cy + 1);
			if (e.item() == selected) outline(ctx, cx - 1, cy - 1, CELL, CELL, 0xFF55FF55);
			if (mouseX >= cx && mouseX < cx + CELL - 2 && mouseY >= cy && mouseY < cy + CELL - 2) hovered = stack;
		}

		String pageText = filtered.isEmpty()
			? "No items found"
			: "Page " + (page + 1) + "/" + pageCount() + " (" + filtered.size() + " items)";
		ctx.drawCenteredTextWithShadow(this.textRenderer, Text.literal(pageText), this.width / 2, top + 130, 0xFFAAAAAA);

		if (selected != null) {
			ItemStack stack = selected.getDefaultStack();
			ctx.drawItem(stack, left, top + 143);
			ctx.drawTextWithShadow(this.textRenderer, "Selected: " + stack.getName().getString(), left + 22, top + 148, 0xFF55FF55);
		} else {
			ctx.drawTextWithShadow(this.textRenderer, "Click an item above to choose it", left, top + 148, 0xFFAAAAAA);
		}

		if (!status.isEmpty()) {
			ctx.drawTextWithShadow(this.textRenderer, status, left, top + (AuctionManager.active() != null ? 252 : 230), statusColor);
		}

		if (hovered != null) ctx.drawItemTooltip(this.textRenderer, hovered, mouseX, mouseY);
	}

	private static void outline(DrawContext ctx, int x, int y, int w, int h, int color) {
		ctx.fill(x, y, x + w, y + 1, color);
		ctx.fill(x, y + h - 1, x + w, y + h, color);
		ctx.fill(x, y, x + 1, y + h, color);
		ctx.fill(x + w - 1, y, x + w, y + h, color);
	}

	@Override
	public boolean shouldPause() {
		return false;
	}
}
