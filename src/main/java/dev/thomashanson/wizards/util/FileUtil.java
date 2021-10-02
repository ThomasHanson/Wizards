package dev.thomashanson.wizards.util;

import java.io.*;

public class FileUtil {

    public static void copy(File src, File dest) throws IOException {

        if (src.isDirectory()) {

            if (!dest.exists())
                dest.mkdir();

            String[] files = src.list();

            if (files == null)
                return;

            for (String file : files) {

                File newSrc = new File(src, file);
                File newDest = new File(dest, file);

                copy(newSrc, newDest);
            }

        } else {

            InputStream in = new FileInputStream(src);
            OutputStream out = new FileOutputStream(dest);

            byte[] buffer = new byte[1024];
            int length;

            // copy the file content in bytes
            while ((length = in.read(buffer)) > 0)
                out.write(buffer, 0, length);

            in.close();
            out.close();
        }
    }

    public static void delete(File file) {

        if (file.isDirectory()) {

            File[] files = file.listFiles();

            if (files == null)
                return;

            for (File child : files)
                delete(child);
        }

        file.delete();
    }
}