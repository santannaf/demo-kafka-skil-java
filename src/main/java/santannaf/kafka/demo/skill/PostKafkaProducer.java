package santannaf.kafka.demo.skill;

import org.apache.avro.Schema;
import org.apache.avro.SchemaBuilder;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class PostKafkaProducer {

    private static final Logger LOG = LoggerFactory.getLogger(PostKafkaProducer.class);

    private static final Schema POST_SCHEMA = SchemaBuilder.record("Post")
            .namespace("santannaf.kafka.demo.skill")
            .fields()
            .requiredLong("id")
            .requiredString("title")
            .requiredString("userId")
            .requiredString("body")
            .endRecord();

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final String topic;

    public PostKafkaProducer(KafkaTemplate<String, Object> kafkaTemplate,
                             @Value("${kafka.topic.posts}") String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
    }

    public void sendEvent(Post post) {
        GenericRecord event = toRecord(post);

        kafkaTemplate.send(topic, event).handle((_, error) -> {
            if (error != null) {
                LOG.error("Error publishing event: {}", error.getMessage());
            } else {
                LOG.info("Event sent successfully: id={}", post.id());
            }
            return null;
        });
    }

    private GenericRecord toRecord(Post post) {
        GenericRecord record = new GenericData.Record(POST_SCHEMA);
        record.put("id", post.id());
        record.put("title", post.title());
        record.put("userId", post.userId());
        record.put("body", post.body());
        return record;
    }
}
