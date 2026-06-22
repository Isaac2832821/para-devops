#!/usr/bin/env bash
# ══════════════════════════════════════════════════════════════════
# cluster-setup.sh — Provisioning del Clúster ECS Fargate
# Colegio Bernardo O'Higgins — Sistema de Gestión Escolar
#
# USO:
#   export DOCKERHUB_USERNAME="tu_usuario"
#   export ACCOUNT_ID="$(aws sts get-caller-identity --query Account --output text)"
#   bash ecs/cluster-setup.sh
#
# REQUISITOS:
#   - AWS CLI v2 configurado con credenciales activas
#   - Permisos: ecs:*, ec2:*, iam:PassRole, elasticloadbalancing:*
#   - Las EC2 existentes (ec2-gateway, ec2-services, ec2-frontend) deben estar running
#
# COSTO ESTIMADO (AWS Academy):
#   ~$0.01/hora por task Fargate (512 CPU, 1024 MB)
#   Con 5 tasks corriendo 8h = ~$0.40 USD del crédito del lab
# ══════════════════════════════════════════════════════════════════

set -euo pipefail

# ── Colores para output ────────────────────────────────────────────
RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'
BLUE='\033[0;34m'; CYAN='\033[0;36m'; NC='\033[0m'
info()    { echo -e "${BLUE}[INFO]${NC} $1"; }
success() { echo -e "${GREEN}[OK]${NC} $1"; }
warn()    { echo -e "${YELLOW}[WARN]${NC} $1"; }
error()   { echo -e "${RED}[ERROR]${NC} $1"; exit 1; }

# ── Variables de configuración ──────────────────────────────────────
CLUSTER_NAME="colegio-cluster"
REGION="us-east-1"
DOCKERHUB_USERNAME="${DOCKERHUB_USERNAME:-}"
ACCOUNT_ID="${ACCOUNT_ID:-$(aws sts get-caller-identity --query Account --output text)}"

[ -z "$DOCKERHUB_USERNAME" ] && error "Debes exportar DOCKERHUB_USERNAME"

echo -e "${CYAN}"
echo "╔══════════════════════════════════════════════════════════╗"
echo "║   ECS Fargate Cluster Setup — Colegio O'Higgins         ║"
echo "║   Región: $REGION                                   ║"
echo "║   Cuenta: $ACCOUNT_ID                       ║"
echo "╚══════════════════════════════════════════════════════════╝"
echo -e "${NC}"

# ─────────────────────────────────────────────────────────────────
# PASO 1: Obtener VPC y Subnets de las instancias EC2 existentes
# ─────────────────────────────────────────────────────────────────
info "PASO 1: Obteniendo VPC y Subnets de las instancias EC2..."

VPC_ID=$(aws ec2 describe-instances \
  --filters "Name=tag:Name,Values=ec2-gateway" "Name=instance-state-name,Values=running" \
  --query "Reservations[0].Instances[0].VpcId" \
  --output text --region "$REGION")

if [[ "$VPC_ID" == "None" || -z "$VPC_ID" ]]; then
  warn "No se encontró ec2-gateway. Usando VPC default..."
  VPC_ID=$(aws ec2 describe-vpcs --filters "Name=isDefault,Values=true" \
    --query "Vpcs[0].VpcId" --output text --region "$REGION")
fi

SUBNET_IDS=$(aws ec2 describe-subnets \
  --filters "Name=vpc-id,Values=$VPC_ID" "Name=map-public-ip-on-launch,Values=true" \
  --query "Subnets[*].SubnetId" \
  --output text --region "$REGION" | tr '\t' ',')

[ -z "$SUBNET_IDS" ] && error "No se encontraron subnets públicas en VPC $VPC_ID"

success "VPC: $VPC_ID"
success "Subnets: $SUBNET_IDS"

# ─────────────────────────────────────────────────────────────────
# PASO 2: Crear Security Group para ECS
# ─────────────────────────────────────────────────────────────────
info "PASO 2: Configurando Security Group para ECS..."

ECS_SG_NAME="colegio-ecs-sg"
EXISTING_SG=$(aws ec2 describe-security-groups \
  --filters "Name=group-name,Values=$ECS_SG_NAME" "Name=vpc-id,Values=$VPC_ID" \
  --query "SecurityGroups[0].GroupId" --output text --region "$REGION" 2>/dev/null || echo "None")

if [[ "$EXISTING_SG" == "None" || -z "$EXISTING_SG" ]]; then
  ECS_SG=$(aws ec2 create-security-group \
    --group-name "$ECS_SG_NAME" \
    --description "Security Group para ECS Fargate - Colegio OHiggins" \
    --vpc-id "$VPC_ID" \
    --region "$REGION" \
    --query "GroupId" --output text)

  # Reglas de entrada: puertos de los microservicios
  aws ec2 authorize-security-group-ingress \
    --group-id "$ECS_SG" --region "$REGION" \
    --ip-permissions \
      "IpProtocol=tcp,FromPort=80,ToPort=80,IpRanges=[{CidrIp=0.0.0.0/0}]" \
      "IpProtocol=tcp,FromPort=8080,ToPort=8086,IpRanges=[{CidrIp=0.0.0.0/0}]" \
      "IpProtocol=tcp,FromPort=8761,ToPort=8761,IpRanges=[{CidrIp=0.0.0.0/0}]" > /dev/null

  success "Security Group creado: $ECS_SG"
else
  ECS_SG="$EXISTING_SG"
  success "Security Group existente reutilizado: $ECS_SG"
fi

# ─────────────────────────────────────────────────────────────────
# PASO 3: Crear el Clúster ECS (Fargate — sin instancias EC2)
# ─────────────────────────────────────────────────────────────────
info "PASO 3: Creando Clúster ECS Fargate: $CLUSTER_NAME..."

EXISTING_CLUSTER=$(aws ecs describe-clusters \
  --clusters "$CLUSTER_NAME" --region "$REGION" \
  --query "clusters[0].status" --output text 2>/dev/null || echo "MISSING")

if [[ "$EXISTING_CLUSTER" != "ACTIVE" ]]; then
  aws ecs create-cluster \
    --cluster-name "$CLUSTER_NAME" \
    --capacity-providers FARGATE FARGATE_SPOT \
    --default-capacity-provider-strategy \
      capacityProvider=FARGATE,weight=1,base=1 \
    --tags key=Project,value=colegio-ohiggins key=Environment,value=production \
    --region "$REGION" > /dev/null
  success "Clúster ECS creado: $CLUSTER_NAME"
else
  success "Clúster ECS ya existe y está ACTIVE"
fi

# ─────────────────────────────────────────────────────────────────
# PASO 4: Crear CloudWatch Log Groups
# ─────────────────────────────────────────────────────────────────
info "PASO 4: Creando CloudWatch Log Groups..."

SERVICES=("eureka-server" "api-gateway" "ms-autenticacion" "ms-calificaciones" "ms-asistencia" "ms-notificaciones" "ms-horarios" "ms-reportes" "frontend")
for svc in "${SERVICES[@]}"; do
  aws logs create-log-group \
    --log-group-name "/ecs/colegio-$svc" \
    --region "$REGION" 2>/dev/null && success "Log group: /ecs/colegio-$svc" || warn "Log group ya existe: /ecs/colegio-$svc"

  # Retención de 7 días (costo mínimo)
  aws logs put-retention-policy \
    --log-group-name "/ecs/colegio-$svc" \
    --retention-in-days 7 \
    --region "$REGION" 2>/dev/null || true
done

# ─────────────────────────────────────────────────────────────────
# PASO 5: Registrar Task Definitions
# ─────────────────────────────────────────────────────────────────
info "PASO 5: Registrando Task Definitions en ECS..."

TASK_DIR="$(dirname "$0")/task-definitions"

# Función para reemplazar placeholders y registrar task
register_task() {
  local FILE="$1"
  local TASK_NAME="$2"
  local TEMP_FILE="/tmp/ecs-task-$TASK_NAME.json"

  # Reemplazar placeholders con valores reales
  sed "s/ACCOUNT_ID/$ACCOUNT_ID/g; s/DOCKERHUB_USERNAME/$DOCKERHUB_USERNAME/g" \
    "$TASK_DIR/$FILE" > "$TEMP_FILE"

  REVISION=$(aws ecs register-task-definition \
    --cli-input-json "file://$TEMP_FILE" \
    --region "$REGION" \
    --query "taskDefinition.revision" --output text)

  success "Task Definition registrada: $TASK_NAME:$REVISION"
  echo "$TASK_NAME:$REVISION"
}

register_task "ecs-task-eureka.json"          "colegio-eureka-server"
register_task "ecs-task-gateway.json"         "colegio-api-gateway"
register_task "ecs-task-autenticacion.json"   "colegio-ms-autenticacion"
register_task "ecs-task-calificaciones.json"  "colegio-ms-calificaciones"
register_task "ecs-task-frontend.json"        "colegio-frontend"

# ─────────────────────────────────────────────────────────────────
# PASO 6: Crear ALB (Application Load Balancer)
# ─────────────────────────────────────────────────────────────────
info "PASO 6: Configurando Application Load Balancer..."

ALB_NAME="colegio-alb"
EXISTING_ALB=$(aws elbv2 describe-load-balancers \
  --names "$ALB_NAME" --region "$REGION" \
  --query "LoadBalancers[0].LoadBalancerArn" --output text 2>/dev/null || echo "None")

if [[ "$EXISTING_ALB" == "None" || -z "$EXISTING_ALB" ]]; then
  SUBNET_LIST=$(echo "$SUBNET_IDS" | tr ',' ' ')

  ALB_ARN=$(aws elbv2 create-load-balancer \
    --name "$ALB_NAME" \
    --subnets $SUBNET_LIST \
    --security-groups "$ECS_SG" \
    --scheme internet-facing \
    --type application \
    --ip-address-type ipv4 \
    --tags Key=Project,Value=colegio-ohiggins \
    --region "$REGION" \
    --query "LoadBalancers[0].LoadBalancerArn" --output text)

  ALB_DNS=$(aws elbv2 describe-load-balancers \
    --load-balancer-arns "$ALB_ARN" --region "$REGION" \
    --query "LoadBalancers[0].DNSName" --output text)

  success "ALB creado: $ALB_NAME"
  success "ALB DNS: $ALB_DNS"
else
  ALB_ARN="$EXISTING_ALB"
  ALB_DNS=$(aws elbv2 describe-load-balancers \
    --load-balancer-arns "$ALB_ARN" --region "$REGION" \
    --query "LoadBalancers[0].DNSName" --output text)
  success "ALB existente reutilizado: $ALB_DNS"
fi

# Target Group para el Frontend (puerto 8080)
TG_FRONTEND=$(aws elbv2 create-target-group \
  --name "colegio-frontend-tg" \
  --protocol HTTP --port 8080 \
  --vpc-id "$VPC_ID" \
  --target-type ip \
  --health-check-path "/" \
  --health-check-interval-seconds 30 \
  --region "$REGION" \
  --query "TargetGroups[0].TargetGroupArn" --output text 2>/dev/null || \
  aws elbv2 describe-target-groups --names "colegio-frontend-tg" --region "$REGION" \
    --query "TargetGroups[0].TargetGroupArn" --output text)

# Target Group para el API Gateway (puerto 8080)
TG_GATEWAY=$(aws elbv2 create-target-group \
  --name "colegio-gateway-tg" \
  --protocol HTTP --port 8080 \
  --vpc-id "$VPC_ID" \
  --target-type ip \
  --health-check-path "/actuator/health" \
  --health-check-interval-seconds 30 \
  --region "$REGION" \
  --query "TargetGroups[0].TargetGroupArn" --output text 2>/dev/null || \
  aws elbv2 describe-target-groups --names "colegio-gateway-tg" --region "$REGION" \
    --query "TargetGroups[0].TargetGroupArn" --output text)

# Listener HTTP:80 → Frontend TG (default)
aws elbv2 create-listener \
  --load-balancer-arn "$ALB_ARN" \
  --protocol HTTP --port 80 \
  --default-actions "Type=forward,TargetGroupArn=$TG_FRONTEND" \
  --region "$REGION" > /dev/null 2>&1 || warn "Listener :80 ya existe"

# Listener HTTP:8080 → Gateway TG
aws elbv2 create-listener \
  --load-balancer-arn "$ALB_ARN" \
  --protocol HTTP --port 8080 \
  --default-actions "Type=forward,TargetGroupArn=$TG_GATEWAY" \
  --region "$REGION" > /dev/null 2>&1 || warn "Listener :8080 ya existe"

success "Target Groups y Listeners configurados"

# ─────────────────────────────────────────────────────────────────
# PASO 7: Crear ECS Services
# ─────────────────────────────────────────────────────────────────
info "PASO 7: Creando ECS Services con alta disponibilidad..."

# Preparar network config (común a todos los services)
FIRST_SUBNET=$(echo "$SUBNET_IDS" | cut -d',' -f1)
NETWORK_CONFIG="awsvpcConfiguration={subnets=[$FIRST_SUBNET],securityGroups=[$ECS_SG],assignPublicIp=ENABLED}"

create_or_update_service() {
  local SERVICE_NAME="$1"
  local TASK_FAMILY="$2"
  local DESIRED_COUNT="$3"
  local EXTRA_CONFIG="${4:-}"

  EXISTING=$(aws ecs describe-services \
    --cluster "$CLUSTER_NAME" --services "$SERVICE_NAME" \
    --region "$REGION" \
    --query "services[0].status" --output text 2>/dev/null || echo "MISSING")

  if [[ "$EXISTING" == "ACTIVE" ]]; then
    aws ecs update-service \
      --cluster "$CLUSTER_NAME" \
      --service "$SERVICE_NAME" \
      --task-definition "$TASK_FAMILY" \
      --desired-count "$DESIRED_COUNT" \
      --region "$REGION" > /dev/null
    success "Service actualizado: $SERVICE_NAME"
  else
    aws ecs create-service \
      --cluster "$CLUSTER_NAME" \
      --service-name "$SERVICE_NAME" \
      --task-definition "$TASK_FAMILY" \
      --desired-count "$DESIRED_COUNT" \
      --launch-type FARGATE \
      --network-configuration "$NETWORK_CONFIG" \
      --deployment-configuration "maximumPercent=200,minimumHealthyPercent=50" \
      --deployment-controller type=ECS \
      --region "$REGION" \
      $EXTRA_CONFIG > /dev/null
    success "Service creado: $SERVICE_NAME (desired: $DESIRED_COUNT tasks)"
  fi
}

# Eureka (1 instancia — service registry no necesita HA en este contexto)
create_or_update_service "colegio-eureka"        "colegio-eureka-server"     1

# API Gateway (2 instancias — alta disponibilidad, con ALB)
create_or_update_service "colegio-gateway"       "colegio-api-gateway"       2 \
  "--load-balancers targetGroupArn=$TG_GATEWAY,containerName=api-gateway,containerPort=8080"

# ms-autenticacion (1 instancia)
create_or_update_service "colegio-autenticacion" "colegio-ms-autenticacion"  1

# ms-calificaciones (1 instancia)
create_or_update_service "colegio-calificaciones" "colegio-ms-calificaciones" 1

# Frontend (2 instancias — con ALB)
create_or_update_service "colegio-frontend"      "colegio-frontend"          2 \
  "--load-balancers targetGroupArn=$TG_FRONTEND,containerName=frontend,containerPort=8080"

# ─────────────────────────────────────────────────────────────────
# PASO 8: Resumen final
# ─────────────────────────────────────────────────────────────────
echo ""
echo -e "${GREEN}╔══════════════════════════════════════════════════════════╗"
echo -e "║          ✅ Clúster ECS Configurado Exitosamente        ║"
echo -e "╚══════════════════════════════════════════════════════════╝${NC}"
echo ""
echo -e "${CYAN}━━━ URLs de Acceso ━━━${NC}"
echo -e "  🌐 Frontend:          http://$ALB_DNS"
echo -e "  🔌 API Gateway:       http://$ALB_DNS:8080"
echo -e "  📋 Eureka Dashboard:  Acceso interno vía VPC"
echo ""
echo -e "${CYAN}━━━ Comandos Útiles ━━━${NC}"
echo -e "  # Ver estado de todos los servicios:"
echo -e "  aws ecs describe-services --cluster $CLUSTER_NAME --region $REGION \\"
echo -e "    --services colegio-eureka colegio-gateway colegio-autenticacion colegio-calificaciones colegio-frontend"
echo ""
echo -e "  # Ver tasks corriendo:"
echo -e "  aws ecs list-tasks --cluster $CLUSTER_NAME --region $REGION"
echo ""
echo -e "  # Escalar un servicio (ej: gateway a 3 instancias):"
echo -e "  aws ecs update-service --cluster $CLUSTER_NAME --service colegio-gateway --desired-count 3 --region $REGION"
echo ""
echo -e "  # Detener todos los services (0 tasks — economiza crédito):"
echo -e "  for svc in colegio-eureka colegio-gateway colegio-autenticacion colegio-calificaciones colegio-frontend; do"
echo -e "    aws ecs update-service --cluster $CLUSTER_NAME --service \$svc --desired-count 0 --region $REGION"
echo -e "  done"
echo ""
