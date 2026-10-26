# 🚀 Deployment Guide

> **Status warning:** The present root `docker-compose.yml` starts Kafka, PostgreSQL, Redis, Kafka UI and market-data producer; the trade processor, broadcaster and historical service blocks are commented out. Therefore the full-stack Docker instructions below are **targets requiring verification**, not one-command deployment proof. For day-to-day work, start only `kafka postgres redis` and run application services locally after checking the Kafka advertised listener configuration. See [resource optimization](RESOURCE-OPTIMIZATION.md) and [roadmap](ROADMAP.md).


Deployment reference and proposed options; validate each command against the current Compose config before use.

---

## Table of Contents

- [Local Development](#local-development)
- [Docker Compose (Recommended)](#docker-compose-recommended)
- [Production Deployment](#production-deployment)
- [Kubernetes (Advanced)](#kubernetes-advanced)
- [Monitoring & Logging](#monitoring--logging)
- [Scaling Strategy](#scaling-strategy)

---

## Local Development

### **Prerequisites**

- **JDK 17** or higher
- **Gradle 8.5** or higher
- **Docker Desktop** (for infrastructure)
- **IntelliJ IDEA** (recommended) or VS Code

### **Setup Infrastructure Only**

```bash
# Start only Kafka, PostgreSQL, Redis
docker-compose up -d kafka postgres redis

# Verify services are running
docker-compose ps

# Check logs
docker-compose logs -f
```

### **Run Services Locally**

**Terminal 1 - Service 1:**
```bash
cd market-data
./gradlew bootRun
```

**Terminal 2 - Service 2:**
```bash
cd trade-processor
export SPRING_R2DBC_URL=r2dbc:postgresql://localhost:5432/trades
export SPRING_R2DBC_USERNAME=trader
export SPRING_R2DBC_PASSWORD=traderpass
export REDIS_HOST=localhost
export REDIS_PORT=6379
./gradlew bootRun
```

**Terminal 3 - Service 3:**
```bash
cd websocket-broadcaster
./gradlew bootRun
```

**Terminal 4 - Service 4:**
```bash
cd historical-service
export SPRING_R2DBC_URL=r2dbc:postgresql://localhost:5432/trades
export SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/trades
export SPRING_R2DBC_USERNAME=trader
export SPRING_R2DBC_PASSWORD=traderpass
export REDIS_HOST=localhost
export REDIS_PORT=6379
./gradlew bootRun
```

### **Verify Everything Works**

```bash
# Test Service 1
curl http://localhost:8080/api/v1/market-data/health

# Test Service 4
curl "http://localhost:8084/api/v1/historical/candles?symbol=BTCUSDT&interval=1h&startTime=1704067200000&endTime=1704153600000"

# Test WebSocket
wscat -c ws://localhost:8082/ws/trades
```

---

## Docker Compose (Recommended)

### **Full Stack Deployment**

```bash
# Clone repository
git clone https://github.com/yourusername/trading-platform.git
cd trading-platform

# Build all services
docker-compose build

# Start everything
docker-compose up -d

# Watch logs
docker-compose logs -f

# Check status
docker-compose ps
```

### **Environment Variables**

Create `.env` file in project root:

```bash
# PostgreSQL
POSTGRES_USER=trader
POSTGRES_PASSWORD=traderpass
POSTGRES_DB=trades

# Redis
REDIS_PASSWORD=

# Kafka
KAFKA_BOOTSTRAP_SERVERS=kafka:9092

# Services
MARKET_DATA_PRODUCER_URL=http://market-data-producer:8080
```

### **Selective Service Deployment**

```bash
# Start only specific services
docker-compose up -d kafka postgres redis market-data-producer

# Scale a service
docker-compose up -d --scale historical-data-service=3

# Restart a service
docker-compose restart trade-processor

# View logs for one service
docker-compose logs -f historical-data-service

# Stop everything
docker-compose down

# Stop and remove volumes (WARNING: deletes data)
docker-compose down -v
```

### **Health Checks**

```bash
# Check all container health
docker-compose ps

# Should show all as "healthy"
# If not, check logs:
docker-compose logs <service-name>
```

### **Rebuild After Code Changes**

```bash
# Rebuild specific service
docker-compose build historical-data-service

# Rebuild and restart
docker-compose up -d --build historical-data-service

# Rebuild everything
docker-compose build
docker-compose up -d
```

---

## Production Deployment

### **Option 1: Single Server (Small Scale)**

**Requirements:**
- Ubuntu 22.04 LTS
- 8GB RAM minimum (16GB recommended)
- 50GB SSD storage
- Docker & Docker Compose installed

**Steps:**

```bash
# 1. Install Docker
curl -fsSL https://get.docker.com -o get-docker.sh
sudo sh get-docker.sh
sudo usermod -aG docker $USER

# 2. Install Docker Compose
sudo curl -L "https://github.com/docker/compose/releases/download/v2.23.0/docker-compose-$(uname -s)-$(uname -m)" -o /usr/local/bin/docker-compose
sudo chmod +x /usr/local/bin/docker-compose

# 3. Clone and configure
git clone https://github.com/yourusername/trading-platform.git
cd trading-platform

# 4. Set production environment variables
cp .env.example .env
nano .env  # Edit with production values

# 5. Deploy
docker-compose -f docker-compose.prod.yml up -d

# 6. Setup monitoring
docker-compose -f docker-compose.monitoring.yml up -d
```

### **Production docker-compose.prod.yml**

```yaml
version: '3.8'

services:
  market-data-producer:
    image: trading-platform/market-data:latest
    restart: always
    environment:
      - SPRING_PROFILES_ACTIVE=prod
    deploy:
      resources:
        limits:
          cpus: '1.0'
          memory: 1G

  trade-processor:
    image: trading-platform/trade-processor:latest
    restart: always
    environment:
      - SPRING_PROFILES_ACTIVE=prod
    deploy:
      resources:
        limits:
          cpus: '2.0'
          memory: 2G

  # ... other services ...

  postgres:
    image: postgres:15
    restart: always
    volumes:
      - postgres-data:/var/lib/postgresql/data
      - ./backups:/backups
    environment:
      - POSTGRES_USER=${POSTGRES_USER}
      - POSTGRES_PASSWORD=${POSTGRES_PASSWORD}
    deploy:
      resources:
        limits:
          cpus: '2.0'
          memory: 4G

volumes:
  postgres-data:
    driver: local
```

### **Security Hardening**

```bash
# 1. Enable firewall
sudo ufw enable
sudo ufw allow 22/tcp    # SSH
sudo ufw allow 80/tcp    # HTTP
sudo ufw allow 443/tcp   # HTTPS

# 2. Setup SSL with Let's Encrypt
sudo apt install certbot
sudo certbot certonly --standalone -d yourdomain.com

# 3. Configure nginx reverse proxy
sudo apt install nginx
sudo nano /etc/nginx/sites-available/trading-platform

# 4. Secure PostgreSQL
# Change default passwords
# Enable SSL connections
# Restrict host access

# 5. Secure Kafka
# Enable SASL authentication
# Setup ACLs for topics
```

### **Backup Strategy**

```bash
# PostgreSQL automated backup
docker exec postgres pg_dump -U trader trades > backup_$(date +%Y%m%d).sql

# Add to crontab for daily backups
0 2 * * * /path/to/backup-script.sh

# Backup to S3 (AWS)
aws s3 cp backup_$(date +%Y%m%d).sql s3://your-bucket/backups/
```

---

## Option 2: Cloud Deployment (AWS)

### **AWS Architecture**

```
Internet → ALB (Load Balancer)
              ↓
    [ECS Fargate Tasks]
    - Service 1 (2 replicas)
    - Service 2 (3 replicas)
    - Service 3 (4 replicas)
    - Service 4 (2 replicas)
              ↓
    [Amazon MSK (Kafka)]
              ↓
    [RDS PostgreSQL (Multi-AZ)]
              ↓
    [ElastiCache Redis]
```

### **Infrastructure as Code (Terraform)**

```hcl
# main.tf
provider "aws" {
  region = "us-east-1"
}

# RDS PostgreSQL
resource "aws_db_instance" "trading_db" {
  identifier        = "trading-platform-db"
  engine            = "postgres"
  engine_version    = "15"
  instance_class    = "db.t3.medium"
  allocated_storage = 100
  storage_encrypted = true
  multi_az          = true
  
  db_name  = "trades"
  username = var.db_username
  password = var.db_password
  
  backup_retention_period = 7
  backup_window          = "03:00-04:00"
  maintenance_window     = "mon:04:00-mon:05:00"
}

# ElastiCache Redis
resource "aws_elasticache_cluster" "trading_cache" {
  cluster_id           = "trading-platform-cache"
  engine               = "redis"
  node_type            = "cache.t3.medium"
  num_cache_nodes      = 1
  parameter_group_name = "default.redis7"
  port                 = 6379
}

# MSK Kafka Cluster
resource "aws_msk_cluster" "trading_kafka" {
  cluster_name           = "trading-platform-kafka"
  kafka_version          = "3.5.1"
  number_of_broker_nodes = 3
  
  broker_node_group_info {
    instance_type   = "kafka.t3.small"
    client_subnets  = var.subnet_ids
    storage_info {
      ebs_storage_info {
        volume_size = 100
      }
    }
  }
}

# ECS Cluster
resource "aws_ecs_cluster" "trading_cluster" {
  name = "trading-platform"
}

# ... ECS task definitions and services ...
```

### **Deploy to AWS**

```bash
# 1. Build and push Docker images to ECR
aws ecr get-login-password --region us-east-1 | docker login --username AWS --password-stdin <account-id>.dkr.ecr.us-east-1.amazonaws.com

docker build -t trading-platform/market-data:latest ./market-data
docker tag trading-platform/market-data:latest <account-id>.dkr.ecr.us-east-1.amazonaws.com/market-data:latest
docker push <account-id>.dkr.ecr.us-east-1.amazonaws.com/market-data:latest

# Repeat for all services...

# 2. Deploy infrastructure
cd terraform
terraform init
terraform plan
terraform apply

# 3. Deploy services to ECS
aws ecs update-service --cluster trading-platform --service market-data-service --force-new-deployment
```

---

## Kubernetes (Advanced)

### **Prerequisites**

- Kubernetes cluster (EKS, GKE, or local minikube)
- kubectl configured
- Helm 3 installed

### **Deploy with Helm**

```bash
# 1. Create namespace
kubectl create namespace trading-platform

# 2. Deploy PostgreSQL with Helm
helm install postgres bitnami/postgresql \
  --namespace trading-platform \
  --set auth.username=trader \
  --set auth.password=traderpass \
  --set auth.database=trades

# 3. Deploy Redis
helm install redis bitnami/redis \
  --namespace trading-platform

# 4. Deploy Kafka
helm install kafka bitnami/kafka \
  --namespace trading-platform

# 5. Deploy application services
kubectl apply -f k8s/

# 6. Check status
kubectl get pods -n trading-platform
kubectl get services -n trading-platform
```

### **Kubernetes Manifests**

**Service 4 Deployment:**

```yaml
# k8s/historical-service-deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: historical-data-service
  namespace: trading-platform
spec:
  replicas: 3
  selector:
    matchLabels:
      app: historical-data-service
  template:
    metadata:
      labels:
        app: historical-data-service
    spec:
      containers:
      - name: historical-service
        image: trading-platform/historical-service:latest
        ports:
        - containerPort: 8084
        env:
        - name: SPRING_PROFILES_ACTIVE
          value: "prod"
        - name: SPRING_R2DBC_URL
          valueFrom:
            secretKeyRef:
              name: db-credentials
              key: r2dbc-url
        resources:
          requests:
            memory: "1Gi"
            cpu: "500m"
          limits:
            memory: "2Gi"
            cpu: "1000m"
        livenessProbe:
          httpGet:
            path: /actuator/health
            port: 8084
          initialDelaySeconds: 60
          periodSeconds: 10
        readinessProbe:
          httpGet:
            path: /actuator/health
            port: 8084
          initialDelaySeconds: 30
          periodSeconds: 5
---
apiVersion: v1
kind: Service
metadata:
  name: historical-data-service
  namespace: trading-platform
spec:
  selector:
    app: historical-data-service
  ports:
  - port: 8084
    targetPort: 8084
  type: LoadBalancer
```

### **Auto-Scaling**

```yaml
# k8s/historical-service-hpa.yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: historical-service-hpa
  namespace: trading-platform
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: historical-data-service
  minReplicas: 2
  maxReplicas: 10
  metrics:
  - type: Resource
    resource:
      name: cpu
      target:
        type: Utilization
        averageUtilization: 70
  - type: Resource
    resource:
      name: memory
      target:
        type: Utilization
        averageUtilization: 80
```

---

## Monitoring & Logging

### **Prometheus + Grafana**

```yaml
# docker-compose.monitoring.yml
version: '3.8'

services:
  prometheus:
    image: prom/prometheus:latest
    ports:
      - "9090:9090"
    volumes:
      - ./prometheus.yml:/etc/prometheus/prometheus.yml
      - prometheus-data:/prometheus

  grafana:
    image: grafana/grafana:latest
    ports:
      - "3000:3000"
    environment:
      - GF_SECURITY_ADMIN_PASSWORD=admin
    volumes:
      - grafana-data:/var/lib/grafana
      - ./grafana/dashboards:/etc/grafana/provisioning/dashboards

volumes:
  prometheus-data:
  grafana-data:
```

**Prometheus Configuration:**

```yaml
# prometheus.yml
global:
  scrape_interval: 15s

scrape_configs:
  - job_name: 'market-data-producer'
    static_configs:
      - targets: ['market-data-producer:8080']
    metrics_path: '/actuator/prometheus'

  - job_name: 'trade-processor'
    static_configs:
      - targets: ['trade-processor:8081']
    metrics_path: '/actuator/prometheus'

  - job_name: 'historical-data-service'
    static_configs:
      - targets: ['historical-data-service:8084']
    metrics_path: '/actuator/prometheus'
```

### **ELK Stack (Logging)**

```bash
# Deploy ELK
docker-compose -f docker-compose.elk.yml up -d

# Access Kibana
http://localhost:5601
```

---

## Scaling Strategy

### **Vertical Scaling**

**Increase resources per container:**

```yaml
deploy:
  resources:
    limits:
      cpus: '4.0'      # Increase from 2.0
      memory: 8G       # Increase from 4G
```

### **Horizontal Scaling**

**Add more instances:**

```bash
# Docker Compose
docker-compose up -d --scale historical-data-service=5

# Kubernetes
kubectl scale deployment historical-data-service --replicas=5
```

### **Database Scaling**

**Read Replicas:**

```sql
-- Create read replica in AWS RDS
aws rds create-db-instance-read-replica \
  --db-instance-identifier trading-db-replica \
  --source-db-instance-identifier trading-db
```

**Connection Pooling:**

```yaml
spring:
  r2dbc:
    pool:
      initial-size: 20
      max-size: 100
```

---

## Performance Optimization

### **JVM Tuning**

```bash
# Add to Dockerfile ENTRYPOINT
JAVA_OPTS="-Xms2g -Xmx4g -XX:+UseG1GC -XX:MaxGCPauseMillis=200"
```

### **Database Optimization**

```sql
-- Add indexes
CREATE INDEX CONCURRENTLY idx_trades_symbol_timestamp 
  ON trades(symbol, trade_timestamp DESC);

-- Partition large tables
CREATE TABLE trades_2024_01 PARTITION OF trades
  FOR VALUES FROM ('2024-01-01') TO ('2024-02-01');
```

---

## Disaster Recovery

### **Backup Plan**

- **Database:** Daily automated backups, 30-day retention
- **Kafka:** Replicate to 3 brokers
- **Redis:** AOF persistence + daily snapshots

### **Recovery Procedure**

```bash
# Restore PostgreSQL from backup
docker exec -i postgres psql -U trader trades < backup_20240101.sql

# Restore Redis from snapshot
docker cp redis-snapshot.rdb redis:/data/dump.rdb
docker restart redis
```

---

## Cost Optimization (AWS)

- Use Spot Instances for non-critical services
- Enable auto-scaling to reduce idle capacity
- Use Reserved Instances for stable workloads
- Store backups in S3 Glacier for long-term retention

**Estimated Monthly Cost:**
- Small: ~$200/month (t3.small instances)
- Medium: ~$800/month (t3.medium instances)
- Large: ~$3000/month (dedicated infrastructure)

---

## Checklist Before Production

- [ ] Change all default passwords
- [ ] Enable SSL/TLS for all connections
- [ ] Setup automated backups
- [ ] Configure monitoring and alerting
- [ ] Load test all services
- [ ] Document runbooks for common incidents
- [ ] Setup CI/CD pipeline
- [ ] Configure log rotation
- [ ] Enable rate limiting
- [ ] Setup health check endpoints

---

**Your platform is ready for production! 🚀**