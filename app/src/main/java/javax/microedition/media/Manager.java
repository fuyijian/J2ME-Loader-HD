/*
 * Copyright 2012 Kulikov Dmitriy
 * Copyright 2017-2020 Nikita Shakarun
 * Copyright 2021-2023 Yury Kharchenko
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package javax.microedition.media;

import android.Manifest;
import android.webkit.MimeTypeMap;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.microedition.io.Connector;
import javax.microedition.media.protocol.DataSource;
import javax.microedition.media.protocol.SourceStream;
import javax.microedition.media.tone.ToneManager;
import javax.microedition.util.ContextHolder;

import ru.woesss.j2me.mmapi.Plugin;
import ru.woesss.j2me.mmapi.synth.SynthPluginFactory;

public class Manager {
	public static final String TONE_DEVICE_LOCATOR = "device://tone";
	public static final String MIDI_DEVICE_LOCATOR = "device://midi";

	private static final String RESOURCE_LOCATOR = "resource://";
	private static final String FILE_LOCATOR = "file://";
	private static final String CAPTURE_AUDIO_LOCATOR = "capture://audio";
	private static final String CAPTURE_VIDEO_LOCATOR = "capture://video";
	private static final String CAPTURE_IMAGE_LOCATOR = "capture://image";
	private static final TimeBase DEFAULT_TIMEBASE = () -> System.nanoTime() / 1000L;
	private static final List<Plugin> PLUGINS = new ArrayList<>();

	/** 所有创建过的播放器（弱引用，关闭后自动回收） */
	private static final java.util.WeakHashMap<Player, Boolean> TRACKED = new java.util.WeakHashMap<>();
	/** 因切后台而被我们暂停的播放器，回前台时恢复 */
	private static final java.util.ArrayList<Player> PAUSED_BY_US = new java.util.ArrayList<>();

	private static Player track(Player player) {
		if (player != null) {
			synchronized (TRACKED) {
				TRACKED.put(player, Boolean.TRUE);
			}
		}
		return player;
	}

	/**
	 * 应用切到后台/回前台时统一处理音频。
	 * <p>
	 * 背景：有些游戏没有按 MIDP 规范在 pauseApp() 里停音乐，切后台还在响。
	 * 这里由模拟器直接把「正在播放」的播放器暂停掉，不依赖游戏自觉；
	 * 回前台时再把被我们暂停的那些恢复（游戏自己停过的不会被误启动）。
	 */
	/** 应用是否处于后台：后台期间所有播放器「保持状态但不发声」，防止游戏自己把音乐重新拉起来 */
	private static volatile boolean inBackground;

	public static boolean isBackground() {
		return inBackground;
	}

	/**
	 * 游戏最后一次设定的音量（0~100）与静音状态。
	 * <p>
	 * 有些游戏把"音量调到 0"只作用在当时那个播放器上；回前台时游戏重建播放器，
	 * 新播放器默认 100 → 音乐又响。新建播放器继承这里的值即可避免。
	 */
	private static volatile int lastLevel = 100;
	private static volatile boolean lastMuted;

	public static int getLastLevel() {
		return lastLevel;
	}

	/**
	 * 新建播放器时应当采用的初始音量。
	 * <p>
	 * 只继承**静音**（游戏把音量设成 0 或 mute）——这时新播放器若默认 100 会立刻出声；
	 * 其它情况一律用默认 100，避免把"某个音效的 30%"继承给音乐，造成音量错乱。
	 */
	public static int getInheritedLevel() {
		return (lastMuted || lastLevel <= 0) ? 0 : 100;
	}

	public static boolean isInheritedMute() {
		return lastMuted || lastLevel <= 0;
	}

	public static void setLastLevel(int level) {
		lastLevel = level;
	}

	public static boolean isLastMuted() {
		return lastMuted;
	}

	public static void setLastMuted(boolean muted) {
		lastMuted = muted;
	}

	/** 每个游戏启动时调用，避免上一款游戏的音量被继承过来 */
	public static void resetVolumeMemory() {
		lastLevel = 100;
		lastMuted = false;
	}

	/** 游戏线程在帧边界等待"回到前台"，避免后台空转耗电 */
	private static final Object BG_LOCK = new Object();

	/**
	 * 后台时阻塞调用线程，直到回到前台。
	 * <p>
	 * 在 Canvas.serviceRepaints / GameCanvas.flushGraphics 这类**帧边界**调用：
	 * 此时游戏没有持有绘制相关的锁，挂住最安全，回前台后从下一帧继续。
	 */
	public static void awaitForeground() {
		if (!inBackground) {
			return;
		}
		synchronized (BG_LOCK) {
			while (inBackground) {
				try {
					BG_LOCK.wait(500);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					return;
				}
			}
		}
	}

	public static void setBackground(boolean background) {
		inBackground = background;
		if (!background) {
			synchronized (BG_LOCK) {
				BG_LOCK.notifyAll();
			}
		}
		synchronized (TRACKED) {
			for (Player player : new java.util.ArrayList<>(TRACKED.keySet())) {
				try {
					if (player instanceof BackgroundAware) {
						((BackgroundAware) player).onBackgroundChanged(background);
					}
				} catch (Throwable ignored) {
				}
			}
		}
	}

	public static Player createPlayer(String locator) throws IOException, MediaException {
		if (locator == null) {
			throw new IllegalArgumentException();
		}
		if (MIDI_DEVICE_LOCATOR.equals(locator) || TONE_DEVICE_LOCATOR.equals(locator)) {
			for (Plugin plugin : PLUGINS) {
				Player player = plugin.createPlayer(locator);
				if (player != null) {
					return track(player);
				}
			}
			return track(new MicroPlayer(locator));
		} else if (locator.startsWith(FILE_LOCATOR) || locator.startsWith(RESOURCE_LOCATOR)) {
			InputStream stream = Connector.openInputStream(locator);
			String extension = locator.substring(locator.lastIndexOf('.') + 1);
			String type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
			return createPlayer(stream, type);
		} else if (locator.startsWith(CAPTURE_AUDIO_LOCATOR) &&
				ContextHolder.requestPermission(Manifest.permission.RECORD_AUDIO)) {
			return track(new RecordPlayer());
		} else if ((locator.startsWith(CAPTURE_IMAGE_LOCATOR) || locator.startsWith(CAPTURE_VIDEO_LOCATOR)) &&
				ContextHolder.requestPermission(Manifest.permission.CAMERA)) {
			return track(new CameraPlayer());
		} else {
			return track(new BasePlayer());
		}
	}

	public static Player createPlayer(DataSource source) throws IOException, MediaException {
		if (source == null) {
			throw new IllegalArgumentException();
		}
		String type = source.getContentType();
		String[] supportedTypes = getSupportedContentTypes(null);
		if (type != null && Arrays.asList(supportedTypes).contains(type.toLowerCase())) {
			source.connect();
			SourceStream[] sourceStreams = source.getStreams();
			if (sourceStreams == null || sourceStreams.length == 0) {
				throw new MediaException();
			}
			SourceStream sourceStream = sourceStreams[0];
			InputStream stream = new InternalSourceStream(sourceStream);
			InternalDataSource datasource = new InternalDataSource(stream, type);
			return track(new MicroPlayer(datasource));
		} else {
			return track(new BasePlayer());
		}
	}

	public static Player createPlayer(final InputStream stream, String type)
			throws IOException, MediaException {
		if (stream == null) {
			throw new IllegalArgumentException();
		}
		InternalDataSource datasource = new InternalDataSource(stream, type);
		for (Plugin plugin : PLUGINS) {
			Player player = plugin.createPlayer(datasource);
			if (player != null) {
				return track(player);
			}
		}
		String[] supportedTypes = getSupportedContentTypes(null);
		if (type != null && Arrays.asList(supportedTypes).contains(type.toLowerCase())) {
			return track(new MicroPlayer(datasource));
		} else {
			return track(new BasePlayer());
		}
	}

	public static String[] getSupportedContentTypes(String str) {
		return new String[]{"audio/wav", "audio/x-wav", "audio/midi", "audio/x-midi",
				"audio/mpeg", "audio/aac", "audio/amr", "audio/amr-wb", "audio/mp3",
				"audio/mp4", "audio/mmf", "audio/x-tone-seq"};
	}

	public static String[] getSupportedProtocols(String str) {
		return new String[]{"device", "file", "http", "resource"};
	}

	public static TimeBase getSystemTimeBase() {
		return DEFAULT_TIMEBASE;
	}

	public synchronized static void playTone(int note, int duration, int volume)
			throws MediaException {
		ToneManager.getInstance().playTone(note, duration, volume);
	}

	static {
		SynthPluginFactory.loadPlugins(PLUGINS);
	}
}
