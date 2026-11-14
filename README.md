# Sono by Dra Alessandra — Diário do Sono (Android)

App Android para monitorar o sono (qualidade, duração, medicamentos, hábitos, escalas) e gerar relatórios
para avaliação de pacientes com distúrbios do sono. Um único app com dois modos:

- **Paciente**: diário diário (Consensus Sleep Diary), medicamentos, ISI, Epworth e STOP-Bang, lembrete
  diário, relatório PDF e CSV, e exportação de um arquivo `.sono` cifrado para o profissional.
- **Profissional**: importa os arquivos `.sono` (com a senha combinada), lista de pacientes, relatório,
  gráficos, diário, escalas e anotações clínicas por paciente.

## Privacidade e segurança
- Sem servidor e sem conta: os dados ficam apenas no aparelho e não entram no backup na nuvem do Android.
- Bloqueio por biometria, ou pelo PIN/padrão do aparelho, ao abrir o app e após 60 s em segundo plano.
- Arquivo de exportação cifrado com AES-256-GCM, com chave derivada da senha via PBKDF2-SHA256
  (210 mil iterações).

## Compilar
Requer JDK 17+ e Android SDK 35. Abra a pasta no Android Studio, ou rode:

```bash
./gradlew assembleRelease
```

O APK sai em `app/build/outputs/apk/release/`. Ele é assinado com a chave de debug, adequada só para
testes por instalação direta. Para distribuição, crie uma chave própria.

## Testes
```bash
./gradlew testDebugUnitTest
```
Os testes cobrem as métricas do sono, a pontuação das escalas, a criptografia, o CSV, a geração das
páginas do relatório e os fluxos de interface (Robolectric).

## Pendências antes de uso clínico formal
- Substituir os textos das escalas pelas versões brasileiras validadas, obtidas com os detentores dos
  direitos (ISI e Epworth: Mapi Research Trust; STOP-Bang: stopbang.ca).
- Testar em aparelhos reais com Android 8 a 15.
- Avaliar o enquadramento na RDC 657/2022 (ANVISA) se houver finalidade diagnóstica ou comercial.

Fonte do logotipo: Great Vibes (SIL Open Font License).
