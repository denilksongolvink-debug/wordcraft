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
| 2026-10-19 | **Abrir `.docx` do armazenamento não funciona** | corrigido, falta testar no aparelho | A ponte de abrir é o `onShowFileChooser` do `MainActivity`, que abre o seletor do sistema (SAF) e devolve o `Uri` ao WebView (§9.1 do PLANO-DEX.md) |
| 2026-10-19 | **Salvar / Salvar como não funciona** | corrigido, falta testar no aparelho | A WebView ignora o clique em `<a download>`; o script injetado em `onPageFinished` intercepta o clique, converte o blob em base64 e chama o plugin `FileBridge.save` (§9.2) |
| 2026-10-19 | **Exportar PDF e outras extensões não funciona** | corrigido, falta testar no aparelho | Mesma ponte do salvar — o `FileBridge` grava em `Downloads/WordCraft` (MediaStore no API 29+) |
| 2026-10-21 | **O `MainActivity` da ponte não compilava** | corrigido | `ActivityResultContracts.OpenDocument` devolve **um** `Uri`, não uma lista; o código antigo tratava o resultado como lista e o build do APK quebrava. Agora o `Uri` único é entregue ao `pendingFileCallback` (e `null` quando o usuário cancela) |
| 2026-10-21 | **O `WebViewClient`/`WebChromeClient` do Capacitor era substituído** | corrigido | O `BridgeWebViewClient` serve o app (`shouldInterceptRequest`); trocá-lo por um `WebViewClient` comum deixa a WebView sem página. Agora os dois clientes são **extendidos**, não substituídos |
| 2026-10-21 | **Salvar / exportar ainda não testado no aparelho** | **aberto** | A ponte (`FileBridge`) está implementada e com testes de unidade, mas falta confirmar no S22/DeX que o arquivo chega em `Downloads/WordCraft` |
| 2026-10-21 | `versionName` sai como `1.0` em vez de `0.8.0` | corrigido, não publicado | Adicionado `version: "0.8.0"` ao `capacitor.config.ts`; falta publicar |
| 2026-10-19 | Commits duplicados no branch | aberto | Três commits com subjects repetidos; consolidar com `reset --soft` + `push --force-with-lease` |
| — | **Redimensionar no DeX não foi testado** | **não testado** | O teste do Gate 2 foi no S22, não no DeX. Atributos confirmados no manifest binário do APK, mas falta validar com o documento aberto |
