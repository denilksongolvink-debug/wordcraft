# Known Issues — WordCraft Android Shell

Bugs e limites encontrados nos testes do shell Android. Um por linha, com data e status.

| Date | Issue | Status | Notes |
|---|---|---|---|
| 2026-10-19 | Casca criada | resolvido | Capacitor 6, WebView, manifest DeX-ready |
| 2026-10-19 | CI falhava em `cp: cannot stat 'apps/wordcraft-web/dist/*'` | resolvido | `Trunk.toml` escreve em `../../dist/web` (raiz do repo), não em `apps/wordcraft-web/dist` |
| 2026-10-19 | CI falhava em `npm ci` | resolvido | Sem `package-lock.json` commitado; trocado por `npm install` |
| 2026-10-19 | CI falhava no resource linking do manifest | resolvido | Template usava `@style/Theme.WordCraft`, que não existe; o `cap add` gera `@style/AppTheme` |
| 2026-10-19 | `npx cap add android` falhava com "Could not find installation of TypeScript" | resolvido | Capacitor exige TypeScript para ler `capacitor.config.ts` |
| 2026-10-19 | `gh workflow run` retornava 404 em branch não-default | resolvido | `workflow_dispatch` só é registrado na branch default; adicionado `branches: [feat/android-webview]` |
| 2026-10-19 | **Abrir `.docx` do armazenamento não funciona** | **aberto** | A WebView não tem File System Access API; o `BridgeWebChromeClient` padrão não implementa `onShowFileChooser`. Previsto na Fase 6 (§9.1 do PLANO-DEX.md) |
| 2026-10-19 | **Salvar / Salvar como não funciona** | **aberto** | A WebView ignora o clique em `<a download>`; o WASM chega a criar o blob, mas nada é gravado. Previsto na Fase 6 (§9.2) |
| 2026-10-19 | **Exportar PDF e outras extensões não funciona** | **aberto** | Mesma causa do salvar — a ponte de gravação não existe |
| 2026-10-19 | `versionName` sai como `1.0` em vez de `0.8.0` | corrigido, não publicado | Adicionado `version: "0.8.0"` ao `capacitor.config.ts`; falta publicar |
| 2026-10-19 | Commits duplicados no branch | aberto | Três commits com subjects repetidos; consolidar com `reset --soft` + `push --force-with-lease` |
| — | **Redimensionar no DeX não foi testado** | **não testado** | O teste do Gate 2 foi no S22, não no DeX. Atributos confirmados no manifest binário do APK, mas falta validar com o documento aberto |
