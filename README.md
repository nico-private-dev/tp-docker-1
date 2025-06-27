# TP – Déploiement automatisé d’une application dans une démarche DevOps

## 🎯 Objectifs opérationnels

L’objectif de ce TP est de **déployer une application de manière entièrement automatisée**, selon les principes de l’approche DevOps. Vous allez mettre en place une infrastructure légère mais complète, permettant :

- le développement et les tests dans un environnement isolé,
- l'intégration continue 
- l’analyse de code automatisée (SAST),
- et le déploiement sur un environnement distant simulé.

Le tout en conteneurs, via une approche **Infrastructure as Code** (IaC), avec **Docker Compose** comme orchestrateur local.

---

## ⚙️ Ce que nous allons construire

Nous allons déployer une infrastructure composée des services suivants :

### 1. 🖥️ `desktop` – Environnement graphique de pilotage

Un conteneur Linux complet, doté d’un environnement graphique **LXDE** accessible via VNC dans un navigateur.

- Accessible depuis la machine hôte à l’adresse : `http://localhost:80`
- Permet de piloter les autres conteneurs sans se heurter aux problèmes de `localhost` ou de `hostname` côté hôte.
- Utilisé comme **point d’entrée universel** pour l’ensemble de l’infrastructure (dev, tests, déploiement, monitoring).

### 2. 🧱 `plateforme` – Environnement de déploiement

Conteneur basé sur **Alpine Linux**, extrêmement léger.

- Contient **OpenSSH**, **Docker**, **Docker-Compose**, **Git**.
- Joue le rôle de **serveur cible de déploiement** pour les pipelines Jenkins.
- Simule un serveur distant dans une architecture plus large.

### 3. 🔧 `jenkins` – Serveur d’intégration continue

Instance locale de **Jenkins LTS**.

- Fournit l’interface d’orchestration CI/CD.
- Pilote les builds, tests et déploiements à partir de pipelines Jenkinsfile.
- Utilise les dépôts Git (auto-hébergés ou distants) comme source de code et d'infrastructure (`docker-compose.yaml`, etc.).

### 4. 🚀 `jenkins-docker-agent` – Agent Docker Jenkins

Un **agent Jenkins rooté** exécuté dans un conteneur Docker, capable de lancer d'autres conteneurs Docker grâce à l'accès au socket Docker de l'hôte (`/var/run/docker.sock`).

- Permet de builder des images Docker, lancer des tests automatisés, et déployer sur les environnements cibles (ex. : le conteneur `plateforme`).
- Utilise le plugin "Docker Pipeline" pour exécuter dynamiquement des étapes Docker dans les pipelines.
- Fonctionne en mode **inbound agent**, connecté automatiquement au Jenkins master via un `secret`.
- Rôle clé dans l'infrastructure CI/CD : **centralise l'exécution des builds Docker dans un environnement isolé mais privilégié**, sans compromettre l'isolation du maître Jenkins.
- Ce setup assure une séparation claire entre l'orchestration (master Jenkins) et l'exécution (agents), tout en gardant une capacité complète d'exécution de conteneurs.

### 5. 🧪 `sonarqube` – Analyse statique de code (SAST)

Serveur **SonarQube** configuré pour recevoir et afficher les résultats d’analyse de code.

- Permet d’assurer la **qualité logicielle** dès la phase d’intégration.
- S’intègre dans le pipeline CI pour lancer automatiquement les analyses lors des pushs.

Le déploiement de ce composant n'est pas une priorité mais est une amélioration bienvenue.

### 6. 🗃️ `gitea` – Serveur Git auto-hébergé

Instance locale de **Gitea**, un gestionnaire de dépôts Git léger et rapide.

- Fournit une interface web pour la gestion du code source, des issues et des pull requests.
- Sert de **point central** pour les dépôts utilisés par Jenkins (via Webhooks ou polling Git).
- Remplace GitLab dans un setup plus minimaliste, tout en restant compatible avec les workflows Git classiques (CI/CD, forks, branches...).

---

## 🧭 Démarche pédagogique

Dans ce TP, vous allez :

- Définir l’infrastructure dans un fichier `docker-compose.yaml` unique (IaC),
- Générer une clé SSH et la configurer pour permettre les déploiements sans mot de passe,
- Écrire un pipeline CI/CD complet (build + analyse + déploiement),
- Valider que le déploiement vers le conteneur cible fonctionne sans intervention manuelle.

---

## 💬 En résumé

Une mini-infrastructure DevOps auto-hébergée, en pur Docker, pilotée graphiquement depuis un conteneur VNC, permettant un enchaînement CI/CD complet : du commit à la mise en production.

> 🧠 *Le tout, sans jamais rien installer sur l’hôte, hormis Docker lui-même.*


_Disclaimer: La mise en forme de ce sujet a en partie été réalisée avec l'aide d'un agent conversationnel._

----

## 🔨 Travail à faire

### Étape 0 - Prérequis

Avant de commencer, assurez-vous que les prérequis suivants sont satisfaits pour pouvoir exécuter les TP dans de bonnes conditions.

- 🐧 **Système Linux requis** : Les exercices doivent être réalisés sur un système Linux, avec un accès root ou `sudo`.

- 🐳 **Docker installé** : L’environnement doit disposer de Docker et Docker Compose installés.
  - Vérifiez l’installation avec :
    ```bash
    docker --version
    docker compose version
    ```

- 🧱 **Définition des conteneurs via Docker Compose** :
  - Tous les services seront définis dans un fichier `docker-compose.yaml`.
  - Cela permet de centraliser la configuration et de démarrer tous les services avec une seule commande :
    ```bash
    docker compose up -d
    ```

- 🌐 **Nom du service = nom d’hôte** :
  - Docker Compose attribue automatiquement à chaque conteneur un nom d’hôte correspondant à son nom de service.
  - Exemple : un service nommé `jenkins` sera joignable depuis un autre conteneur avec l’URL `http://jenkins:8080`.

- 💾 **Volumes ou montages liés pour la persistance** :
  - Il est important de **conserver les données** (configuration Jenkins, jobs, plugins, logs…) entre les redémarrages ou suppressions de conteneurs.
  - Pour cela, utilisez :
    - des volumes Docker :
      ```yaml
      volumes:
        - jenkins_home:/var/jenkins_home
      ```
    - ou des montages liés (bind mounts) :
      ```yaml
      volumes:
        - ./jenkins_data:/var/jenkins_home
      ```
  - Cela évite de perdre les données en cas de recréation du conteneur.

- ✅ Vérifiez que les ports nécessaires (par ex. `8080` pour Jenkins) sont libres sur votre machine.

---

### Étape 1 — Déploiement du service `desktop`

Ce service correspond à une machine Linux graphique accessible via VNC dans un navigateur. Il servira de poste d’administration pour interagir avec les autres conteneurs de l’infrastructure.

#### Instructions

1. Dans votre fichier `docker-compose.yml`, définis un service nommé `desktop` basé sur l'image Docker publique **`dorowu/ubuntu-desktop-lxde-vnc`**.

2. Je vous conseille de créer votre conteneur à partir d'un Dockerfile afin d'y inclure les commandes suivantes:

```shell
    rm -f /etc/apt/sources.list.d/google-chrome.list && \
    apt-get update && \
    apt-get install -y firefox unzip git nano
```

3. Configurer :
   - Un mappage de port pour rendre le bureau accessible via `http://localhost`.
   - Deux variables d’environnement obligatoires : `USER` et `PASSWORD`
   - Un redémarrage automatique du conteneur avec la directive `restart: unless-stopped`.

4. Lancer le service avec la commande suivante :

    ```bash
    docker compose up -d --build --force-recreate
    ```

    - `--build` : force la reconstruction des images (utile en cas de modification).
    - `--force-recreate` : force la recréation des conteneurs même s’ils existent déjà.

5. Vérifier le bon démarrage du conteneur :

    ```bash
    docker compose ps
    ```

6. Une fois lancé, accèder à l’interface graphique via ton navigateur à l'adresse suivante :

    ```
    http://localhost
    ```

    Vous devriez voir s'afficher un bureau LXDE directement dans le navigateur.

#### 🔐 Remarque sur la sécurité

Les variables `USER` et `PASSWORD` sont visibles en clair dans le fichier `docker-compose.yml`.  
Ceci est **acceptable ici**, dans un cadre **local, pédagogique**, sans enjeu de sécurité. En production, on privilégierait un stockage des secrets via des volumes chiffrés, des fichiers `.env` ignorés du VCS, ou des gestionnaires de secrets (Vault, AWS Secrets Manager...).

---

## Étape 2 — Déploiement du service `plateforme`

Ce service représente un environnement de déploiement **"prod-like"**, à partir duquel nous pourrons simuler des actions DevOps (ex. : déploiement applicatif, exécution de conteneurs, etc.).

### 🔧 Image personnalisée à construire

Contrairement au service `desktop`, ici **aucune image préexistante** ne répond à nos besoins.  
Nous allons donc **construire notre propre image Docker** à l’aide d’un `Dockerfile`.

Cette image est fournie un peu plus bas dans la section `🧪 Dockerfile`

### ⚠️ Pas de mappage de port par défaut

Par souci de sécurité, **aucun port n’est exposé** vers la machine hôte.  
Cependant, pour déboguer une connexion SSH (ex. via `ssh root@localhost -p 2222`), vous pouvez temporairement ajouter un mappage de port dans le `docker-compose.yml` :

```yaml
ports:
  - "2222:22"
```

### 🐳 DoD — Docker-outside-of-Docker

Nous souhaitons que le conteneur `plateforme` puisse exécuter des commandes Docker (ex. : `docker run`, `docker build`, etc.).

Pour cela, on **monte le socket Docker de l’hôte dans le conteneur** :

```yaml
volumes:
  - /var/run/docker.sock:/var/run/docker.sock
```

C’est ce qu’on appelle le **Docker-outside-of-Docker (DoD)**.  
Cela permet au conteneur de piloter le moteur Docker de l’hôte, **sans avoir Docker installé dans le conteneur lui-même**.

> 🛑 **Attention** : ce montage donne des **droits élevés** sur le démon Docker de l’hôte.  
> À ne jamais faire en production sans précautions.

### 🔐 Authentification SSH sécurisée

Idéalement nous devrions suivre la procéduire suivante

> La connexion SSH en tant que `root` est **désactivée par défaut** (bonne pratique de sécurité).
>
> Nous devons donc créer un **utilisateur dédié** (dans l'exemple `jenkins`) pour se connecter via SSH.
>
> Cet utilisateur devra **faire partie du groupe `docker`** pour accéder au socket monté.
>

**Par simplicité et gain de temps**, il vous est fourni un Dockerfile permettant de se connecter en shh avec l'utilisateur `root` et le mot de passe `root`

### 🧪 Dockerfile

Voici le Dockerfile de la plateforme à intégrer dans votre `docker-compose.yaml`


```Dockerfile
# A ne jamais exposer en prod tel quel !
FROM alpine:latest

RUN apk add --no-cache openssh git docker-cli docker-compose

RUN echo "root:root" | chpasswd

# Configuration minimale de SSHD, pas adapté à un contexte de prod.
# Normalement pas de connexion en root, pas de password, on configure une paire de clé publique/privée
RUN ssh-keygen -A \
    && echo "PermitRootLogin yes" >> /etc/ssh/sshd_config \
    && echo "PasswordAuthentication yes" >> /etc/ssh/sshd_config \
    && echo "PubkeyAuthentication yes" >> /etc/ssh/sshd_config \
    && echo "AllowUsers root" >> /etc/ssh/sshd_config

EXPOSE 22

# On lance le serveur SSH
CMD sh -c "/usr/sbin/sshd -D"
```

---

## Etape 3 : Test du serveur SSH sur le service `plateforme`

_N.B: Vous pouvez réaliser cette étape depuis votre machine hôte également. Dans ce cas pensez à réaliser un port forwarding._

1. Sur l'application desktop, ouvrir un terminal.

2. Installer un client SSH si ce n'est pas déjà fait :  
```bash
sudo apt install ssh
```
3. Vérifier que l'agent SSH est bien lancé :
```bash
eval "$(ssh-agent -s)"
```

1. Vous pouvez maintenant vous connecter sur votre serveur de déploiement :

```bash
ssh root@plateforme
```

_N.B: Vous pouvez utiliser le paramètre `-p 2022` par exemple si vous vous connecter depusi votre machine avec un port forwarding sur le port 2022._

---

## Etape 4: Déploiement du service jenkins

Installer Jenkins en complétant votre `docker-compose.yaml` en vous appuyant sur [la documentation d'installation de jenkins](https://hub.docker.com/r/jenkins/jenkins).

Quelques conseils pour l'installation:
- Par défaut l'IHM de Jenkins est disponible sur le port `8080`. Je vous conseille de changer le port http en `80`. Vous pouvez utiliser la variable d'environnement suivante : `JENKINS_OPTS=--httpPort=80`
- Attention, en cas de modification de votre docker-compose.yml, même en recréant les conteneurs votre configuration Jenkins ne sera pas mise à jour. C'est normal et c'est le comportement attendu si vous utilisez un volume. Donc en cas d'erreur de configuration pensez à supprimer votre volume jenkins. 

### Patch pour les webhooks

Créer un fichier `disable-crumbs.groovy` avec le contenu suivant :

```groovy
// disable-crumbs.groovy
import jenkins.model.Jenkins

Jenkins.instance.setCrumbIssuer(null)
println "--> CSRF protection disabled via init script"
```

Ce script permet de désactiver une vérification de sécurité pour permettre des appels depuis `gitea` plus simple.

Ce fichier doit être placé dans `/usr/share/jenkins/ref/init.groovy.d/` il s'agit d'un répertoire dans lequel les scripts
groovy présents seront automatiquement exécutés au démarrage de Jenkins.

### Finalisation installation Jenkins

Une fois votre conteneur `running`, rendez-vous sur `http://jenkins`:
- Dévérouiller jenkins grâce au secret affiché dans les logs de démarrage du conteneur
- Finaliser l'installation en sélectionnant les plugins conseillés par défaut
- Ajouter un utilisateur administrateur

Jenkins permet *Out of the Box* d'exécuter des pipelines sur le noeud master, l'orchestrateur. Ce n'est pas optimal, mais dans un premier temps nous nous en contenterons. 

Nous allons faire un test:
- Créer un pipeline de type `Freestyle project`
- Ajouter un build step `Execute shell`
- Entrer une commande `echo hello world !`
- Sauvegarder et lancer un build
- Consulter la console vous devrez avoir une sortie similaire à ce qui suit. Si c'est le cas félicitations, si ce n'est pas le cas, on lache rien ;-)

```shell
Started by user Jean Jean
Running as SYSTEM
Building in workspace /var/jenkins_home/workspace/test2
[test2] $ /bin/sh -xe /tmp/jenkins14715562595819495743.sh
+ echo hello world !
hello world !
Finished: SUCCESS
```

---

## Etape 5: Déploiement du service `gitea`

Installer Gitea en complétant votre `docker-compose.yaml` en vous appuyant sur [la documentation d'installation de gitea](https://docs.gitea.com/category/installation).

Par défaut l'IHM de Gitea est disponible sur le port `3000`. [En vous aidant de cette documentation](https://docs.gitea.com/administration/config-cheat), au moyen des variables d'environnement, réaliser les ajustements de configuration afin de :
- Sur le conteneur gitea, exposer le service gitea sur le port `80` au lieu de `3000`
- Avoir une `ROOT_URL` et `LOCAL_ROOT_URL` égales à `http://gitea`
- Autoriser à contacter Jenkins via Webhook en créant une variable d'environnement `GITEA__webhook__ALLOWED_HOST_LIST` à `jenkins` 

Lorsque votre service est `UP`, rendez-vous sur `http://gitea`:
- Finaliser l'installation
- Ajouter un utilisateur. Comme il s'agit du premier utilisateur, il va bénéficier des droits admin.
- Créer un nouveau dépôt que vous nommerez `score`
- Importer le [code du webservice score disponible au téléchargement en suivant ce lien github](https://github.com/geomatiq/r408-td3/archive/refs/heads/master.zip). Pour réaliser l'opération depuis un terminal voici la commande de téléchargement : `curl -LJO https://github.com/geomatiq/r408-td3/archive/refs/heads/master.zip`.

Vous préférez utiliser le code d'un de vos projets ? Vous êtes libre de le faire, par contre, en fonction des technos, le support sur la mise en place de votre pipeline sera plus compliqué.

---

## Etape 6: On branche les fils maintenant ?

### Création du job Jenkins

Dans un premier temps, ajouter un `Item` sur jenkins de type `Pipeline`. Cocher `Trigger builds remotely` et renseigner un token.
Ce token n'est pas utilisé pour l'authentification auprès de gitlab mais uniquement pour la pipeline.
Par simplicité, dans le cadre de ce TP vous pouvez mettre `score`.

Indiquer un `pipeline script` avec le contenu suivant:

```groovy
pipeline {
    agent any

    stages {
        stage('Hello') {
            steps {
                echo 'Hello World'
            }
        }
    }
}
```

A terme nous allons plutôt récupérer les étapes de la pipeline dans le dépôt du projet `score` via un fichier `Jenkinsfile`
(de la même manière que la pipeline que nous avions réalisé sur gitlab.com avec le fichier `.gitlab-ci.yml`).

### Création du webhook gitea

L'objectif du webhook est de notifier le serveur d'intégration continue qu'un changement est survenu dans la base de code.

Dans les paramètres du dépôt `score` sur gitea ajouter un webhook avec les informations suivantes:
- Target URL: `http://jenkins/job/NOM_DE_VOTRE_PIPELINE/build?token=TOKEN_DEFINI_DANS_JOB_JENKINS`
- Authorization Header: `BASIC XXXXX`. Remplacer `XXXXX` par le résultat de la commande suivante: `echo -n 'VOTRE_JENKINS_USERNAME:VOTRE_MDP_JENKINS' | base64`

_N.B: En dehors d'un TP ne pas utiliser ce type d'authorization header, le mot de passe est en clair. On créé plutôt un token dédié à gitea dans jenkins._
_Nous n'avons pas fait cette approche car il y a de la configuration supplémentaire côté CSRF_

Pour vérifier que cela fonctionne vous avez simplement à faire un commit sur votre dépôt `score`.

### Intégration du Jenkinsfile

Sur Jenkins, modifier votre définition de pipeline en sélectionnant `Pipeline script from SCM`. Configurer la connexion à votre repository `score`.

Ajouter à la racine dans votre projet `score`, un fichier `Jenkinsfile` qui va comporter le code suivant :

```groovy
pipeline {
    agent any

    stages {
        stage('Hello') {
            steps {
                echo 'Hello World'
            }
        }
    }
}
```

Faire un commit et vérifier la bonne exécution de la pipeline.

---

## Etape 7: Déploiement du jenkins docker agent

### Enrichissement du docker-compose.yml

Nous pourrions installer java sur l'orchestrateur jenkins mais:
- L'orchestrateur devrait uniquement orchestrer et ne pas exécuter de pipeline
- Il ne faut pas installer de dépendances applicatives en dur sur les environnements CI/CD.

Nous allons donc déployer un nouveau conteneur en charge d'exécuter les pipelines et disposant de Docker afin que notre application gère elle même son environnement de build dans la ci/cd.

- Utiliser l'image `jenkins/inbound-agent:latest`.
- Penser à installer avec `apt-get` la dépendance `docker.io`.
- Valoriser les variables d'environnement suivantes:
  - `JENKINS_URL`
  - `JENKINS_AGENT_NAME` # Il s'agit d'une propriété pour identifier l'agent à utiliser dans le Jenkinsfile que vous allez écrire plus tard
  - `JENKINS_AGENT_WORKDIR` # Définir un répertoire de travail pour l'agent. C'est arbitraire. Eviter simplement la racine.
  - `JENKINS_SECRET` # Lire les instructions qui suivent

### Enregistrement d'un nouvel agent dans Jenkins

Sur l'interface web de Jenkins:
  - Aller dans `Manage Jenkins / Nodes / New node`.
    - Utiliser le même nom que la variable `JENKINS_AGENT_NAME`
    - Cocher `Permanent agent`
    - Cliquer sur `create`
  - Valoriser ensuite ces valeurs:
    - `labels: docker`
    - Utiliser le même chemin pour remote root directory que la variable `JENKINS_AGENT_WORKDIR`
    - Sauvegarder
  
Sur la page qui suit vous allez voir des commandes similaires à ça :

```shell
curl -sO http://jenkins/jnlpJars/agent.jar
java -jar agent.jar -url http://jenkins/ -secret 0216e32fc39216f5db85bd00665ee4802c2545a53401406ccf6c70732bca0a65 -name test -webSocket -workDir "/home/jenkins/agent"
```

Conserver consentieusement la valeur du secret, ici `0216e32fc39216f5db85bd00665ee4802c2545a53401406ccf6c70732bca0a65`. Il s'agit de la valeur à renseigner dans la variable d'environnement `JENKINS_SECRET`.

Mettre à jour votre fichier `docker-compose.yaml` puis si nécessaire relancer vos services. 

Vous pouvez vérifier que la connexion s'effectue correctement en vérifiant les logs du conteneur jenkins docker agent. Dans l'interface web de Jenkins, sur la page du nouveau noeud, il doit être écrit `Agent is connected`.

---

## Etape 8: Mise en place de la pipeline CI

Sur Jenkins, aller dans `Gérer Jenkins` et installer le plugin `Docker pipeline`.

Modifier votre fichier Jenkinsfile afin de réaliser la compilation et les tests de votre application java. Nous utilisons un projet avec java 17.

Voici un exemple de Jenkinsfile:

```jenkinsfile
pipeline {
    agent {
        docker {
            image 'maven:3.9.6-eclipse-temurin-17' // Maven + JDK 17
            label 'docker-agent'
        }
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        // TODO: à compléter
    }
}
```

Ici j'ai renseigné un label `docker-agent` car c'est le nom que j'ai défini dans ma configuration à l'étape 7 (Dans la variable `JENKINS_AGENT_NAME`). C'est à adapter en fonction de la votre.

---

## Etape 9: Amélioration de la pipeline pour déployer sur la plateforme

### Ajout d'une étape de construction d'une image docker

Pour l'instant notre pipeline est capable de réaliser le build et les tests de notre application.
Cela nous permet de vérifier la non régression de notre code.
Nous souhaitons désormais pouvoir exploiter notre application.

La première étape consiste à réaliser du **Continous Delivery**, à chaque commit nous allons construire notre livrable et le déposer sur un registre pour le rendre accessible.

L'application fournie est une application java avec maven pour gestionnaire de dépendance.
Pour rappel, nous pouvons générer l'archive java exécutable avec la commande `mvn package`.

-> Vous devez réaliser la conteneurisation de votre application. Pour se faire, rendez-vous dans le dépôt git de votre application, écrivez un `Dockerfile` et rajouter un `stage` dans votre jenkinsfile pour procéder à la construction de votre image avec `docker build`.

_Astuce: Votre Dockerfile doit comporter un environnement ***d'exécution** java (JRE), importer le jar construit par votre pipeline dans le FS de votre image et disposer d'une commande par défaut pour lancer votre application_

_Astuce 2: Appuyez-vous sur la documentation du JRE disponible sur la page Docker Hub de l'image java que vous avez sélectionnée._

### Et maintenant, qu'est-ce que j'en fais de mon image ?

Votre image est construite mais perdue à la fin de chaque job Jenkins. 

Vous devez l'héberger sur un serveur, le registre d'images.

Pour cette exercice, il vous sera proposé une correction avec un registre public sur Docker Hub. Vous pouvez tout à fait utiliser le registre d'images de gitlab.com.





---

## Etape 10: Elaboration d'un workflow git

TODO

---

## Etape 11: Implémentation du workflow git dans la CI/CD

TODO

---

## Etape 12: Pour aller plus loin

Vous pouvez réaliser les actions suivantes:
- Chaque pipeline télécharge systématiquement les dépendances maven. Ce n'est pas environment friendly, ni dev friendly car la durée de vos jobs sont rallongées. Vous pouvez améliorer ceci en conservant le répertoire des dépendances maven (.m2). Jenkins proprose des plugins, sinon vous pouvez utiliser un volume ou montage lié.
- Déploiement de Sonarquabe + intégrer une analyse SAST dans la pipeline avec quality gate
- Amélioration de la sécurité (paire de clés, pas de bypass CSRF...)

## Crédit

Maxime LAMBERT - Cours CESI - INF83 - Juin 2025
