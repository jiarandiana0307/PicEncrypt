package com.example.picencrypt.utils;

import java.util.Arrays;

public abstract class BasePicEncryptScramble extends ImageScramble {
	protected double key;
	
	public BasePicEncryptScramble(Image image, double key) {
		this(image.getPixels(), image.getWidth(), image.getHeight(), key);
	}

	public BasePicEncryptScramble(int[] pixels, int width, int height, double key) {
		super(pixels, width, height);
		this.key = key;
	}
	
	protected double[][] generateLogistic(double x1, int n) {
		double[][] arr = new double[n][2];
		double x = x1;
		arr[0][0] = x;
		arr[0][1] = 0;
		for (int i = 1; i < n; ++i) {
			x = 3.9999999 * x * (1 - x);
			arr[i][0] = x;
			arr[i][1] = i;
		}
		return arr;
	}
	
	protected int[] getSortedPositions(double[][] logistacMap, int positionSize) {
		Arrays.sort(logistacMap, (a, b) -> a[0] > b[0] ? 1 : -1);
		int[] positions = new int[positionSize];
		for (int i = 0; i < positionSize; ++i) {
			positions[i] = (int) logistacMap[i][1];
		}
		return positions;
	}
}
