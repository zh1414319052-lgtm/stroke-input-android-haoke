/*
  Copyright 2021, 2023 Conway
  Modified 2026-10-28 by Haoke Tools (浩科工具站): candidate long press and candidate size,
  emphasised first candidate while typing strokes, theme colours.
  Licensed under the GNU General Public License v3.0 (GPL-3.0-only).
  This is free software with NO WARRANTY etc. etc.,
  see LICENSE or <https://www.gnu.org/licenses/>.
*/

package io.github.yawnoc.strokeinput;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.StateListDrawable;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

/*
  An adapter which holds candidate buttons.
*/
public class CandidatesViewAdapter
  extends RecyclerView.Adapter<CandidatesViewAdapter.ButtonHolder>
{
  private CandidateListener candidateListener;
  private final LayoutInflater layoutInflater;
  private final List<String> candidates;
  private final float baseTextSizePx;
  private final int baseHeightPx;
  private float sizeScale = 1f;
  private boolean firstCandidateIsEmphasised = false; // the candidate that space/enter would commit
  private ColorStateList candidateTextColours;
  private ColorStateList firstCandidateTextColours;
  private int candidatesFill;
  private int pressedFill;

  CandidatesViewAdapter(final Context context, final List<String> candidates)
  {
    this.layoutInflater = LayoutInflater.from(context);
    this.candidates = candidates;
    baseTextSizePx = context.getResources().getDimension(R.dimen.font_size__candidate_button);
    baseHeightPx = context.getResources().getDimensionPixelSize(R.dimen.height__candidate_button);
    setThemeColours(KeyboardTheme.forName(KeyboardTheme.DEFAULT));
  }

  private void setThemeColours(final KeyboardTheme theme)
  {
    final int[][] states = {{android.R.attr.state_pressed}, {}};
    candidateTextColours = new ColorStateList(states, new int[]{theme.onAccent, theme.candidateText});
    firstCandidateTextColours = new ColorStateList(states, new int[]{theme.onAccent, theme.firstCandidateText});
    candidatesFill = theme.candidatesFill;
    pressedFill = theme.accent;
  }

  @SuppressLint("NotifyDataSetChanged")
  public void setTheme(final KeyboardTheme theme)
  {
    setThemeColours(theme);
    notifyDataSetChanged();
  }

  /*
    Each button needs its own state drawable (pressed: accent).
  */
  private StateListDrawable makeCandidateBackground()
  {
    final StateListDrawable background = new StateListDrawable();
    background.addState(new int[]{android.R.attr.state_pressed}, new ColorDrawable(pressedFill));
    background.addState(new int[]{}, new ColorDrawable(candidatesFill));
    return background;
  }

  public interface CandidateListener
  {
    void onCandidate(String candidate);
    void onCandidateLongPress(String candidate);
  }

  public int getScaledHeightPx()
  {
    return Math.round(baseHeightPx * sizeScale);
  }

  @SuppressLint("NotifyDataSetChanged")
  public void setSizeScale(final float sizeScale)
  {
    if (this.sizeScale != sizeScale)
    {
      this.sizeScale = sizeScale;
      notifyDataSetChanged();
    }
  }

  public void setCandidateListener(final CandidateListener candidateListener)
  {
    this.candidateListener = candidateListener;
  }

  @SuppressLint("NotifyDataSetChanged")
  public void updateCandidates(final List<String> candidates, final boolean firstCandidateIsEmphasised)
  {
    this.candidates.clear();
    this.candidates.addAll(candidates);
    this.firstCandidateIsEmphasised = firstCandidateIsEmphasised;
    notifyDataSetChanged();
  }

  @NonNull
  @Override
  public ButtonHolder onCreateViewHolder(@NonNull final ViewGroup viewGroup, final int viewType)
  {
    final Button candidateButton = (Button) layoutInflater.inflate(R.layout.candidate_button, viewGroup, false);
    return new ButtonHolder(candidateButton);
  }

  @Override
  public void onBindViewHolder(@NonNull final ButtonHolder buttonHolder, final int candidateIndex)
  {
    final String candidate = candidates.get(candidateIndex);
    final Button candidateButton = buttonHolder.candidateButton;
    candidateButton.setText(candidate);
    candidateButton.setTextSize(TypedValue.COMPLEX_UNIT_PX, baseTextSizePx * sizeScale);
    final boolean isEmphasised = firstCandidateIsEmphasised && candidateIndex == 0;
    candidateButton.setTextColor((isEmphasised) ? firstCandidateTextColours : candidateTextColours);
    candidateButton.setTypeface(Typeface.DEFAULT, (isEmphasised) ? Typeface.BOLD : Typeface.NORMAL);
    candidateButton.setBackground(makeCandidateBackground());

    final ViewGroup.LayoutParams layoutParameters = candidateButton.getLayoutParams();
    final int scaledHeightPx = getScaledHeightPx();
    if (layoutParameters.height != scaledHeightPx)
    {
      layoutParameters.height = scaledHeightPx;
      candidateButton.setLayoutParams(layoutParameters);
    }
  }

  @Override
  public int getItemCount()
  {
    return candidates.size();
  }

  public class ButtonHolder
    extends RecyclerView.ViewHolder
    implements View.OnClickListener, View.OnLongClickListener
  {
    private final Button candidateButton;

    public ButtonHolder(final Button candidateButton)
    {
      super(candidateButton);
      candidateButton.setOnClickListener(this);
      candidateButton.setOnLongClickListener(this);
      this.candidateButton = candidateButton;
    }

    @Override
    public void onClick(View view)
    {
      if (candidateListener != null)
      {
        candidateListener.onCandidate(candidateButton.getText().toString());
      }
    }

    @Override
    public boolean onLongClick(View view)
    {
      if (candidateListener != null)
      {
        candidateListener.onCandidateLongPress(candidateButton.getText().toString());
      }
      return true; // consumed, so the candidate is not also committed
    }
  }
}
