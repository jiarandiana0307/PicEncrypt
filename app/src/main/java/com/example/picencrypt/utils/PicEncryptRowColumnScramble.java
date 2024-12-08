package com.example.picencrypt.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PicEncryptRowColumnScramble extends BasePicEncryptScramble {
	private static final int maxTaskCount = 50;
	
	public PicEncryptRowColumnScramble(Image image, double key) {
		super(image, key);
	}

	public PicEncryptRowColumnScramble(int[] pixels, int width, int height, double key) {
		super(pixels, width, height, key);
	}

	@Override
	public Image process(ProcessType processType) {
		return processType == ProcessType.ENCRYPT ? encrypt() : decrypt();
	}

	@Override
	public Image encrypt() {
		int[] newPixels = pixels.clone();
		double x = key;

		final int coreCount = Runtime.getRuntime().availableProcessors();
		ExecutorService executorService = Executors.newFixedThreadPool(coreCount);
		List<Callable<Integer>> tasks = new ArrayList<>();
		for (int j = 0, offset = 0; j < height; ++j, offset += width) {
			final double[][] logisticMap = generateLogistic(x, width);
			x = logisticMap[width - 1][0];

			final int offset2 = offset;
			tasks.add(() -> {
				final int[] positions = getSortedPositions(logisticMap, width);
				for (int i = 0; i < width; ++i) {
					pixels[i + offset2] = newPixels[positions[i] + offset2];
				}
				return null;
			});
			if (tasks.size() >= maxTaskCount) {
				try {
					executorService.invokeAll(tasks);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				tasks.clear();
			}
		}

		try {
			executorService.invokeAll(tasks);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		tasks.clear();
		
		x = key;
		for (int i = 0; i < width; ++i) {
			final double[][] logisticMap = generateLogistic(x, height);
			x = logisticMap[height - 1][0];

			final int i2 = i;
			tasks.add(() -> {
				final int[] positions = getSortedPositions(logisticMap, height);
				for (int j = 0; j < height; ++j) {
					newPixels[i2 + j * width] = pixels[i2 + positions[j] * width];
				}
				return null;
			});
			if (tasks.size() >= maxTaskCount) {
				try {
					executorService.invokeAll(tasks);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				tasks.clear();
			}
		}
		try {
			executorService.invokeAll(tasks);
			executorService.shutdown();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		return new Image(newPixels, width, height);
	}

	@Override
	public Image decrypt() {
		int[] newPixels = pixels.clone();
		double x = key;

		final int coreCount = Runtime.getRuntime().availableProcessors();
		ExecutorService executorService = Executors.newFixedThreadPool(coreCount);
		List<Callable<Integer>> tasks = new ArrayList<>();
		for (int i = 0; i < width; ++i) {
			final double[][] logisticMap = generateLogistic(x, height);
			x = logisticMap[height - 1][0];
			
			final int i2 = i;
			tasks.add(() -> {
				final int[] positions = getSortedPositions(logisticMap, height);
				for (int j = 0, offset = 0; j < height; ++j, offset += width) {
					pixels[i2 + positions[j] * width] = newPixels[i2 + offset];
				}
				return null;
			});
			if (tasks.size() >= maxTaskCount) {
				try {
					executorService.invokeAll(tasks);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				tasks.clear();
			}
		}
		
		try {
			executorService.invokeAll(tasks);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		tasks.clear();
		
		x = key;
		for (int j = 0, offset = 0; j < height; ++j, offset += width) {
			final double[][] logisticMap = generateLogistic(x, width);
			x = logisticMap[width - 1][0];

			final int offset2 = offset;
			tasks.add(() -> {
				final int[] positions = getSortedPositions(logisticMap, width);
				for (int i = 0; i < width; ++i) {
					newPixels[positions[i] + offset2] = pixels[i + offset2];
				}
				return null;
			});
			if (tasks.size() >= maxTaskCount) {
				try {
					executorService.invokeAll(tasks);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				tasks.clear();
			}
		}
		
		try {
			executorService.invokeAll(tasks);
			executorService.shutdown();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		return new Image(newPixels, width, height);
	}

}
