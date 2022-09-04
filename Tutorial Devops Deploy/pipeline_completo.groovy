pipeline {
    agent any

    environment {
		registry = "mondadori89/app-dcw5" // ALTERAR
        registryCredential = "dockerhub_id"
        dockerImage = ''
    }

    stages {
    	stage('Clone Repository') {
    		steps {  
                git branch: "main", url: 'https://gitlab.com/mondadori89/app-dcw5.git' // ALTERAR
			}
    	}
    	stage('Build Docker Image') {
            steps{
                script {
                    dockerImage = docker.build registry + ":develop"
                }
            }
        }
    	stage('Send image to Docker Hub') {
            steps{
                script {
                    docker.withRegistry( '', registryCredential) {
                        dockerImage.push()
                    }
                }
            }
        }
    	stage('Deploy') {
		    steps{
                step([$class: 'AWSCodeDeployPublisher',
                    applicationName: 'dcw-app', // ALTERAR
                    awsAccessKey: "***********", // ALTERAR
                    awsSecretKey: "***********************", // ALTERAR
                    credentials: 'awsAccessKey',
                    deploymentGroupAppspec: false,
                    deploymentGroupName: 'dcw-app-group', // ALTERAR
                    deploymentMethod: 'deploy',
                    excludes: '',
                    iamRoleArn: '',
                    includes: '**',
                    pollingFreqSec: 15,
                    pollingTimeoutSec: 600,
                    proxyHost: '',
                    proxyPort: 0,
                    region: 'us-east-1', // CHECAR
                    s3bucket: 'mondadori89-dcw-2022', // ALTERAR
                    s3prefix: '', 
                    subdirectory: '',
                    versionFileName: '',
                    waitForCompletion: true])
            }
        }
    	stage('Cleaning up') {
        	steps {
            	sh "docker rmi $registry:develop"
        	}
		}
    }
}