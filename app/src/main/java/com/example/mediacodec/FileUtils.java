package com.example.mediacodec;

import android.os.Environment;
import android.util.Log;

import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;

public class FileUtils {

    public static void writeBytes(String fileName, byte[] array) {
        FileOutputStream writer = null;
        try {
            //ture表示以追加方式写文件
//            writer = new FileOutputStream(Environment.getExternalStorageState() + "/codec.h264", true);
            writer = new FileOutputStream(fileName, true);

            writer.write(array);
            writer.write('\n');
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (writer != null) {
                    writer.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static void writeContent(String fileName, byte[] array) {
        char[] HEX_CHAR_TABLE = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'};
        StringBuilder sb = new StringBuilder();

        for (byte b : array) {
            sb.append(HEX_CHAR_TABLE[(b & 0xf0) >> 4]);
            sb.append(HEX_CHAR_TABLE[b & 0x0f]);
        }

        Log.i("wirte--->", "writContent:" + sb.toString());
        FileWriter writer = null;
        try {
//            writer = new FileWriter(Environment.getExternalStorageState() + "/codecH264.txt", true);
            writer = new FileWriter(fileName, true);

            writer.write(sb.toString());
            writer.write('\n');

        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (writer != null) {
                    writer.close();
                }

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}

