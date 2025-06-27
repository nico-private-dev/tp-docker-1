FROM dorowu/ubuntu-desktop-lxde-vnc:latest

USER root

# Installation des dépendances supplémentaires
RUN rm -f /etc/apt/sources.list.d/google-chrome.list && \
    apt-get update && \
    apt-get install -y firefox unzip git nano
    