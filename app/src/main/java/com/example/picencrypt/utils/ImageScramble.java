package com.example.picencrypt.utils;

public abstract class ImageScramble {
	protected final int[] pixels;
	protected final int width;
	protected final int height;
	protected final int pixelCount;
	public enum ProcessType { ENCRYPT, DECRYPT }

	public static class Image {
		private final int[] pixels;
		private final int width;
		private final int height;
		
		public Image(int[] pixels, int width, int height) {
			this.pixels = pixels;
			this.width = width;
			this.height = height;
		}
		
		public int[] getPixels() {
			return pixels;
		}
		
		public int getWidth() {
			return width;
		}
		
		public int getHeight() {
			return height;
		}
	}
	
	public ImageScramble(Image image) {
		this(image.getPixels(), image.getWidth(), image.getHeight());
	}
	
	public ImageScramble(int[] pixels, int width, int height) {
		this.pixels = pixels;
		this.width = width;
		this.height = height;
		pixelCount = width * height;
	}
	
	public int getWidth() {
		return width;
	}
	
	public int getHeight() {
		return height;
	}
	
	public abstract Image process(ProcessType processType);
	
	public abstract Image encrypt();
	
	public abstract Image decrypt();
}
