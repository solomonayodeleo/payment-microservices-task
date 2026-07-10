#!/bin/bash
set -e

mkdir -p gateway payments-api ledger-mock settlement-worker

echo "Scaffolding Gateway..."
curl -s -G https://start.spring.io/starter.tgz -d dependencies=webflux,cloud-gateway -d type=maven-project -d groupId=com.interswitch.capstone -d artifactId=gateway -d name=gateway -d javaVersion=17 | tar -xzf - -C gateway

echo "Scaffolding Payments API..."
curl -s -G https://start.spring.io/starter.tgz -d dependencies=web,data-redis,actuator -d type=maven-project -d groupId=com.interswitch.capstone -d artifactId=payments-api -d name=payments-api -d javaVersion=17 | tar -xzf - -C payments-api

echo "Scaffolding Ledger Mock..."
curl -s -G https://start.spring.io/starter.tgz -d dependencies=web,actuator -d type=maven-project -d groupId=com.interswitch.capstone -d artifactId=ledger-mock -d name=ledger-mock -d javaVersion=17 | tar -xzf - -C ledger-mock

echo "Scaffolding Settlement Worker..."
curl -s -G https://start.spring.io/starter.tgz -d dependencies=web,actuator -d type=maven-project -d groupId=com.interswitch.capstone -d artifactId=settlement-worker -d name=settlement-worker -d javaVersion=17 | tar -xzf - -C settlement-worker

echo "Scaffolding complete."
