#!/bin/bash
set -e

echo "===1.检查并安装docker===";
if command -v docker &> /dev/null; then
    echo "docker已安装: $(docker --version)"
else
    # OpenCloudOS 9 兼容CentOS 9,用官方docker源
    echo "安装docker依赖..."
    dnf install -y dnf-plugins-core 2>&1 | tail -2
    echo "添加docker官方源..."
    cat > /etc/yum.repos.d/docker-ce.repo << 'REPO'
[docker-ce-stable]
name=Docker CE Stable - $basearch
baseurl=https://download.docker.com/linux/centos/9/$basearch/stable
enabled=1
gpgcheck=1
gpgkey=https://download.docker.com/linux/centos/gpg
REPO
    echo "安装docker-ce..."
    dnf install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin 2>&1 | tail -5
fi

echo "===2.启动docker===";
systemctl enable docker
systemctl start docker
systemctl is-active docker && echo "docker运行中" || echo "docker启动失败"

echo "===3.配置docker镜像加速(国内必备)==="
mkdir -p /etc/docker
cat > /etc/docker/daemon.json << 'JSON'
{
  "registry-mirrors": [
    "https://docker.m.daocloud.io",
    "https://dockerproxy.com",
    "https://docker.nju.edu.cn",
    "https://docker.mirrors.ustc.edu.cn"
  ],
  "log-driver": "json-file",
  "log-opts": {
    "max-size": "50m",
    "max-file": "3"
  }
}
JSON
systemctl daemon-reload
systemctl restart docker
echo "镜像加速已配置"

echo "===4.验证docker===";
docker --version
docker compose version
docker run --rm hello-world 2>&1 | tail -5

echo "===5.创建项目目录===";
mkdir -p /opt/yingshi
mkdir -p /opt/yingshi/nginx/certs
mkdir -p /opt/yingshi/storage/postgres-data
mkdir -p /opt/yingshi/storage/minio-data
echo "目录已创建:"
ls -la /opt/yingshi/

echo "===Docker安装完成==="
