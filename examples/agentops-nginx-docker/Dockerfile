FROM alpine:3.20

RUN apk add --no-cache openssh-server nginx \
    && ssh-keygen -A \
    && adduser -D -s /bin/sh ops \
    && echo 'ops:ops' | chpasswd \
    && mkdir -p /home/ops/.ssh /run/nginx /workspace \
    && chown -R ops:ops /home/ops/.ssh \
    && chmod 700 /home/ops/.ssh \
    && sed -i 's/#PasswordAuthentication yes/PasswordAuthentication no/' /etc/ssh/sshd_config \
    && sed -i 's/#PubkeyAuthentication yes/PubkeyAuthentication yes/' /etc/ssh/sshd_config

COPY entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh

EXPOSE 22 80

CMD ["/entrypoint.sh"]
