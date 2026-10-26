/**
 * MicroEmulator
 * Copyright (C) 2008 Bartek Teodorczyk <barteo@barteo.net>
 * Copyright (C) 2017-2018 Nikita Shakarun
 * Copyright (C) 2021-2022 Yury Kharchenko
 * <p>
 * It is licensed under the following two licenses as alternatives:
 * 1. GNU Lesser General Public License (the "LGPL") version 2.1 or any newer version
 * 2. Apache License (the "AL") Version 2.0
 * <p>
 * You may not use this file except in compliance with at least one of
 * the above two licenses.
 * <p>
 * You may obtain a copy of the LGPL at
 * http://www.gnu.org/licenses/old-licenses/lgpl-2.1.txt
 * <p>
 * You may obtain a copy of the AL at
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the LGPL or the AL for the specific language governing permissions and
 * limitations.
 *
 * @version $Id$
 */

package org.microemu.android.asm;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;

public class AndroidProducer {

	public static byte[] instrument(final byte[] classData, String classFileName)
			throws IllegalArgumentException {
		ClassReader cr = new ClassReader(classData);
		if (!cr.getClassName().equals(classFileName.substring(0, classFileName.length() - 6))) {
			throw new IllegalArgumentException("Class name does not match path");
		}

		/*
		 * 帧处理策略（两次踩坑后的结论）：
		 * - class 版本 < 50 的类本来就没有 StackMapTable（老 J2ME 游戏，如 Java 1.1 编译的移植版），
		 *   若用 COMPUTE_FRAMES 强造帧，遇到 MIDP 类（不在编译期 classpath）只能退化成 Object，
		 *   会产生**不精确的帧**，ART 校验失败 → 游戏启动即崩（VerifyError）。
		 * - class 版本 >= 50 的类带帧：不要 SKIP_FRAMES，直接保留原始帧即可。
		 *   本类的改写都是**等价替换**（invokestatic 换 owner / 插入被紧随其后的调用消耗的常量），
		 *   栈形状不变，因此原帧依然有效 —— 既避免 dx 推断失败（stack: overflow），也不会造出错帧。
		 */
		ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_MAXS);
		ClassVisitor cv = new AndroidClassVisitor(cw);
		cr.accept(cv, ClassReader.SKIP_DEBUG);

		return cw.toByteArray();
	}
}
