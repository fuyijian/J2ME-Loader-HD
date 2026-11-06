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

package javax.microedition.media;

/**
 * 需要感知应用前后台切换的播放器实现。
 * <p>
 * 应用切到后台时由 {@link Manager#setBackground(boolean)} 统一通知：
 * 实现者应当立刻静音，并在后台期间拒绝再次发声——有些游戏发现音乐被停会自己再调
 * {@code start()}，只在切后台那一下 stop() 是拦不住的。
 */
public interface BackgroundAware {
	/**
	 * @param background true = 进入后台（静音），false = 回到前台（恢复）
	 */
	void onBackgroundChanged(boolean background);
}
