package com.example.picencrypt.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class RowPixelScramble extends BasePixelScramble {

	public RowPixelScramble(Image image, String key) {
		super(image, key);
	}
	
	public RowPixelScramble(int[] pixels, int width, int height, String key) {
		super(pixels, width, height, key);
	}

	@Override
	public Image process(ProcessType processType) {
		final int[] xArray = shuffle(width);
		final int[] newPixels = new int[pixelCount];
		
		final int coreCount = Runtime.getRuntime().availableProcessors();
		final int taskCount = Math.min(width, coreCount);
		final int step = (int) Math.ceil((double)width / taskCount);
		
		List<Callable<Integer>> tasks = new ArrayList<>();

		for (int k = 0; k < taskCount; ++k) {
			final int begin = k * step;
			final int end = Math.min(begin + step, width);
			
			Callable<Integer> task;
			if (processType == ProcessType.ENCRYPT) {
				task = () -> {
					for (int i = begin; i < end; ++i) {
						for (int j = 0; j < height; ++j) {
							final int m = xArray[(xArray[j % width] + i) % width];
							newPixels[i + j * width] = pixels[m + j * width];
						}
					}
					return null;
				};
			} else {
				task = () -> {
					for (int i = begin; i < end; ++i) {
						for (int j = 0; j < height; ++j) {
							final int m = xArray[(xArray[j % width] + i) % width];
							newPixels[m + j * width] = pixels[i + j * width];
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
}
