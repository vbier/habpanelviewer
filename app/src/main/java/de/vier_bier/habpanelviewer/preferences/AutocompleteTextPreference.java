package de.vier_bier.habpanelviewer.preferences;

import android.annotation.SuppressLint;
import android.content.Context;
import android.preference.EditTextPreference;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;

/**
 * EditTextPreference with auto completion.
 */
class AutocompleteTextPreference extends EditTextPreference {
    private final AutoCompleteTextView mTextView;

    public AutocompleteTextPreference(Context context, AttributeSet attrs) {
        super(context, attrs);

        mTextView = new AutoCompleteTextView(context, attrs);
    }

    @SuppressLint("MissingSuperCall")
    @Override
    protected void onBindDialogView(View view) {
        EditText editText = mTextView;
        editText.setText(getText());

        ViewParent oldParent = editText.getParent();
        if (oldParent != view) {
            if (oldParent != null) {
                ((ViewGroup) oldParent).removeView(editText);
            }
            onAddEditTextToDialogView(view, editText);
        }
    }

    /**
     * Returns the {@link AutoCompleteTextView} widget that will be shown in the dialog.
     *
     * @return The {@link AutoCompleteTextView} widget that will be shown in the dialog.
     */
    public AutoCompleteTextView getEditText() {
        return mTextView;
    }

    @Override
    protected void onDialogClosed(boolean positiveResult) {
        super.onDialogClosed(positiveResult);

        if (positiveResult) {
            String value = getEditText().getText().toString();
            if (callChangeListener(value)) {
                setText(value);
            }
        }
    }
}
