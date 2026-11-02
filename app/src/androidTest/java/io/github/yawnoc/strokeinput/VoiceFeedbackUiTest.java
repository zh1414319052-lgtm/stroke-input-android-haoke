/*
  Copyright 2026 Haoke Tools (浩科工具站)
  Licensed under GPL-3.0-only; see LICENSE.
*/
package io.github.yawnoc.strokeinput;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.util.Log;

import androidx.appcompat.content.res.AppCompatResources;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/*
  Native Canvas checks with synthetic microphone levels: these are visual evidence, not a
  microphone or recognition test. They do not change saved theme, audio permission or user data.
  Text uses Android's existing open Roboto/Noto platform font family, as the app's status does.
*/
@RunWith(AndroidJUnit4.class)
public class VoiceFeedbackUiTest
{
  private static final String[] THEMES = {KeyboardTheme.PAPER, KeyboardTheme.SKY, KeyboardTheme.NIGHT};

  private static Context context()
  {
    return InstrumentationRegistry.getInstrumentation().getTargetContext();
  }

  private static int px(final float dp)
  {
    return Math.round(dp * context().getResources().getDisplayMetrics().density);
  }

  private static Paint textPaint()
  {
    final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    paint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
    paint.setTextSize(17 * context().getResources().getDisplayMetrics().scaledDensity);
    paint.setTextAlign(Paint.Align.CENTER);
    return paint;
  }

  private static Drawable drawable(final int resource)
  {
    final Drawable icon = AppCompatResources.getDrawable(context(), resource);
    assertNotNull(icon);
    return icon.mutate();
  }

  private static Bitmap render(
    final VoiceFeedbackRenderer renderer, final KeyboardTheme theme, final float level,
    final long frameTime, final boolean animate
  )
  {
    final Bitmap bitmap = Bitmap.createBitmap(px(360), px(270), Bitmap.Config.ARGB_8888);
    final Canvas canvas = new Canvas(bitmap);
    canvas.drawColor(theme.keyboardFill);
    final Paint background = new Paint(Paint.ANTI_ALIAS_FLAG);
    background.setColor(theme.keyStrokeFill);
    final RectF pad = new RectF(px(6), px(6), px(354), px(206));
    canvas.drawRoundRect(pad, px(theme.keyRadiusDp), px(theme.keyRadiusDp), background);
    renderer.draw(canvas, pad, theme.accent, theme.onAccent, theme.keyText, true, level, false,
      context().getString(R.string.status__voice_listening), textPaint(),
      drawable(R.drawable.icon_microphone), frameTime, animate);

    // The same drawable/tint contract used by the keyboard's return-only rendering branch.
    final RectF returnKey = new RectF(px(300), px(212), px(354), px(264));
    background.setColor(theme.keyEnterFill);
    canvas.drawRoundRect(returnKey, px(theme.keyRadiusDp), px(theme.keyRadiusDp), background);
    final Drawable returnIcon = drawable(R.drawable.icon_keyboard_return);
    returnIcon.setTint(theme.enterText);
    returnIcon.setBounds(px(314), px(225), px(340), px(251));
    returnIcon.draw(canvas);
    return bitmap;
  }

  private static int countColour(final Bitmap bitmap, final int colour, final RectF region)
  {
    int count = 0;
    for (int y = Math.round(region.top); y < Math.round(region.bottom); y++)
    {
      for (int x = Math.round(region.left); x < Math.round(region.right); x++)
      {
        if (bitmap.getPixel(x, y) == colour)
        {
          count++;
        }
      }
    }
    return count;
  }

  @Test
  public void allThemes_useTheirColours_andRespectReducedMotion() throws Exception
  {
    final File evidence = new File(context().getCacheDir(), "voice-ui-checks");
    assertTrue(evidence.isDirectory() || evidence.mkdirs());
    try (FileOutputStream note = new FileOutputStream(new File(evidence, "README.txt")))
    {
      note.write(("Synthetic input visual checks. PNGs are native renderer evidence, not a real "
        + "microphone/recognition test or a complete app screenshot. Android Roboto/Noto platform fonts.\n")
        .getBytes(StandardCharsets.UTF_8));
    }

    for (final String name : THEMES)
    {
      final KeyboardTheme theme = KeyboardTheme.forName(name);
      final VoiceFeedbackRenderer renderer = new VoiceFeedbackRenderer(context().getResources());
      final Bitmap silentFirst = render(renderer, theme, 0, 1_000, true);
      final Bitmap silentLater = render(renderer, theme, 0, 1_400, true);
      assertFalse(name + ": recording ring should move even during digital silence",
        silentFirst.sameAs(silentLater));

      renderer.reset();
      final Bitmap reducedFirst = render(renderer, theme, .8f, 2_000, false);
      final Bitmap reducedLater = render(renderer, theme, .8f, 3_000, false);
      assertTrue(name + ": reduced motion must not vary with time",
        reducedFirst.sameAs(reducedLater));
      final Bitmap quiet = render(renderer, theme, 0, 4_000, false);
      assertFalse(name + ": reduced motion must still respond to the supplied audio level",
        reducedFirst.sameAs(quiet));

      final RectF pad = new RectF(px(6), px(6), px(354), px(206));
      assertTrue(name + ": microphone should use the theme accent",
        countColour(reducedFirst, theme.accent, pad) > px(20));
      assertTrue(name + ": microphone symbol should use onAccent",
        countColour(reducedFirst, theme.onAccent, pad) > px(10));
      assertTrue(name + ": return symbol should use the enter text colour",
        countColour(reducedFirst, theme.enterText, new RectF(px(310), px(220), px(344), px(256))) > px(10));
      try (FileOutputStream output = new FileOutputStream(new File(evidence, name.toLowerCase(Locale.ROOT) + "-synthetic.png")))
      {
        assertTrue(reducedFirst.compress(Bitmap.CompressFormat.PNG, 100, output));
      }
      Log.i("VoiceFeedbackUiTest", name + ": colours, moving recording ring, reduced motion and audio response PASS");
      silentFirst.recycle();
      silentLater.recycle();
      reducedFirst.recycle();
      reducedLater.recycle();
      quiet.recycle();
    }
    Log.i("VoiceFeedbackUiTest", "Synthetic native visual evidence: " + evidence.getAbsolutePath());
  }

  @Test
  public void narrowAndShortPad_doesNotDrawOutsideItsBounds()
  {
    final Bitmap bitmap = Bitmap.createBitmap(px(240), px(180), Bitmap.Config.ARGB_8888);
    final Canvas canvas = new Canvas(bitmap);
    canvas.drawColor(Color.MAGENTA);
    final RectF pad = new RectF(px(30), px(25), px(190), px(145));
    final KeyboardTheme theme = KeyboardTheme.forName(KeyboardTheme.NIGHT);
    final VoiceFeedbackRenderer renderer = new VoiceFeedbackRenderer(context().getResources());
    renderer.draw(canvas, pad, theme.accent, theme.onAccent, theme.keyText, true, 1, false,
      "Listening… tap again to finish", textPaint(), drawable(R.drawable.icon_microphone), 1_000, false);
    for (int y = 0; y < bitmap.getHeight(); y++)
    {
      for (int x = 0; x < bitmap.getWidth(); x++)
      {
        if (!pad.contains(x + .5f, y + .5f))
        {
          assertTrue("drawing escaped pad at " + x + "," + y, bitmap.getPixel(x, y) == Color.MAGENTA);
        }
      }
    }
    assertTrue("the constrained renderer should still paint inside the pad",
      countColour(bitmap, theme.accent, pad) > 0);
    bitmap.recycle();
  }
}
