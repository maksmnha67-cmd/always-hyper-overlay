# Always Hyper (v1.3 beta)

Приложение рисует чёрную "остров"-пилюлю (как Dynamic Island) поверх всех
приложений, с настраиваемой шириной, скруглением углов и вертикальным
положением. Есть переключение на "классический" вид (просто вырез камеры,
т.е. остров выключен).

- Package: `com.shyz.alwayshyper`
- Version: `1.3 beta` (versionCode 130)
- minSdk 26 (Android 8.0+)

## Как это работает

- `MainActivity.kt` — экран настроек на Jetpack Compose, точь-в-точь по
  дизайну из `always-hyper.jsx` (тёмная навигация, две карточки-моков
  телефона, переключатель, три слайдера, нижний таб-бар).
- `OverlayService.kt` — фоновый сервис, который добавляет `View` через
  `WindowManager` с типом `TYPE_APPLICATION_OVERLAY`. Он держит подписку на
  `SharedPreferences`, поэтому пилюля обновляется вживую, пока ты двигаешь
  слайдеры.
- `Prefs.kt` — общее хранилище состояния (SharedPreferences) между Activity
  и сервисом.
- `BootReceiver.kt` — если остров был включён, восстанавливает его после
  перезагрузки телефона.

## Разрешение "поверх других приложений"

При первом включении тумблера, если разрешение `SYSTEM_ALERT_WINDOW` ещё не
выдано, откроется системный экран
"Отображение поверх других приложений" → там нужно включить переключатель
для Always Hyper и вернуться назад. Приложение само проверит разрешение при
возврате на экран (по `onResume`) и включит сервис.

## Сборка через GitHub Actions

### Вариант А — автоматически, скриптом (нужен токен)

1. Создай Personal Access Token (classic) на
   https://github.com/settings/tokens/new со scope **repo** и **workflow**.
2. Запусти:
   ```bash
   cd always-hyper
   export GITHUB_TOKEN=ghp_твой_токен
   ./scripts/push_to_github.sh <твой_github_username> always-hyper private
   ```
   Скрипт сам создаст репозиторий через GitHub API и запушит туда весь
   проект. Токен нигде не сохраняется — используется только на время пуша.
3. Открой вкладку **Actions** в новом репозитории — сборка запустится
   автоматически.

### Вариант Б — вручную, без токена

1. Создай новый пустой репозиторий на GitHub.
2. Залей туда содержимое этой папки:
   ```bash
   cd always-hyper
   git init
   git add .
   git commit -m "Always Hyper 1.3 beta"
   git branch -M main
   git remote add origin https://github.com/<твой_профиль>/<репозиторий>.git
   git push -u origin main
   ```
3. Открой вкладку **Actions** в репозитории — workflow `Build APK` запустится
   автоматически на пуш в `main` (или его можно запустить вручную кнопкой
   "Run workflow").
4. Когда сборка завершится (зелёная галочка), зайди в неё и в разделе
   **Artifacts** скачай `always-hyper-debug-apk` (для установки на телефон)
   или `always-hyper-release-apk-unsigned` (без подписи, для CI/тестов —
   для реальной установки release-сборку нужно будет подписать).

## Установка APK на телефон

1. Скачай `.apk` из артефактов Actions (это будет `.zip`, внутри — сам apk).
2. Перенеси на телефон и открой файл, либо `adb install app-debug.apk`.
3. Разреши установку из "неизвестных источников", если Android спросит.
4. Открой Always Hyper → включи тумблер → выдай разрешение "поверх других
   приложений" в открывшихся системных настройках → вернись в приложение.

## Локальная сборка (если нужно проверить до пуша)

Нужны установленные Android SDK + JDK 17. Тогда:
```bash
gradle wrapper --gradle-version 8.7   # один раз, чтобы создать ./gradlew
./gradlew assembleDebug
```
APK появится в `app/build/outputs/apk/debug/app-debug.apk`.
