pipeline {
    agent any
    
    environment {
        GCR_REGISTRY = "asia.gcr.io/upsmf-368011" 
        IMAGE_NAME = "grievance-be-uat"
        BRANCH_NAME = "main" 
	IMAGE_TAG = 1.0   
    }

    stages {
        stage('Clone Repository') {
            steps {
                git branch: 'main', url: 'https://github.com/vky25/Grievance.git'
            }
        }

        stage('Build Artifact') {
            steps {
                sh 'mvn clean install -DskipTests' 
            }
        }

        stage('Build Docker Image') {
            steps {
                script {
		    def dockerTag = "${IMAGE_TAG}-${env.BUILD_NUMBER}"	
		    def dockerImage = docker.build("${GCR_REGISTRY}/${IMAGE_NAME}:${dockerTag}")	
                }
            }
        }
        stage('Push to GCR') {
            steps {
                script {
                        sh "docker push ${GCR_REGISTRY}/${IMAGE_NAME}:${dockerTag}"
                }
            }
        }   

    }
}
