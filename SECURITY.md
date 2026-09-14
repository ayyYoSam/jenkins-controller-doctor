# Security Policy

## Supported Versions

Security fixes are applied to actively maintained releases.

| Version | Supported |
| ------- | --------- |
| Latest release | Yes |
| Older releases | No |

## Reporting a Vulnerability

Please do not report security vulnerabilities through public GitHub issues.

Use GitHub's private vulnerability reporting feature when available.

Security reports should include:

- A clear description of the vulnerability.
- Steps required to reproduce it.
- The affected version.
- The potential impact.
- Any known mitigation or workaround.

Please avoid including credentials, API tokens, cookies, or other sensitive Jenkins data in reports.

## Security Design

Jenkins Controller Doctor is designed as a diagnostic tool.

The project follows these principles:

- Read-only interaction with Jenkins.
- Credentials must never be written to logs.
- API tokens must not be included in generated reports.
- HTTPS should be preferred for remote Jenkins connections.
- Network operations use explicit timeouts.
- Remote responses are subject to size limits.
- Diagnostic failures should not expose sensitive connection details.