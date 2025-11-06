# Kafka 설치 및 설정 가이드

## 서버 추가 필요 여부

### 현재 서버 (Ubuntu-sre-service)
- **사양**: 2GB RAM, 2 vCPU, 60GB SSD
- **IP**: 3.36.29.123

### 서버 추가 필요 여부 판단

#### 단일 서버로 시작 (개발/테스트 단계)
- ✅ **가능**: 현재 서버에서 Kafka를 실행할 수 있습니다
- ✅ **장점**: 비용 절감, 빠른 시작
- ⚠️ **단점**: 고가용성 없음, 서버 장애 시 전체 시스템 중단

#### 여러 서버 필요 (프로덕션 환경)
- ✅ **권장**: 최소 **3개의 서버** (Kafka 클러스터)
- ✅ **장점**: 고가용성, 부하 분산, 장애 복구
- ⚠️ **단점**: 비용 증가, 관리 복잡도 증가

### 추천 방안

1. **개발/테스트 단계**: 현재 서버에서 시작
2. **프로덕션 단계**: 3개의 Lightsail 인스턴스 추가 (총 4개)

---

## 단일 서버에 Kafka 설치 (현재 서버)

### 1. Kafka 다운로드 및 설치

```bash
# SSH 접속
ssh -i ./aws-gihwan-ssh.pem ubuntu@3.36.29.123

# Java 확인 (이미 설치되어 있음)
java -version

# Kafka 다운로드
cd /opt
sudo wget https://downloads.apache.org/kafka/3.6.0/kafka_2.13-3.6.0.tgz

# 압축 해제
sudo tar -xzf kafka_2.13-3.6.0.tgz
sudo mv kafka_2.13-3.6.0 kafka
sudo chown -R ubuntu:ubuntu /opt/kafka

# PATH 추가 (~/.bashrc)
echo 'export KAFKA_HOME=/opt/kafka' >> ~/.bashrc
echo 'export PATH=$PATH:$KAFKA_HOME/bin' >> ~/.bashrc
source ~/.bashrc
```

### 2. Kafka 설정

```bash
# 설정 파일 수정
cd /opt/kafka/config
vi server.properties
```

**주요 설정 변경**:
```properties
# Broker ID
broker.id=0

# 로그 디렉토리
log.dirs=/opt/kafka/kafka-logs

# Zookeeper 연결 (단일 서버)
zookeeper.connect=localhost:2181

# 리스너 설정 (외부 접속 허용)
listeners=PLAINTEXT://0.0.0.0:9092
advertised.listeners=PLAINTEXT://3.36.29.123:9092
```

### 3. Zookeeper 설정

```bash
# Zookeeper 설정 파일 수정
vi /opt/kafka/config/zookeeper.properties

# 데이터 디렉토리 설정
dataDir=/opt/kafka/zookeeper-data
```

### 4. systemd 서비스 등록

#### Zookeeper 서비스
```bash
sudo tee /etc/systemd/system/zookeeper.service > /dev/null <<EOF
[Unit]
Description=Apache Zookeeper
After=network.target

[Service]
Type=simple
User=ubuntu
WorkingDirectory=/opt/kafka
ExecStart=/opt/kafka/bin/zookeeper-server-start.sh /opt/kafka/config/zookeeper.properties
Restart=always
RestartSec=10

[Install]
WantedBy=multi-user.target
EOF
```

#### Kafka 서비스
```bash
sudo tee /etc/systemd/system/kafka.service > /dev/null <<EOF
[Unit]
Description=Apache Kafka
After=network.target zookeeper.service
Requires=zookeeper.service

[Service]
Type=simple
User=ubuntu
WorkingDirectory=/opt/kafka
ExecStart=/opt/kafka/bin/kafka-server-start.sh /opt/kafka/config/server.properties
Restart=always
RestartSec=10

[Install]
WantedBy=multi-user.target
EOF

# 서비스 활성화 및 시작
sudo systemctl daemon-reload
sudo systemctl enable zookeeper kafka
sudo systemctl start zookeeper
sleep 5
sudo systemctl start kafka
```

### 5. 방화벽 설정 (Lightsail)

Lightsail 콘솔에서 다음 포트를 열어주세요:
- **9092**: Kafka Broker
- **2181**: Zookeeper (선택사항, 내부 통신용)

### 6. 서비스 확인

```bash
# Zookeeper 상태 확인
sudo systemctl status zookeeper

# Kafka 상태 확인
sudo systemctl status kafka

# Topic 생성 테스트
/opt/kafka/bin/kafka-topics.sh --create \
  --bootstrap-server localhost:9092 \
  --replication-factor 1 \
  --partitions 3 \
  --topic cost-web-app-topic

# Topic 목록 확인
/opt/kafka/bin/kafka-topics.sh --list --bootstrap-server localhost:9092
```

---

## 여러 서버에 Kafka 클러스터 구성 (프로덕션)

### 1. Lightsail 인스턴스 추가

추가로 **2개의 Ubuntu 인스턴스**를 생성하세요:
- **인스턴스 2**: Kafka Broker 1
- **인스턴스 3**: Kafka Broker 2
- **기존 인스턴스**: Kafka Broker 0 (Spring Boot + Kafka)

### 2. 각 서버에 Kafka 설치

각 서버에서 위의 단일 서버 설치 과정을 반복하되, `server.properties`에서 broker.id를 다르게 설정:

**서버 1 (기존)**:
```properties
broker.id=0
advertised.listeners=PLAINTEXT://3.36.29.123:9092
```

**서버 2**:
```properties
broker.id=1
advertised.listeners=PLAINTEXT://<서버2_IP>:9092
```

**서버 3**:
```properties
broker.id=2
advertised.listeners=PLAINTEXT://<서버3_IP>:9092
```

### 3. Zookeeper 클러스터 설정

각 서버의 `zookeeper.properties`:
```properties
server.1=3.36.29.123:2888:3888
server.2=<서버2_IP>:2888:3888
server.3=<서버3_IP>:2888:3888
```

### 4. Kafka 클러스터 설정

각 서버의 `server.properties`:
```properties
zookeeper.connect=3.36.29.123:2181,<서버2_IP>:2181,<서버3_IP>:2181
```

### 5. Topic 생성 (Replication Factor 3)

```bash
/opt/kafka/bin/kafka-topics.sh --create \
  --bootstrap-server 3.36.29.123:9092 \
  --replication-factor 3 \
  --partitions 3 \
  --topic cost-web-app-topic
```

---

## 애플리케이션 설정

### application.yml 수정

현재는 `localhost:9092`로 설정되어 있습니다. 여러 서버를 사용하는 경우:

```yaml
spring:
  kafka:
    bootstrap-servers: 3.36.29.123:9092,<서버2_IP>:9092,<서버3_IP>:9092
    topic:
      replication-factor: 3  # 여러 서버 사용 시
```

---

## Kafka API 사용 예제

### 메시지 전송
```bash
# 특정 파티션에 전송
curl -X POST "http://3.36.29.123:8080/api/kafka/send/partition?partition=0&key=user1&message=Hello"

# 키 기반 전송
curl -X POST "http://3.36.29.123:8080/api/kafka/send?key=user1&message=Hello"

# 단순 전송
curl -X POST "http://3.36.29.123:8080/api/kafka/send/simple?message=Hello"
```

### seek() API 사용
```bash
# 특정 offset으로 이동
curl -X POST "http://3.36.29.123:8080/api/kafka/seek/offset?partition=0&offset=10"

# 파티션 처음으로 이동
curl -X POST "http://3.36.29.123:8080/api/kafka/seek/beginning?partition=0"

# 파티션 끝으로 이동
curl -X POST "http://3.36.29.123:8080/api/kafka/seek/end?partition=0"
```

### Kafka 정보 조회
```bash
curl http://3.36.29.123:8080/api/kafka/info
```

---

## Manual Commit vs Auto Commit

### Manual Commit (현재 설정)
- ✅ **데이터 손실 방지**: 처리 완료 후 커밋
- ✅ **중복 처리 방지**: seek() API로 재처리 가능
- ✅ **에러 처리**: 실패 시 커밋하지 않아 재처리 가능
- ⚠️ **주의**: 반드시 `acknowledgment.acknowledge()` 호출 필요

### Auto Commit
- ✅ **간단함**: 자동으로 커밋
- ⚠️ **데이터 손실 가능**: 처리 중 오류 시 커밋됨
- ⚠️ **중복 처리 가능**: 처리 실패 후 재시작 시 중복 처리

---

## 모니터링

### Kafka 로그 확인
```bash
# Kafka 로그
sudo journalctl -u kafka -f

# Zookeeper 로그
sudo journalctl -u zookeeper -f
```

### Topic 정보 확인
```bash
# Topic 상세 정보
/opt/kafka/bin/kafka-topics.sh --describe \
  --bootstrap-server localhost:9092 \
  --topic cost-web-app-topic

# Consumer Group 정보
/opt/kafka/bin/kafka-consumer-groups.sh \
  --bootstrap-server localhost:9092 \
  --group cost-web-app-consumer-group \
  --describe
```

---

## 문제 해결

### Kafka가 시작되지 않는 경우
```bash
# 로그 확인
sudo journalctl -u kafka -n 50

# 포트 확인
sudo ss -tulpn | grep 9092

# Zookeeper 확인
sudo systemctl status zookeeper
```

### 외부 접속이 안 되는 경우
1. Lightsail 방화벽 규칙 확인 (9092 포트)
2. `advertised.listeners` 설정 확인
3. 보안 그룹 확인

