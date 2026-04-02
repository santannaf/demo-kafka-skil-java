#!/usr/bin/env bash
# ==============================================================================
# Configura ACLs no broker Kafka para a aplicacao demo-kafka-skill.
#
# Pre-requisito: o ambiente Docker deve estar rodando.
#   docker compose -f docker-compose.kafka.yaml up -d
#
# Uso: ./scripts/setup-acls.sh
#
# O que este script faz:
#   - Permite que CN=demo-kafka-skill produza no topico "posts-topic"
#   - Permite que CN=demo-kafka-skill consuma do topico "posts-topic"
#   - Permite que CN=demo-kafka-skill use o consumer group "demo-kafka-skill-group"
#   - Lista as ACLs configuradas ao final
# ==============================================================================

set -euo pipefail

BROKER="localhost:9092"
APP_CN="demo-kafka-skill"
TOPIC="posts-topic"
GROUP="demo-kafka-skill-group"

echo ""
echo "Configurando ACLs para CN=${APP_CN}..."
echo ""

# --------------------------------------------------------------------------
# Criar o topico (caso nao exista)
# --------------------------------------------------------------------------
echo "==> Criando topico '${TOPIC}' (se nao existir)..."
docker exec kafka kafka-topics --bootstrap-server "${BROKER}" \
  --create --if-not-exists \
  --topic "${TOPIC}" \
  --partitions 3 \
  --replication-factor 1

# --------------------------------------------------------------------------
# ACL: permitir PRODUCE no topico
# --------------------------------------------------------------------------
echo "==> ACL: permitir ${APP_CN} produzir em '${TOPIC}'..."
docker exec kafka kafka-acls --bootstrap-server "${BROKER}" \
  --add \
  --allow-principal "User:CN=${APP_CN}" \
  --operation Write \
  --operation Describe \
  --topic "${TOPIC}"

# --------------------------------------------------------------------------
# ACL: permitir CONSUME do topico
# --------------------------------------------------------------------------
echo "==> ACL: permitir ${APP_CN} consumir de '${TOPIC}'..."
docker exec kafka kafka-acls --bootstrap-server "${BROKER}" \
  --add \
  --allow-principal "User:CN=${APP_CN}" \
  --operation Read \
  --operation Describe \
  --topic "${TOPIC}"

# --------------------------------------------------------------------------
# ACL: permitir uso do consumer group
# --------------------------------------------------------------------------
echo "==> ACL: permitir ${APP_CN} usar o group '${GROUP}'..."
docker exec kafka kafka-acls --bootstrap-server "${BROKER}" \
  --add \
  --allow-principal "User:CN=${APP_CN}" \
  --operation Read \
  --group "${GROUP}"

# --------------------------------------------------------------------------
# Listar ACLs configuradas
# --------------------------------------------------------------------------
echo ""
echo "==> ACLs configuradas:"
docker exec kafka kafka-acls --bootstrap-server "${BROKER}" --list

echo ""
echo "Setup concluido. A aplicacao '${APP_CN}' pode produzir e consumir do topico '${TOPIC}'."
echo ""
echo "Para testar:"
echo "  ./gradlew bootRun --args='--spring.profiles.active=ssl'"
