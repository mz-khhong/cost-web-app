# Grafana 모니터링 가이드

## 1. Grafana 접속하기

### 로컬 환경
1. Docker Compose 실행 후 브라우저에서 접속:
   ```
   http://localhost:3000
   ```

### EC2 서버 배포 후
1. EC2 인스턴스의 공인 IP 또는 도메인으로 접속:
   ```
   http://<EC2_IP>:3000
   ```
   또는 도메인이 설정된 경우:
   ```
   http://<your-domain>:3000
   ```

## 2. 초기 로그인

- **사용자명**: `admin`
- **비밀번호**: `admin`
- 첫 로그인 시 비밀번호 변경을 권장합니다.

## 3. 데이터소스 확인

Prometheus 데이터소스는 자동으로 설정되어 있습니다.

확인 방법:
1. 좌측 메뉴에서 **Configuration (톱니바퀴 아이콘)** → **Data sources** 클릭
2. **Prometheus** 데이터소스가 있는지 확인
3. **URL**이 `http://prometheus:9090`로 설정되어 있는지 확인
4. 하단의 **Save & Test** 버튼으로 연결 테스트

## 4. 대시보드 만들기

### 방법 1: 기본 대시보드 생성 (수동)

1. **Create (더하기 아이콘)** → **Dashboard** 클릭
2. **Add visualization** 클릭
3. **Prometheus** 데이터소스 선택
4. 원하는 메트릭 쿼리 입력

### 방법 2: Spring Boot 메트릭 쿼리 예제

#### HTTP 요청 수
```
rate(http_server_requests_seconds_count[5m])
```

#### HTTP 응답 시간 (평균)
```
rate(http_server_requests_seconds_sum[5m]) / rate(http_server_requests_seconds_count[5m])
```

#### JVM 메모리 사용량
```
jvm_memory_used_bytes{area="heap"}
```

#### JVM 스레드 수
```
jvm_threads_live_threads
```

#### HTTP 에러 비율 (5xx)
```
rate(http_server_requests_seconds_count{status=~"5.."}[5m])
```

#### 애플리케이션 가동 상태
```
up{job="cost-web-app"}
```

### 방법 3: Spring Boot 공식 대시보드 가져오기

1. Grafana에서 **+** → **Import** 클릭
2. Dashboard ID `11378` 입력 (Spring Boot 2.1 Statistics 대시보드)
3. 또는 Dashboard ID `6756` 입력 (JVM Micrometer 대시보드)
4. **Load** 클릭
5. Prometheus 데이터소스 선택 후 **Import** 클릭

## 5. 유용한 메트릭들

### HTTP 메트릭
- `http_server_requests_seconds_count`: HTTP 요청 수
- `http_server_requests_seconds_sum`: 총 응답 시간
- `http_server_requests_seconds_max`: 최대 응답 시간

### JVM 메트릭
- `jvm_memory_used_bytes`: 메모리 사용량
- `jvm_memory_max_bytes`: 최대 메모리
- `jvm_threads_live_threads`: 활성 스레드 수
- `jvm_threads_states_threads`: 스레드 상태별 개수
- `jvm_gc_pause_seconds`: GC 일시 중지 시간

### 시스템 메트릭
- `process_cpu_usage`: CPU 사용률
- `process_uptime_seconds`: 애플리케이션 가동 시간

## 6. 알림(Alert) 설정

1. 대시보드 패널에서 **Edit** 클릭
2. 우측 **Alert** 탭 선택
3. **Create Alert Rule** 클릭
4. 조건 설정:
   - 예: `jvm_memory_used_bytes > 1000000000` (1GB 초과)
5. 알림 채널 연결 (이메일, Slack 등)

## 7. 메트릭 확인 방법

### Prometheus에서 직접 확인
브라우저에서 `http://localhost:9090` 접속 후:
1. 상단 검색창에 메트릭 이름 입력
2. **Execute** 클릭하여 현재 값 확인

### Spring Boot Actuator에서 확인
브라우저에서 `http://localhost:8080/actuator/prometheus` 접속하여:
- 수집되는 모든 메트릭 목록 확인
- Prometheus 형식의 메트릭 데이터 확인

## 8. 트러블슈팅

### 데이터가 보이지 않는 경우

1. **Prometheus 연결 확인**
   - Grafana → Configuration → Data sources → Prometheus
   - "Save & Test" 클릭하여 연결 상태 확인

2. **Prometheus 타겟 확인**
   - Prometheus 웹 UI (`http://localhost:9090/targets`) 접속
   - `cost-web-app` 타겟이 UP 상태인지 확인

3. **Spring Boot 메트릭 엔드포인트 확인**
   - `http://localhost:8080/actuator/prometheus` 접속
   - 메트릭이 출력되는지 확인

4. **컨테이너 로그 확인**
   ```bash
   docker-compose logs prometheus
   docker-compose logs grafana
   docker-compose logs cost-web-app
   ```

### 네트워크 이슈

Docker Compose의 네트워크 설정 확인:
```bash
docker network inspect cost-web-app_cost-network
```

각 컨테이너가 같은 네트워크에 연결되어 있는지 확인해야 합니다.

## 9. 프로덕션 환경 권장사항

1. **보안 강화**
   - Grafana 관리자 비밀번호 변경
   - 방화벽 규칙 설정 (필요한 IP만 접근 허용)
   - HTTPS 설정 고려

2. **대시보드 백업**
   - Grafana 대시보드를 JSON으로 export하여 버전 관리

3. **용량 관리**
   - Prometheus 데이터 보관 기간 설정
   - 필요시 Thanos나 VictoriaMetrics 사용 고려

4. **모니터링 범위 확장**
   - EC2 인스턴스 메트릭 (node_exporter)
   - 데이터베이스 메트릭
   - 네트워크 메트릭

