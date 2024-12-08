package com.example.picencrypt.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BlockScramble extends BasePixelScramble {
	private final int xBlockCount;
	private final int yBlockCount;
	
	public BlockScramble(Image image, String key) {
		this(image.getPixels(), image.getWidth(), image.getHeight(), key);
	}

	public BlockScramble(int[] pixels, int width, int height, String key) {
		this(pixels, width, height, key, 32, 32);
	}
	
	public BlockScramble(int[] pixels, int width, int height, String key, int xBlockCount, int yBlockCount) {
		super(pixels, width, height, key);
		this.xBlockCount = xBlockCount;
		this.yBlockCount = yBlockCount;
	}
	
	@Override
	public Image process(ProcessType processType) {
		final int[] xArray = shuffle(xBlockCount);
		final int[] yArray = shuffle(yBlockCount);

		final int newWidth;
		final int newHeight;
		if (width % xBlockCount > 0) {
			newWidth = width + xBlockCount - width % xBlockCount;
		} else {
			newWidth = width;
		}
		if (height % yBlockCount > 0) {
			newHeight = height + yBlockCount - height % yBlockCount;
		} else {
			newHeight = height;
		}
		final int blockWidth = newWidth / xBlockCount;
		final int blockHeight = newHeight / yBlockCount;
		final int[] newPixels = new int[newWidth * newHeight];

		final int coreCount = Runtime.getRuntime().availableProcessors();
		final int taskCount = Math.min(newWidth, coreCount);
		final int step = (int) Math.ceil((double)newWidth / taskCount);

		List<Callable<Integer>> tasks = new ArrayList<>();

		for (int k = 0; k < taskCount; ++k) {
			final int begin = k * step;
			final int end = Math.min(begin + step, newWidth);

			Callable<Integer> task;
			if (processType == ProcessType.ENCRYPT) {
				task = () -> {
					for (int i = begin; i < end; ++i) {
						for (int j = 0; j < newHeight; ++j) {
							int n = j;
							int m = (xArray[(n / blockHeight) % xBlockCount] * blockWidth + i) % newWidth;
							m = xArray[m / blockWidth] * blockWidth + m % blockWidth;
							n = (yArray[m / blockWidth % yBlockCount] * blockHeight + n) % newHeight;
							n = yArray[n / blockHeight] * blockHeight + n % blockHeight;
							newPixels[i + j * newWidth] = pixels[m % width + n % height * width];
						}
					}
					return null;
				};

			} else {
				task = () -> {
					for (int i = begin; i < end; ++i) {
						for (int j = 0; j < newHeight; ++j) {
							int n = j;
							int m = (xArray[(n / blockHeight) % xBlockCount] * blockWidth + i) % newWidth;
							m = xArray[m / blockWidth] * blockWidth + m % blockWidth;
							n = (yArray[m / blockWidth % yBlockCount] * blockHeight + n) % newHeight;
							n = yArray[n / blockHeight] * blockHeight + n % blockHeight;
							newPixels[m + n * newWidth] = pixels[i % width + j % height * width];
						}
					}
					return null;
				};
			}

			tasks.add(task);
		}

		ExecutorService executorService = Executors.newFixedThreadPool(taskCount);
		try {
			executorService.invokeAll(tasks);
		} catch (InterruptedException e) {
			e.printStackTrace();
		} finally {
			executorService.shutdown();
		}

		return new Image(newPixels, newWidth, newHeight);	
	}
}
