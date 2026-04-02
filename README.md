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

### Modo SSL com mTLS + ACLs (porta 9093)

O ambiente Docker usa mTLS (`KAFKA_SSL_CLIENT_AUTH: required`) e ACLs por topico. O broker identifica a aplicacao pelo CN do certificado do cliente (`CN=demo-kafka-skill`).

```bash
# 1. Subir o ambiente
docker compose -f docker-compose.kafka.yaml up -d

# 2. Configurar as ACLs (permite CN=demo-kafka-skill produzir/consumir no posts-topic)
./scripts/setup-acls.sh

# 3. Rodar a aplicacao
./gradlew bootRun --args='--spring.profiles.active=ssl'
```

Os certificados foram gerados pela lib (`spring-kafka/certs/generate-certs.sh --app-name demo-kafka-skill`) e ja estao incluidos no repositorio:

- `certs/` — montados no container Kafka (broker keystore + truststore)
- `src/main/resources/ssl/` — usados pela app (client keystore + truststore com CA)

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

Os certificados já estão incluídos no repositório. Para regenerá-los:

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

### Por que o mesmo keystore/truststore funciona nos dois lados?

Neste demo, os arquivos em `certs/` (montados no broker) e `src/main/resources/ssl/` (usados pela app) sao copias identicas. Isso funciona porque:

- O **broker** usa o `kafka.keystore.p12` para apresentar seu certificado SSL nas conexoes (identidade do servidor).
- A **app** usa o `kafka.truststore.p12` para **confiar** no certificado do broker.

Como o certificado e auto-assinado, o truststore contem exatamente o certificado que foi gerado no keystore — por isso o mesmo par serve para ambos os lados. A app tambem recebe o keystore na configuracao (`ssl-key-store-location`), mas ele nao e utilizado na pratica porque o broker esta com `KAFKA_SSL_CLIENT_AUTH: none` (nao exige certificado do cliente).

### Em producao

Num cenario real, os certificados do broker e do cliente seriam **distintos**:

1. **Broker** — teria um keystore com certificado assinado por uma CA (interna ou publica), nao auto-assinado.
2. **App (cliente)** — teria um truststore contendo apenas o certificado da CA (para confiar no broker), e **nao** uma copia do keystore do broker.
3. **mTLS (opcional)** — se o broker exigir autenticacao mutua (`KAFKA_SSL_CLIENT_AUTH: required`), a app tambem precisaria de seu proprio keystore com um certificado de cliente assinado pela mesma CA (ou por outra CA confiavel pelo broker).
