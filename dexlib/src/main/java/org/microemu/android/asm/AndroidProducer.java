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

		// 插桩会改动指令，原始 StackMapTable 会失效，必须重算帧。
		// 只用 COMPUTE_MAXS 会导致输出缺少 frames，dx 在 class 版本 >= 50（Java 6+）时
		// 推断失败并抛 "SimException: stack: overflow"（现代 JDK 编译的移植游戏常见）。
		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES) {
			@Override
			protected String getCommonSuperClass(String type1, String type2) {
				if (type1.equals(type2)) {
					return type1;
				}
				if ("java/lang/Object".equals(type1) || "java/lang/Object".equals(type2)) {
					return "java/lang/Object";
				}
				try {
					return super.getCommonSuperClass(type1, type2);
				} catch (Throwable t) {
					// MIDP 等类不在编译期 classpath 上，退化为 Object，保证帧合法
					return "java/lang/Object";
				}
			}
		};
		ClassVisitor cv = new AndroidClassVisitor(cw);
		cr.accept(cv, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

		return cw.toByteArray();
	}
}
