# Broxa — Tempo de Tela 📱

App companheiro do **Broxa Games**. Lê o tempo de uso dos apps do celular (Bem-estar Digital) e envia automaticamente pro jogo, via Firebase (`forge_screentime/<nome>`).

## Como instalar (no celular)

1. Baixe o APK mais recente: **[Releases → latest → `app-debug.apk`](../../releases/latest)**
2. Abra o arquivo e permita "instalar de fonte desconhecida".
3. Abra o app, escolha seu nome (Rodrigo / Mizuki / Craudo / Ana).
4. Toque em **"1. Conceder acesso ao uso"** → ache "Broxa Tempo de Tela" na lista → ative.
5. Toque em **"2. Sincronizar agora"**.

Pronto. O app também sincroniza sozinho a cada ~6h em segundo plano. No Broxa Games, abra a aba **📱 TELA**.

## Build

Compilado automaticamente pelo GitHub Actions (`.github/workflows/build.yml`) — não precisa de Android Studio. Cada push gera um APK no Release `latest`.
