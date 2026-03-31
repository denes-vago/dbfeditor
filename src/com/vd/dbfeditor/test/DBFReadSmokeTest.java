package com.vd.dbfeditor.test;

import com.vd.dbfeditor.dbf.DBFEngine;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;

public class DBFReadSmokeTest {
    public static void main(String[] args) {
        if (args.length < 1 || args.length > 2) {
            printUsage();
            System.exit(1);
        }

        Charset charset = DBFEngine.DEFAULT_CHARSET;
        if (args.length == 2) {
            try {
                charset = Charset.forName(args[1]);
            } catch (Exception e) {
                System.err.println("Ismeretlen karakterkódolás: " + args[1]);
                System.exit(1);
            }
        }

        Path dbfPath = Path.of(args[0]);
        if (!Files.isRegularFile(dbfPath)) {
            System.err.println("A fájl nem található: " + dbfPath);
            System.exit(1);
        }

        try {
            DBFEngine.read(dbfPath, charset);
        } catch (IOException e) {
            System.err.println("Hiba a fájl olvasása közben: " + e.getMessage());
            System.exit(1);
        }
    }

    private static void printUsage() {
        System.out.println("Használat: java -cp build com.vd.dbfeditor.DBFReadSmokeTest <fájl.dbf> [karakterkódolás]");
        System.out.println("Példa:    java -cp build com.vd.dbfeditor.DBFReadSmokeTest ugyfelek.dbf IBM852");
        System.out.println("Alapértelmezett karakterkódolás: " + DBFEngine.DEFAULT_CHARSET.displayName());
    }
}
