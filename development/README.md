# Kafka, Redis, PostgreSQL

Yêu cầu Docker Engine/Docker Desktop đang chạy và Docker Compose v2 trở lên.
Hai file Compose chạy độc lập, có project name và volume riêng cho local/prod.
Local chỉ có hạ tầng; prod chạy thêm service `app` từ image đã build sẵn
(`development/Dockerfile`).

## Local

Từ thư mục gốc repository:

```sh
# Không bắt buộc: sao chép để đổi mật khẩu hoặc cổng mặc định.
cp development/.env.local.example development/.env.local
sh development/scripts/run-docker.sh
sh development/scripts/run-docker.sh ps
sh development/scripts/run-docker.sh logs -f
sh development/scripts/run-docker.sh down
```

Script không có tham số sẽ chạy `up -d --wait --wait-timeout 180`.
Có thể gọi script bằng đường dẫn tuyệt đối từ bất kỳ thư mục nào.

| Dịch vụ | Kết nối từ máy local | Kết nối từ container cùng mạng |
| --- | --- | --- |
| PostgreSQL | `localhost:5432` | `pgsql:5432` |
| Redis | `localhost:6379` | `redis:6379` |
| Kafka | `localhost:9092` | `kafka:29092` |

PostgreSQL mặc định: database/user `do_something`, password `local_password`.
Redis local không đặt mật khẩu. Các cổng chỉ bind vào `127.0.0.1`.
Đổi cổng trong `.env.local` nếu máy đang có dịch vụ sử dụng cổng đó.

Ví dụ biến môi trường cho Spring Boot chạy trên máy local:

```dotenv
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/do_something
SPRING_DATASOURCE_USERNAME=do_something
SPRING_DATASOURCE_PASSWORD=local_password
SPRING_DATA_REDIS_HOST=localhost
SPRING_DATA_REDIS_PORT=6379
SPRING_KAFKA_BOOTSTRAP_SERVERS=localhost:9092
```

`pom.xml` đã bao gồm starter Redis/Kafka và PostgreSQL JDBC driver.

## Production trên một máy chủ

```sh
cp development/.env.prod.example development/.env.prod
# Điền POSTGRES_PASSWORD và REDIS_PASSWORD trước khi chạy.
chmod 600 development/.env.prod
sh development/scripts/run-docker-product.sh
sh development/scripts/run-docker-product.sh ps
```

Không commit file `.env.prod`. Compose từ chối chạy nếu mật khẩu trống.
Mật khẩu được truyền bằng biến môi trường; người có quyền Docker có thể đọc được.

Prod chạy `app` (Spring Boot) cùng hạ tầng. Service `app` lấy image từ biến
`APP_IMAGE`, chờ pgsql/redis/kafka healthy mới khởi động, nhận sẵn các biến
`SPRING_DATASOURCE_*`, `SPRING_DATA_REDIS_*`, `SPRING_KAFKA_BOOTSTRAP_SERVERS`
theo `.env.prod`, và chỉ publish `127.0.0.1:${APP_PORT:-8080}`. Đặt reverse proxy
phía trước nếu cần mở ra Internet.

```sh
APP_IMAGE=ghcr.io/<owner>/do-something:latest \
  sh development/scripts/run-docker-product.sh up -d --wait
```

Biến môi trường của shell được ưu tiên hơn `.env.prod`, nên CD chỉ cần export
`APP_IMAGE` trước khi gọi script. Build image thủ công từ thư mục gốc repository:

```sh
docker build -f development/Dockerfile -t do-something:local .
```

Mạng `default` là `internal`; riêng `app` gắn thêm mạng `edge` để gọi được ra
ngoài. Hạ tầng không publish cổng ra host. Ứng dụng nằm ở Compose khác muốn dùng
chung hạ tầng thì khai báo mạng đó là external:

```yaml
services:
  app:
    networks: [backend]
networks:
  backend:
    external: true
    name: do-something-prod_default
```

Dùng các địa chỉ container trong bảng trên; đặt `SPRING_DATASOURCE_PASSWORD` và
`SPRING_DATA_REDIS_PASSWORD` khớp với `.env.prod`.

Kafka dùng KRaft, một node broker/controller, không cần ZooKeeper.
Prod tắt tự tạo topic; tạo topic rõ ràng trước khi sử dụng:

```sh
sh development/scripts/run-docker-product.sh exec kafka \
  /opt/kafka/bin/kafka-topics.sh --bootstrap-server kafka:29092 \
  --create --if-not-exists --topic example.events --partitions 3 --replication-factor 1
```

Đây là cấu hình một máy chủ, không có HA: PostgreSQL, Redis và Kafka đều có một instance.
Kafka dùng PLAINTEXT trong mạng Docker riêng, chưa có TLS/SASL; chỉ cho phép container
đáng tin cậy tham gia mạng. Khi cần HA hoặc kết nối qua nhiều máy chủ, cần thiết kế
cluster, xác thực và TLS riêng. Không mở trực tiếp listener hiện tại ra Internet.

## Health check và tài liệu API

`GET /api/health` là endpoint public (không cần xác thực) để load balancer,
Docker healthcheck và uptime monitor gọi. Nó đọc lại health indicator của
Actuator nên PostgreSQL, Redis, Kafka và disk được kiểm tra sẵn. Trả `200` khi
tất cả UP, `503` khi có thành phần DOWN:

```json
{
  "status": "UP",
  "application": "do-something",
  "version": "0.0.1-SNAPSHOT",
  "timestamp": "2026-09-07T03:21:00Z",
  "components": { "db": "UP", "diskSpace": "UP", "ping": "UP", "redis": "UP" }
}
```

Mọi endpoint khác yêu cầu HTTP Basic (`SecurityConfig`). `/actuator/**` không
nằm trong danh sách public nên vẫn cần xác thực.

Tài liệu OpenAPI sinh bằng springdoc:

| | URL |
| --- | --- |
| Swagger UI | `http://localhost:8080/swagger-ui.html` |
| OpenAPI JSON | `http://localhost:8080/v3/api-docs` |

Prod tắt cả hai (`SPRINGDOC_API_DOCS_ENABLED`/`SPRINGDOC_SWAGGER_UI_ENABLED`
= `false`). Đặt `SWAGGER_ENABLED=true` trong `.env.prod` nếu thật sự cần bật,
và chỉ mở qua reverse proxy có xác thực.

## Deploy tự động (GitHub Actions)

`.github/workflows/cd.yml` chỉ chạy khi push vào `main` (hoặc `workflow_dispatch`
từ `main`): build image bằng `development/Dockerfile`, push lên
`ghcr.io/<owner>/<repo>` với tag `<sha>` và `latest`, rồi SSH vào server để
`pull` + `up -d --wait`.

Secrets đặt trong **environment `production`** (Settings → Environments →
`production` → Environment secrets), không phải repository secrets. Job `deploy`
khai báo `environment: production` nên chỉ job đó đọc được; job `build` thì
không. Thiếu secret sẽ fail ngay ở bước `Check production secrets`.

| Tên | Bắt buộc | Ý nghĩa |
| --- | --- | --- |
| `SSH_HOST` | có | Host/IP của server prod |
| `SSH_USER` | có | User SSH, phải nằm trong group `docker` |
| `SSH_PRIVATE_KEY` | có | Private key deploy (không đặt passphrase) |
| `SSH_KNOWN_HOSTS` | nên có | Host key đã pin; thiếu thì workflow dùng `ssh-keyscan` |

Environment variables tuỳ chọn (`vars`, cũng đặt trong environment
`production`): `DEPLOY_PATH` (mặc định `/opt/do-something`), `SSH_PORT`
(mặc định `22`).

Thứ tự tra cứu của GitHub là environment → repository → organization, nên secret
trùng tên ở cấp repository sẽ bị environment ghi đè. Environment còn cho phép bật
protection rule: yêu cầu reviewer duyệt, giới hạn branch `main`, hoặc wait timer
trước khi deploy chạy.

Chuẩn bị trên server trước lần deploy đầu:

```sh
sudo mkdir -p /opt/do-something/development
sudo chown "$USER" /opt/do-something -R
# Chép .env.prod.example, điền mật khẩu, rồi:
chmod 600 /opt/do-something/development/.env.prod
```

Workflow chỉ giải nén `development/` vào `DEPLOY_PATH`; `.env.prod` trên server
không nằm trong gói nên không bị ghi đè. Rollback bằng cách chạy lại script với
`APP_IMAGE` trỏ về tag `<sha>` cũ.

Workflow `docker logout ghcr.io` sau khi deploy vì `GITHUB_TOKEN` hết hạn cùng
job. Muốn `pull` thủ công trên server thì tự đăng nhập bằng personal access token
có quyền `read:packages`.

Test hiện chưa chạy trong CD: `DoSomethingApplicationTests.contextLoads` cần
DataSource nên `./mvnw verify` fail. Khi có CI riêng (Testcontainers hoặc service
containers), thêm `needs:` vào job `build`.

## Dữ liệu và vận hành

- Dữ liệu được giữ trong named volume khi restart hoặc `down`.
- `down -v` xóa toàn bộ dữ liệu của môi trường tương ứng; chỉ dùng khi muốn reset.
- Redis bật AOF với `appendfsync everysec`; có thể mất khoảng một giây ghi khi sự cố.
- Kafka giữ log 7 ngày; theo dõi dung lượng đĩa và điều chỉnh retention theo nhu cầu.
- PostgreSQL 18 mount tại `/var/lib/postgresql`. Đổi biến `POSTGRES_*` không cập nhật
  user/password/database đã khởi tạo trong volume; cần thay đổi bằng SQL.
- Giữ nguyên `KAFKA_CLUSTER_ID` khi tái sử dụng volume Kafka.
- Cần backup và kiểm tra restore PostgreSQL cùng dữ liệu nghiệp vụ trước khi vận hành.
- PostgreSQL/Redis dùng tag major (`18-alpine`, `8-alpine`); Kafka dùng `4.1.2`.
  Khi phát hành, nên chốt digest image đã kiểm thử và không nâng major PostgreSQL
  trực tiếp trên volume cũ.

Tham khảo image chính thức: [Kafka](https://hub.docker.com/r/apache/kafka/),
[Redis](https://hub.docker.com/_/redis), [PostgreSQL](https://hub.docker.com/_/postgres).
