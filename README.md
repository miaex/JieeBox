# JIEE BOX — V1

Serveur de fichiers local portable pour Android. Transforme un téléphone en
petit serveur HTTP accessible via son hotspot Wi-Fi — aucun internet, aucun
compte, aucun cloud.

## Comment compiler l'APK

1. Ouvrez ce dossier (`JieeBox/`) directement dans **Android Studio** (Koala ou
   plus récent) via *File → Open*.
2. Laissez Gradle synchroniser (il télécharge les dépendances listées dans
   `app/build.gradle.kts`, notamment `nanohttpd` et Jetpack Compose — une
   connexion internet est nécessaire **une seule fois**, pour Android Studio
   lui-même, pas pour l'app une fois installée).
3. Branchez un téléphone Android réel (le hotspot Wi-Fi et les gros transferts
   se testent mal sur un émulateur) avec le débogage USB activé.
4. *Run → Run 'app'*, ou générez l'APK directement avec :
   ```
   ./gradlew assembleDebug
   ```
   L'APK se trouve ensuite dans `app/build/outputs/apk/debug/app-debug.apk`.
5. Pour un APK signé/release : *Build → Generate Signed Bundle / APK*.

## Compiler l'APK sans PC (depuis un téléphone)

Ce dépôt inclut `.github/workflows/build.yml` : GitHub peut compiler l'APK à
ta place dans le cloud, sans Android Studio ni PC.

1. Crée un compte GitHub si besoin (gratuit) — via le navigateur du
   téléphone.
2. Crée un nouveau dépôt (bouton "+" → *New repository*), nom libre, par
   exemple `jiee-box`.
3. Sur la page du dépôt vide, utilise *"uploading an existing file"* (lien
   proposé automatiquement) pour envoyer le contenu de ce dossier. Sur
   mobile, l'upload multi-fichiers avec structure de dossiers est parfois
   limité selon le navigateur — si ça bloque, la solution la plus fiable
   depuis un téléphone est une application comme **Working Copy** (iOS) ou
   **MGit** (Android), ou l'appli officielle **GitHub** qui permet de
   créer/éditer des fichiers un par un. À défaut, dès que tu as accès à
   n'importe quel ordinateur (même emprunté 10 minutes),
   `git init && git add . && git commit -m "v1" && git push` suffit.
4. Une fois les fichiers poussés sur la branche `main`, va dans l'onglet
   **Actions** du dépôt. Le workflow "Build APK" se lance automatiquement
   (ou clique sur *Run workflow* pour le lancer à la main).
5. Attends la fin du build (quelques minutes). Ouvre le run terminé, section
   **Artifacts** en bas de page : télécharge `JieeBox-debug-apk` (un `.zip`
   contenant l'APK).
6. Sur ton téléphone Android : dézippe si besoin, ouvre le `.apk`, autorise
   "sources inconnues" si demandé, installe.

Aucun Android Studio, aucun PC, aucune ligne de commande locale n'est
nécessaire avec cette méthode — tout tourne sur les serveurs de GitHub.

## Utilisation

1. Lancez JIEE BOX, appuyez sur **+ Fichiers** ou **+ Dossier** et choisissez
   ce que vous voulez partager.
2. Activez le hotspot Wi-Fi du téléphone (Paramètres rapides → Point d'accès
   Wi-Fi) — l'app ne l'active pas elle-même (voir limitation ci-dessous).
3. Appuyez sur **DÉMARRER LA BOX**. L'adresse (ex. `http://192.168.43.1:8080`)
   s'affiche ; appuyez dessus pour la copier.
4. Sur l'autre appareil : connectez-vous au hotspot, ouvrez Chrome/Safari,
   collez l'adresse. La liste des fichiers apparaît, chacun avec un bouton
   Télécharger.

## Architecture

```
com.jiee.box
├── MainActivity.kt          UI, sélecteurs de fichiers SAF, permissions
├── JieeBoxApplication.kt    instance partagée du FileRepository
├── data/
│   ├── PublishedFile.kt     modèle + formatage des tailles
│   └── FileRepository.kt    liste publiée, persistance, permissions SAF
├── network/
│   └── NetworkUtils.kt      détection de l'IP locale (hotspot)
├── server/
│   ├── JieeHttpServer.kt    serveur HTTP (NanoHTTPD), streaming + Range
│   └── WebUi.kt             page HTML servie aux clients
├── service/
│   └── BoxService.kt        service au premier plan hébergeant le serveur
└── ui/
    ├── BoxViewModel.kt
    ├── HomeScreen.kt        écran principal (Compose)
    └── theme/                thème Material3
```

Aucun fichier n'est jamais copié : `FileRepository` ne stocke que des URIs SAF
(`content://...`) avec permission persistée, et `JieeHttpServer` les diffuse
directement depuis le stockage d'origine vers le réseau, en streaming.

## Limitations Android et choix effectués (spec section 22)

- **Hotspot activé manuellement.** Android ne permet pas à une app tierce
  d'activer par programme le point d'accès Wi-Fi sans une action explicite de
  l'utilisateur (restriction volontaire depuis Android 8, pour éviter les
  abus). JIEE BOX détecte l'interface une fois qu'elle est active, mais ne
  peut pas l'allumer elle-même. Alternative : un bouton qui ouvre directement
  l'écran système du point d'accès (`Settings.ACTION_WIFI_TETHER_SETTING`,
  non garanti sur tous les constructeurs).
- **IP du hotspot.** Il n'existe pas d'API publique fiable et universelle pour
  "l'IP du hotspot" (les API DHCP de `WifiManager` sont dépréciées/restreintes
  depuis Android 8+, et leur comportement varie selon les marques). La
  solution retenue — énumérer les interfaces réseau et repérer celle nommée
  `ap0`/`wlan0`/`swlan0` — est l'approche la plus fiable en pratique, mais
  reste une heuristique : sur un appareil très inhabituel, l'IP affichée
  pourrait être incorrecte.
- **Nombre d'appareils connectés.** HTTP est sans état ; il n'y a pas de
  notion native de "connexion active". Le compteur affiché est une estimation
  basée sur les adresses IP ayant fait une requête dans les 60 dernières
  secondes — pertinent pour l'usage visé, mais pas un vrai suivi de session.
- **Service au premier plan obligatoire.** Sans lui, Android tue ou limite
  fortement les sockets réseau en arrière-plan (Doze / App Standby), ce qui
  casserait des téléchargements de plusieurs Go. Cela impose une notification
  persistante "BOX active" pendant que le serveur tourne — c'est voulu et
  nécessaire pour la fiabilité (section 19 du cahier des charges).
- **Permissions SAF persistantes.** Elles peuvent être révoquées par le
  système (rarement) ou si l'utilisateur efface les données de l'app / du
  fournisseur de stockage. `FileRepository.refreshAvailability()` détecte ce
  cas au démarrage et marque le fichier "indisponible" plutôt que de planter
  (conforme à la section 14).
- **QR code (section 16) :** non implémenté en V1 pour rester minimal, comme
  autorisé par le cahier des charges ("V1 ou V1.1"). Le bouton "Copier
  l'adresse" couvre le besoin immédiat ; un QR code (ex. via ZXing) est un
  ajout simple pour V1.1.
- **Reprise de téléchargement / téléchargements multiples en un clic
  (sections 10-11, V1.1) :** le serveur gère déjà les requêtes `Range` par
  fichier (donc pause/reprise fonctionne déjà dans Chrome pour un fichier
  donné) ; le multi-sélection et le zip de dossier restent prévus pour V1.1.

## Prochaines étapes suggérées (V1.1 / V1.2, cf. cahier des charges §20)

QR code, sélection/téléchargement multiple, tri et recherche côté web,
mot de passe optionnel, upload vers la box, nom personnalisé de la box.
