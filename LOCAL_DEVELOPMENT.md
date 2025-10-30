# 로컬 개발 환경 설정 가이드 (Docker 없이)

Docker를 설치할 수 없는 환경에서 로컬 개발을 위한 가이드입니다.

## 사전 요구사항

- **Java 17 이상** 설치 필요
- **Gradle** (프로젝트에 Gradle Wrapper 포함)
- (선택) **Prometheus** - 메트릭 수집용
- (선택) **Grafana** - 모니터링 대시보드용

## 1. Spring Boot 애플리케이션 실행

### 방법 1: Gradle Wrapper 사용 (권장)

```bash
# 프로젝트 루트 디렉토리에서
./gradlew bootRun
```

### 방법 2: 빌드 후 실행

```bash
# 빌드
./gradlew clean build

# JAR 파일 실행
java -jar build/libs/cost-web-app-0.0.1-SNAPSHOT.jar
```

### 방법 3: IDE에서 실행

1. IntelliJ IDEA / Eclipse / VS Code에서 프로젝트 열기
2. `CostWebAppApplication.java` 파일에서 `main` 메서드 실행
3. 또는 IDE의 Run/Debug 기능 사용

## 2. 애플리케이션 확인

애플리케이션이 정상 실행되면 다음 URL로 접속 가능합니다:

- **애플리케이션**: http://localhost:8080
- **Health Check**: http://localhost:8080/actuator/health
- **API 엔드포인트**: http://localhost:8080/api/health
- **Prometheus 메트릭**: http://localhost:8080/actuator/prometheus
- **모든 메트릭**: http://localhost:8080/actuator/metrics

## 3. Prometheus 설정 (선택사항)

로컬에서 Prometheus를 사용하려면:

### 3.1 Prometheus 설치

#### macOS (Homebrew)
```bash
brew install prometheus
```

#### Linux
```bash
# 다운로드
wget https://github.com/prometheus/prometheus/releases/download/v2.48.0/prometheus-2.48.0.linux-amd64.tar.gz
tar xvfz prometheus-*.tar.gz
cd prometheus-*
```

#### Windows
[Prometheus 공식 사이트](https://prometheus.io/download/)에서 다운로드

### 3.2 Prometheus 실행

프로젝트 루트 디렉토리에서:

```bash
# prometheus-local.yml 파일을 사용하여 실행
prometheus --config.file=monitoring/prometheus-local.yml --storage.tsdb.path=./prometheus-data
```

또는 설치된 Prometheus의 경우:

```bash
# prometheus 실행 파일 경로에 따라 조정
./prometheus --config.file=monitoring/prometheus-local.yml --storage.tsdb.path=./prometheus-data
```

접속: http://localhost:9090

## 4. Grafana 설정 (선택사항)

로컬에서 Grafana를 사용하려면:

### 4.1 Grafana 설치

#### macOS (Homebrew)
```bash
brew install grafana
```

#### Linux
```bash
# Ubuntu/Debian
sudo apt-get update
sudo apt-get install -y software-properties-common
sudo add-apt-repository "deb https://packages.grafana.com/oss/deb stable main"
wget -q -O - https://packages.grafana.com/gpg.key | sudo apt-key add -
sudo apt-get update
sudo apt-get install grafana

# 또는 바이너리 다운로드
wget https://dl.grafana.com/oss/release/grafana-10.2.0.linux-amd64.tar.gz
tar -zxvf grafana-10.2.0.linux-amd64.tar.gz
cd grafana-10.2.0
```

#### Windows
[Grafana 공식 사이트](https://grafana.com/grafana/download)에서 다운로드

### 4.2 Grafana 실행

```bash
# macOS/Linux
grafana-server

# 또는 설치 경로에서
./bin/grafana-server
```

접속: http://localhost:3000
- 기본 ID: `admin`
- 기본 PW: `admin`

### 4.3 Grafana에서 Prometheus 데이터소스 설정

1. Grafana에 로그인
2. Configuration (톱니바퀴) → Data sources → Add data source
3. Prometheus 선택
4. URL: `http://localhost:9090` 입력
5. Save & Test 클릭

## 5. 개발 워크플로우

### 기본 개발 흐름

1. **코드 수정**
   ```bash
   # IDE에서 코드 편집
   ```

2. **애플리케이션 재시작**
   ```bash
   # Gradle bootRun 사용 시: Ctrl+C 후 다시 실행
   # 또는 IDE의 재시작 기능 사용
   ```

3. **테스트 실행**
   ```bash
   ./gradlew test
   ```

4. **빌드 확인**
   ```bash
   ./gradlew clean build
   ```

### Hot Reload (자동 재시작) 설정

Spring Boot DevTools를 추가하면 코드 변경 시 자동으로 재시작됩니다:

`build.gradle`에 추가:
```gradle
dependencies {
    // ... 기존 dependencies
    developmentOnly 'org.springframework.boot:spring-boot-devtools'
}
```

## 6. 프로파일 관리

로컬 개발 시 `local` 프로파일이 기본으로 활성화됩니다.

프로파일 확인:
```bash
# application.yml에서 확인
spring.profiles.active=local
```

다른 프로파일로 실행:
```bash
./gradlew bootRun --args='--spring.profiles.active=dev'
```

## 7. 트러블슈팅

### 포트 충돌
포트 8080이 이미 사용 중인 경우:
```bash
# application-local.yml 수정
server.port=8081
```

### 메모리 부족
Gradle 빌드 시 메모리 옵션 추가:
```bash
export GRADLE_OPTS="-Xmx2048m -XX:MaxPermSize=512m"
```

또는 `gradle.properties` 파일 생성:
```properties
org.gradle.jvmargs=-Xmx2048m -XX:MaxMetaspaceSize=512m
```

### Prometheus 연결 안 됨
1. Spring Boot 애플리케이션이 실행 중인지 확인
2. `http://localhost:8080/actuator/prometheus` 접속 가능한지 확인
3. Prometheus가 올바른 포트(9090)에서 실행 중인지 확인

## 8. 배포 전 체크리스트

로컬에서 개발한 코드를 배포하기 전:

1. **테스트 실행**
   ```bash
   ./gradlew test
   ```

2. **빌드 확인**
   ```bash
   ./gradlew clean build
   ```

3. **Health Check 확인**
   ```bash
   curl http://localhost:8080/actuator/health
   ```

4. **Git 커밋 및 푸시**
   ```bash
   git add .
   git commit -m "커밋 메시지"
   git push origin main
   ```

## 9. Docker 없는 환경 vs Docker 환경 비교

| 항목 | 로컬 (Docker 없음) | 배포 (Docker) |
|------|-------------------|---------------|
| 개발 환경 | Java + Gradle 직접 설치 | Docker Compose |
| 실행 방식 | `./gradlew bootRun` | `docker-compose up` |
| 모니터링 | 선택적 설치 (Prometheus/Grafana) | 자동 포함 |
| 장점 | 빠른 시작, 디버깅 용이 | 환경 일관성, 배포 준비 완료 |
| 단점 | 각자 설정 필요 | Docker 설치 필요 |

**권장**: 로컬 개발은 Docker 없이, 배포는 EC2에서 Docker 사용

## 10. 추가 리소스

- [Spring Boot 공식 문서](https://spring.io/projects/spring-boot)
- [Prometheus 설치 가이드](https://prometheus.io/docs/prometheus/latest/installation/)
- [Grafana 설치 가이드](https://grafana.com/docs/grafana/latest/setup-grafana/installation/)

