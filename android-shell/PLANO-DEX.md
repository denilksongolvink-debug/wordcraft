# Plano: WordCraft no Samsung DeX (APK via WebView)

> **Repo base:** https://github.com/storytold/wordcraft (Rust + egui/wgpu, MIT OR Apache-2.0, alpha)
> **Fork local:** `~/git/forks/wordcraft` · **branch:** `feat/android-webview`
> **Estratégia:** build web (WASM) → casca Android (WebView) → APK gerado no GitHub Actions
> **Plano B:** port nativo (cdylib + cargo-ndk) só se a rota web falhar nos gates

> **Status geral:** Gate 1 passou. Fases 3 e 4 concluídas. Gate 2 parcial (ver §14).

---

## 1. Objetivo e critério de sucesso

**Objetivo:** ter um `.apk` instalável no Galaxy que abra o WordCraft em janela redimensionável no DeX.

**Pronto quando (MVP):**
- [x] APK instala e abre sem crash
- [x] A UI renderiza (ribbon, página, rulers) e responde a mouse e teclado físico
- [x] Dá pra digitar, formatar (Ctrl+B, estilos) e usar undo/redo
- [ ] Abre um `.docx` do armazenamento do aparelho
- [ ] Salva/exporta `.docx` e PDF de volta pro armazenamento
- [ ] Redimensionar a janela no DeX não reinicia o app nem perde o documento

**Fora do escopo do MVP:** publicar na Play Store, assinatura de release, modo offline via service worker, port nativo.

---

## 2. Visão geral das fases (com gates)

```
Fase 0  Fork + reconhecimento            ✔
   ↓
Fase 1  Validar build web no Chrome      ✔  GATE 1: passou
   ↓
Fase 2  Ajustes no web                   —  não precisou
   ↓
Fase 3  Casca Android (Capacitor)        ✔
   ↓
Fase 4  CI no GitHub Actions → APK       ✔  APK gerado e verificado
   ↓
Fase 5  Testes no DeX                    ✔  parcial: Gate 2 parcial
   ↓
Fase 6  Polimento / backlog
```

> **Atenção:** funcionar no Chrome **não garante** funcionar na WebView. Por isso o Gate 2 existe separado do Gate 1.

---

## 3. Fase 0: Fork e reconhecimento

### 3.1 Setup

```bash
git clone https://github.com/<seu-user>/wordcraft
cd wordcraft
git checkout -b feat/android-webview
```

Feito: clone do fork `denilksongolvink-debug/wordcraft`, branch `feat/android-webview`.

### 3.2 Lido antes de mexer

`README.md`, `AGENTS.md`, `CLAUDE.md`, `apps/wordcraft-web/`, `.github/workflows/`, `ROADMAP.md`, `docs/gaps.md`.

### 3.3 Respostas que o código deu (§3.3 original)

| Pergunta | Resposta encontrada | Consequência |
|---|---|---|
| Usa File System Access API (`showSaveFilePicker`)? | **SIM, com fallback.** `apps/wordcraft-web/src/web.rs:399` chama `showSaveFilePicker` e cai num `<a download>` com blob (`trigger_anchor_download`, linha 516) | O fallback existe, mas a WebView **ignora** o clique no `<a download>` → precisa de ponte |
| Save usa `<a download>` com blob? | **SIM** | Precisa interceptar o clique e gravar de verdade |
| Exige COOP/COEP? | **NÃO.** Nenhum `SharedArrayBuffer`/atomics no tree | Não precisa injetar headers |
| Só WebGPU, sem fallback WebGL2? | **NÃO.** `eframe` com `wgpu` já traz o fallback WebGL2 (`apps/wordcraft-web/Cargo.toml:32`) | Risco de GPU na WebView reduzido |

Caminhos de arquivo do web (a base da ponte):
- **Abrir** → `Services::open_async` (`crates/ui-egui/src/lib.rs:114`) → no browser o `rfd` cria um `<input type="file">` oculto → o arquivo cai em `Services::inbox`
- **Salvar/exportar** → `Services::download` (`crates/ui-egui/src/lib.rs:123`) → `blob:` + clique em `<a download>`

---

## 4. Fase 1: Validar o build web ✔

**GATE 1 — PASSOU.**

- Rota C: `trunk build --release` em `apps/wordcraft-web` → `dist/web/` (wasm 44 MB + js 165 KB)
- Servido com `python3 -m http.server 8771`; MIME `application/wasm` confirmado
- UI renderiza e é usável no browser → segue para Fase 3 (Fase 2 pulada)

### 4.1 Checklist de testes (Chrome)

| # | Teste | Esperado | Resultado |
|---|---|---|---|
| 1 | Página carrega, canvas aparece | Ribbon + documento renderizados | ✔ |
| 2 | Digitar texto com teclado físico | Caracteres aparecem sem lag | ✔ |
| 3 | Ctrl+B, Ctrl+Z, Ctrl+C/V | Funcionam; clipboard ok | ✔ |
| 4 | Mouse: clique, drag-select, scroll, botão direito | Respondem | ✔ |
| 5 | Zoom e redimensionar a janela | Re-layout sem travar | ✔ |
| 6 | Abrir `.docx` | Seletor abre e documento carrega | ✔ |
| 7 | Salvar `.docx` / exportar PDF | Arquivo chega ao armazenamento | ✔ |
| 8 | Acentos e `ç` em pt-BR, UI em Português (Brasil) | Corretos | ✔ |
| 9 | Dark mode | Alterna | ✔ |
| 10 | Documento grande (muitas páginas) | Desempenho aceitável | ✔ |

---

## 5. Fase 2: Ajustes no web — não necessário

Gate 1 passou nos testes 1–5 e os testes 6–7 funcionam no browser. Não houve mudança no código web.

---

## 6. Fase 3: Casca Android (Capacitor) ✔

### 6.1 Escolha

**Capacitor.** Setup por `npx cap add android`, plugins prontos, casca fora do workspace Rust (`android-shell/` não entra em `cargo xtask ci` nem nas checagens de camada).

### 6.2 Estrutura no fork

```
wordcraft/
├── android-shell/
│   ├── package.json          # @capacitor/cli, core, android, filesystem, share + typescript (dev)
│   ├── capacitor.config.ts   # appId com.wordcraft.app, appName WordCraft, version 0.8.0
│   ├── android-manifest.xml  # template DeX-ready (sobrescreve o gerado)
│   ├── android-main-activity.kt  # template do MainActivity (sobrescreve o gerado)
│   ├── www/                  # cópia do dist do web (gerada no CI; não commitada)
│   ├── README.md
│   └── KNOWN_ISSUES.md
├── .github/workflows/android-apk.yml
└── dist/web/                 # saída do trunk (fora do android-shell)
```

`android/` é gerado pelo `npx cap add android` e **nunca commitado**.

### 6.3 `capacitor.config.ts`

```ts
import type { CapacitorConfig } from "@capacitor/cli";

const config: CapacitorConfig = {
  appId: "com.wordcraft.app",
  appName: "WordCraft",
  version: "0.8.0",
  webDir: "www",
  server: { androidScheme: "https" },
  android: { allowMixedContent: false },
};

export default config;
```

### 6.4 Ajustes de DeX (no `android-shell/android-manifest.xml`)

```xml
android:resizeableActivity="true"
android:configChanges="orientation|screenSize|smallestScreenSize|screenLayout|keyboard|keyboardHidden|density|uiMode"
android:windowSoftInputMode="adjustResize"
android:launchMode="singleTask"
```

O `launchMode="singleTask"` foi adicionado além do plano original — é o que o Capacitor gera e evita atividade duplicada ao reabrir pelo launcher.

Sem isso a janela reinicia a cada resize no DeX e o documento aberto se perde.

### 6.5 Arquivos: o ponto mais delicado — **PENDENTE**

A WebView **não** trata como o Chrome:
- **Abrir:** `<input type="file">` só funciona se o `WebChromeClient` implementar `onShowFileChooser`. O `BridgeWebChromeClient` padrão do Capacitor não implementa.
- **Salvar:** download via `blob:` não dispara nada por padrão.

**Estado atual: NÃO IMPLEMENTADO.** Confirmado em teste no aparelho: salvar, salvar como e exportar (PDF etc.) não funcionam.

**Solução escolhida (implementar na Fase 6):**

1. **Abrir** — sobrescrever `onShowFileChooser` na `MainActivity`: abrir o seletor do sistema (`ActivityResultContracts.OpenDocument`) e devolver o resultado pelo `ValueCallback<Uri[]>`. Daí o arquivo flui pelo caminho JS que o `rfd` já usa e cai no `inbox` — **sem mexer no Rust**.
2. **Salvar/exportar** — injetar JS no `onPageFinished` que intercepta cliques em `a[download]`, lê o blob, converte pra base64 e chama um plugin `FileBridge` em Kotlin, que grava na pasta Downloads (via `MediaStore` no API 29+) ou no storage do app, e oferece `@capacitor/share`.

Recomendação do plano: começar pela opção 1 e só trocar se ficar frágil. **Confirmado como o caminho certo.**

### 6.6 Headers COOP/COEP

Não se aplica (§3.3: o build não usa threads).

### 6.7 Depuração

Pendente: habilitar `webContentsDebuggingEnabled` no build **debug** e injetar Eruda.

### 6.8 GATE 2 (WebView) — **PARCIAL**

Ver §14.

---

## 7. Fase 4: CI no GitHub Actions → APK ✔

`.github/workflows/android-apk.yml` — **verde**, APK gerado e verificado.

Fluxo real (diferente do rascunho original do plano):

```yaml
on:
  workflow_dispatch:
  push:
    branches: [feat/android-webview]   # workflow_dispatch sozinho não funciona em branch não-default
    tags: ["v*"]

steps:
  checkout
  dtolnay/rust-toolchain@stable (wasm32-unknown-unknown)
  Swatinem/rust-cache@v2
  taiki-e/install-action@v2 (trunk, wasm-bindgen-cli)
  Build web:            trunk build --release --public-url ./
  Copy web build:       cp -r dist/web/. android-shell/www/     # <-- dist fica na RAIZ do repo
  setup-node 22 / setup-java 21
  Install shell deps:   npm install                              # <-- não npm ci
  Add Capacitor:        npx cap add android                       # <-- não cap sync
  Copy DeX manifest:    cp android-shell/android-manifest.xml android/.../AndroidManifest.xml
  Copy MainActivity:    cp android-shell/android-main-activity.kt android/.../MainActivity.kt
  Sync Capacitor:       npx cap sync android
  Build APK:            ./gradlew assembleDebug
  upload-artifact:      wordcraft-debug-apk
```

### Correções que foram precisas (5)

| # | Problema | Causa | Correção |
|---|---|---|---|
| 1 | `cp: cannot stat 'apps/wordcraft-web/dist/*'` | `apps/wordcraft-web/Trunk.toml` define `dist = "../../dist/web"` → a saída cai na **raiz** do repo | Copiar de `dist/web/.` |
| 2 | `npm ci` falha | Não há `package-lock.json` commitado | Usar `npm install` |
| 3 | Falha de resource linking | O template usava `@style/Theme.WordCraft`, inexistente; o `cap add` gera `@style/AppTheme` | Referenciar os nomes gerados pelo Capacitor |
| 4 | `Could not find installation of TypeScript` | O Capacitor exige TypeScript para ler `capacitor.config.ts` | Adicionar `typescript` em `devDependencies` |
| 5 | `HTTP 404: workflow not found on the default branch` | `workflow_dispatch` só é registrado na branch default | Adicionar `branches: [feat/android-webview]` ao trigger `push` |

### Verificação do APK (não assumir — conferir)

- `gh run download <run-id> --repo <dono>/<repo>` e inspecionar o zip
- Confirmar: `AndroidManifest.xml`, `classes.dex`, `assets/public/index.html`, o `.wasm`
- Confirmar no dex: `BridgeActivity`, `FilesystemPlugin`, `SharePlugin` — **as classes podem estar em `classes2.dex`/`classes3.dex`; buscar em todos** ou dá falso negativo
- **O `AndroidManifest.xml` compilado é binário**: atributos viram bitmask/enum. Buscar `adjustResize`/`singleTask`/`keyboardHidden` como string não encontra nada. Decodificar o AXML e ler os valores: `configChanges` como bitmask, `windowSoftInputMode = 0x10` para `adjustResize`, `launchMode = 2` para `singleTask`

### Resultado verificado

```
package             = com.wordcraft.app
minSdk / targetSdk  = 22 / 34
activity            = com.wordcraft.app.MainActivity
resizeableActivity  = true
configChanges       = 0x1fb0 → keyboard|keyboardHidden|orientation|
                                 screenLayout|uiMode|screenSize|
                                 smallestScreenSize|density
windowSoftInputMode = 0x10    → adjustResize
launchMode          = 2       → singleTask
permissions         = INTERNET, READ/WRITE_EXTERNAL_STORAGE
assets/public/index.html + wordcraft-web-*_bg.wasm  → embutidos (funciona offline)
```

### Assinatura

- **Debug** (MVP): o APK de debug instala por sideload ✔
- **Release** (depois): keystore em *GitHub Secrets* (`KEYSTORE_BASE64`, `KEY_ALIAS`, `KEY_PASSWORD`) e `assembleRelease` + `apksigner`
- Nunca commitar keystore no repo

---

## 8. Fase 5: Instalação e testes — **parcial**

1. [x] Baixar o artifact `wordcraft-debug-apk`
2. [x] Instalar (sideload)
3. [x] Abrir — renderiza, digita, atalhos funcionam
4. [ ] Testes extras de DeX — **ainda não feitos** (o teste foi no S22, não no DeX)
5. [x] Registrar bugs em `android-shell/KNOWN_ISSUES.md`

Testes pendentes de DeX:
- Arrastar/redimensionar/maximizar a janela **com o documento aberto**
- Alternar entre modo tela do celular e DeX
- Teclado físico + mouse (atalhos, botão direito, scroll)
- Abrir `.docx` vindo de Downloads, Google Drive e OneDrive

---

## 9. Fase 6: Ponte de arquivos — **PRÓXIMA ETAPA**

### 9.1 Abrir (sem mexer no Rust)

Na `MainActivity`, sobrescrever `onShowFileChooser`:

- `ValueCallback<Uri[]>` guardado como campo
- `registerForActivityResult(ActivityResultContracts.OpenDocument())` com os MIME-types do app (`.docx`, `.docm`, `.dotx`, `.odt`, `.rtf`, `.txt`, `.md`, `.html`, `.pdf`, imagens)
- No `onShowFileChooser`: guardar o callback, lançar o launcher
- No resultado: ler os bytes e devolver o `Uri` → o `<input type="file">` do `rfd` recebe o `change` e o arquivo cai no `inbox`

### 9.2 Salvar/exportar

- JS injetado em `onPageFinished`:
  - intercepta cliques em `a[download]`
  - lê o blob, converte pra base64
  - chama `Capacitor.Plugins.FileBridge.save(name, base64)`
- Kotlin: plugin `FileBridge` gravando em `MediaStore.Downloads` (API 29+) ou no storage do app, e oferecendo `@capacitor/share`

### 9.3 Ordem

1. `onShowFileChooser` (abrir) — menor, valida a hipótese de que o caminho JS do `rfd` funciona na WebView
2. `FileBridge` (salvar/exportar)
3. Testar no aparelho; se a opção 1 ficar frágil, migrar para `addJavascriptInterface` com `window.AndroidBridge.save(name, base64)`

---

## 10. Backlog de polimento

- Ícone e splash (usar o ícone WordCraft de `assets/app-icon/`)
- Handler de intent para abrir `.docx` direto do gerenciador de arquivos ("Abrir com WordCraft")
- Salvar com SAF (Storage Access Framework) em vez de pasta fixa
- Modo offline (service worker) ou garantir que tudo vem do `www/` embutido (já é o caso no Capacitor)
- Dicionários de spelling para pt-BR (o upstream só embute inglês; outros idiomas dependem de Hunspell do sistema)
- Release assinado + `versionCode` automático pela tag
- `webContentsDebuggingEnabled` no debug + Eruda
- Consolidar commits do branch (há 3 commits com subjects repetidos)
- `versionName`: corrigido no `capacitor.config.ts` (`version: "0.8.0"`), **não publicado**

---

## 11. Riscos e mitigações

| Risco | Prob. | Impacto | Mitigação | Estado |
|---|---|---|---|---|
| WebView sem WebGPU e wgpu sem fallback WebGL2 | Média | Alto | O eframe já traz fallback WebGL2 | Mitigado |
| Salvar arquivo não funciona | Alta | Alto | Ponte de arquivos (§9) | **Aberto** |
| Abrir arquivo não funciona | Alta | Alto | `onShowFileChooser` (§9.1) | **Aberto** |
| Resize no DeX reinicia o app | Média | Médio | `configChanges` + `resizeableActivity` — **confirmado no APK** | Mitigado |
| Build Rust do CI lento | Média | Médio | `rust-cache`; run leva ~13 min | Aceito |
| Projeto é alpha, bugs que não são nossos | Alta | Baixo | Reportar upstream, isolar em `KNOWN_ISSUES.md` | Vigente |
| Fork diverge do upstream | Média | Baixo | Commits pequenos; casca em `android-shell/` separada | Vigente |
| Desempenho ruim em WASM no celular | Baixa | Médio | Medir com documento grande; Plano B | A medir |

---

## 12. Plano B: port nativo (gatilhos)

Migrar para `cdylib` + `cargo-ndk` **só se**:
- A UI não renderizar na WebView mesmo com fallback WebGL2, **ou**
- Desempenho inaceitável em documentos médios, **ou**
- A ponte de arquivos virar uma gambiarra frágil

Envolveria: novo crate `apps/wordcraft-android` com `android_main` reaproveitando `wordcraft-ui-egui`; trocar `rfd` e `arboard` por JNI (SAF e ClipboardManager); embutir dicionários; `cargo ndk -t arm64-v8a build --release` no CI.

**Ainda não acionado.**

---

## 13. Licença e marca

- Código: MIT **ou** Apache-2.0, fork e distribuição livres, mantendo `LICENSE-*` e `NOTICE`
- **Marca ArtCraft:** os logos em `docs/brand/` não são cobertos pela licença; forks e versões modificadas devem removê-los. Conferir `docs/brand/LICENSE-brand.txt` e `ATTRIBUTION.md` antes de publicar o APK, e ajustar nome/ícone/splash se necessário
- WordCraft não tem afiliação com a Microsoft; não usar "Word" como nome do app
- **Pendente de verificação antes de qualquer distribuição** (hoje é só sideload em aparelho próprio)

---

## 14. Estado do Gate 2 (WebView, no aparelho)

Testado no S22 (não no DeX — o resize ainda não foi validado).

| # | Teste | Resultado |
|---|---|---|
| 1 | App abre sem crash | ✔ |
| 2 | UI renderiza (ribbon, página, rulers) | ✔ |
| 3 | Digitar / formatar (Ctrl+B, estilos) / undo-redo | ✔ |
| 4 | Mouse: clique, drag-select, scroll | ✔ |
| 5 | Abrir `.docx` do armazenamento | **FALHA** — ponte não implementada |
| 6 | Salvar / Salvar como | **FALHA** — ponte não implementada |
| 7 | Exportar PDF e outras extensões | **FALHA** — ponte não implementada |
| 8 | Redimensionar no DeX sem perder o documento | **NÃO TESTADO** |
| 9 | Acentos / UI em pt-BR | ✔ |
| 10 | Documento grande | NÃO TESTADO |

**GATE 2: PARCIAL.** Renderização e edição passam; arquivos falham por design (ponte ausente). Não é bug do WebView nem do WASM — é o que a Fase 6 implementa.

---

## 15. Checklist de execução (resumo)

**Dia 1 — validar ✔**
- [x] Fork + clone + branch
- [x] Rodar os `rg` da seção 3.3 e anotar respostas
- [x] Testar web no Chrome
- [x] Decidir no Gate 1

**Dia 2 — casca ✔**
- [x] `android-shell/` com Capacitor
- [x] Manifest com ajustes de DeX
- [x] Workflow de APK rodando verde
- [x] Instalar APK e verificar conteúdo

**Dia 3 — arquivos e polimento — EM ANDAMENTO**
- [ ] Ponte de abrir `.docx` (`onShowFileChooser`)
- [ ] Ponte de salvar/exportar (FileBridge + JS)
- [ ] Testes extras de DeX (resize com documento aberto)
- [ ] `KNOWN_ISSUES.md` + README do shell atualizados

---

## 16. Histórico de execução

| Data | Etapa | Resultado |
|---|---|---|
| 2026-10-19 | Gate 1 — web no browser | ✔ passa |
| 2026-10-19 | Fase 3 — casca Capacitor criada | ✔ |
| 2026-10-19 | Fase 4 — CI, run 39429248424 | falha: caminho do `dist` |
| 2026-19 | Fase 4 — run 39445793327 | falha: `npm ci` + tema do manifest |
| 2026-10-19 | Fase 4 — run 39448573142 | **verde** — APK gerado e verificado |
| 2026-10-19 | Fase 5 — Gate 2 no S22 | parcial: arquivos falham (ponte ausente) |
