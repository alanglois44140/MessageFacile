# MessageFacile — application Android

Application Android locale de gestion de contacts et d'envoi direct de SMS individuels ou groupés.

## Fonctionnalités
- Ajout, modification et suppression de contacts (prénom, nom, téléphone).
- Import de contacts depuis un fichier CSV, avec reconnaissance des colonnes `prenom`, `nom` et `telephone` même si leur ordre change.
- Création, modification et suppression de modèles SMS.
- Variables : `{prenom}`, `{nom}`, `{nom_complet}`.
- Sélection d'un ou plusieurs contacts et envoi direct en un seul appui.
- Chaque destinataire reçoit un SMS individuel, avec son prénom/nom remplacé dans le modèle.
- Pas d'historique ni de compte rendu des envois.
- Personnalisation du nom, du symbole ou de l'image de logo affichée dans l'application, et de la couleur (palette ou code hexadécimal).
- Données conservées localement sur le téléphone ; aucun compte ni serveur.

## Import CSV
Le format le plus simple est un fichier CSV avec trois colonnes dans cet ordre : `prenom,nom,telephone`.

```csv
prenom,nom,telephone
Camille,Martin,0612345678
Alex,Durand,+33612345678
```

Les séparateurs virgule, point-virgule et tabulation sont reconnus. Les champs entre guillemets sont acceptés. Dans Excel, conserver la colonne téléphone au format **Texte** pour préserver le zéro initial.

## Construction de l'APK
Le projet contient un workflow GitHub Actions (`.github/workflows/build-apk.yml`) qui construit automatiquement l'APK de débogage sur un serveur de compilation. Le nom affiché sous l'icône est `MessageFacile`.

Le fichier APK n'est pas inclus dans cette archive : il doit être produit par une compilation Android. Le workflow est prêt, mais il faut d'abord placer le contenu de ce dossier dans un dépôt GitHub, puis ouvrir **Actions**, sélectionner **Construire MessageFacile (APK)** et cliquer sur **Run workflow**. À la fin, télécharger l'artefact `MessageFacile-APK` contenant `app-debug.apk`.

Cette étape nécessite un compte GitHub et quelques manipulations dans son interface web ; aucune programmation n'est demandée. Ne pas installer un APK provenant d'une autre source.

## Installation et permissions
- L'application demande l'autorisation Android `Envoyer des SMS` au premier envoi.
- Un téléphone avec carte SIM/ligne active et service SMS est nécessaire.
- L'envoi groupé déclenche un SMS distinct par contact sélectionné ; le coût éventuel dépend du forfait.
- L'envoi est déclenché en un seul appui sur le bouton. Il n'y a pas d'historique ni de retour applicatif sur la livraison.
- Les SMS peuvent ne pas être acheminés si le réseau ou l'opérateur rencontre un problème.
- Les contacts et modèles restent dans les données privées de l'application. Ils sont supprimés si l'application est désinstallée ou si ses données sont effacées.

## Limite de personnalisation
Le nom de l'application est `MessageFacile`. Le logo choisi et la couleur sont personnalisables dans l'interface. L'icône système affichée sur l'écran d'accueil Android est intégrée à l'APK et ne change pas lorsque le logo interne est modifié.
