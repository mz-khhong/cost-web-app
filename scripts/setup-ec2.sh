#!/bin/bash

# EC2 인스턴스 초기 설정 스크립트
# 사용법: sudo bash scripts/setup-ec2.sh

set -e

echo "=========================================="
echo "EC2 인스턴스 초기 설정 시작"
echo "=========================================="

# 1. 패키지 업데이트
echo "Step 1: Updating packages..."
apt update

# 2. Java 17 설치
echo "Step 2: Installing Java 17..."
apt install -y openjdk-17-jdk

# JAVA_HOME 설정
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
echo "export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64" >> /etc/environment

# 3. Prometheus 설치
echo "Step 3: Installing Prometheus..."
useradd --no-create-home --shell /bin/false prometheus 2>/dev/null || true

mkdir -p /etc/prometheus
mkdir -p /var/lib/prometheus

cd /tmp
wget -q https://github.com/prometheus/prometheus/releases/download/v2.48.0/prometheus-2.48.0.linux-amd64.tar.gz
tar -xzf prometheus-2.48.0.linux-amd64.tar.gz
cd prometheus-2.48.0.linux-amd64

cp prometheus /usr/local/bin/
cp promtool /usr/local/bin/
chown prometheus:prometheus /usr/local/bin/prometheus
chown prometheus:prometheus /usr/local/bin/promtool

cp -r consoles /etc/prometheus
cp -r console_libraries /etc/prometheus
chown -R prometheus:prometheus /etc/prometheus
chown -R prometheus:prometheus /var/lib/prometheus

# Prometheus 설정 파일 (애플리케이션이 배포된 후 업데이트 필요)
cat > /etc/prometheus/prometheus.yml <<EOF
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
EOF

chown prometheus:prometheus /etc/prometheus/prometheus.yml

# Prometheus systemd 서비스
cat > /etc/systemd/system/prometheus.service <<EOF
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
EOF

systemctl daemon-reload
systemctl enable prometheus
systemctl start prometheus

# 4. Grafana 설치
echo "Step 4: Installing Grafana..."
apt-get install -y software-properties-common
wget -q -O /usr/share/keyrings/grafana.key https://apt.grafana.com/gpg.key
echo "deb [signed-by=/usr/share/keyrings/grafana.key] https://apt.grafana.com stable main" | tee -a /etc/apt/sources.list.d/grafana.list
apt-get update
apt-get install -y grafana

systemctl daemon-reload
systemctl enable grafana-server
systemctl start grafana-server

# 5. 애플리케이션 디렉토리 및 서비스 설정
echo "Step 5: Setting up application directory..."
mkdir -p /home/ubuntu/cost-web-app
chown ubuntu:ubuntu /home/ubuntu/cost-web-app

mkdir -p /var/log/cost-web-app
chown ubuntu:ubuntu /var/log/cost-web-app

# 애플리케이션 systemd 서비스 (애플리케이션 배포 후 활성화)
cat > /etc/systemd/system/cost-web-app.service <<EOF
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
EOF

systemctl daemon-reload

# 6. 방화벽 설정 (UFW가 활성화된 경우)
echo "Step 6: Configuring firewall..."
ufw allow 22/tcp 2>/dev/null || true
ufw allow 8080/tcp 2>/dev/null || true
ufw allow 9090/tcp 2>/dev/null || true
ufw allow 3000/tcp 2>/dev/null || true

echo "=========================================="
echo "초기 설정 완료!"
echo "=========================================="
echo ""
echo "다음 단계:"
echo "1. 프로젝트 클론: cd /home/ubuntu/cost-web-app && git clone <repo-url> ."
echo "2. 애플리케이션 빌드: ./gradlew clean build"
echo "3. 서비스 시작: sudo systemctl start cost-web-app"
echo "4. Grafana 설정: http://<EC2_IP>:3000 (admin/admin)"
echo ""

