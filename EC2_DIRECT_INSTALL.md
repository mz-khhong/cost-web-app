# EC2 인스턴스 직접 설치 가이드 (Docker 없이)

EC2 인스턴스에 Docker 없이 Java 애플리케이션, Prometheus, Grafana를 직접 설치하는 방법입니다.

## 사전 준비

- EC2 Ubuntu 인스턴스 (20.04 LTS 이상 권장)
- SSH 접속 권한
- sudo 권한

## 1. Java 17 설치

### Ubuntu/Debian

```bash
# 패키지 업데이트
sudo apt update

# Java 17 설치 (OpenJDK)
sudo apt install -y openjdk-17-jdk

# 또는 Amazon Corretto (AWS 권장)
sudo apt install -y wget
wget -O- https://apt.corretto.aws/corretto.key | sudo apt-key add -
sudo add-apt-repository 'deb https://apt闲retto.aws stable main'
sudo apt update
sudo apt install -y java-17-amazon-corretto-jdk

# Java 버전 확인
java -version
javac -version

# JAVA_HOME 설정
echo 'export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64' | sudo tee -a /etc/environment
# 또는 Corretto의 경우
# echo 'export JAVA_HOME=/usr/lib/jvm/java-17-amazon-corretto' | sudo tee -a /etc/environment

source /etc/environment
```

## 2. Gradle 설치 (선택사항)

프로젝트에 Gradle Wrapper가 포함되어 있지만, 시스템 전역 설치도 가능합니다:

```bash
# Gradle 설치
wget https://services.gradle.org/distributions/gradle-8.5-bin.zip
sudo unzip -d /opt gradle-8.5-bin.zip

# 환경 변수 설정
echo 'export PATH=$PATH:/opt/gradle-8.5/bin' | sudo tee -a /etc/environment
source /etc/environment

gradle -v
```

## 3. Prometheus 설치

```bash
# Prometheus 사용자 생성
sudo useradd --no-create-home --shell /bin/false prometheus

# 디렉토리 생성
sudo mkdir /etc/prometheus
sudo mkdir /var/lib/prometheus

# Prometheus 다운로드
cd /tmp
wget https://github.com/prometheus/prometheus/releases/download/v2.48.0/prometheus-2.48.0.linux-amd64.tar.gz
tar -xvf prometheus-2.48.0.linux-amd64.tar.gz
cd prometheus-2.48.0.linux-amd64

# 파일 복사
sudo cp prometheus /usr/local/bin/
sudo cp promtool /usr/local/bin/
sudo chown prometheus:prometheus /usr/local/bin/prometheus
sudo chown prometheus:prometheus /usr/local/bin/promtool

# 설정 파일 복사
sudo cp -r consoles /etc/prometheus
sudo cp -r console_libraries /etc/prometheus
sudo chown -R prometheus:prometheus /etc/prometheus
sudo chown -R prometheus:prometheus /var/lib/prometheus
```

### Prometheus 설정 파일 생성

```bash
sudo nano /etc/prometheus/prometheus.yml
```

다음 내용 입력:

```yaml
global:
  scrape_interval: 15s
  evaluation_interval: 15s

scrape_configs:
  - job_name: 'cost-web-app'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['localhost:8080']
        labels:
          application: 'cost-web-app'

  - job_name: 'prometheus'
    static_configs:
      - targets: ['localhost:9090']
```

### Prometheus systemd 서비스 생성

```bash
sudo nano /etc/systemd/system/prometheus.service
```

다음 내용 입력:

```ini
[Unit]
Description=Prometheus
Wants=network-online.target
After=network-online.target

[Service]
User=prometheus
Group=prometheus
Type=simple
ExecStart=/usr/local/bin/prometheus \
    --config.file /etc/prometheus/prometheus.yml \
    --storage.tsdb.path /var/lib/prometheus/ \
    --web.console.templates=/etc/prometheus/consoles \
    --web.console.libraries=/etc/prometheus/console_libraries \
    --web.listen-address=0.0.0.0:9090 \
    --web.enable-lifecycle

Restart=always

[Install]
WantedBy=multi-user.target
```

서비스 시작:

```bash
sudo systemctl daemon-reload
sudo systemctl enable prometheus
sudo systemctl start prometheus
sudo systemctl status prometheus
```

## 4. Grafana 설치

### Ubuntu/Debian

```bash
# 필요한 패키지 설치
sudo apt-get install -y software-properties-common

# Grafana GPG 키 추가
sudo wget -q -O /usr/share/keyrings/grafana.key https://apt.grafana.com/gpg.key

# 저장소 추가
echo "deb [signed-by=/usr/share/keyrings/grafana.key] https://apt.grafana.com stable main" | sudo tee -a /etc/apt/sources.list.d/grafana.list

# 업데이트 및 설치
sudo apt-get update
sudo apt-get install -y grafana

# 서비스 시작
sudo systemctl daemon-reload
sudo systemctl enable grafana-server
sudo systemctl start grafana-server
sudo systemctl status grafana-server
```

### Grafana 초기 설정

1. Grafana 접속: http://<EC2_IP>:3000
   - 기본 ID: `admin`
   - 기본 PW: `admin`

2. Prometheus 데이터소스 추가:
   - Configuration → Data sources → Add data source
   - Prometheus 선택
   - URL: `http://localhost:9090`
   - Save & Test

## 5. 애플리케이션 배포 및 실행

### 프로젝트 디렉토리 설정

```bash
# 프로젝트 디렉토리 생성
mkdir -p /home/ubuntu/cost-web-app
cd /home/ubuntu/cost-web-app

# Git 저장소 클론
git clone https://github.com/mz-khhong/cost-web-app.git .

# 애플리케이션 빌드
./gradlew clean build -x test
```

### systemd 서비스 생성

```bash
sudo nano /etc/systemd/system/cost-web-app.service
```

다음 내용 입력:

```ini
[Unit]
Description=Cost Web App
After=network.target

[Service]
Type=simple
User=ubuntu
WorkingDirectory=/home/ubuntu/cost-web-app
ExecStart=/usr/bin/java -jar /home/ubuntu/cost-web-app/build/libs/cost-web-app-0.0.1-SNAPSHOT.jar
Restart=always
RestartSec=10
StandardOutput=journal
StandardError=journal

Environment="SPRING_PROFILES_ACTIVE=prod"
Environment="SERVER_PORT=8080"

[Install]
WantedBy=multi-user.target
```

서비스 시작:

```bash
sudo systemctl daemon-reload
sudo systemctl enable cost-web-app
sudo systemctl start cost-web-app
sudo systemctl status cost-web-app
```

### 로그 확인

```bash
# 실시간 로그 확인
sudo journalctl -u cost-web-app -f

# 최근 로그 확인
sudo journalctl -u cost-web-app -n 100
```

## 6. 방화벽 설정 (Security Group)

EC2 Security Group에서 다음 포트 열기:
- **8080**: Spring Boot 애플리케이션
- **9090**: Prometheus
- **3000**: Grafana
- **22**: SSH (이미 열려있을 것)

### AWS Security Group 설정

1. EC2 콘솔 → Security Groups
2. 인스턴스에 연결된 Security Group 선택
3. Inbound rules → Edit inbound rules
4. 다음 규칙 추가:
   - Type: Custom TCP, Port: 8080, Source: 0.0.0.0/0 (또는 필요한 IP만)
   - Type: Custom TCP, Port: 9090, Source: 0.0.0.0/0
   - Type: Custom TCP, Port: 3000, Source: 0.0.0.0/0

### UFW 방화벽 설정 (필요시)

```bash
sudo ufw allow 22/tcp
sudo ufw allow 8080/tcp
sudo ufw allow 9090/tcp
sudo ufw allow 3000/tcp
sudo ufw enable
sudo ufw status
```

## 7. 배포 스크립트 작성

자동 배포를 위한 스크립트 생성:

```bash
nano /home/ubuntu/cost-web-app/deploy.sh
```

다음 내용 입력:

```bash
#!/bin/bash

set -e

cd /home/ubuntu/cost-web-app

echo "Pulling latest changes..."
git pull origin main

echo "Building application..."
./gradlew clean build -x test

echo "Restarting service..."
sudo systemctl restart cost-web-app

echo "Deployment completed!"
sudo systemctl status cost-web-app
```

실행 권한 부여:

```bash
chmod +x /home/ubuntu/cost-web-app/deploy.sh
```

## 8. 애플리케이션 설정 파일

프로덕션 환경 설정 파일 생성:

```bash
nano /home/ubuntu/cost-web-app/src/main/resources/application-prod.yml
```


```yaml
server:
  port: 8080

spring:
  application:
    name: cost-web-app

management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus,metrics
  metrics:
    export:
      prometheus:
        enabled: true
  endpoint:
    health:
      show-details: always

logging:
  level:
    root: INFO
    com.cost: INFO
  file:
    name: /var/log/cost-web-app/application.log
```

로그 디렉토리 생성:

```bash
sudo mkdir -p /var/log/cost-web-app
sudo chown ubuntu:ubuntu /var/log/cost-web-app
```

## 9. 서비스 관리 명령어

### 애플리케이션
```bash
# 시작
sudo systemctl start cost-web-app

# 중지
sudo systemctl stop cost-web-app

# 재시작
sudo systemctl restart cost-web-app

# 상태 확인
sudo systemctl status cost-web-app

# 로그 확인
sudo journalctl -u cost-web-app -f
```

### Prometheus
```bash
sudo systemctl start prometheus
sudo systemctl stop prometheus
sudo systemctl restart prometheus
sudo systemctl status prometheus
```

### Grafana
```bash
sudo systemctl start grafana-server
sudo systemctl stop grafana-server
sudo systemctl restart grafana-server
sudo systemctl status grafana-server
```

## 10. GitHub Actions 배포 워크플로우 업데이트

`.github/workflows/deploy.yml` 파일을 Docker 없이 배포하도록 수정:

```yaml
name: Deploy to EC2

on:
  push:
    branches:
      - main

jobs:
  deploy:
    runs-on: ubuntu-latest
    
    steps:
      - name: Deploy via SSH
        uses: appleboy/ssh-action@v1.0.0
        with:
          host: ${{ secrets.EC2_HOST_DEV }}
          username: ubuntu
          key: ${{ secrets.EC2_SSH_KEY_DEV }}
          script: |
            cd /home/ubuntu/cost-web-app
            git pull origin main
            ./gradlew clean build -x test
            sudo systemctl restart cost-web-app
            sudo systemctl status cost-web-app
```

또는 배포 스크립트 사용:

```yaml
          script: |
            cd /home/ubuntu/cost-web-app
            ./deploy.sh
```

## 11. 트러블슈팅

### 애플리케이션이 시작되지 않는 경우

1. **로그 확인**
   ```bash
   sudo journalctl -u cost-web-app -n 50
   ```

2. **JAR 파일 존재 확인**
   ```bash
   ls -lh /home/ubuntu/cost-web-app/build/libs/
   ```

3. **포트 충돌 확인**
   ```bash
   sudo netstat -tulpn | grep 8080
   ```

4. **Java 프로세스 확인**
   ```bash
   ps aux | grep java
   ```

### Prometheus가 메트릭을 수집하지 않는 경우

1. **타겟 상태 확인**
   - http://<EC2_IP>:9090/targets 접속
   - `cost-web-app` 타겟이 UP인지 확인

2. **애플리케이션 메트릭 엔드포인트 확인**
   ```bash
   curl http://localhost:8080/actuator/prometheus
   ```

3. **네트워크 연결 확인**
   ```bash
   curl http://localhost:8080/actuator/health
   ```

### Grafana에 데이터가 보이지 않는 경우

1. **Prometheus 데이터소스 연결 확인**
   - Configuration → Data sources → Prometheus
   - Save & Test 클릭

2. **Prometheus가 실행 중인지 확인**
   ```bash
   sudo systemctl status prometheus
   curl http://localhost:9090/api/v1/targets
   ```

## 12. 모니터링 및 유지보수

### 로그 로테이션 설정

```bash
sudo nano /etc/logrotate.d/cost-web-app
```

```
/var/log/cost-web-app/*.log {
    daily
    rotate 7
    compress
    delaycompress
    missingok
    notifempty
    create 0640 ubuntu ubuntu
    sharedscripts
}
```

### 디스크 공간 모니터링

```bash
# Prometheus 데이터 디렉토리 크기 확인
du -sh /var/lib/prometheus

# 로그 디렉토리 크기 확인
du -sh /var/log/cost-web-app
```

### 자동 업데이트 스크립트

주기적으로 업데이트를 확인하는 cron 작업:

```bash
crontab -e
```

다음 추가 (매일 오전 2시 체크):

```
0 2 * * * cd /home/ubuntu/cost-web-app && git fetch && git diff origin/main --quiet || echo "Updates available"
```

## 13. 보안 권장사항

1. **Grafana 비밀번호 변경**
   - 기본 비밀번호를 반드시 변경하세요

2. **인증 설정**
   - Grafana에 인증 설정 추가 고려
   - Nginx를 통한 리버스 프록시 설정 권장

3. **방화질 규칙**
   - 필요한 IP만 접근 허용

4. **SSL/TLS 설정**
   - 도메인 사용 시 Let's Encrypt 인증서 사용 권장

