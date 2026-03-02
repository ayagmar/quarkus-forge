# Docs Site (Landing + Antora)

## Local build

```bash
npm ci --prefix site
npm run docs:build --prefix site
```

Build output:
- final publishable site: `site/build/pages`
- Antora docs bundle (before landing overlay): `site/build/site`

## Local preview

```bash
npm run docs:serve --prefix site
```

Open `http://localhost:8080`.

## Landing page CSS (dev only)

`site/output.css` is a generated Tailwind artifact and is not tracked in git.
To iterate on the landing page (`site/index.html`) without running the full
Antora build, generate and watch it locally:

```bash
npm run docs:dev:css --prefix site
```

Then open `site/index.html` directly in a browser. The file will be ignored
by git automatically (`.gitignore`).

## Link check

```bash
npm run docs:linkcheck --prefix site
```
