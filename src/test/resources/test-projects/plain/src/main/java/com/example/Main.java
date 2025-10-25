package com.example;

import java.util.logging.Logger;
import java.lang.management.ManagementFactory;
import javax.xml.parsers.DocumentBuilderFactory;

/** Minimal app that exercises a few JDK modules so jdeps has something to do. */
public class Main {
    public static void main(String[] args) throws Exception {
        Logger.getLogger("test").info("starting");
        String name = ManagementFactory.getRuntimeMXBean().getName();
        var dbf = DocumentBuilderFactory.newInstance();
        var db = dbf.newDocumentBuilder();
        db.parse(new java.io.ByteArrayInputStream("<a/>".getBytes()));
        System.out.println("OK:" + name);
    }
}
