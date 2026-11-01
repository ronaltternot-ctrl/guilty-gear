package org.ppsspp.ppsspp;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Random;
import java.util.Set;

/**
 * The player's own music ("TU MÚSICA" in the launcher), in place of the game's.
 *
 * The launcher copies the songs to files/music/tracks/ and keeps the settings in
 * files/music/config.properties. Each game process (":emu" PSP, ":arcade" arcade and PS1,
 * ":mugen") reads them when the game starts, turns the game's own music off its own way
 * (see {@link #replacesGameMusic}) and plays the songs with this class while the game is on
 * screen: it pauses with the game's menu and when the game goes to the background.
 */
final class MusicPlayer {
	private static final String TAG = "MusicPlayer";

	static final String PSP = "psp", PSX = "psx", ARCADE = "arcade", MUGEN = "mugen", DC = "dc";
	static final String[] SYSTEMS = { PSP, PSX, DC, ARCADE, MUGEN };
	static final String[] SYSTEM_NAMES = { "PSP", "PS1", "DC", "Arcade", "MUGEN" };

	/** What Android's player opens (the picker also accepts any audio/* file). */
	private static final Set<String> AUDIO_EXT = new HashSet<>(Arrays.asList(
		"mp3", "m4a", "aac", "mp4", "3gp", "ogg", "oga", "opus", "flac", "wav", "mid", "midi", "xmf", "imy", "ota", "rtttl", "amr", "mka", "webm"));

	// -------------------------------------------------------------------------------------------
	// Storage (shared by the launcher and the game processes)

	static File dir(Context c) {
		return new File(c.getFilesDir(), "music");
	}

	static File tracksDir(Context c) {
		return new File(dir(c), "tracks");
	}

	private static File configFile(Context c) {
		return new File(dir(c), "config.properties");
	}

	private static File infoFile(Context c) {
		return new File(dir(c), "tracks.properties");
	}

	static final class Settings {
		/** Use the player's music in the games. */
		boolean enabled;
		boolean shuffle = true;
		/** 0–100. */
		int volume = 80;
		/** Also turn off the game's sound effects and voices. */
		boolean muteAll;
		/** Systems where the game keeps its own music. */
		final Set<String> off = new HashSet<>();

		boolean usesIn(String system) {
			return enabled && !off.contains(system);
		}
	}

	static Settings load(Context c) {
		Properties p = readProps(configFile(c));
		Settings s = new Settings();
		s.enabled = Boolean.parseBoolean(p.getProperty("enabled", "false"));
		s.shuffle = Boolean.parseBoolean(p.getProperty("shuffle", "true"));
		s.muteAll = Boolean.parseBoolean(p.getProperty("muteAll", "false"));
		try {
			s.volume = Math.max(0, Math.min(100, Integer.parseInt(p.getProperty("volume", "80").trim())));
		} catch (NumberFormatException e) {
			s.volume = 80;
		}
		for (String sys : p.getProperty("off", "").split(",")) {
			if (!sys.trim().isEmpty()) {
				s.off.add(sys.trim());
			}
		}
		return s;
	}

	static void save(Context c, Settings s) {
		Properties p = new Properties();
		p.setProperty("enabled", String.valueOf(s.enabled));
		p.setProperty("shuffle", String.valueOf(s.shuffle));
		p.setProperty("muteAll", String.valueOf(s.muteAll));
		p.setProperty("volume", String.valueOf(s.volume));
		StringBuilder off = new StringBuilder();
		for (String sys : SYSTEMS) {
			if (s.off.contains(sys)) {
				off.append(off.length() > 0 ? "," : "").append(sys);
			}
		}
		p.setProperty("off", off.toString());
		writeProps(configFile(c), p);
	}

	/** The songs, in the order they were added. */
	static List<File> tracks(Context c) {
		File[] files = tracksDir(c).listFiles();
		List<File> out = new ArrayList<>();
		if (files == null) {
			return out;
		}
		for (File f : files) {
			if (f.isFile() && !f.getName().endsWith(".part") && !f.getName().startsWith(".")) {
				out.add(f);
			}
		}
		Collections.sort(out, (a, b) -> {
			int byTime = Long.compare(a.lastModified(), b.lastModified());
			return byTime != 0 ? byTime : a.getName().compareToIgnoreCase(b.getName());
		});
		return out;
	}

	/**
	 * True when this system's game plays the player's songs instead of its music. Each game turns
	 * its own music off where it can: PSP the compressed music tracks (ATRAC3, MP3, AAC: patch
	 * 0009), PS1 the CD and XA audio (PCSX ReARMed's options), MUGEN the BGM volume (Ikemen GO's
	 * config). The Neo Geo and other arcade boards make music and effects on the same sound chip,
	 * so there all the game's sound goes off (see {@link #mutesAllSound}).
	 */
	static boolean replacesGameMusic(Context c, String system) {
		return load(c).usesIn(system) && !tracks(c).isEmpty();
	}

	/** True when the game must be silent: the player asked for it, or the system can't do less. */
	static boolean mutesAllSound(Context c, String system) {
		Settings s = load(c);
		// Arcade boards and the Dreamcast (its music is streamed and mixed by the AICA chip, with no
		// way to tell it apart) can only go silent.
		return s.usesIn(system) && !tracks(c).isEmpty() && (s.muteAll || ARCADE.equals(system) || DC.equals(system));
	}

	/** For the PSP emulator (acplus.ini [Music] MuteGameMusic): 0 off, 1 the music, 2 all sound. */
	static int pspMuteMode(Context c) {
		if (!replacesGameMusic(c, PSP)) {
			return 0;
		}
		return load(c).muteAll ? 2 : 1;
	}

	static boolean looksLikeAudio(String name, String mime) {
		if (mime != null && mime.startsWith("audio/")) {
			return true;
		}
		String n = name == null ? "" : name.toLowerCase(Locale.ROOT);
		int dot = n.lastIndexOf('.');
		return dot >= 0 && AUDIO_EXT.contains(n.substring(dot + 1));
	}

	/** A file name for the copy: no folders, no control characters, not too long. */
	static String cleanName(String name) {
		String n = name == null ? "" : name.replace('\\', '/');
		n = n.substring(n.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}:*?\"<>|]", "_").trim();
		while (n.startsWith(".")) {
			n = n.substring(1);
		}
		if (n.length() > 120) {
			int dot = n.lastIndexOf('.');
			String ext = dot > 0 && n.length() - dot <= 6 ? n.substring(dot) : "";
			n = n.substring(0, 120 - ext.length()) + ext;
		}
		return n.isEmpty() ? "cancion" : n;
	}

	/** "name.mp3", or "name (2).mp3" when that one is taken. */
	static File unique(File dir, String name) {
		File f = new File(dir, name);
		if (!f.exists()) {
			return f;
		}
		int dot = name.lastIndexOf('.');
		String base = dot > 0 ? name.substring(0, dot) : name;
		String ext = dot > 0 ? name.substring(dot) : "";
		for (int i = 2; ; i++) {
			f = new File(dir, base + " (" + i + ")" + ext);
			if (!f.exists()) {
				return f;
			}
		}
	}

	/** Copies one song picked by the player and checks that Android can play it. Off the UI thread. */
	static File addTrack(Context c, Uri uri) throws IOException {
		FileOps.Picked p = FileOps.describe(c, uri);
		String mime = null;
		try {
			mime = c.getContentResolver().getType(uri);
		} catch (RuntimeException ignored) {
			// Go by the name.
		}
		if (!looksLikeAudio(p.name, mime)) {
			throw new IOException("no es un archivo de música (MP3, M4A, OGG, FLAC, WAV…)");
		}
		File dir = tracksDir(c);
		if (!dir.isDirectory() && !dir.mkdirs()) {
			throw new IOException("No se pudo crear la carpeta de música");
		}
		FileOps.requireSpace(dir, p.size);
		File dest = unique(dir, cleanName(p.name));
		FileOps.copyUri(c, uri, dest, null);
		long ms = probe(dest);
		if (ms < 0) {
			//noinspection ResultOfMethodCallIgnored
			dest.delete();
			throw new IOException("Android no puede reproducir este archivo (formato no compatible o dañado)");
		}
		synchronized (MusicPlayer.class) {
			Properties info = readProps(infoFile(c));
			info.setProperty(dest.getName(), String.valueOf(ms));
			writeProps(infoFile(c), info);
		}
		return dest;
	}

	static void removeTrack(Context c, File f) {
		//noinspection ResultOfMethodCallIgnored
		f.delete();
		synchronized (MusicPlayer.class) {
			Properties info = readProps(infoFile(c));
			if (info.remove(f.getName()) != null) {
				writeProps(infoFile(c), info);
			}
		}
	}

	/** The length of each song (ms), by file name, as measured when it was added. */
	static Properties durations(Context c) {
		return readProps(infoFile(c));
	}

	static String title(File f) {
		String n = f.getName();
		int dot = n.lastIndexOf('.');
		if (dot > 0) {
			n = n.substring(0, dot);
		}
		return n.replace('_', ' ').trim();
	}

	static String formatDuration(long ms) {
		if (ms <= 0) {
			return "";
		}
		long s = (ms + 500) / 1000;
		return s >= 3600 ? String.format(Locale.ROOT, "%d:%02d:%02d", s / 3600, (s / 60) % 60, s % 60)
			: String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60);
	}

	/** The song's length in ms, or -1 when Android's player can't open it. */
	private static long probe(File f) {
		MediaPlayer mp = new MediaPlayer();
		try {
			mp.setDataSource(f.getPath());
			mp.prepare();
			return Math.max(0, mp.getDuration());
		} catch (IOException | RuntimeException e) {
			Log.w(TAG, "Can't play " + f.getName(), e);
			return -1;
		} finally {
			mp.release();
		}
	}

	private static Properties readProps(File f) {
		Properties p = new Properties();
		if (f.isFile()) {
			try (InputStream in = new FileInputStream(f)) {
				p.load(in);
			} catch (IOException | IllegalArgumentException e) {
				Log.w(TAG, "Read " + f, e);
			}
		}
		return p;
	}

	private static void writeProps(File f, Properties p) {
		File parent = f.getParentFile();
		if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
			Log.w(TAG, "Could not create " + parent);
			return;
		}
		File tmp = new File(f.getPath() + ".tmp");
		try (OutputStream out = new FileOutputStream(tmp)) {
			p.store(out, null);
		} catch (IOException e) {
			Log.w(TAG, "Write " + f, e);
			//noinspection ResultOfMethodCallIgnored
			tmp.delete();
			return;
		}
		try {
			FileOps.replace(tmp, f);
		} catch (IOException e) {
			Log.w(TAG, "Write " + f, e);
		}
	}

	// -------------------------------------------------------------------------------------------
	// Playback, in a game process

	/** The player for this game, or null when the game keeps its own music. */
	static MusicPlayer create(Context c, String system) {
		Settings s = load(c);
		if (!s.usesIn(system)) {
			return null;
		}
		List<File> list = tracks(c);
		if (list.isEmpty()) {
			return null;
		}
		return new MusicPlayer(c.getApplicationContext(), list, s);
	}

	private final Handler handler = new Handler(Looper.getMainLooper());
	private final Context app;
	private final List<File> playlist;
	private final float gain;
	private MediaPlayer player;
	private boolean prepared;
	private int index;
	private int failures;
	private boolean foreground, running, released;
	private float fade = 1f;

	private MusicPlayer(Context app, List<File> tracks, Settings s) {
		this.app = app;
		this.playlist = new ArrayList<>(tracks);
		// The slider is closer to what the ear hears with a squared curve.
		float v = s.volume / 100f;
		this.gain = v * v;
		String last = readProps(stateFile()).getProperty("last", "");
		if (s.shuffle && playlist.size() > 1) {
			Collections.shuffle(playlist, new Random());
			// Don't start with the song the last game ended on.
			if (playlist.get(0).getName().equals(last)) {
				Collections.swap(playlist, 0, 1 + new Random().nextInt(playlist.size() - 1));
			}
		} else {
			// In order: carry on with the song after the last one played.
			for (int i = 0; i < playlist.size(); i++) {
				if (playlist.get(i).getName().equals(last)) {
					index = (i + 1) % playlist.size();
					break;
				}
			}
		}
	}

	private File stateFile() {
		return new File(dir(app), "state.properties");
	}

	/** The game's screen is in front (onResume) or not (onPause). From any thread. */
	void setForeground(boolean on) {
		post(() -> {
			foreground = on;
			update();
		});
	}

	/** The game is running (false while its menu is open). From any thread. */
	void setRunning(boolean on) {
		post(() -> {
			running = on;
			update();
		});
	}

	void release() {
		post(() -> {
			released = true;
			handler.removeCallbacksAndMessages(null);
			releasePlayer();
		});
	}

	private void post(Runnable r) {
		if (Looper.myLooper() == Looper.getMainLooper()) {
			r.run();
		} else {
			handler.post(r);
		}
	}

	private boolean wanted() {
		return !released && foreground && running;
	}

	private void update() {
		if (released) {
			return;
		}
		if (player == null) {
			if (wanted()) {
				startTrack();
			}
			return;
		}
		if (!prepared) {
			return;   // onPrepared comes back here
		}
		try {
			if (wanted() && !player.isPlaying()) {
				fadeIn();
				player.start();
			} else if (!wanted() && player.isPlaying()) {
				handler.removeCallbacks(fadeStep);
				player.pause();
			}
		} catch (IllegalStateException e) {
			Log.w(TAG, "Player state", e);
			releasePlayer();
		}
	}

	private void startTrack() {
		releasePlayer();
		if (released || playlist.isEmpty() || failures >= playlist.size()) {
			if (failures >= playlist.size()) {
				Log.w(TAG, "None of the songs plays");
			}
			return;
		}
		File f = playlist.get(index);
		final MediaPlayer mp = new MediaPlayer();
		player = mp;
		prepared = false;
		mp.setAudioAttributes(new AudioAttributes.Builder()
			.setUsage(AudioAttributes.USAGE_GAME)
			.setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
			.build());
		mp.setLooping(playlist.size() == 1);
		mp.setOnPreparedListener(p -> {
			if (p != player) {
				return;
			}
			prepared = true;
			failures = 0;
			fade = 0f;
			applyVolume();
			remember(f);
			update();
		});
		mp.setOnCompletionListener(p -> {
			if (p == player) {
				next();
			}
		});
		mp.setOnErrorListener((p, what, extra) -> {
			if (p == player) {
				Log.w(TAG, "Error " + what + "/" + extra + " playing " + f.getName());
				failures++;
				handler.post(this::next);
			}
			return true;
		});
		try {
			mp.setDataSource(f.getPath());
			mp.prepareAsync();
		} catch (IOException | RuntimeException e) {
			Log.w(TAG, "Can't open " + f.getName(), e);
			failures++;
			handler.post(this::next);
		}
	}

	private void next() {
		if (released || playlist.isEmpty()) {
			return;
		}
		index = (index + 1) % playlist.size();
		releasePlayer();
		if (wanted()) {
			startTrack();
		}
	}

	private void releasePlayer() {
		handler.removeCallbacks(fadeStep);
		if (player != null) {
			try {
				player.release();
			} catch (RuntimeException ignored) {
				// Already gone.
			}
			player = null;
			prepared = false;
		}
	}

	private void remember(File f) {
		Properties p = new Properties();
		p.setProperty("last", f.getName());
		writeProps(stateFile(), p);
	}

	private void fadeIn() {
		handler.removeCallbacks(fadeStep);
		fade = Math.min(fade, 0.05f);
		applyVolume();
		handler.postDelayed(fadeStep, 40);
	}

	private final Runnable fadeStep = new Runnable() {
		@Override
		public void run() {
			fade = Math.min(1f, fade + 0.08f);
			applyVolume();
			if (fade < 1f) {
				handler.postDelayed(this, 40);
			}
		}
	};

	private void applyVolume() {
		if (player != null) {
			try {
				float v = gain * fade;
				player.setVolume(v, v);
			} catch (IllegalStateException ignored) {
				// Released meanwhile.
			}
		}
	}
}
