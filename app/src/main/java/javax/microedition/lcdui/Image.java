/*
 * Copyright 2012 Kulikov Dmitriy
 * Copyright 2017-2018 Nikita Shakarun
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

package javax.microedition.lcdui;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Rect;

import java.io.IOException;
import java.io.InputStream;

import javax.microedition.lcdui.game.Sprite;
import javax.microedition.shell.AppClassLoader;

import ru.playsoftware.j2meloader.util.PNGUtils;

public class Image {
	private Bitmap bitmap;
	private Graphics graphics;
	private final Rect bounds;
	/**
	 * Ratio between the size of the backing bitmap and the logical size of the image.
	 * Used by the supersampled screen buffer, where one logical pixel is backed
	 * by {@code pixelScale * pixelScale} bitmap pixels.
	 */
	private final int pixelScale;
	private boolean isBlackWhiteAlpha;

	public Image(Bitmap bitmap) {
		this(bitmap, 1);
	}

	Image(Bitmap bitmap, int pixelScale) {
		if (bitmap == null) {
			throw new NullPointerException();
		}
		this.bitmap = bitmap;
		this.pixelScale = Math.max(1, pixelScale);
		bounds = new Rect(0, 0, bitmap.getWidth() / this.pixelScale,
				bitmap.getHeight() / this.pixelScale);
	}

	public Bitmap getBitmap() {
		return bitmap;
	}

	/**
	 * @return the ratio between the backing bitmap size and the logical image size,
	 * always at least 1
	 */
	public int getPixelScale() {
		return pixelScale;
	}

	public static Image createImage(int width, int height) {
		return createImage(width, height, Color.WHITE);
	}

	public static Image createImage(int width, int height, int argb) {
		// 游戏创建的可绘制缓冲：跟随超采样倍率（上限 2），否则预渲染进缓存的文字不会变清晰
		try {
			return createImage(width, height, argb, Canvas.mutableImageScale(width, height));
		} catch (OutOfMemoryError e) {
			// 内存不够就退回 1x，宁可模糊也不能崩
			return createImage(width, height, argb, 1);
		}
	}

	/**
	 * Creates an image which is drawn at supersampled resolution.
	 *
	 * @param pixelScale ratio between the backing bitmap size and the logical size
	 */
	public static Image createImage(int width, int height, int argb, int pixelScale) {
		int scale = Math.max(1, pixelScale);
		Bitmap bitmap = Bitmap.createBitmap(width * scale, height * scale, Bitmap.Config.ARGB_8888);
		if (argb != 0) bitmap.eraseColor(argb);
		return new Image(bitmap, scale);
	}

	public static Image createImage(String resname) throws IOException {
		Bitmap b;
		try (InputStream stream = AppClassLoader.getResourceAsStream(null, resname)) {
			if (stream == null) {
				throw new IOException("Can't read image: " + resname);
			}
			b = PNGUtils.getFixedBitmap(stream);
		}
		if (b == null) {
			throw new IOException("Can't decode image: " + resname);
		}
		return new Image(b);
	}

	public static Image createImage(InputStream stream) throws IOException {
		Bitmap b = PNGUtils.getFixedBitmap(stream);
		if (b == null) {
			throw new IOException("Can't decode image");
		}
		return new Image(b);
	}

	public static Image createImage(byte[] imageData, int imageOffset, int imageLength) {
		Bitmap b = PNGUtils.getFixedBitmap(imageData, imageOffset, imageLength);
		if (b == null) {
			throw new IllegalArgumentException("Can't decode image");
		}
		return new Image(b);
	}

	public static Image createImage(Image image, int x, int y, int width, int height, int transform) {
		final int ps = image.pixelScale;
		if (ps > 1) {
			// 源图是超采样的：物理区域 = 逻辑区域 × ps；结果保持同样倍率以保留细节
			int pw = width * ps;
			int ph = height * ps;
			Matrix m = transform == 0 ? null : Sprite.transformMatrix(transform, pw / 2.0f, ph / 2.0f);
			Bitmap bmp = Bitmap.createBitmap(image.bitmap, x * ps, y * ps, pw, ph, m, false);
			return new Image(bmp, ps);
		}
		Matrix m = transform == 0 ? null : Sprite.transformMatrix(transform, width / 2.0f, height / 2.0f);
		return new Image(Bitmap.createBitmap(image.bitmap, x, y, width, height, m, false));
	}

	public static Image createImage(Image source) {
		if (source.isMutable())
			return new Image(Bitmap.createBitmap(source.bitmap), source.pixelScale);
		return source;
	}

	public static Image createRGBImage(int[] rgb, int width, int height, boolean processAlpha) {
		if (!processAlpha) {
			final int length = width * height;
			int[] tmp = new int[length];
			for (int i = 0; i < length; i++) {
				tmp[i] = rgb[i] | 0xFF000000;
			}
			rgb = tmp;
		}
		return new Image(Bitmap.createBitmap(rgb, width, height, Bitmap.Config.ARGB_8888));
	}

	public Graphics getGraphics() {
		return new Graphics(this);
	}

	public boolean isMutable() {
		return bitmap.isMutable();
	}

	public int getWidth() {
		return bounds.right;
	}

	public int getHeight() {
		return bounds.bottom;
	}

	public void getRGB(int[] rgbData, int offset, int scanlength, int x, int y, int width, int height) {
		if (pixelScale > 1) {
			// 调用方期望逻辑像素，把超采样区域缩回来读
			Bitmap area = Bitmap.createBitmap(bitmap, x * pixelScale, y * pixelScale,
					width * pixelScale, height * pixelScale);
			Bitmap scaled = Bitmap.createScaledBitmap(area, width, height, true);
			scaled.getPixels(rgbData, offset, scanlength, 0, 0, width, height);
			return;
		}
		bitmap.getPixels(rgbData, offset, scanlength, x, y, width, height);
	}

	void copyTo(Image dst) {
		Rect src = new Rect(0, 0, bounds.right * pixelScale, bounds.bottom * pixelScale);
		dst.getSingleGraphics().getCanvas().drawBitmap(bitmap, src, bounds, null);
	}

	void copyTo(Image dst, int x, int y) {
		Rect src = new Rect(0, 0, bounds.right * pixelScale, bounds.bottom * pixelScale);
		Rect r = new Rect(x, y, x + bounds.right, y + bounds.bottom);
		dst.getSingleGraphics().getCanvas().drawBitmap(bitmap, src, r, null);
	}

	public Graphics getSingleGraphics() {
		if (graphics == null) {
			graphics = getGraphics();
		}
		return graphics;
	}

	void setSize(int width, int height) {
		if (width * pixelScale > bitmap.getWidth() || height * pixelScale > bitmap.getHeight()) {
			bitmap = Bitmap.createBitmap(width * pixelScale, height * pixelScale,
					Bitmap.Config.ARGB_8888);
			// the cached graphics is bound to the replaced bitmap
			graphics = null;
		}
		bounds.right = width;
		bounds.bottom = height;
		if (graphics != null) {
			graphics.reset(0, 0, width, height);
		}
	}

	public Rect getBounds() {
		return bounds;
	}

	public boolean isBlackWhiteAlpha() {
		return isBlackWhiteAlpha;
	}

	public void setBlackWhiteAlpha(boolean blackWhiteAlpha) {
		isBlackWhiteAlpha = blackWhiteAlpha;
	}
}
