# Demo Kafka Skill

Projeto de demonstracao de producao e consumo de mensagens Kafka usando a biblioteca `com.tanna.spring:kafka` com Spring Boot 4.x e Java 25.

## Stack

- Java 25 (GraalVM)
- Spring Boot 4.0.5
- Gradle 9.4.1
- `com.tanna.spring:kafka:1.0.0` (wrapper opinionated sobre Spring Kafka com Avro + Schema Registry)
- Apache Avro 1.12.1

## Estrutura

```
src/main/java/santannaf/kafka/demo/skill/
  DemoKafkaSkillApplication.java   # @SpringBootApplication + @EnabledArchKafka
  Post.java                        # Record (id, title, userId, body)
  PostController.java              # REST endpoint POST /posts
  PostKafkaProducer.java           # Serializa Post em GenericRecord Avro e envia ao topico
  PostEventConsumer.java           # @KafkaListener com ACK manual, loga eventos recebidos

src/main/resources/
  application-plaintext.properties # Conexao PLAINTEXT na porta 29092
  application-ssl.properties       # Conexao SSL na porta 9093 com certificados auto-assinados
  ssl/                             # Keystore e truststore PKCS12 para modo SSL

certs/                             # Certificados montados no container Kafka (SSL)
docker-compose.kafka.yaml          # Kafka + Zookeeper + Schema Registry + Control Center
requests.http                      # Arquivo HTTP para teste do endpoint
```

## Pre-requisitos

- Java 25 (GraalVM)
- Docker e Docker Compose

## Subindo o ambiente Kafka

```bash
docker compose -f docker-compose.kafka.yaml up -d
```

Servicos disponiveis:

| Servico         | URL                     | Descricao                          |
|-----------------|-------------------------|------------------------------------|
| Kafka (host)    | `localhost:29092`       | Listener PLAINTEXT para a app      |
| Kafka SSL       | `localhost:9093`        | Listener SSL com certificado       |
| Schema Registry | `http://localhost:8081` | Gerenciamento de schemas Avro      |
| Control Center  | `http://localhost:9021` | UI de monitoramento do Kafka       |

Para parar:

```bash
docker compose -f docker-compose.kafka.yaml down
```

## Executando a aplicacao

### Modo PLAINTEXT (porta 29092)

```bash
./gradlew bootRun --args='--spring.profiles.active=plaintext'
```

### Modo SSL (porta 9093)

```bash
./gradlew bootRun --args='--spring.profiles.active=ssl'
```

Os certificados auto-assinados ja estao incluidos em `src/main/resources/ssl/` e sao referenciados via `classpath:` no profile SSL.

## Testando

### Via curl

```bash
curl -X POST http://localhost:8080/posts \
  -H "Content-Type: application/json" \
  -d '{"id": 1, "title": "Hello Kafka", "userId": "user-42", "body": "Primeira mensagem"}'
```

### Via IntelliJ

Abra o arquivo `requests.http` na raiz do projeto e clique no botao de play ao lado do request.

### Saida esperada

No console da aplicacao voce vera os logs do producer e do consumer:

```
Event sent successfully: id=1
[PostEventConsumer] Event received: id=1, title=Hello Kafka, userId=user-42, body=Primeira mensagem
```

## Testes

```bash
./gradlew test
```

## Gerando os certificados SSL (opcional)

Os certificados ja estao incluidos no repositorio. Para regenera-los:

```bash
cd certs

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

# Limpar
rm kafka-broker.crt

# Copiar para o classpath
cp kafka.keystore.p12 kafka.truststore.p12 ../src/main/resources/ssl/
```
