package com.myhome.application.snowboydemo;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public final class SnowboyUtils {
    private SnowboyUtils() {
        // Empty private constructor
    }

    public static File getSnowboyDirectory(Context context) {
        File snowboyDirectory = new File(context.getFilesDir().getPath(),"lin2021");
//        File snowboyDirectory = new File(Environment.getDataDirectory().getPath(),"lin2021");
        if (!snowboyDirectory.exists()){
            boolean wassucc = snowboyDirectory.mkdirs();
            if (!wassucc) {
                System.out.println("was not successful.");
            }
        }
        snowboyDirectory.mkdir();
        return snowboyDirectory;
    }

    public static void copyAssets(Context context) {
        try {

            File snowboyDirectory = getSnowboyDirectory(context);
//
            String[] paths = context.getAssets().list("snowboy");
            for (String path : paths) {
                File file = new File(snowboyDirectory, path);
                InputStream inputStream = context.getAssets().open("snowboy/" + path);

                OutputStream outputStream = new FileOutputStream(file);

                byte[] buffer = new byte[1024];
                int readSize = inputStream.read(buffer, 0, 1024);
                while (readSize > 0) {
                    outputStream.write(buffer, 0, readSize);
                    readSize = inputStream.read(buffer, 0, 1024);
                }

                inputStream.close();
                outputStream.close();
            }
        } catch (IOException e) {
            Log.e("hotword", e.getMessage(), e);
        }
    }
}
