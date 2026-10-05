package online.wanan.xingchen;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class XingChenApplication {
    public static void main(String[] args) { SpringApplication.run(XingChenApplication.class, args); }
}
