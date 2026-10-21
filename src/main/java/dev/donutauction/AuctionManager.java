package dev.donutauction;

import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Holds the running auction, reads /pay messages and sends the announcements. */
public final class AuctionManager {
	public static final class Auction {
		public final String name;
		public final ItemStack stack;
		public final int count;
		public final long durationSeconds;
		public final long endMillis;
		public final double minBid;
		public final String host;
		/** total paid per player */
		public final Map<String, Double> totals = new LinkedHashMap<>();
		public String leader;
		public double leaderAmount;
		final Set<Integer> announced = new HashSet<>();

		Auction(String name, ItemStack stack, int count, long durationSeconds, double minBid, String host) {
			this.name = name;
			this.stack = stack;
			this.count = count;
			this.durationSeconds = durationSeconds;
			this.endMillis = System.currentTimeMillis() + durationSeconds * 1000L;
			this.minBid = minBid;
			this.host = host;
		}

		public long secondsLeft() {
			return Math.max(0, (long) Math.ceil((endMillis - System.currentTimeMillis()) / 1000.0));
		}

		public String topLine() {
			if (leader == null) return "No bids yet (min " + Util.money(minBid) + ")";
			return leader + " - " + Util.money(leaderAmount);
		}
	}

	public static final class Result {
		public final String name;
		public final ItemStack stack;
		public final int count;
		public final String winner;
		public final double amount;
		public final long untilMillis;

		Result(String name, ItemStack stack, int count, String winner, double amount, long untilMillis) {
			this.name = name;
			this.stack = stack;
			this.count = count;
			this.winner = winner;
			this.amount = amount;
			this.untilMillis = untilMillis;
		}
	}

	private static Auction active;
	private static Result result;
	private static final Deque<String> outbox = new ArrayDeque<>();
	private static int cooldown;

	private AuctionManager() {
	}

	public static Auction active() {
		return active;
	}

	public static Result result() {
		return result;
	}

	/** @return an error message, or null when the auction started. */
	public static String start(MinecraftClient mc, String name, ItemStack stack, int count, long seconds, double minBid) {
		if (mc.player == null || mc.getNetworkHandler() == null) return "You are not in a world/server.";
		if (active != null) return "An auction is already running. Cancel it first.";
		if (seconds < 5) return "Time must be at least 5 seconds.";
		if (seconds > 24 * 3600) return "Time can be 24h at most.";

		String me = mc.getSession().getUsername();
		active = new Auction(name, stack, count, seconds, minBid, me);
		result = null;
		outbox.clear();

		say(mc, Util.fill(AuctionConfig.get().startMessage, Map.of(
			"name", name,
			"count", String.valueOf(count),
			"item", stack.getName().getString(),
			"min", Util.money(minBid),
			"time", Util.human(seconds),
			"me", me)));

		// big title on the screen
		mc.inGameHud.setTitleTicks(10, 70, 20);
		mc.inGameHud.setSubtitle(Text.literal(count + "x " + stack.getName().getString() + "  |  min " + Util.money(minBid)));
		mc.inGameHud.setTitle(Text.literal("Auction started: " + name));
		return null;
	}

	public static void cancel(MinecraftClient mc) {
		if (active == null) return;
		Auction a = active;
		active = null;
		say(mc, Util.fill(AuctionConfig.get().cancelMessage, Map.of("name", a.name)));
	}

	/** Called every client tick. */
	public static void tick(MinecraftClient mc) {
		if (mc.player == null || mc.getNetworkHandler() == null) {
			// left the server: drop everything
			active = null;
			result = null;
			outbox.clear();
			return;
		}

		if (cooldown > 0) cooldown--;
		if (cooldown <= 0 && !outbox.isEmpty()) {
			String msg = outbox.poll();
			mc.getNetworkHandler().sendChatMessage(msg);
			cooldown = Math.max(5, AuctionConfig.get().chatDelayTicks);
		}

		if (active != null) {
			Auction a = active;
			long left = a.secondsLeft();
			for (int s : AuctionConfig.get().countdownAnnounceSeconds) {
				if (s < a.durationSeconds && left <= s && a.announced.add(s)) {
					say(mc, Util.fill(AuctionConfig.get().countdownMessage, Map.of(
						"name", a.name,
						"seconds", String.valueOf(s),
						"top", a.topLine())));
					break;
				}
			}
			if (System.currentTimeMillis() >= a.endMillis) finish(mc);
		} else if (result != null && System.currentTimeMillis() > result.untilMillis) {
			result = null;
		}
	}

	private static void finish(MinecraftClient mc) {
		Auction a = active;
		active = null;
		long until = System.currentTimeMillis() + AuctionConfig.get().resultDisplaySeconds * 1000L;
		result = new Result(a.name, a.stack, a.count, a.leader, a.leaderAmount, until);

		String item = a.stack.getName().getString();
		mc.inGameHud.setTitleTicks(10, 80, 20);
		if (a.leader != null) {
			say(mc, Util.fill(AuctionConfig.get().winnerMessage, Map.of(
				"name", a.name,
				"player", a.leader,
				"count", String.valueOf(a.count),
				"item", item,
				"amount", Util.money(a.leaderAmount))));
			mc.inGameHud.setSubtitle(Text.literal(a.leader + " wins " + a.count + "x " + item + " for " + Util.money(a.leaderAmount)));
			mc.inGameHud.setTitle(Text.literal("Auction ended: " + a.name));
			localMessage(mc, "[Auction] Winner: " + a.leader + " (" + Util.money(a.leaderAmount) + "). Give them " + a.count + "x " + item + ".");
		} else {
			say(mc, Util.fill(AuctionConfig.get().noBidsMessage, Map.of("name", a.name)));
			mc.inGameHud.setSubtitle(Text.literal("No bids"));
			mc.inGameHud.setTitle(Text.literal("Auction ended: " + a.name));
		}
	}

	/** Called for every system/game chat line. */
	public static void onGameMessage(MinecraftClient mc, String line) {
		Auction a = active;
		if (a == null || line == null) return;
		String text = line.trim();
		if (text.startsWith("[AUCTION]")) return; // our own announcements

		for (Pattern p : AuctionConfig.get().patterns()) {
			Matcher m = p.matcher(text);
			if (!m.find()) continue;
			String player = m.group(1);
			double amount = Util.parseMoney(m.group(2));
			if (amount <= 0) continue;
			onPayment(mc, a, player, amount);
			return;
		}
		if (AuctionConfig.get().debug) {
			localMessage(mc, "[Auction debug] not a payment: " + text);
		}
	}

	private static void onPayment(MinecraftClient mc, Auction a, String player, double amount) {
		if (player.equalsIgnoreCase(a.host)) return;
		double total = a.totals.merge(player, amount, Double::sum);
		localMessage(mc, "[Auction] " + player + " paid " + Util.money(amount) + " (total " + Util.money(total) + ")");

		if (total < a.minBid) return;
		if (a.leader == null || total > a.leaderAmount) {
			a.leader = player;
			a.leaderAmount = total;
			if (AuctionConfig.get().announceNewTopBid) {
				say(mc, Util.fill(AuctionConfig.get().newBidMessage, Map.of(
					"name", a.name,
					"player", player,
					"amount", Util.money(total))));
			}
		}
	}

	private static void say(MinecraftClient mc, String message) {
		String msg = message.replace('\n', ' ').trim();
		while (msg.startsWith("/")) msg = msg.substring(1);
		if (msg.length() > 250) msg = msg.substring(0, 250);
		if (msg.isEmpty()) return;
		if (AuctionConfig.get().publicChat) {
			outbox.add(msg);
		} else {
			localMessage(mc, msg);
		}
	}

	private static void localMessage(MinecraftClient mc, String message) {
		if (mc.player != null) mc.player.sendMessage(Text.literal(message), false);
	}
}
