package santannaf.kafka.demo.skill;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/posts")
public class PostController {

    private final PostKafkaProducer producer;

    public PostController(PostKafkaProducer producer) {
        this.producer = producer;
    }

    @PostMapping
    public ResponseEntity<String> publish(@RequestBody Post post) {
        producer.sendEvent(post);
        return ResponseEntity.accepted().body("Event sent to Kafka");
    }
}
