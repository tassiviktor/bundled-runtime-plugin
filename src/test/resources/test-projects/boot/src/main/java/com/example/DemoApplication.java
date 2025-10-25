package com.example;

import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.*;
import javax.xml.parsers.DocumentBuilderFactory;

@SpringBootApplication
public class DemoApplication implements CommandLineRunner {
    public static void main(String[] args) {
        SpringApplication.exit(SpringApplication.run(DemoApplication.class, args));
    }
    @Override public void run(String... args) throws Exception {
        DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(new java.io.ByteArrayInputStream("<b/>".getBytes()));
        System.out.println("OK_BOOT");
    }
}
