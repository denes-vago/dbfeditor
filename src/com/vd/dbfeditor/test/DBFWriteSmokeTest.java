package com.vd.dbfeditor.test;

import com.vd.dbfeditor.DBFEngine;
import java.nio.charset.Charset;
import java.nio.file.Path;

public class DBFWriteSmokeTest {
    public static void main(String[] args) throws Exception {
        Charset charset = Charset.forName("ISO-8859-1");
        Path source = Path.of("IR_SZAM.DBF");
        Path target = Path.of("IR_SZAM_test.DBF");

        DBFEngine.DBFFile dbf = DBFEngine.read(source, charset);
        dbf.records().get(0).set(1, "TESZT");
        DBFEngine.write(target, charset, dbf);

        DBFEngine.DBFFile saved = DBFEngine.read(target, charset);
        System.out.println(saved.records().get(0).get(1));
        System.out.println(saved.records().size());
        System.out.println(saved.lastUpdate());
    }
}
