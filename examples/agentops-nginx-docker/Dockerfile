FROM alpine:3.20

RUN apk add --no-cache nginx \
    && mkdir -p /run/nginx /workspace

COPY entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh

EXPOSE 80

CMD ["/entrypoint.sh"]
