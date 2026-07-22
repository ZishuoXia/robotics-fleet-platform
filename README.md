# Cloud Robotics Fleet Management Platform

> A production-inspired backend platform for cloud-based robot fleet management, featuring multi-tenant architecture, secure authentication, and real-time device monitoring.

## Overview

Robotics Fleet Platform is an enterprise-oriented backend system for managing cloud-connected robot fleets. The platform provides centralized management for robotic devices, organizations, and users while supporting secure multi-tenant isolation and scalable backend architecture.

This project is developed as a software engineering portfolio inspired by real-world industrial robotics platforms (e.g. AWS RoboMaker, Formant), focusing on backend system design rather than robot control algorithms.

---

## Features

### Multi-Tenant Architecture
- Tenant-level data isolation
- Tenant context management via 'TenantContext' (ThreadLocal)
- Repository and service layer validation for cross-tenant access prevention

### Authentication & Authorization
- JWT-based authentication
- Spring Security integration
- Role-Based Access Control (RBAC) with ADMIN, OPERATOR, VIEWER roles
- Fine-grained permission management ('device:read', 'device:write', 'user:read', 'user:write')

### Device Management
- Device registration and lifecycle management (PROVISIONED → ONLINE → OFFLINE → DECOMMISSIONED)
- CRUD operations with tenant-scoped queries
- Soft-delete (decommission) pattern

### Real-Time Device Monitoring
- Device heartbeat reporting via REST API
- Redis-based online/offline status cache (TTL-based expiry)
- Scheduled offline detection (configurable threshold)
- WebSocket (STOMP/SockJS) real-time status push

### Event-Driven Backend
- Kafka-based asynchronous heartbeat processing
- Event consumption with idempotent message handling
- Partition strategy by tenantId for ordered per-tenant processing

### Infrastructure
- Docker Compose deployment (PostgreSQL, Redis, Kafka)
- Flyway database migration
- Maven build management

---

## Architecture

'''
                     +----------------------+
                     |    Robot Devices     |
                     +----------+-----------+
                                |
                         REST / Heartbeat
                                |
                                ▼
                   +-------------------------+
                   | Spring Boot Backend API |
                   +-----------+-------------+
                               |
          +--------------------+----------------------+
          |                    |                      |
          ▼                    ▼                      ▼
     PostgreSQL             Redis                 Kafka
   (Persistent Data)   (Online Status Cache)   (Event Queue)
          |                                         |
          |                                         ▼
          |                               Heartbeat Consumer
          |                                         |
          +--------------------+--------------------+
                               |
                               ▼
                        WebSocket Push
'''

**Heartbeat Processing Flow:**
'''
Device → POST /heartbeat → Controller → Kafka → Consumer → Redis + PostgreSQL + WebSocket
                            ↓
                       Return 202 (async)
'''

---

## Technology Stack

| Category | Technologies |
|----------|--------------|
| Language | Java 17 |
| Framework | Spring Boot 3.5 |
| Security | Spring Security, JWT (jjwt) |
| Database | PostgreSQL 16 |
| ORM | Spring Data JPA |
| Cache | Redis 7 |
| Messaging | Apache Kafka (KRaft mode) |
| Database Migration | Flyway |
| Build Tool | Maven (Wrapper) |
| Containerization | Docker Compose |
| API Documentation | SpringDoc OpenAPI (Swagger UI) |

---

## Project Structure

'''
src/main/java/com/zishuo/fleet/
├── common/          # Shared utilities, response wrappers, exception handling
├── config/          # Application configuration (OpenAPI, Jackson, etc.)
├── infrastructure/  # Kafka, Redis, WebSocket infrastructure setup
├── module/
│   ├── auth/        # Authentication (login, register, JWT)
│   ├── device/      # Device CRUD and lifecycle management
│   └── heartbeat/   # Heartbeat reporting, Kafka producer/consumer, online detection
├── health/          # Health check endpoint
└── security/        # JWT filter, tenant context, user details service
'''

The project follows a layered architecture:
'''
Controller → Service → Repository → Database
'''

---

## Getting Started

### Prerequisites

- Java 17+
- Docker Desktop

### Clone

'''bash
git clone https://github.com/<your-username>/robotics-fleet-platform.git
cd robotics-fleet-platform
'''

### Start Infrastructure

'''bash
docker compose up -d
'''

This starts:
- **PostgreSQL 16** (port 5432) — user: 'fleet', password: 'fleet_dev_pwd', database: 'fleet'
- **Redis 7** (port 6379)
- **Apache Kafka** (port 9092, KRaft mode, no Zookeeper)
- **Kafka UI** (port 8090) — browser-based Kafka management console

### Run

'''bash
./mvnw spring-boot:run
'''

Or run the main class 'FleetApplication' directly in IntelliJ IDEA.

The application starts on **port 8080** with the 'dev' profile active.

### Default Credentials

| Field | Value |
|-------|-------|
| Tenant | 'default' |
| Username | 'admin' |
| Password | 'admin123' |

> Seeded via Flyway migration for local development only — not for production use.

### API Documentation

Once the application is running:

- **Swagger UI:** http://localhost:8080/swagger-ui.html
- **OpenAPI Spec:** http://localhost:8080/v3/api-docs
- **Health Check:** http://localhost:8080/api/v1/health
- **Actuator:** http://localhost:8080/actuator/health

---

## Testing

### Unit Tests

'''bash
# Run all tests
./mvnw test

# Run a specific test class
./mvnw test -Dtest=AuthServiceTest

# Run a specific test method
./mvnw test -Dtest=AuthServiceTest#shouldLoginSuccessfully
'''

Tests use Mockito ('@ExtendWith(MockitoExtension.class)') with given/when/then structure.

### End-to-End Testing

**Heartbeat Simulation Script** — simulates device heartbeat reporting:

'''bash
# Install dependency
pip install requests

# Simulate device 1, heartbeat every 10 seconds
python src/main/java/com/zishuo/fleet/scripts/simulate_devices.py --device-ids 1 --interval 10

# Simulate multiple devices
python src/main/java/com/zishuo/fleet/scripts/simulate_devices.py --device-ids 1,2,3 --interval 15
'''

**WebSocket Tester** — browser-based real-time notification testing:

Open 'src/main/java/com/zishuo/fleet/scripts/ws-tester.html' in a browser. It logs in via REST API, connects to the WebSocket endpoint, and subscribes to device status push notifications.

---

## Key API Endpoints

| Method | Path | Description | Auth |
|--------|------|-------------|------|
| POST | '/api/v1/auth/login' | Login, returns JWT | Public |
| POST | '/api/v1/auth/register' | Register new user | Public |
| GET | '/api/v1/auth/me' | Current user profile | Required |
| POST | '/api/v1/devices' | Register a device | 'device:write' |
| GET | '/api/v1/devices' | List devices (paginated) | 'device:read' |
| GET | '/api/v1/devices/{id}' | Get device details | 'device:read' |
| PATCH | '/api/v1/devices/{id}' | Update device | 'device:write' |
| DELETE | '/api/v1/devices/{id}' | Decommission device | 'device:write' |
| POST | '/api/v1/devices/{id}/heartbeat' | Report heartbeat (async) | 'device:write' |
| GET | '/api/v1/devices/online' | List online devices | 'device:read' |
| GET | '/api/v1/devices/{id}/status' | Device real-time status | 'device:read' |
| GET | '/api/v1/health' | Health check | Public |

---

## Development Progress

### M1 — Foundation ✔
- Multi-tenant architecture
- JWT authentication & RBAC authorization
- User and tenant management
- Device registry (CRUD)
- Flyway database migration
- Global exception handling

### M2 — Real-Time Device Monitoring ✔
- Device heartbeat API (async via Kafka)
- Kafka producer/consumer pipeline
- Redis online status cache (TTL-based)
- Scheduled offline detection
- WebSocket (STOMP/SockJS) real-time push

### M3 — Planned
- Robot task scheduling
- Fleet dispatch management
- OTA upgrade workflow
- Monitoring dashboard
- CI/CD pipeline
- Kubernetes deployment

---

## Author

**Zishuo Xia**

MS in Computer Science (Incoming)
University of Southern California

Backend | Distributed Systems | Cloud Computing | Robotics Software
