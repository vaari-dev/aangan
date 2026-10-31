# Security

## Reporting

Report privately, not in a public issue: [Security → Report a vulnerability](https://github.com/vaari-dev/aangan/security/advisories/new).
Include how to reproduce it, the TV and the Aangan version. Details stay private until a fix is released.

Only the latest release gets fixes.

## Scope

Aangan has no server or account. What matters:

- **The phone setup page.** An HTTP server on the home network (port 8737, or any free port), only while Settings
  is open, under a secret path that is new each time and shown only on the TV. It assumes a trusted home network.
  In scope: reaching it without the secret, keeping it alive after Settings closes, misbehaviour on bad input.
- **The Home-button service.** An optional accessibility service that only opens Aangan on Home or when the TV's
  launcher appears, and can't read window content. In scope: anything that lets it see or do more.
- **Network requests.** Only HTTPS to Open-Meteo (forecast, air quality, city search). In scope: making it send
  anything else, or anywhere else.

More in the [security model](https://vaari-dev.github.io/aangan/developers.html#security).

## Supply chain

- GitHub Actions are pinned to full commits; Dependabot proposes updates a week after release.
- zizmor audits the workflows; jobs get least permissions and checkouts don't keep the token.
- The signing key lives only in the approval-gated `release` environment; a release fails unless signed with the
  certificate below.
- Gradle verifies every download against [`gradle/verification-metadata.xml`](gradle/verification-metadata.xml).

## Checking a download

Every release is signed with the same certificate, SHA-256:

    7f905b6d3cfbcc92cd2da7203c5b6cf365b65d67cb3d162a4c54d4043caa1484

    apksigner verify --print-certs aangan.apk

`Signer #1 certificate SHA-256 digest` must match. If it doesn't, don't install it, and tell us where you got it.
