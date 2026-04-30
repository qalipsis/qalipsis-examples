# QALIPSIS Distributed System - Helm Chart

Helm chart equivalent of `../docker-compose.yml`, deploying the full QALIPSIS distributed system demo to Kubernetes.

## Architecture

```
                           +-------+
                           | NGINX |  (reverse proxy)
                           +---+---+
                               |
          +--------------------+--------------------+
          |          |         |         |           |
     +----+---+ +---+---+ +--+---+ +---+------+ +--+--------+
     | Kibana | |Elastic-| |Influx| | Redpanda | | http-to-  |
     +----+---+ |search  | |DB    | | Console  | | kafka     |
          |     +---+----+ +------+ +---+------+ +-----+-----+
          |         |                    |             |
          |         |           +--------+--------+    |
          |         |           |    Redpanda     |<---+
          |         |           +-----------------+
          |         |                    |
          |         |              +-----+----+    +-------+
          |         |              | kafka-   +--->| Redis |
          |         |              | to-db    |    +-------+
          |         |              +-----+----+
          |         |                    |
          |         |              +-----+------+  +--------+
          |         |              | TimescaleDB|  | pgAdmin|
          |         |              +------------+  +--------+
```

### Docker-Compose to Helm Mapping

| Docker-Compose Service | Helm Component   | K8s Kind    | Notes                                                                  |
|------------------------|------------------|-------------|------------------------------------------------------------------------|
| `elasticsearch`        | elasticsearch    | Deployment  | PVC for data persistence                                               |
| `kibana`               | kibana           | Deployment  | Init container waits for Elasticsearch                                 |
| `redis`                | redis            | Deployment  | Stateless, no persistence needed                                       |
| `redpanda`             | redpanda         | StatefulSet | VolumeClaimTemplate for broker data                                    |
| `redpanda-console`     | redpanda-console | Deployment  | Config injected via env, waits for Redpanda                            |
| `timescale`            | timescale        | StatefulSet | VolumeClaimTemplate + init ConfigMap                                   |
| `pgadmin`              | pgadmin          | Deployment  | servers.json via ConfigMap                                             |
| `http-to-kafka`        | http-to-kafka    | Deployment  | Waits for Redpanda                                                     |
| `kafka-to-db`          | kafka-to-db      | Deployment  | Waits for Redis, Redpanda, TimescaleDB                                 |
| `influxdb`             | influxdb         | Deployment  | PVC for data persistence                                               |
| `healthy_checker`      | *(removed)*      | -           | Replaced by K8s readiness/liveness probes                              |
| *(new)*                | nginx            | Deployment  | Reverse proxy to Kibana, ES, InfluxDB, Redpanda Console, HTTP-to-Kafka |

### NGINX Reverse Proxy

The chart includes an NGINX reverse proxy that provides a single entry point to access the UI and API services. When
deployed, the following paths are available:

| Path                 | Backend Service  | Description                     |
|----------------------|------------------|---------------------------------|
| `/`                  | *(built-in)*     | Landing page with links         |
| `/kibana/`           | Kibana           | Kibana dashboard (WebSocket)    |
| `/elasticsearch/`    | Elasticsearch    | Elasticsearch REST API          |
| `/influxdb/`         | InfluxDB         | InfluxDB HTTP API and UI        |
| `/redpanda-console/` | Redpanda Console | Redpanda Console UI (WebSocket) |
| `/http-to-kafka/`    | HTTP-to-Kafka    | HTTP-to-Kafka bridge API        |

The NGINX service is exposed as `NodePort` by default. Change to `LoadBalancer` for cloud environments:

```bash
helm install my-release . --set nginx.service.type=LoadBalancer
```

### Key Differences from Docker-Compose

- **`depends_on`** is replaced by **init containers** that poll with `nc -z` until the dependency is reachable.
- **Docker healthchecks** become Kubernetes **readiness and liveness probes** (httpGet, tcpSocket, or exec).
- **Named volumes** become **PersistentVolumeClaims** or **VolumeClaimTemplates** (for StatefulSets).
- **Bind-mounted config files** become **ConfigMaps**.
- **Passwords** are stored in **Secrets** (base64-encoded in templates, sourced from `values.yaml`).
- **Port mappings** become **Services** (ClusterIP by default; switch to NodePort/LoadBalancer for external access).

## Prerequisites

- Kubernetes cluster (1.24+)
- Helm 3.x
- `kubectl` configured to reach your cluster
- A StorageClass available for persistent volumes (or use `emptyDir` by disabling persistence)

## Quick Start

```bash
# From the distributed-system/ directory
cd helm/qalipsis-distributed

# Dry-run to preview rendered manifests
helm template my-release . | less

# Install
helm install my-release .

# Install into a specific namespace
helm install my-release . --namespace qalipsis --create-namespace
```

## Configuration

All configuration is in `values.yaml`. Every component can be independently enabled/disabled.

### Disabling a Component

```bash
helm install my-release . --set kibana.enabled=false --set pgadmin.enabled=false
```

### Custom Values File

```bash
helm install my-release . -f my-custom-values.yaml
```

### Key Configuration Parameters

| Parameter                        | Default                                      | Description                          |
|----------------------------------|----------------------------------------------|--------------------------------------|
| `elasticsearch.enabled`          | `true`                                       | Deploy Elasticsearch                 |
| `elasticsearch.image.repository` | `docker.elastic.co/.../elasticsearch`        | Image repository                     |
| `elasticsearch.image.tag`        | `7.17.21`                                    | Image tag                            |
| `elasticsearch.javaOpts`         | `-Xms256m -Xmx256m`                          | JVM heap settings                    |
| `elasticsearch.persistence.size` | `5Gi`                                        | PVC size                             |
| `kibana.enabled`                 | `true`                                       | Deploy Kibana                        |
| `kibana.image.repository`        | `docker.elastic.co/kibana/kibana`            | Image repository                     |
| `kibana.image.tag`               | `7.17.21`                                    | Image tag                            |
| `redis.enabled`                  | `true`                                       | Deploy Redis                         |
| `redis.image.repository`         | `redis`                                      | Image repository                     |
| `redis.image.tag`                | `latest`                                     | Image tag                            |
| `redpanda.enabled`               | `true`                                       | Deploy Redpanda broker               |
| `redpanda.image.repository`      | `docker.redpanda.com/.../redpanda`           | Image repository                     |
| `redpanda.image.tag`             | `v24.1.3`                                    | Image tag                            |
| `redpanda.persistence.size`      | `5Gi`                                        | Broker data PVC size                 |
| `redpandaConsole.enabled`        | `true`                                       | Deploy Redpanda Console              |
| `timescale.enabled`              | `true`                                       | Deploy TimescaleDB                   |
| `timescale.image.repository`     | `timescale/timescaledb`                      | Image repository                     |
| `timescale.image.tag`            | `latest-pg16`                                | Image tag                            |
| `timescale.postgres.user`        | `qalipsis_demo`                              | PostgreSQL user                      |
| `timescale.postgres.password`    | `qalipsis`                                   | PostgreSQL password                  |
| `timescale.postgres.database`    | `qalipsis`                                   | PostgreSQL database name             |
| `timescale.persistence.size`     | `5Gi`                                        | PostgreSQL data PVC size             |
| `pgadmin.enabled`                | `true`                                       | Deploy pgAdmin                       |
| `httpToKafka.enabled`            | `true`                                       | Deploy HTTP-to-Kafka bridge          |
| `httpToKafka.kafka.topic`        | `http-request`                               | Target Kafka topic                   |
| `kafkaToDb.enabled`              | `true`                                       | Deploy Kafka-to-DB service           |
| `kafkaToDb.image.repository`     | `aerisconsulting/qalipsis-demo-microservice` | Image repository                     |
| `kafkaToDb.image.tag`            | `0.1.1`                                      | Image tag                            |
| `influxdb.enabled`               | `true`                                       | Deploy InfluxDB                      |
| `influxdb.init.org`              | `qalipsis`                                   | InfluxDB organization                |
| `influxdb.init.bucket`           | `iot`                                        | InfluxDB bucket                      |
| `influxdb.persistence.size`      | `5Gi`                                        | InfluxDB data PVC size               |
| `nginx.enabled`                  | `true`                                       | Deploy NGINX reverse proxy           |
| `nginx.image.repository`         | `nginx`                                      | Image repository                     |
| `nginx.image.tag`                | `1.27-alpine`                                | Image tag                            |
| `nginx.service.type`             | `NodePort`                                   | Service type (NodePort/LoadBalancer) |

## Debugging Guide

### 1. Check Overall Release Status

```bash
# List all releases
helm list -A

# Get status of a specific release
helm status my-release

# See the history of a release (useful after failed upgrades)
helm history my-release
```

### 2. Check Pod Status

```bash
# Overview of all pods in the release
kubectl get pods -l "app.kubernetes.io/part-of=qalipsis-distributed"

# Describe a specific pod to see events (scheduling, pulling, probes)
kubectl describe pod <pod-name>

# Check pod logs
kubectl logs <pod-name>
kubectl logs <pod-name> -c <container-name>    # specific container
kubectl logs <pod-name> -c <init-container>     # init container logs
kubectl logs <pod-name> --previous              # logs from previous crash
```

### 3. Common Issues and Fixes

#### Pods stuck in `Pending`

- **Cause**: No StorageClass available or insufficient cluster resources.
- **Fix**: Check `kubectl describe pod <name>` for events. If PVC-related:
  ```bash
  kubectl get pvc
  kubectl describe pvc <pvc-name>
  ```
  Disable persistence if you don't need it:
  ```bash
  helm upgrade my-release . \
    --set elasticsearch.persistence.enabled=false \
    --set redpanda.persistence.enabled=false \
    --set timescale.persistence.enabled=false \
    --set influxdb.persistence.enabled=false
  ```

#### Pods stuck in `Init:0/1`

- **Cause**: Init container waiting for a dependency service that isn't ready.
- **Fix**: Check which init container is blocked:
  ```bash
  kubectl logs <pod-name> -c wait-for-elasticsearch
  kubectl logs <pod-name> -c wait-for-redpanda
  kubectl logs <pod-name> -c wait-for-dependencies
  ```
  Then check the dependency pod:
  ```bash
  kubectl get pods -l app.kubernetes.io/name=elasticsearch
  kubectl logs <dependency-pod-name>
  ```

#### Pods in `CrashLoopBackOff`

- **Cause**: Application fails to start (bad config, dependency unreachable, OOM).
- **Fix**:
  ```bash
  kubectl logs <pod-name> --previous    # see why it crashed
  kubectl describe pod <pod-name>       # check OOMKilled, resource limits
  ```
  If OOM, increase memory limits in `values.yaml`:
  ```bash
  helm upgrade my-release . --set elasticsearch.resources.limits.memory=2Gi
  ```

#### Readiness/Liveness probe failures

- **Cause**: Service is slow to start or probe path is wrong.
- **Fix**: Check events on the pod:
  ```bash
  kubectl describe pod <pod-name>
  # Look for "Unhealthy" events with probe details
  ```
  Exec into the pod to test manually:
  ```bash
  kubectl exec -it <pod-name> -- curl -s localhost:9200    # elasticsearch
  kubectl exec -it <pod-name> -- redis-cli ping            # redis
  kubectl exec -it <pod-name> -- pg_isready                # timescale
  ```

### 4. Inspect Rendered Templates

```bash
# Render all templates without installing
helm template my-release . > rendered.yaml

# Render a specific template
helm template my-release . -s templates/elasticsearch-deployment.yaml

# Render with custom values
helm template my-release . -f custom-values.yaml

# Show computed values (merged defaults + overrides)
helm get values my-release
helm get values my-release --all    # includes defaults
```

### 5. Debug Helm Template Issues

```bash
# Enable debug output
helm template my-release . --debug 2>&1 | less

# Lint the chart for structural errors
helm lint .

# Dry-run install to catch K8s API validation errors
helm install my-release . --dry-run --debug
```

### 6. Access Services Locally

**Via the NGINX reverse proxy** (recommended -- single port to forward):

```bash
# Forward the NGINX proxy port
kubectl port-forward svc/my-release-qalipsis-distributed-nginx 8080:80

# Then open in browser:
#   http://localhost:8080/                   -> Landing page
#   http://localhost:8080/kibana/            -> Kibana
#   http://localhost:8080/elasticsearch/     -> Elasticsearch API
#   http://localhost:8080/influxdb/          -> InfluxDB
#   http://localhost:8080/redpanda-console/  -> Redpanda Console
#   http://localhost:8080/http-to-kafka/     -> HTTP-to-Kafka
```

**Direct port-forward** (bypassing the proxy, for debugging individual services):

```bash
kubectl port-forward svc/my-release-qalipsis-distributed-elasticsearch 9200:9200
kubectl port-forward svc/my-release-qalipsis-distributed-kibana 5601:5601
kubectl port-forward svc/my-release-qalipsis-distributed-redpanda-console 28080:8080
kubectl port-forward svc/my-release-qalipsis-distributed-pgadmin 25433:80
kubectl port-forward svc/my-release-qalipsis-distributed-influxdb 18086:8086
kubectl port-forward svc/my-release-qalipsis-distributed-http-to-kafka 18443:8443
```

### 7. Debug the NGINX Reverse Proxy

```bash
# Check NGINX pod status and logs
kubectl get pods -l app.kubernetes.io/name=nginx
kubectl logs -l app.kubernetes.io/name=nginx

# View the rendered nginx.conf inside the pod
kubectl exec -it <nginx-pod> -- cat /etc/nginx/nginx.conf

# Test upstream connectivity from inside NGINX
kubectl exec -it <nginx-pod> -- wget -qO- http://<fullname>-elasticsearch:9200

# Reload config after a helm upgrade (automatic via checksum annotation)
# The pod restarts automatically when the ConfigMap changes
```

### 8. Upgrade and Rollback

```bash
# Upgrade with new values
helm upgrade my-release . -f new-values.yaml

# Rollback to previous revision
helm rollback my-release

# Rollback to a specific revision
helm history my-release
helm rollback my-release <revision-number>
```

### 9. Full Cleanup

```bash
# Uninstall the release
helm uninstall my-release

# PVCs are NOT deleted automatically - clean them up manually if needed
kubectl delete pvc -l "app.kubernetes.io/part-of=qalipsis-distributed"
```

## Improving the Chart

### Adding Ingress

Create `templates/ingress.yaml` to expose services externally without port-forwarding:

```yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: {{ include "qalipsis.fullname" . }}
spec:
  rules:
    - host: kibana.example.com
      http:
        paths:
          - path: /
            pathType: Prefix
            backend:
              service:
                name: {{ include "qalipsis.fullname" . }}-kibana
                port:
                  number: 5601
```

### Adding NetworkPolicies

Restrict traffic between components (e.g., only `kafka-to-db` can reach Redis):

```yaml
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: redis-access
spec:
  podSelector:
    matchLabels:
      app.kubernetes.io/name: redis
  ingress:
    - from:
        - podSelector:
            matchLabels:
              app.kubernetes.io/name: kafka-to-db
```

### Production Hardening Checklist

- [ ] Replace default passwords in `values.yaml` or use external secret management (Sealed Secrets, Vault)
- [ ] Set appropriate resource requests/limits based on load testing
- [ ] Configure `storageClass` for each persistent component
- [ ] Add Ingress resources with TLS termination
- [ ] Add NetworkPolicies to restrict inter-service traffic
- [ ] Add PodDisruptionBudgets for critical services
- [ ] Consider using the official Helm charts for Elasticsearch, Redis, and InfluxDB in production
- [ ] Add Prometheus ServiceMonitors for observability
- [ ] Configure anti-affinity rules to spread pods across nodes

## File Structure

```
helm/qalipsis-distributed/
  Chart.yaml                              # Chart metadata
  values.yaml                             # Default configuration values
  templates/
    _helpers.tpl                          # Shared template helpers
    elasticsearch-deployment.yaml         # Elasticsearch Deployment
    elasticsearch-service.yaml            # Elasticsearch Service
    elasticsearch-pvc.yaml                # Elasticsearch PersistentVolumeClaim
    kibana-deployment.yaml                # Kibana Deployment
    kibana-service.yaml                   # Kibana Service
    redis-deployment.yaml                 # Redis Deployment
    redis-service.yaml                    # Redis Service
    redpanda-statefulset.yaml             # Redpanda StatefulSet
    redpanda-service.yaml                 # Redpanda Service
    redpanda-console-deployment.yaml      # Redpanda Console Deployment
    redpanda-console-service.yaml         # Redpanda Console Service
    timescale-statefulset.yaml            # TimescaleDB StatefulSet
    timescale-service.yaml                # TimescaleDB Service
    timescale-configmap.yaml              # TimescaleDB init scripts
    timescale-secret.yaml                 # TimescaleDB credentials
    pgadmin-deployment.yaml               # pgAdmin Deployment
    pgadmin-service.yaml                  # pgAdmin Service
    pgadmin-configmap.yaml                # pgAdmin server config
    http-to-kafka-deployment.yaml         # HTTP-to-Kafka Deployment
    http-to-kafka-service.yaml            # HTTP-to-Kafka Service
    kafka-to-db-deployment.yaml           # Kafka-to-DB Deployment
    influxdb-deployment.yaml              # InfluxDB Deployment
    influxdb-service.yaml                 # InfluxDB Service
    influxdb-pvc.yaml                     # InfluxDB PersistentVolumeClaim
    influxdb-secret.yaml                  # InfluxDB credentials
    nginx-deployment.yaml                 # NGINX reverse proxy Deployment
    nginx-service.yaml                    # NGINX reverse proxy Service
    nginx-configmap.yaml                  # NGINX configuration (nginx.conf)
```
