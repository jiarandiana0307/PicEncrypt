package com.example.picencrypt.utils;

import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TomatoScramble extends ImageScramble {
	private final int offset;
	private int[] positions;
	private int pos;

	public TomatoScramble(Image image) {
		this(image, 1);
	}

	public TomatoScramble(int[] pixels, int width, int height) {
		this(pixels, width, height, 1);
	}

	public TomatoScramble(Image image, double key) {
		this(image.getPixels(), image.getWidth(), image.getHeight(), key);
	}
	
	public TomatoScramble(int[] pixels, int width, int height, double key) {
		super(pixels, width, height);
		offset = (int) Math.round((Math.sqrt(5) - 1) / 2 * pixelCount * key);
	}
	
	@Override
	public Image process(ProcessType processType) {
		gilbert2d();
		int loopPosition = pixelCount - offset;
		int[] newPixels = new int[pixelCount];

		if (pixelCount > 10000) {
			int taskCount = Runtime.getRuntime().availableProcessors();
			ArrayList<Callable<Integer>> tasks = new ArrayList<>(taskCount);
			final int step = (int) Math.ceil((double)pixelCount / taskCount);

			for (int i = 0; i < taskCount; ++i) {
				final int begin = step * i;
				final int end = Math.min(begin + step, pixelCount);

				Callable<Integer> task;
				if (processType == ProcessType.ENCRYPT) {
					task = () -> {
						if (begin >= loopPosition) {
							for (int j = begin; j < end; ++j) {
								newPixels[positions[j - loopPosition]] = pixels[positions[j]];
							}
						} else if (end <= loopPosition) {
							for (int j = begin; j < end; ++j) {
								newPixels[positions[j + offset]] = pixels[positions[j]];
							}
						} else {
							for (int j = begin; j < loopPosition; ++j) {
								newPixels[positions[j + offset]] = pixels[positions[j]];
							}
							for (int j = loopPosition; j < end; ++j) {
								newPixels[positions[j - loopPosition]] = pixels[positions[j]];
							}
						}
						return null;
					};

				} else {
					task = () -> {
						if (begin >= loopPosition) {
							for (int j = begin; j < end; ++j) {
								newPixels[positions[j]] = pixels[positions[j - loopPosition]];
							}
						} else if (end <= loopPosition) {
							for (int j = begin; j < end; ++j) {
								newPixels[positions[j]] = pixels[positions[j + offset]];
							}
						} else {
							for (int j = begin; j < loopPosition; ++j) {
								newPixels[positions[j]] = pixels[positions[j + offset]];
							}
							for (int j = loopPosition; j < end; ++j) {
								newPixels[positions[j]] = pixels[positions[j - loopPosition]];
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

		} else {
			if (processType == ProcessType.ENCRYPT) {
				for (int i = 0; i < loopPosition; ++i) {
					newPixels[positions[i + offset]] = pixels[positions[i]];
				}
				for (int i = loopPosition; i < pixelCount; ++i) {
					newPixels[positions[i - loopPosition]] = pixels[positions[i]];
				}
			} else {
				for (int i = 0; i < loopPosition; ++i) {
					newPixels[positions[i]] = pixels[positions[i + offset]];
				}
				for (int i = loopPosition; i < pixelCount; ++i) {
					newPixels[positions[i]] = pixels[positions[i - loopPosition]];
				}			
			}
		}

		positions = null;
		return new Image(newPixels, width, height);
	}
	
	public Image encrypt() {
		return process(ProcessType.ENCRYPT);
	}

	public Image decrypt() {
		return process(ProcessType.DECRYPT);
	}
	
	private void gilbert2d() {
		positions = new int[pixelCount];
		pos = 0;
		if (width >= height) {
			generate2d(0, 0, width, 0, 0, height);
		} else {
			generate2d(0, 0, 0, height, width, 0);
		}
	}
	
	private void generate2d(int x, int y, int ax, int ay, int bx, int by) {
		final int w = Math.abs(ax + ay);
		final int h = Math.abs(bx + by);
		final int dax = (int) Math.signum(ax);
		final int day = (int) Math.signum(ay);
		final int dbx = (int) Math.signum(bx);
		final int dby = (int) Math.signum(by);
		
		if (h == 1) {
			for (int i = 0; i < w; ++i) {
				positions[pos] = x + y * width;
				pos++;
				x += dax;
				y += day;
			}
			return;
		}
		
		if (w == 1) {
			for (int i = 0; i < h; ++i) {
				positions[pos] = x + y * width;
				pos++;
				x += dbx;
				y += dby;
			}
			return;
		}
		
		int ax2 = Math.floorDiv(ax, 2);
		int ay2 = Math.floorDiv(ay, 2);
		int bx2 = Math.floorDiv(bx, 2);
		int by2 = Math.floorDiv(by, 2);
		final int w2 = Math.abs(ax2 + ay2);
		final int h2 = Math.abs(bx2 + by2);
		
		if (2 * w > 3 * h) {
			if ((w2 & 1) == 1 && w > 2) {
				ax2 += dax;
				ay2 += day;
			}
			generate2d(x, y, ax2, ay2, bx, by);
			generate2d(x + ax2, y + ay2, ax - ax2, ay - ay2, bx, by);
		} else {
			if ((h2 & 1) == 1 && h > 2) {
				bx2 += dbx;
				by2 += dby;
			}
			generate2d(x, y, bx2, by2, ax2, ay2);
			generate2d(x + bx2, y + by2, ax, ay, bx - bx2, by - by2);
			generate2d(x + (ax-dax) + (bx2-dbx), y + (ay-day)+(by2-dby), -bx2, -by2, -(ax-ax2), -(ay-ay2));
		}
	}
}
