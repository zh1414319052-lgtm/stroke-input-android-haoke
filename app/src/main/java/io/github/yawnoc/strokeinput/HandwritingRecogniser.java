/*
  Copyright 2026 Haoke Tools (浩科工具站), modified build of Stroke Input Method by Conway
  Licensed under the GNU General Public License v3.0 (GPL-3.0-only).
  This is free software with NO WARRANTY etc. etc.,
  see LICENSE or <https://www.gnu.org/licenses/>.
*/

package io.github.yawnoc.strokeinput;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;

/*
  Offline handwriting recognition with the PP-OCRv6 rec small model (PaddleOCR, Apache-2.0),
  in the ONNX conversion by RapidOCR (Apache-2.0), run with ONNX Runtime (MIT).
  The ink is drawn into a 48-pixel-high line image, so several characters written in a row are read together.
  Loading and recognition run on one background thread; results come back on the main thread.
*/
public final class HandwritingRecogniser
{
  private static final String LOG_TAG = "HandwritingRecogniser";

  private static final String MODEL_ASSET = "handwriting/PP-OCRv6_rec_small.onnx";
  private static final String DICTIONARY_ASSET = "handwriting/ppocrv6_dict.txt";
  private static final String MODEL_DIRECTORY = "handwriting";

  static final int LINE_HEIGHT = 48;
  static final int MIN_LINE_WIDTH = 320; // PaddleOCR's 48 x 320 input; shorter lines are padded
  private static final float INK_WIDTH_FRACTION_OF_LINE = 0.065f;
  private static final int ALTERNATIVES_PER_CHARACTER = 6;
  private static final int MAX_CANDIDATES = 12;
  private static final int INTRA_OP_THREADS = 2;

  public interface StateListener
  {
    void onRecogniserStateChanged();
  }

  public interface ResultListener
  {
    void onHandwritingRecognised(int inkVersion, List<String> candidates);
  }

  private enum State { LOADING, READY, FAILED }

  private final Context applicationContext;
  private final ExecutorService executor = Executors.newSingleThreadExecutor();
  private final Handler mainHandler = new Handler(Looper.getMainLooper());
  private volatile State state = State.LOADING;

  private OrtEnvironment environment;
  private OrtSession session;
  private String inputName;
  private List<String> labels;

  public HandwritingRecogniser(final Context context, final StateListener stateListener)
  {
    applicationContext = context.getApplicationContext();
    executor.execute(
      () ->
      {
        state = load() ? State.READY : State.FAILED;
        mainHandler.post(stateListener::onRecogniserStateChanged);
      }
    );
  }

  public boolean isReady()
  {
    return state == State.READY;
  }

  public boolean hasFailed()
  {
    return state == State.FAILED;
  }

  private boolean load()
  {
    final long startMilliseconds = System.currentTimeMillis();
    try
    {
      final File modelFile = copyModelToStorage();
      labels = loadLabels();
      environment = OrtEnvironment.getEnvironment();
      environment.setTelemetry(false); // also kept out in AndroidManifest.xml: nothing leaves the device
      final OrtSession.SessionOptions options = new OrtSession.SessionOptions();
      options.setIntraOpNumThreads(INTRA_OP_THREADS);
      options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT);
      session = environment.createSession(modelFile.getAbsolutePath(), options);
      inputName = session.getInputNames().iterator().next();
      Log.i(LOG_TAG, "Loaded handwriting model in " + (System.currentTimeMillis() - startMilliseconds) + " ms");
      return true;
    }
    catch (IOException | OrtException | RuntimeException | UnsatisfiedLinkError exception)
    {
      Log.e(LOG_TAG, "Loading the handwriting model failed", exception);
      return false;
    }
  }

  /*
    ONNX Runtime loads a model from a file, so the (uncompressed) asset is copied once to app storage.
  */
  private File copyModelToStorage() throws IOException
  {
    final File directory = new File(applicationContext.getNoBackupFilesDir(), MODEL_DIRECTORY);
    final File modelFile = new File(directory, new File(MODEL_ASSET).getName());
    final long assetLength;
    try (AssetFileDescriptor descriptor = applicationContext.getAssets().openFd(MODEL_ASSET))
    {
      assetLength = descriptor.getLength();
    }
    if (modelFile.length() == assetLength)
    {
      return modelFile;
    }

    if (!directory.isDirectory() && !directory.mkdirs())
    {
      throw new IOException("Cannot create " + directory);
    }
    final File partialFile = new File(directory, modelFile.getName() + ".part");
    try (
      InputStream inputStream = applicationContext.getAssets().open(MODEL_ASSET);
      OutputStream outputStream = new FileOutputStream(partialFile)
    )
    {
      final byte[] buffer = new byte[1 << 16];
      int length;
      while ((length = inputStream.read(buffer)) != -1)
      {
        outputStream.write(buffer, 0, length);
      }
    }
    if (!partialFile.renameTo(modelFile))
    {
      throw new IOException("Cannot rename " + partialFile);
    }
    return modelFile;
  }

  /*
    Label 0 is the CTC blank, then one label per dictionary line, then the space.
  */
  private List<String> loadLabels() throws IOException
  {
    final List<String> loadedLabels = new ArrayList<>();
    loadedLabels.add("");
    try (
      BufferedReader reader =
              new BufferedReader(
                new InputStreamReader(applicationContext.getAssets().open(DICTIONARY_ASSET), StandardCharsets.UTF_8)
              )
    )
    {
      String line;
      while ((line = reader.readLine()) != null)
      {
        loadedLabels.add(line);
      }
    }
    loadedLabels.add(" ");
    return loadedLabels;
  }

  public void recognise(
    final List<float[]> strokes,
    final float padHeight,
    final int inkVersion,
    final ResultListener resultListener
  )
  {
    executor.execute(
      () ->
      {
        final List<String> candidates = recogniseNow(strokes, padHeight);
        mainHandler.post(() -> resultListener.onHandwritingRecognised(inkVersion, candidates));
      }
    );
  }

  /*
    Recognition on the calling thread (the background thread, or a test); empty if not ready or on error.
  */
  List<String> recogniseNow(final List<float[]> strokes, final float padHeight)
  {
    if (state != State.READY || strokes.isEmpty())
    {
      return Collections.emptyList();
    }

    final HandwritingInk.LineLayout layout = HandwritingInk.lineLayout(strokes, padHeight, LINE_HEIGHT);
    final int tensorWidth = Math.max(MIN_LINE_WIDTH, layout.width);
    final float[] input = lineTensor(strokes, layout, tensorWidth);

    try (
      OnnxTensor inputTensor =
              OnnxTensor.createTensor(environment, FloatBuffer.wrap(input), new long[]{1, 3, LINE_HEIGHT, tensorWidth});
      OrtSession.Result result = session.run(Collections.singletonMap(inputName, inputTensor))
    )
    {
      final OnnxTensor outputTensor = (OnnxTensor) result.get(0);
      final long[] shape = outputTensor.getInfo().getShape(); // [1, time steps, labels]
      if (shape.length != 3 || shape[2] != labels.size())
      {
        Log.e(LOG_TAG, "Unexpected model output shape " + java.util.Arrays.toString(shape));
        return Collections.emptyList();
      }
      final FloatBuffer outputBuffer = outputTensor.getFloatBuffer();
      final float[] probabilities = new float[outputBuffer.remaining()];
      outputBuffer.get(probabilities);
      return HandwritingDecoder.candidates(
        probabilities,
        (int) shape[1],
        labels,
        ALTERNATIVES_PER_CHARACTER,
        MAX_CANDIDATES
      );
    }
    catch (OrtException exception)
    {
      Log.e(LOG_TAG, "Handwriting recognition failed", exception);
      return Collections.emptyList();
    }
  }

  /*
    Black ink on white in a 3 x 48 x width tensor, normalised to -1..1 as in PaddleOCR;
    the area beyond the line image stays 0 (PaddleOCR's padding).
  */
  private static float[] lineTensor(
    final List<float[]> strokes,
    final HandwritingInk.LineLayout layout,
    final int tensorWidth
  )
  {
    final Bitmap bitmap = Bitmap.createBitmap(layout.width, LINE_HEIGHT, Bitmap.Config.ARGB_8888);
    final Canvas canvas = new Canvas(bitmap);
    canvas.drawColor(Color.WHITE);

    final Paint inkPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    inkPaint.setColor(Color.BLACK);
    inkPaint.setStyle(Paint.Style.STROKE);
    inkPaint.setStrokeCap(Paint.Cap.ROUND);
    inkPaint.setStrokeJoin(Paint.Join.ROUND);
    inkPaint.setStrokeWidth(Math.max(2f, INK_WIDTH_FRACTION_OF_LINE * LINE_HEIGHT));

    final Path path = new Path();
    for (final float[] stroke : strokes)
    {
      if (stroke.length < 2)
      {
        continue;
      }
      final float startX = stroke[0] * layout.scale + layout.offsetX;
      final float startY = stroke[1] * layout.scale + layout.offsetY;
      if (stroke.length == 2)
      {
        canvas.drawPoint(startX, startY, inkPaint); // a tap is a dot
        continue;
      }
      path.reset();
      path.moveTo(startX, startY);
      for (int index = 2; index + 1 < stroke.length; index += 2)
      {
        path.lineTo(stroke[index] * layout.scale + layout.offsetX, stroke[index + 1] * layout.scale + layout.offsetY);
      }
      canvas.drawPath(path, inkPaint);
    }

    final int width = layout.width;
    final int[] pixels = new int[width * LINE_HEIGHT];
    bitmap.getPixels(pixels, 0, width, 0, 0, width, LINE_HEIGHT);
    bitmap.recycle();

    final int planeSize = LINE_HEIGHT * tensorWidth;
    final float[] tensor = new float[3 * planeSize];
    for (int y = 0; y < LINE_HEIGHT; y++)
    {
      for (int x = 0; x < width; x++)
      {
        final float value = (pixels[y * width + x] & 0xFF) / 127.5f - 1f;
        final int offset = y * tensorWidth + x;
        tensor[offset] = value;
        tensor[planeSize + offset] = value;
        tensor[2 * planeSize + offset] = value;
      }
    }
    return tensor;
  }

  public void close()
  {
    executor.execute(
      () ->
      {
        try
        {
          if (session != null)
          {
            session.close();
          }
        }
        catch (OrtException exception)
        {
          Log.e(LOG_TAG, "Closing the handwriting model failed", exception);
        }
        state = State.FAILED;
      }
    );
    executor.shutdown();
  }
}
