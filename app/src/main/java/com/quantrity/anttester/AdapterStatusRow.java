package com.quantrity.anttester;

import android.content.Context;
import android.text.Layout;
import android.util.AttributeSet;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class AdapterStatusRow extends LinearLayout {
    private TextView label;
    private TextView value;
    private int valueLeftPadding;
    private int valueRightPadding;

    public AdapterStatusRow(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override protected void onFinishInflate() {
        super.onFinishInflate();
        label = (TextView) getChildAt(0);
        value = (TextView) getChildAt(1);
        valueLeftPadding = value.getPaddingLeft();
        valueRightPadding = value.getPaddingRight();
    }

    @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        float labelWidth = desiredWidth(label) + label.getPaddingLeft() + label.getPaddingRight();
        float valueWidth = desiredWidth(value) + valueLeftPadding + valueRightPadding;
        int available = MeasureSpec.getSize(widthMeasureSpec) - getPaddingLeft() - getPaddingRight();
        boolean stack = MeasureSpec.getMode(widthMeasureSpec) != MeasureSpec.UNSPECIFIED
                && Math.ceil(labelWidth) + Math.ceil(valueWidth) > available;
        int orientation = stack ? VERTICAL : HORIZONTAL;
        if (getOrientation() != orientation) {
            setOrientation(orientation);
            LayoutParams params = (LayoutParams) value.getLayoutParams();
            params.width = stack ? LayoutParams.MATCH_PARENT : 0;
            params.weight = stack ? 0 : 1;
            value.setGravity(stack ? Gravity.LEFT : Gravity.RIGHT);
            value.setPadding(stack ? 0 : valueLeftPadding, value.getPaddingTop(),
                    stack ? 0 : valueRightPadding, value.getPaddingBottom());
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    private static float desiredWidth(TextView view) {
        return Layout.getDesiredWidth(view.getText(), view.getPaint());
    }
}
