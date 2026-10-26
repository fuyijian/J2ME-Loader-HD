/*
 * Copyright 2012 Kulikov Dmitriy
 * Copyright 2017-2020 Nikita Shakarun
 * Copyright 2023 Yury Kharchenko
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

import android.util.Log;

import com.arthenica.ffmpegkit.FFmpegKit;
import com.arthenica.ffmpegkit.FFmpegSession;
import com.arthenica.ffmpegkit.FFprobeKit;
import com.arthenica.ffmpegkit.MediaInformation;
import com.arthenica.ffmpegkit.MediaInformationSession;
import com.arthenica.ffmpegkit.ReturnCode;
import com.arthenica.ffmpegkit.StreamInformation;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import ru.woesss.j2me.mmapi.FileCacheDataSource;

/**
 * 把游戏的音频流落成文件后再交给 MediaPlayer。
 * <p>
 * 上游实现每创建一次 Player 都要：写临时文件 → ffprobe 探测 → 若为 ADPCM（MMF/SMAF 常见）
 * 再调 ffmpeg 转成 WAV。游戏每播一个音效就重复一次这套流程，ffprobe + ffmpeg 的启动开销
 * 合计可达上百毫秒，表现为「音效延迟」。
 * <p>
 * 这里做两件事，都不改变播放语义：
 * 1. 按「类型 + 内容哈希」缓存转换结果，同一段音频重复播放时直接复用文件；
 * 2. 对不可能携带 ADPCM 的类型（mp3/amr/midi/aac 等）跳过 ffprobe。
 */
class InternalDataSource extends FileCacheDataSource {
	private static final String TAG = InternalDataSource.class.getSimpleName();

	/** 超过该大小的流不做内存读取与缓存，退回原来的流式写入 */
	private static final int MAX_CACHED_SIZE = 4 * 1024 * 1024;
	/** 缓存条数上限（每条对应游戏里的一段音频） */
	private static final int MAX_CACHE_ENTRIES = 64;

	/** LRU：key = 类型 + ':' + 内容哈希，value = 已就绪（可能已转码）的文件 */
	private static final Map<String, File> CONVERTED =
			new LinkedHashMap<String, File>(16, 0.75f, true) {
				@Override
				protected boolean removeEldestEntry(Map.Entry<String, File> eldest) {
					return size() > MAX_CACHE_ENTRIES;
				}
			};

	/** true 表示 mediaFile 来自缓存，disconnect() 时不能删 */
	private boolean sharedFile;

	InternalDataSource(InputStream stream, String type) throws IllegalArgumentException, IOException {
		super(type);

		byte[] data = readFully(stream, MAX_CACHED_SIZE);
		if (data != null) {
			String key = (type == null ? "" : type.toLowerCase(Locale.US)) + ':' + digest(data);
			synchronized (CONVERTED) {
				File cached = CONVERTED.get(key);
				if (cached != null && cached.isFile() && cached.length() > 0) {
					File unused = mediaFile;   // 父类构造时建的空临时文件，命中缓存后用不到
					mediaFile = cached;
					sharedFile = true;
					if (!unused.delete()) {
						unused.deleteOnExit();
					}
					Log.d(TAG, "Media cache hit: " + cached.getName());
					return;
				}
			}
			writeFile(data);
			if (mayBeAdpcm(data)) {
				convert();
			}
			synchronized (CONVERTED) {
				CONVERTED.put(key, mediaFile);
			}
			return;
		}

		// 大流或无法完整读取：保持上游实现（流式写盘 + 探测）
		Log.d(TAG, "Large media stream, fallback to streaming copy");
		final String name = mediaFile.getName();
		try (RandomAccessFile raf = new RandomAccessFile(mediaFile, "rw")) {
			int length = stream.available();
			if (length >= 0) {
				raf.setLength(length);
				Log.d(TAG, "Changing file size to " + length + " bytes: " + name);
			}
			byte[] buf = new byte[4096];
			int read;
			while ((read = stream.read(buf)) != -1) {
				raf.write(buf, 0, read);
			}
		} catch (IOException e) {
			Log.d(TAG, "Media pipe failure: " + e);
			throw e;
		}
		Log.d(TAG, "Media pipe closed: " + name);
		convert();
	}

	@Override
	public void disconnect() {
		if (sharedFile) {
			// 缓存文件被其它 Player 共用，不能在这里删除
			return;
		}
		super.disconnect();
	}

	/** 读到内存；超过上限返回 null，交由调用方走流式回退 */
	private static byte[] readFully(InputStream stream, int max) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream(16 * 1024);
		byte[] buf = new byte[4096];
		int read;
		int total = 0;
		while ((read = stream.read(buf)) != -1) {
			total += read;
			if (total > max) {
				return null;
			}
			out.write(buf, 0, read);
		}
		return out.toByteArray();
	}

	private void writeFile(byte[] data) throws IOException {
		try (RandomAccessFile raf = new RandomAccessFile(mediaFile, "rw")) {
			raf.setLength(data.length);
			raf.write(data);
		}
	}

	/**
	 * 用文件头判断是否可能含 ADPCM —— 只有这种才值得跑 ffprobe/ffmpeg。
	 * <p>
	 * 上游是每次创建播放器都跑一次 ffprobe，而 ffmpeg-kit 的会话开销很大；
	 * 判断格式只需读几个字节。实测大量「.mmf」其实是标准 MIDI（头部 MThd），
	 * 根本不需要转码，之前每次播放都白跑一次探测，表现为音效延迟。
	 */
	private static boolean mayBeAdpcm(byte[] d) {
		if (d == null || d.length < 12) {
			return false;
		}
		if (matches(d, 0, "MThd")) {
			return false;                       // 标准 MIDI（含改名成 .mmf 的）
		}
		if (matches(d, 0, "RIFF") && matches(d, 8, "WAVE")) {
			return wavUsesAdpcm(d);             // 需要看 fmt 块的编码标签
		}
		if (matches(d, 0, "MMMD")) {
			return true;                        // 真正的 SMAF/MMF：可能含 ADPCM，交给 ffprobe
		}
		return false;                           // mp3 / amr / aac / mp4 / ogg 等不可能是 ADPCM
	}

	private static boolean wavUsesAdpcm(byte[] d) {
		int off = 12;
		while (off + 8 <= d.length) {
			int size = (d[off + 4] & 0xFF) | (d[off + 5] & 0xFF) << 8
					| (d[off + 6] & 0xFF) << 16 | (d[off + 7] & 0xFF) << 24;
			if (size < 0 || off + 8 + size > d.length) {
				return true;                    // 结构异常，保守起见去探测
			}
			if (matches(d, off, "fmt ") && size >= 2) {
				int tag = (d[off + 8] & 0xFF) | (d[off + 9] & 0xFF) << 8;
				return tag == 0x0002 || tag == 0x0011;   // MS ADPCM / IMA ADPCM
			}
			off += 8 + size + (size & 1);
		}
		return false;
	}

	private static boolean matches(byte[] d, int off, String ascii) {
		if (off + ascii.length() > d.length) {
			return false;
		}
		for (int i = 0; i < ascii.length(); i++) {
			if ((d[off + i] & 0xFF) != ascii.charAt(i)) {
				return false;
			}
		}
		return true;
	}


	private static String digest(byte[] data) {
		try {
			MessageDigest md = MessageDigest.getInstance("SHA-1");
			byte[] hash = md.digest(data);
			StringBuilder sb = new StringBuilder(hash.length * 2);
			for (byte b : hash) {
				sb.append(Character.forDigit((b >> 4) & 0xF, 16));
				sb.append(Character.forDigit(b & 0xF, 16));
			}
			return sb.toString();
		} catch (Exception e) {
			// 退化：长度 + 首尾字节，仍能命中共用同一段音频的重复播放
			return data.length + ":" + (data.length > 0 ? data[0] : 0)
					+ ":" + (data.length > 1 ? data[data.length - 1] : 0);
		}
	}

	private void convert() {
		try {
			String path = mediaFile.getPath();
			MediaInformationSession session = FFprobeKit.getMediaInformation(path);
			MediaInformation mediaInformation = session.getMediaInformation();
			if (mediaInformation != null) {
				StreamInformation streamInformation = mediaInformation.getStreams().get(0);
				if (streamInformation.getCodec().contains("adpcm")) {
					int dot = path.lastIndexOf('.');
					String newName = (dot == -1 ? path : path.substring(0, dot)) + ".wav";
					String cmd = "-i " + path + " -acodec pcm_u8 -ar 16000 " + newName;
					FFmpegSession ffmpegSession = FFmpegKit.execute(cmd);
					ReturnCode rc = ffmpegSession.getReturnCode();
					if (rc.isValueSuccess()) {
						Log.i(TAG, "Command execution completed successfully.");
						if (mediaFile.delete()) {
							Log.w(TAG, "convert: error delete file=" + mediaFile);
						}
						mediaFile = new File(newName);
						mediaFile.deleteOnExit();
					} else {
						Log.i(TAG, String.format(
								"Command execution failed with rc=%d and the output below.", rc.getValue()));
					}
				}
			}
		} catch (Throwable t) {
			Log.e(TAG, "FFmpeg error", t);
		}
	}
}
