# DSH Agent — Android

Agente de IA nativo para Android, com interface escura inspirada no estilo visual mostrado na referência, projetos isolados e compilação automática via GitHub Actions.

## Recursos incluídos

- Interface mobile em Kotlin + Jetpack Compose: chat, projetos, ferramentas e configurações; seletor de modelo e nível de pensamento no compositor.
- Vários provedores/chaves/modelos compatíveis com a API de chat OpenAI-style. Presets: DeepSeek, OpenAI, OpenRouter, Groq, Together AI, Gemini OpenAI-compatible e URL personalizada.
- Fallback automático para outro provedor configurado se a chamada falhar (incluindo erros de autenticação, cota/rate limit ou erros do servidor).
- Chaves cifradas localmente com AES-GCM via Android Keystore.
- Anexos de imagem, vídeo (até 3 quadros representativos), áudio (tentativa de transcrição por `/audio/transcriptions`) e arquivos de texto.
- Ferramentas que o modelo pode chamar: listar arquivos, ler/gravar arquivos de texto do projeto, pesquisar na web e salvar memória durável no `MEMORY.md`.
- Workspace interno privado por padrão. Opcionalmente, escolha uma pasta pelo seletor de documentos Android; nessa pasta o app cria `DSH Projects`.
- Integração Shizuku para executar comandos no shell Android com pedido de aprovação visível por comando.
- Fluxo GitHub Actions para compilar um APK de debug e publicá-lo como artifact.

## O que precisa configurar no primeiro uso

1. Instale o APK gerado pelo workflow.
2. Abra **Configurações → Provedores e chaves → Adicionar**.
3. Escolha um preset, informe sua API key e o ID exato do modelo. Pode adicionar várias entradas, inclusive várias chaves do mesmo provedor.
4. Deixe habilitadas as entradas que devem ser usadas no fallback. O provedor atualmente selecionado é tentado primeiro; os demais habilitados são tentados depois.
5. Escolha o nível de pensamento ao lado do seletor de modelo no compositor.
6. Para shell, instale e inicie o Shizuku, ative o módulo **Shell Android via Shizuku** e conceda a permissão solicitada.

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
- PDF e formatos binários arbitrários não têm extração textual embutida; arquivos textuais comuns são lidos diretamente. Imagens são enviadas em base64 para o provedor configurado.
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
  ui/        # interface Compose e ViewModel
.github/workflows/android.yml
```

## Segurança

- Toda chamada de shell gerada pelo modelo precisa de confirmação explícita do usuário.
- A gravação de arquivos é restrita ao diretório do projeto e bloqueia caminhos com `..`.
- As chaves são cifradas usando a chave AES do Android Keystore; se os dados locais forem corrompidos ou a chave do Keystore ficar indisponível, será necessário cadastrar as chaves novamente.
- Não adicione API keys reais ao repositório, issues, logs do Actions ou capturas de tela.
