# Infomaniak kDrive app

## A modern Android application for [kDrive by Infomaniak](https://www.infomaniak.com/kdrive).
### Synchronise, share, collaborate.  The Swiss cloud that’s 100% secure.

#### :cloud: All the space you need
Always have access to all your photos, videos and documents. kDrive can store up to 106 TB of data.

#### :globe_with_meridians: A collaborative ecosystem. Everything included. 
Collaborate online on Office documents, organise meetings, share your work. Anything is possible!

#### :lock:  kDrive respects your privacy
Protect your data in a sovereign cloud exclusively developed and hosted in Switzerland. Infomaniak doesn’t analyze or resell your data.

[<img src="https://play.google.com/intl/en_us/badges/static/images/badges/en_badge_web_generic.png" 
      alt="Download from Google Play" height="100">](https://play.google.com/store/apps/details?id=com.infomaniak.drive)

## License & Contributions
This project is under GPLv3 license.
If you see a bug or an enhanceable point, feel free to create an issue, so that we can discuss about it, and once approved, we or you (depending on the priority of the bug/improvement) will take care of the issue and apply a merge request.
Please, don't do a merge request before creating an issue.

## Tech things

### Languages
Layouts were made in **XML** with Android "Layout" components, the whole project is developed in **Kotlin**. 

### Compatibility
The minimum needed version to execute the app is Android Lollipop 5.1 (API 22), anyway, we recommend to use the most recent version of Android, the majority of our tests having been carried out on Android 10 & 11 (API 29 & 30).

### Cache
We use [Realm.io](https://realm.io/) on both platforms (iOS and Android) to store the offline data of files, shares, app and user preferences (in different databases instances). [Android Room](https://developer.android.com/training/data-storage/room) is used to store API access token and basic user data.

### Structure
The structure of the app, its algorithms and the general functioning are common with the iOS app. 



## Tests

In order to test the app with Unit and UI tests, you have to copy `Env-Example` class in AndroidTest package and name it `Env`.\
⚠️ Don't forget to disable 2FA on your Infomaniak account if you want to execute tests, this feature is not supported for AddUser test.\
Replace values contained in file by yours and launch the tests 👍
