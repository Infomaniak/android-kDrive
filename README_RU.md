<center>

[English](README.md) | **Русский**

<br>

<img src="fastlane/metadata/android/en-US/images/icon.png" alt="Logo" style="max-width: 150px; height: auto;">

<br>

# Приложение Infomaniak kDrive

</center>

## Современное Android-приложение для [kDrive от Infomaniak](https://www.infomaniak.com/kdrive).
### Синхронизация, совместное использование, совместная работа. Швейцарское облако с полной защитой данных.

#### :cloud: Всё необходимое пространство
Ваши фотографии, видео и документы всегда под рукой. kDrive может хранить до 106 ТБ данных.

#### :globe_with_meridians: Экосистема для совместной работы. Всё включено.
Совместная работа онлайн над документами Office, организация встреч, обмен файлами — всё возможно!

#### :lock: kDrive уважает вашу конфиденциальность
Защитите свои данные в суверенном облаке, разработанном и размещённом исключительно в Швейцарии. Infomaniak не анализирует и не продаёт ваши данные.

[<img src="https://play.google.com/intl/en_us/badges/static/images/badges/en_badge_web_generic.png" 
      alt="Download from Google Play" height="100">](https://play.google.com/store/apps/details?id=com.infomaniak.drive)
[<img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png"
      alt="Get it on F-Droid"
      height="100">](https://f-droid.org/packages/com.infomaniak.drive/)

## Лицензия и вклад
Проект распространяется под лицензией GPLv3.
Если вы обнаружите баг или хотите предложить улучшение, создайте issue для обсуждения. После утверждения мы или вы (в зависимости от приоритетности) займёмся исправлением и сделаем pull request.
Пожалуйста, не отправляйте pull request, не создав предварительно issue для обсуждения.

## Технические детали

### Языки
Макеты созданы с использованием **XML**-компонентов Android. Весь проект написан на **Kotlin**.

### Совместимость
Минимальная версия Android для запуска приложения — Lollipop 5.1 (API 22). Рекомендуем использовать последние версии Android, большинство тестов проводилось на Android 10 и 11 (API 29 и 30).

### Кэш
Для хранения офлайн-файлов, данных об общем доступе, настроек приложения и пользовательских предпочтений используется [Realm.io](https://realm.io/) на обеих платформах (iOS и Android). Для хранения токенов API и основной информации о пользователе применяется Android Room.

### Структура
Структура приложения, алгоритмы и общее функционирование совпадают с iOS-версией.

### Разрешения
<div style="overflow-x: auto;">
<table style="width: 100%; min-width: 320px; border-collapse: collapse;" border="1" cellpadding="5" cellspacing="0">
<thead>
<tr>
<th>Название разрешения</th>
<th>Использование</th>
</tr>
</thead>
<tbody>
<tr>
<td><code>GET_ACCOUNTS</code>, <code>AUTHENTICATE_ACCOUNTS</code>, <code>MANAGE_ACCOUNTS</code>, <code>USE_CREDENTIALS</code></td>
<td>Доступ и управление AccountManager.</td>
</tr>
<tr>
<td><code>com.infomaniak.permission.ASK_CREDENTIAL</code>, <code>com.infomaniak.permission.RECEIVE_CREDENTIAL</code></td>
<td><em>(*Временно не используется*)</em> Позволяет kDrive обмениваться учетными данными с другими приложениями Infomaniak для аутентификации без повторного ввода логина.</td>
</tr>
<tr>
<td><code>INTERNET</code>, <code>ACCESS_NETWORK_STATE</code></td>
<td>Проверка доступа к Интернету для обновления интерфейса и ограничения функций в офлайн-режиме.</td>
</tr>
<tr>
<td><code>READ_EXTERNAL_STORAGE</code>, <code>WRITE_EXTERNAL_STORAGE</code></td>
<td>Чтение файлов с устройства для их загрузки в kDrive. Запись используется для скачивания файлов из kDrive и работы с MediaStore.</td>
</tr>
<tr>
<td><code>READ_SYNC_SETTINGS</code>, <code>WRITE_SYNC_SETTINGS</code>, <code>READ_SYNC_STATS</code></td>
<td>Управление автоматической синхронизацией (проверка, включение и т.д.)</td>
</tr>
<tr>
<td><code>RECEIVE_BOOT_COMPLETED</code></td>
<td>Определение запуска устройства для перезапуска службы синхронизации.</td>
</tr>
<tr>
<td><code>FOREGROUND_SERVICE</code></td>
<td>Используется для фоновой загрузки файлов и службы синхронизации.</td>
</tr>
<tr>
<td><code>REQUEST_IGNORE_BATTERY_OPTIMIZATIONS</code></td>
<td>Позволяет скачивать файлы в фоне.</td>
</tr>
<tr>
<td><code>USE_BIOMETRIC</code></td>
<td>Используется для блокировки/разблокировки приложения.</td>
</tr>
<tr>
<td><code>REQUEST_INSTALL_PACKAGES</code></td>
<td>Позволяет устанавливать APK-файлы из приложения kDrive.</td>
</tr>
</tbody>
</table>
</div>

## Тесты

Для выполнения Unit и UI тестов скопируйте класс `Env-Example` из пакета AndroidTest и назовите его `Env`.
⚠️ Не забудьте отключить двухфакторную аутентификацию (2FA) в аккаунте Infomaniak перед запуском тестов, так как она не поддерживается для теста AddUser.
Замените значения в файле на свои и запускайте тесты 👍
