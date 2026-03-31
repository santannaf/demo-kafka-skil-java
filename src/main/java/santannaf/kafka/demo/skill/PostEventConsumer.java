package santannaf.kafka.demo.skill;

import org.apache.avro.generic.GenericRecord;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
public class PostEventConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(PostEventConsumer.class);

    @KafkaListener(topics = "${kafka.topic.posts}", groupId = "${kafka.arch.consumer.consumer-group-id}")
    public void onMessage(ConsumerRecord<String, GenericRecord> event, Acknowledgment ack) {
        try {
            var record = event.value();
            LOG.info("[PostEventConsumer] Event received: id={}, title={}, userId={}, body={}",
                    record.get("id"),
                    record.get("title"),
                    record.get("userId"),
                    record.get("body"));
            ack.acknowledge();
        } catch (Exception e) {
            LOG.error("[PostEventConsumer] Error processing event: {}", e.getMessage());
        }
    }
}
