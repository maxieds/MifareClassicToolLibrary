package com.maxieds.MifareClassicToolLibrary;

import android.text.format.Time;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Random;

public class MCTUtils {

    private static final String TAG = MCTUtils.class.getSimpleName();

    public static String BytesToHexString(byte[] bytes) {
        StringBuilder ret = new StringBuilder();
        if (bytes != null) {
            for (Byte b : bytes) {
                ret.append(String.format("%02X", b.intValue() & 0xFF));
            }
        }
        return ret.toString();
    }

    public static byte[] HexStringToBytes(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        try {
            for (int i = 0; i < len; i += 2) {
                data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                                     + Character.digit(s.charAt(i+1), 16));
            }
        } catch (Exception e) {
            Log.d(TAG, "Argument(s) for hexStringToByteArray(String s)"
                    + "was not a hex string");
        }
        return data;
    }

    public static boolean IsHexAnd16Byte(String hexString) {
        if (!hexString.matches("[0-9A-Fa-f]+")) { // Error, not hex.
            return false;
        }
        else if (hexString.length() != 32) { // Error, not 16 byte (32 chars).
            return false;
        }
        return true;
    }

    public static boolean IsHexAnd6Byte(String hexString) {
        if (!hexString.matches("[0-9A-Fa-f]+")) { // Error, not hex.
            return false;
        }
        else if (hexString.length() != 12) { // Error, not 6 byte (12 chars).
            return false;
        }
        return true;
    }

    public static byte[] GetRandomBytes(int numBytes) {
        if(numBytes <= 0) {
            return null;
        }
        Random rnGen = new Random(System.currentTimeMillis());
        byte[] randomBytes = new byte[numBytes];
        for(int b = 0; b < numBytes; b++) {
            randomBytes[b] = (byte) rnGen.nextInt(0xff);
        }
        return randomBytes;
    }

    public static String[] ReadKeysFromTextFile(InputStream keyDataStream) {
        if(keyDataStream == null) {
            return null;
        }
        List<String> keysList = new ArrayList<String>();
        String textLine;
        try {
            InputStreamReader keyInputStreamReader = new InputStreamReader(keyDataStream);
            BufferedReader textFileReader = new BufferedReader(keyInputStreamReader);
            while (true) {
                textLine = textFileReader.readLine();
                if (textLine == null) {
                    break;
                }
                else if(textLine.length() == 0) {
                     continue;
                }
                else if(textLine.charAt(0) != '#' && textLine.charAt(0) != '\n') {
                     Log.i(TAG, textLine);
                     keysList.add(textLine);
                }
            }
            textFileReader.close();
            keyInputStreamReader.close();
            keyDataStream.close();
        } catch(IOException ioe) {
            ioe.printStackTrace();
            return null;
        }
        String[] keysStringArray = new String[keysList.size()];
        for(int s = 0; s < keysList.size(); s++) {
            keysStringArray[s] = keysList.get(s);
        }
        return keysStringArray;
    }

    public static String GetTimestamp() {
        Time currentTime = new Time();
        currentTime.setToNow();
        return currentTime.format("%Y-%m-%d @ %T %p");
    }

    public static class DiffTimeTimer {

        private long localStartTime;
        private long localEndTime;

        public DiffTimeTimer() {
            localStartTime = localEndTime = 0;
        }

        public static long getTimeNowMillis() {
             return Calendar.getInstance().getTime().getTime();
        }

        public void startTimer() {
            localStartTime = getTimeNowMillis();
        }

        public void endTimer() {
            localEndTime = getTimeNowMillis();
        }

        public long diffTimer() {
             return getTimeNowMillis() - localStartTime;
        }

        public long getDiffTimeMillis() {
             return localEndTime - localStartTime;
        }

    }

}