# Prompt à donner à Claude — refonte du monstre principal

Tu travailles dans le projet THE INTERSTICE, un mod Minecraft Java 1.21.1 utilisant Fabric, Java 21 et Veil. Ta mission concerne exclusivement la créature principale, l’Adhérent : son modèle, ses textures, son squelette, ses animations et leur intégration dans le rendu existant. Ne transforme pas cette demande en une refonte du monde ou des événements.

## Résultat attendu

Repars de zéro sur la direction visuelle du monstre. Le modèle actuel a été rejeté : ses membres ressemblent à des baguettes rectangulaires, ses articulations à des pièces assemblées, son thorax à une cage et sa tête à un crâne cartoon. Le problème est la construction des volumes et la silhouette. Ajouter des cubes ou des dents ne le résoudra pas.

Je veux un animal impossible, lourd et inquiétant, capable de ramper au sol et de s’accrocher aux murs gigantesques de la dimension. Il doit sembler vivant et physiquement cohérent avant de sembler spectaculaire. Les quatre références jointes représentent une seule espèce. Utilise-les comme guide de proportions, de masses, de matériaux et d’appuis, sans prétendre qu’une image constitue un modèle ou une animation déjà réalisés.

## Comment utiliser les quatre références

1. Face trois-quarts : largeur des épaules, position de la tête, masse du thorax et continuité de la peau.
2. Profil : courbure du dos, longueur des membres, position du bassin et contacts au sol.
3. Escalade : orientation du corps et des mains sur une surface verticale, silhouette vue de dos.
4. Redressement : impression de taille, transfert de poids vers les pattes arrière et menace silencieuse.

Si une image contient un détail anatomique contradictoire, conserve un squelette unique cohérent entre les quatre vues. Ne change jamais le nombre de membres selon l’animation.

## Anatomie et silhouette

- Quatre membres : deux bras porteurs et deux pattes arrière. Pas de bras supplémentaires.
- Épaules larges et lourdes ; omoplates saillantes sous une peau continue. Le thorax concentre la masse.
- Dos voûté, abdomen creusé et bassin plus étroit. Évite le long tube horizontal uniforme.
- Très longs avant-bras, coudes lisibles et connectés par des volumes de chair. Les articulations ne sont pas des billes décoratives.
- Mains porteuses : trois longs doigts principaux, un pouce plus court et des ongles sombres recourbés. À distance, la main doit rester lisible.
- Pattes arrière digitigrades, jarret élevé, cuisse assez solide pour porter le redressement. Ne confonds pas jarret et genou en cassant arbitrairement les articulations.
- Tête relativement petite et cunéiforme, presque enfouie entre les épaules. Cou épais et tendu plutôt que cou de girafe segmenté.
- Aucun regard humain lisible : pas de gros yeux ronds brillants ni de sourire de squelette. Le visage peut avoir des replis qui masquent les yeux et une bouche étroite située dessous.
- Bouche discrète au repos, révélée seulement lors d’une menace. Les dents sont secondaires ; la silhouette doit fonctionner bouche fermée.
- Taille cible à ajuster aux références : environ 3 m de long et 1,6 m aux épaules en déplacement bas, jusqu’à 3,4 m en redressement. Vérifie ces dimensions avec un repère de joueur de 1,8 bloc.

Le monstre doit rester identifiable en silhouette noire, de face, de profil et sur un mur. Évite les longues pointes qui ressemblent à des oreilles de lapin, les bois, les cornes gratuites et les doigts si fins qu’ils disparaissent à cinq mètres.

## Construction du modèle

Privilégie un vrai maillage low-poly organique : sections effilées, faces obliques, transitions de largeur et articulations continues. Ne remplace pas un cylindre par une pile de vingt cubes. Utilise la géométrie pour les grandes formes et la texture pour les détails de peau, les tendons et les côtes.

Commence par inspecter les capacités réelles des outils et du rendu. Blockbench en format Generic peut servir de source de maillage ; le convertisseur actuel du projet est orienté cubes et ne doit pas être présenté comme compatible avec des maillages arbitraires. Si le rendu nécessite une nouvelle voie d’export, implémente une voie réellement prise en charge après vérification des API locales. N’invente aucune capacité de Veil, aucun chargeur glTF et aucune compatibilité GeckoLib.

Si tu utilises des segments de maillage rigides attachés aux os, fais chevaucher proprement les volumes aux articulations pour éviter les trous. Si tu utilises des poids de skinning, vérifie que le rendu les supporte et teste les déformations. Une subdivision visuelle dans l’éditeur ne garantit pas qu’elle existe dans le jeu.

Conserve une source modifiable, un export reproductible et des noms d’os stables. Ne livre pas uniquement du Java généré. Ne réutilise pas aveuglément l’ancien générateur de cubes.

## Matières et textures

Peau gris cendre, légèrement chaude dans les plis, avec des variations discrètes. Thorax tendu, zones épaissies aux appuis, quelques zones luisantes autour de la bouche. Pas de rouge saturé partout, pas de blanc osseux uniforme, pas de grandes taches noires peintes pour simuler des volumes absents.

Choisis une résolution cohérente avec la distance de vue et les performances ; une base couleur de 512 × 512 constitue un point de départ raisonnable, pas une obligation. Prévois des UV propres et des détails lisibles sans rendre le monstre photoréaliste au milieu des blocs. N’annonce pas de normal maps, de rugosité ou de shaders de peau si ces canaux ne sont pas réellement utilisés par le rendu.

## Animations indispensables

Construis un squelette avant les détails. Prévois bassin, colonne, thorax, cou, tête, mâchoire, épaules, bras, avant-bras, poignets, doigts, cuisses, jambes et pieds. N’anime pas tout le corps en un seul bloc.

1. Repos : respiration lente du thorax, déplacements infimes de poids, tête presque immobile. Pas de balancement de jouet en boucle courte.
2. Observation : arrêt du corps, puis orientation retardée du cou et de la tête. Une brève immobilité vaut mieux que des tremblements permanents.
3. Marche lente : anticipation de la main, ouverture des doigts, contact, compression du bras, passage du poids, puis relâchement.
4. Course : poussée lisible des pattes arrière, réception des bras et mouvement secondaire du thorax. Adapte la fréquence à la distance parcourue pour limiter le patinage.
5. Escalade : appuis sur le véritable mur, mains orientées vers la surface, corps retenu par ses membres. Une simple rotation d’un animal qui marche au sol ne suffit pas.
6. Redressement : pieds arrière ancrés, bassin qui se place au-dessus des appuis, thorax qui se lève, bras qui cessent progressivement de porter.
7. Menace : ouverture progressive de la bouche et tension du cou. Évite la bouche béante pendant toutes les actions.
8. Chute : perte d’appui, recherche de prise, inertie puis impact. Pas de lévitation ni de retour instantané à la pose neutre.
9. Récupération : absorption du choc, appui d’une main, remise en place des membres puis reprise de la recherche.

Les animations doivent être éditables dans l’outil choisi et jouées effectivement par le mod. L’IK peut être précalculée, mais il faut vérifier les positions interpolées entre les clés et les transitions entre animations. Une erreur faible sur les seules clés ne prouve pas que les pieds restent au sol en mouvement.

Teste les cycles à vitesse réelle et au ralenti. Pendant un appui, le point de contact reste fixe dans le monde ; pendant un pas, le pied se lève intentionnellement. Vérifie les glissements, les pénétrations du sol, les changements de sens des genoux et les doigts qui traversent les poignets.

## Intégration et performances

Préserve les comportements serveur : observation sur les murs, chute, perception et recherche au sol. Adapte dimensions et collisions à la nouvelle taille, puis vérifie les abris, les passages et la navigation. Le rendu reste côté client.

Le monstre proche peut utiliser un modèle détaillé. La horde doit conserver des représentations plus simples : ne dessine pas des milliers de copies du modèle complet. Prévois réduction de détail, limitation de l’animation et culling. Donne les mesures observées et évite de promettre un nombre de FPS universel.

## Méthode de validation

Avance sans demander l’approbation pour chaque os. Crée une sauvegarde de l’ancienne version puis travaille sur cette refonte. Inspecte les outils disponibles et utilise réellement Blockbench si la connexion fonctionne ; si elle échoue, indique exactement ce qui a été fabriqué hors de l’éditeur.

Valide d’abord les masses sur un fond neutre et avec une lumière claire. Affiche une référence de taille de joueur. Ensuite, vérifie dans la dimension avec sa lumière et son brouillard. Le brouillard ne doit pas masquer les défauts de construction.

Produis face, profil, trois-quarts, vue sur un mur et une courte vidéo ou capture animée des déplacements. Des captures fixes ne suffisent pas pour affirmer que l’animation est correcte. Compile et lance les tests pertinents après intégration.

## Livraison

Fournis le modèle source, les textures, les animations, l’export utilisé par le jeu, la procédure de régénération et les preuves visuelles. Distingue ce qui a été compilé, testé automatiquement, observé dans le jeu et réellement observé en mouvement. Indique les défauts restants.

Critère de réussite : un joueur distingue une masse vivante, lourde, organique et anormalement proportionnée qui se déplace avec des appuis convaincants. Les épaules, le visage et les mains doivent être reconnaissables sans compter les dents. Le modèle ne doit plus ressembler à une collection de cubes reliés par des baguettes.
