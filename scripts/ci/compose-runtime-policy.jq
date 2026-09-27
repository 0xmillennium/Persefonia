def check($condition; $message):
  if $condition then . else error($message) end;

def secret_targets($service): [$service.secrets[]?.target // .source] | sort;

check((.services | keys) == ["app"]; "runtime must contain only app")
| check((.services.app | has("build") | not); "runtime must not build an image")
| check(.services.app.image == $image; "application image changed")
| check(.services.app.user == "10001:10001" and .services.app.read_only == true
    and .services.app.cap_drop == ["ALL"]
    and (.services.app.security_opt | index("no-new-privileges:true") != null)
    and (.services.app.tmpfs | index("/tmp") != null);
    "application hardening changed")
| check(.services.app.environment.SPRING_PROFILES_ACTIVE == "docker"
    and .services.app.environment.PERSEFONIA_MANAGEMENT_PORT == $management_port
    and .services.app.healthcheck.test == ["CMD", "curl", "--fail", "--silent", "--show-error",
      "--output", "/dev/null", ("http://127.0.0.1:" + $management_port + "/actuator/health/readiness")];
    "application profile or readiness healthcheck changed")
| check((.services.app | has("depends_on") | not)
    and (.services.app.labels // {}) == {};
    "application must not own dependency startup or routing")
| check(.services.app.environment.PERSEFONIA_POSTGRES_HOST == $postgres_host
    and .services.app.environment.PERSEFONIA_POSTGRES_PORT == $postgres_port
    and .services.app.environment.PERSEFONIA_REDIS_HOST == $redis_host
    and .services.app.environment.PERSEFONIA_REDIS_PORT == $redis_port
    and .services.app.environment.POSTGRES_DB == "persefonia"
    and .services.app.environment.POSTGRES_USER == "persefonia"
    and .services.app.environment.SPRING_DATA_REDIS_USERNAME == $redis_username
    and .services.app.environment.PERSEFONIA_CONTACT_RATE_LIMIT_REDIS_KEY_PREFIX == $redis_key_prefix;
    "application dependency configuration changed")
| check(secret_targets(.services.app) == ["persefonia.cache-purge.cloudflare.api-token",
    "persefonia.contact.rate-limit.secret", "spring.data.redis.password",
    "spring.datasource.password", "spring.security.oauth2.client.registration.authelia.client-secret"]
    and (.secrets | keys) == ["cloudflare_api_token", "contact_rate_limit_secret",
      "oidc_client_secret", "postgres_password", "redis_password"]
    and (all(.secrets | to_entries[]; .value.file == ($secret_directory + "/" + .key)));
    "application secret contract changed")
# Some Compose versions omit an explicit false from rendered bind options.
# Descriptor tests retain the source-level create_host_path contract.
| check((.services.app.volumes | length) == 1 and any(.services.app.volumes[]?;
    .source == $media_source and .target == "/var/lib/persefonia/media"
    and .type == "bind" and (.bind.create_host_path // false) == false);
    "Media bind mount changed")
| check((.services.app.ports | length) == 1
    and .services.app.ports[0].host_ip == "127.0.0.1"
    and .services.app.ports[0].target == 8080
    and (.services.app.ports[0].published | tostring) == $app_port;
    "only the loopback application port may be published")
| check((.volumes // {}) == {} and ((.networks // {}) | keys) == ["default"]
    and (.networks.default.external // false) == false;
    "runtime must not own dependency volumes or custom networks")
| check([.services.app.environment | keys[] | select(test(
    "OIDC|SMTP|CLOUDFLARE|ADMIN.*GROUP|PUBLIC_HOST|PUBLIC_BASE_URL|TRUSTED_PROXY|TRAEFIK|CONTACT_MAIL"))]
    | length == 0; "application Compose contract must not introduce production integrations")
| true
