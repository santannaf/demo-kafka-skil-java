---
name: kafka-client-setup
description: Guide for implementing the com.tanna.spring:kafka library in Spring Boot 4.x client projects (Java/Kotlin). Covers dependency setup, configuration, producer/consumer examples, SSL, batch, secondary connections, and local Docker environment.
user_invocable: true
---

# Kafka Library Client Setup Guide

You are helping the user set up a client project that uses the `com.tanna.spring:kafka` library. This library wraps Spring Kafka with opinionated defaults: Avro serialization via Confluent Schema Registry, SSL/TLS support, custom error handling, batch listeners, and flexible ACK modes.

**Spring Boot 4.x / Java 21+ required.**

> **IMPORTANT:** When creating configuration files in client projects, always prefer `application.properties`. Only create `application.yaml` / `application.yml` if the user explicitly requests YAML format. The examples below show both formats for reference, but `.properties` is the default when implementing.

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
      - "9093:9093"
    volumes:
      - ./certs/kafka.keystore.p12:/etc/kafka/secrets/kafka.keystore.p12:ro
      - ./certs/kafka.truststore.p12:/etc/kafka/secrets/kafka.truststore.p12:ro
      - ./certs/ssl_credentials:/etc/kafka/secrets/ssl_credentials:ro
    environment:
      KAFKA_BROKER_ID: 1
      KAFKA_ADVERTISED_HOST_NAME: localhost
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka:9092,PLAINTEXT_HOST://localhost:29092,SSL://localhost:9093
      KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: PLAINTEXT:PLAINTEXT,PLAINTEXT_HOST:PLAINTEXT,SSL:SSL
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
      # SSL config
      KAFKA_SSL_KEYSTORE_FILENAME: kafka.keystore.p12
      KAFKA_SSL_KEYSTORE_CREDENTIALS: ssl_credentials
      KAFKA_SSL_KEY_CREDENTIALS: ssl_credentials
      KAFKA_SSL_TRUSTSTORE_FILENAME: kafka.truststore.p12
      KAFKA_SSL_TRUSTSTORE_CREDENTIALS: ssl_credentials
      KAFKA_SSL_CLIENT_AUTH: none
      KAFKA_SSL_ENDPOINT_IDENTIFICATION_ALGORITHM: ""
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
        echo "Waiting for Kafka brokers to start and necessary topics to be available"
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

| Service                       | URL                     | Description                       |
|-------------------------------|-------------------------|-----------------------------------|
| Kafka Broker (internal)       | `kafka:9092`            | For inter-container communication |
| Kafka Broker (host PLAINTEXT) | `localhost:29092`       | For application running on host   |
| Kafka Broker (host SSL)       | `localhost:9093`        | For SSL connections from host     |
| Schema Registry               | `http://localhost:8081` | Avro schema management            |
| Control Center                | `http://localhost:9021` | Kafka cluster monitoring UI       |

> **Note:** Use `localhost:29092` as `bootstrap-servers` in `application.properties` for PLAINTEXT connections. Use `localhost:9093` for SSL connections. The default setup should always prefer PLAINTEXT (`localhost:29092`); use SSL (`localhost:9093`) only when the user wants to test SSL locally.

### SSL certificates for local Docker

To use the SSL listener (`localhost:9093`), the certificates must be generated using the script included in the **library project** (`spring-kafka`), not in the client. The script uses `keytool`, which is part of the JDK — **JDK 21+ must be installed and on PATH**.

```bash
# No repositorio da lib (spring-kafka)
cd certs

# With defaults
./generate-certs.sh

# Customizing for your organization
./generate-certs.sh --org MyCompany --location SaoPaulo --state SP --country BR

# Customizing password and validity
./generate-certs.sh --org MyCompany --password my-password --validity 730
```

Available options:

| Option       | Default    | Description                       |
|--------------|------------|-----------------------------------|
| `--org`      | `MyOrg`    | Organization name                 |
| `--location` | `City`     | City                              |
| `--state`    | `State`    | State                             |
| `--country`  | `BR`       | Country code (2 letters)          |
| `--password` | `changeit` | Keystore password                 |
| `--validity` | `365`      | Certificate validity in days      |

This script generates certificates following the **production model** with a separate CA:

| File                       | Content                                      | Used by        |
|----------------------------|----------------------------------------------|----------------|
| `ca-root.p12`              | CA keystore (private key + certificate)      | Admin only     |
| `ca-root.crt`              | CA certificate in PEM format                 | Reference      |
| `kafka.keystore.p12`       | Broker certificate signed by the CA          | Broker (Kafka) |
| `kafka.client-keystore.p12`| Client certificate signed by the CA          | Client (App)   |
| `kafka.truststore.p12`     | CA certificate (to validate the broker)      | Client (App)   |
| `ssl_credentials`          | Keystore password (used by Confluent Docker) | Broker (Kafka) |

**Trust flow:**
1. The **broker** presents its certificate (signed by the CA) on SSL connections.
2. The **client** validates the broker certificate using the truststore that contains the CA.
3. The **client** presents its own certificate (signed by the same CA) — required for mTLS.

After generating, copy the necessary files to the client project:

```bash
# Broker files: copy to client's certs/ directory (for docker-compose volumes)
cp certs/kafka.keystore.p12 certs/kafka.truststore.p12 certs/ssl_credentials <client-project>/certs/

# Client app files: copy truststore + client keystore to the classpath
cp certs/kafka.truststore.p12 certs/kafka.client-keystore.p12 <client-project>/src/main/resources/ssl/
```

> **IMPORTANT:** The client should NOT generate its own certificates. Always use the `generate-certs.sh` script from the library project (`spring-kafka/certs/`). This ensures the broker and client share the same CA trust chain.

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

### Switching Between PLAINTEXT and SSL with Spring Profiles

The recommended approach is to use **Spring profiles** to separate PLAINTEXT and SSL configurations. Create two profile-specific files and activate the desired one at startup:

| Profile       | File                                | Port    | Use case                      |
|---------------|-------------------------------------|---------|-------------------------------|
| `plaintext`   | `application-plaintext.properties`  | `29092` | Local development (default)   |
| `ssl`         | `application-ssl.properties`        | `9093`  | SSL testing / production-like |

**Running with a profile:**
```bash
# PLAINTEXT (no certificates needed)
./gradlew bootRun --args='--spring.profiles.active=plaintext'
# or with Maven
./mvnw spring-boot:run -Dspring-boot.run.arguments='--spring.profiles.active=plaintext'

# SSL (requires certificates in src/main/resources/ssl/)
./gradlew bootRun --args='--spring.profiles.active=ssl'
# or with Maven
./mvnw spring-boot:run -Dspring-boot.run.arguments='--spring.profiles.active=ssl'
```

**Profile file: `application-plaintext.properties`**
```properties
spring.application.name=my-service

kafka.topic.my-topic=my-topic

kafka.arch.common.bootstrap-servers=localhost:29092
kafka.arch.common.schema-registry=http://localhost:8081
kafka.arch.common.enable-connection-ssl-protocol-mode=false

kafka.arch.producer.ack-producer-config=all
kafka.arch.producer.compress-type=snappy

kafka.arch.consumer.consumer-group-id=my-consumer-group
kafka.arch.consumer.ack-consumer-config=manual
kafka.arch.consumer.event-auto-offset-reset-config=latest
kafka.arch.consumer.enable-avro-reader-config=false
```

**Profile file: `application-ssl.properties`**
```properties
spring.application.name=my-service

kafka.topic.my-topic=my-topic

kafka.arch.common.bootstrap-servers=localhost:9093
kafka.arch.common.schema-registry=http://localhost:8081
kafka.arch.common.enable-connection-ssl-protocol-mode=true
kafka.arch.common.ssl-trust-store-location=classpath:ssl/kafka.truststore.p12
kafka.arch.common.ssl-trust-store-password=changeit
kafka.arch.common.ssl-key-store-location=classpath:ssl/kafka.client-keystore.p12
kafka.arch.common.ssl-key-store-password=changeit

kafka.arch.producer.ack-producer-config=all
kafka.arch.producer.compress-type=snappy

kafka.arch.consumer.consumer-group-id=my-consumer-group
kafka.arch.consumer.ack-consumer-config=manual
kafka.arch.consumer.event-auto-offset-reset-config=latest
kafka.arch.consumer.enable-avro-reader-config=false
```

> **Note:** The `kafka.truststore.p12` contains the CA certificate (to trust the broker) and `kafka.client-keystore.p12` contains the client's own certificate signed by the same CA. Both are generated by the library's `generate-certs.sh` script.

> **Note:** The base `application.properties` file should contain only properties that are common to all profiles (e.g., `spring.application.name`). Kafka-specific properties should go in the profile files.

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

#### application.properties
```properties
kafka.arch.common.bootstrap-servers=broker:9093
kafka.arch.common.schema-registry=https://schema-registry:8081
kafka.arch.common.enable-connection-ssl-protocol-mode=true
kafka.arch.common.certificate-type=<sua_area>
kafka.arch.common.environment=stg
kafka.arch.common.ssl-trust-store-password=changeit
kafka.arch.common.ssl-key-store-password=changeit

kafka.arch.producer.ack-producer-config=all
kafka.arch.producer.compress-type=snappy

kafka.arch.consumer.consumer-group-id=my-consumer-group
kafka.arch.consumer.ack-consumer-config=manual
kafka.arch.consumer.event-auto-offset-reset-config=earliest
```

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

#### application.properties
```properties
kafka.arch.common.bootstrap-servers=broker:9093
kafka.arch.common.schema-registry=https://schema-registry:8081
kafka.arch.common.enable-connection-ssl-protocol-mode=true
kafka.arch.common.ssl-trust-store-location=/opt/certs/truststore.p12
kafka.arch.common.ssl-trust-store-password=changeit
kafka.arch.common.ssl-key-store-location=/opt/certs/keystore.p12
kafka.arch.common.ssl-key-store-password=changeit

kafka.arch.producer.ack-producer-config=all
kafka.arch.producer.compress-type=snappy

kafka.arch.consumer.consumer-group-id=my-consumer-group
kafka.arch.consumer.ack-consumer-config=manual
kafka.arch.consumer.event-auto-offset-reset-config=earliest
```

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

#### application.properties
```properties
kafka.arch.common.bootstrap-servers=primary-broker:9092
kafka.arch.common.schema-registry=http://primary-registry:8081
kafka.arch.common.enable-another-connection=true
kafka.arch.common.another-bootstrap-servers=secondary-broker:9092
kafka.arch.common.another-schema-registry=http://secondary-registry:8081
```

#### application.yaml
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
| `enable-virtual-threads`                  | boolean | `false`  | Uses virtual threads for listener container (Java 21+)                         |

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

#### application.properties
```properties
kafka.arch.consumer.enable-batch-listener=true
kafka.arch.consumer.ack-consumer-config=manual
kafka.arch.consumer.max-poll-records=100
```

#### application.yaml
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

A lib inclui testes de integração SSL com Testcontainers. Esses testes validam a conexão real na porta 9093.

### Estrutura dos certificados

Os certificados para o ambiente Docker local e testes sao gerados pelo script `certs/generate-certs.sh` na raiz do repositorio da lib. O script segue o modelo de producao com CA separada:

```
certs/
├── generate-certs.sh         # Script de geracao (executar para regenerar)
├── ca-root.p12               # CA keystore (chave privada + certificado)
├── ca-root.crt               # Certificado da CA em PEM
├── kafka.keystore.p12        # Keystore do broker (certificado assinado pela CA)
├── kafka.client-keystore.p12 # Keystore do cliente (certificado assinado pela CA)
├── kafka.truststore.p12      # Truststore (contem apenas a CA)
└── ssl_credentials           # Senha dos keystores (default: "changeit")
```

The script requires **JDK 21+** installed (for the `keytool` command). To regenerate with custom values:

```bash
cd certs
./generate-certs.sh --org MyCompany --location SaoPaulo --state SP
```

### Fluxo de confianca (modelo producao)

```
CA (ca-root)
  |
  +-- assina --> certificado do broker  (kafka.keystore.p12)
  |
  +-- assina --> certificado do cliente (kafka.client-keystore.p12)
  |
  +-- confia <-- truststore do cliente  (kafka.truststore.p12 contem ca-root.crt)
```

> **IMPORTANTE:** O truststore do cliente contem o certificado da CA, nao o certificado do broker diretamente. O client-keystore contem a identidade do cliente, assinada pela mesma CA.

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
