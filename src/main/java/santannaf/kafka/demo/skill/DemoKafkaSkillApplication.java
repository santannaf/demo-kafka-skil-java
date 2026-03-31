package santannaf.kafka.demo.skill;

import com.tanna.annotation.EnabledArchKafka;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@EnabledArchKafka(appName = "demo-kafka-skill")
public class DemoKafkaSkillApplication {
    static void main(String[] args) {
        SpringApplication.run(DemoKafkaSkillApplication.class, args);
    }
}
