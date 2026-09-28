# Security policy

Hermes TTY holds a key that can run commands on the user's machine through Hermes Agent, so
security reports are very welcome.

## Reporting a vulnerability

Please **don't open a public issue.** Report it privately through GitHub:
[Security → Report a vulnerability](https://github.com/planetwally/hermes-tty/security/advisories/new).

Include what you found, how to reproduce it, and the app version (settings → about). You'll get an
answer within a week; fixes ship as a new release with credit to you unless you'd rather stay anonymous.

## Scope

In scope: this app and `pair.py` — e.g. leaking or mishandling the API key, pairing links that
connect without confirmation, or bypassing the app lock.

Out of scope: the Hermes Agent gateway itself (report those to
[NousResearch/hermes-agent](https://github.com/NousResearch/hermes-agent)), and setups that expose
the gateway to the internet against the README's advice.
