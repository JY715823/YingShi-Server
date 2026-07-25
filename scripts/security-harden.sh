#!/bin/bash
set -e

echo "===1.安装sshguard==="
dnf install -y sshguard sshguard-firewalld 2>&1 | tail -5

echo "===2.启动firewalld==="
systemctl enable firewalld
systemctl start firewalld
systemctl is-active firewalld && echo "firewalld运行中" || echo "firewalld启动失败"

echo "===3.配置firewalld规则==="
# 移除默认ssh服务(22端口)，改为精确端口控制
firewall-cmd --permanent --remove-service=ssh 2>/dev/null || true
# 放行SSH(22)、HTTPS(8443)、HTTP(80,用于Let's Encrypt验证)
firewall-cmd --permanent --add-port=22/tcp
firewall-cmd --permanent --add-port=8443/tcp
firewall-cmd --permanent --add-port=80/tcp
firewall-cmd --reload
echo "---当前firewalld规则---"
firewall-cmd --list-all

echo "===4.启动sshguard==="
systemctl enable sshguard
systemctl restart sshguard
systemctl is-active sshguard && echo "sshguard运行中" || echo "sshguard启动失败"

echo "===5.安装dnf-automatic(自动安全更新)==="
dnf install -y dnf-automatic 2>&1 | tail -3
sed -i 's/^upgrade_type.*/upgrade_type = security/' /etc/dnf/automatic.conf
sed -i 's/^apply_updates.*/apply_updates = yes/' /etc/dnf/automatic.conf
sed -i 's/^emit_via.*/emit_via = stdio/' /etc/dnf/automatic.conf
systemctl enable --now dnf-automatic.timer
systemctl is-active dnf-automatic.timer && echo "自动安全更新已启用" || echo "自动更新启用失败"

echo "===6.加固sysctl(防SYN Flood等)==="
cat > /etc/sysctl.d/99-security.conf << 'SYSCTL'
# 防SYN Flood攻击
net.ipv4.tcp_syncookies = 1
# 禁用ICMP重定向
net.ipv4.conf.all.accept_redirects = 0
net.ipv4.conf.default.accept_redirects = 0
# 禁用源路由
net.ipv4.conf.all.accept_source_route = 0
net.ipv4.conf.default.accept_source_route = 0
# 禁用IP转发(非路由器)
net.ipv4.ip_forward = 0
# 忽略ICMP广播
net.ipv4.icmp_echo_ignore_broadcasts = 1
# 记录可疑数据包
net.ipv4.conf.all.log_martians = 1
# 禁用IPv6(如不需要)
net.ipv6.conf.all.disable_ipv6 = 1
net.ipv6.conf.default.disable_ipv6 = 1
SYSCTL
sysctl --system 2>&1 | tail -3
echo "sysctl加固完成"

echo "===全部P0加固完成==="
