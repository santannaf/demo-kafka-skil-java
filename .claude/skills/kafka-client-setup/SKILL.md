---
name: kafka-client-setup
description: Guide for implementing the com.tanna.spring:kafka library in Spring Boot 4.x client projects (Java/Kotlin). Covers dependency setup, configuration, producer/consumer examples, SSL, batch, secondary connections, and local Docker environment.
user_invocable: true
---

# Kafka Library Client Setup Guide

You are helping the user set up a client project that uses the `com.tanna.spring:kafka` library. This library wraps Spring Kafka with opinionated defaults: Avro serialization via Confluent Schema Registry, SSL/TLS support, custom error handling, batch listeners, and flexible ACK modes.

**Spring Boot 4.x / Java 21+ required.**

---

## 1. Dependencies

### Gradle (Groovy - Java)
```groovy
implementation 'com.tanna.spring:kafka:1.0.0'
implementation 'org.apache.avro:avro:1.12.1'
```

### Gradle (Kotlin DSL)
```kotlin
implementation("com.tanna.spring:kafka:1.0.0")
implementation("org.apache.avro:avro:1.12.1")
```

### Maven
```xml
<dependency>
    <groupId>com.tanna.spring</groupId>
    <artifactId>kafka</artifactId>
    <version>1.0.0</version>
</dependency>

<dependency>
<groupId>org.apache.avro</groupId>
<artifactId>avro</artifactId>
<version>1.12.1</version>
</dependency>
```

---

## 2. Local Docker Environment

Create a `docker-compose.kafka.yaml` file in the project root to spin up Kafka, Zookeeper, Schema Registry, and Control Center locally:

```yaml
name: 'env-kafka'

services:
  zookeeper:
    image: confluentinc/cp-zookeeper:7.4.0
    platform: linux/amd64
    container_name: zookeeper
    environment:
      ZOOKEEPER_CLIENT_PORT: 2181
      ZOOKEEPER_TICK_TIME: 2000

  kafka:
    image: confluentinc/cp-kafka:7.4.0
    platform: linux/amd64
    container_name: kafka
    depends_on:
      - zookeeper
    ports:
      - "9092:9092"
      - "29092:29092"
    environment:
      KAFKA_BROKER_ID: 1
      KAFKA_ADVERTISED_HOST_NAME: localhost
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka:9092,PLAINTEXT_HOST://localhost:29092
      KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: PLAINTEXT:PLAINTEXT,PLAINTEXT_HOST:PLAINTEXT
      KAFKA_INTER_BROKER_LISTENER_NAME: PLAINTEXT
      KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
      KAFKA_AUTO_CREATE_TOPICS_ENABLE: "true"
      KAFKA_LOG4J_LOGGERS: "kafka.controller=INFO,kafka.producer.async.DefaultEventHandler=INFO,state.change.logger=INFO"
      KAFKA_GROUP_INITIAL_REBALANCE_DELAY_MS: 100
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
      KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR: 1
      KAFKA_TRANSACTION_STATE_LOG_MIN_ISR: 1
      KAFKA_AUTHORIZER_CLASS_NAME: kafka.security.authorizer.AclAuthorizer
      KAFKA_ALLOW_EVERYONE_IF_NO_ACL_FOUND: "true"
      CONFLUENT_METRICS_ENABLE: 'true'
    extra_hosts:
      - "host.docker.internal:172.17.0.1"

  schema-registry:
    image: confluentinc/cp-schema-registry:7.4.0
    platform: linux/amd64
    container_name: schema-registry
    ports:
      - "8081:8081"
    depends_on:
      - zookeeper
      - kafka
    environment:
      SCHEMA_REGISTRY_HOST_NAME: schema-registry
      SCHEMA_REGISTRY_KAFKASTORE_BOOTSTRAP_SERVERS: 'PLAINTEXT://kafka:9092'
      SCHEMA_REGISTRY_LISTENERS: http://0.0.0.0:8081

  control-center:
    image: confluentinc/cp-enterprise-control-center:6.0.1
    platform: linux/amd64
    hostname: control-center
    container_name: control-center
    depends_on:
      - kafka
      - schema-registry
    ports:
      - "9021:9021"
    environment:
      CONTROL_CENTER_BOOTSTRAP_SERVERS: 'kafka:9092'
      CONTROL_CENTER_REPLICATION_FACTOR: 1
      CONTROL_CENTER_SCHEMA_REGISTRY_URL: http://schema-registry:8081
      CONFLUENT_METRICS_TOPIC_REPLICATION: 1
      CONTROL_CENTER_COMMAND_TOPIC_REPLICATION: 1
      CONTROL_CENTER_MONITORING_INTERCEPTOR_TOPIC_REPLICATION: 1
      CONTROL_CENTER_INTERNAL_TOPICS_PARTITIONS: 1
      CONTROL_CENTER_INTERNAL_TOPICS_REPLICATION: 1
      CONTROL_CENTER_MONITORING_INTERCEPTOR_TOPIC_PARTITIONS: 1
      CONTROL_CENTER_STREAMS_NUM_STREAM_THREADS: 1
      CONTROL_CENTER_MODE_ENABLE: 'all'
      CONTROL_CENTER_CONSUMERS_VIEW_ENABLE: 'true'
      CONTROL_CENTER_STREAMS_CACHE_MAX_BYTES_BUFFERING: 104857600
      PORT: 9021
    command:
      - bash
      - -c
      - |
        echo "Waiting two minutes for Kafka brokers to start and
               necessary topics to be available"
        sleep 60
        /etc/confluent/docker/run
    extra_hosts:
      - "host.docker.internal:172.17.0.1"
```

### Starting the environment
```bash
docker compose -f docker-compose.kafka.yaml up -d
```

### Stopping the environment
```bash
docker compose -f docker-compose.kafka.yaml down
```

### Available endpoints after startup

| Service                 | URL                     | Description                       |
|-------------------------|-------------------------|-----------------------------------|
| Kafka Broker (internal) | `kafka:9092`            | For inter-container communication |
| Kafka Broker (host)     | `localhost:29092`       | For application running on host   |
| Schema Registry         | `http://localhost:8081` | Avro schema management            |
| Control Center          | `http://localhost:9021` | Kafka cluster monitoring UI       |

> **Note:** Use `localhost:29092` as `bootstrap-servers` in `application.properties`/`application.yaml` when running the application on the host machine. The `kafka:9092` address is for inter-container communication only.

---

## 3. Activation

The client project MUST annotate a configuration class (or the main application class) with `@EnabledArchKafka` to activate the library:

### Java
```java
import com.tanna.annotation.EnabledArchKafka;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@EnabledArchKafka(appName = "my-service")
public class MyApplication {
    public static void main(String[] args) {
        SpringApplication.run(MyApplication.class, args);
    }
}
```

### Kotlin
```kotlin
import com.tanna.annotation.EnabledArchKafka
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
@EnabledArchKafka(appName = "my-service")
class MyApplication

fun main(args: Array<String>) {
    runApplication<MyApplication>(*args)
}
```

---

## 4. Configuration Properties

All properties are prefixed with `kafka.arch`.

### 4.1 Basic Example (without SSL)

#### application.properties
```properties
kafka.arch.common.bootstrap-servers=localhost:29092
kafka.arch.common.schema-registry=http://localhost:8081
kafka.arch.common.enable-connection-ssl-protocol-mode=false

kafka.arch.producer.ack-producer-config=all
kafka.arch.producer.compress-type=snappy
kafka.arch.producer.type-partitioner=UniformStickyPartitioner

kafka.arch.consumer.consumer-group-id=my-consumer-group
kafka.arch.consumer.ack-consumer-config=manual
kafka.arch.consumer.event-auto-offset-reset-config=latest
kafka.arch.consumer.enable-avro-reader-config=false
```

#### application.yaml
```yaml
kafka:
  arch:
    common:
      bootstrap-servers: localhost:29092
      schema-registry: http://localhost:8081
      enable-connection-ssl-protocol-mode: false
    producer:
      ack-producer-config: all
      compress-type: snappy
      type-partitioner: UniformStickyPartitioner
    consumer:
      consumer-group-id: my-consumer-group
      ack-consumer-config: manual
      event-auto-offset-reset-config: latest
      enable-avro-reader-config: false
```

### 4.2 SSL - Internal Mode (certificates inside the lib)

Use `certificate-type` when the `.p12` files are embedded in the library's resources. The lib resolves the correct certificate based on `certificate-type` and `environment`.

#### application.yaml
```yaml
kafka:
  arch:
    common:
      bootstrap-servers: broker:9093
      schema-registry: https://schema-registry:8081
      enable-connection-ssl-protocol-mode: true
      certificate-type: <sua_area>
      environment: stg
      ssl-trust-store-password: changeit
      ssl-key-store-password: changeit
    producer:
      ack-producer-config: all
      compress-type: snappy
    consumer:
      consumer-group-id: my-consumer-group
      ack-consumer-config: manual
      event-auto-offset-reset-config: earliest
```

### 4.3 SSL - External Mode (client provides certificates)

Use `ssl-trust-store-location` and `ssl-key-store-location` when the client provides its own certificates. Accepted formats:
- Path absoluto: `/opt/certs/truststore.p12`
- Classpath: `classpath:certs/truststore.p12`
- Nome simples: `truststore.p12` (tenta classpath primeiro, senao assume filesystem)

#### application.yaml
```yaml
kafka:
  arch:
    common:
      bootstrap-servers: broker:9093
      schema-registry: https://schema-registry:8081
      enable-connection-ssl-protocol-mode: true
      ssl-trust-store-location: /opt/certs/truststore.p12
      ssl-trust-store-password: changeit
      ssl-key-store-location: /opt/certs/keystore.p12
      ssl-key-store-password: changeit
    producer:
      ack-producer-config: all
      compress-type: snappy
    consumer:
      consumer-group-id: my-consumer-group
      ack-consumer-config: manual
      event-auto-offset-reset-config: earliest
```

> **IMPORTANT:** `certificate-type` and `ssl-trust/key-store-location` are mutually exclusive. Use one or the other, not both.

> **IMPORTANT:** SSL requires port 9093 (not 9092). The library validates this and throws `IllegalArgumentException` if SSL is enabled with port 9092.

### 4.4 Secondary Connection

```yaml
kafka:
  arch:
    common:
      bootstrap-servers: primary-broker:9092
      schema-registry: http://primary-registry:8081
      enable-another-connection: true
      another-bootstrap-servers: secondary-broker:9092
      another-schema-registry: http://secondary-registry:8081
```

When `enable-another-connection=true`, the library registers additional beans: `anotherProducerFactory`, `anotherKafkaAdmin`, `anotherKafkaTemplate`, and `anotherKafkaListenerContainerFactory`.

---

## 5. Full Properties Reference

### Common (`kafka.arch.common.*`)

| Property                              | Type    | Default          | Description                                                                                                 |
|---------------------------------------|---------|------------------|-------------------------------------------------------------------------------------------------------------|
| `bootstrap-servers`                   | String  | `localhost:9092` | Kafka broker address(es), comma-separated                                                                   |
| `client-id`                           | String  | -                | Client ID for broker tracking                                                                               |
| `environment`                         | String  | `dev`            | Ambiente de execucao (stg, qa, prod, dev). Usado no modo interno para resolver certificados                 |
| `schema-registry`                     | String  | `localhost:8081` | Confluent Schema Registry URL                                                                               |
| `enable-connection-ssl-protocol-mode` | boolean | `false`          | Enable SSL/TLS                                                                                              |
| `certificate-type`                    | String  | -                | Modo interno: tipo do certificado (ex: `<sua_area>`). Mutuamente exclusivo com ssl-trust/key-store-location |
| `ssl-trust-store-location`            | String  | -                | Modo externo: truststore path (PKCS12). Aceita path absoluto, classpath: ou nome simples                    |
| `ssl-trust-store-password`            | String  | -                | Truststore password                                                                                         |
| `ssl-key-store-location`              | String  | -                | Modo externo: keystore path (PKCS12). Aceita path absoluto, classpath: ou nome simples                      |
| `ssl-key-store-password`              | String  | -                | Keystore password                                                                                           |
| `reconnect-backoff`                   | int     | `50`             | Initial reconnect backoff (ms)                                                                              |
| `reconnect-backoff-max`               | int     | `2000`           | Max reconnect backoff (ms)                                                                                  |
| `events-concurrency`                  | int     | `2`              | Listener concurrency level                                                                                  |
| `enable-another-connection`           | boolean | `false`          | Enable secondary cluster                                                                                    |
| `another-bootstrap-servers`           | String  | -                | Secondary broker address(es)                                                                                |
| `another-schema-registry`             | String  | -                | Secondary Schema Registry URL                                                                               |

### Producer (`kafka.arch.producer.*`)

| Property                    | Type    | Default  | Allowed Values                                      |
|-----------------------------|---------|----------|-----------------------------------------------------|
| `ack-producer-config`       | String  | `all`    | `0`, `1`, `all`                                     |
| `max-producer-retry`        | int     | `5`      | -                                                   |
| `batch-size`                | int     | `20000`  | bytes                                               |
| `linger-ms`                 | int     | `10`     | ms                                                  |
| `enable-idempotence-config` | boolean | `false`  | (library always enables idempotence internally)     |
| `compress-type`             | String  | `none`   | `none`, `gzip`, `snappy`, `lz4`, `zstd`             |
| `type-partitioner`          | String  | -        | `RoundRobinPartitioner`, `UniformStickyPartitioner` |
| `transactional-id`          | String  | -        | Enables Kafka transactions                          |
| `enable-reactive-project`   | boolean | `false`  | For Reactor Kafka                                   |

### Consumer (`kafka.arch.consumer.*`)

| Property                                  | Type    | Default  | Allowed Values                                                                 |
|-------------------------------------------|---------|----------|--------------------------------------------------------------------------------|
| `consumer-group-id`                       | String  | -        | **Required**                                                                   |
| `ack-consumer-config`                     | String  | `manual` | `record`, `batch`, `time`, `count`, `count_time`, `manual`, `manual_immediate` |
| `event-auto-offset-reset-config`          | String  | `latest` | `earliest`, `latest`, `none`                                                   |
| `enable-auto-commit`                      | boolean | `false`  | -                                                                              |
| `max-poll-records`                        | int     | `500`    | -                                                                              |
| `max-poll-interval-ms`                    | int     | `300000` | ms                                                                             |
| `fetch-min-bytes`                         | int     | `100000` | bytes                                                                          |
| `fetch-max-wait-bytes`                    | int     | `500`    | ms                                                                             |
| `session-timeout-ms`                      | int     | `20000`  | ms                                                                             |
| `heartbeat-interval-ms`                   | int     | `3000`   | ms                                                                             |
| `request-timeout-config-ms`               | int     | `30000`  | ms                                                                             |
| `enable-avro-reader-config`               | boolean | `true`   | true=SpecificRecord, false=GenericRecord                                       |
| `enable-batch-listener`                   | boolean | `false`  | -                                                                              |
| `enable-async-ack`                        | boolean | `false`  | -                                                                              |
| `max-attempts-consumer-record`            | int     | `3`      | -                                                                              |
| `interval-retry-attempts-consumer-record` | int     | `10000`  | ms                                                                             |

---

## 6. Producer Example

### Java
```java
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
public class PostsKafkaProducer {

    private static final Logger LOG = LoggerFactory.getLogger(PostsKafkaProducer.class);

    private static final Schema POST_SCHEMA = SchemaBuilder.record("Post")
            .namespace("com.example.entity")
            .fields()
            .requiredLong("id")
            .requiredString("title")
            .requiredString("userId")
            .requiredString("body")
            .endRecord();

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final String topic;

    public PostsKafkaProducer(KafkaTemplate<String, Object> kafkaTemplate,
                              @Value("${kafka.topic.posts}") String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
    }

    public void sendEvent(Post post) {
        GenericRecord event = toRecord(post);

        kafkaTemplate.send(topic, event).handle((result, error) -> {
            if (error != null) {
                LOG.error("Error publishing event: {}", error.getMessage());
            }
            else {
                LOG.info("Event sent successfully");
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
```

### Kotlin
```kotlin
import org.apache.avro.SchemaBuilder
import org.apache.avro.generic.GenericData
import org.apache.avro.generic.GenericRecord
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component

@Component
class PostsKafkaProducer(
    private val kafkaTemplate: KafkaTemplate<String, Any>,
    @Value("\${kafka.topic.posts}") private val topic: String
) {

    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        private val POST_SCHEMA = SchemaBuilder.record("Post")
            .namespace("com.example.entity")
            .fields()
            .requiredLong("id")
            .requiredString("title")
            .requiredString("userId")
            .requiredString("body")
            .endRecord()
    }

    fun sendEvent(post: Post) {
        val event = toRecord(post)

        kafkaTemplate.send(topic, event).handle { _, error ->
            if (error != null) {
                log.error("Error publishing event: {}", error.message)
            }
            else {
                log.info("Event sent successfully")
            }
            null
        }
    }

    private fun toRecord(post: Post): GenericRecord {
        return GenericData.Record(POST_SCHEMA).apply {
            put("id", post.id)
            put("title", post.title)
            put("userId", post.userId)
            put("body", post.body)
        }
    }
}
```

---

## 7. Consumer Example

### Java
```java
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
        }
        catch (Exception e) {
            LOG.error("[PostEventConsumer] Error processing event: {}", e.getMessage());
        }
    }
}
```

### Kotlin
```kotlin
import org.apache.avro.generic.GenericRecord
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component

@Component
class PostEventConsumer {

    private val log = LoggerFactory.getLogger(javaClass)

    @KafkaListener(topics = ["\${kafka.topic.posts}"], groupId = "\${kafka.arch.consumer.consumer-group-id}")
    fun onMessage(event: ConsumerRecord<String, GenericRecord>, ack: Acknowledgment) {
        try {
            val record = event.value()
            log.info("[PostEventConsumer] Event received: id={}, title={}, userId={}, body={}",
                record["id"],
                record["title"],
                record["userId"],
                record["body"])
            ack.acknowledge()
        }
        catch (e: Exception) {
            log.error("[PostEventConsumer] Error processing event: {}", e.message)
        }
    }
}
```

---

## 8. Batch Consumer Example

### Configuration
```yaml
kafka:
  arch:
    consumer:
      enable-batch-listener: true
      ack-consumer-config: manual
      max-poll-records: 100
```

### Java
```java
import org.apache.avro.generic.GenericRecord;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PostBatchConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(PostBatchConsumer.class);

    @KafkaListener(topics = "${kafka.topic.posts}", groupId = "${kafka.arch.consumer.consumer-group-id}")
    public void onMessage(List<ConsumerRecord<String, GenericRecord>> events, Acknowledgment ack) {
        try {
            LOG.info("[PostBatchConsumer] Received batch of {} events", events.size());
            for (var event : events) {
                var record = event.value();
                LOG.info("Processing: id={}", record.get("id"));
            }
            ack.acknowledge();
        }
        catch (Exception e) {
            LOG.error("[PostBatchConsumer] Error processing batch: {}", e.getMessage());
        }
    }
}
```

### Kotlin
```kotlin
import org.apache.avro.generic.GenericRecord
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component

@Component
class PostBatchConsumer {

    private val log = LoggerFactory.getLogger(javaClass)

    @KafkaListener(topics = ["\${kafka.topic.posts}"], groupId = "\${kafka.arch.consumer.consumer-group-id}")
    fun onMessage(events: List<ConsumerRecord<String, GenericRecord>>, ack: Acknowledgment) {
        try {
            log.info("[PostBatchConsumer] Received batch of {} events", events.size)
            events.forEach { event ->
                val record = event.value()
                log.info("Processing: id={}", record["id"])
            }
            ack.acknowledge()
        }
        catch (e: Exception) {
            log.error("[PostBatchConsumer] Error processing batch: {}", e.message)
        }
    }
}
```

---

## 9. Beans Provided by the Library

Once `@EnabledArchKafka` is active, these beans are auto-registered (all `@Primary`):

| Bean                            | Type                                      | Notes                           |
|---------------------------------|-------------------------------------------|---------------------------------|
| `kafkaComponentsFactory`        | `KafkaComponentsFactory`                  | Central factory                 |
| `producerFactory`               | `ProducerFactory<String, Object>`         | Avro + SSL configured           |
| `kafkaAdmin`                    | `KafkaAdmin`                              | Topic management                |
| `kafkaTemplate`                 | `KafkaTemplate<String, Object>`           | With Micrometer + Observation   |
| `kafkaListenerContainerFactory` | `ConcurrentKafkaListenerContainerFactory` | ACK mode, batch, error handling |

With `enable-another-connection=true`, additional beans: `anotherProducerFactory`, `anotherKafkaAdmin`, `anotherKafkaTemplate`, `anotherKafkaListenerContainerFactory`.

---

## 10. Key Behaviors

- **Idempotence** is always enabled internally (`enable.idempotence=true`, `max.in.flight.requests.per.connection=1`)
- **Serialization**: `StringSerializer` for keys, `KafkaAvroSerializer` for values (producer); `StringDeserializer` + `SafeKafkaAvroDeserializer` for consumer
- **SafeKafkaAvroDeserializer**: swallows deserialization errors (returns null, logs) instead of crashing the consumer
- **Error handling**: configurable retry with `max-attempts-consumer-record` and `interval-retry-attempts-consumer-record`
- **Observability**: Micrometer and Spring Observation enabled on KafkaTemplate
- **SSL dual mode**: `certificate-type` para certificados internos da lib (resolve por ambiente automaticamente) ou `ssl-trust/key-store-location` para certificados do cliente. Mutuamente exclusivos. Ambos funcionam em JVM e native image (GraalVM)
- The library runs **before** Spring's `KafkaAutoConfiguration` and registers `@Primary` beans that override defaults

---

## 11. Testes de Integração SSL

A lib inclui testes de integração SSL com certificados `.p12` auto-assinados e Testcontainers. Esses testes validam a conexão real na porta 9093.

### Estrutura dos certificados de teste

```
src/test/resources/ssl/
├── kafka.keystore.p12     # Keystore com chave privada + certificado auto-assinado
├── kafka.truststore.p12   # Truststore com o certificado do broker importado
└── ssl_credentials        # Arquivo texto com a senha ("changeit")
```

### Gerando certificados de teste

```bash
# Gerar keystore com certificado auto-assinado
keytool -genkeypair -alias kafka-broker -keyalg RSA -keysize 2048 -validity 365 \
  -dname "CN=localhost,OU=Test,O=Test,L=Test,ST=Test,C=BR" \
  -ext "SAN=DNS:localhost,DNS:kafka,IP:127.0.0.1" \
  -keystore kafka.keystore.p12 -storetype PKCS12 \
  -storepass changeit -keypass changeit

# Exportar o certificado
keytool -exportcert -alias kafka-broker -keystore kafka.keystore.p12 \
  -storepass changeit -file kafka-broker.crt

# Importar no truststore
keytool -importcert -alias kafka-broker -file kafka-broker.crt \
  -keystore kafka.truststore.p12 -storetype PKCS12 \
  -storepass changeit -noprompt

rm kafka-broker.crt
```

> **IMPORTANTE:** O certificado do keystore DEVE ser importado no truststore. Sem isso, o Kafka falha com `PKIX path building failed`.

### Container Kafka com SSL (Testcontainers)

O teste usa `GenericContainer` com `confluentinc/cp-kafka:7.4.0` em KRaft mode. Pontos-chave da configuração:

1. **Dois listeners**: `BROKER:PLAINTEXT` (inter-broker) + `SSL` (clientes externos). Usar SSL para inter-broker causa erro de validação com certificados auto-assinados.

2. **Advertised listeners dinâmico**: O Testcontainers mapeia portas aleatoriamente. Para resolver isso, o container usa um script que espera por um arquivo de sinal antes de iniciar o Kafka:
   ```java
   .withCommand("sh", "-c",
     "while [ ! -f /tmp/kafka_listeners ]; do sleep 0.1; done && " +
     "export KAFKA_ADVERTISED_LISTENERS=$(cat /tmp/kafka_listeners) && " +
     "/etc/confluent/docker/run")
   ```

3. **Wait strategy no-op**: A estratégia padrão espera a porta abrir, mas o Kafka só inicia após o sinal. Usa-se uma `AbstractWaitStrategy` vazia + polling manual via `AdminClient`.

4. **Env vars do Confluent**: A imagem `cp-kafka` requer `KAFKA_SSL_KEYSTORE_FILENAME` + `KAFKA_SSL_*_CREDENTIALS` (não `KAFKA_SSL_KEYSTORE_LOCATION`).

### Executando os testes de integração

```bash
# Perfil Maven 'integration' habilita a tag @Tag("integration")
./mvnw test -pl kafka -Pintegration -Dtest=KafkaSslIntegrationTest
```

### Testes disponíveis

| Teste                                             | Descrição                                                             |
|---------------------------------------------------|-----------------------------------------------------------------------|
| `shouldProduceAndConsumeViaSsl`                   | Produz e consome uma mensagem via SSL (end-to-end)                    |
| `shouldConnectViaSsl_usingKafkaComponentsFactory` | Valida que o `KafkaComponentsFactory` cria beans corretamente com SSL |
| `shouldConnectViaSsl_usingCertificateResolver`    | Valida conexão via `AdminClient` usando certificados resolvidos       |
