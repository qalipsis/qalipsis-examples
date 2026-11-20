# Contributing to the QALIPSIS examples

Thank you for considering a contribution to the QALIPSIS examples. Each directory of this repository is a
self-contained scenario project showing how to test a technology or an architecture with QALIPSIS.

---

## Getting started

```bash
./gradlew build                  # all the examples
./gradlew :<example>:build       # one example, named as in settings.gradle.kts
```

An example should run with the services started by its own `docker-compose.yml` or by the root one, without any
account, token or manual edit.

---

## Pull requests

- One example, or one change to an example, per pull request.
- Add a `README.md` to a new example: what it demonstrates, how to start the required services, how to run it.
- Keep the examples small and focused on the QALIPSIS concepts they illustrate.

---

## Licensing

This repository is distributed under the [Apache License 2.0](./LICENSE). As stated in its section 5, every
contribution you submit is licensed under the same terms. The project does not accept contributions under any
other terms.

The Apache License covers the content of this repository only. Running a scenario relies on the QALIPSIS
runtime — `qalipsis-head`, `qalipsis-factory`, `qalipsis-runtime`, `qalipsis-shared`, `qalipsis-cluster` and
`qalipsis-webapp` — which is dual-licensed under the GNU AGPL 3.0 **or** a
commercial license, at the licensee's choice: see
[LICENSE-COMMERCIAL.md](https://github.com/qalipsis/qalipsis-oss/blob/main/LICENSE-COMMERCIAL.md) in
`qalipsis-oss`. The QALIPSIS API modules this repository compiles against are under the Apache License 2.0.

---

## Contributor License Agreement

Before your first pull request can be merged, you must sign
the [QALIPSIS Individual Contributor License Agreement](https://github.com/qalipsis/qalipsis-oss/blob/main/cla/individual-cla.md).
The CLA
bot posts the signing link as a comment on the pull request; signing takes a minute and is remembered for all your
later contributions to the QALIPSIS repositories. Contributing on behalf of an employer requires
the [Corporate Contributor License Agreement](https://github.com/qalipsis/qalipsis-oss/blob/main/cla/corporate-cla.md),
signed by someone entitled to bind the company.

The CLA does **not** transfer your copyright. You keep it, and grant AERIS IT Solutions GmbH a non-exclusive right to
distribute your contribution — under the Apache License 2.0, and in the commercial and SaaS editions of QALIPSIS.

---

## What you may contribute

- **Your own work only.** Submit only content you wrote, or have the right to submit. If your employer holds rights
  on your work, the corporate CLA must be signed first. You remain responsible for everything you submit, including
  content produced with code generators or AI assistants: review it as if you had written it yourself, and make sure
  it does not reproduce third-party code.
- **No code from the QALIPSIS runtime modules.** Do not copy or adapt code from `qalipsis-head`, `qalipsis-factory`,
  `qalipsis-runtime`, `qalipsis-shared`, `qalipsis-cluster` or `qalipsis-webapp`: these modules
  are dual-licensed (AGPL 3.0 or commercial), and their code cannot enter an Apache-licensed repository. Depend on
  the API modules instead; if something you need is missing there, open an issue on
  [`qalipsis-oss`](https://github.com/qalipsis/qalipsis-oss/issues).
- **Third-party code** only under a license compatible with the Apache License 2.0 (Apache, MIT, BSD, …), with its
  original copyright notice kept, and mentioned explicitly in the pull request description. Never copy code
  licensed under the GPL, LGPL or AGPL, nor code published without a license.
- **Dependencies** must not bring a strong copyleft license — GPL without Classpath Exception, AGPL — into the
  dependency graph.
- **No secrets nor personal data**: no credentials, tokens, private hostnames or real user data, even in tests or
  configuration samples. Use placeholders.
- **Trademarks**: as stated in section 6 of the Apache License, the QALIPSIS name and logo are not licensed. Use them
  only to describe the origin of the code, not to name or promote a derived product.

---

## License headers

Every new source file starts with the Apache License header: copy it from a neighbouring file and set the current
year in the copyright line. Do not alter the header of an existing file, except to correct it.
