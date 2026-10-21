package dev.donutauction;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/** Saved to .minecraft/config/donutauction.json */
public class AuctionConfig {
	private static final Logger LOGGER = LoggerFactory.getLogger("donutauction");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static AuctionConfig instance;

	/** true = announcements are sent to public chat, false = only shown to you. */
	public boolean publicChat = true;
	/** Announce in chat every time somebody takes the lead. */
	public boolean announceNewTopBid = true;
	/** Minimum delay between two messages the mod sends (20 ticks = 1 second). Avoids chat spam kicks. */
	public int chatDelayTicks = 50;
	/** Announce in chat when this many seconds are left. */
	public int[] countdownAnnounceSeconds = {30, 10};
	/** How long the result panel stays on screen after the auction ends. */
	public int resultDisplaySeconds = 20;
	/** Prints chat lines that did not look like a payment (only while an auction runs). */
	public boolean debug = false;

	/**
	 * Regex patterns used to read /pay messages. Group 1 = player name, group 2 = amount.
	 * If DonutSMP changes the message, edit this (turn "debug" on to see the raw lines).
	 */
	public List<String> paymentPatterns = new ArrayList<>(List.of(
		"^(?:\\[[^\\]]*\\]\\s*)?(\\w{3,16}) paid you \\$?([\\d,.]+\\s*[kKmMbBtT]?)"
	));

	public String startMessage = "[AUCTION] {name} | {count}x {item} | Min bid {min} | Time {time} | Bid with /pay {me} <amount>";
	public String newBidMessage = "[AUCTION] {name}: top bid is {player} with {amount}!";
	public String countdownMessage = "[AUCTION] {name}: {seconds}s left! Top bid: {top}";
	public String winnerMessage = "[AUCTION] {name} ended! {player} wins {count}x {item} with {amount}!";
	public String noBidsMessage = "[AUCTION] {name} ended with no bids.";
	public String cancelMessage = "[AUCTION] {name} was cancelled.";

	private transient List<Pattern> compiled = new ArrayList<>();

	public static AuctionConfig get() {
		if (instance == null) load();
		return instance;
	}

	public List<Pattern> patterns() {
		return compiled;
	}

	public static void load() {
		Path path = FabricLoader.getInstance().getConfigDir().resolve("donutauction.json");
		AuctionConfig cfg = null;
		if (Files.exists(path)) {
			try (Reader reader = Files.newBufferedReader(path)) {
				cfg = GSON.fromJson(reader, AuctionConfig.class);
			} catch (Exception e) {
				LOGGER.error("Could not read donutauction.json, using defaults", e);
			}
		}
		if (cfg == null) cfg = new AuctionConfig();
		if (cfg.paymentPatterns == null || cfg.paymentPatterns.isEmpty()) {
			cfg.paymentPatterns = new AuctionConfig().paymentPatterns;
		}
		if (cfg.countdownAnnounceSeconds == null) cfg.countdownAnnounceSeconds = new int[0];
		cfg.compiled = new ArrayList<>();
		for (String p : cfg.paymentPatterns) {
			try {
				cfg.compiled.add(Pattern.compile(p));
			} catch (PatternSyntaxException e) {
				LOGGER.error("Invalid payment pattern in config: {}", p);
			}
		}
		instance = cfg;
		cfg.save(path);
	}

	private void save(Path path) {
		try (Writer writer = Files.newBufferedWriter(path)) {
			GSON.toJson(this, writer);
		} catch (IOException e) {
			LOGGER.error("Could not write donutauction.json", e);
		}
	}
}
