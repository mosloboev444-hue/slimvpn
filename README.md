# SlimVPN — клиент AmneziaWG для Android TV

[![License: MIT](https://img.shields.io/badge/license-MIT-2ea44f)](LICENSE)
[![APK](https://img.shields.io/badge/APK-6%20MB-0aa1dd)](https://github.com/mosloboev444-hue/slimvpn/releases/latest)
[![Android TV](https://img.shields.io/badge/Android%20TV-8.0%2B-3ddc84)](https://developer.android.com/tv)

Минималистичный VPN-клиент для ТВ-приставки: импорт `.conf` из папки `Download`, одна кнопка «Подключить»,
и **автоматическое включение VPN при запуске YouTube** — с автоматическим выключением, когда YouTube закрыли.

![Экран SlimVPN](docs/screen-main.png)

## Зачем AmneziaWG

Обычный WireGuard в российских сетях режется DPI: рукопожатие проходит, а данные — нет.
AmneziaWG добавляет обфускацию (`Jc`, `Jmin`, `Jmax`, `H1`–`H4`, подмена заголовков), и такой трафик
проходит. Конфиги AmneziaWG совместимы с WARP: `Endpoint = engage.cloudflareclient.com:4500`.

## Возможности

- импорт любого AmneziaWG/WireGuard `.conf` из `/sdcard/Download`;
- подключение одной кнопкой, статус и состояние туннеля;
- **сторож YouTube**: приложение раз в 2 секунды смотрит активное окно (UsageStats) и поднимает VPN,
  когда на экране YouTube (`com.google.android.youtube.tv` или SmartTube), а при выходе — опускает;
- ручной VPN сторож не трогает: выключает только то, что поднял сам;
- переключатель «Автоподключение для YouTube» на главном экране;
- автозапуск сторожа после загрузки приставки (`BOOT_COMPLETED`).

## Установка

```bash
adb install -r SlimVPN-2.1.apk
adb push my.conf /sdcard/Download/my.conf
# разрешения, которые нужны сторожу (на ТВ выдаются только через adb/root):
adb shell appops set com.slimvpn.app GET_USAGE_STATS allow
adb shell appops set com.slimvpn.app ACTIVATE_VPN allow
```

Дальше: открыть SlimVPN → «Импорт из Download» → «Подключить».

## Как устроено

```
app/src/main/java/com/slimvpn/app/
  MainActivity.java     — экран: профиль, подключение, переключатель автоподключения
  VpnController.java    — единая точка управления туннелем (Backend + Tunnel)
  AutoVpnService.java   — foreground-сторож: UsageStats → вкл/выкл VPN
  BootReceiver.java     — запуск сторожа после загрузки
  org/amnezia/awg/**    — Java-часть AmneziaWG (backend, config, crypto) — Apache-2.0
app/src/main/jniLibs/armeabi-v7a/libwg-go.so — Go-ядро AmneziaWG, собрано под android/arm
```

## Сборка Go-ядра

`libwg-go.so` уже лежит в `jniLibs`, но его можно пересобрать:

```bash
# нужен Go 1.25+ и Android NDK
tools/build-libwg-go.ps1 -GoPath C:\go\bin\go.exe -Ndk C:\android-sdk\ndk\27.2.12479018
```

Скрипт делает то же, что Makefile из `amneziawg-android`, но без POSIX-утилит (flock/sha256sum/tar),
поэтому работает на Windows:

```
GOOS=android GOARCH=arm CGO_ENABLED=1 CC=<ndk>/armv7a-linux-androideabi21-clang.cmd \
  go build -tags linux -buildmode=c-shared -o libwg-go.so .
```

## Лицензия

Код приложения — MIT (см. [LICENSE](LICENSE)). Java- и Go-часть AmneziaWG — Apache-2.0,
см. [LICENSE-AMNEZIAWG.txt](LICENSE-AMNEZIAWG.txt) и проекты
[amneziawg-android](https://github.com/amnezia-vpn/amneziawg-android),
[amneziawg-go](https://github.com/amnezia-vpn/amneziawg-go).

**Важно:** репозиторий не содержит конфигов и ключей. Свой конфиг вы получаете у провайдера VPN
или генерируете сами (например, WARP через `wgcf`).