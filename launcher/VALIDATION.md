# Validation du 17 octobre 2026

## Gestion du refus de connexion

- Refus d’application identifié uniquement sur l’endpoint de connexion Minecraft lorsque la réponse indique explicitement « Invalid app registration » ; les refus de profil Java et de compte Xbox restent distincts.
- Fixtures HTTP : application non approuvée, profil Java refusé, refus Xbox, absence de profil Java et annulation vérifiés. Diagnostics sans données de réponse, jetons ni identité du joueur.
- Erreurs affichées dans un panneau sombre intégré ; l’opération se termine avant que l’utilisateur ferme le panneau. Les boutons sont alors réactivés.
- Liste des instances : template explicite conservant le fond sombre pendant la désactivation liée à la connexion.
- Brouillon d’accès à Minecraft conservé localement avec le contact et le Tenant ID fournis ; le lien public reste à compléter. Aucun formulaire n’a été envoyé.
- La vérification silencieuse de la session locale n’a pas pu restaurer une connexion ; aucun navigateur n’a été ouvert et aucun succès Minecraft réel n’est annoncé.

## Nouveau moteur GPU et modèle 360°

- Maillages persistants OpenGL 3.3, anticrénelage MSAA 4× ; plus de rasterisation CPU / copie WriteableBitmap du skin dans le chemin normal.
- Lettres volumétriques fermées, surfaces avant/arrière/latérales et ombre. Le tag partage le yaw intégral du personnage ; l’ancien angle limité à ±13° a été supprimé.
- Tests GPU réels sur NVIDIA GeForce RTX 4060 Ti : neuf angles de 0° à 360°, profil étroit, dos visible, retour identique après un tour, absence d’erreur OpenGL et buffers du flou stables.
- Flou GPU testé avec trois expositions de poses interpolées ; actif seulement pendant les déplacements rapides.
- Cadence pilotée par chaque image distincte du compositeur, sans filtrage irrégulier à 90 Hz. Suppression également de la limite des storyboards.
- Fenêtre réelle : environ 160–165 rendus/s en phase stable et environ 6,6 ms au 95e percentile sur un relevé stable. Démarrage, capture d’écran, changement de fenêtre et interaction peuvent réduire ces valeurs ; elles ne comptent pas les images effectivement présentées au moniteur.
- Rotation manuelle vérifiée visuellement. Mise en pause des deux boucles vérifiée dans le diagnostic lorsque la fenêtre est réduite ; reprise observée sans saut.
- Tests CPU de secours, modèles classique/slim/ancien et simulation de six heures passés après le changement.
- Dernier contrôle de la fenêtre WPF réelle : **162,3 rendus/s**, intervalle p95 **6,85 ms**, puis fermeture réussie avec libération GPU et désabonnement du compositeur.

Les relevés ci-dessous concernent les étapes précédentes du rendu CPU.

- Compilation Release et publication Windows x64 autonome réussies.
- Fenêtre et templates WPF chargés et rendus dans les contrôles de vérification.
- Moteur texturé : skins classiques, slim et anciens 64 × 32 vérifiés.
- Sur 1 200 rendus : environ 1,9 ms par image dans le contrôle local ; 40 octets d’allocations sur la boucle mesurée (chronomètre), buffers d’image réutilisés.
- Simulation de six heures à 90 pas/s : transformations finies, déplacements bornés, séquence marche/course/retour stable. Il s’agit d’une simulation, pas d’une session réelle de six heures.
- Fenêtre active, neige et flou activés, mode normal : environ 90,0 rendus du personnage terminés par seconde ; environ 1,6–2,0 ms de calcul du personnage. Cette mesure ne compte pas les images effectivement présentées par le moniteur.
- Fenêtre réduite : les deux abonnements de rendu deviennent inactifs ; position du personnage et temps de la neige inchangés entre deux relevés séparés. Les animations de l’interface sont également mises en pause.
- Restauration : reprise vérifiée visuellement depuis la pose suspendue, sans rattrapage du temps écoulé.
- Tests de préparation sans launcher officiel, isolation des instances et conservation des fichiers officiels réussis sur des dossiers temporaires.

Le titre est du texte simple. Le nametag est projeté dans la scène 3D, avec relief, plaque et ombre ; il suit les déplacements du personnage. Le pseudo fixe a été retiré. Le test de rendu inclut le nametag, ses déplacements et les buffers réutilisés (environ 2,1 ms/image, 40 octets sur 1 200 images lors de cette session). La police vient du Minecraft 1.8.9 local ; cet asset n’est pas embarqué.

## Installation et lancement directs

- Installation réelle de 1.8.9 depuis un dossier vide : JAR, bibliothèques, assets et Java téléchargés, sans configuration du launcher officiel.
- JAR volontairement endommagé uniquement dans cette fixture temporaire : nouvelle installation et empreinte finale identique à l’original.
- 1.8.9, 1.21.11 et 26.2 : ressources du .minecraft existant vérifiées, runtime présent et processus de lancement direct préparé avec dossier d’instance isolé, sans paramètres `${...}` non résolus.
- Échanges Microsoft → Xbox → XSTS → profil Java testés avec réponses HTTP simulées, refus HTTP 403, absence de profil Java et annulation. Les corps d’erreur contenant des données sensibles ne sont pas affichés.
- Sessions hors ligne factices refusées par le service de lancement.
- Fenêtre vérifiée visuellement : nametag intégré, retrait du pseudo séparé et du statut permanent, bouton Installer / vérifier, connexion Microsoft dans Réglages.

**Configuration Microsoft :** l’identifiant public fourni par le créateur de LarpClient est intégré en ressource de l’exécutable et enregistré dans la configuration locale. La présence et le contenu de la ressource ont été vérifiés après compilation ; compilation réussie sans erreur ni avertissement.

**Limite de validation :** la connexion interactive réelle, le rafraîchissement d’une session réelle et le lancement d’une partie avec un compte Java n’ont pas encore été validés. L’inscription de l’application Entra ne prouve pas son autorisation par l’API Minecraft. Aucun jeu n’a été démarré avec les identifiants de fixture. Voir MICROSOFT-SETUP.md.
