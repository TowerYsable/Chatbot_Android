package com.myhome.application.tools;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class calculateDb {
// 计算波形用的分贝
    public static double calculateDb2(short[] buffer) {
        double energy = 0.0;
        for (short value : buffer) {
            energy += value * value;
        }
        energy /= buffer.length;
        energy = (10 * Math.log10(1 + energy)) / 100;
        energy = Math.min(energy, 1.0);
        return energy;
    }

    public static String assetFilePath(Context context, String assetName) {
        File file = new File(context.getFilesDir(), assetName);
        if (file.exists() && file.length() > 0) {
            return file.getAbsolutePath();
        }
        try (InputStream is = context.getAssets().open(assetName)) {
            try (OutputStream os = new FileOutputStream(file)) {
                byte[] buffer = new byte[4 * 1024];
                int read;
                while ((read = is.read(buffer)) != -1) {
                    os.write(buffer, 0, read);
                }
                os.flush();
            }
            return file.getAbsolutePath();
        } catch (IOException e) {
            Log.e("error", "Error process asset " + assetName + " to file path");
        }
        return null;
    }
}
