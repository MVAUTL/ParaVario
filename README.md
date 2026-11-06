# ParaVario

Prototype de suivi de vol en parapente pour Wear OS, testé sur Galaxy Watch5 Pro.

- Vario filtré, altitude barométrique standard et vitesse sol GPS.
- Pression, altitude GPS et accélération linéaire dans les détails.
- Journal CSV local à une ligne par seconde.
- L’écran reste allumé en cours de session. Quitter l’application arrête l’enregistrement.

## Installation

Télécharger `ParaVario.apk` depuis les [versions GitHub](https://github.com/MVAUTL/ParaVario/releases/latest), puis installer avec `adb install -r ParaVario.apk` après avoir associé la montre.

## Publication

Chaque envoi sur `main` déclenche la compilation, la signature et la publication de l’APK dans GitHub Releases. Le secret Actions `PARAVARIO_KEYSTORE` contient la clé de signature encodée en Base64. Réutiliser la même clé permet de mettre à jour l’application installée. L’installation sur la montre se fait manuellement.
