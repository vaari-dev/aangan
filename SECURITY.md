# Security

## Reporting a problem

Please report security problems privately, not in a public issue. On
[github.com/vaari-dev/aangan](https://github.com/vaari-dev/aangan), open the **Security** tab and choose
**Report a vulnerability** ([direct link](https://github.com/vaari-dev/aangan/security/advisories/new)).

Say what you found, how to reproduce it, and which TV and Aangan version you saw it on. We'll reply in the report,
and keep the details private until a fix is released.

## Supported versions

Only the latest release gets security fixes. Updates come from the same place you installed Aangan: Google Play,
or [GitHub Releases](https://github.com/vaari-dev/aangan/releases).

## What's worth looking at

Aangan has no server and no account, so its attack surface is small. These are the parts that matter:

- **The phone setup page.** While Settings is open on the TV, and only then, Aangan runs a small HTTP server on the
  home network, on port 8737 (or any free port). Every page is under a secret path, new each time Settings opens and
  shown only on the TV as a QR code. It uses plain HTTP and assumes a trusted home network. Ways to reach it without
  the secret, to keep it running after Settings closes, or to make it misbehave on bad input are all in scope.
- **The Home-button service.** An optional accessibility service whose only job is to open Aangan when Home is
  pressed or the TV's own launcher appears. It can't read window content. Anything that lets it see or do more than
  that is in scope.
- **Network requests.** Aangan's only outside requests go over HTTPS to Open-Meteo, for the forecast, air quality
  and city search. A way to make it send anything else, or send it somewhere else, is in scope.

The [developer guide](https://vaari-dev.github.io/aangan/developers.html#security) describes the security model in
more detail.

## Checking a download

Every release is signed with the same key. Its certificate's SHA-256 digest is:

    7f905b6d3cfbcc92cd2da7203c5b6cf365b65d67cb3d162a4c54d4043caa1484

To check an APK before you install it, run `apksigner` from the Android SDK's build tools:

    apksigner verify --print-certs aangan.apk

The line `Signer #1 certificate SHA-256 digest` should show the digest above. If it doesn't, don't install the APK,
and please tell us where you got it.
