# Security Policy

## Supported Versions

| Version | Supported |
|---------|-----------|
| Latest release | ✅ |
| Previous minor | ✅ security fixes only |
| Older | ❌ |

## Reporting a Vulnerability

**Do not open a public GitHub issue for security vulnerabilities.**

Please report security issues by emailing the maintainer directly or using [GitHub's private vulnerability reporting](https://github.com/ayagmar/quarkus-forge/security/advisories/new).

Include in your report:
- A description of the vulnerability
- Steps to reproduce
- Potential impact
- Any suggested fixes or mitigations

You can expect an acknowledgement within **72 hours** and a resolution timeline within **14 days** for confirmed vulnerabilities.

## Security Considerations

Quarkus Forge makes outbound HTTPS requests to `code.quarkus.io` to fetch catalog data and download project archives. If you operate in a restricted network environment, refer to the [offline caching](docs/modules/ROOT/pages/reference/forge-files-and-state.adoc) documentation.

Archive extraction uses hardened ZIP handling (Zip-Bomb and Zip-Slip protection) via `SafeZipExtractor`. However, always review downloaded archives in security-sensitive environments.
