# A ne jamais exposer en prod tel quel !
FROM alpine:latest

RUN apk add --no-cache openssh git docker-cli docker-compose

ARG USER=root
ARG PASSWORD=root
RUN echo "${USER}:${PASSWORD}" | chpasswd

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