package com.lgh.tapclick.myclass;

/**
 * Creates and compares a compact visual fingerprint for the area around a
 * coordinate rule. The format is deliberately versioned so malformed or
 * future signatures fail closed instead of falling back to a blind click.
 */
public final class VisualCoordinateSignature {
    public static final int DEFAULT_MATCH_THRESHOLD = 72;

    private static final String VERSION_PREFIX = "v1:";
    private static final int GRID_SIZE = 16;
    private static final int SAMPLE_COUNT = GRID_SIZE * GRID_SIZE;
    private static final int ENCODED_LENGTH = VERSION_PREFIX.length() + SAMPLE_COUNT * 2;
    private static final int MIN_REGION_SIZE = 48;
    private static final int MAX_REGION_SIZE = 112;
    private static final double MIN_INFORMATION_SCORE = 5.0d;
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private VisualCoordinateSignature() {
    }

    /**
     * Selects a square region around the target coordinate. Near screen edges
     * the region is shifted inward rather than shrunk, keeping sampling stable.
     */
    public static Region calculateRegion(int screenWidth, int screenHeight,
                                         int centerX, int centerY) {
        int minimumDimension = Math.min(screenWidth, screenHeight);
        if (screenWidth <= 0 || screenHeight <= 0 || minimumDimension < GRID_SIZE) {
            return null;
        }
        int regionSize = clamp(minimumDimension / 16, MIN_REGION_SIZE, MAX_REGION_SIZE);
        regionSize = Math.min(regionSize, minimumDimension);
        int maximumLeft = screenWidth - regionSize;
        int maximumTop = screenHeight - regionSize;
        int left = clamp(centerX - regionSize / 2, 0, maximumLeft);
        int top = clamp(centerY - regionSize / 2, 0, maximumTop);
        return new Region(left, top, regionSize, regionSize);
    }

    /**
     * Encodes a region as a 16 x 16 grayscale grid. Visually uniform regions
     * are rejected because they cannot safely distinguish an ad from a normal
     * page with the same background colour.
     */
    public static String create(int[] argbPixels, int width, int height) {
        int[] samples = sample(argbPixels, width, height);
        if (samples == null || !hasEnoughInformation(samples)) {
            return null;
        }
        StringBuilder builder = new StringBuilder(ENCODED_LENGTH);
        builder.append(VERSION_PREFIX);
        for (int sample : samples) {
            builder.append(HEX[(sample >>> 4) & 0x0f]);
            builder.append(HEX[sample & 0x0f]);
        }
        return builder.toString();
    }

    public static boolean isValid(String signature) {
        return decode(signature) != null;
    }

    /**
     * Returns a score in [0, 100], or -1 when either input is invalid. Raw
     * luminance, brightness-normalised structure and signed edge gradients are
     * combined so modest global brightness changes remain acceptable without
     * treating unrelated flat regions as equal.
     */
    public static int matchScore(String expectedSignature, int[] currentArgbPixels,
                                 int width, int height) {
        int[] expected = decode(expectedSignature);
        int[] current = sample(currentArgbPixels, width, height);
        if (expected == null || current == null) {
            return -1;
        }

        double expectedMean = mean(expected);
        double currentMean = mean(current);
        double rawDifference = 0.0d;
        double structureDifference = 0.0d;
        for (int i = 0; i < SAMPLE_COUNT; i++) {
            rawDifference += Math.abs(expected[i] - current[i]);
            structureDifference += Math.abs(
                    (expected[i] - expectedMean) - (current[i] - currentMean));
        }

        double gradientDifference = 0.0d;
        int gradientCount = 0;
        for (int y = 0; y < GRID_SIZE; y++) {
            for (int x = 0; x < GRID_SIZE; x++) {
                int index = y * GRID_SIZE + x;
                if (x + 1 < GRID_SIZE) {
                    gradientDifference += Math.abs(
                            (expected[index + 1] - expected[index])
                                    - (current[index + 1] - current[index]));
                    gradientCount++;
                }
                if (y + 1 < GRID_SIZE) {
                    gradientDifference += Math.abs(
                            (expected[index + GRID_SIZE] - expected[index])
                                    - (current[index + GRID_SIZE] - current[index]));
                    gradientCount++;
                }
            }
        }

        double rawScore = differenceScore(rawDifference / SAMPLE_COUNT);
        double structureScore = differenceScore(structureDifference / SAMPLE_COUNT);
        double gradientScore = differenceScore(
                gradientCount == 0 ? 255.0d : gradientDifference / gradientCount);
        int score = (int) Math.round(
                rawScore * 0.35d + structureScore * 0.35d + gradientScore * 0.30d);
        return clamp(score, 0, 100);
    }

    private static int[] decode(String signature) {
        if (signature == null
                || signature.length() != ENCODED_LENGTH
                || !signature.startsWith(VERSION_PREFIX)) {
            return null;
        }
        int[] samples = new int[SAMPLE_COUNT];
        int offset = VERSION_PREFIX.length();
        for (int i = 0; i < SAMPLE_COUNT; i++) {
            int high = Character.digit(signature.charAt(offset + i * 2), 16);
            int low = Character.digit(signature.charAt(offset + i * 2 + 1), 16);
            if (high < 0 || low < 0) {
                return null;
            }
            samples[i] = (high << 4) | low;
        }
        return hasEnoughInformation(samples) ? samples : null;
    }

    private static int[] sample(int[] argbPixels, int width, int height) {
        long requiredPixels = (long) width * height;
        if (argbPixels == null
                || width < GRID_SIZE
                || height < GRID_SIZE
                || requiredPixels <= 0
                || requiredPixels > argbPixels.length) {
            return null;
        }
        int[] samples = new int[SAMPLE_COUNT];
        for (int sampleY = 0; sampleY < GRID_SIZE; sampleY++) {
            int top = sampleY * height / GRID_SIZE;
            int bottom = (sampleY + 1) * height / GRID_SIZE;
            for (int sampleX = 0; sampleX < GRID_SIZE; sampleX++) {
                int left = sampleX * width / GRID_SIZE;
                int right = (sampleX + 1) * width / GRID_SIZE;
                long luminanceSum = 0L;
                int pixelCount = 0;
                for (int y = top; y < bottom; y++) {
                    int rowOffset = y * width;
                    for (int x = left; x < right; x++) {
                        int color = argbPixels[rowOffset + x];
                        int red = (color >>> 16) & 0xff;
                        int green = (color >>> 8) & 0xff;
                        int blue = color & 0xff;
                        luminanceSum += (77 * red + 150 * green + 29 * blue + 128) >>> 8;
                        pixelCount++;
                    }
                }
                if (pixelCount == 0) {
                    return null;
                }
                samples[sampleY * GRID_SIZE + sampleX] =
                        (int) (luminanceSum / pixelCount);
            }
        }
        return samples;
    }

    private static boolean hasEnoughInformation(int[] samples) {
        double mean = mean(samples);
        double variance = 0.0d;
        double gradient = 0.0d;
        int gradientCount = 0;
        for (int y = 0; y < GRID_SIZE; y++) {
            for (int x = 0; x < GRID_SIZE; x++) {
                int index = y * GRID_SIZE + x;
                double difference = samples[index] - mean;
                variance += difference * difference;
                if (x + 1 < GRID_SIZE) {
                    gradient += Math.abs(samples[index + 1] - samples[index]);
                    gradientCount++;
                }
                if (y + 1 < GRID_SIZE) {
                    gradient += Math.abs(samples[index + GRID_SIZE] - samples[index]);
                    gradientCount++;
                }
            }
        }
        double standardDeviation = Math.sqrt(variance / SAMPLE_COUNT);
        double averageGradient = gradientCount == 0 ? 0.0d : gradient / gradientCount;
        return Math.max(standardDeviation, averageGradient * 1.5d)
                >= MIN_INFORMATION_SCORE;
    }

    private static double mean(int[] samples) {
        long sum = 0L;
        for (int sample : samples) {
            sum += sample;
        }
        return (double) sum / samples.length;
    }

    private static double differenceScore(double averageDifference) {
        return Math.max(0.0d, 100.0d * (1.0d - averageDifference / 255.0d));
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(value, maximum));
    }

    public static final class Region {
        private final int left;
        private final int top;
        private final int width;
        private final int height;

        private Region(int left, int top, int width, int height) {
            this.left = left;
            this.top = top;
            this.width = width;
            this.height = height;
        }

        public int getLeft() {
            return left;
        }

        public int getTop() {
            return top;
        }

        public int getWidth() {
            return width;
        }

        public int getHeight() {
            return height;
        }
    }
}
