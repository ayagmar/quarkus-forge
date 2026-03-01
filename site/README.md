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

## Link check

```bash
npm run docs:linkcheck --prefix site
```
