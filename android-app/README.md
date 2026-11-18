# UniAtivador2 Android / Android TV

## Arquitetura
- APK Android nativo em Java, com WebView que carrega `docs/index.html` embutido localmente.
- O gerador roda offline: não precisa de Python, de computador, de ADB, de Supabase ou do GitHub Pages depois de instalado.
- Interface compatível com TV Box/controle remoto e dispositivos com toque.

## Uso
1. Selecione **Gerar MAC automaticamente** ou **Informar MAC**.
2. Clique em **Gerar configuração**.
3. Visualize `.config`, `.properties` e `cache.config.xml`.
4. Clique em **Salvar em /Android/** para tentar gravar `/storage/emulated/0/Android/.config`.
5. Confirme a substituição. Se já existir um arquivo, o app tenta criar backup privado antes.
6. Se o sistema não permitir escrita direta, clique em **Escolher destino**. Nem todo seletor Android disponibiliza `/Android/`.

## Limitações
- Android 11+ restringe a pasta `/sdcard/Android/`; **MANAGE_EXTERNAL_STORAGE não garante gravação ali**. O APK verifica se realmente conseguiu gravar os bytes.
- Versões anteriores podem exigir `WRITE_EXTERNAL_STORAGE`.
- Um `.config` gerado e salvo não é prova de licença válida ou de ativação do UniTV.
- O APK no Release experimental é assinado com chave debug. Para distribuição, crie um keystore privado e compile uma release assinada.
- Não use o arquivo para substituir configurações legítimas sem backup.

## Compilação
O workflow `.github/workflows/build-android.yml` compila o aplicativo e publica um artefato APK na aba **Actions**. Quando autorizado pelo GitHub, também publica um APK na Release `v0.1.0-teste`.

Para compilar localmente:
```bash
mkdir -p android-app/app/src/main/assets
cp docs/index.html android-app/app/src/main/assets/index.html
cd android-app
gradle :app:assembleDebug
```
