---
name: ops_troubleshoot
description: Nginx 故障排查与修复标准作业程序。用户要求排查 502、nginx 起不来、接口不通或需要修复 nginx 配置时必须遵循。
---

# Nginx 故障排查 SOP

1. 使用 `bash` 通过 SSH 查看 `/workspace/error.log` 的最后 50 行。
2. 如果日志出现 `unknown directive`、`upstream prematurely closed connection` 或配置文件路径，读取对应配置文件。
3. 对本工作区挂载的配置文件使用 `edit_file` 精准替换，提供足够上下文，禁止用 `sed` 盲改。
4. 修改后通过 SSH 执行 `nginx -t -c /workspace/nginx.conf` 验证语法。
5. 需要 `nginx -s reload` 时正常调用 `bash`，等待 Telegram 审批结果。
6. 审批拒绝或超时时，停止服务状态变更并汇报原因。
