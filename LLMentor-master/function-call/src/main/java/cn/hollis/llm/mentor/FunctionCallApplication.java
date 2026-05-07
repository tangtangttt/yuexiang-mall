package cn.hollis.llm.mentor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class FunctionCallApplication {

    public static void main(String[] args) {
        SpringApplication.run(FunctionCallApplication.class, args);
    }

}
