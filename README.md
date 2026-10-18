# LarpClient

**LarpClient** est un launcher Windows indépendant pour **Minecraft: Java Edition**, avec une interface en français, un thème gris/noir/blanc, une scène de skin 3D et des instances séparées.

Versions proposées : **1.8.9 · 1.21.11 · 26.2**.

LarpClient is an independent Windows launcher for Minecraft: Java Edition. It installs official game files and the Java runtime, authenticates players through Microsoft in their system browser, and starts Java directly. A valid Java Edition account is required to play.

## Le launcher

- Installation et vérification des versions, bibliothèques, ressources et du runtime Java depuis les sources officielles.
- Réutilisation des ressources présentes dans `.minecraft`, avec dossiers de parties et de réglages propres à chaque instance.
- Connexion Microsoft avec MSAL, puis vérification Xbox / XSTS / Minecraft du profil Java.
- Aucun mot de passe demandé par LarpClient ; cache de session propre au launcher, chiffré avec Windows DPAPI.
- Skin et nametag volumétriques en OpenGL 3.3, rotation sur 360°, marche/course, flou de mouvement et neige en arrière-plan.
- Animations suspendues lorsque la fenêtre est inactive ou réduite.

Le jeu se prépare et se lance depuis LarpClient sans ouvrir le launcher officiel.

**État du projet : en développement.** L'application Microsoft est enregistrée ; son accès à l'API Minecraft et une connexion réelle restent à valider. Un refus HTTP 403 a été rencontré : il ne suffit pas, à lui seul, à en établir la cause. Le launcher exige une session authentifiée et ne propose pas de contournement hors ligne. Le lancement actuel prépare Minecraft vanilla ; l'intégration du mod est encore à réaliser. Aucun gain de FPS en jeu n'est annoncé.

## Compiler le launcher

Prérequis : Windows et SDK .NET 10.

```powershell
dotnet run --project launcher/LarpClient.csproj

dotnet publish launcher/LarpClient.csproj -c Release -r win-x64 --self-contained true -p:PublishSingleFile=true -p:IncludeNativeLibrariesForSelfExtract=true -p:EnableCompressionInSingleFile=true -o launcher/dist
```

La publication produit une application Windows x64 autonome. Conserver les notices et licences des dépendances lors de sa distribution.

## Organisation

| Dossier | Contenu |
| --- | --- |
| `launcher/` | Launcher Windows WPF / .NET 10, installation et connexion |
| `launcher-checks/` | Vérifications du modèle et des animations |
| `launcher-ui-checks/` | Vérifications de la fenêtre et des styles WPF |
| `launcher-integration-checks/` | Vérifications de l'installation et des échanges d'authentification simulés |
| `launcher-gpu-checks/` | Vérifications du rendu OpenGL |
| `src/`, `gradle/` | Mod Fabric 26.2, projet distinct du launcher |

Voir le [guide du launcher](launcher/README.md), la [configuration Microsoft](launcher/MICROSOFT-SETUP.md), les [résultats de validation](launcher/VALIDATION.md) et la [documentation du mod](docs/MOD.md).

Les vérifications disponibles s'exécutent sur Windows :

```powershell
dotnet run --project launcher-checks/LarpClient.Checks.csproj -c Release
dotnet run --project launcher-ui-checks/LarpClient.UiChecks.csproj -c Release
dotnet run --project launcher-integration-checks/LarpClient.IntegrationChecks.csproj -c Release
dotnet run --project launcher-gpu-checks/LarpClient.GpuChecks.csproj -c Release
```

L'identifiant public de l'application Microsoft est inclus dans `launcher/larpclient.auth.json`. Ce fichier ne contient aucun secret. Les sessions, profils personnels, coordonnées de contact, fichiers de jeu, caches et exécutables générés sont exclus du dépôt.

## Crédits

Le mod est dérivé du projet [Adin](https://github.com/kolfish/adin), distribué sous CC0 ; la [licence existante](LICENSE) est conservée. Les dépendances du launcher et les polices conservent leurs propres licences : voir [THIRD-PARTY-NOTICES.txt](launcher/THIRD-PARTY-NOTICES.txt) et [OFL.txt](launcher/Assets/Fonts/OFL.txt).

LarpClient est un projet indépendant, sans affiliation avec Mojang ou Microsoft. Minecraft: Java Edition appartient à ses ayants droit ; ses fichiers de jeu ne sont pas redistribués dans ce dépôt.
