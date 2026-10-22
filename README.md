# DSH Agent — Android

Agente de IA nativo para Android, com interface escura inspirada no estilo visual mostrado na referência, projetos isolados e compilação automática via GitHub Actions.

## Recursos herdados da 0.2.0

- **Vários modelos por provedor**: cadastre uma URL base (ex.: `https://codecraftapi.com/v1`) e uma chave uma única vez. O app busca os modelos em `GET {URL}/models`; marque os que quer usar ou digite os IDs manualmente (aceita vários separados por vírgula/espaço). O seletor do chat agrupa os modelos por provedor, com busca.
- **Upload de ZIP no chat** (menu `+` → *Projeto ZIP*): o ZIP é extraído em uma subpasta do projeto (remove a pasta raiz única, ignora `.git`, `node_modules` etc., bloqueia caminhos inseguros e respeita limites de tamanho). O agente recebe a estrutura e os arquivos-chave e explora o resto com as ferramentas. Se o módulo de arquivos estiver desativado, o ZIP só é lido e resumido.
- **Agente sem limite de etapas**: o loop de ferramentas continua até o modelo concluir; use **Parar** para interromper. Saídas antigas de ferramentas são compactadas automaticamente quando o contexto fica grande.
- **Novas ferramentas do agente**: `edit_project_file` (substituição exata), `search_project`, `move_project_file`, `delete_project_path` (com aprovação), `fetch_url` (https público, bloqueia endereços privados) e leitura por linhas.
- **Projetos**: árvore de pastas, busca por nome, visualizador/editor de arquivos, novo arquivo, importar/exportar ZIP, renomear/excluir projeto.
- **Chat**: Markdown com blocos de código (copiar), cartão de ações do agente, copiar/tentar de novo/excluir mensagem, renomear/excluir sessões, botão Parar, PDFs renderizados como imagens para modelos com visão.
- Instruções personalizadas globais, conversas salvas em arquivos (migração automática da versão anterior), retry automático sem `reasoning_effort`/`tools` quando o provedor rejeita esses parâmetros.

## Recursos base

- Interface mobile em Kotlin + Jetpack Compose: chat, projetos, ferramentas e configurações.
- Vários provedores compatíveis com a API de chat OpenAI-style, com fallback automático para o próximo provedor ativo em caso de falha/cota.
- Chaves cifradas localmente com AES-GCM via Android Keystore.
- Anexos de imagem, vídeo (até 3 quadros), áudio (transcrição via `/audio/transcriptions`, se o provedor oferecer), PDF e texto.
- Workspace interno privado por padrão; opcionalmente uma pasta escolhida pelo seletor de documentos (o app cria `DSH Projects`).
- Shizuku para shell Android com aprovação por comando.
- GitHub Actions compila um APK de debug.

## Primeiro uso

1. Instale o APK gerado pelo workflow.
2. **Configurações → Adicionar provedor**: escolha um preset (ou *Compatível personalizado*), informe URL base e API key.
3. Toque em **Buscar modelos automaticamente**. Se o provedor não expuser `/models`, digite os IDs manualmente.
4. Salve e escolha o modelo no chip do compositor.
5. Para shell, instale e inicie o Shizuku, ative o módulo na aba Ferramentas e conceda a permissão.

## Compilar no GitHub Actions

1. Crie um repositório vazio no GitHub chamado, por exemplo, `dsh-agent-android` (não adicione README/licença pelo site, pois eles já estão aqui).
2. Extraia o ZIP desta fonte no computador ou no ambiente onde você usa Git. Abra um terminal na pasta `DSHAgentAndroid` e execute (troque `SEU_USUARIO` pelo seu usuário do GitHub):

   ```bash
   git init
   git add .
   git commit -m "Initial DSH Agent Android"
   git branch -M main
   git remote add origin https://github.com/SEU_USUARIO/dsh-agent-android.git
   git push -u origin main
   ```

3. No GitHub, abra **Actions**. A workflow `.github/workflows/android.yml` roda em push, pull request ou manualmente em **Android debug APK → Run workflow**. Se a aba Actions estiver desabilitada para o repositório, habilite-a nas configurações.
4. Quando terminar, abra a execução concluída e baixe o artifact **`dsh-agent-debug-apk`**. Dentro dele estará `app-debug.apk`.

O workflow instala o Android SDK necessário, configura JDK 17 e Gradle 8.9 e executa `:app:assembleDebug`. O APK gerado é de desenvolvimento; para distribuição pública, configure assinatura de release e revise as permissões e a política de privacidade.

## Limitações importantes

- O app é um ponto de partida funcional, não um sistema capaz de fazer literalmente qualquer coisa. A IA só consegue usar ferramentas implementadas e habilitadas, e continua sujeita às capacidades/limites do provedor, rede e Android.
- Os presets de provedores usam um endpoint compatível com `/chat/completions`. Provedores com APIs proprietárias incompatíveis precisam de um adaptador específico.
- O nível de pensamento é passado como `reasoning_effort` somente para famílias de modelos que aceitam esse parâmetro; nos demais, ele orienta o prompt. O comportamento exato depende do provedor/modelo.
- A transcrição de áudio tenta o endpoint OpenAI-compatible `/audio/transcriptions` com `whisper-1`. Nem todos os provedores oferecem esse endpoint. Vídeos são representados por até três quadros; o app não faz análise audiovisual contínua.
- PDFs são enviados como imagens das 4 primeiras páginas (sem extração de texto); outros binários não são interpretados. Arquivos textuais são lidos diretamente.
- Só há suporte ao formato `/chat/completions` (OpenAI). Modelos que exijam a API nativa da Anthropic (`/v1/messages`) ou `/responses` precisam que o gateway faça a conversão. Imagens são enviadas em base64 para o provedor configurado.
- O módulo de pesquisa usa resultados públicos extraídos do DuckDuckGo HTML e pode falhar por bloqueios ou mudanças no site.
- Shizuku não concede root por si só. A permissão disponível costuma corresponder à identidade ADB/shell e continua restrita pelas versões do Android/OEM. Este app mostra os comandos e exige confirmação antes da execução; não autorize comandos que você não entende.
- As chaves ficam cifradas no dispositivo, mas o prompt, texto dos anexos e imagens podem ser enviados ao provedor de IA selecionado. Não envie conteúdo confidencial a um provedor sem avaliar sua política.
- Os módulos são nativos e ativáveis/desativáveis. O carregamento dinâmico de plugins de terceiros ainda não está implementado nesta versão.

## Estrutura

```text
app/src/main/java/com/lippezin/dshagent/
  ai/        # cliente de provedor, loop de ferramentas e anexos
  data/      # modelos e preferências cifradas
  storage/   # arquivos e memória por projeto
  tools/     # shell Shizuku e pesquisa web
  background/# fila persistente, worker e notificações
  ui/        # interface Compose e ViewModel
.github/workflows/android.yml
```

## Segurança

- Toda chamada de shell gerada pelo modelo precisa de confirmação explícita do usuário.
- A gravação de arquivos é restrita ao diretório do projeto e bloqueia caminhos com `..`.
- As chaves são cifradas usando a chave AES do Android Keystore; se os dados locais forem corrompidos ou a chave do Keystore ficar indisponível, será necessário cadastrar as chaves novamente.
- Não adicione API keys reais ao repositório, issues, logs do Actions ou capturas de tela.

## Execução em segundo plano (v0.3.0)

As mensagens agora são agendadas com **WorkManager** e salvas primeiro em armazenamento privado do aplicativo (`filesDir/agent-task-queue`). O trabalho usa a restrição de rede `CONNECTED`, um `CoroutineWorker` em primeiro plano e uma notificação persistente durante a execução. Assim, o usuário pode sair da atividade do chat e apagar a tela sem depender do `ViewModel` da tela para manter o pedido vivo. Ao voltar, o app observa o trabalho e atualiza a conversa original, mesmo que outra sessão tenha sido selecionada.

- Se a tarefa estiver na fila sem conexão, ela fica persistida e o WorkManager tenta executá-la quando a rede voltar.
- Se a conexão cair durante a chamada, o worker solicita nova tentativa quando não há internet validada. A tentativa reinicia a tarefa a partir do pedido salvo; o motor ainda não checkpointa cada subetapa de raciocínio/ferramenta, então operações de arquivo já concluídas podem ser repetidas em uma retomada.
- As tarefas que dependem de modelos hospedados na nuvem não conseguem obter nova inferência sem internet. Esta versão não inclui um LLM local para raciocínio offline.
- O painel **Atividade** mostra atualizações de estado, ferramentas e fallback de provedores. Ele não tenta mostrar a cadeia de pensamento privada do modelo, que não é exposta de forma padronizada pelas APIs dos provedores.
- Execução de shell e exclusão de arquivos exigem confirmação. Quando uma solicitação dessas aparece no worker sem interface aberta, a ação é recusada, em vez de executar silenciosamente; reabra o app para autorizar manualmente.
- No Android 13+, o app solicita permissão para notificações. Se a permissão for negada, a execução em primeiro plano ainda fica sujeita às regras do Android, mas a notificação de progresso poderá não aparecer na gaveta.
- O sistema operacional e fabricantes podem interromper processos por políticas de bateria; o WorkManager é durável, mas não oferece garantia absoluta de que o processo nunca será interrompido. Tarefas interrompidas podem ser reexecutadas.

### Permissões e limites de execução

O manifesto declara o tipo de serviço em primeiro plano `dataSync` exigido pelo Android moderno. Em Android 15/API 35, esse tipo possui limite acumulado de tempo em primeiro plano; em Android 16, trabalhos longos baseados em WorkManager também continuam sujeitos às cotas de tarefas do sistema. Para pedidos muito longos, o WorkManager pode adiar ou reexecutar o trabalho.

## Build fix — 0.3.1

The first 0.3 build log exposed two Kotlin compilation issues in `AgentViewModel.kt`:

- `AgentTaskPayload` was referenced without importing it from `background.AgentTaskStore`.
- `WorkInfo` does not expose the worker input `Data` as `inputData`; the task is now recovered from the unique task tag (`dsh-agent-task:<taskId>`), while progress/output data remain available for status updates.

These fixes are included in the 0.3.1 source archive.

## Roteadores locais — 0.3.2

- **URL base HTTP/localhost**: o campo de URL aceita `http://` e `https://`, com porta (ex.: `http://localhost:PORTA/v1`, `http://127.0.0.1:PORTA/v1` ou o IP do PC na rede, como `http://192.168.0.10:PORTA/v1`). O manifesto agora permite tráfego HTTP. Se o host HTTP não for local/privado (localhost, 127.x, 10.x, 172.16–31.x, 192.168.x, Tailscale, `.local`), a tela avisa que a chave seria enviada sem criptografia.
- **Modelo opcional**: um provedor pode ser salvo sem nenhum modelo. Nesse caso o app não envia o campo `model` e o roteador usa o padrão dele. No seletor do chat aparece a opção *Modelo padrão do roteador*. Se o roteador exigir o campo, a mensagem de erro avisa e você pode adicionar o ID depois.
- **API key opcional**: vazia, o app não envia o cabeçalho `Authorization` (útil para roteadores locais sem autenticação).
- **Buscar modelos** também funciona em HTTP: o app consulta `{URL}/models`; muitos roteadores listam ali os modelos e combos disponíveis.
- Novo preset: *Roteador local (HTTP)*.
- A ferramenta `fetch_url` do agente continua restrita a HTTPS público.

## Redesign — 0.4.0

- **Identidade visual "midnight indigo"**: fundo quase preto azulado, superfícies em camadas, acento índigo/violeta e gradiente de marca (logo, botão de envio, títulos de destaque). Tipografia e cantos arredondados refinados em todo o app (`Theme.kt`).
- **Chat**: boas-vindas com logo luminoso, cápsula do modelo ativo e quatro cartões de ações rápidas; respostas em largura total com cabeçalho do agente e etiqueta do modelo; bolha de usuário com acabamento suave; cartão de ações do agente com nome da ferramenta em destaque; indicador de "digitando" animado e botão Parar.
- **Compositor**: cápsula flutuante com borda em gradiente quando há conteúdo, botão `+` circular, cápsulas de modelo e esforço de raciocínio e botão de envio com gradiente.
- **Navegação**: barra inferior flutuante em cápsula (o item ativo expande com o rótulo) e barra lateral com logo em telas largas.
- Todas as demais telas (Projetos, Ferramentas, Configurações, seletor de modelos) foram reentonadas para a nova paleta e títulos de seção em destaque.
- Barras do sistema e cor de destaque do tema XML ajustadas ao novo fundo.

## Memória e integrações — 0.5.0

Compatibilidade com o fluxo de "memória infinita" para agentes de código (CLAUDE.md/AGENTS.md, cofre Obsidian, graphify e ai-memory). Configure em **Ferramentas → Memória e integrações**.

### 1. Regras `AGENTS.md` / `CLAUDE.md`
- Os arquivos na **raiz do projeto** entram automaticamente no prompt de sistema (se os dois existirem e forem idênticos, vale um só).
- Os de **subpastas** (inclusive de repositórios extraídos de ZIP) entram **sob demanda**: na primeira vez que o agente lê, edita, grava ou lista algo dentro da pasta, as regras dela são anexadas ao resultado da ferramenta.
- Eles nunca dispensam a aprovação do usuário para shell e exclusão. Previews de ZIP agora incluem `AGENTS.md`, `CLAUDE.md` e `GEMINI.md`.

### 2. Cofre Obsidian como memória de longo prazo
- **Criar cofre** gera um projeto com `00-Inbox`, `10-Trabalho`, `20-Estudos`, `30-Projetos`, `50-Pessoal`, `70-Daily`, `99-Templates`, `CLAUDE.md` e `AGENTS.md` (tabela de roteamento palavra-chave → pasta, regras de leitura e escrita), `HOME.md` e templates `_nota`, `_daily`, `_decisao`. Nada existente é sobrescrito (*Completar estrutura* roda de novo com segurança).
- Também dá para usar um cofre existente: selecione o projeto e envie o ZIP do cofre pelo chat. A raiz é detectada por `.obsidian`, `CLAUDE.md` ou `AGENTS.md`.
- Ferramentas do agente: `vault_search` (termos + filtros `folder/area/type/status/tag`, no máximo 10 resultados, mostrando frontmatter), `vault_list`, `vault_read` (com `frontmatter_only`), `vault_write` (nota atômica em kebab-case sem acento, frontmatter padrão, nasce em `00-Inbox` com `status: inbox` quando não há destino) e `vault_update` (acrescenta ou troca o corpo e atualiza `updated`, sem tocar em `id`/`created`).
- O `CLAUDE.md` do cofre vai no prompt, então o agente já sabe a tabela de roteamento sem varrer o vault.
- Substitui o `mcpvault` do vídeo, que roda por `npx`/stdio e não existe no Android.

### 3. graphify
- O app **não gera** o grafo (é uma ferramenta Python do PC: `uv tool install graphifyy` e `graphify extract .`). Inclua a pasta `graphify-out/` no ZIP do projeto.
- Com `graphify-out/GRAPH_REPORT.md` presente, um trecho do relatório vai ao prompt e, havendo `graph.json` (até 12 MB, formato NetworkX), a ferramenta `graph_query` devolve nós, arquivos e conexões para o termo pedido.

### 4. Servidores MCP por HTTP (ai-memory)
- Cliente MCP "Streamable HTTP" (initialize, `tools/list`, `tools/call`; respostas JSON ou SSE; token Bearer opcional). As ferramentas aparecem para o modelo como `mcp_<servidor>_<ferramenta>`.
- Para o **ai-memory**: suba o servidor no PC (`docker run ... akitaonrails/ai-memory:latest`), use `http://IP-DO-PC:49374/mcp` e o token gerado (`ai-memory generate-auth-token`); fora do loopback ele exige token. Use **Testar** para listar as ferramentas.
- Opção por servidor para pedir aprovação a cada chamada. Resultados MCP são tratados como dados, nunca como instruções.
- Limitações: não há captura automática de sessões (os *hooks* `/hook` do ai-memory existem só para CLIs de código no PC) e servidores somente stdio não são suportados.

> Esta versão não foi compilada no ambiente em que foi escrita; rode o workflow do GitHub Actions e envie o log caso algum erro de compilação apareça.
