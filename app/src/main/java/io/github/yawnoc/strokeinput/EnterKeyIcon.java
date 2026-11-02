/*
  Copyright 2026 Haoke Tools (浩科工具站)
  Licensed under GPL-3.0-only; see LICENSE.
*/
package io.github.yawnoc.strokeinput;

import android.view.inputmethod.EditorInfo;

/*
  Semantic state of the shared ENTER key, resolved from the same editor state as
  the existing action dispatch. The renderer always uses the user's filled return
  arrow; these states retain the original labels and never select another shape.
*/
enum EnterKeyIcon
{
  RETURN(R.string.display_text__return),
  DONE(R.string.display_text__done),
  GO(R.string.display_text__go),
  NEXT(R.string.display_text__next),
  PREVIOUS(R.string.display_text__previous),
  SEARCH(R.string.display_text__search),
  SEND(R.string.display_text__send);

  final int displayTextResource;

  EnterKeyIcon(final int displayTextResource)
  {
    this.displayTextResource = displayTextResource;
  }

  static EnterKeyIcon forEditorAction(final int action, final boolean hasAction)
  {
    if (!hasAction)
    {
      return RETURN;
    }
    return switch (action & EditorInfo.IME_MASK_ACTION)
    {
      case EditorInfo.IME_ACTION_DONE -> DONE;
      case EditorInfo.IME_ACTION_GO -> GO;
      case EditorInfo.IME_ACTION_NEXT -> NEXT;
      case EditorInfo.IME_ACTION_PREVIOUS -> PREVIOUS;
      case EditorInfo.IME_ACTION_SEARCH -> SEARCH;
      case EditorInfo.IME_ACTION_SEND -> SEND;
      default -> RETURN;
    };
  }
}
