# Distributed File Storage System

A production-ready, secure distributed file storage system built with
Spring Boot (gateway + storage nodes) and React (dashboard).

---

## Architecture Overview

```
  Browser / React
       │  JWT in Authorization header
       ▼
┌──────────────────────────────────────────┐
│           Gateway Server  :8080          │
│                                          │
│  ┌────────────┐   ┌──────────────────┐  │
│  │ JWT Filter │   │ FileController   │  │
│  └────────────┘   └────────┬─────────┘  │
│  ┌────────────┐            │            │
│  │  MySQL DB  │   ┌────────▼─────────┐  │
│  │  metadata  │   │FileChunkingService│  │
│  └────────────┘   └────────┬─────────┘  │
│  ┌────────────────────────▼──────────┐  │
│  │       LoadBalancerService          │  │
│  │  Weighted Least-Load + Zone Aware  │  │
│  └──────┬──────────┬─────────┬───────┘  │
└─────────┼──────────┼─────────┼──────────┘
    JWT   │    JWT   │   JWT   │
          ▼          ▼         ▼
    ┌──────────┐ ┌──────────┐ ┌──────────┐
    │  Node 1  │ │  Node 2  │ │  Node 3  │
    │ :8081    │ │ :8082    │ │ :8083    │
    │ zone-a   │ │ zone-b   │ │ zone-c   │
    └──────────┘ └──────────┘ └──────────┘
    /data/dfs/     /data/dfs/    /data/dfs/
    node-1/        node-2/       node-3/
```

---

## Project Structure

```
distributed-file-storage/
│
├── gateway-server/                    # Spring Boot — orchestrator
│   ├── Dockerfile
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/dfs/
│       │   ├── GatewayApplication.java
│       │   ├── config/
│       │   │   ├── SecurityConfig.java        # Spring Security + CORS
│       │   │   ├── StorageProperties.java     # Node registry config
│       │   │   └── GlobalExceptionHandler.java
│       │   ├── controller/
│       │   │   ├── AuthController.java        # /api/auth/**
│       │   │   ├── FileController.java        # /api/files/**
│       │   │   └── NodeController.java        # /api/nodes/** (admin)
│       │   ├── dto/
│       │   │   ├── AuthRequest.java
│       │   │   ├── AuthResponse.java
│       │   │   ├── RegisterRequest.java
│       │   │   └── ChunkUploadResult.java
│       │   ├── model/
│       │   │   ├── User.java                  # users table
│       │   │   ├── FileMetadata.java          # files table
│       │   │   ├── FileChunk.java             # file_chunks table
│       │   │   └── StorageNode.java           # storage_nodes table
│       │   ├── repository/
│       │   │   ├── UserRepository.java
│       │   │   ├── FileMetadataRepository.java
│       │   │   ├── FileChunkRepository.java
│       │   │   └── StorageNodeRepository.java
│       │   ├── security/
│       │   │   ├── JwtService.java            # Token generation & validation
│       │   │   └── JwtAuthenticationFilter.java
│       │   └── service/
│       │       ├── FileChunkingService.java   # ★ Core chunking + failover
│       │       ├── LoadBalancerService.java   # ★ Node selection algorithm
│       │       └── NodeHealthMonitor.java     # Heartbeat + purge scheduler
│       └── resources/
│           ├── application.yml
│           └── schema.sql
│
├── storage-node/                      # Spring Boot — physical storage
│   ├── Dockerfile
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/dfs/node/
│       │   ├── StorageNodeApplication.java
│       │   ├── config/
│       │   │   ├── NodeProperties.java
│       │   │   └── NodeSecurityConfig.java
│       │   ├── controller/
│       │   │   └── ChunkController.java       # /api/chunks/**
│       │   ├── security/
│       │   │   └── NodeJwtFilter.java         # Validates every request
│       │   └── service/
│       │       └── ChunkStorageService.java   # Disk I/O + checksum
│       └── resources/
│           └── application.yml                # Profiles: node2, node3
│
├── frontend/                          # React + Vite + Tailwind
│   ├── Dockerfile
│   ├── nginx.conf
│   ├── package.json
│   ├── vite.config.js
│   ├── tailwind.config.js
│   └── src/
│       ├── main.jsx
│       ├── App.jsx                    # Router + sidebar shell
│       ├── index.css
│       ├── hooks/
│       │   └── useAuth.js             # AuthContext + JWT storage
│       ├── services/
│       │   └── api.js                 # Axios instance + all API calls
│       └── pages/
│           ├── Login.jsx
│           ├── Dashboard.jsx          # Upload (drag & drop + progress bar)
│           └── Trash.jsx              # Soft-delete restoration UI
│
└── docker-compose.yml                 # One-command full stack
```

---

## Database Schema

```sql
users          — id, username, email, password_hash, role, quota, used
storage_nodes  — id (node-1…), base_url, zone, status, capacity, heartbeat
files          — id, owner_id, stored_name (UUID), mime, size, checksum,
                 upload_status, deleted_at (soft-delete)
file_chunks    — id, file_id, node_id, chunk_order, chunk_size, checksum,
                 is_replica, storage_path
refresh_tokens — id, user_id, token_hash, expires_at, revoked
```

---

## Core Algorithms

### Chunking  (`FileChunkingService`)
1. Read full file bytes from the multipart upload
2. Split into N slices of `chunkSizeBytes` (default 1 MB)
3. For each chunk: compute SHA-256 checksum, call `LoadBalancerService`
4. POST chunk bytes to each selected node via `RestTemplate`
5. Persist `FileChunk` records (primary + replicas) with `storage_path`
6. Mark `FileMetadata.upload_status = COMPLETE` when all chunks land

### Load Balancing  (`LoadBalancerService`)
```
Strategy: Weighted Least-Load + Zone Awareness

1. Filter: status=ONLINE, lastHeartbeat < 2 min ago,
           availableCapacity >= chunkSize
2. Sort by usedCapacity/totalCapacity ascending (least loaded first)
3. Greedily pick from unused availability zones
4. Fall back to any healthy node when zones are exhausted
```

### Failover Download
```
For each chunk index 0..N-1:
  copies = [primary, replica-1, replica-2]  (sorted: primary first)
  for copy in copies:
    if copy.nodeId NOT in failedNodes:
      try:
        data = GET node.baseUrl/api/chunks/{id}
        verify SHA-256(data) == chunk.checksum
        break  ← success
      catch:
        failedNodes.add(copy.nodeId)
  if no copy succeeded → IOException("All replicas failed")
Reassemble byte stream → verify full-file SHA-256
```

### Soft Delete / Recovery
```
softDelete(file):  SET deleted_at = NOW()         (30-day window begins)
restore(file):     SET deleted_at = NULL           (if within 30 days)
hardDelete(file):  DELETE chunks from all nodes + DELETE metadata row
purgeJob (@cron):  DELETE files WHERE deleted_at < NOW() - 30 DAYS
```

---

## Security Model

| Layer | Mechanism |
|---|---|
| User auth | BCrypt(12) passwords · HS256 JWT (24 h TTL) |
| Gateway routes | `SecurityFilterChain` — stateless, bearer-only |
| Storage node | `NodeJwtFilter` — verifies signature on every request |
| Secret sharing | Nodes share the same `jwt.secret` — no round-trip auth |
| CORS | Restricted to `http://localhost:3000` (override in prod) |
| Headers | `X-Frame-Options DENY`, `X-Content-Type-Options nosniff` |

---

## Quick Start

### Option A — Docker Compose (recommended)
```bash
# 1. Clone and build
git clone <repo> && cd distributed-file-storage

# 2. Start everything (MySQL + Gateway + 3 Nodes + Frontend)
docker compose up --build

# 3. Open the dashboard
open http://localhost:3000
```

### Option B — Run locally
```bash
# 1. Start MySQL (or update application.yml to point at your instance)
mysql -u root -p < gateway-server/src/main/resources/schema.sql

# 2. Start Gateway
cd gateway-server
mvn spring-boot:run

# 3. Start Nodes (3 separate terminals)
cd storage-node
mvn spring-boot:run                                    # Node 1 → :8081
mvn spring-boot:run -Dspring-boot.run.profiles=node2  # Node 2 → :8082
mvn spring-boot:run -Dspring-boot.run.profiles=node3  # Node 3 → :8083

# 4. Start Frontend
cd frontend
npm install && npm run dev   # → http://localhost:3000
```

---

## API Reference

### Auth
| Method | Endpoint | Body |
|--------|----------|------|
| POST | `/api/auth/register` | `{username, email, password}` |
| POST | `/api/auth/login` | `{username, password}` |

### Files (requires `Authorization: Bearer <token>`)
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET  | `/api/files` | List active files |
| POST | `/api/files/upload` | Upload (multipart/form-data `file=`) |
| GET  | `/api/files/{id}/download` | Download reassembled file |
| DELETE | `/api/files/{id}` | Soft-delete (move to trash) |
| GET  | `/api/files/trash` | List trashed files |
| POST | `/api/files/{id}/restore` | Restore from trash |
| DELETE | `/api/files/{id}/permanent` | Permanent delete |

### Nodes (admin role required)
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/nodes` | All nodes + status |
| GET | `/api/nodes/healthy` | Only ONLINE nodes |

---

## Extending to Production

- **TLS**: terminate at nginx/load balancer; use `https://` in node URLs
- **Secrets**: move `jwt.secret` to Vault or AWS Secrets Manager
- **Node auth**: add an `X-Node-Token` secret for node-to-node comms
- **Resumable uploads**: replace single-request upload with a chunked
  presign-then-PUT protocol (tus.io or custom)
- **Replication repair**: background job to detect and re-replicate chunks
  whose node went permanently offline
- **Monitoring**: Micrometer → Prometheus → Grafana dashboards

---

*Built with Spring Boot 3.2, Spring Security 6, JJWT 0.12, React 18,
Vite 5, Tailwind CSS 3, MySQL 8.2.*
