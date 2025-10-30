# Kubernetes 기반 GitOps 가이드

## GitOps와 Kubernetes 관계

GitOps는 Kubernetes 없이도 가능하지만, Kubernetes 환경에서 더 강력하고 표준화된 방식으로 구현할 수 있습니다.

### 현재 방식 (단순 GitOps)
- GitHub Actions → SSH → EC2에서 `docker-compose up`
- **장점**: 간단, 비용 저렴
- **단점**: 롤백/병렬 배포/자동 복구 등 고급 기능 부족

### Kubernetes 기반 GitOps
- Git 저장소 변경 → ArgoCD/Flux 감지 → Kubernetes 자동 배포
- **장점**: 자동 롤백, 선언적 구성, 상태 동기화, 멀티 환경 관리
- **단점**: 학습 곡선, 인프라 관리 필요

## 비용 비교

### 옵션 1: AWS EKS (관리형)
- **클러스터 비용**: 약 $73/월 (시간당 $0.10)
- **워커 노드**: EC2 인스턴스 비용 별도
- **장점**: 완전 관리형, 자동 업데이트
- **단점**: 높은 비용

### 옵션 2: k3s (EC2에 직접 설치) ⭐ **비용 최적화 추천**
- **클러스터 비용**: **무료**
- **워커 노드**: EC2 인스턴스 비용만 (t3.medium 정도면 충분)
- **장점**: 가볍고 빠름, 설정 간단, 프로덕션급 안정성
- **단점**: 수동 업데이트 필요

### 옵션 3: kubeadm (표준 Kubernetes)
- **클러스터 비용**: **무료**
- **워커 노드**: EC2 인스턴스 비용만
- **장점**: 표준 Kubernetes, 유연성
- **단점**: 설정 복잡, 운영 노하우 필요

## 추천: k3s + ArgoCD 조합

비용 최적화를 고려한 추천 구성입니다.

### 아키텍처

```
GitHub Repository
    ↓
ArgoCD (k3s 클러스터에서 실행)
    ↓
Kubernetes API
    ↓
Pod (Spring Boot App)
    ↓
Service → LoadBalancer/Ingress
```

## k3s 클러스터 구축 가이드

### 1. EC2 인스턴스 준비

최소 사양:
- **인스턴스 타입**: t3.medium 이상 (2 vCPU, 4GB RAM)
- **OS**: Ubuntu 20.04 LTS 이상
- **보안 그룹**: 6443 (k3s), 80, 443, 30880 (ArgoCD) 포트 오픈

### 2. k3s 설치 (단일 노드, 소규모 환경용)

```bash
# k3s 설치 (서버 모드)
curl -sfL https://get.k3s.io | sh -

# 설치 확인
sudo k3s kubectl get nodes

# kubeconfig 권한 설정
mkdir -p ~/.kube
sudo cp /etc/rancher/k3s/k3s أولوب~/~/.kube/config
sudo chown $USER:$USER ~/.kube/config

# kubectl 별칭 설정
echo 'alias k=kubectl' >> ~/.bashrc
source ~/.bashrc
```

### 3. ArgoCD 설치

```bash
# ArgoCD 네임스페이스 생성
kubectl create namespace argocd

# ArgoCD 설치
kubectl apply -n argocd -f https://raw.githubusercontent.com/argoproj/argo-cd/stable/manifests/install.yaml

# 설치 확인 (약 1-2분 소요)
kubectl get pods -n argocd -w

# ArgoCD 서버 접근을 위한 포트 포워딩
kubectl port-forward svc/argocd-server -n argocd 8080:443 &
```

초기 비밀번호 확인:
```bash
kubectl -n argocd get secret argocd-initial-admin-secret -o jsonpath="{.data.password}" | base64 -d
echo
```

ArgoCD 접속: https://localhost:8080
- 사용자명: `admin`
- 비밀번호: 위에서 확인한 값

### 4. 애플리케이션 Kubernetes 매니페스트 작성

프로젝트에 Kubernetes 매니페스트 디렉토리 생성:

```bash
mkdir -p k8s/base
```

`k8s/base/deployment.yaml`:
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: cost-web-app
  labels:
    app: cost-web-app
spec:
  replicas: 2
  selector:
    matchLabels:
      app: cost-web-app
  template:
    metadata:
      labels:
        app: cost-web-app
    spec:
      containers:
      - name: cost-web-app
        image: cost-web-app:latest  # 실제 이미지는 Docker Hub나 ECR 사용
        ports:
        - containerPort: 8080
        env:
        - name: SPRING_PROFILES_ACTIVE
          value: "prod"
        resources:
          requests:
            memory: "512Mi"
            cpu: "500m"
          limits:
            memory: "1Gi"
            cpu: "1000m"
        livenessProbe:
          httpGet:
            path: /actuator/health
            port: 8080
          initialDelaySeconds: 60
          periodSeconds: 10
        readinessProbe:
          httpGet:
            path: /actuator/health
            port: 8080
          initialDelaySeconds: 30
          periodSeconds: 5
```

`k8s/base/service.yaml`:
```yaml
apiVersion: v1
kind: Service
metadata:
  name: cost-web-app
spec:
  type: NodePort  # 또는 LoadBalancer (AWS ELB 사용 시)
  selector:
    app: cost-web-app
  ports:
  - port: 80
    targetPort: 8080
    nodePort: 30080
```

`k8s/base/kustomization.yaml`:
```yaml
apiVersion: kustomize.config.k8s.io/v1beta1
kind: Kustomization

resources:
  - deployment.yaml
  - service.yaml

commonLabels:
  app: cost-web-app
  environment: production

images:
  - name: cost-web-app
    newName: your-docker-registry/cost-web-app
    newTag: latest
```

### 5. Docker 이미지 빌드 및 푸시

GitHub Actions에서 이미지 빌드 후 컨테이너 레지스트리에 푸시:

`.github/workflows/build-and-push.yml`:
```yaml
name: Build and Push Docker Image

on:
  push:
    branches:
      - main

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      
      - name: Set up Docker Buildx
        uses: docker/setup-buildx-action@v2
      
      - name: Login to Docker Hub  # 또는 AWS ECR
        uses: docker/login-action@v2
        with:
          username: ${{ secrets.DOCKER_USERNAME }}
          password: ${{ secrets.DOCKER_PASSWORD }}
      
      - name: Build and push
        uses: docker/build-push-action@v4
        with:
          context: .
          push: true
          tags: your-docker-registry/cost-web-app:latest
```

### 6. ArgoCD Application 등록

#### 방법 1: ArgoCD UI 사용

1. ArgoCD 웹 콘솔 접속
2. **New App** 클릭
3. 다음 정보 입력:
   - **Application Name**: `cost-web-app`
   - **Project**: `default`
   - **Sync Policy**: `Automatic` (선택)
   - **Repository URL**: `https://github.com/mz-khhong/cost-web-app.git`
   - **Path**: `k8s/base`
   - **Cluster URL**: `https://kubernetes.default.svc`
   - **Namespace**: `default`

#### 방법 2: ArgoCD CLI 사용

```bash
# ArgoCD CLI 설치
brew install argocd  # macOS
# 또는
curl -sSL -o /usr/local/bin/argocd https://github.com/argoproj/argo-cd/releases/latest/download/argocd-linux-amd64
chmod +x /usr/local/bin/argocd

# ArgoCD 로그인
argocd login localhost:8080

# Application 생성
argocd app create cost-web-app \
  --repo https://github.com/mz-khhong/cost-web-app.git \
  --path k8s/base \
  --dest-server https://kubernetes.default.svc \
  --dest-namespace default \
  --sync-policy automated \
  --self-heal \
  --auto-prune
```

#### 방법 3: GitOps 방식 (App of Apps 패턴)

`k8s/thin/argocd-application.yaml`:
```yaml
apiVersion: argoproj.io/v1alpha1
kind: Application
metadata:
  name: cost-web-app
  namespace: argocd
spec:
  project: default
  source:
    repoURL: https://github.com/mz-khhong/cost-web-app.git
    targetRevision: main
    path: k8s/base
  destination:
    server: https://kubernetes.default.svc
    namespace: default
  syncPolicy:
    automated:
      prune: true
      selfHeal: true
    syncOptions:
      - CreateNamespace=true
```

이 파일을 ArgoCD가 관리하는 별도 저장소에 배치하거나, 현재 저장소의 `constants/argocd/` 디렉토리에 배치하고 ArgoCD App of Apps로 관리.

## 전체 워크플로우

```
1. 개발자가 코드 작성
   ↓
2. GitHub에 push
   ↓
3. GitHub Actions가 Docker 이미지 빌드 및 푸시
   ↓
4. ArgoCD가 Git 저장소 변경 감지
   ↓
5. ArgoCD가 Kubernetes 클러스터에 자동 배포
   ↓
6. 롤링 업데이트, 헬스 체크, 자동 복구
```

## 주요 GitOps 도구 비교

### ArgoCD
- **장점**: UI 제공, 쉬운 사용, 강력한 기능
- **단점**: 리소스 사용량이 큼
- **적합**: UI가 필요하고 빠른 시작을 원할 때

### Flux
- **장점**: 가볍고, GitOps 원칙에 더 충실
- **단점**: CLI 중심, 학습 곡선 있음
- **적합**: GitOps 원칙을 엄격히 따르고 싶을 때

### GitOps with GitHub Actions
- **장점**: 이미 사용 중, 간단
- **단점**: GitOps 도구만큼 강력하지 않음

## 비용 최적화 팁

1. **단일 노드 k3s**: 개발/스테이징 환경
2. **t3.medium 인스턴스**: 워커 노드로 충분
3. **Spot 인스턴스**: 비중요 워크로드에 사용
4. **압축 이미지**: Multi-stage 빌드로 이미지 크기 최소화
5. **HPA (Horizontal Pod Autoscaler)**: 트래픽에 따라 자동 스케일링

## 모니터링 통합

Prometheus와 Grafana는 Kubernetes 환경에서도 동일하게 작동:
- Prometheus Operator 사용 권장
- ServiceMonitor로 애플리케이션 메트릭 자동 수집

## 다음 단계

1. 단일 EC2에 k3s 설치하여 테스트
2. ArgoCD 설치 및 애플리케이션 등록
3. 간단한 애플리케이션으로 배포 테스트
4. 프로덕션 적용 고려

## 참고 자료

- [k3s 공식 문서](https://k3s.io/)
- [ArgoCD 공식 문서](https://argo-cd.readthedocs.io/)
- [Kubernetes 공식 문서](https://kubernetes.io/docs/)

