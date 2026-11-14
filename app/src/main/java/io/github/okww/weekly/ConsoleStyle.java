package io.github.okww.weekly;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.widget.Button;

final class ConsoleStyle {
    static final int BG = Color.rgb(23, 26, 31), SIDE = Color.rgb(29, 33, 40);
    static final int CARD = Color.rgb(34, 39, 47), BORDER = Color.rgb(54, 62, 73);
    static final int TEXT = Color.rgb(239, 243, 248), MUTED = Color.rgb(158, 171, 189);
    static final int ACCENT = Color.rgb(44, 220, 228), ACCENT_BG = Color.rgb(29, 63, 69);
    static final int RED = Color.rgb(255, 145, 148);

    static int dp(Context context, int value) { return Math.round(value * context.getResources().getDisplayMetrics().density); }

    static GradientDrawable surface(Context context, int color, int radius, boolean border) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(color);
        background.setCornerRadius(dp(context, radius));
        if (border) background.setStroke(dp(context, 1), BORDER);
        return background;
    }

    static void button(Button button, boolean primary) {
        Context context = button.getContext();
        button.setAllCaps(false);
        button.setTextSize(13);
        button.setTextColor(primary ? BG : TEXT);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setMinHeight(dp(context, 46));
        button.setMinimumHeight(dp(context, 46));
        button.setPadding(dp(context, 14), dp(context, 5), dp(context, 14), dp(context, 5));
        button.setBackground(new RippleDrawable(ColorStateList.valueOf(0x30ffffff),
                surface(context, primary ? ACCENT : CARD, 9, !primary), null));
    }
}
