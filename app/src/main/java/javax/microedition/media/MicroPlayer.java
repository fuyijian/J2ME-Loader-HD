/*
 * Copyright 2012 Kulikov Dmitriy
 * Copyright 2017-2020 Nikita Shakarun
 * Copyright 2020-2023 Yury Kharchenko
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

import android.media.MediaPlayer;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import javax.microedition.media.control.MetaDataControl;
import javax.microedition.media.control.ToneControl;
import javax.microedition.media.protocol.DataSource;
import javax.microedition.media.tone.MidiToneConstants;
import javax.microedition.media.tone.ToneSequence;

import kotlin.io.FilesKt;
import ru.woesss.j2me.mmapi.FileCacheDataSource;
import ru.woesss.j2me.mmapi.protocol.device.DeviceMetaData;

public class MicroPlayer extends BasePlayer implements MediaPlayer.OnCompletionListener, ToneControl,
		BackgroundAware {
	private static final String TAG = MicroPlayer.class.getSimpleName();

	protected final MediaPlayer player = new AndroidPlayer();
	protected final DataSource source;
	private final InternalMetaData metadata;

	public MicroPlayer(String locator) throws IOException {
		if (!Manager.TONE_DEVICE_LOCATOR.equals(locator)) {
			throw new IllegalArgumentException();
		}
		source = new FileCacheDataSource("audio/x-tone-seq", "mid");
		addControl(MidiToneConstants.TONE_CONTROL_FULL_NAME, this);
		metadata = new DeviceMetaData();
		init();
	}

	MicroPlayer(DataSource datasource) {
		source = datasource;
		metadata = new InternalMetaData();
		init();
	}

	private void init() {
		player.setOnCompletionListener(this);
		addControl(MetaDataControl.class.getName(), metadata);
	}

	@Override
	public synchronized void onCompletion(MediaPlayer mp) {
		complete();
	}

	@Override
	public void doRealize() throws IOException {
		source.connect();
		player.setDataSource(source.getLocator());
	}

	public void doPrefetch() throws IOException {
		metadata.updateMetaData(source);
	}

	public void doStart() {
		if (Manager.isBackground()) {
			// 后台期间游戏要播放：先不真正开始（省电），回前台再补 start
			pendingStart = true;
			return;
		}
		player.start();
	}

	/** 因切后台被我们暂停的播放器，回前台要恢复（并重设音量） */
	private boolean pausedByUs;
	/** 后台期间游戏发起的播放：先不开始，等回前台再补 */
	private boolean pendingStart;

	/**
	 * 最近一次设置的音量。回前台恢复播放后要重新应用；
	 * 初值取自"游戏最后一次设定的音量"——游戏重建播放器时通常不会重设音量，
	 * 若默认给 1.0 就会出现「游戏里调成 0，回来又有声」。
	 */
	private float lastLeft = Manager.getInheritedLevel() / 100f;
	private float lastRight = lastLeft;

	/**
	 * 应用切前后台时由 Manager 调用。
	 * <p>
	 * 后台：真正 pause（省电），回前台恢复并重新应用游戏设定的音量
	 *（MediaPlayer 重新开始后音量会回到默认，不补会让调成 0 的音量又响）。
	 */
	public void onBackgroundChanged(boolean background) {
		if (background) {
			// 只有「确实在播放」的才标记为我们暂停的；
			// 否则回前台会把游戏已经停掉的播放器重新拉起来（表现为音乐莫名其妙又响）
			if (state == STARTED) {
				try {
					player.pause();
				} catch (Throwable ignored) {
				}
				pausedByUs = true;
			}
		} else if (pausedByUs || pendingStart) {
			pausedByUs = false;
			pendingStart = false;
			try {
				player.start();
				player.setVolume(lastLeft, lastRight);
			} catch (Throwable ignored) {
			}
		}
	}

	public void doStop() {
		pausedByUs = false;
		pendingStart = false;
		player.pause();
	}

	public void doClose() {
		pausedByUs = false;
		pendingStart = false;
		player.release();
		source.disconnect();
	}

	public void doReset() {
		player.reset();
	}

	public void doSetMediaTime(long usec) {
		int time = (int) usec / 1000;
		player.seekTo(time);
	}

	@Override
	public long doGetMediaTime() {
		return player.getCurrentPosition() * 1000L;
	}

	@Override
	public long doGetDuration() {
		return player.getDuration() * 1000L;
	}

	@Override
	public void doSetLooping(boolean looping) {
		player.setLooping(looping);
	}

	@Override
	public String doGetContentType() {
		return source.getContentType();
	}

	@Override
	public void doSetVolume(float left, float right) {
		Manager.setLastLevel(Math.round(left * 100));
		this.lastLeft = left;
		this.lastRight = right;
		player.setVolume(left, right);
	}


	// ToneControl

	@Override
	public void setSequence(byte[] sequence) {
		if (state >= PREFETCHED) {
			throw new IllegalStateException();
		} else if (sequence == null) {
			throw new IllegalArgumentException("sequence is NULL");
		}
		try {
			ToneSequence tone = new ToneSequence(sequence);
			tone.process();
			byte[] data = tone.getByteArray();
			String locator = source.getLocator();
			FilesKt.writeBytes(new File(locator), data);
		} catch (Exception e) {
			Log.e(TAG, "setSequence: ", e);
			throw new IllegalArgumentException(e);
		}
	}
}
