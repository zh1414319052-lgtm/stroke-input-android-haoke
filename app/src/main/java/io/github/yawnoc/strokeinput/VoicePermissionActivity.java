/*
  Copyright 2026 Haoke Tools (浩科工具站), modified build of Stroke Input Method by Conway
  Licensed under the GNU General Public License v3.0 (GPL-3.0-only).
  This is free software with NO WARRANTY etc. etc.,
  see LICENSE or <https://www.gnu.org/licenses/>.
*/

package io.github.yawnoc.strokeinput;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.NonNull;

/*
  An input method cannot show the microphone permission dialog itself,
  so this see-through activity asks for it when voice input is first used, then closes.
*/
public class VoicePermissionActivity
  extends Activity
{
  private static final int REQUEST_CODE = 1;

  @Override
  protected void attachBaseContext(final Context baseContext)
  {
    super.attachBaseContext(AppLanguage.wrap(baseContext));
  }

  @Override
  protected void onCreate(final Bundle savedInstanceState)
  {
    super.onCreate(savedInstanceState);
    if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
    {
      finish();
      return;
    }
    if (savedInstanceState == null)
    {
      requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_CODE);
    }
  }

  @Override
  public void onRequestPermissionsResult(
    final int requestCode,
    @NonNull final String[] permissions,
    @NonNull final int[] grantResults
  )
  {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    if (requestCode != REQUEST_CODE)
    {
      return;
    }
    final boolean isGranted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
    Toast.makeText(
      this,
      (isGranted) ? R.string.message__voice_permission_granted : R.string.message__voice_permission_denied,
      Toast.LENGTH_LONG
    ).show();
    finish();
  }
}
