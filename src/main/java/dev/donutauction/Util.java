package dev.donutauction;

import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Util {
	private static final Pattern DURATION_PART = Pattern.compile("(\\d+)([hms]?)");
	private static final Pattern MONEY = Pattern.compile("^(\\d+(?:\\.\\d+)?)([kmbt]?)$");

	private Util() {
	}

	/** "5m", "90s", "1h30m", "2h". A bare number counts as minutes. Returns -1 if invalid. */
	public static long parseDurationSeconds(String input) {
		if (input == null) return -1;
		String s = input.trim().toLowerCase(Locale.ROOT).replace(" ", "");
		if (s.isEmpty() || !s.matches("(\\d+[hms]?)+")) return -1;
		try {
			Matcher m = DURATION_PART.matcher(s);
			long total = 0;
			while (m.find()) {
				long n = Long.parseLong(m.group(1));
				switch (m.group(2)) {
					case "h" -> total += n * 3600;
					case "s" -> total += n;
					default -> total += n * 60;
				}
			}
			return total;
		} catch (NumberFormatException e) {
			return -1;
		}
	}

	/** "1000", "1,000", "$2.5k", "3m", "1b", "2t". Returns -1 if invalid. */
	public static double parseMoney(String input) {
		if (input == null) return -1;
		String s = input.trim().toLowerCase(Locale.ROOT).replace("$", "").replace(",", "").replace(" ", "");
		Matcher m = MONEY.matcher(s);
		if (!m.matches()) return -1;
		double value = Double.parseDouble(m.group(1));
		return switch (m.group(2)) {
			case "k" -> value * 1_000d;
			case "m" -> value * 1_000_000d;
			case "b" -> value * 1_000_000_000d;
			case "t" -> value * 1_000_000_000_000d;
			default -> value;
		};
	}

	public static String money(double value) {
		String[] suffix = {"", "K", "M", "B", "T"};
		int i = 0;
		double x = value;
		while (Math.abs(x) >= 1000 && i < suffix.length - 1) {
			x /= 1000;
			i++;
		}
		String s;
		if (x == Math.rint(x)) {
			s = String.valueOf((long) x);
		} else {
			s = String.format(Locale.ROOT, "%.2f", x).replaceAll("0+$", "").replaceAll("\\.$", "");
		}
		return "$" + s + suffix[i];
	}

	/** mm:ss or h:mm:ss */
	public static String clock(long seconds) {
		if (seconds < 0) seconds = 0;
		long h = seconds / 3600;
		long m = (seconds % 3600) / 60;
		long s = seconds % 60;
		if (h > 0) return String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s);
		return String.format(Locale.ROOT, "%02d:%02d", m, s);
	}

	/** "1h 30m", "5m", "45s" */
	public static String human(long seconds) {
		long h = seconds / 3600;
		long m = (seconds % 3600) / 60;
		long s = seconds % 60;
		StringBuilder sb = new StringBuilder();
		if (h > 0) sb.append(h).append("h ");
		if (m > 0) sb.append(m).append("m ");
		if (s > 0 || sb.length() == 0) sb.append(s).append("s ");
		return sb.toString().trim();
	}

	public static String fill(String template, Map<String, String> values) {
		String out = template;
		for (Map.Entry<String, String> e : values.entrySet()) {
			out = out.replace("{" + e.getKey() + "}", e.getValue());
		}
		return out;
	}
}
