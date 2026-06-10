# Blogging App End-to-End DevOps Deployment Guide

## Project Overview

This project demonstrates a complete DevOps CI/CD pipeline for deploying a Spring Boot Blogging Application on Amazon EKS using Jenkins, SonarQube, Nexus, Docker, Kubernetes, Prometheus, and Grafana.

---

# Architecture

```text
GitHub
   |
   v
Jenkins
   |
   +--> Maven Build
   |
   +--> SonarQube Analysis
   |
   +--> Nexus Artifact Upload
   |
   +--> Docker Build
   |
   +--> Trivy Security Scan
   |
   +--> Docker Hub Push
   |
   +--> EKS Deployment
   |
   +--> Prometheus Monitoring
   |
   +--> Grafana Dashboard
```

---

# Infrastructure Setup

## AWS Resources

### EC2 Instances

| Server            | Purpose               |
| ----------------- | --------------------- |
| Jenkins Server    | CI/CD Pipeline        |
| Monitoring Server | EKS Management        |
| Nexus Server      | Artifact Repository   |
| SonarQube Server  | Code Quality Analysis |

### EKS Cluster

```bash
eksctl create cluster \
--name devopsshack-cluster \
--region ap-south-1 \
--nodes 3
```

Verification:

```bash
kubectl get nodes
```

---

# Jenkins Installation

```bash
sudo apt update
sudo apt install openjdk-17-jdk -y

wget -q -O - https://pkg.jenkins.io/debian-stable/jenkins.io-2023.key | sudo apt-key add -

sudo sh -c 'echo deb https://pkg.jenkins.io/debian-stable binary/ > /etc/apt/sources.list.d/jenkins.list'

sudo apt update
sudo apt install jenkins -y

sudo systemctl start jenkins
sudo systemctl enable jenkins
```

Access:

```text
http://<JENKINS_PUBLIC_IP>:8080
```

---

# Docker Installation on Jenkins

```bash
sudo apt install docker.io -y

sudo usermod -aG docker jenkins
sudo usermod -aG docker ubuntu

sudo systemctl restart docker
sudo systemctl restart jenkins
```

Verify:

```bash
docker ps
```

---

# SonarQube Setup

```bash
docker run -d \
--name sonar \
-p 9000:9000 \
sonarqube:lts-community
```

Access:

```text
http://<SONAR_SERVER>:9000
```

Generate Token:

```text
Administration
→ Security
→ Users
→ Tokens
```

Add token in Jenkins Credentials.

---

# Nexus Setup

```bash
docker run -d \
-p 8081:8081 \
--name nexus \
sonatype/nexus3
```

Access:

```text
http://<NEXUS_SERVER>:8081
```

Repositories Created:

```text
maven-releases
maven-snapshots
```

Configure Maven Settings.xml.

---

# Spring Boot Application Configuration

## Dependencies Added

```xml
spring-boot-starter-actuator
micrometer-registry-prometheus
```

---

## application.properties

```properties
spring.application.name=twitter-app

spring.datasource.url=jdbc:h2:mem:twitterapp
spring.datasource.driverClassName=org.h2.Driver
spring.datasource.username=sa
spring.datasource.password=password

spring.jpa.database-platform=org.hibernate.dialect.H2Dialect

spring.h2.console.enabled=true

management.endpoints.web.exposure.include=health,info,prometheus
management.endpoint.health.show-details=always

management.prometheus.metrics.export.enabled=true
management.metrics.binders.processor.enabled=false
```

---

## Security Configuration

Actuator endpoints exposed:

```java
.requestMatchers("/actuator/**").permitAll()
```

Without this configuration Prometheus received:

```text
received unsupported Content-Type "text/html"
```

because Spring Security redirected requests to the login page.

---

# DockerHub Configuration

Create Jenkins Credentials:

```text
docker-cred
```

Type:

```text
Username with Password
```

---

# Kubernetes Namespace

```bash
kubectl create namespace webapps
```

---

# Docker Registry Secret

```bash
kubectl create secret docker-registry regcred \
--docker-server=https://index.docker.io/v1/ \
--docker-username=<dockerhub-user> \
--docker-password=<dockerhub-password> \
-n webapps
```

Verify:

```bash
kubectl get secret -n webapps
```

---

# Jenkins Service Account

Create Service Account:

```yaml
apiVersion: v1
kind: ServiceAccount
metadata:
  name: jenkins
  namespace: webapps
```

Apply:

```bash
kubectl apply -f serviceaccount.yaml
```

---

# RBAC Configuration

```yaml
apiVersion: rbac.authorization.k8s.io/v1
kind: ClusterRoleBinding
metadata:
  name: jenkins-admin
roleRef:
  kind: ClusterRole
  name: cluster-admin
  apiGroup: rbac.authorization.k8s.io
subjects:
- kind: ServiceAccount
  name: jenkins
  namespace: webapps
```

Apply:

```bash
kubectl apply -f rbac.yaml
```

---

# Generate Jenkins Token

```bash
kubectl get secrets -n webapps

kubectl describe secret mysecretname -n webapps
```

Copy token.

Store in Jenkins Credentials:

```text
ID = k8s-token
Type = Secret Text
```

---

# Jenkins Pipeline

Pipeline Stages:

```text
Git Checkout
Compile
Trivy FS Scan
SonarQube Analysis
Build
Publish Artifacts
Docker Build
Trivy Image Scan
Docker Push
K8S Deploy
Verify Deployment
Email Notification
```

---

# Kubernetes Deployment

Deployment:

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: bloggingapp-deployment
  namespace: webapps
spec:
  replicas: 2
```

Service:

```yaml
apiVersion: v1
kind: Service
metadata:
  name: bloggingapp-ssvc
  namespace: webapps
spec:
  type: LoadBalancer
```

Deploy:

```bash
kubectl apply -f deployment-service.yml
```

Verify:

```bash
kubectl get all -n webapps
```

---

# Jenkins K8S Deployment Stage

```groovy
withKubeCredentials(
kubectlCredentials: [[
credentialsId: 'k8s-token',
namespace: 'webapps',
serverUrl: 'https://<EKS-ENDPOINT>'
]]
)
```

---

# Prometheus Installation

Add Helm Repo:

```bash
helm repo add prometheus-community \
https://prometheus-community.github.io/helm-charts

helm repo update
```

Install:

```bash
helm install monitoring \
prometheus-community/kube-prometheus-stack \
-n monitoring \
--create-namespace
```

Verify:

```bash
kubectl get pods -n monitoring
```

---

# Grafana Access

Get Service:

```bash
kubectl get svc -n monitoring
```

Access:

```text
http://<grafana-loadbalancer>
```

Default:

```text
admin
prom-operator
```

---

# Application Monitoring

Service Label Added:

```yaml
labels:
  app: bloggingapp
```

---

# ServiceMonitor

```yaml
apiVersion: monitoring.coreos.com/v1
kind: ServiceMonitor
metadata:
  name: bloggingapp-monitor
  namespace: monitoring
  labels:
    release: monitoring

spec:
  namespaceSelector:
    matchNames:
      - webapps

  selector:
    matchLabels:
      app: bloggingapp

  endpoints:
  - port: http
    path: /actuator/prometheus
    interval: 15s
```

Apply:

```bash
kubectl apply -f bloggingapp-monitor.yaml
```

---

# Prometheus Verification

Navigate:

```text
Status
→ Target Health
```

Expected:

```text
serviceMonitor/monitoring/bloggingapp-monitor

2/2 UP
```

---

# Useful PromQL Queries

### JVM Memory

```promql
jvm_memory_used_bytes
```

### CPU Usage

```promql
system_cpu_usage
```

### HTTP Requests

```promql
http_server_requests_seconds_count
```

### JVM Uptime

```promql
process_uptime_seconds
```

### Tomcat Threads

```promql
tomcat_threads_current_threads
```

---

# Grafana Dashboards

Import Dashboard IDs:

```text
4701   JVM Micrometer
6756   Spring Boot Statistics
15757  Kubernetes Views Global
15759  Kubernetes Views Nodes
15760  Kubernetes Views Pods
```

---

# Problems Faced and Fixes

## Docker Permission Error

Error:

```text
permission denied while trying to connect docker.sock
```

Fix:

```bash
sudo usermod -aG docker jenkins
sudo systemctl restart jenkins
```

---

## Jenkins Login Failure

Verified:

```bash
/var/lib/jenkins/users/
```

User configuration existed.

Restarted Jenkins and validated security configuration.

---

## EKS Authentication Failure

Error:

```text
Unable to locate credentials
```

Fix:

```bash
aws configure

aws eks update-kubeconfig \
--region ap-south-1 \
--name devopsshack-cluster
```

---

## Prometheus Scraping Failure

Error:

```text
received unsupported Content-Type "text/html"
```

Root Cause:

```text
Spring Security redirected
/actuator/prometheus
to login page.
```

Fix:

```java
.requestMatchers("/actuator/**").permitAll()
```

Redeployed application.

Result:

```text
Target State = UP
```

---

# Final Outcome

Successfully implemented:

✅ GitHub Integration

✅ Jenkins CI/CD

✅ SonarQube Analysis

✅ Nexus Artifact Repository

✅ Docker Build & Push

✅ Trivy Security Scanning

✅ Amazon EKS Deployment

✅ Kubernetes Service Exposure

✅ Prometheus Monitoring

✅ Grafana Visualization

✅ Spring Boot Metrics Monitoring

✅ Email Notifications

---

# Future Enhancements

* Alertmanager Email Alerts
* Slack Notifications
* ArgoCD GitOps Deployment
* Loki Log Aggregation
* Grafana Tempo Tracing
* AWS ALB Ingress Controller
* Horizontal Pod Autoscaler
* Terraform Automation

---

Author: Rajesh Chilkuri

Project: End-to-End DevOps CI/CD Pipeline on AWS EKS with Monitoring
