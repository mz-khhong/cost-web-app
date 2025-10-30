# Cost Web App

AWS + Spring Boot + GitOps 체계를 통해 비용 최적화된 웹 애플리케이션 서비스 및 Grafana를 통한 모니터링 시스템입니다.

## 기술 스택

- **Spring Boot 3.2.0** (Java 17)
- **Gradle** (빌드 도구)
- **Docker** & **Docker Compose**
- **Prometheus** (메트릭 수집)
- **Grafana** (모니터링 대시보드)
- **GitHub Actions** (GitOps 자동 배포)

## 프로젝트 구조

```
cost-web-app/
├── src/
│   └── main/
│       ├── java/com/cost/app/
│       │   ├── CostWebAppApplication.java
│       │   └── controller/
│       │       └── HealthController.java
│       └── resources/
│           └── application.yml
├── monitoring/
│   ├── prometheus.yml
│   └── grafana/
│       ├── datasources/
│       │   └── prometheus.yml
│       └── dashboards/
│           └── dashboard.yml
├── build.gradle
├── Dockerfile
├── docker-compose.yml
└── .github/workflows/
    └── deploy.yml
```

## 로컬 실행 방법

### Docker를 사용하는 경우

#### 사전 요구사항
- Docker & Docker Compose
- Java 17 이상 (로컬 빌드용)

#### Docker Compose로 실행

```bash
# 전체 스택 실행 (Spring Boot + Prometheus + Grafana)
docker-compose up -d --build

# 로그 확인
docker-compose logs -f

# 중지
docker-compose down
```

### Docker를 사용하지 않는 경우 (로컬 개발)

#### 사전 요구사항
- Java 17 이상
- Gradle (프로젝트에 Gradle Wrapper 포함)

#### 애플리케이션만 실행

```bash
# 빌드
./gradlew clean build

# 실행
./gradlew bootRun
```

접속: http://localhost:8080
- Health Check: http://localhost:8080/actuator/health
- Prometheus 메트릭: http://localhost:8080/actuator/prometheus

**상세 가이드**: [로컬 개발 환경 설정 가이드](LOCAL_DEVELOPMENT.md) 참조
- Prometheus/Grafana 설치 및 실행 방법
- Docker 없이 모니터링 설정하는 방법
- 개발 워크플로우

## 서비스 접속 정보

- **Spring Boot 애플리케이션**: http://localhost:8080
  - Health Check: http://localhost:8080/actuator/health
  - API: http://localhost:8080/api/health
  - Prometheus 메트릭: http://localhost:8080/actuator/prometheus

- **Grafana**: http://localhost:3000
  - 기본 ID: admin
  - 기본 PW: admin
  - 자세한 사용 방법: [Grafana 가이드](GRAFANA_GUIDE.md) 참조

- **Prometheus**: http://localhost:9090

## GitOps 배포

프로젝트는 두 가지 GitOps 방식을 지원합니다:

### 방식 1: 단순 GitOps (현재 기본)

GitHub Actions를 통해 `main` 브랜치에 푸시하면 자동으로 EC2 서버에 배포됩니다.

#### GitHub Secrets 설정

다음 Secrets을 GitHub 저장소에 설정해야 합니다:

- `EC2_HOST_DEV`: EC2 인스턴스의 호스트 주소 (예: ec2-xx-xx-xx-xx.compute-1.amazonaws.com)
- `EC2_SSH_KEY_DEV`: EC2 인스턴스 접속용 SSH Private Key

#### 배포 프로세스

1. 코드 변경 후 `main` 브랜치에 푸시
2. GitHub Actions가 자동으로 트리거됨
3. EC2 서버에 SSH 접속
4. 프로젝트 디렉토리에서 `git pull` 실행
5. `docker-compose down` 후 `docker-compose up -d --build` 실행

### 방식 2: Kubernetes 기반 GitOps (고급) ⚙️

더 강력한 GitOps 기능(자동 롤백, 선언적 구성, 상태 동기화)을 원한다면 Kubernetes 환경 구축을 고려하세요.

**상세 가이드**: [Kubernetes GitOps 가이드](KUBERNETES_GITOPS.md) 참조

비용 효율적인 옵션:
- **k3s** (추천): EC2에 직접 설치, 클러스터 비용 무료
- **AWS EKS**: 관리형 서비스, 약 $73/월

주요 구성:
- **k3s**: 경량 Kubernetes 클러스터
- **ArgoCD**: GitOps 도구 (자동 동기화, 롤백)
- Kubernetes 매니페스트: `k8s/base/` 디렉토리

## EC2 서버 초기 설정

### Docker 사용하는 경우

EC2 서버에서 다음 명령어를 실행하여 프로젝트를 클론하고 설정합니다:

```bash
# 프로젝트 디렉토리 생성
mkdir -p /home/ubuntu/cost-web-app
cd /home/ubuntu/cost-web-app

# Git 저장소 클론
git clone https://github.com/mz-khhong/cost-web-app.git .

# Docker 및 Docker Compose 설치 확인
docker --version
docker-compose --version

# 환경 변수 설정 (필요시)
# application-prod.yml 파일 생성 등

# 초기 실행
docker-compose up -d --build
```

### Docker 없이 직접 설치하는 경우

EC2 인스턴스에 Java, Prometheus, Grafana를 직접 설치하는 방법:

**상세 가이드**: [EC2_DIRECT_INSTALL.md](EC2_DIRECT_INSTALL.md) 참조

간단한 설치 (자동화 스크립트):

```bash
# EC2 인스턴스에 SSH UC-0
# 프로젝트 클론
mkdir -p /home/ubuntu/cost-web-app
cd /home/ubuntu/cost-web-app
git clone https://github.com/mz-khhong/cost-web-app.git .

# 초기 설정 스크립트 실행 (sudo 권한 필요)
sudo bash scripts/setup-ec2.sh

# 애플리케이션 배포
./gradlew clean build
sudo systemctl start cost-web-app
```

또는 수동 설치 방법은 `EC2_DIRECT_INSTALL.md` 문서를 참조하세요.

## 모니터링 설정

### Prometheus 메트릭 수집

Spring Boot Actuator가 `/actuator/prometheus` 엔드포인트를 통해 메트릭을 제공하며, Prometheus가 이를 자동으로 수집합니다.

### Grafana 대시보드

Grafana에서 Prometheus 데이터소스를 사용하여 대시보드를 구성할 수 있습니다.
기본적으로 Prometheus 데이터소스가 자동으로 설정됩니다.

**Grafana 모니터링 사용 방법**: [GRAFANA_GUIDE.md](GRAFANA_GUIDE.md) 문서를 참조하세요.

## 주요 기능

- **Health Check**: `/actuator/health` 엔드포인트를 통한 애플리케이션 상태 확인
- **메트릭 수집**: Prometheus를 통한 애플리케이션 메트릭 수집
- **자동 배포**: GitHub Actions를 통한 GitOps 기반 자동 배포
- **컨테이너 기반**: Docker를 통한 환경 독립성 보장

## 비용 최적화 포인트

- Multi-stage Docker 빌드를 통한 이미지 크기 최적화
- Alpine Linux 기반 경량 런타임 이미지 사용
- Health Check를 통한 안정적인 컨테이너 관리
- 자동화된 배포를 통한 운영 효율성 향상
