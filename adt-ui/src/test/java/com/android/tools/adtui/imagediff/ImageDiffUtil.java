/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.tools.adtui.imagediff;

import com.android.testutils.TestResources;
import org.jetbrains.annotations.NotNull;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static java.awt.image.BufferedImage.TYPE_INT_ARGB;
import static java.io.File.separatorChar;
import static org.junit.Assert.*;

/**
 * Utility methods to be used by the tests of {@link com.android.tools.adtui.imagediff} package.
 */
public final class ImageDiffUtil {

  /**
   * Font that can be used in image comparison tests that generate images that contain a lot of text. Lucida Sans Regular looks similar in
   * different OS, so it seems a good choice for these kinds of test.
   */
  public static final Font DEFAULT_IMG_DIFF_FONT = new Font("Lucida Sans Regular", Font.PLAIN, 12);

  private static final String TEST_DATA_DIR = "/imagediff/";

  private static final Dimension TEST_IMAGE_DIMENSION = new Dimension(640, 480);

  private static final String IMG_DIFF_TEMP_DIR = getTempDir() + "/imagediff";

  /**
   * Unmodifiable list containing all the {@link ImageDiffEntry} of the imagediff package.
   * They are used for running the tests of {@link com.android.tools.adtui.imagediff} package as well as exporting its baseline images.
   * When a generator is implemented, its entries should be included in this list.
   */
  public static final List<ImageDiffEntry> IMAGE_DIFF_ENTRIES = Collections.unmodifiableList(new ArrayList<ImageDiffEntry>() {{
    addAll(new LineChartEntriesRegistrar().getImageDiffEntries());
    addAll(new StateChartEntriesRegistrar().getImageDiffEntries());
  }});

  static {
    // Create tmpDir in case it doesn't exist
    new File(IMG_DIFF_TEMP_DIR).mkdirs();
  }

  /**
   * Default threshold to be used when comparing two images.
   * If the calculated difference between the images is greater than this value (in %), the test should fail.
   * TODO: current value is 0.5%. This can be revisited later in case it happens not to be a good value.
   */
  public static final float DEFAULT_IMAGE_DIFF_PERCENT_THRESHOLD = 0.5f;

  private ImageDiffUtil() {
  }

  /**
   * Compares a generated image with a baseline one. If the images differ by more than a determined percentage (similarityThreshold),
   * an image containing the expected, actual and diff images is generated and the test that calls this method fails.
   *
   * @param baselineImageFilename filename of the baseline image
   * @param generatedImage image generated by a test
   * @param similarityThreshold how much (in percent) the baseline and the generated images can differ to still be considered similar
   */
  public static void assertImagesSimilar(String baselineImageFilename, BufferedImage generatedImage, float similarityThreshold) {
    File baselineImageFile = TestResources.getFile(ImageDiffUtil.class, TEST_DATA_DIR + baselineImageFilename);
    BufferedImage baselineImage;

    try {
      baselineImage = convertToARGB(ImageIO.read(baselineImageFile));
      assertImageSimilar(baselineImageFilename, baselineImage, generatedImage, similarityThreshold);
    } catch (IOException e) {
      fail(e.getMessage());
    }
  }

  /**
   * Exports an image as a baseline.
   *
   * @param destinationFile where the image should be exported to
   * @param image image to be exported
   */
  public static void exportBaselineImage(File destinationFile, BufferedImage image) {
    try {
      ImageIO.write(image, "PNG", destinationFile);
    } catch (IOException e) {
      System.err.println("Caught IOException while trying to export a baseline image: " + destinationFile.getName());
    }
  }

  /**
   * Creates a {@link BufferedImage} from a Swing component.
   */
  public static BufferedImage getImageFromComponent(Component component) {
    component.setSize(TEST_IMAGE_DIMENSION);
    component.setPreferredSize(TEST_IMAGE_DIMENSION);
    // Call doLayout in the content pane and its children
    doLayoutComponentTree(component);

    @SuppressWarnings("UndesirableClassUsage") // Don't want Retina images in unit tests
    BufferedImage image = new BufferedImage(component.getWidth(), component.getHeight(), TYPE_INT_ARGB);
    Graphics2D g = image.createGraphics();
    component.printAll(g);
    g.dispose();

    return image;
  }

  /**
   * Call doLayout in {@param component} and in its children, recursively.
   */
  private static void doLayoutComponentTree(Component component) {
    synchronized (component.getTreeLock()) {
      component.doLayout();
      // If component is a container, call doLayout in its children
      if (component instanceof Container) {
        for (Component child : ((Container) component).getComponents()) {
          doLayoutComponentTree(child);
        }
      }
    }
  }

  /**
   * Converts a BufferedImage type to {@link TYPE_INT_ARGB},
   * which is the only type accepted by {@link ImageDiffUtil#assertImageSimilar}.
   */
  private static BufferedImage convertToARGB(@NotNull BufferedImage inputImg) {
    if (inputImg.getType() == TYPE_INT_ARGB) {
      return inputImg; // Early return in case the image has already the correct type
    }
    @SuppressWarnings("UndesirableClassUsage") // Don't want Retina images in unit tests
    BufferedImage outputImg = new BufferedImage(inputImg.getWidth(), inputImg.getHeight(), TYPE_INT_ARGB);
    Graphics2D g2d = outputImg.createGraphics();
    g2d.drawImage(inputImg, 0, 0, null);
    g2d.dispose();
    return outputImg;
  }

  public static void assertImageSimilar(String imageName, BufferedImage goldenImage,
                                  BufferedImage image, double maxPercentDifferent) throws IOException {
    assertEquals("Only TYPE_INT_ARGB image types are supported", TYPE_INT_ARGB, image.getType());

    if (goldenImage.getType() != TYPE_INT_ARGB) {
      @SuppressWarnings("UndesirableClassUsage") // Don't want Retina images in unit tests
      BufferedImage temp = new BufferedImage(goldenImage.getWidth(), goldenImage.getHeight(), TYPE_INT_ARGB);
      temp.getGraphics().drawImage(goldenImage, 0, 0, null);
      goldenImage = temp;
    }
    assertEquals(TYPE_INT_ARGB, goldenImage.getType());

    int imageWidth = Math.min(goldenImage.getWidth(), image.getWidth());
    int imageHeight = Math.min(goldenImage.getHeight(), image.getHeight());

    // Blur the images to account for the scenarios where there are pixel
    // differences
    // in where a sharp edge occurs
    // goldenImage = blur(goldenImage, 6);
    // image = blur(image, 6);

    int width = 3 * imageWidth;
    @SuppressWarnings("UnnecessaryLocalVariable")
    int height = imageHeight; // makes code more readable
    @SuppressWarnings("UndesirableClassUsage") // Don't want Retina images in unit tests
    BufferedImage deltaImage = new BufferedImage(width, height, TYPE_INT_ARGB);
    Graphics g = deltaImage.getGraphics();

    // Compute delta map
    long delta = 0;
    for (int y = 0; y < imageHeight; y++) {
      for (int x = 0; x < imageWidth; x++) {
        int goldenRgb = goldenImage.getRGB(x, y);
        int rgb = image.getRGB(x, y);
        if (goldenRgb == rgb) {
          deltaImage.setRGB(imageWidth + x, y, 0x00808080);
          continue;
        }

        // If the pixels have no opacity, don't delta colors at all
        if (((goldenRgb & 0xFF000000) == 0) && (rgb & 0xFF000000) == 0) {
          deltaImage.setRGB(imageWidth + x, y, 0x00808080);
          continue;
        }

        int deltaR = ((rgb & 0xFF0000) >>> 16) - ((goldenRgb & 0xFF0000) >>> 16);
        int newR = 128 + deltaR & 0xFF;
        int deltaG = ((rgb & 0x00FF00) >>> 8) - ((goldenRgb & 0x00FF00) >>> 8);
        int newG = 128 + deltaG & 0xFF;
        int deltaB = (rgb & 0x0000FF) - (goldenRgb & 0x0000FF);
        int newB = 128 + deltaB & 0xFF;

        int avgAlpha = ((((goldenRgb & 0xFF000000) >>> 24)
                         + ((rgb & 0xFF000000) >>> 24)) / 2) << 24;

        int newRGB = avgAlpha | newR << 16 | newG << 8 | newB;
        deltaImage.setRGB(imageWidth + x, y, newRGB);

        delta += Math.abs(deltaR);
        delta += Math.abs(deltaG);
        delta += Math.abs(deltaB);
      }
    }

    // 3 different colors, 256 color levels
    long total = imageHeight * imageWidth * 3L * 256L;
    float percentDifference = (float) (delta * 100 / (double) total);

    String error = null;
    if (percentDifference > maxPercentDifferent) {
      error = String.format("Images differ (by %.1f%%)", percentDifference);
    } else if (Math.abs(goldenImage.getWidth() - image.getWidth()) >= 2) {
      error = "Widths differ too much for " + imageName + ": " + goldenImage.getWidth() + "x" + goldenImage.getHeight() +
              "vs" + image.getWidth() + "x" + image.getHeight();
    } else if (Math.abs(goldenImage.getHeight() - image.getHeight()) >= 2) {
      error = "Heights differ too much for " + imageName + ": " + goldenImage.getWidth() + "x" + goldenImage.getHeight() +
              "vs" + image.getWidth() + "x" + image.getHeight();
    }

    assertEquals(TYPE_INT_ARGB, image.getType());
    if (error != null) {
      // Expected on the left
      // Golden on the right
      g.drawImage(goldenImage, 0, 0, null);
      g.drawImage(image, 2 * imageWidth, 0, null);

      // Labels
      if (imageWidth > 80) {
        g.setColor(Color.RED);
        g.drawString("Expected", 10, 20);
        g.drawString("Actual", 2 * imageWidth + 10, 20);
      }

      File output = new File(getTempDir(), "delta-" + imageName.replace(separatorChar, '_'));
      if (output.exists()) {
        boolean deleted = output.delete();
        assertTrue(deleted);
      }
      ImageIO.write(deltaImage, "PNG", output);
      error += " - see details in " + output.getPath();
      System.out.println(error);
      fail(error);
    }

    g.dispose();
  }

  @NotNull
  // TODO move this function to a common location for all our tests
  public static File getTempDir() {
    if (System.getProperty("os.name").equals("Mac OS X")) {
      return new File("/tmp"); //$NON-NLS-1$
    }

    return new File(System.getProperty("java.io.tmpdir")); //$NON-NLS-1$
  }
}
