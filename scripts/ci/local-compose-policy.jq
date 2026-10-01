def check($condition; $message):
  if $condition then . else error($message) end;

def pinned: test("^[^[:space:]@]+@sha256:[0-9a-f]{64}$");
def loopback($port):
  any(.services.app.ports[]?; .host_ip == "127.0.0.1" and .target == $port);
def service_loopback($service; $port):
  any(.services[$service].ports[]?; .host_ip == "127.0.0.1" and .target == $port);
def no_sensitive_values:
  all(.environment | keys[];
    (test("(PASSWORD|SECRET|TOKEN|API_KEY)$") | not));
def grant($service; $source; $target):
  any(.services[$service].secrets[]?; .source == $source and .target == $target);

check(.name == "persefonia-local"; "local Compose project name changed")
| check((.services | keys) == ["app", "postgres", "redis"]; "local Compose must contain exactly app, postgres, redis")
| check(all(.services[]; (has("build") | not) and (.privileged // false) == false);
    "local services must not build images or run privileged")
| check(all(.services[]; .image | pinned); "all local images must have immutable SHA-256 digests")
| check(.services.app.image == "ghcr.io/ringcentral-docker/node:22.22.2-jdk25@sha256:d8b709dbd229236715fa16403e68c6e310fde38e285cd0bfc788f049a3cab535";
    "app must use the qualified upstream development toolchain")
| check(.services.postgres.image == "postgres:17.11-alpine@sha256:18cfe3ef5e6815560c98237d6216d1e5119702fb0f3894c8785dd58b8bbe5d73";
    "PostgreSQL image differs from the accepted identity")
| check(.services.redis.image == "redis:8.10-alpine@sha256:becdda6c7f4b3fb42e42fd7f120bbf5c54c4caaaf16f26da24e4563d2c1f0576";
    "Redis image differs from the accepted identity")
| check(.services.app.working_dir == "/workspace"
    and .services.app.command == ["./gradlew", "--no-daemon", "--console=plain", ":app:bootRun"]
    and .services.app.stop_grace_period == "30s"
    and (.services.app.volumes | length) == 1
    and .services.app.volumes[0].type == "bind"
    and .services.app.volumes[0].source == $workspace
    and .services.app.volumes[0].target == "/workspace";
    "app must run the current bind-mounted source through Gradle bootRun with a graceful stop budget")
| check((.services.app.user | test("^[1-9][0-9]*:[1-9][0-9]*$")); "app must use a non-root UID/GID")
| check(.services.app.init == true and .services.app.read_only == true
        and .services.app.cap_drop == ["ALL"]
        and (.services.app.security_opt | index("no-new-privileges:true") != null)
        and (.services.app.tmpfs | index("/tmp") != null);
        "non-root local app hardening changed")
| check(.services.app.environment.SPRING_PROFILES_ACTIVE == "local"
    and .services.app.environment.HOME == "/workspace/.runtime/home"
    and .services.app.environment.GRADLE_USER_HOME == "/workspace/.runtime/gradle"
    and .services.app.environment.NPM_CONFIG_CACHE == "/workspace/.runtime/npm"
    and .services.app.environment.PERSEFONIA_MEDIA_STORAGE_ROOT == "/workspace/.runtime/media"
    and .services.app.environment.PERSEFONIA_MEDIA_STORAGE_REQUIRED == "true"
    and .services.app.environment.MANAGEMENT_ENDPOINT_HEALTH_GROUP_READINESS_INCLUDE == "readinessState,db,mediaStorage,databaseMigrations"
    and .services.app.environment.PERSEFONIA_MANAGEMENT_ADDRESS == "0.0.0.0"
    and .services.app.environment.PERSEFONIA_MANAGEMENT_PORT == "9001"
    and (.services.app | has("env_file") | not);
    "local app profile, cache, media, management, or explicit environment contract changed")
| check(.services.app.environment.PERSEFONIA_DATASOURCE_URL ==
      ("jdbc:postgresql://postgres:5432/" + .services.postgres.environment.POSTGRES_DB)
    and .services.app.environment.PERSEFONIA_DATASOURCE_USERNAME == .services.postgres.environment.POSTGRES_USER
    and .services.app.environment.PERSEFONIA_REDIS_HOST == "redis"
    and .services.app.environment.PERSEFONIA_REDIS_PORT == "6379"
    and .services.app.environment.SPRING_DATA_REDIS_USERNAME == .services.redis.environment.PERSEFONIA_REDIS_USERNAME
    and .services.app.environment.SPRING_DATA_REDIS_USERNAME != "default"
    and (.services.app.environment.PERSEFONIA_PUBLIC_BASE_URL | length) > 0
    and .services.app.environment.PERSEFONIA_CONTACT_RATE_LIMIT_REDIS_KEY_PREFIX == .services.redis.environment.PERSEFONIA_REDIS_KEY_PREFIX;
    "local application environment must match dependencies and required inputs")
| check(all(.services[]; no_sensitive_values)
    and (.services.postgres.environment | keys) == ["POSTGRES_DB", "POSTGRES_PASSWORD_FILE", "POSTGRES_USER"]
    and .services.postgres.environment.POSTGRES_PASSWORD_FILE == "/run/secrets/postgres_password"
    and (.services.redis.environment | keys) == ["PERSEFONIA_REDIS_KEY_PREFIX", "PERSEFONIA_REDIS_USERNAME"];
    "sensitive values must not appear in service environments")
| check((.secrets | keys) == ["contact_rate_limit_secret", "postgres_password", "redis_password"]
    and (grant("app"; "postgres_password"; "spring.datasource.password"))
    and (grant("app"; "redis_password"; "spring.data.redis.password"))
    and (grant("app"; "contact_rate_limit_secret"; "persefonia.contact.rate-limit.secret"))
    and (.services.app.secrets | length) == 3
    and (grant("postgres"; "postgres_password"; "postgres_password"))
    and (.services.postgres.secrets | length) == 1
    and (grant("redis"; "redis_password"; "redis_password"))
    and (.services.redis.secrets | length) == 1
    and .secrets.postgres_password.file == ($workspace + "/secrets/postgres_password.examples")
    and .secrets.redis_password.file == ($workspace + "/secrets/redis_password.examples")
    and .secrets.contact_rate_limit_secret.file == ($workspace + "/secrets/contact_rate_limit_secret.examples");
    "baseline credentials must use only tracked local Compose secret examples")
| check((.services.app.depends_on | keys) == ["postgres", "redis"]
    and all(.services.app.depends_on[]; .condition == "service_healthy")
    and .services.app.healthcheck.test == ["CMD", "curl", "--fail", "--silent", "--show-error",
      "--output", "/dev/null", "http://127.0.0.1:9001/actuator/health/readiness"];
    "app must wait for healthy dependencies and expose readiness")
| check((.services.app.ports | length) == 2 and loopback(8080) and loopback(9001)
    and all(.services.app.ports[]; .host_ip == "127.0.0.1")
    and (.services.postgres.ports | length) == 1 and service_loopback("postgres"; 5432)
    and (.services.redis.ports | length) == 1 and service_loopback("redis"; 6379);
    "local ports must be published only on loopback")
| check((.services.postgres.volumes | length) == 1
    and .services.postgres.volumes[0].type == "volume"
    and .services.postgres.volumes[0].source == "postgres-data"
    and .services.postgres.volumes[0].target == "/var/lib/postgresql/data"
    and (.volumes | keys) == ["postgres-data"]
    and (.services.postgres.healthcheck.test[1] | contains("pg_isready"));
    "PostgreSQL must have health and one durable data volume")
| check(.services.redis.user == "999:1000" and .services.redis.read_only == true
    and .services.redis.cap_drop == ["ALL"]
    and (.services.redis.security_opt | index("no-new-privileges:true") != null)
    and (.services.redis.tmpfs | index("/tmp") != null)
    and (.services.redis.volumes | length) == 1
    and .services.redis.volumes[0].type == "bind"
    and .services.redis.volumes[0].source == ($workspace + "/scripts/local-redis-entrypoint.sh")
    and .services.redis.volumes[0].target == "/usr/local/bin/persefonia-local-redis-entrypoint.sh"
    and .services.redis.volumes[0].read_only == true
    and .services.redis.entrypoint == ["sh", "/usr/local/bin/persefonia-local-redis-entrypoint.sh"]
    and .services.redis.command == null
    and .services.redis.healthcheck.test == ["CMD", "redis-cli", "ping"];
    "Redis must use a non-root ACL bootstrap, credential-free PING health, and no durable state")
| check((.networks | keys) == ["default"] and (.networks.default.external // false) == false
    and (.services.app.labels // {}) == {};
    "local Compose must not own production networks or routing")
| true
