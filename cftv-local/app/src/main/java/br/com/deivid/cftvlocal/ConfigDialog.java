package br.com.deivid.cftvlocal;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.Locale;

final class ConfigDialog {
    interface OnSaved { void onSaved(String[] urls); }

    private ConfigDialog() {}

    static void show(Activity activity, String[] current, OnSaved callback) {
        ScrollView scroll = new ScrollView(activity);
        LinearLayout form = new LinearLayout(activity);
        form.setOrientation(LinearLayout.VERTICAL);
        int p = dp(activity, 18);
        form.setPadding(p, p, p, p);
        scroll.addView(form);

        TextView note = new TextView(activity);
        note.setText("URLs RTSP das 4 câmeras. Credenciais são armazenadas criptografadas no tablet. O P2P é configurado separadamente via provisionamento seguro.");
        note.setTextColor(Color.DKGRAY);
        note.setTextSize(14);
        form.addView(note);

        EditText[] fields = new EditText[4];
        for (int i = 0; i < 4; i++) {
            TextView label = new TextView(activity);
            label.setText("Câmera " + (i + 1));
            label.setTypeface(Typeface.DEFAULT_BOLD);
            label.setPadding(0, dp(activity, 14), 0, dp(activity, 4));
            form.addView(label);

            fields[i] = new EditText(activity);
            fields[i].setSingleLine(true);
            fields[i].setText(current[i] == null ? "" : current[i]);
            fields[i].setHint("rtsp://usuario:senha@192.168.x.x:554/...");
            form.addView(fields[i], new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("Configuração local")
                .setView(scroll)
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Salvar", null)
                .create();

        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String[] values = new String[4];
            boolean valid = true;
            for (int i = 0; i < 4; i++) {
                values[i] = fields[i].getText().toString().trim();
                if (!values[i].toLowerCase(Locale.ROOT).startsWith("rtsp://")) {
                    fields[i].setError("Informe uma URL RTSP válida");
                    valid = false;
                }
            }
            if (!valid) return;
            callback.onSaved(values);
            dialog.dismiss();
        }));
        dialog.show();
    }

    private static int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
