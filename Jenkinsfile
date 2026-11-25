pipeline {
    agent any
    environment{
        SERVICE = "govt_104_backend"
        REPO1 = "https://github.com/Zen-Plus/${SERVICE}.git"
        JOB = "${env.JOB_NAME}"
        BRANCH = "master"
        DEPLOY_SERVER = "120.126.126.79"
        DEPLOY_PATH = "/mmu-data/104_govt/backend/"
        DOCKER_REPO = "120.126.126.79:5000"
        IMAGE_NAME = "104_govt"+"-backend"
        Latest_Image = "$DOCKER_REPO/${IMAGE_NAME.toLowerCase()}:${BUILD_NUMBER}"
        GIT_CREDS = "zenplus-git-creds"
    }
    stages {

        stage('Cloning 104 Govt Backend Source Code') {
            steps {
                cleanWs()
                git branch: "${BRANCH}", credentialsId: "${GIT_CREDS}", url: "${REPO1}"
            }
        }
        stage('Image Build') {
            steps {
                    // The repo ships its own multi-stage Dockerfile (maven build -> JRE 17 runtime),
                    // so no external Dockerfile is copied in.
                    sh '''
                        docker build -t $DOCKER_REPO/$(echo $IMAGE_NAME | tr '[:upper:]' '[:lower:]'):$BUILD_NUMBER .
                    '''
            }
        }
        stage('Docker Image Push Registry') {
            steps{
                sh '''
                    docker login $DOCKER_REPO
                    docker push $DOCKER_REPO/$(echo $IMAGE_NAME | tr '[:upper:]' '[:lower:]'):$BUILD_NUMBER
                '''
            }
        }
        stage('Update Service With Latest Image'){
            steps{
                // The server's docker-compose.yaml also runs postgres and mongo.
                // Only the backend image line is replaced and only the backend container is
                // recreated, so the databases keep running.
                sh '''
                ssh zpl@$DEPLOY_SERVER <<EOF
                docker pull $Latest_Image
                cd $DEPLOY_PATH
                sed -i "s#image: .*104_govt-backend:.*#image: $Latest_Image#" docker-compose.yaml
                docker-compose up -d --no-deps backend
EOF
                '''
            }
        }
        stage('Cleanup Old Images') {
            steps {
            script {
                def repository = "${DOCKER_REPO}/v2/${IMAGE_NAME.toLowerCase()}"
                def imagesToKeep = 5
                sh """
                # Get list of image tags sorted by version number (assuming numeric tags)
                IMAGE_TAGS=\$(curl -s http://${repository}/tags/list | jq -r '.tags[]' | sort -nr)
                echo "Keeping the latest ${imagesToKeep} images..."
                COUNTER=0
                for TAG in \$IMAGE_TAGS; do
                    COUNTER=\$((COUNTER+1))
                    if [ \$COUNTER -gt ${imagesToKeep} ]; then
                        DIGEST=\$(curl -s -H "Accept: application/vnd.docker.distribution.manifest.v2+json" \
                        -I http://${repository}/manifests/\$TAG | \
                        grep -i Docker-Content-Digest | awk '{print \$2}' | tr -d '\\r')
                        if [ ! -z "\$DIGEST" ]; then
                            echo "Deleting image: ${repository}:\$TAG"
                            curl -s -X DELETE http://${repository}/manifests/\$DIGEST
                        fi
                    fi
                done
            """
                }
            }
        }
        stage('Clean Workspace') {
            steps {
                cleanWs()
            }
        }

    }
    post {
        failure {
            mail to: "zenplussdteam@zenplus.in",
                 subject: "🚨 104 GOVT BACKEND Jenkins Job '${env.JOB_NAME}' (#${env.BUILD_NUMBER}) FAILED",
                 body: "The job has failed. Please check Jenkins at ${env.BUILD_URL}"
        }
    }
}
