#!/usr/bin/env bash
# ══════════════════════════════════════════════════════════════════
# cluster-setup.sh — Provisioning del Clúster EKS + Repositorios ECR
# Colegio Bernardo O'Higgins — Sistema de Gestión Escolar
#
# USO:
#   export ACCOUNT_ID="$(aws sts get-caller-identity --query Account --output text)"
#   bash eks/cluster-setup.sh
#
# REQUISITOS:
#   - AWS CLI v2 configurado con credenciales activas
#   - eksctl instalado (https://eksctl.io)
#   - kubectl instalado
#   - Permisos: eks:*, ecr:*, ec2:*, iam:*, cloudformation:*
#
# COSTO ESTIMADO (AWS Academy):
#   ~$0.10/hora por nodo t3.medium + $0.10/hora EKS control plane
#   Opción Fargate Profile: solo pagas por pods (~$0.04/vCPU/hora)
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
CLUSTER_NAME="colegio-eks-cluster"
REGION="us-east-1"
K8S_VERSION="1.30"
NODE_TYPE="t3.medium"
NODE_COUNT=2
NAMESPACE="colegio"
ECR_PREFIX="colegio"

ACCOUNT_ID="${ACCOUNT_ID:-$(aws sts get-caller-identity --query Account --output text)}"
ECR_REGISTRY="${ACCOUNT_ID}.dkr.ecr.${REGION}.amazonaws.com"

echo -e "${CYAN}"
echo "╔══════════════════════════════════════════════════════════╗"
echo "║   EKS Cluster Setup — Colegio Bernardo O'Higgins        ║"
echo "║   Región:  ${REGION}                                 ║"
echo "║   Cuenta:  ${ACCOUNT_ID}                   ║"
echo "║   Cluster: ${CLUSTER_NAME}               ║"
echo "╚══════════════════════════════════════════════════════════╝"
echo -e "${NC}"

# ─────────────────────────────────────────────────────────────────
# PASO 1: Verificar herramientas requeridas
# ─────────────────────────────────────────────────────────────────
info "PASO 1: Verificando herramientas..."

command -v aws    &>/dev/null || error "AWS CLI no encontrado. Instala aws-cli v2."
command -v eksctl &>/dev/null || error "eksctl no encontrado. Instala desde https://eksctl.io"
command -v kubectl &>/dev/null || error "kubectl no encontrado."

success "AWS CLI: $(aws --version | head -1)"
success "eksctl: $(eksctl version)"
success "kubectl: $(kubectl version --client --short 2>/dev/null || kubectl version --client)"

# ─────────────────────────────────────────────────────────────────
# PASO 2: Crear Repositorios ECR
# ─────────────────────────────────────────────────────────────────
info "PASO 2: Creando repositorios ECR..."

SERVICES=(
  "colegio-api-gateway"
  "colegio-eureka-server"
  "colegio-ms-autenticacion"
  "colegio-ms-calificaciones"
  "colegio-frontend"
)

for repo in "${SERVICES[@]}"; do
  EXISTING=$(aws ecr describe-repositories \
    --repository-names "${ECR_PREFIX}/${repo}" \
    --region "$REGION" \
    --query "repositories[0].repositoryUri" \
    --output text 2>/dev/null || echo "MISSING")

  if [[ "$EXISTING" == "MISSING" || -z "$EXISTING" ]]; then
    URI=$(aws ecr create-repository \
      --repository-name "${ECR_PREFIX}/${repo}" \
      --region "$REGION" \
      --image-scanning-configuration scanOnPush=true \
      --tags Key=Project,Value=colegio-ohiggins \
      --query "repository.repositoryUri" \
      --output text)
    success "Repositorio ECR creado: $URI"
  else
    success "Repositorio ECR ya existe: $EXISTING"
  fi

  # Política de lifecycle: mantener solo últimas 10 imágenes
  aws ecr put-lifecycle-policy \
    --repository-name "${ECR_PREFIX}/${repo}" \
    --region "$REGION" \
    --lifecycle-policy-text '{
      "rules": [{
        "rulePriority": 1,
        "description": "Mantener últimas 10 imágenes",
        "selection": {
          "tagStatus": "tagged",
          "tagPrefixList": ["sha-"],
          "countType": "imageCountMoreThan",
          "countNumber": 10
        },
        "action": {"type": "expire"}
      }]
    }' 2>/dev/null || true
done

echo ""
info "URIs de repositorios ECR:"
for repo in "${SERVICES[@]}"; do
  echo "  ${ECR_REGISTRY}/${ECR_PREFIX}/${repo}"
done
echo ""

# ─────────────────────────────────────────────────────────────────
# PASO 3: Crear Cluster EKS con eksctl
# ─────────────────────────────────────────────────────────────────
info "PASO 3: Verificando/Creando cluster EKS: ${CLUSTER_NAME}..."

CLUSTER_STATUS=$(aws eks describe-cluster \
  --name "$CLUSTER_NAME" \
  --region "$REGION" \
  --query "cluster.status" \
  --output text 2>/dev/null || echo "NOT_FOUND")

if [[ "$CLUSTER_STATUS" == "ACTIVE" ]]; then
  success "Cluster EKS ya existe y está ACTIVE. Saltando creación."
else
  info "Creando cluster EKS (esto tarda ~15-20 minutos)..."

  eksctl create cluster \
    --name "$CLUSTER_NAME" \
    --region "$REGION" \
    --version "$K8S_VERSION" \
    --nodegroup-name colegio-nodes \
    --node-type "$NODE_TYPE" \
    --nodes "$NODE_COUNT" \
    --nodes-min 1 \
    --nodes-max 3 \
    --managed \
    --asg-access \
    --external-dns-access \
    --full-ecr-access \
    --appmesh-access \
    --alb-ingress-access \
    --tags "Project=colegio-ohiggins,Environment=production"

  success "Cluster EKS creado: ${CLUSTER_NAME}"
fi

# ─────────────────────────────────────────────────────────────────
# PASO 4: Actualizar kubeconfig
# ─────────────────────────────────────────────────────────────────
info "PASO 4: Actualizando kubeconfig..."

aws eks update-kubeconfig \
  --name "$CLUSTER_NAME" \
  --region "$REGION"

success "kubeconfig actualizado para cluster: ${CLUSTER_NAME}"

# Verificar conectividad
kubectl get nodes
echo ""

# ─────────────────────────────────────────────────────────────────
# PASO 5: Crear Namespace y aplicar manifests base
# ─────────────────────────────────────────────────────────────────
info "PASO 5: Creando namespace '${NAMESPACE}'..."

MANIFEST_DIR="$(dirname "$0")/k8s-manifests"

kubectl apply -f "${MANIFEST_DIR}/namespace.yaml"
success "Namespace '${NAMESPACE}' listo."

# ─────────────────────────────────────────────────────────────────
# PASO 6: Crear Secret para ECR (pull de imágenes)
# ─────────────────────────────────────────────────────────────────
info "PASO 6: Configurando acceso a ECR desde los pods..."

# En EKS con nodegroup managed, los nodos ya tienen acceso a ECR via IAM role.
# Solo necesitamos confirmar que el node role tiene la policy AmazonEC2ContainerRegistryReadOnly.
NODE_ROLE=$(aws eks describe-nodegroup \
  --cluster-name "$CLUSTER_NAME" \
  --nodegroup-name colegio-nodes \
  --region "$REGION" \
  --query "nodegroup.nodeRole" \
  --output text 2>/dev/null || echo "NOT_FOUND")

if [[ "$NODE_ROLE" != "NOT_FOUND" ]]; then
  ROLE_NAME=$(basename "$NODE_ROLE")
  aws iam attach-role-policy \
    --role-name "$ROLE_NAME" \
    --policy-arn "arn:aws:iam::aws:policy/AmazonEC2ContainerRegistryReadOnly" \
    2>/dev/null && success "Policy ECR ReadOnly adjuntada al node role: $ROLE_NAME" \
    || warn "Policy ECR ya estaba adjuntada (OK)."
fi

# ─────────────────────────────────────────────────────────────────
# PASO 7: Resumen final
# ─────────────────────────────────────────────────────────────────
echo ""
echo -e "${GREEN}╔══════════════════════════════════════════════════════════╗"
echo -e "║        ✅ Cluster EKS Configurado Exitosamente          ║"
echo -e "╚══════════════════════════════════════════════════════════╝${NC}"
echo ""
echo -e "${CYAN}━━━ Repositorios ECR ━━━${NC}"
for repo in "${SERVICES[@]}"; do
  echo "  📦 ${ECR_REGISTRY}/${ECR_PREFIX}/${repo}"
done
echo ""
echo -e "${CYAN}━━━ Comandos Útiles ━━━${NC}"
echo -e "  # Ver nodos del cluster:"
echo -e "  kubectl get nodes"
echo ""
echo -e "  # Ver pods del namespace colegio:"
echo -e "  kubectl get pods -n ${NAMESPACE}"
echo ""
echo -e "  # Ver servicios (LoadBalancers):"
echo -e "  kubectl get svc -n ${NAMESPACE}"
echo ""
echo -e "  # Ver logs de un pod:"
echo -e "  kubectl logs -n ${NAMESPACE} deployment/api-gateway"
echo ""
echo -e "  # Login a ECR:"
echo -e "  aws ecr get-login-password --region ${REGION} | docker login --username AWS --password-stdin ${ECR_REGISTRY}"
echo ""
echo -e "${YELLOW}⚠️  Próximo paso: Aplicar los manifests de Kubernetes:${NC}"
echo -e "  kubectl apply -f eks/k8s-manifests/"
echo ""
echo -e "${YELLOW}⚠️  Recuerda configurar el Secret en GitHub Actions:${NC}"
echo -e "  AWS_ACCOUNT_ID = ${ACCOUNT_ID}"
echo ""
