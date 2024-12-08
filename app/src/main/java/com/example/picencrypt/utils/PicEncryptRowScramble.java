package com.example.picencrypt.utils;

import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PicEncryptRowScramble extends BasePicEncryptScramble {
	
	public PicEncryptRowScramble(Image image, double key) {
		super(image, key);
	}

	public PicEncryptRowScramble(int[] pixels, int width, int height, double key) {
		super(pixels, width, height, key);
	}

	@Override
	public Image process(ProcessType processType) {
		double[][] logisticMap = generateLogistic(key, width);
		int[] positions = getSortedPositions(logisticMap, width);

		int[] newPixels = new int[pixelCount];

		final int taskCount = Runtime.getRuntime().availableProcessors();
		ArrayList<Callable<Integer>> tasks = new ArrayList<>(taskCount);
		final int step = (int) Math.ceil((double)width / taskCount);
		final int offset = (height - 1) * width;

		for (int k = 0; k < taskCount; ++k) {
			final int begin = step * k;
			final int end = Math.min(begin + step, width);

			Callable<Integer> task;
			if (processType == ProcessType.ENCRYPT) {
				task = () -> {
					for (int i = begin; i < end; ++i) {
						final int m = positions[i];
						for (int j = offset; j >= 0; j -= width) {
							newPixels[i + j] = pixels[m + j];
						}
					}
					return null;
				};

			} else {
				task = () -> {
					for (int i = begin; i < end; ++i) {
						final int m = positions[i];
						for (int j = offset; j >= 0; j -= width) {
							newPixels[m + j] = pixels[i + j];
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

		return new Image(newPixels, width, height);
	}

	@Override
	public Image encrypt() {
		return process(ProcessType.ENCRYPT);
	}

	@Override
	public Image decrypt() {
		return process(ProcessType.DECRYPT);
	}

}
