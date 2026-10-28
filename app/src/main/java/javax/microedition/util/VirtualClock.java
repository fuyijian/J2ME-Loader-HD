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

/**
 * 游戏内虚拟时钟：让"游戏感知到的时间"按倍速流逝，实现 2×/3×/4× 加速。
 * <p>
 * 机制与常见"提速版 jar"（例如注入 {@code javx.lang.System} 的做法）一致：
 * 安装时 {@code AndroidMethodVisitor} 会把游戏字节码里的
 * {@code java.lang.System.currentTimeMillis()} / {@code nanoTime()} / {@code Thread.sleep(...)}
 * 重定向到本类；游戏自己按时间推进的逻辑因此跑得更快，而真实时间不受影响。
 * <p>
 * 注意：重定向发生在**导入（dex 转换）时**，所以本版本之前导入过的游戏需要重新导入才会生效；
 * 菜单里会检测并提示。
 */
public final class VirtualClock {
	private static volatile double speed = 1.0;

	/** 虚拟时间基准：baseVirt 对应真实时间 baseReal，换挡时重设以保证时间连续 */
	private static long baseVirt;
	private static long baseReal;
	/** 最近一次返回的虚拟时间，用于保证单调不回退 */
	private static volatile long lastIssued;

	static {
		baseReal = System.currentTimeMillis();
		baseVirt = baseReal;
		lastIssued = baseVirt;
	}

	private VirtualClock() {
	}

	public static double getSpeed() {
		return speed;
	}

	/** 实时切换倍速；重设基准避免时间跳变 */
	public static void setSpeed(double value) {
		if (value <= 0) {
			value = 1.0;
		}
		synchronized (VirtualClock.class) {
			long now = System.currentTimeMillis();
			baseVirt = baseVirt + (long) ((now - baseReal) * speed);
			baseReal = now;
			speed = value;
		}
	}

	/**
	 * 替代游戏的 System.currentTimeMillis()。
	 * <p>
	 * 注意：这里**不能**在倍速为 1 时直接返回真实时间——加速期间虚拟时间已经领先真实时间，
	 * 一旦直接读真实时间，游戏会看到时间"倒流"，把下一帧排到很远的将来，表现为卡死 0 帧。
	 * 因此始终从累计基准推进，并保证单调不回退。
	 */
	public static long currentTimeMillis() {
		long virt = baseVirt + (long) ((System.currentTimeMillis() - baseReal) * speed);
		long last = lastIssued;
		if (virt < last) {
			virt = last;
		}
		lastIssued = virt;
		return virt;
	}

	/** 替代游戏的 System.nanoTime()（毫秒精度足够游戏使用） */
	public static long nanoTime() {
		return currentTimeMillis() * 1_000_000L;
	}

	/** 把虚拟等待时长换算成真实等待时长（供自研 Timer 等调度使用） */
	public static long scaleDelay(long millis) {
		if (millis <= 0) {
			return 0;
		}
		double s = speed;
		if (s == 1.0) {
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
		Thread.sleep(scaleDelay(millis));
	}

	/** 替代游戏的 Thread.sleep(long, int) */
	public static void sleep(long millis, int nanos) throws InterruptedException {
		sleep(millis);
	}
}
