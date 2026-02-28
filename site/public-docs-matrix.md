# Public Docs Inclusion Matrix

| Path / Area | Publish | Reason |
| --- | --- | --- |
| `site/index.html` | Yes | Public marketing landing page |
| `docs/modules/ROOT/pages/**` | Yes | End-user docs rendered by Antora |
| `docs/antora.yml` + `site/antora-playbook.yml` | Yes (build-time) | Public docs configuration |
| `docs/ai/**` | No | Internal planning/tracking artifacts |
| `docs/operations/**` | No | Internal operational runbooks |
| `docs/release-notes/**` | No | Internal release tracking notes |
| Root `docs/*.md` legacy architecture/process notes | No | Not part of Antora source tree |

## Publication rule

Only Antora module pages under `docs/modules/ROOT/pages/**` are published in the generated docs output.
