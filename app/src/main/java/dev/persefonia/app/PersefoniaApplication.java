package dev.persefonia.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.actuate.autoconfigure.web.ManagementContextConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

@SpringBootApplication
@ComponentScan(
        basePackages = "dev.persefonia",
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ANNOTATION,
                classes = ManagementContextConfiguration.class))
public class PersefoniaApplication {
    public static void main(String[] args) {
        SpringApplication.run(PersefoniaApplication.class, args);
    }
}
