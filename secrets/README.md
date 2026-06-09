# Local Docker Secrets

The `secrets/` directory is for local, untracked secret files. Real secret
files must not be committed. `.env` may point to secret file paths only;
secret values must be supplied to services through Docker secrets.

Create these local files before starting the Compose services:

- `secrets/postgres_password.txt`
- `secrets/redis.conf`

For example, the untracked Redis configuration can contain:

```text
requirepass replace_with_local_secret
```

This is a fake example only. Replace it with a local secret and do not commit
the real file. Tracked fake examples are available under `secrets/examples/`.
