#!/bin/sh
set -eu

if [ -f /workspace/ssh/authorized_keys ]; then
  cp /workspace/ssh/authorized_keys /home/ops/.ssh/authorized_keys
  chown ops:ops /home/ops/.ssh/authorized_keys
  chmod 600 /home/ops/.ssh/authorized_keys
fi

/usr/sbin/sshd
nginx

tail -f /dev/null
