package dev.donutauction;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

/** The panel shown at the top of the screen while an auction runs (and after it ends). */
public final class AuctionHud {
	private AuctionHud() {
	}

	public static void render(DrawContext ctx) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.player == null || mc.options.hudHidden) return;

		AuctionManager.Auction a = AuctionManager.active();
		AuctionManager.Result r = AuctionManager.result();
		if (a == null && r == null) return;

		TextRenderer tr = mc.textRenderer;
		int w = 210;
		int h = 52;
		int x = (mc.getWindow().getScaledWidth() - w) / 2;
		int y = 6;

		ctx.fill(x, y, x + w, y + h, 0xAA000000);
		ctx.fill(x, y, x + w, y + 1, 0xFFFFAA00);

		if (a != null) {
			ctx.drawItem(a.stack, x + 6, y + 18);
			int tx = x + 30;
			ctx.drawTextWithShadow(tr, "AUCTION: " + a.name, tx, y + 5, 0xFFFFAA00);
			ctx.drawTextWithShadow(tr, a.count + "x " + a.stack.getName().getString(), tx, y + 16, 0xFFFFFFFF);
			ctx.drawTextWithShadow(tr, "Time left: " + Util.clock(a.secondsLeft()), tx, y + 28, 0xFF55FFFF);
			int topColor = a.leader == null ? 0xFFFFFF55 : 0xFF55FF55;
			ctx.drawTextWithShadow(tr, "Top: " + a.topLine(), tx, y + 40, topColor);
		} else {
			ctx.drawItem(r.stack, x + 6, y + 18);
			int tx = x + 30;
			ctx.drawTextWithShadow(tr, "AUCTION ENDED: " + r.name, tx, y + 5, 0xFFFFAA00);
			ctx.drawTextWithShadow(tr, r.count + "x " + r.stack.getName().getString(), tx, y + 16, 0xFFFFFFFF);
			if (r.winner != null) {
				ctx.drawTextWithShadow(tr, "Winner: " + r.winner, tx, y + 28, 0xFF55FF55);
				ctx.drawTextWithShadow(tr, "Paid: " + Util.money(r.amount), tx, y + 40, 0xFF55FF55);
			} else {
				ctx.drawTextWithShadow(tr, "No bids", tx, y + 28, 0xFFFF5555);
			}
		}
	}
}
