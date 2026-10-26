/*
 * Copyright 2026 J2ME Loader HD
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

package javax.microedition.util;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;

/**
 * 游戏内虚拟时钟：把游戏的 System.currentTimeMillis() / Thread.sleep() 按倍速缩放，
 * 实现 2x / 3x / 4x 加速。
 * <p>
 * 原理：安装时 {@code org.microemu.android.asm.AndroidMethodVisitor} 会把游戏字节码里的
 * {@code System.currentTimeMillis()} 与 {@code Thread.sleep(...)} 重定向到本类，
 * 因此这里可以把"游戏感知到的时间"拉快/拉慢，而真实时间不变。
 * <p>
 * 注意：重定向发生在**安装（dex 转换）时**，所以调过倍速后需要重新导入游戏才会生效。
 */
public final class VirtualClock {
	static final String PREF_GAME_SPEED = "pref_game_speed";

	private static volatile float speed = 1f;
	/** 最近一次换挡时刻：虚拟时间 baseVirt 对应真实时间 baseReal */
	private static volatile long baseVirt;
	private static volatile long baseReal;

	@SuppressWarnings("FieldCanBeLocal")
	private static final SharedPreferences.OnSharedPreferenceChangeListener LISTENER =
			(sharedPreferences, key) -> {
				if (PREF_GAME_SPEED.equals(key)) {
					setSpeed(sharedPreferences.getInt(key, 1));
				}
			};

	static {
		baseReal = System.currentTimeMillis();
		baseVirt = baseReal;
		try {
			Context context = ContextHolder.getAppContext();
			SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
			speed = normalize(sp.getInt(PREF_GAME_SPEED, 1));
			sp.registerOnSharedPreferenceChangeListener(LISTENER);
		} catch (Throwable ignored) {
			// 过早调用（App 尚未就绪）时保持 1x，不影响游戏
		}
	}

	private VirtualClock() {
	}

	private static float normalize(int value) {
		if (value < 1) {
			return 1f;
		}
		return value > 10 ? 10f : value;
	}

	public static float getSpeed() {
		return speed;
	}

	/** 切换倍速；会重设基准点，保证虚拟时间连续、不跳变 */
	public static synchronized void setSpeed(float newSpeed) {
		if (newSpeed <= 0) {
			newSpeed = 1f;
		}
		long nowReal = System.currentTimeMillis();
		baseVirt = baseVirt + (long) ((nowReal - baseReal) * speed);
		baseReal = nowReal;
		speed = newSpeed;
	}

	/** 替代游戏的 System.currentTimeMillis() */
	public static long currentTimeMillis() {
		float s = speed;
		if (s == 1f) {
			return System.currentTimeMillis();
		}
		return baseVirt + (long) ((System.currentTimeMillis() - baseReal) * s);
	}

	/** 替代游戏的 System.nanoTime()（按毫秒精度换算即可） */
	public static long nanoTime() {
		return currentTimeMillis() * 1_000_000L;
	}

	/** 把虚拟等待时长换算成真实等待时长（供 Timer 等自研调度使用） */
	public static long scaleDelay(long millis) {
		if (millis <= 0) {
			return 0;
		}
		float s = speed;
		if (s == 1f) {
			return millis;
		}
		long real = (long) (millis / s);
		return real < 1 ? 1 : real;
	}

	/** 替代游戏的 Thread.sleep(long)：真实等待时间按倍速缩短 */
	public static void sleep(long millis) throws InterruptedException {
		if (millis <= 0) {
			return;
		}
		float s = speed;
		long real = s == 1f ? millis : (long) (millis / s);
		if (real < 1) {
			real = 1;
		}
		Thread.sleep(real);
	}

	/** 替代游戏的 Thread.sleep(long, int) */
	public static void sleep(long millis, int nanos) throws InterruptedException {
		sleep(millis);
	}
}
