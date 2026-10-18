# Connexion Microsoft de LarpClient

LarpClient installe Minecraft et Java et prépare le lancement sans launcher officiel. L’identifiant public de l’application Microsoft LarpClient fourni par son créateur est maintenant intégré dans l’exécutable. Les joueurs peuvent ouvrir **Réglages → Se connecter à Microsoft** sans renseigner de configuration.

La procédure ci-dessous permet de remplacer cette application par une autre application appartenant au développeur du launcher. L’inscription Entra seule ne confirme pas que l’application est autorisée par l’API Minecraft ; la connexion réelle doit encore être vérifiée avec un compte Java.

1. Enregistrer **LarpClient** dans Microsoft Entra, avec prise en charge des comptes Microsoft personnels.
2. Ajouter la plateforme **Applications mobiles et de bureau** et la redirection `http://localhost`. LarpClient utilise MSAL, le navigateur système et le retour local pour l’authentification. Aucun mot de passe n’est saisi dans LarpClient.
3. Faire autoriser l’identifiant de cette application pour l’API Minecraft si nécessaire. Un identifiant enregistré mais non autorisé peut recevoir HTTP 403.
4. Créer `larpclient.auth.json` à côté de `LarpClient.exe`, ou dans `%APPDATA%\LarpClient`, avec le contenu suivant en remplaçant la valeur par l’identifiant public réel :

```json
{
  "clientId": "IDENTIFIANT-PUBLIC-DE-L-APPLICATION"
}
```

La variable d’environnement `LARPCLIENT_MICROSOFT_CLIENT_ID` a priorité sur ce fichier. Sans fichier externe ni variable, l’identifiant intégré à l’exécutable est utilisé. L’identifiant est public ; **aucun secret d’application ni mot de passe ne doit être ajouté**. Les joueurs n’ont pas à créer leur propre application ni à transporter un fichier de configuration avec l’exécutable.

Dans LarpClient, ouvrir **Réglages → Se connecter à Microsoft**. Le navigateur Microsoft effectue la connexion, puis LarpClient échange le jeton avec Xbox/XSTS et vérifie le profil Java. Le bouton Jouer effectue également cette connexion si nécessaire. Le cache MSAL est chiffré pour l’utilisateur Windows courant par DPAPI dans `microsoft-session.bin`. Le profil public est dans `java-profile.json` ; le jeton Minecraft reste en mémoire. Déconnexion retire le cache propre à LarpClient.

**Installer / vérifier** est utilisable sans connexion. Le lancement complet attend une connexion Java valide ; aucune session hors ligne factice n’est utilisée pour contourner la connexion.

## Refus Minecraft après inscription Entra

Créer une application dans Entra fournit un identifiant public, mais n’autorise pas automatiquement cet identifiant sur l’API Minecraft. La documentation CmlLib indique une étape séparée d’approbation. Le [formulaire AppID Review](https://forms.cloud.microsoft/Pages/ResponsePage.aspx?id=v4j5cvGGr0GRqy180BHbR-ajEQ1td1ROpz00KtS8Gd5UNVpPTkVLNFVROVQxNkdRMEtXVjNQQjdXVC4u), vérifié le 17 octobre 2026, demande l’e-mail de contact correspondant au portail Azure, le nom de l’application, son Client ID, son Directory (tenant) ID, un lien public présentant le projet et une justification. Son premier champ demande au créateur de confirmer la lecture et la compréhension de l’EULA et des Usage Guidelines.

Le brouillon personnel est conservé uniquement dans `%APPDATA%\LarpClient\minecraft-access-request.md` lorsqu’il a été préparé. Aucun renseignement de contact n’est intégré à l’exécutable ni au dépôt source. Le formulaire n’a pas été envoyé automatiquement.

Le launcher distingue maintenant les refus de l’application des refus de compte Xbox ou de profil Java. Un HTTP 403 seul ne prouve pas l’absence d’approbation. Le message spécifique « autorisation Minecraft requise » est utilisé seulement si le service d’authentification renvoie explicitement « Invalid app registration ». Un diagnostic contenant uniquement l’étape, le type de refus, le statut HTTP et la date est enregistré dans `%APPDATA%\LarpClient\authentication-diagnostic.json`. Il n’inclut ni jetons, ni profil du joueur, ni corps des réponses.

Le bouton **Autorisation Minecraft** ouvre le formulaire pour lecture et validation par le créateur. L’approbation appartient à l’équipe Minecraft ; aucune modification locale du Client ID ou de la redirection ne remplace cette décision.

Sources de configuration : [MSAL et le navigateur système](https://learn.microsoft.com/en-us/entra/msal/dotnet/acquiring-tokens/using-web-browsers), [enregistrement du Client ID pour Minecraft](https://cmllib.github.io/CmlLib.Core-wiki/en/auth.microsoft/xboxauthnet.game.msal/clientid/).
