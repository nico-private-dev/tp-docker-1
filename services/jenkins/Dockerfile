FROM jenkins/jenkins:lts

USER root

# Installation des outils nécessaires
RUN apt-get update && \
    apt-get install -y apt-transport-https \
    ca-certificates \
    curl \
    gnupg2 \
    software-properties-common

# Nettoyage
RUN apt-get clean && \
    rm -rf /var/lib/apt/lists/*

# Copier le script de désactivation des CSRF crumbs
COPY disable-crumbs.groovy /usr/share/jenkins/ref/init.groovy.d/

USER jenkins