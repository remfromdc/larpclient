# LarpClient

Launcher Windows C# / WPF (.NET 10) pour **1.8.9**, **1.21.11** et **26.2**. Fond noir profond, panneaux et contours gris discrets, neige douce, menus animés, instances et aperçu du skin Java. Les réglages, formulaires et fenêtres de message partagent la même palette monochrome.

## Installer et jouer

Choisir une instance, puis **JOUER**. LarpClient vérifie et télécharge les ressources manquantes auprès des serveurs Mojang : version, bibliothèques, assets et runtime Java approprié. Il lance ensuite directement le processus Java avec la session Microsoft du joueur. Aucun launcher officiel n’est ouvert et aucun fichier `launcher_profiles*.json` n’est créé ou modifié. **Installer / vérifier** permet de préparer une version sans se connecter. Une installation peut être annulée puis reprise ; les fichiers déjà présents sont contrôlés par empreinte et réutilisés.

**L’identifiant public Microsoft propre à LarpClient est intégré dans l’exécutable.** Ouvrir **Réglages → Se connecter à Microsoft**. Voir [MICROSOFT-SETUP.md](MICROSOFT-SETUP.md) pour le remplacer. La connexion s’effectue dans le navigateur Microsoft puis revient à LarpClient ; elle ne passe jamais par le launcher Minecraft. Le cache MSAL propre à LarpClient est chiffré par DPAPI pour l’utilisateur Windows courant. Aucun mot de passe n’est demandé dans l’interface, aucun jeton du launcher officiel n’est lu. La connexion réelle et l’accès à l’API Minecraft restent à valider avec le compte du joueur.

Les ressources sont partagées avec `%APPDATA%\.minecraft`. Les parties, réglages, mods et natives sont isolés dans `%APPDATA%\LarpClient\instances\[version]` et un sous-dossier unique pour les instances personnalisées. Les mondes et packs existants ne sont pas copiés automatiquement. Le fichier `launcher.json` conserve les instances et préférences. Les téléchargements simultanés entre deux fenêtres LarpClient sont verrouillés.

## Aperçu et animations

Le personnage utilise désormais **OpenGL 3.3 sur la carte graphique**, intégré à WPF par OpenTK.GLWpfControl. Les modèles sont construits une fois puis conservés en buffers GPU : peau classique/slim/64 × 32, articulations, profondeur et couches extérieures. Le **nametag est un maillage volumique**, composé de solides fermés avec devant, dos, côtés et ombre. Il partage la rotation complète du skin sur 360°, y compris à la souris. Son profil devient naturellement étroit ; son dos est visible comme celui d’un véritable objet. Le pseudo fixe et le statut permanent restent retirés. La police Minecraft est lue depuis le JAR 1.8.9 local, sans embarquer cet asset ; une police monospace sert de secours.

Le personnage entre en marchant, tourne, traverse la scène en courant et revient. La période de repos lui laisse le temps d’effectuer un tour complet. Le glisser permet de l’orienter ; un double-clic rejoue la course. L’anticrénelage MSAA 4× stabilise les contours à la résolution réelle de la fenêtre. Le flou GPU additionne trois poses pendant les mouvements rapides, avec buffers réutilisés ; il reste absent au repos. Les animations suivent **chaque image du compositeur**, sans quantification à 90 Hz. Les boucles s’arrêtent lorsque la fenêtre est inactive ou réduite, puis reprennent sans rattrapage. Les réglages permettent d’arrêter les animations, la rotation ou le flou. Nunito est intégrée sous licence OFL.

Un rendu CPU utilisant les mêmes solides pour le nametag sert de secours si l’initialisation GPU échoue. `--cpu-character` le force pour diagnostic. La fenêtre active testée sur RTX 4060 Ti a atteint environ 160–165 rendus/s après démarrage ; cette mesure n’est pas une garantie du nombre d’images présentées par le moniteur. Les redimensionnements n’allouent plus les buffers CPU quand le GPU est actif.

Le profil public mémorisé par LarpClient est prioritaire. Au premier démarrage seulement, un profil public déjà présent peut servir d’aperçu sans utiliser sa session. Après Déconnexion, cet aperçu n’est pas restauré automatiquement. L’import PNG modifie uniquement l’aperçu local. Le skin public est téléchargé depuis les serveurs Mojang.

Les refus de connexion s’affichent dans un panneau sombre. Le launcher identifie séparément un refus d’application Minecraft explicite, un refus de profil Java et un refus Xbox. Le diagnostic local ne contient que l’étape, le type de refus, le statut HTTP et la date. La création de l’application Entra ne remplace pas l’approbation de son identifiant par Minecraft ; voir les liens et la procédure dans MICROSOFT-SETUP.md. La liste des instances conserve son fond sombre lorsqu’elle est temporairement désactivée pendant la connexion.

## Compiler

```powershell
dotnet run --project launcher/LarpClient.csproj
dotnet publish launcher/LarpClient.csproj -c Release -r win-x64 --self-contained true -p:PublishSingleFile=true -p:IncludeNativeLibrariesForSelfExtract=true -p:EnableCompressionInSingleFile=true -o launcher/dist
```

L’exécutable embarque l’identifiant public de l’application depuis la ressource `larpclient.auth.json`. Un fichier externe de ce nom peut le remplacer si nécessaire. Distribuer également `MICROSOFT-SETUP.md`, `THIRD-PARTY-NOTICES.txt` et la licence des polices. CmlLib.Core gère l’installation et la préparation du processus ; MSAL gère OAuth. Le code de connexion Xbox/Minecraft ne journalise ni réponses ni lignes de commande.

## Vérifications

```powershell
dotnet run --project launcher-checks/LarpClient.Checks.csproj -c Release
dotnet run --project launcher-ui-checks/LarpClient.UiChecks.csproj -c Release
dotnet run --project launcher-integration-checks/LarpClient.IntegrationChecks.csproj -c Release
dotnet run --project launcher-gpu-checks/LarpClient.GpuChecks.csproj -c Release
dotnet run --project launcher-gpu-checks/LarpClient.GpuChecks.csproj -c Release -- --window
# Tests réseau facultatifs : téléchargement réel dans un dossier temporaire,
# puis vérification des trois versions dans le .minecraft existant.
dotnet run --project launcher-integration-checks/LarpClient.IntegrationChecks.csproj -c Release -- --fresh-install --installed-versions
```

Les fixtures d’authentification ne lancent jamais le jeu avec de faux identifiants. Le test d’installation vierge vérifie le téléchargement, Java, la réparation d’un JAR endommagé et la préparation du processus. Une connexion et une partie réelles restent à valider après configuration du Client ID.

`--measure-animation` mesure dix secondes actives ; `--profile-session` conserve au maximum 60 échantillons dans `animation-metrics.json`, avec le périphérique GPU et le 95e percentile des intervalles entre rendus. Le temps de soumission GPU ne mesure pas le temps d’exécution GPU. `--software-render` force le secours WPF logiciel pour un diagnostic. Une simulation de six heures vérifie la stabilité des transformations, sans remplacer une session réelle de cette durée.

## Mod

Le lancement est actuellement **vanilla**. Le code du mod Fabric se trouve dans `src/` à la racine du dépôt ; voir [sa documentation](../docs/MOD.md). Son intégration au launcher et le choix de son chargeur restent à réaliser. Le dossier `mods` est réservé à cette intégration. Aucun gain de FPS en jeu n’est annoncé ni mesuré.
