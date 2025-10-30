#!/bin/bash

set -e

echo "=========================================="
echo "Starting deployment process..."
echo "=========================================="

cd /home/ubuntu/cost-web-app

echo "Step 1: Pulling latest changes from Git..."
git pull origin main

echo "Step 2: Building application..."
./gradlew clean build -x test

echo "Step 3: Restarting service..."
sudo systemctl restart cost-web-app

echo "Step 4: Waiting for service to start..."
sleep 5

echo "Step 5: Checking service status..."
sudo systemctl status cost-web-app --no-pager

echo "Step 6: Checking application health..."
sleep 3
curl -f http://localhost:8080/actuator/health || echo "Health check failed"

echo "=========================================="
echo "Deployment completed!"
echo "=========================================="

