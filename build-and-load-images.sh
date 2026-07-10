#!/bin/bash
set -e

echo "Building images..."
docker build -t gateway:latest ./gateway
docker build -t payments-api:latest ./payments-api
docker build -t ledger-mock:latest ./ledger-mock
docker build -t settlement-worker:latest ./settlement-worker

echo "Loading images into Kind cluster..."
kind load docker-image gateway:latest --name ds-lab
kind load docker-image payments-api:latest --name ds-lab
kind load docker-image ledger-mock:latest --name ds-lab
kind load docker-image settlement-worker:latest --name ds-lab

echo "Images loaded."
