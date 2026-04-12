# Custom CA Certificates

Place any custom CA certificate files (`.crt`) in this directory. They will be
imported into the JVM truststore during the Docker build.

This is needed when running behind a corporate proxy (e.g. Zscaler, Netskope)
that performs SSL inspection and re-signs HTTPS traffic with its own CA.

## How to obtain your corporate CA cert

1. Run: `openssl s_client -connect api.snaptrade.com:443 -showcerts </dev/null 2>/dev/null | sed -ne '/-BEGIN CERTIFICATE-/,/-END CERTIFICATE-/p'`
2. Save the last certificate(s) in the chain as `.crt` files in this directory.
3. Rebuild: `docker compose build backend`

The `.crt` files are gitignored — do not commit them.
