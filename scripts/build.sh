#!/bin/bash

# All-OS-Simulator Build Script
set -e

echo "=================================="
echo "All-OS-Simulator Build Process"
echo "=================================="

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Check prerequisites
check_prerequisites() {
    echo -e "${YELLOW}Checking prerequisites...${NC}"
    
    # Check Java
    if ! command -v java &> /dev/null; then
        echo -e "${RED}Java is not installed${NC}"
        exit 1
    fi
    
    # Check Maven
    if ! command -v mvn &> /dev/null; then
        echo -e "${RED}Maven is not installed${NC}"
        exit 1
    fi
    
    # Check Node.js
    if ! command -v node &> /dev/null; then
        echo -e "${RED}Node.js is not installed${NC}"
        exit 1
    fi
    
    # Check Python
    if ! command -v python3 &> /dev/null; then
        echo -e "${RED}Python 3 is not installed${NC}"
        exit 1
    fi
    
    # Check Docker
    if ! command -v docker &> /dev/null; then
        echo -e "${YELLOW}Warning: Docker is not installed (required for containerization)${NC}"
    fi
    
    echo -e "${GREEN}All prerequisites satisfied${NC}"
}

# Build Java backend
build_backend() {
    echo -e "${YELLOW}Building Java backend...${NC}"
    cd all-os-simulator
    
    # Clean previous build
    mvn clean
    
    # Run tests
    mvn test
    
    # Build JAR
    mvn package -DskipTests
    
    echo -e "${GREEN}Backend build complete${NC}"
    cd ..
}

# Build web frontend
build_frontend() {
    echo -e "${YELLOW}Building web frontend...${NC}"
    cd web/frontend
    
    # Install dependencies
    npm ci
    
    # Run tests
    npm test -- --watchAll=false
    
    # Build production bundle
    npm run build
    
    echo -e "${GREEN}Frontend build complete${NC}"
    cd ../..
}

# Build mobile apps
build_mobile() {
    echo -e "${YELLOW}Building mobile applications...${NC}"
    
    # React Native build
    cd mobile/react-native
    npm ci
    
    # Android build
    if [ "$1" == "android" ] || [ "$1" == "all" ]; then
        echo "Building Android APK..."
        cd android
        ./gradlew assembleRelease
        cd ..
    fi
    
    # iOS build (requires macOS)
    if [ "$1" == "ios" ] || [ "$1" == "all" ]; then
        if [[ "$OSTYPE" == "darwin"* ]]; then
            echo "Building iOS app..."
            cd ios
            pod install
            xcodebuild -workspace AllOSSimulator.xcworkspace \
                      -scheme AllOSSimulator \
                      -configuration Release \
                      -archivePath build/AllOSSimulator.xcarchive \
                      archive
            cd ..
        else
            echo -e "${YELLOW}iOS build skipped (requires macOS)${NC}"
        fi
    fi
    
    echo -e "${GREEN}Mobile build complete${NC}"
    cd ../..
}

# Download AI models
download_models() {
    echo -e "${YELLOW}Downloading AI models...${NC}"
    
    mkdir -p ai-models
    cd ai-models
    
    # Download TensorFlow models
    if [ ! -f "code-completion.pb" ]; then
        wget https://storage.googleapis.com/ai-models/code-completion.pb
    fi
    
    if [ ! -f "bug-detection.pb" ]; then
        wget https://storage.googleapis.com/ai-models/bug-detection.pb
    fi
    
    # Download PyTorch models
    if [ ! -f "nlp-model.pt" ]; then
        wget https://storage.googleapis.com/ai-models/nlp-model.pt
    fi
    
    # Download OpenCV models
    if [ ! -f "yolov4.weights" ]; then
        wget https://github.com/AlexeyAB/darknet/releases/download/darknet_yolo_v3_optimal/yolov4.weights
    fi
    
    cd ..
    echo -e "${GREEN}Model download complete${NC}"
}

# Build Docker images
build_docker() {
    echo -e "${YELLOW}Building Docker images...${NC}"
    
    # Build main application image
    docker build -t all-os-simulator:latest .
    
    # Build AI server image
    docker build -t all-os-simulator-ai:latest -f Dockerfile.ai .
    
    # Build web server image
    docker build -t all-os-simulator-web:latest -f Dockerfile.web web/
    
    echo -e "${GREEN}Docker images built${NC}"
}

# Package application
package_application() {
    echo -e "${YELLOW}Packaging application...${NC}"
    
    VERSION=$(mvn help:evaluate -Dexpression=project.version -q -DforceStdout)
    PACKAGE_NAME="all-os-simulator-${VERSION}"
    
    # Create package directory
    mkdir -p "dist/${PACKAGE_NAME}"
    
    # Copy artifacts
    cp all-os-simulator/target/*.jar "dist/${PACKAGE_NAME}/"
    cp -r web/frontend/build "dist/${PACKAGE_NAME}/web"
    cp -r ai-models "dist/${PACKAGE_NAME}/"
    cp -r config "dist/${PACKAGE_NAME}/"
    cp -r scripts "dist/${PACKAGE_NAME}/"
    cp README.md "dist/${PACKAGE_NAME}/"
    cp LICENSE "dist/${PACKAGE_NAME}/"
    
    # Create archives
    cd dist
    tar -czf "${PACKAGE_NAME}.tar.gz" "${PACKAGE_NAME}"
    zip -r "${PACKAGE_NAME}.zip" "${PACKAGE_NAME}"
    cd ..
    
    echo -e "${GREEN}Package created: dist/${PACKAGE_NAME}${NC}"
}

# Main build process
main() {
    check_prerequisites
    
    # Parse arguments
    BUILD_TYPE=${1:-all}
    
    case $BUILD_TYPE in
        backend)
            build_backend
            ;;
        frontend)
            build_frontend
            ;;
        mobile)
            build_mobile ${2:-all}
            ;;
        docker)
            build_docker
            ;;
        models)
            download_models
            ;;
        all)
            build_backend
            build_frontend
            build_mobile all
            download_models
            build_docker
            package_application
            ;;
        *)
            echo "Usage: $0 [backend|frontend|mobile|docker|models|all]"
            exit 1
            ;;
    esac
    
    echo -e "${GREEN}=================================="
    echo -e "Build completed successfully!"
    echo -e "==================================${NC}"
}

# Run main function
main "$@"