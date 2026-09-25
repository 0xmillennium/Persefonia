def check($condition; $message):
  if $condition then . else error($message) end;

def network_names($service): ($service.networks | keys | sort);
def secret_targets($service): [$service.secrets[]?.target // .source] | sort;
def has_mount($service; $source; $target):
  any($service.volumes[]?; .source == $source and .target == $target);
def port_is($service; $target):
  ($service.ports | length) == 1 and
  $service.ports[0].host_ip == "127.0.0.1" and
  $service.ports[0].target == $target;
def core($doc):
  $doc
  | check((.services | has("app") and has("postgres") and has("redis")); "required services are missing")
  | check((.services | [.[] | has("build")] | all(.[]; . == false)); "runtime must not build images")
  | check(.services.app.user == "10001:10001" and .services.app.read_only == true
      and .services.app.cap_drop == ["ALL"]
      and (.services.app.security_opt | index("no-new-privileges:true") != null)
      and (.services.app.tmpfs | index("/tmp") != null)
      and (.services.app.healthcheck.test | tostring | contains("/actuator/health/readiness"));
      "application hardening or healthcheck changed")
  | check(secret_targets(.services.app) | index("spring.datasource.password") != null
      and index("spring.data.redis.password") != null
      and index("persefonia.contact.rate-limit.secret") != null;
      "application secret targets changed")
  | check(.services.postgres.read_only == true and .services.redis.read_only == true
      and .services.postgres.cap_drop == ["ALL"] and .services.redis.cap_drop == ["ALL"]
      and (.services.postgres.security_opt | index("no-new-privileges:true") != null)
      and (.services.redis.security_opt | index("no-new-privileges:true") != null)
      and (.services.postgres.image | test("^postgres:[^@]+@sha256:[a-f0-9]{64}$"))
      and (.services.redis.image | test("^redis:[^@]+@sha256:[a-f0-9]{64}$"))
      and has_mount(.services.postgres; "postgres-data"; "/var/lib/postgresql/data")
      and any(.services.redis.volumes[]?;
        .source == $redis_helper and .target == "/usr/local/bin/redis-start.sh" and .read_only == true)
      and (.services.redis.tmpfs | index("/data") != null)
      and (.services.postgres.tmpfs | index("/var/run/postgresql") != null)
      and (.services.postgres.healthcheck.test | length) > 0
      and (.services.redis.healthcheck.test | length) > 0;
      "PostgreSQL or Redis hardening changed")
  | check(.networks.datanet.internal == true
      and network_names(.services.postgres) == ["datanet"]
      and network_names(.services.redis) == ["datanet"];
      "data network isolation changed")
  # Some Compose versions omit an explicit false from rendered bind options.
  # Descriptor tests retain the source-level create_host_path contract.
  | check(any(.services.app.volumes[]?;
      .source == $media_source and .target == "/var/lib/persefonia/media"
      and .type == "bind" and (.bind.create_host_path // false) == false);
      "Media bind mount changed");

def local_policy($doc):
  $doc | core(.)
  | check(.services.app.image == $image and .services.app.environment.SPRING_PROFILES_ACTIVE == "docker";
      "local image or application profile changed")
  | check(network_names(.services.app) == ["datanet", "default"]
      and (.networks.default.internal // false) == false
      and (.networks | has("backnet") | not)
      and (.networks | has("frontnet") | not);
      "local network topology changed")
  | check(port_is(.services.app; 8080) and port_is(.services.postgres; 5432)
      and port_is(.services.redis; 6379); "local ports must bind only to loopback")
  | check((.services.app.labels // {}) == {}
      and (.services.app.environment | has("PERSEFONIA_OIDC_ISSUER_URI") | not)
      and (.services.app.environment | has("PERSEFONIA_SMTP_HOST") | not)
      and (.services.app.environment | has("PERSEFONIA_CLOUDFLARE_ZONE_ID") | not);
      "local runtime depends on production integration");

def production_policy($doc):
  $doc | core(.)
  | check(.name == "persefonia" and .services.app.image == $image;
      "production project or immutable image identity changed")
  | check([.services[] | (.ports // []) | length] | all(.[]; . == 0);
      "production service publishes a host port")
  | check(network_names(.services.app) == ["backnet", "datanet", "frontnet"]
      and .networks.backnet.external == true and .networks.backnet.name == "backnet"
      and .networks.frontnet.external == true and .networks.frontnet.name == "frontnet"
      and .services.app.networks.frontnet.gw_priority == 1
      and (.services.app.networks.backnet.aliases | index("persefonia-app") != null);
      "production network topology or outbound gateway changed")
  | check(.services.app.environment.SPRING_PROFILES_ACTIVE == "docker,prod"
      and .services.app.environment.PERSEFONIA_MANAGEMENT_ADDRESS == "0.0.0.0"
      and .services.app.environment.PERSEFONIA_MANAGEMENT_PORT == "9001"
      and .services.app.environment.PERSEFONIA_SMTP_HOST == "postfix-internal"
      and .services.app.environment.PERSEFONIA_SMTP_PORT == "25"
      and .services.app.environment.PERSEFONIA_OIDC_CLIENT_ID == "persefonia"
      and .services.app.environment.PERSEFONIA_PUBLIC_BASE_URL == ("https://" + $host)
      and .services.app.environment.PERSEFONIA_ADMIN_ALLOWLISTED_SUBJECTS == $subjects
      and .services.app.environment.PERSEFONIA_ADMIN_ALLOWLISTED_EMAILS == $emails;
      "production application environment changed")
  | check(secret_targets(.services.app) | index("spring.security.oauth2.client.registration.authelia.client-secret") != null
      and index("persefonia.cache-purge.cloudflare.api-token") != null;
      "production application secrets changed")
  | check(.secrets.postgres_password.file == "/etc/persefonia/secrets/postgres_password"
      and .secrets.redis_password.file == "/etc/persefonia/secrets/redis_password"
      and .secrets.contact_rate_limit_secret.file == "/etc/persefonia/secrets/contact_rate_limit_secret"
      and .secrets.oidc_client_secret.file == "/etc/persefonia/secrets/oidc_client_secret"
      and .secrets.cloudflare_api_token.file == "/etc/persefonia/secrets/cloudflare_api_token";
      "production secret source paths changed")
  | .services.app.labels as $labels
  | check($labels["traefik.enable"] == "true"
      and $labels["traefik.docker.network"] == "backnet"
      and $labels["traefik.http.routers.persefonia.rule"] == ("Host(`" + $host + "`)")
      and ($labels["traefik.http.routers.persefonia-admin.rule"]
        | contains("Host(`" + $host + "`)") and contains("Path(`/admin`)")
          and contains("PathPrefix(`/admin/`)"))
      and (($labels["traefik.http.routers.persefonia.priority"] | tonumber)
        < ($labels["traefik.http.routers.persefonia-admin.priority"] | tonumber));
      "Traefik public/admin router rules or priorities changed")
  | check(["persefonia", "persefonia-admin"] | all(.[]; . as $router |
      $labels["traefik.http.routers.\($router).entrypoints"] == "websecure"
      and $labels["traefik.http.routers.\($router).tls"] == "true"
      and $labels["traefik.http.routers.\($router).tls.certresolver"] == "letsencrypt"
      and $labels["traefik.http.routers.\($router).service"] == "persefonia");
      "Traefik TLS or service selection changed")
  | check($labels["traefik.http.routers.persefonia.middlewares"] == "profile-persefonia-public@file"
      and $labels["traefik.http.routers.persefonia-admin.middlewares"] == "profile-persefonia-admin@file"
      and $labels["traefik.http.services.persefonia.loadbalancer.server.port"] == "8080"
      and ([$labels | keys[] | select(startswith("traefik.http.routers."))
        | split(".")[3]] | unique) == ["persefonia", "persefonia-admin"]
      and ([$labels | keys[] | select(startswith("traefik.http.middlewares."))] | length) == 0
      and ([$labels | keys[] | select(startswith("traefik.http.services.") and . != "traefik.http.services.persefonia.loadbalancer.server.port")] | length) == 0
      and ([$labels[] | select(test("forwardauth|mw-auth|profile-public-auth|profile-sensitive-auth|mw-headers"; "i"))] | length) == 0;
      "Traefik middleware profile or application port changed");

if $mode == "local" then local_policy(.)
elif $mode == "production" then production_policy(.)
else error("unknown Compose runtime policy mode") end
| true
