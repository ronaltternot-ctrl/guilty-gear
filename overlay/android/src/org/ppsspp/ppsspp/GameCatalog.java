package org.ppsspp.ppsspp;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Handler;
import android.util.Log;
import android.util.LruCache;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Pictures for the game catalog: the icon each PSP game carries (PSP_GAME/ICON0.PNG, 144 x 80),
 * kept in files/catalog/, and a drawn cover for games without one.
 */
final class GameCatalog {
	private GameCatalog() {}

	private static final String TAG = "GameCatalog";

	/** How many games the catalog keeps (each one holds a file permission). */
	static final int MAX_GAMES = 40;

	/** Badge of each kind of game in the catalog. */
	static final int BADGE_GG = 0, BADGE_BB = 1, BADGE_KOF = 2, BADGE_OTHER = 3, BADGE_SF = 4;

	static final String[] BADGE_NAMES = { "GUILTY GEAR", "BLAZBLUE", "KOF", "OTRO JUEGO", "STREET FIGHTER" };
	static final int[] BADGE_COLORS = { 0xFFD7263D, 0xFF2F6BFF, 0xFFF08A24, 0xFF6C6C78, 0xFFE8B21E };

	/** Order of the filters in the catalog ("other" last). */
	static final int[] BADGE_ORDER = { BADGE_GG, BADGE_BB, BADGE_KOF, BADGE_SF, BADGE_OTHER };

	static int badge(String discId, String title) {
		switch (new GameInspector.Result(null, discId, title).match()) {
			case TARGET:
			case OTHER_GUILTY_GEAR:
				return BADGE_GG;
			case BLAZBLUE:
				return BADGE_BB;
			case KOF:
				return BADGE_KOF;
			case STREET_FIGHTER:
				return BADGE_SF;
			default:
				return BADGE_OTHER;
		}
	}

	interface IconListener {
		/** On the main thread; icon is null when the game has none (draw the cover instead). */
		void onIcon(String uri, Bitmap icon);
	}

	private static final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "GameCatalog");
		t.setPriority(Thread.MIN_PRIORITY);
		return t;
	});

	private static final LruCache<String, Bitmap> cache = new LruCache<>(MAX_GAMES + 8);

	static File iconFile(Context context, String uri) {
		return new File(new File(context.getFilesDir(), "catalog"), hash(uri) + ".png");
	}

	private static File noIconFile(Context context, String uri) {
		return new File(new File(context.getFilesDir(), "catalog"), hash(uri) + ".none");
	}

	/** After an inspection that saved (or did not find) the icon. */
	static void inspected(Context context, String uri) {
		cache.remove(uri);
		File icon = iconFile(context, uri);
		File none = noIconFile(context, uri);
		if (icon.isFile()) {
			if (none.isFile() && !none.delete()) {
				Log.w(TAG, "Could not delete " + none);
			}
		} else {
			markNoIcon(none);
		}
	}

	static void forget(Context context, String uri) {
		cache.remove(uri);
		File icon = iconFile(context, uri);
		File none = noIconFile(context, uri);
		if ((icon.isFile() && !icon.delete()) || (none.isFile() && !none.delete())) {
			Log.w(TAG, "Could not delete the icon of " + uri);
		}
	}

	static Bitmap cached(String uri) {
		return cache.get(uri);
	}

	/**
	 * Loads a game's icon in the background. Games added before the catalog existed have no
	 * saved icon yet: it is read from the ISO / CSO once (a few small reads).
	 */
	static void load(Context context, final String uri, final Handler handler, final IconListener listener) {
		final Context app = context.getApplicationContext();
		Bitmap hit = cache.get(uri);
		if (hit != null) {
			listener.onIcon(uri, hit);
			return;
		}
		worker.execute(() -> {
			Bitmap bmp = null;
			try {
				File icon = iconFile(app, uri);
				File none = noIconFile(app, uri);
				if (!icon.isFile() && !none.isFile()) {
					try {
						GameInspector.inspect(app, Uri.parse(uri), icon);
						if (!icon.isFile()) {
							markNoIcon(none);
						}
					} catch (IOException | SecurityException | IllegalArgumentException e) {
						// No access right now (moved file, SD card out): try again next time.
						Log.w(TAG, "No icon for " + uri + ": " + e);
					}
				}
				if (icon.isFile()) {
					bmp = BitmapFactory.decodeFile(icon.getPath());
				}
			} catch (Throwable t) {
				Log.w(TAG, "Icon failed for " + uri, t);
			}
			if (bmp != null) {
				cache.put(uri, bmp);
			}
			final Bitmap result = bmp;
			handler.post(() -> listener.onIcon(uri, result));
		});
	}

	private static void markNoIcon(File none) {
		try {
			File dir = none.getParentFile();
			if (dir != null && !dir.isDirectory() && !dir.mkdirs()) {
				return;
			}
			if (!none.isFile() && !none.createNewFile()) {
				Log.w(TAG, "Could not create " + none);
			}
		} catch (IOException e) {
			Log.w(TAG, "Could not create " + none, e);
		}
	}

	private static final LruCache<String, Bitmap> covers = new LruCache<String, Bitmap>(12 * 1024 * 1024) {
		@Override
		protected int sizeOf(String key, Bitmap value) {
			return value.getByteCount();
		}
	};

	/** A cover in the icon's 144:80 shape for games without an icon (or until it loads). */
	static Bitmap cover(int badge, String title, int widthPx) {
		String key = badge + "|" + widthPx + "|" + (badge == BADGE_OTHER ? initials(title) : "");
		Bitmap hit = covers.get(key);
		if (hit == null) {
			hit = drawCover(badge, title, widthPx);
			covers.put(key, hit);
		}
		return hit;
	}

	private static Bitmap drawCover(int badge, String title, int widthPx) {
		int w = Math.max(144, widthPx);
		int h = Math.round(w * 80f / 144f);
		Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
		Canvas c = new Canvas(bmp);
		int base = BADGE_COLORS[Math.max(0, Math.min(BADGE_COLORS.length - 1, badge))];
		Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
		bg.setShader(new LinearGradient(0, 0, w, h, darken(base, 0.55f), 0xFF0D0D11, Shader.TileMode.CLAMP));
		c.drawRect(0, 0, w, h, bg);

		// Diagonal stripes, like a cabinet's side art.
		Paint stripe = new Paint(Paint.ANTI_ALIAS_FLAG);
		stripe.setColor((base & 0x00FFFFFF) | 0x30000000);
		stripe.setStrokeWidth(h * 0.10f);
		for (float x = -h; x < w; x += h * 0.36f) {
			c.drawLine(x, h, x + h, 0, stripe);
		}

		String big;
		switch (badge) {
			case BADGE_GG: big = "GG"; break;
			case BADGE_BB: big = "BB"; break;
			case BADGE_KOF: big = "KOF"; break;
			case BADGE_SF: big = "SF"; break;
			default: big = initials(title); break;
		}
		Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
		text.setColor(Color.WHITE);
		text.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD_ITALIC));
		text.setTextAlign(Paint.Align.CENTER);
		text.setTextSize(h * 0.50f);
		text.setShadowLayer(h * 0.04f, 0, h * 0.02f, 0xC0000000);
		c.drawText(big, w / 2f, h * 0.58f, text);

		Paint small = new Paint(Paint.ANTI_ALIAS_FLAG);
		small.setColor(0xD0FFFFFF);
		small.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
		small.setTextAlign(Paint.Align.CENTER);
		small.setTextSize(h * 0.12f);
		small.setLetterSpacing(0.2f);
		c.drawText(BADGE_NAMES[Math.max(0, Math.min(BADGE_NAMES.length - 1, badge))], w / 2f, h * 0.84f, small);

		Paint frame = new Paint(Paint.ANTI_ALIAS_FLAG);
		frame.setStyle(Paint.Style.STROKE);
		frame.setStrokeWidth(Math.max(2f, h * 0.02f));
		frame.setColor((base & 0x00FFFFFF) | 0x90000000);
		float in = frame.getStrokeWidth() / 2f;
		c.drawRect(new RectF(in, in, w - in, h - in), frame);
		return bmp;
	}

	private static String initials(String title) {
		if (title == null || title.trim().isEmpty()) {
			return "PSP";
		}
		StringBuilder out = new StringBuilder();
		for (String word : title.trim().split("[\\s:\\-]+")) {
			if (!word.isEmpty() && Character.isLetterOrDigit(word.charAt(0))) {
				out.append(Character.toUpperCase(word.charAt(0)));
				if (out.length() == 3) {
					break;
				}
			}
		}
		return out.length() == 0 ? "PSP" : out.toString().toUpperCase(Locale.ROOT);
	}

	private static int darken(int color, float f) {
		int r = Math.round(Color.red(color) * f);
		int g = Math.round(Color.green(color) * f);
		int b = Math.round(Color.blue(color) * f);
		return Color.rgb(r, g, b);
	}

	private static String hash(String s) {
		try {
			byte[] d = MessageDigest.getInstance("SHA-1").digest(s.getBytes(StandardCharsets.UTF_8));
			StringBuilder out = new StringBuilder();
			for (int i = 0; i < 10; i++) {
				out.append(String.format(Locale.ROOT, "%02x", d[i] & 0xFF));
			}
			return out.toString();
		} catch (NoSuchAlgorithmException e) {
			return Integer.toHexString(s.hashCode());
		}
	}
}
