/*
  Copyright 2026 Haoke Tools (浩科工具站), modified build of Stroke Input Method by Conway
  Licensed under the GNU General Public License v3.0 (GPL-3.0-only).
  See LICENSE or <https://www.gnu.org/licenses/>.
*/

package io.github.yawnoc.strokeinput;

import com.k2fsa.sherpa.onnx.OfflineModelConfig;
import com.k2fsa.sherpa.onnx.OfflineSenseVoiceModelConfig;

/* Model files and their separate licence/attribution are bundled in assets/voice. */
final class VoiceModel
{
  static final String NAME = "SenseVoice-Small WSYue int8 (2025-09-09)";
  static final int FEATURE_DIM = 80;

  private VoiceModel()
  {
  }

  static void configure(final OfflineModelConfig config)
  {
    final OfflineSenseVoiceModelConfig senseVoice = new OfflineSenseVoiceModelConfig();
    senseVoice.setModel("voice/model.int8.onnx");
    senseVoice.setLanguage("auto");
    senseVoice.setUseInverseTextNormalization(true);
    config.setSenseVoice(senseVoice);
    config.setTokens("voice/tokens.txt");
  }

  static String tidy(final String text)
  {
    // Only recognised text belongs in the editor, not language/emotion/event tags.
    return text == null ? "" : text.replaceAll("<\\|[^<>]*\\|>", "").trim();
  }
}
