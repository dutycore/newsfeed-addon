package dk.dutyc0re.newsfeed.core.hud;

import net.labymod.api.client.gfx.pipeline.renderer.text.TextRenderingOptions;
import net.labymod.api.client.gui.icon.Icon;
import net.labymod.api.client.gui.screen.state.RoundedData;
import net.labymod.api.client.gui.screen.state.ScreenCanvas;
import net.labymod.api.util.color.GradientDirection;

/**
 * Small wrapper around the canvas that applies a fade alpha to every color. Shared by the panel
 * and the toast so they look the same.
 */
public final class Painter {

  public static final int PANEL_RGB = 0x0E1014;
  public static final int WHITE = 0xFFFFFF;

  private static final int TEXT_FLAGS = TextRenderingOptions.USE_FLOATING_POINT_VALUES;

  private final ScreenCanvas canvas;
  private final float alpha;

  public Painter(ScreenCanvas canvas, float alpha) {
    this.canvas = canvas;
    this.alpha = alpha;
  }

  public ScreenCanvas canvas() {
    return this.canvas;
  }

  /** A painter on the same canvas with an extra alpha multiplier. */
  public Painter withAlpha(float multiplier) {
    return new Painter(this.canvas, this.alpha * multiplier);
  }

  private int argb(int rgb, float opacity) {
    int a = Math.round(Math.max(0F, Math.min(1F, opacity * this.alpha)) * 255F);
    return (a << 24) | (rgb & 0xFFFFFF);
  }

  /** The panel background: dark, rounded, with a 1 px (design) white 8% border. */
  public void panel(float x, float y, float width, float height, float radius, float opacity) {
    this.canvas.submitRelativeRoundedRect(x, y, width, height, this.argb(PANEL_RGB, opacity),
        RoundedData.builder()
            .setRadius(radius)
            .setLowerEdgeSoftness(-0.125F)
            .setUpperEdgeSoftness(0.5F)
            .setBorderThickness(0.5F)
            .setBorderColor(this.argb(WHITE, 0.08F))
            .build());
  }

  public void rounded(float x, float y, float width, float height, float radius, int rgb,
      float opacity) {
    this.canvas.submitRelativeRoundedRect(x, y, width, height, this.argb(rgb, opacity),
        RoundedData.builder()
            .setRadius(radius)
            .setLowerEdgeSoftness(-0.125F)
            .setUpperEdgeSoftness(0.5F)
            .build());
  }

  /** A panel-colored bar with only the bottom corners rounded, e.g. a footer over a list. */
  public void bottomBar(float x, float y, float width, float height, float radius,
      float opacity) {
    this.canvas.submitRelativeRoundedRect(x, y, width, height, this.argb(PANEL_RGB, opacity),
        RoundedData.builder()
            .setLeftBottomRadius(radius)
            .setRightBottomRadius(radius)
            .setLowerEdgeSoftness(-0.125F)
            .setUpperEdgeSoftness(0.5F)
            .build());
  }

  /** A vertical fade from {@code fromOpacity} at the top to {@code toOpacity} at the bottom. */
  public void gradientDown(float x, float y, float width, float height, int rgb,
      float fromOpacity, float toOpacity) {
    this.canvas.submitRelativeGradientRect(GradientDirection.TOP_TO_BOTTOM, x, y, width, height,
        this.argb(rgb, fromOpacity), this.argb(rgb, toOpacity));
  }

  public void rect(float x, float y, float width, float height, int rgb, float opacity) {
    this.canvas.submitRelativeRect(x, y, width, height, this.argb(rgb, opacity));
  }

  public void circle(float x, float y, float radius, int rgb, float opacity) {
    this.canvas.submitCircle(x, y, radius, this.argb(rgb, opacity));
  }

  /**
   * A floppy disk ("save") icon of {@code size} × {@code size}: a square with the top-right
   * corner cut off, a shutter at the top and a label at the bottom. Outlined, or filled (with
   * shutter and label cut out in the panel color) when {@code filled}.
   */
  public void floppy(float x, float y, float size, float lineWidth, int rgb, float opacity,
      boolean filled) {
    int color = this.argb(rgb, opacity);
    float cut = size * 0.28F;
    float right = x + size;
    float bottom = y + size;

    // Shutter (top middle) and label (bottom) positions
    float shutterX = x + size * 0.26F;
    float shutterWidth = size * 0.38F;
    float shutterHeight = size * 0.3F;
    float labelX = x + size * 0.2F;
    float labelWidth = size * 0.6F;
    float labelY = y + size * 0.56F;

    if (filled) {
      this.canvas.submitRelativeRect(x, y, size - cut, size, color);
      this.canvas.submitRelativeRect(right - cut, y + cut, cut, size - cut, color);
      this.canvas.submitTriangle(right - cut, y, right, y + cut, right - cut, y + cut, color);

      int hole = this.argb(PANEL_RGB, opacity);
      this.canvas.submitRelativeRect(shutterX, y, shutterWidth, shutterHeight, hole);
      this.canvas.submitRelativeRect(labelX, labelY, labelWidth, bottom - labelY - lineWidth,
          hole);
      return;
    }

    float half = lineWidth / 2F;
    this.canvas.submitRelativeRect(x, y, size - cut, lineWidth, color);                // top
    this.canvas.submitRelativeRect(x, y, lineWidth, size, color);                      // left
    this.canvas.submitRelativeRect(x, bottom - lineWidth, size, lineWidth, color);     // bottom
    this.canvas.submitRelativeRect(right - lineWidth, y + cut, lineWidth, size - cut, color);
    this.canvas.submitLine(right - cut, y + half, right - half, y + cut, lineWidth, color);

    // Shutter: a small solid block hanging from the top edge
    this.canvas.submitRelativeRect(shutterX, y, shutterWidth, shutterHeight, color);
    // Label: an open box standing on the bottom edge
    this.canvas.submitRelativeRect(labelX, labelY, labelWidth, lineWidth, color);
    this.canvas.submitRelativeRect(labelX, labelY, lineWidth, bottom - labelY, color);
    this.canvas.submitRelativeRect(labelX + labelWidth - lineWidth, labelY, lineWidth,
        bottom - labelY, color);
  }

  /** A circle outline between {@code innerRadius} and {@code outerRadius}. */
  public void ring(float x, float y, float innerRadius, float outerRadius, int rgb,
      float opacity) {
    this.canvas.submitCircle(x, y, innerRadius, outerRadius, this.argb(rgb, opacity));
  }

  public void icon(Icon icon, float x, float y, float size) {
    this.canvas.submitIcon(icon, x, y, size, size, false, this.argb(WHITE, 1F));
  }

  public float width(String text, float scale) {
    return this.canvas.getTextWidth(text) * scale;
  }

  public float lineHeight(float scale) {
    return this.canvas.getLineHeight() * scale;
  }

  public void text(String text, float x, float y, int rgb, float opacity, float scale) {
    // Minecraft treats very low alpha as opaque, so skip nearly invisible text.
    if (opacity * this.alpha < 0.04F) {
      return;
    }
    this.canvas.submitText(text, x, y, this.argb(rgb, opacity), scale, TEXT_FLAGS);
  }

  /** Draws text vertically centered in a row of the given height. */
  public void textCentered(String text, float x, float y, float rowHeight, int rgb, float opacity,
      float scale) {
    float textHeight = this.canvas.getLineHeight() * scale;
    this.text(text, x, y + (rowHeight - textHeight) / 2F + scale * 0.5F, rgb, opacity, scale);
  }

  public static float easeOut(float t) {
    float inverse = 1F - t;
    return 1F - inverse * inverse * inverse;
  }

  public static float easeInOut(float t) {
    return t < 0.5F ? 4F * t * t * t : 1F - (float) Math.pow(-2F * t + 2F, 3) / 2F;
  }

  public static float approach(float value, float target, float step) {
    if (value < target) {
      return Math.min(target, value + step);
    }
    return Math.max(target, value - step);
  }

  public static float lerp(float from, float to, float t) {
    return from + (to - from) * t;
  }
}
