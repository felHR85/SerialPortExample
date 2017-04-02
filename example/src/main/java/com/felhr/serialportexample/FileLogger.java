package com.felhr.serialportexample;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * A simple class for logging to a file.
 * Each append will open, write, and close the file.
 * The path and filename can be modified and will result in the next append being to the new file.
 */
public class FileLogger {
    private String mFileName;
    private final Object mFileLock = new Object();

    /**
     * Construct a new FileLogger for a file at path/fileName location.
     * @param path Directory which will be created to store the log file.
     * @param baseName Base name of the file which will be prefixed with a timestamp
     */
    public FileLogger(String path, String baseName)
    {
        mFileName = NewFile(path, baseName);
    }

    /**
     * Set a new path and filename to be used by the next append.
     * @param path Directory which will be created to store the log file.
     * @param baseName Base name of the file which will be prefixed with a timestamp
     * @return
     */
    public String NewFile(String path, String baseName)
    {
        synchronized (mFileLock) {
            mFileName = path + "/" + FormattedFileName(baseName);
        }
        return mFileName;
    }

    /**
     * Append the given text to the file.
     * The file is opened, appended to and closed.
     *
     * @param text The text to append to the file.
     */
    public void appendLog(String text)
    {
        synchronized (mFileLock) {
            File logFile = new File(mFileName);
            if (!logFile.exists()) {
                try {
                    logFile.createNewFile();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            try {
                //BufferedWriter for performance, true to set append to file flag
                BufferedWriter buf = new BufferedWriter(new FileWriter(logFile, true));
                buf.append(text);
                buf.flush();
                buf.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
    private String FormattedFileName(String fileName)
    {
        String newName = new SimpleDateFormat("yyyyMMdd_HHmmss-'" + fileName + "'", Locale.US).format(new Date());
        return newName;
    }
}
